#!/bin/bash

set -e

NAMESPACE="mentoria"

# Carrega as variáveis do arquivo .env
if [ ! -f ".env" ]; then
    echo "Erro: arquivo .env não encontrado!"
    echo "Copie .env.example para .env e configure as credenciais."
    exit 1
fi

set -a
source .env
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