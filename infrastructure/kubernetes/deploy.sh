#!/bin/bash

set -e

NAMESPACE="mentoria"
K8S_DIR="infrastructure/kubernetes"

echo "Criando namespace..."
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

echo "Configurando namespace padrão..."
kubectl config set-context --current --namespace="$NAMESPACE"

echo "Criando Secrets..."
./infrastructure/kubernetes/scripts/create-secrets.sh

echo "Aplicando ConfigMaps..."
kubectl apply -f "$K8S_DIR/configmaps/"

echo "Aplicando armazenamento..."
kubectl apply -f "$K8S_DIR/storage/"

echo "Aplicando Deployments..."
kubectl apply -f "$K8S_DIR/deployments/"

echo "Aplicando Services..."
kubectl apply -f "$K8S_DIR/services/"

echo "Aplicando Ingress..."
kubectl apply -f "$K8S_DIR/ingress/"

echo "Implantação concluída!"
kubectl get deployments
kubectl get pods
kubectl get pvc
kubectl get ingress