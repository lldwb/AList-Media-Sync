#!/bin/sh
# ============================================================
# AList-Media-Sync 一键诊断脚本（Linux / Docker）
# ============================================================
# 用法：
#   ./diagnose.sh                                       # 默认输出至 ./diagnostics/latest
#   ./diagnose.sh --output /tmp/diag --max-lines 500    # 自定义输出目录与最多日志行数
#   ./diagnose.sh --trace-id manual-test-001            # 指定优先收集的 traceId
#
# 退出码：
#   0  成功，诊断包已生成
#   2  部分信息不可用但摘要已生成
#   1  生成失败
# ============================================================

set -e

# ---- 路径解析 ----
SCRIPT_PATH="$(readlink -f "$0" 2>/dev/null || realpath "$0" 2>/dev/null || echo "$0")"
SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"
# 诊断脚本支持两种位置：项目根目录 / 一体化启动包根目录
if [ -f "$SCRIPT_DIR/../pom.xml" ]; then
    PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
else
    PROJECT_ROOT="$SCRIPT_DIR"
fi
cd "$PROJECT_ROOT"

# ---- 默认参数 ----
OUTPUT_DIR="./diagnostics/latest"
TRACE_ID=""
MAX_LINES=2000

# ---- 解析命令行参数 ----
while [ "$#" -gt 0 ]; do
    case "$1" in
        --output)
            OUTPUT_DIR="$2"; shift 2 ;;
        --trace-id)
            TRACE_ID="$2"; shift 2 ;;
        --max-lines)
            MAX_LINES="$2"; shift 2 ;;
        -h|--help)
            sed -n '1,20p' "$0"; exit 0 ;;
        *)
            echo "[警告] 未知参数：$1" >&2; shift ;;
    esac
done

LOG_DIR="${LOG_PATH:-./logs}"
DATA_DIR="${DATA_DIR:-./data}"

# ---- 颜色输出 ----
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

print_info() { echo "${GREEN}[信息]${NC} $1"; }
print_warn() { echo "${YELLOW}[警告]${NC} $1" >&2; }
print_error() { echo "${RED}[错误]${NC} $1" >&2; }

# ---- 生成自身 traceId（若未指定则按时间生成） ----
if [ -z "$TRACE_ID" ]; then
    TRACE_ID="diag-$(date +%Y%m%d%H%M%S)-$(printf '%04x' $$)"
fi

print_info "诊断 traceId：$TRACE_ID"
print_info "输出目录：$OUTPUT_DIR"
print_info "日志目录：$LOG_DIR"

# ---- 准备临时目录 ----
mkdir -p "$(dirname "$OUTPUT_DIR")"
TMP_DIR="$(dirname "$OUTPUT_DIR")/tmp-$$"
mkdir -p "$TMP_DIR/logs" "$TMP_DIR/config"

MISSING=""
add_missing() {
    if [ -z "$MISSING" ]; then
        MISSING="$1"
    else
        MISSING="$MISSING\n- $1"
    fi
}

# ---- 收集日志摘录 ----
copy_log_tail() {
    src="$1"; dst="$2"
    if [ -r "$src" ]; then
        tail -n "$MAX_LINES" "$src" > "$dst" 2>/dev/null || add_missing "复制日志失败：$src"
        # 文本级敏感信息脱敏：Authorization/Token/password 行
        sed -i \
            -e 's/\(Authorization:\s*\)[^[:space:]]*/\1***REDACTED***/Ig' \
            -e 's/\(Cookie:\s*\)[^[:space:]]*/\1***REDACTED***/Ig' \
            -e 's/\("\(password\|token\|secret\|key\|cookie\|authorization\)[^"]*"\s*:\s*"\)[^"]*"/\1***REDACTED***"/Ig' \
            -e 's/\([?&]\(password\|token\|secret\|key\|cookie\|authorization\)[^=]*=\)[^&[:space:]]*/\1***REDACTED***/Ig' \
            "$dst" 2>/dev/null || true
    else
        add_missing "$src 不存在或不可读"
    fi
}

copy_log_tail "$LOG_DIR/error.log" "$TMP_DIR/logs/error.log"
copy_log_tail "$LOG_DIR/app.log" "$TMP_DIR/logs/app.log"

