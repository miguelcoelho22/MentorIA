"""O LLM tratado como dependência remota falível.

Três mecanismos ficam aqui, todos sem dependências externas:

- ``CircuitBreaker``: depois de N falhas seguidas, para de chamar o provedor por um tempo
  e falha na hora (``CircuitOpenError``). Passado esse tempo, deixa uma chamada de teste
  passar (meio-aberto): se ela der certo, o circuito fecha; se falhar, abre de novo.
- ``TtlCache``: cache por chave (hash do pedido) com tempo de vida e limite de itens.
  A mesma redação corrigida duas vezes não gera uma segunda chamada paga ao modelo.
- ``Metrics``: tempo de resposta medido e tokens gastos por operação, para a análise de
  latência e custo exposta em ``GET /api/v1/metrics``.

O timeout e o retry com backoff exponencial ficam no cliente da OpenAI usado pelo
LangChain (``timeout`` e ``max_retries`` em ``providers.chat_model``).
"""

import hashlib
import json
import threading
import time
from collections import OrderedDict, deque
from dataclasses import dataclass, field
from typing import Any, Callable


class CircuitOpenError(Exception):
    """O circuito está aberto: o provedor falhou demais e não será chamado agora."""


class CircuitBreaker:
    CLOSED, OPEN, HALF_OPEN = "CLOSED", "OPEN", "HALF_OPEN"

    def __init__(self, failure_threshold: int = 3, open_seconds: float = 30.0,
                 clock: Callable[[], float] = time.monotonic, ignore: tuple[type[BaseException], ...] = ()):
        self.failure_threshold = max(1, failure_threshold)
        self.open_seconds = open_seconds
        self._clock = clock
        self._ignore = ignore
        self._lock = threading.Lock()
        self._state = self.CLOSED
        self._failures = 0
        self._opened_at = 0.0
        self._trial_running = False

    @property
    def state(self) -> str:
        with self._lock:
            self._refresh()
            return self._state

    def _refresh(self) -> None:
        if self._state == self.OPEN and self._clock() - self._opened_at >= self.open_seconds:
            self._state = self.HALF_OPEN
            self._trial_running = False

    def call(self, function: Callable, *args, **kwargs):
        with self._lock:
            self._refresh()
            if self._state == self.OPEN:
                remaining = self.open_seconds - (self._clock() - self._opened_at)
                raise CircuitOpenError(f"Circuito aberto após {self._failures} falhas seguidas do provedor; "
                                       f"nova tentativa em {max(0.0, remaining):.0f} s.")
            if self._state == self.HALF_OPEN:
                if self._trial_running:
                    raise CircuitOpenError("Circuito meio-aberto: uma chamada de teste já está em andamento.")
                self._trial_running = True
        try:
            result = function(*args, **kwargs)
        except self._ignore:
            # Erro do nosso lado (ex.: resposta fora do contrato), não indisponibilidade do provedor.
            with self._lock:
                self._trial_running = False
            raise
        except Exception:
            with self._lock:
                self._failures += 1
                self._trial_running = False
                if self._state == self.HALF_OPEN or self._failures >= self.failure_threshold:
                    self._state = self.OPEN
                    self._opened_at = self._clock()
            raise
        with self._lock:
            self._state = self.CLOSED
            self._failures = 0
            self._trial_running = False
        return result

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            self._refresh()
            return {"state": self._state, "consecutiveFailures": self._failures,
                    "failureThreshold": self.failure_threshold, "openSeconds": self.open_seconds}


