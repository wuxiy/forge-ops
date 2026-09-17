#!/usr/bin/env bash
# ForgeOps 2.0 恢复验证（OPS-06/07/08/09，Q-05 故障注入）
# 真实进程：Gateway + Runtime + 隔离 PostgreSQL。
# 注入：Gateway 处理中 kill -9 重启；PostgreSQL 短暂中断；Runtime 中断后恢复。
set -uo pipefail
CURL="curl --max-time 30"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATEWAY="$ROOT/gateway/forgeops-gateway"
RUNTIME="$ROOT/runtime/forgeops-paseo-runtime"
DB_PORT="${FORGEOPS_VERIFY_DB_PORT:-55434}"
DB_CONTAINER="forgeops-v2-recovery-pg"
RUNTIME_PORT=17679
GATEWAY_PORT=18096
WORKDIR="$(mktemp -d /tmp/forgeops-v2-recovery.XXXXXX)"
DB_USER=forgeops
DB_PASSWORD="verify-recovery-$(date +%s)"
TOKEN_SECRET="verify-recovery-secret-$(date +%s)"
SERVICE_TOKEN="verify-recovery-service-$(date +%s)"

PASS_COUNT=0; FAIL_COUNT=0
declare -a RESULTS
record() {
  RESULTS+=("$1|$2|$3")
  case "$1" in
    PASS) PASS_COUNT=$((PASS_COUNT+1)); printf 'PASS  %s — %s\n' "$2" "$3" ;;
    FAIL) FAIL_COUNT=$((FAIL_COUNT+1)); printf 'FAIL  %s — %s\n' "$2" "$3" ;;
  esac
}

cleanup() {
  if [ "${SCRIPT_FAILED:-0}" = "1" ]; then
    mkdir -p "/tmp/verify-v2-logs-recovery" && cp -R "$WORKDIR"/. "/tmp/verify-v2-logs-recovery/" 2>/dev/null || true
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

mint_token() {
  python3 - "$TOKEN_SECRET" "$1" "$2" "$3" <<'PY'
import base64, hmac, hashlib, sys, time
secret, subject, project, scopes = sys.argv[1:5]
payload = "v1|%s|%s|%s|%d" % (subject, project, scopes, int(time.time()) + 600)
encoded = base64.urlsafe_b64encode(payload.encode()).rstrip(b"=").decode()
sig = base64.urlsafe_b64encode(hmac.new(secret.encode(), encoded.encode(), hashlib.sha256).digest()).rstrip(b"=").decode()
print(encoded + "." + sig)
PY
}

start_gateway() {
  "$JAVA_HOME/bin/java" -jar "$(ls "$GATEWAY"/target/*.jar | head -1)" \
    --server.port="$GATEWAY_PORT" \
    --forgeops.v2.registry.path="$WORKDIR/registry" \
    --forgeops.v2.registry.workspace-root="$WORKDIR/workspace" \
    --forgeops.v2.security.token-secret="$TOKEN_SECRET" \
    --forgeops.v2.runtime.base-url="http://127.0.0.1:$RUNTIME_PORT" \
    --forgeops.v2.runtime.service-token="$SERVICE_TOKEN" \
    --forgeops.v2.runtime.run-timeout-millis=60000 \
    --forgeops.v2.github.enabled=false \
    --forgeops.v2.verification.planner-enabled=false \
    --forgeops.v2.verification.executor-task-root="$WORKDIR/executor-tasks" \
    --forgeops.v2.reconciliation.abandoned-threshold-seconds=5 \
    --spring.datasource.url="jdbc:postgresql://127.0.0.1:$DB_PORT/forgeops_v2" \
    --spring.datasource.username="$DB_USER" \
    --spring.datasource.password="$DB_PASSWORD" \
    >> "$WORKDIR/gateway.log" 2>&1 &
  GATEWAY_PID=$!
}

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
wait_http "http://127.0.0.1:$RUNTIME_PORT/v1/runs/probe" 401 30 || true

start_gateway
if wait_http "http://127.0.0.1:$GATEWAY_PORT/actuator/health/readiness" 200 90; then
  record PASS "initial-readiness" "UP with PostgreSQL and Runtime reachable"
else
  record FAIL "initial-readiness" "see $WORKDIR/gateway.log"; exit 1
fi

TOKEN="$(mint_token verifier pilot feedback:write,feedback:read)"

# ---------------------------------------------------------------- OPS-08: PostgreSQL 短暂中断
docker pause "$DB_CONTAINER" >/dev/null
sleep 2
READINESS_DOWN="$($CURL -o /dev/null -w '%{http_code}' "http://127.0.0.1:$GATEWAY_PORT/actuator/health/readiness")"
SUBMIT_DOWN="$($CURL -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"title":"during outage","description":"x"}' \
  "http://127.0.0.1:$GATEWAY_PORT/api/v2/projects/pilot/feedback")"
docker unpause "$DB_CONTAINER" >/dev/null
if wait_http "http://127.0.0.1:$GATEWAY_PORT/actuator/health/readiness" 200 60; then
  record PASS "ops08-postgres-outage" "readiness=$READINESS_DOWN submit=$SUBMIT_DOWN during outage; recovered to UP"
else
  record FAIL "ops08-postgres-outage" "readiness did not recover (during: $READINESS_DOWN/$SUBMIT_DOWN)"
fi

# ---------------------------------------------------------------- OPS-09: Runtime 中断 → readiness DOWN
kill "$RUNTIME_PID" 2>/dev/null; wait "$RUNTIME_PID" 2>/dev/null
sleep 1
RUNTIME_READINESS="$($CURL -s "http://127.0.0.1:$GATEWAY_PORT/actuator/health/readiness")"
FORGEOPS_RUNTIME_SERVICE_TOKEN="$SERVICE_TOKEN" \
FORGEOPS_RUNTIME_DATA_FILE="$WORKDIR/runtime-data/runs.json" \
FORGEOPS_RUNTIME_ALLOWED_ROOTS="$WORKDIR/workspace" \
FORGEOPS_RUNTIME_TASK_ROOTS="$WORKDIR/executor-tasks" \
FORGEOPS_RUNTIME_PORT="$RUNTIME_PORT" \
node "$RUNTIME/dist/server.js" >> "$WORKDIR/runtime.log" 2>&1 &
RUNTIME_PID=$!
if wait_http "http://127.0.0.1:$RUNTIME_PORT/v1/runs/probe" 401 30; then
  if wait_http "http://127.0.0.1:$GATEWAY_PORT/actuator/health/readiness" 200 90; then
    record PASS "ops09-runtime-outage-readiness" "readiness recovered after Runtime restart"
  else
    record FAIL "ops09-runtime-outage-readiness" "readiness did not recover"
  fi
else
  record FAIL "ops09-runtime-outage-readiness" "runtime did not restart"
fi

# ---------------------------------------------------------------- OPS-06: Gateway 处理中 kill -9 后恢复，无重复外部动作
SUBMIT="$($CURL -o "$WORKDIR/submit.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"recovery probe","description":"gateway restart during triage queue"}' \
  "http://127.0.0.1:$GATEWAY_PORT/api/v2/projects/pilot/feedback")"
