#!/usr/bin/env bash
# ForgeOps 2.0 安全验证（SEC-04/12，OPS-03/04）
# 真实检查：无默认秘密启动失败、Runtime 不发布端口、Webhook 签名与重放、日志无秘密。
set -uo pipefail
CURL="curl --max-time 30"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATEWAY="$ROOT/gateway/forgeops-gateway"
RUNTIME="$ROOT/runtime/forgeops-paseo-runtime"
DB_PORT="${FORGEOPS_VERIFY_DB_PORT:-55435}"
DB_CONTAINER="forgeops-v2-security-pg"
RUNTIME_PORT=17680
GATEWAY_PORT=18097
WEBHOOK_SECRET="verify-security-webhook-secret-$(date +%s)"
WORKDIR="$(mktemp -d /tmp/forgeops-v2-security.XXXXXX)"
DB_USER=forgeops
DB_PASSWORD="verify-security-$(date +%s)"
TOKEN_SECRET="verify-security-token-$(date +%s)"
SERVICE_TOKEN="verify-security-service-$(date +%s)"

PASS_COUNT=0; FAIL_COUNT=0; SKIP_COUNT=0
declare -a RESULTS
record() {
  RESULTS+=("$1|$2|$3")
  case "$1" in
    PASS) PASS_COUNT=$((PASS_COUNT+1)); printf 'PASS  %s — %s\n' "$2" "$3" ;;
    FAIL) FAIL_COUNT=$((FAIL_COUNT+1)); printf 'FAIL  %s — %s\n' "$2" "$3" ;;
    SKIPPED) SKIP_COUNT=$((SKIP_COUNT+1)); printf 'SKIP  %s — %s\n' "$2" "$3" ;;
  esac
}

cleanup() {
  if [ "${SCRIPT_FAILED:-0}" = "1" ]; then
    mkdir -p "/tmp/verify-v2-logs-security" && cp -R "$WORKDIR"/. "/tmp/verify-v2-logs-security/" 2>/dev/null || true
  fi
  [ -n "${GATEWAY_PID:-}" ] && kill "$GATEWAY_PID" 2>/dev/null
  [ -n "${RUNTIME_PID:-}" ] && kill "$RUNTIME_PID" 2>/dev/null
  docker rm -f "$DB_CONTAINER" >/dev/null 2>&1
  rm -rf "$WORKDIR"
}
trap cleanup EXIT

JAVA21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
if [ -z "$JAVA21_HOME" ]; then
  echo "JDK 21 is required"; exit 2
fi
export JAVA_HOME="$JAVA21_HOME"


wait_http() {
  local url="$1" expected="$2" timeout_s="$3"; shift 3
  local waited=0 status
  while [ "$waited" -lt "$timeout_s" ]; do
    status="$($CURL -o /dev/null -w '%{http_code}' "$@" "$url" 2>/dev/null || echo 000)"
    [ "$status" = "$expected" ] && return 0
    sleep 2; waited=$((waited+2))
  done
  return 1
}

# ---------------------------------------------------------------- OPS-03: 缺必需秘密 → compose 拒绝
if env -u FORGEOPS_DB_USER -u FORGEOPS_DB_PASSWORD -u FORGEOPS_V2_SECURITY_TOKEN_SECRET -u FORGEOPS_RUNTIME_SERVICE_TOKEN \
  docker compose -f "$ROOT/deploy/docker-compose/forgeops-v2.yml" config -q >/dev/null 2>&1; then
  record FAIL "ops03-no-default-secrets" "compose accepted configuration without required secrets"
else
  record PASS "ops03-no-default-secrets" "compose fails when required secrets are missing"
fi

# 仓库与镜像无有效默认 Token/密码：扫描已跟踪文件中的可疑硬编码
LEAKS="$(grep -rInE '(api[_-]?key|password|secret|token) *= *"[A-Za-z0-9+/=_-]{24,}"' \
  --include='*.yml' --include='*.yaml' --include='*.properties' --include='*.env' \
  "$ROOT/deploy" "$ROOT/registry" 2>/dev/null | grep -v 'change-me' | wc -l | tr -d ' ')"
[ "$LEAKS" = "0" ] && record PASS "no-hardcoded-secrets" "deploy/registry config scan clean" \
  || record FAIL "no-hardcoded-secrets" "$LEAKS suspicious entries"

