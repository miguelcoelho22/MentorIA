import os
from dataclasses import dataclass
from pathlib import Path

DEFAULT_CORPUS_DIR = Path(__file__).resolve().parents[2] / "corpus"
DEFAULT_CATALOG_DIR = Path(__file__).resolve().parents[2] / "catalog"

PROVIDERS = {
    "openai": {
        "base_url": None,
        "llm_model": "gpt-4o-mini",
        "embedding_model": "text-embedding-3-small",
        "embedding_batch": 200,
        "send_dimensions": True,
        "llm_max_retries": 1,
        # US$ por milhão de tokens (gpt-4o-mini, tabela pública da OpenAI).
        "price_input": 0.15,
        "price_output": 0.60,
    },
    "gemini": {
        "base_url": "https://generativelanguage.googleapis.com/v1beta/openai/",
        "llm_model": "gemini-3.1-flash-lite",
        "embedding_model": "gemini-embedding-001",
        "embedding_batch": 50,
        "send_dimensions": False,
        "llm_max_retries": 3,
        # Estimativa em US$ por milhão de tokens no plano pago do Gemini Flash-Lite (o gratuito custa 0).
        # Confira a tabela do Google e ajuste com LLM_PRICE_INPUT_PER_MILLION / LLM_PRICE_OUTPUT_PER_MILLION.
        "price_input": 0.10,
        "price_output": 0.40,
    },
}


def _env(name: str, default: str) -> str:
    return os.getenv(name) or default


def _flag(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if not value:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    database_url: str
    provider: str
    api_key: str | None
    base_url: str | None
    llm_model: str
    llm_timeout_seconds: float
    llm_max_retries: int
    structured_output: str
    embedding_model: str
    embedding_dimensions: int
    embedding_batch: int
    send_dimensions: bool
    rate_limit_wait_seconds: float
    top_k: int
    essay_k: int
    corpus_dir: Path
    catalog_dir: Path
    auto_ingest: bool
    rabbitmq_url: str = "amqp://guest:guest@localhost:5672/"
    rabbitmq_exchange: str = "mentoria.saga"
    rabbitmq_queue: str = "ai.processar.redacao"
    rabbitmq_reply_routing_key: str = "saga.redacao.analisada"
    messaging_enabled: bool = False
    feedback_service_url: str | None = None
    circuit_failure_threshold: int = 3
    circuit_open_seconds: float = 30.0
    cache_size: int = 256
    cache_ttl_seconds: float = 3600.0
    price_input_per_million: float = 0.15
    price_output_per_million: float = 0.60

    @property
    def openai_compatible(self) -> bool:
        return self.base_url is not None

    @classmethod
    def from_env(cls) -> "Settings":
        provider = _env("LLM_PROVIDER", "openai").strip().lower()
        if provider not in PROVIDERS:
            raise ValueError(f"LLM_PROVIDER inválido: {provider}. Use um de: {', '.join(PROVIDERS)}.")
        preset = PROVIDERS[provider]
        key_vars = ("GEMINI_API_KEY", "LLM_API_KEY") if provider == "gemini" else ("OPENAI_API_KEY", "LLM_API_KEY")
        return cls(
            database_url=_env("DATABASE_URL", "postgresql://localhost:5432/ai_db"),
            provider=provider,
            api_key=next((os.getenv(var) for var in key_vars if os.getenv(var)), None),
            base_url=os.getenv("LLM_BASE_URL") or preset["base_url"],
            llm_model=_env("LLM_MODEL", preset["llm_model"]),
            llm_timeout_seconds=float(_env("LLM_TIMEOUT_SECONDS", "45")),
            llm_max_retries=int(_env("LLM_MAX_RETRIES", str(preset["llm_max_retries"]))),
            structured_output=_env("LLM_STRUCTURED_OUTPUT", "json_schema"),
            embedding_model=_env("EMBEDDING_MODEL", preset["embedding_model"]),
            embedding_dimensions=int(_env("EMBEDDING_DIMENSIONS", "1536")),
            embedding_batch=int(_env("EMBEDDING_BATCH", str(preset["embedding_batch"]))),
            send_dimensions=_flag("EMBEDDING_SEND_DIMENSIONS", preset["send_dimensions"]),
            rate_limit_wait_seconds=float(_env("RATE_LIMIT_WAIT_SECONDS", "60")),
            top_k=int(_env("RAG_TOP_K", "3")),
            essay_k=int(_env("RAG_ESSAY_K", "3")),
            corpus_dir=Path(_env("CORPUS_DIR", str(DEFAULT_CORPUS_DIR))),
            catalog_dir=Path(_env("CATALOG_DIR", str(DEFAULT_CATALOG_DIR))),
            auto_ingest=_flag("AUTO_INGEST", True),
            rabbitmq_url=_env("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/"),
            rabbitmq_exchange=_env("RABBITMQ_EXCHANGE", "mentoria.saga"),
            rabbitmq_queue=_env("RABBITMQ_QUEUE", "ai.processar.redacao"),
            rabbitmq_reply_routing_key=_env("RABBITMQ_REPLY_ROUTING_KEY", "saga.redacao.analisada"),
            messaging_enabled=_flag("MESSAGING_ENABLED", True),
            feedback_service_url=os.getenv("FEEDBACK_SERVICE_URL") or None,
            circuit_failure_threshold=int(_env("CIRCUIT_FAILURE_THRESHOLD", "3")),
            circuit_open_seconds=float(_env("CIRCUIT_OPEN_SECONDS", "30")),
            cache_size=int(_env("LLM_CACHE_SIZE", "256")),
            cache_ttl_seconds=float(_env("LLM_CACHE_TTL_SECONDS", "3600")),
            price_input_per_million=float(_env("LLM_PRICE_INPUT_PER_MILLION", str(preset["price_input"]))),
            price_output_per_million=float(_env("LLM_PRICE_OUTPUT_PER_MILLION", str(preset["price_output"]))),
        )
