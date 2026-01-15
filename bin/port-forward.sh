#!/bin/bash
set -euo pipefail

NAMESPACE="shortlink"

# -----------------------------
# Start port-forwards in background
# -----------------------------

kubectl port-forward svc/postgres 5432:5432 -n ${NAMESPACE} &
POSTGRES_ALL_PID=$!

kubectl port-forward svc/redis 6379:6379 -n ${NAMESPACE} &
REDIS_PID=$!

# Give port-forwards a few seconds to be ready
sleep 5

echo "Port-forwards running:"
echo "postgres-all PID: POSTGRES_ALL_PID"
echo "redis PID: $REDIS_PID"
