#!/bin/bash

set -e

NAMESPACE="mentoria"

# Carrega as variáveis do arquivo .env
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE=".env"
[ -f "$ENV_FILE" ] || ENV_FILE="$SCRIPT_DIR/../../docker/.env"
if [ ! -f "$ENV_FILE" ]; then
    echo "Erro: arquivo .env não encontrado!"
    echo "Copie infrastructure/docker/.env.example para infrastructure/docker/.env e configure as credenciais."
    exit 1
fi

set -a
source "$ENV_FILE"
set +a

# Cria os Secrets dos bancos de dados
kubectl create secret generic mentoria-db-secrets \
  --namespace="$NAMESPACE" \
  --from-literal=ACCOUNT_DB_USER="$ACCOUNT_DB_USER" \
  --from-literal=ACCOUNT_DB_PASSWORD="$ACCOUNT_DB_PASSWORD" \
  --from-literal=ASSESSMENT_DB_USER="$ASSESSMENT_DB_USER" \
  --from-literal=ASSESSMENT_DB_PASSWORD="$ASSESSMENT_DB_PASSWORD" \
  --from-literal=FEEDBACK_DB_USER="$FEEDBACK_DB_USER" \
  --from-literal=FEEDBACK_DB_PASSWORD="$FEEDBACK_DB_PASSWORD" \
  --from-literal=STUDY_PLAN_DB_USER="$STUDY_PLAN_DB_USER" \
  --from-literal=STUDY_PLAN_DB_PASSWORD="$STUDY_PLAN_DB_PASSWORD" \
  --from-literal=AI_DB_USER="$AI_DB_USER" \
  --from-literal=AI_DB_PASSWORD="$AI_DB_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

# Cria o Secret do RabbitMQ
kubectl create secret generic mentoria-rabbitmq-secrets \
  --namespace="$NAMESPACE" \
  --from-literal=RABBITMQ_USER="$RABBITMQ_USER" \
  --from-literal=RABBITMQ_PASSWORD="$RABBITMQ_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "Secrets criados ou atualizados com sucesso!"