# ---- 收集脱敏环境变量 ----
ENV_FILE="$TMP_DIR/environment.txt"
{
    echo "诊断 traceId: $TRACE_ID"
    echo "生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "部署形态: $( [ -f /.dockerenv ] && echo Docker || ([ -x "$PROJECT_ROOT/runtime/bin/java" ] && echo 一体化启动包 || echo 本地开发) )"
    echo "操作系统: $(uname -srm)"
    echo "时区: $(date '+%Z')"
    echo "CPU 核心: $(nproc 2>/dev/null || echo unknown)"
    echo "内存信息:"
    free -h 2>/dev/null | sed 's/^/  /' || echo "  free 命令不可用"
    echo "磁盘信息:"
    df -h "$DATA_DIR" 2>/dev/null | sed 's/^/  /' || echo "  df 命令不可用"
    echo
    echo "环境变量（敏感字段已脱敏）:"
    env | sort | awk -F= '{
        key=tolower($1);
        if (key ~ /password|passwd|pwd|token|secret|credential|authorization|auth|cookie|session|apikey|api[-_]key|privatekey|private[-_]key|accesskey|access[-_]key|salt|signature|cryptokey|crypto[-_]key/) {
            print "  " $1 "=***REDACTED***";
        } else {
            print "  " $0;
        }
    }'
} > "$ENV_FILE" 2>/dev/null || add_missing "环境信息收集失败"

# ---- 收集配置文件摘要（应用配置） ----
CONFIG_OUT="$TMP_DIR/config/config.redacted.json"
{
    echo "{"
    echo "  \"sourceFiles\": ["
    if [ -f "$PROJECT_ROOT/src/main/resources/application.yaml" ]; then
        echo "    \"src/main/resources/application.yaml\""
    fi
    if [ -f "$PROJECT_ROOT/config/application.yaml" ]; then
        echo "    , \"config/application.yaml\""
    fi
    if [ -f "$PROJECT_ROOT/.env" ]; then
        echo "    , \".env\""
    fi
    echo "  ],"
    echo "  \"redactionNote\": \"脚本采集到的配置文件值已被脱敏。详细配置请参考应用内 /api/diagnostics/run 输出。\""
    echo "}"
} > "$CONFIG_OUT"

# ---- 生成 summary.md ----
SUMMARY="$TMP_DIR/summary.md"
{
    echo "# 诊断摘要"
    echo
    echo "## 基本信息"
    echo "- 生成时间：$(date '+%Y-%m-%d %H:%M:%S')"
    echo "- 部署形态：$( [ -f /.dockerenv ] && echo Docker || ([ -x "$PROJECT_ROOT/runtime/bin/java" ] && echo 一体化启动包 || echo 本地开发) )"
    echo "- 应用版本：$(grep -E '^\s*version:' "$PROJECT_ROOT/src/main/resources/application.yaml" 2>/dev/null | head -1 | awk '{print $2}' || echo 不可获取)"
    echo "- Trace ID：$TRACE_ID"
    echo
    echo "## 最近一次失败"
    if [ -r "$TMP_DIR/logs/error.log" ] && [ -s "$TMP_DIR/logs/error.log" ]; then
        echo "- Trace ID：参见 logs/error.log 中最新错误条目"
        echo "- 错误信息：见 logs/error.log 末尾"
    else
        echo "- 未发现近期错误日志"
    fi
    echo
    echo "## 关键证据"
    echo "- 错误日志：logs/error.log"
    echo "- 应用日志：logs/app.log"
    echo "- 配置摘要：config/config.redacted.json"
    echo "- 环境摘要：environment.txt"
    echo
    echo "## 缺失信息"
    if [ -z "$MISSING" ]; then
        echo "- 无"
    else
        printf -- "- %b\n" "$MISSING"
    fi
    echo
    echo "## 建议下一步"
    echo "- 通过响应头 X-Trace-Id 或上述 Trace ID 串联日志"
    echo "- 如服务在运行，可调用 POST /api/diagnostics/run 获取更完整的诊断包"
} > "$SUMMARY"

# ---- 原子替换 latest ----
if [ -d "$OUTPUT_DIR" ]; then
    rm -rf "$OUTPUT_DIR"
fi
mv "$TMP_DIR" "$OUTPUT_DIR"

# ---- 输出最终结果 ----
if [ -z "$MISSING" ]; then
    echo "诊断包已生成：$OUTPUT_DIR"
    echo "摘要文件：$OUTPUT_DIR/summary.md"
    exit 0
else
    echo "诊断包已生成但信息不完整：$OUTPUT_DIR"
    echo "摘要文件：$OUTPUT_DIR/summary.md"
    printf "缺失信息：\n- %b\n" "$MISSING"
    exit 2
fi
