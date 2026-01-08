#!/bin/bash
set -euo pipefail

NAMESPACE="shortlink"

# -----------------------------
# Start port-forwards in background
# -----------------------------

kubectl port-forward svc/postgres-admin 5432:5432 -n ${NAMESPACE}
POSTGRES_ADMIN_PID=$!

kubectl port-forward svc/postgres-shortlink 5433:5432 -n ${NAMESPACE}
POSTGRES_SHORTLINK_PID=$!

kubectl port-forward svc/redis 6379:6379 -n ${NAMESPACE}
REDIS_PID=$!

# Give port-forwards a few seconds to be ready
sleep 5

#
## -----------------------------
## Kill background port-forwards after build
## -----------------------------
#kill POSTGRES_ADMIN_PID POSTGRES_SHORTLINK_PID REDIS_PID || true