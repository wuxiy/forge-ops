#!/usr/bin/env bash
# 模拟现有 CI/CD 对 ForgeOps Gateway 的三个回调（Phase 3 验收用）
# 用法: ./scripts/simulate-cicd-callbacks.sh <feedbackId 如 FB-1001> [gatewayUrl] [secret]
set -euo pipefail

FEEDBACK_ID="${1:?用法: $0 <FB-xxxx> [gatewayUrl] [secret]}"
GATEWAY="${2:-http://localhost:8080}"
SECRET="${3:-forgeops-dev-secret}"
EVENT_BASE="sim-$(date +%s)"

post() {
  local source="$1" body="$2"
  curl -sS -X POST "$GATEWAY/api/v1/callback/$source" \
    -H 'Content-Type: application/json' \
    -H "X-ForgeOps-Callback-Secret: $SECRET" \
    -d "$body"
  echo
}

echo "== 1/3 git: PR merged（人工 merge 后触发）=="
post git "{\"projectId\":\"demo-app\",\"feedbackId\":\"$FEEDBACK_ID\",\"externalEventId\":\"$EVENT_BASE-git\",\"eventType\":\"PR_MERGED\",\"actor\":\"wuxi\",\"actorType\":\"USER\",\"prUrl\":\"https://github.com/wuxiy/forge-ops/pull/1\",\"commitSha\":\"$(git rev-parse --short HEAD 2>/dev/null || echo sim)\",\"status\":\"MERGED\"}"

echo "== 2/3 ci: 构建成功 =="
post ci "{\"projectId\":\"demo-app\",\"feedbackId\":\"$FEEDBACK_ID\",\"externalEventId\":\"$EVENT_BASE-ci\",\"eventType\":\"BUILD_SUCCESS\",\"pipelineId\":\"sim-12831\",\"status\":\"SUCCESS\"}"

echo "== 3/3 deployment: 发布测试环境成功 =="
post deployment "{\"projectId\":\"demo-app\",\"feedbackId\":\"$FEEDBACK_ID\",\"externalEventId\":\"$EVENT_BASE-deploy\",\"eventType\":\"DEPLOY_SUCCESS\",\"environment\":\"test\",\"version\":\"0.1.1-test\",\"commitSha\":\"$(git rev-parse --short HEAD 2>/dev/null || echo sim)\",\"status\":\"SUCCESS\"}"

echo "完成。此时反馈应进入 WAITING_VERIFY（待验证），SDK『我的反馈』可点 [验证通过]/[仍有问题]。"