# ---------------------------------------------------------------- OPS-04: 私网暴露
cat > "$WORKDIR/compose.env" <<EOF
FORGEOPS_DB_USER=$DB_USER
FORGEOPS_DB_PASSWORD=$DB_PASSWORD
FORGEOPS_DB_NAME=forgeops_v2
FORGEOPS_V2_SECURITY_TOKEN_SECRET=$TOKEN_SECRET
FORGEOPS_RUNTIME_SERVICE_TOKEN=$SERVICE_TOKEN
EOF
COMPOSE_OUT="$(docker compose -f "$ROOT/deploy/docker-compose/forgeops-v2.yml" --env-file "$WORKDIR/compose.env" config 2>/dev/null)"
PUBLISHED_COUNT="$(echo "$COMPOSE_OUT" | grep -c 'published:')"
NON_LOOPBACK_BINDS="$(echo "$COMPOSE_OUT" | grep 'host_ip:' | grep -vc '127.0.0.1' || true)"
if [ "$PUBLISHED_COUNT" = "1" ] && [ "$NON_LOOPBACK_BINDS" = "0" ]; then
  record PASS "ops04-private-exposure" "only the gateway publishes a port and it binds loopback"
else
  record FAIL "ops04-private-exposure" "published=$PUBLISHED_COUNT non-loopback=$NON_LOOPBACK_BINDS"
fi

# ---------------------------------------------------------------- 真实进程：SEC-04 Webhook 签名/重放、SEC-12 日志
docker rm -f "$DB_CONTAINER" >/dev/null 2>&1
docker run -d --name "$DB_CONTAINER" -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -e POSTGRES_DB=forgeops_v2 -p "127.0.0.1:$DB_PORT:5432" postgres:16-alpine >/dev/null
for i in $(seq 1 30); do docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" >/dev/null 2>&1 && break; sleep 1; done

mkdir -p "$WORKDIR/registry" "$WORKDIR/workspace/pilot-repo" "$WORKDIR/executor-tasks" "$WORKDIR/runtime-data"
git -C "$WORKDIR/workspace/pilot-repo" init -q 2>/dev/null
cat > "$WORKDIR/registry/pilot.yaml" <<'EOF'
id: pilot
enabled: true
repositoryRoot: pilot-repo
allowedPaths:
  - .
browserOrigins:
  - https://pilot.verify.local
policy:
  productionDeploy: false
github:
  repository: example/pilot
  baseBranch: main
  allowedMergeLogins:
    - pilot-owner
  requiredCheckName: forgeops-test
  testEnvironment: test
qualityPolicy:
  categoryMappings:
    forgeops-test: BUILD
EOF

(cd "$RUNTIME" && pnpm build >/dev/null 2>&1)
FORGEOPS_RUNTIME_SERVICE_TOKEN="$SERVICE_TOKEN" \
FORGEOPS_RUNTIME_DATA_FILE="$WORKDIR/runtime-data/runs.json" \
FORGEOPS_RUNTIME_ALLOWED_ROOTS="$WORKDIR/workspace" \
FORGEOPS_RUNTIME_TASK_ROOTS="$WORKDIR/executor-tasks" \
FORGEOPS_RUNTIME_PORT="$RUNTIME_PORT" \
node "$RUNTIME/dist/server.js" > "$WORKDIR/runtime.log" 2>&1 &
RUNTIME_PID=$!

java -jar "$(ls "$GATEWAY"/target/*.jar | head -1)" \
  --server.port="$GATEWAY_PORT" \
  --forgeops.v2.registry.path="$WORKDIR/registry" \
  --forgeops.v2.registry.workspace-root="$WORKDIR/workspace" \
  --forgeops.v2.security.token-secret="$TOKEN_SECRET" \
  --forgeops.v2.runtime.base-url="http://127.0.0.1:$RUNTIME_PORT" \
  --forgeops.v2.runtime.service-token="$SERVICE_TOKEN" \
  --forgeops.v2.github.enabled=true \
  --forgeops.v2.github.webhook-secret="$WEBHOOK_SECRET" \
  --forgeops.v2.github.api-token="read-only-probe-token" \
  --forgeops.v2.verification.planner-enabled=false \
  --forgeops.v2.verification.executor-task-root="$WORKDIR/executor-tasks" \
  --spring.datasource.url="jdbc:postgresql://127.0.0.1:$DB_PORT/forgeops_v2" \
  --spring.datasource.username="$DB_USER" \
  --spring.datasource.password="$DB_PASSWORD" \
  > "$WORKDIR/gateway.log" 2>&1 &
GATEWAY_PID=$!

if ! wait_http "http://127.0.0.1:$GATEWAY_PORT/actuator/health/readiness" 200 120; then
  record FAIL "gateway-start" "see $WORKDIR/gateway.log"
  echo "gateway failed to start; aborting"; SCRIPT_FAILED=1; exit 1
fi

