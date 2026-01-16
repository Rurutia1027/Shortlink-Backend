#!/bin/bash
set -euo pipefail

istio_envoy_pod_name=$(kubectl get pods -n istio-system -l app=istio-ingress -o jsonpath='{.items[0].metadata.name}')

kubectl -n istio-system port-forward  ${istio_envoy_pod_name} 8080:80

# we fetch current istio-system namespace inner svc name via this command
# kubectl get pods -n istio-system -l app=istio-ingress  -o jsonpath='{.items[0]}.metadata.name'