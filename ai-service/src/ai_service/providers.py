import logging
import math
import time
from typing import Callable, Sequence

from ai_service.config import Settings

log = logging.getLogger(__name__)

EmbedQuery = Callable[[str], list[float]]
EmbedDocuments = Callable[[Sequence[str]], list[list[float]]]


def fit_dimensions(vector: Sequence[float], dimensions: int) -> list[float]:
    if len(vector) == dimensions:
        return list(vector)
    if len(vector) < dimensions:
        raise ValueError(f"O modelo devolveu {len(vector)} dimensões; o banco espera {dimensions}. "
                         "Ajuste EMBEDDING_DIMENSIONS e recrie as tabelas.")
    truncated = list(vector[:dimensions])
    norm = math.sqrt(sum(v * v for v in truncated)) or 1.0
    return [v / norm for v in truncated]


def is_rate_limit(error: BaseException) -> bool:
    return getattr(error, "status_code", None) == 429 or type(error).__name__ == "RateLimitError"


def with_rate_limit_retry(call: Callable, attempts: int, wait_seconds: float,
                          sleep: Callable[[float], None] = time.sleep) -> Callable:
    def wrapper(*args, **kwargs):
        for attempt in range(1, attempts + 1):
            try:
                return call(*args, **kwargs)
            except Exception as error:
                if not is_rate_limit(error) or attempt == attempts:
                    raise
                log.warning("Limite de requisições do provedor atingido; nova tentativa em %.0f s (%d/%d).",
                            wait_seconds, attempt, attempts - 1)
                sleep(wait_seconds)
    return wrapper


def _embeddings(settings: Settings, batch: int):
    from langchain_openai import OpenAIEmbeddings

    options = dict(model=settings.embedding_model, api_key=settings.api_key, chunk_size=batch, max_retries=2)
    if settings.openai_compatible:
        options.update(base_url=settings.base_url, check_embedding_ctx_length=False)
    if settings.send_dimensions:
        options["dimensions"] = settings.embedding_dimensions
    return OpenAIEmbeddings(**options)


def query_embedder(settings: Settings) -> EmbedQuery:
    client = _embeddings(settings, batch=1)
    dimensions = settings.embedding_dimensions
    return lambda text: fit_dimensions(client.embed_query(text), dimensions)


def document_embedder(settings: Settings, attempts: int = 30) -> EmbedDocuments:
    client = _embeddings(settings, batch=settings.embedding_batch)
    dimensions = settings.embedding_dimensions
    embed = with_rate_limit_retry(client.embed_documents, attempts, settings.rate_limit_wait_seconds)
    return lambda texts: [fit_dimensions(vector, dimensions) for vector in embed(list(texts))]


def chat_model(settings: Settings):
    from langchain_openai import ChatOpenAI

    from ai_service.analysis import AnalysisResult

    options = dict(model=settings.llm_model, api_key=settings.api_key, temperature=0,
                   timeout=settings.llm_timeout_seconds, max_retries=settings.llm_max_retries)
    if settings.openai_compatible:
        options["base_url"] = settings.base_url
    return ChatOpenAI(**options).with_structured_output(AnalysisResult, method=settings.structured_output)


def embedding_fingerprint(settings: Settings) -> str:
    return f"{settings.provider}:{settings.embedding_model}:{settings.embedding_dimensions}"
