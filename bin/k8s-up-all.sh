#!/bin/bash
set -euo pipefail

# -----------------------------
# Config
# -----------------------------
CLUSTER_NAME="k8s-cluster"
K8S_CONFIG_FILE="config/kind-config.yaml"
ISTIO_NAMESPACE="istio-system"
AURORA_NAMESPACE="aurora"

# Services URLs (in-cluster)
PROM_URL="http://prometheus-server.${ISTIO_NAMESPACE}.svc.cluster.local"
JAEGER_URL="http://jaeger-query.${ISTIO_NAMESPACE}.svc.cluster.local:16686"
GRAFANA_URL="http://grafana.${ISTIO_NAMESPACE}.svc.cluster.local:3000"

# Helm values paths
PROM_VALUES="config/istio-prometheus-values.yaml"
GRAFANA_VALUES="config/istio-grafana-values.yaml"

# -----------------------------
# Create Kind Cluster
# -----------------------------
echo "====================================================="
echo "[1] Creating Kind cluster"
echo "====================================================="
if ! kind get clusters | grep -q "$CLUSTER_NAME"; then
    kind create cluster --name "$CLUSTER_NAME" --config "$K8S_CONFIG_FILE"
else
    echo "[INFO] Kind cluster '$CLUSTER_NAME' already exists. Skipping creation."
fi

kubectl wait --for=condition=Ready nodes --all --timeout=180s
kubectl get nodes -o wide
kubectl get pods -A

# -----------------------------
# Namespaces
# -----------------------------
kubectl create namespace "$ISTIO_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# -----------------------------
# Helm Repos
# -----------------------------
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo add jaegertracing https://jaegertracing.github.io/helm-charts
helm repo add kiali https://kiali.org/helm-charts
helm repo add istio https://istio-release.storage.googleapis.com/charts
helm repo update

# -----------------------------
# Install Istio Core
# -----------------------------
helm install istio-base istio/base -n "$ISTIO_NAMESPACE" --create-namespace
helm install istiod istio/istiod -n "$ISTIO_NAMESPACE" --set telemetry.enabled=true
helm install istio-ingress istio/gateway -n "$ISTIO_NAMESPACE"

# -----------------------------
# Install Prometheus
# -----------------------------
helm upgrade --install prometheus prometheus-community/prometheus \
  -n "$ISTIO_NAMESPACE" \
  -f "$PROM_VALUES"

# -----------------------------
# Install Grafana
# -----------------------------
helm upgrade --install grafana grafana/grafana \
  -n "$ISTIO_NAMESPACE" \
  -f "$GRAFANA_VALUES"

# -----------------------------
# Install Jaeger
# -----------------------------
helm upgrade --install jaeger jaegertracing/jaeger \
  -n "$ISTIO_NAMESPACE" \
  --set provisionDataStore.cassandra=false \
  --set storage.type=memory \
  --set query.service.type=ClusterIP \
  --set query.ingress.enabled=false \
  --set query.replicaCount=1

# -----------------------------
# Install Kiali
# -----------------------------
helm upgrade --install kiali-server kiali/kiali-server \
  -n "$ISTIO_NAMESPACE" \
  --set auth.strategy=anonymous \
  --set external_services.prometheus.url="$PROM_URL" \
  --set external_services.tracing.enabled=true \
  --set external_services.tracing.provider=jaeger \
  --set external_services.tracing.in_cluster_url="$JAEGER_URL" \
  --set external_services.grafana.url="$GRAFANA_URL" \
  --create-namespace

# This not stable in ci pipeline use sleep 30 in pipeline instead
## -----------------------------
## Wait for all pods
## -----------------------------
#kubectl wait --for=condition=Ready pods --all -n "$ISTIO_NAMESPACE" --timeout=60s

# -----------------------------
# Summary & Port-forward tips
# -----------------------------
echo "====================================================="
echo "[INFO] Setup complete!"
echo "[INFO] Prometheus: kubectl port-forward -n $ISTIO_NAMESPACE svc/prometheus 9090:9090"
echo "[INFO] Grafana: kubectl port-forward -n $ISTIO_NAMESPACE svc/grafana 3000:3000"
echo "[INFO] Jaeger: kubectl port-forward -n $ISTIO_NAMESPACE svc/jaeger-query 16686:16686"
echo "[INFO] Kiali: kubectl port-forward -n $ISTIO_NAMESPACE svc/kiali 20001:20001"
echo "====================================================="
