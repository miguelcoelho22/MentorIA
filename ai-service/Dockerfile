# Etapa 1: Build e instalação das dependências
FROM ghcr.io/astral-sh/uv:python3.14-bookworm-slim AS build

WORKDIR /app

COPY pyproject.toml uv.lock ./

RUN uv sync --frozen --no-dev --no-install-project

# Etapa 2: Runtime
FROM python:3.14-slim

WORKDIR /app

COPY --from=build /app/.venv /app/.venv
COPY src/ ./src/

ENV PATH="/app/.venv/bin:$PATH"
ENV PYTHONPATH="/app/src"
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

CMD ["uvicorn", "ai_service.main:app", "--host", "0.0.0.0", "--port", "8000"]