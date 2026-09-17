#!/usr/bin/env bash
# 真实 Paseo 通道验证（VER-05/VER-06、AGT-04/08/09 的真实 daemon 部分）
# 前置：Paseo daemon（>=0.8.0，Codex provider）监听 FORGEOPS_PASEO_URL（默认 ws://127.0.0.1:6767/ws）。
# 无 daemon 时显式 ENV_BLOCKED 退出 3，不计入通过。
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUNTIME="$ROOT/runtime/forgeops-paseo-runtime"
WORKDIR="$(mktemp -d "$HOME/.forgeops-verify/paseo-real.XXXXXX")"
RUNTIME_PORT=17682
SERVICE_TOKEN="paseo-real-$(date +%s)"
PASEO_URL="${FORGEOPS_PASEO_URL:-ws://127.0.0.1:6767/ws}"
PASEO_HOST="$(echo "$PASEO_URL" | sed -E 's#.*://([^:/]+).*#\1#')"
PASEO_PORT="$(echo "$PASEO_URL" | sed -nE 's#.*:([0-9]+).*#\1#p')"; PASEO_PORT="${PASEO_PORT:-6767}"

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
summary() {
  echo
  echo "================ verify-v2-paseo-real summary ================"
  for line in "${RESULTS[@]}"; do echo "$line" | awk -F'|' '{printf "%-8s %-30s %s\n", $1, $2, $3}'; done
  echo "PASS=$PASS_COUNT FAIL=$FAIL_COUNT SKIPPED=$SKIP_COUNT"
}
cleanup() {
  [ -n "${RUNTIME_PID:-}" ] && kill "$RUNTIME_PID" 2>/dev/null
  rm -rf "$WORKDIR"
}
trap cleanup EXIT

if ! nc -z -w 3 "$PASEO_HOST" "$PASEO_PORT" 2>/dev/null; then
  record SKIPPED "daemon-reachable" "no Paseo daemon at $PASEO_URL (ENV_BLOCKED)"
  summary; exit 3
fi

mkdir -p "$WORKDIR/tasks/plan-1" "$WORKDIR/ws/repo"
(cd "$WORKDIR/ws/repo" && git init -q . && echo fixture > README.md && git add README.md \
  && git -c user.email=verify@local -c user.name=verify commit -qm init) >/dev/null 2>&1

(cd "$RUNTIME" && pnpm build >/dev/null 2>&1)
FORGEOPS_RUNTIME_SERVICE_TOKEN="$SERVICE_TOKEN" \
FORGEOPS_RUNTIME_DATA_FILE="$WORKDIR/runs.json" \
FORGEOPS_RUNTIME_ALLOWED_ROOTS="$WORKDIR" \
FORGEOPS_RUNTIME_TASK_ROOTS="$WORKDIR/tasks" \
FORGEOPS_RUNTIME_PORT="$RUNTIME_PORT" \
FORGEOPS_RUNTIME_RUN_TIMEOUT_MS=300000 \
FORGEOPS_PASEO_URL="$PASEO_URL" \
node "$RUNTIME/dist/server.js" > "$WORKDIR/runtime.log" 2>&1 &
RUNTIME_PID=$!
PROBE=000
for i in $(seq 1 15); do
  PROBE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:$RUNTIME_PORT/v1/runs/probe 2>/dev/null || echo 000)"
  [ "$PROBE" = 401 ] && break; sleep 2
done
[ "$PROBE" = 401 ] && record PASS "runtime-up" "service-token challenge enforced" \
  || { record FAIL "runtime-up" "probe=$PROBE see $WORKDIR/runtime.log"; summary; exit 1; }

submit() { # payload-file
  curl -s --max-time 240 -X POST -H "Authorization: Bearer $SERVICE_TOKEN" \
    -H "Content-Type: application/json" -d @"$1" http://127.0.0.1:$RUNTIME_PORT/v1/runs
}

# ---------------------------------------------------------------- VER-05 same key ≥10 concurrent
cat > "$WORKDIR/ver05.json" <<EOF
{"idempotencyKey":"ver05-$(date +%s)","projectId":"pilot","role":"VERIFICATION","cwd":"$WORKDIR/tasks/plan-1",
 "prompt":"Reply with exactly one JSON object {\"riskLevel\":\"LOW\"}.",
 "outputSchema":{"type":"object","required":["riskLevel"],"properties":{"riskLevel":{"type":"string","enum":["LOW","MEDIUM","HIGH","CRITICAL"]}},"additionalProperties":false},
 "timeoutMs":240000}
EOF
PIDS=""
for i in $(seq 1 10); do submit "$WORKDIR/ver05.json" > "$WORKDIR/ver05-$i.json" & PIDS="$PIDS $!"; done
for pid in $PIDS; do wait "$pid"; done
python3 - "$WORKDIR" <<'PY'
import json, os, sys, glob
work = sys.argv[1]
snaps = []
for f in sorted(glob.glob(f'{work}/ver05-*.json')):
    try: snaps.append(json.load(open(f)))
    except Exception: pass
