#!/usr/bin/env bash
# ForgeOps 2.0 完整闭环验证（Q-06 / OPS-11）
# 真实执行：Maven 全量（surefire+failsafe，真实 PostgreSQL）、pnpm typecheck/test、
# Compose 静态校验、真实 Gateway/Runtime 进程上的 API 与边界验证。
# 任何断言失败 → FAIL；未配置外部系统（Paseo daemon/GitHub）→ 显式 SKIPPED，不计入通过。
set -uo pipefail
CURL="curl --max-time 30"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATEWAY="$ROOT/gateway/forgeops-gateway"
RUNTIME="$ROOT/runtime/forgeops-paseo-runtime"
DB_PORT="${FORGEOPS_VERIFY_DB_PORT:-55433}"
DB_CONTAINER="forgeops-v2-verify-pg"
RUNTIME_PORT=17678
GATEWAY_PORT=18095
WORKDIR="$(mktemp -d /tmp/forgeops-v2-e2e.XXXXXX)"
DB_USER=forgeops
DB_PASSWORD="verify-e2e-$(date +%s)"
TOKEN_SECRET="verify-e2e-token-secret-$(date +%s)"
SERVICE_TOKEN="verify-e2e-service-token-$(date +%s)"

PASS_COUNT=0; FAIL_COUNT=0; SKIP_COUNT=0
declare -a RESULTS

record() { # result name detail
  RESULTS+=("$1|$2|$3")
  case "$1" in
    PASS) PASS_COUNT=$((PASS_COUNT+1)); printf 'PASS  %s — %s\n' "$2" "$3" ;;
    FAIL) FAIL_COUNT=$((FAIL_COUNT+1)); printf 'FAIL  %s — %s\n' "$2" "$3" ;;
    SKIPPED) SKIP_COUNT=$((SKIP_COUNT+1)); printf 'SKIP  %s — %s\n' "$2" "$3" ;;
  esac
}

summary() {
  echo
  echo "================ verify-v2-e2e summary ================"
  for line in "${RESULTS[@]}"; do echo "$line" | awk -F'|' '{printf "%-8s %-34s %s\n", $1, $2, $3}'; done
  echo "PASS=$PASS_COUNT FAIL=$FAIL_COUNT SKIPPED=$SKIP_COUNT"
}

cleanup() {
  if [ "${SCRIPT_FAILED:-0}" = "1" ]; then
    mkdir -p "/tmp/verify-v2-logs-e2e" && cp -R "$WORKDIR"/. "/tmp/verify-v2-logs-e2e/" 2>/dev/null || true
  fi
  [ -n "${GATEWAY_PID:-}" ] && kill "$GATEWAY_PID" 2>/dev/null
  [ -n "${RUNTIME_PID:-}" ] && kill "$RUNTIME_PID" 2>/dev/null
  docker rm -f "$DB_CONTAINER" >/dev/null 2>&1
  rm -rf "$WORKDIR"
}
trap cleanup EXIT

wait_http() { # url expected_status timeout_s [curl_extra...]
  local url="$1" expected="$2" timeout_s="$3"; shift 3
  local waited=0 status
  while [ "$waited" -lt "$timeout_s" ]; do
    status="$($CURL -o /dev/null -w '%{http_code}' "$@" "$url" 2>/dev/null || echo 000)"
    [ "$status" = "$expected" ] && return 0
    sleep 2; waited=$((waited+2))
  done
  return 1
}

# ---------------------------------------------------------------- Preconditions
if ! docker info >/dev/null 2>&1; then
  echo "docker is required for the isolated PostgreSQL"; exit 2
fi
JAVA21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
if [ -z "$JAVA21_HOME" ]; then
  echo "JDK 21 is required"; exit 2
fi
export JAVA_HOME="$JAVA21_HOME"

# ---------------------------------------------------------------- 1. Maven 全量（真实 PG）
docker rm -f "$DB_CONTAINER" >/dev/null 2>&1
docker run -d --name "$DB_CONTAINER" -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -e POSTGRES_DB=forgeops_v2 -p "127.0.0.1:$DB_PORT:5432" postgres:16-alpine >/dev/null
for i in $(seq 1 30); do
  docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" >/dev/null 2>&1 && break
  sleep 1
done

export FORGEOPS_DB_URL="jdbc:postgresql://127.0.0.1:$DB_PORT/forgeops_v2" \
       FORGEOPS_DB_USER="$DB_USER" FORGEOPS_DB_PASSWORD="$DB_PASSWORD"
if (cd "$GATEWAY" && mvn verify > "$WORKDIR/mvn-verify.log" 2>&1); then
  UNIT_TESTS="$(grep -h "Tests run:" "$GATEWAY/target/surefire-reports"/*.txt 2>/dev/null | awk -F'[:,]' '{t+=$2;f+=$4;e+=$6;s+=$8} END {print t" tests, "f" failures, "e" errors, "s" skipped"}')"
  IT_TESTS="$(grep -h "Tests run:" "$GATEWAY/target/failsafe-reports"/*.txt 2>/dev/null | awk -F'[:,]' '{t+=$2;f+=$4;e+=$6;s+=$8} END {print t" tests, "f" failures, "e" errors, "s" skipped"}')"
  if echo "$UNIT_TESTS$IT_TESTS" | grep -Ev 'failures=0|0 failures' | grep -qv '0 failures'; then :; fi
  record PASS "mvn-verify" "surefire[$UNIT_TESTS] failsafe[$IT_TESTS]"