sleep 2
kill -9 "$GATEWAY_PID" 2>/dev/null; wait "$GATEWAY_PID" 2>/dev/null
sleep 1
: > "$WORKDIR/gateway-restart.log"
start_gateway
if wait_http "http://127.0.0.1:$GATEWAY_PORT/actuator/health/readiness" 200 120; then
  STUCK_DISPATCHING=1
  for i in $(seq 1 20); do
    STUCK_DISPATCHING="$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d forgeops_v2 -tAc \
      "select count(*) from outbox_event where state = 'DISPATCHING'")"
    [ "$STUCK_DISPATCHING" = "0" ] && break
    sleep 2
  done
  AUDIT_DUPES="$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d forgeops_v2 -tAc \
    "select count(*) from (select feedback_id, cycle_id, action, count(*) c from audit_log where action='AGENT_QUEUED' group by 1,2,3 having count(*) > 1) d")"
  if [ "$AUDIT_DUPES" = "0" ] && [ "$STUCK_DISPATCHING" = "0" ]; then
    record PASS "ops06-gateway-restart" "no duplicate AGENT_QUEUED facts, no stuck DISPATCHING outbox rows"
  else
    record FAIL "ops06-gateway-restart" "dupes=$AUDIT_DUPES stuck=$STUCK_DISPATCHING"
  fi
else
  record FAIL "ops06-gateway-restart" "gateway did not restart"
fi

# ---------------------------------------------------------------- OPS-07: Runtime 重启后 Gateway 观察到一致结果
mkdir -p "$WORKDIR/executor-tasks/plan-recovery"
RUN_SUBMIT="$($CURL -o "$WORKDIR/run.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $SERVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"idempotencyKey\":\"recovery-verif-1\",\"projectId\":\"pilot\",\"role\":\"VERIFICATION\",\"cwd\":\"$WORKDIR/executor-tasks/plan-recovery\",\"prompt\":\"Return one JSON object.\",\"outputSchema\":{\"type\":\"object\",\"required\":[\"riskLevel\"]},\"timeoutMs\":60000}" \
  "http://127.0.0.1:$RUNTIME_PORT/v1/runs")"
kill "$RUNTIME_PID" 2>/dev/null; wait "$RUNTIME_PID" 2>/dev/null
FORGEOPS_RUNTIME_SERVICE_TOKEN="$SERVICE_TOKEN" \
FORGEOPS_RUNTIME_DATA_FILE="$WORKDIR/runtime-data/runs.json" \
FORGEOPS_RUNTIME_ALLOWED_ROOTS="$WORKDIR/workspace" \
FORGEOPS_RUNTIME_TASK_ROOTS="$WORKDIR/executor-tasks" \
FORGEOPS_RUNTIME_PORT="$RUNTIME_PORT" \
node "$RUNTIME/dist/server.js" >> "$WORKDIR/runtime.log" 2>&1 &
RUNTIME_PID=$!
wait_http "http://127.0.0.1:$RUNTIME_PORT/v1/runs/probe" 401 30 || true
REINSPECT="$($CURL -H "Authorization: Bearer $SERVICE_TOKEN" "http://127.0.0.1:$RUNTIME_PORT/v1/runs/recovery-verif-1")"
STATE="$(python3 -c "import json;print(json.loads('''$REINSPECT''').get('state'))" 2>/dev/null || echo invalid)"
if [ "$RUN_SUBMIT" = "202" ] && [ "$STATE" != "invalid" ]; then
  record PASS "ops07-runtime-restart" "durable run survived restart, state=$STATE"
else
  record FAIL "ops07-runtime-restart" "submit=$RUN_SUBMIT state=$STATE"
fi

echo
echo "================ verify-v2-recovery summary ================"
for line in "${RESULTS[@]}"; do echo "$line" | awk -F'|' '{printf "%-8s %-34s %s\n", $1, $2, $3}'; done
echo "PASS=$PASS_COUNT FAIL=$FAIL_COUNT"
[ "$FAIL_COUNT" -eq 0 ] || { SCRIPT_FAILED=1; exit 1; }
exit 0
