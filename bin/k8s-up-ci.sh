#!/bin/bash
set -euo pipefail

CLUSTER_NAME="k8s-cluster"
K8S_CONFIG_FILE="config/kind-config.yaml"

# Create Kind Cluster if not exist
if ! kind get clusters | grep -q "$CLUSTER_NAME"; then
    kind create cluster --name "$CLUSTER_NAME" --config "$K8S_CONFIG_FILE"
else
    echo "[INFO] Kind cluster '$CLUSTER_NAME' already exists. Skipping creation."
fi

# Wait for all nodes get ready
kubectl wait --for=condition=Ready nodes --all --timeout=180s
kubectl get nodes -o wide