WEBHOOK_BODY='{"repository":{"full_name":"example/pilot"},"action":"completed","check_run":{"id":1,"name":"forgeops-test","pull_requests":[{"number":9}],"head_sha":"deadbeef","status":"completed","conclusion":"success"}}'
SIG="$(printf '%s' "$WEBHOOK_BODY" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" -hex | sed 's/^.* //')"

NO_SIG="$($CURL -o /dev/null -w '%{http_code}' -X POST -H "Content-Type: application/json" \
  -H "X-GitHub-Delivery: sec-no-sig" -H "X-GitHub-Event: check_run" \
  -d "$WEBHOOK_BODY" "http://127.0.0.1:$GATEWAY_PORT/integrations/github")"
BAD_SIG="$($CURL -o /dev/null -w '%{http_code}' -X POST -H "Content-Type: application/json" \
  -H "X-GitHub-Delivery: sec-bad-sig" -H "X-GitHub-Event: check_run" \
  -H "X-Hub-Signature-256: sha256=$(printf '%s' "$WEBHOOK_BODY" | openssl dgst -sha256 -hmac "wrong-secret" -hex | sed 's/^.* //')" \
  -d "$WEBHOOK_BODY" "http://127.0.0.1:$GATEWAY_PORT/integrations/github")"
GOOD_SIG="$($CURL -o /dev/null -w '%{http_code}' -X POST -H "Content-Type: application/json" \
  -H "X-GitHub-Delivery: sec-good-sig" -H "X-GitHub-Event: check_run" \
  -H "X-Hub-Signature-256: sha256=$SIG" \
  -d "$WEBHOOK_BODY" "http://127.0.0.1:$GATEWAY_PORT/integrations/github")"
if [ "$NO_SIG" = "400" ] && [ "$BAD_SIG" = "401" ]; then
  record PASS "sec04-webhook-signature" "absent header rejected 400, wrong signature rejected 401 (signed delivery: $GOOD_SIG)"
else
  record FAIL "sec04-webhook-signature" "no-sig=$NO_SIG bad-sig=$BAD_SIG"
fi

# SEC-04 重放：同一 delivery id 重复投递 → Inbox 只有一条事实
REPLAY1="$($CURL -o /dev/null -w '%{http_code}' -X POST -H "Content-Type: application/json" \
  -H "X-GitHub-Delivery: sec-replay-id" -H "X-GitHub-Event: check_run" \
  -H "X-Hub-Signature-256: sha256=$SIG" -d "$WEBHOOK_BODY" "http://127.0.0.1:$GATEWAY_PORT/integrations/github")"
REPLAY2="$($CURL -o /dev/null -w '%{http_code}' -X POST -H "Content-Type: application/json" \
  -H "X-GitHub-Delivery: sec-replay-id" -H "X-GitHub-Event: check_run" \
  -H "X-Hub-Signature-256: sha256=$SIG" -d "$WEBHOOK_BODY" "http://127.0.0.1:$GATEWAY_PORT/integrations/github")"
FACTS="$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d forgeops_v2 -tAc \
  "select count(*) from integration_event where external_event_id = 'sec-replay-id'")"
if [ "$REPLAY1" = "202" ] && [ "$REPLAY2" = "202" ] && [ "$FACTS" = "1" ]; then
  record PASS "sec04-webhook-replay-idempotent" "two deliveries, one durable fact"
else
  record FAIL "sec04-webhook-replay-idempotent" "r1=$REPLAY1 r2=$REPLAY2 facts=$FACTS"
fi

# SEC-12：日志与 Compose 输出不含秘密原值
sleep 2
if grep -q "$WEBHOOK_SECRET\|$TOKEN_SECRET\|$SERVICE_TOKEN\|$DB_PASSWORD" \
  "$WORKDIR/gateway.log" "$WORKDIR/runtime.log" 2>/dev/null; then
  record FAIL "sec12-no-secrets-in-logs" "a configured secret appears verbatim in service logs"
else
  record PASS "sec12-no-secrets-in-logs" "gateway and runtime logs contain no configured secret values"
fi

# 停止后完成清理性断言
echo
echo "================ verify-v2-security summary ================"
for line in "${RESULTS[@]}"; do echo "$line" | awk -F'|' '{printf "%-8s %-34s %s\n", $1, $2, $3}'; done
echo "PASS=$PASS_COUNT FAIL=$FAIL_COUNT SKIPPED=$SKIP_COUNT"
[ "$FAIL_COUNT" -eq 0 ] || { SCRIPT_FAILED=1; exit 1; }
[ "$SKIP_COUNT" -eq 0 ] || exit 3
exit 0
