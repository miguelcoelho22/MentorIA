#!/bin/bash

set -e

NAMESPACE="mentoria"

kubectl create secret generic mentoria-db-secrets \
  --namespace="$NAMESPACE" \
  --from-literal=ACCOUNT_DB_USER=account \
  --from-literal=ACCOUNT_DB_PASSWORD=account_dev \
  --from-literal=ASSESSMENT_DB_USER=assessment \
  --from-literal=ASSESSMENT_DB_PASSWORD=assessment_dev \
  --from-literal=FEEDBACK_DB_USER=feedback \
  --from-literal=FEEDBACK_DB_PASSWORD=feedback_dev \
  --from-literal=STUDY_PLAN_DB_USER=studyplan \
  --from-literal=STUDY_PLAN_DB_PASSWORD=studyplan_dev \
  --from-literal=AI_DB_USER=ai \
  --from-literal=AI_DB_PASSWORD=ai_dev \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic mentoria-rabbitmq-secrets \
  --namespace="$NAMESPACE" \
  --from-literal=RABBITMQ_USER=mentoria \
  --from-literal=RABBITMQ_PASSWORD=mentoria_dev \
  --dry-run=client -o yaml | kubectl apply -f -

echo "Secrets criados ou atualizados com sucesso!"