ids = {s.get('providerRunId') for s in snaps}
states = {s.get('state') for s in snaps}
ok = len(snaps) == 10 and len(ids) == 1 and None not in ids and 'RUNNING' in states
print('VER05_RESULT', 'PASS' if ok else 'FAIL', 'responses=', len(snaps), 'ids=', ids, 'states=', states)
PY
VER05=$(python3 - "$WORKDIR" <<'PY'
import json, os, sys, glob
work = sys.argv[1]
snaps = [json.load(open(f)) for f in sorted(glob.glob(f'{work}/ver05-*.json'))]
ids = {s.get('providerRunId') for s in snaps}
print('PASS' if len(snaps) == 10 and len(ids) == 1 and None not in ids else 'FAIL')
PY
)
[ "$VER05" = PASS ] && record PASS "ver05-real-idempotency" "10 concurrent same-key submits → one provider run" \
  || record FAIL "ver05-real-idempotency" "see $WORKDIR/ver05-*.json"

# provider-side single instance
PROVIDER_ID=$(python3 -c "import json,glob;print(json.load(open(sorted(glob.glob('$WORKDIR/ver05-*.json'))[0]))['providerRunId'])" 2>/dev/null)
if [ -n "$PROVIDER_ID" ]; then
  curl -s --max-time 60 -X POST -H "Authorization: Bearer $SERVICE_TOKEN" "http://127.0.0.1:$RUNTIME_PORT/v1/runs/$(python3 -c "import json,glob;print(json.load(open(sorted(glob.glob('$WORKDIR/ver05-*.json'))[0]))['idempotencyKey'])")" >/dev/null
fi

# ---------------------------------------------------------------- VER-06 cancel on the real channel
cat > "$WORKDIR/ver06.json" <<EOF
{"idempotencyKey":"ver06-cancel-$(date +%s)","projectId":"pilot","role":"VERIFICATION","cwd":"$WORKDIR/tasks/plan-1",
 "prompt":"Write a 800-word essay about the sea, then reply one JSON object {\"riskLevel\":\"LOW\"}.",
 "outputSchema":{"type":"object","required":["riskLevel"],"properties":{"riskLevel":{"type":"string","enum":["LOW","MEDIUM","HIGH","CRITICAL"]}},"additionalProperties":false},
 "timeoutMs":240000}
EOF
CANCEL_KEY=$(python3 -c "import json;print(json.load(open('$WORKDIR/ver06.json'))['idempotencyKey'])")
SUBMIT_STATE=$(submit "$WORKDIR/ver06.json" | python3 -c "import json,sys;print(json.load(sys.stdin)['state'])" 2>/dev/null)
sleep 3
CANCEL_STATE=$(curl -s --max-time 60 -X POST -H "Authorization: Bearer $SERVICE_TOKEN" \
  "http://127.0.0.1:$RUNTIME_PORT/v1/runs/$CANCEL_KEY" | python3 -c "import json,sys;print(json.load(sys.stdin)['state'])" 2>/dev/null)
[ "$CANCEL_STATE" = "CANCELLED" ] && record PASS "ver06-real-cancel" "provider run stopped, snapshot CANCELLED (submit=$SUBMIT_STATE)" \
  || record FAIL "ver06-real-cancel" "submit=$SUBMIT_STATE cancel=$CANCEL_STATE"

# ---------------------------------------------------------------- AGT-09 inspect stays bounded while daemon is unreachable
# daemon lifecycle is environment-managed: stopping it here is only done when explicitly allowed.
if [ "${FORGEOPS_PASEO_MANAGE:-false}" = "true" ]; then
  PASEO_CLI="${FORGEOPS_PASEO_CLI:-}"
  if [ -n "$PASEO_CLI" ]; then
    (cd "$WORKDIR" && "$PASEO_CLI" daemon stop >/dev/null 2>&1)
    T0=$(date +%s)
    OUTAGE_STATE=$(curl -s --max-time 30 -H "Authorization: Bearer $SERVICE_TOKEN" \
      "http://127.0.0.1:$RUNTIME_PORT/v1/runs/$CANCEL_KEY" | python3 -c "import json,sys;print(json.load(sys.stdin)['state'])" 2>/dev/null)
    TOOK=$(( $(date +%s) - T0 ))
    (cd "$WORKDIR" && "$PASEO_CLI" daemon start $( [ -n "${FORGEOPS_PASEO_HOME:-}" ] && echo --home "$FORGEOPS_PASEO_HOME" ) --port "$PASEO_PORT" --no-relay --no-mcp --no-web-ui >/dev/null 2>&1)
    if [ -n "$OUTAGE_STATE" ] && [ "$TOOK" -le 30 ]; then
      record PASS "agt09-outage-inspect-bounded" "inspect during outage returned in ${TOOK}s without hanging"
    else
      record FAIL "agt09-outage-inspect-bounded" "state=$OUTAGE_STATE took=${TOOK}s"
    fi
  else
    record SKIPPED "agt09-outage-inspect-bounded" "FORGEOPS_PASEO_CLI not set"
  fi
else
  record SKIPPED "agt09-outage-inspect-bounded" "daemon lifecycle not managed by script (set FORGEOPS_PASEO_MANAGE=true)"
fi

summary
[ "$FAIL_COUNT" -eq 0 ] || { SCRIPT_FAILED=1; exit 1; }
[ "$SKIP_COUNT" -eq 0 ] || exit 3
exit 0
