#!/usr/bin/env bash
# ============================================================
# AI 加工链路一键验证脚本（智谱 GLM）
# 用法:
#   ./verify_ai.sh <智谱API_KEY>
#   或 export UMS_AI_API_KEY=... 后直接 ./verify_ai.sh
#
# 做什么：
#   1. 停止现有 8080 服务，带 UMS_AI_API_KEY 重启
#   2. 调补加工接口（所有无摘要记录，最多 100 条）
#   3. 轮询等待 AI 加工完成
#   4. 展示 ai_processing_log 统计、摘要样例、分类分布、队列状态
#   5. 全部成功退出码 0，否则非 0
#
# 依赖：curl / mysql / python3（macOS 自带）；JDK17 + Maven 已安装
# 日志：/tmp/ums-main-ai.log（服务启动日志，排查用）
# ============================================================
set -euo pipefail

KEY="${1:-${UMS_AI_API_KEY:-}}"
if [[ -z "$KEY" ]]; then
  echo "用法: $0 <智谱API_KEY>   （或先 export UMS_AI_API_KEY=...）"
  exit 1
fi

JAVA_HOME_DIR=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
SVC_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG=/tmp/ums-main-ai.log
export MYSQL_PWD="${UMS_MYSQL_PASSWORD:-root1234}"
MQ_AUTH="${UMS_MQ_USER:-ums_admin}:${UMS_MQ_PASSWORD:-1eAODIRLqpRazsxV}"

mysql_q() { mysql -uroot cp_news -N -B -e "$1" 2>/dev/null; }

echo "==> 1/5 停止现有 Java 服务（8080）"
lsof -ti :8080 | xargs kill 2>/dev/null || true
sleep 2

echo "==> 2/5 带 UMS_AI_API_KEY 启动服务（日志: ${LOG}）"
(cd "$SVC_DIR" && UMS_AI_API_KEY="$KEY" JAVA_HOME="$JAVA_HOME_DIR" \
  nohup mvn -q spring-boot:run > "$LOG" 2>&1 &)

echo -n "    等待健康检查"
READY=0
for i in $(seq 1 90); do
  if curl -sf localhost:8080/actuator/health 2>/dev/null | grep -q UP; then READY=1; break; fi
  echo -n "."; sleep 1
done
echo
if [[ $READY -ne 1 ]]; then
  echo "    ❌ 服务启动超时（90s）。排查: tail -50 $LOG"
  exit 1
fi
echo "    ✅ 服务就绪"

echo "==> 3/5 触发补加工（pendingOnly，全部无摘要记录）"
curl -s -X POST 'localhost:8080/internal/ai/reprocess?pendingOnly=true'
echo

echo -n "    等待 AI 加工完成"
TIMEOUT=300   # 19 条 × 2 次调用，正常 1~3 分钟
ELAPSED=0
PENDING=-1
STABLE=0
while (( ELAPSED < TIMEOUT )); do
  NEW_PENDING=$(mysql_q "SELECT COUNT(*) FROM cp_news WHERE summary IS NULL;")
  DLQ=$(curl -s -u "$MQ_AUTH" 'http://127.0.0.1:15672/api/queues/%2F/ums.content.parsed.dlq' \
        | python3 -c 'import json,sys; print(json.load(sys.stdin)["messages"])' 2>/dev/null || echo "?")
  if [[ "$NEW_PENDING" == "0" ]]; then PENDING=0; break; fi
  # DLQ 有消息且 pending 不再下降 → 判定失败跳出
  if [[ "$DLQ" != "0" && "$DLQ" != "?" && "$NEW_PENDING" == "$PENDING" ]]; then
    STABLE=$((STABLE + 1))
    if (( STABLE >= 3 )); then break; fi
  else
    STABLE=0
  fi
  PENDING=$NEW_PENDING
  echo -n "."; sleep 5; ELAPSED=$((ELAPSED + 5))
done
echo

echo "==> 4/5 结果统计"
echo "---- ai_processing_log（operation × status）----"
mysql_q "SELECT operation, status, COUNT(*) AS cnt, MAX(latency_ms) AS max_ms \
FROM ai_processing_log GROUP BY operation, status ORDER BY operation, status;"
echo
echo "---- 失败原因（如有）----"
FAILED_CNT=$(mysql_q "SELECT COUNT(*) FROM ai_processing_log WHERE status='FAILED';")
if [[ "$FAILED_CNT" != "0" ]]; then
  mysql_q "SELECT operation, LEFT(error_summary, 120) FROM ai_processing_log WHERE status='FAILED' LIMIT 5;"
fi
echo "---- 分类分布 ----"
mysql_q "SELECT category, COUNT(*) AS cnt FROM cp_news WHERE category IS NOT NULL GROUP BY category;"
echo
echo "---- 队列状态 ----"
curl -s -u "$MQ_AUTH" 'http://127.0.0.1:15672/api/queues/%2F' \
  | python3 -c 'import json,sys
for q in json.load(sys.stdin):
    if "parsed" in q["name"]: print(f"  {q[\"name\"]}: {q[\"messages\"]}")'
echo

echo "==> 5/5 摘要样例（三段式中文摘要）"
mysql cp_news --default-character-set=utf8mb4 -e \
  "SELECT LEFT(title, 30) AS 标题, LEFT(summary, 260) AS 摘要 FROM cp_news WHERE summary IS NOT NULL LIMIT 2;" 2>/dev/null

# ---- 判定 ----
FINAL_PENDING=$(mysql_q "SELECT COUNT(*) FROM cp_news WHERE summary IS NULL;")
SUCCESS_CNT=$(mysql_q "SELECT COUNT(*) FROM ai_processing_log WHERE status='SUCCESS';")
FINAL_DLQ=$(curl -s -u "$MQ_AUTH" 'http://127.0.0.1:15672/api/queues/%2F/ums.content.parsed.dlq' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["messages"])' 2>/dev/null || echo "?")

echo "============================================================"
if [[ "$SUCCESS_CNT" != "0" && "$FINAL_PENDING" == "0" && "$FINAL_DLQ" == "0" ]]; then
  echo "✅ AI 加工链路验证通过：SUCCESS×${SUCCESS_CNT}，无待加工，无死信"
  exit 0
elif [[ "$SUCCESS_CNT" != "0" ]]; then
  echo "⚠️ 部分成功：SUCCESS×${SUCCESS_CNT}，待加工×${FINAL_PENDING}，死信×${FINAL_DLQ}"
  echo "   重放死信: services/ums-main-java/scripts/replay_dlq.py"
  exit 2
else
  echo "❌ 加工未成功（待加工×${FINAL_PENDING}，死信×${FINAL_DLQ}）。排查: tail -50 ${LOG}"
  exit 1
fi