else
  record FAIL "mvn-verify" "see $WORKDIR/mvn-verify.log"
  echo "Maven verification failed; aborting further E2E steps."
  grep -E "Tests run:|FAILURE!|ERROR.*java|Caused by:" "$WORKDIR/mvn-verify.log" | tail -20
  summary; exit 1
fi

# ---------------------------------------------------------------- 2. pnpm 全量
if (cd "$ROOT" && pnpm -r typecheck > "$WORKDIR/pnpm-typecheck.log" 2>&1 \
  && pnpm -r test > "$WORKDIR/pnpm-test.log" 2>&1); then
  record PASS "pnpm-typecheck-test" "all workspace packages"
else
  record FAIL "pnpm-typecheck-test" "see $WORKDIR/pnpm-*.log"
fi

# ---------------------------------------------------------------- 3. Compose 静态校验（OPS-01）
cat > "$WORKDIR/compose.env" <<EOF
FORGEOPS_DB_USER=$DB_USER
FORGEOPS_DB_PASSWORD=$DB_PASSWORD
FORGEOPS_DB_NAME=forgeops_v2
FORGEOPS_V2_SECURITY_TOKEN_SECRET=$TOKEN_SECRET
FORGEOPS_RUNTIME_SERVICE_TOKEN=$SERVICE_TOKEN
EOF
if docker compose -f "$ROOT/deploy/docker-compose/forgeops-v2.yml" --env-file "$WORKDIR/compose.env" config -q 2>"$WORKDIR/compose-config.err"; then
  record PASS "compose-config" "forgeops-v2.yml parses with explicit secrets"
else
  record FAIL "compose-config" "$(head -c 200 "$WORKDIR/compose-config.err")"
fi

# ---------------------------------------------------------------- 4. 真实进程栈
mkdir -p "$WORKDIR/registry" "$WORKDIR/workspace/pilot-repo" "$WORKDIR/executor-tasks" "$WORKDIR/runtime-data"
git -C "$WORKDIR/workspace/pilot-repo" init -q 2>/dev/null
echo "fixture" > "$WORKDIR/workspace/pilot-repo/README.md"
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
FORGEOPS_RUNTIME_RUN_TIMEOUT_MS=60000 \
node "$RUNTIME/dist/server.js" > "$WORKDIR/runtime.log" 2>&1 &
RUNTIME_PID=$!

if wait_http "http://127.0.0.1:$RUNTIME_PORT/v1/runs/probe" 401 30; then
  record PASS "runtime-service-auth" "unauthenticated Runtime call rejected with 401"
else
  record FAIL "runtime-service-auth" "runtime did not start; see $WORKDIR/runtime.log"
fi

java -jar "$(ls "$GATEWAY"/target/*.jar | head -1)" \
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
  > "$WORKDIR/gateway.log" 2>&1 &
GATEWAY_PID=$!

if wait_http "http://127.0.0.1:$GATEWAY_PORT/actuator/health/readiness" 200 90; then
  record PASS "gateway-readiness" "health/readiness UP against real PostgreSQL and Runtime"
else
  record FAIL "gateway-readiness" "gateway did not become ready; see $WORKDIR/gateway.log"
fi

# ---------------------------------------------------------------- 5. 真实 API 旅程（浏览器面）
mint_token() { # subject project scopes
  python3 - "$TOKEN_SECRET" "$1" "$2" "$3" <<'PY'
import base64, hmac, hashlib, sys, time
secret, subject, project, scopes = sys.argv[1:5]
payload = "v1|%s|%s|%s|%d" % (subject, project, scopes, int(time.time()) + 600)
encoded = base64.urlsafe_b64encode(payload.encode()).rstrip(b"=").decode()
sig = base64.urlsafe_b64encode(hmac.new(secret.encode(), encoded.encode(), hashlib.sha256).digest()).rstrip(b"=").decode()
print(encoded + "." + sig)
PY
}
TOKEN="$(mint_token verifier pilot feedback:write,feedback:read)"
SUBMIT="$($CURL -o "$WORKDIR/submit.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"verify e2e","description":"script driven natural feedback","browserContext":{"url":"https://pilot.verify.local/home"}}' \
  "http://127.0.0.1:$GATEWAY_PORT/api/v2/projects/pilot/feedback")"
if [ "$SUBMIT" = "201" ] && python3 -c "import json;d=json.load(open('$WORKDIR/submit.json'));assert d['state'] in ('OPEN','IN_PROGRESS')" 2>/dev/null; then
  FEEDBACK_ID="$(python3 -c "import json;print(json.load(open('$WORKDIR/submit.json'))['id'])")"
  record PASS "feedback-submit" "201 created, id=$FEEDBACK_ID"
