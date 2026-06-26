#!/bin/sh
# ============================================================
# diagnose.sh smoke test
# ============================================================
# 用法：sh scripts/diagnose-smoke-test.sh
# 流程：
#   1. 准备临时项目目录与最小日志样本
#   2. 执行 diagnose.sh
#   3. 验证 diagnostics/latest/summary.md 与必要证据文件存在
#   4. 验证敏感字段已被脱敏
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TMP_BASE="$(mktemp -d -t diagnose-smoke.XXXXXX)"

cleanup() {
    rm -rf "$TMP_BASE"
}
trap cleanup EXIT

mkdir -p "$TMP_BASE/logs" "$TMP_BASE/scripts"
cp "$PROJECT_ROOT/scripts/diagnose.sh" "$TMP_BASE/scripts/diagnose.sh"
chmod +x "$TMP_BASE/scripts/diagnose.sh"

# 模拟日志（包含一段敏感字段）
cat > "$TMP_BASE/logs/app.log" <<'EOF'
2026-06-27 00:00:00 INFO 启动
2026-06-27 00:00:01 ERROR 调用失败 Authorization: Bearer SUPER-SECRET-TOKEN-123
{"password":"plaintextpwd","other":"safe"}
EOF
cp "$TMP_BASE/logs/app.log" "$TMP_BASE/logs/error.log"

cd "$TMP_BASE"
LOG_PATH="$TMP_BASE/logs" sh scripts/diagnose.sh --output "$TMP_BASE/diagnostics/latest" --max-lines 100
rc=$?

if [ $rc -ne 0 ] && [ $rc -ne 2 ]; then
    echo "[FAIL] diagnose.sh 返回非预期退出码：$rc" >&2
    exit 1
fi

[ -f "$TMP_BASE/diagnostics/latest/summary.md" ] || { echo "[FAIL] summary.md 不存在" >&2; exit 1; }
[ -f "$TMP_BASE/diagnostics/latest/logs/error.log" ] || { echo "[FAIL] logs/error.log 不存在" >&2; exit 1; }
[ -f "$TMP_BASE/diagnostics/latest/environment.txt" ] || { echo "[FAIL] environment.txt 不存在" >&2; exit 1; }

# 验证脱敏：敏感字段不应出现在 logs 摘录
if grep -q "SUPER-SECRET-TOKEN-123" "$TMP_BASE/diagnostics/latest/logs/error.log"; then
    echo "[FAIL] 原始 Token 未脱敏" >&2
    exit 1
fi
if grep -q "plaintextpwd" "$TMP_BASE/diagnostics/latest/logs/error.log"; then
    echo "[FAIL] 原始 password 未脱敏" >&2
    exit 1
fi

echo "[PASS] diagnose.sh smoke test 通过"
