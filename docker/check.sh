#!/usr/bin/env bash
# Confirma cada serviço do ambiente local (CA3). Uso: docker/check.sh
set -u
cd "$(dirname "$0")"
fail=0
export HERACLES_S3_PORT=${HERACLES_S3_PORT:-4567} HERACLES_API_PORT=${HERACLES_API_PORT:-8080}
check() { # nome comando...
  local name=$1; shift
  if "$@" >/dev/null 2>&1; then echo "ok    $name"; else echo "FALHA $name"; fail=1; fi
}
check postgres      docker compose exec -T postgres pg_isready -U postgres -d heracles
check elasticmq     curl -fs "http://localhost:${HERACLES_SQS_PORT:-9324}/?Action=ListQueues"
check localstack-s3 bash -c 'curl -fs http://localhost:${HERACLES_S3_PORT:-4567}/_localstack/health | grep -Eq "\"s3\": \"(available|running)\""'
check oidc          curl -fs http://localhost:${HERACLES_OIDC_PORT:-8082}/default/.well-known/openid-configuration
check otel          curl -fs http://localhost:${HERACLES_GRAFANA_PORT:-3001}/api/health
check backend       bash -c 'curl -fs http://localhost:${HERACLES_API_PORT:-8080}/actuator/health/liveness | grep -q UP'
exit $fail