else
  record FAIL "feedback-submit" "status=$SUBMIT body=$(head -c 120 "$WORKDIR/submit.json")"
fi

MINE_STATUS="$($CURL -o "$WORKDIR/mine.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:$GATEWAY_PORT/api/v2/projects/pilot/feedback/mine")"
[ "$MINE_STATUS" = "200" ] && record PASS "feedback-mine" "200 with $(python3 -c "import json;print(len(json.load(open('$WORKDIR/mine.json'))))" 2>/dev/null || echo '?') items" \
  || record FAIL "feedback-mine" "status=$MINE_STATUS"

TAMPERED="$($CURL -o /dev/null -w '%{http_code}' -H "Authorization: Bearer ${TOKEN}x" \
  "http://127.0.0.1:$GATEWAY_PORT/api/v2/projects/pilot/feedback/mine")"
[ "$TAMPERED" = "401" ] && record PASS "token-tamper-rejected" "401 invalid credential" || record FAIL "token-tamper-rejected" "status=$TAMPERED"

CROSS="$($CURL -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $(mint_token intruder other-project feedback:read)" \
  "http://127.0.0.1:$GATEWAY_PORT/api/v2/projects/pilot/feedback/mine")"
[ "$CROSS" = "403" ] && record PASS "cross-project-rejected" "403" || record FAIL "cross-project-rejected" "status=$CROSS"

# ---------------------------------------------------------------- 6. Runtime 边界（验证角色真实提交/查询/取消）
mkdir -p "$WORKDIR/executor-tasks/plan-e2e"
RUN_REQ="{\"idempotencyKey\":\"e2e-verif-1\",\"projectId\":\"pilot\",\"role\":\"VERIFICATION\",\"cwd\":\"$WORKDIR/executor-tasks/plan-e2e\",\"prompt\":\"Return one JSON object per schema.\",\"outputSchema\":{\"type\":\"object\",\"required\":[\"riskLevel\"]},\"timeoutMs\":30000}"
RUN_SUBMIT="$($CURL -o "$WORKDIR/run.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $SERVICE_TOKEN" \
  -H "Content-Type: application/json" -d "$RUN_REQ" "http://127.0.0.1:$RUNTIME_PORT/v1/runs")"
[ "$RUN_SUBMIT" = "202" ] && record PASS "verification-role-submit" "202 accepted in isolated task dir" \
  || record FAIL "verification-role-submit" "status=$RUN_SUBMIT $(head -c 120 "$WORKDIR/run.json")"

REPO_CWD="$(python3 -c "import json;print(json.dumps(json.loads('''$RUN_REQ''') | {'idempotencyKey':'e2e-verif-2','cwd':'$WORKDIR/workspace/pilot-repo'}))")"
REPO_SUBMIT="$($CURL -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $SERVICE_TOKEN" \
  -H "Content-Type: application/json" -d "$REPO_CWD" "http://127.0.0.1:$RUNTIME_PORT/v1/runs")"
[ "$REPO_SUBMIT" = "400" ] && record PASS "verification-role-worktree-refused" "repository cwd rejected for VERIFICATION role" \
  || record FAIL "verification-role-worktree-refused" "status=$REPO_SUBMIT"

CANCEL="$($CURL -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $SERVICE_TOKEN" \
  "http://127.0.0.1:$RUNTIME_PORT/v1/runs/e2e-verif-1")"
[ "$CANCEL" = "200" ] && record PASS "verification-role-cancel" "cancel returned a snapshot" \
  || record FAIL "verification-role-cancel" "status=$CANCEL"

# ---------------------------------------------------------------- 7. Paseo 真实通道（可选：daemon 在线才执行）
PASEO_URL="${FORGEOPS_PASEO_URL:-ws://127.0.0.1:6767/ws}"
PASEO_HOST="$(echo "$PASEO_URL" | sed -E 's#.*://([^:/]+).*#\1#')"
PASEO_PORT="$(echo "$PASEO_URL" | sed -nE 's#.*:([0-9]+).*#\1#p')"
PASEO_PORT="${PASEO_PORT:-6767}"
if nc -z -w 2 "$PASEO_HOST" "$PASEO_PORT" 2>/dev/null; then
  record SKIPPED "paseo-real-channel" "daemon detected at $PASEO_URL but agent model credentials are not scripted; see AGT/VER evidence"
else
  record SKIPPED "paseo-real-channel" "no Paseo daemon at $PASEO_URL (ENV_BLOCKED: not counted as PASS)"
fi

# ---------------------------------------------------------------- Summary
summary
[ "$FAIL_COUNT" -eq 0 ] || { SCRIPT_FAILED=1; exit 1; }
# SKIPPED 不计入通过：存在 SKIPPED 时以 3 退出，报告必须显式标注
[ "$SKIP_COUNT" -eq 0 ] || exit 3
exit 0