class TtlCache:
    def __init__(self, max_items: int = 256, ttl_seconds: float = 3600.0,
                 clock: Callable[[], float] = time.monotonic):
        self.max_items = max_items
        self.ttl_seconds = ttl_seconds
        self._clock = clock
        self._items: OrderedDict[str, tuple[float, Any]] = OrderedDict()
        self._lock = threading.Lock()
        self.hits = 0
        self.misses = 0

    @staticmethod
    def key(*parts: Any) -> str:
        raw = json.dumps(parts, ensure_ascii=False, sort_keys=True, default=str)
        return hashlib.sha256(raw.encode("utf-8")).hexdigest()

    def get(self, key: str) -> Any | None:
        if self.max_items <= 0:
            return None
        with self._lock:
            item = self._items.get(key)
            if item is None or self._clock() - item[0] > self.ttl_seconds:
                self._items.pop(key, None)
                self.misses += 1
                return None
            self._items.move_to_end(key)
            self.hits += 1
            return item[1]

    def put(self, key: str, value: Any) -> None:
        if self.max_items <= 0:
            return
        with self._lock:
            self._items[key] = (self._clock(), value)
            self._items.move_to_end(key)
            while len(self._items) > self.max_items:
                self._items.popitem(last=False)

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            total = self.hits + self.misses
            return {"items": len(self._items), "maxItems": self.max_items, "ttlSeconds": self.ttl_seconds,
                    "hits": self.hits, "misses": self.misses,
                    "hitRate": round(self.hits / total, 3) if total else 0.0}


@dataclass
class _Operation:
    latencies_ms: deque = field(default_factory=lambda: deque(maxlen=500))
    calls: int = 0
    failures: int = 0
    cache_hits: int = 0
    llm_calls: int = 0
    input_tokens: int = 0
    output_tokens: int = 0


def _percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round(fraction * (len(ordered) - 1))))
    return ordered[index]


class Metrics:
    def __init__(self, price_input_per_million: float = 0.0, price_output_per_million: float = 0.0,
                 model: str = ""):
        self.price_input = price_input_per_million
        self.price_output = price_output_per_million
        self.model = model
        self._ops: dict[str, _Operation] = {}
        self._lock = threading.Lock()

    def _op(self, name: str) -> _Operation:
        return self._ops.setdefault(name, _Operation())

    def record(self, name: str, latency_ms: float, ok: bool, cache_hit: bool = False,
               input_tokens: int = 0, output_tokens: int = 0, llm_calls: int = 0) -> None:
        with self._lock:
            op = self._op(name)
            op.calls += 1
            op.latencies_ms.append(latency_ms)
            op.failures += 0 if ok else 1
            op.cache_hits += 1 if cache_hit else 0
            op.llm_calls += llm_calls
            op.input_tokens += input_tokens
            op.output_tokens += output_tokens

    def cost(self, input_tokens: float, output_tokens: float) -> float:
        return (input_tokens * self.price_input + output_tokens * self.price_output) / 1_000_000

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            operations = {}
            for name, op in self._ops.items():
                values = list(op.latencies_ms)
                avg_in = op.input_tokens / op.llm_calls if op.llm_calls else 0.0
                avg_out = op.output_tokens / op.llm_calls if op.llm_calls else 0.0
                per_call = self.cost(avg_in, avg_out)
                operations[name] = {
                    "calls": op.calls,
                    "failures": op.failures,
                    "cacheHits": op.cache_hits,
                    "latencyMs": {
                        "avg": round(sum(values) / len(values), 1) if values else 0.0,
                        "p50": round(_percentile(values, 0.50), 1),
                        "p95": round(_percentile(values, 0.95), 1),
                        "max": round(max(values), 1) if values else 0.0,
                        "samples": len(values),
                    },
                    "tokensPerLlmCall": {"input": round(avg_in, 1), "output": round(avg_out, 1)},
                    "estimatedCostUsd": {
                        "perOperation": round(per_call, 6),
                        "per1000": round(per_call * 1_000, 4),
                        "per100000": round(per_call * 100_000, 2),
                    },
                }
            return {"model": self.model,
                    "pricesUsdPerMillionTokens": {"input": self.price_input, "output": self.price_output},
                    "operations": operations}


class Timer:
    def __init__(self) -> None:
        self.start = time.perf_counter()

    @property
    def elapsed_ms(self) -> float:
        return (time.perf_counter() - self.start) * 1000
