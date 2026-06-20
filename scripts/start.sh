#!/bin/sh
# ============================================================
# AList-Media-Sync Linux 启动脚本
# ============================================================
# 用法：
#   ./start.sh          # 前台启动（推荐）
#   sh start.sh         # 不依赖可执行权限
#
# 环境变量（可通过 export 设置或启动时内联）：
#   SERVER_PORT=8080           应用监听端口
#   DATA_DIR=./data            数据目录路径
#   LOGGING_LEVEL=INFO         日志级别
#   JAVA_OPTS="-Xms128m -Xmx256m"  JVM 额外参数
#   DISK_SPACE_THRESHOLD_MB=100    磁盘空间警告阈值（MB）
#   APP_AUTH_PASSWORD          覆盖配置文件中的认证密码
#   CI=true                    非交互模式（跳过确认提示）
#
# 退出码：
#   0   正常退出
#   1   预检查失败
#   2   用户拒绝继续
#   130 收到 SIGINT（Ctrl+C）
#   143 收到 SIGTERM
# ============================================================

set -e

# ---- 路径解析 ----
# 通过脚本自身位置定位启动包根目录（解析符号链接）
SCRIPT_PATH="$(readlink -f "$0" 2>/dev/null || realpath "$0" 2>/dev/null || echo "$0")"
SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"
cd "$SCRIPT_DIR"

# ---- 默认环境变量 ----
SERVER_PORT="${SERVER_PORT:-8080}"
DATA_DIR="${DATA_DIR:-./data}"
LOGGING_LEVEL="${LOGGING_LEVEL:-INFO}"
JAVA_OPTS="${JAVA_OPTS:--Xms128m -Xmx256m}"
DISK_SPACE_THRESHOLD_MB="${DISK_SPACE_THRESHOLD_MB:-100}"

# ---- 颜色输出 ----
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ---- 工具函数 ----
print_error() {
    echo "${RED}[错误]${NC} $1" >&2
}

print_warn() {
    echo "${YELLOW}[警告]${NC} $1" >&2
}

print_info() {
    echo "${GREEN}[信息]${NC} $1"
}

print_banner() {
    echo "${CYAN}$1${NC}"
}

# 检测是否为非交互模式
is_non_interactive() {
    # CI 环境变量或 stdin 非终端
    if [ "${CI}" = "true" ]; then
        return 0
    fi
    if [ ! -t 0 ]; then
        return 0
    fi
    return 1
}

# ---- 预检查 1：JRE 存在性与架构匹配 ----
check_jre() {
    JAVA_EXEC="$SCRIPT_DIR/runtime/bin/java"

    # 检测系统架构
    ARCH=$(uname -m)
    if [ "$ARCH" != "x86_64" ] && [ "$ARCH" != "amd64" ]; then
        print_error "当前系统架构为 $ARCH，本启动包仅支持 x86_64 架构。"
        echo "建议：请下载对应架构的启动包，或使用 Docker 部署方案。"
        exit 1
    fi

    # 检查内置 JRE
    if [ -x "$JAVA_EXEC" ]; then
        print_info "使用内置 JRE：$("$JAVA_EXEC" -version 2>&1 | head -1)"
        return 0
    fi

    # 回退到系统 Java
    if command -v java >/dev/null 2>&1; then
        JAVA_EXEC="$(command -v java)"
        print_warn "未找到内置 JRE，使用系统 Java：$("$JAVA_EXEC" -version 2>&1 | head -1)"
        return 0
    fi

    print_error "未检测到 Java 运行环境。"
    echo "建议：本启动包已内置 JRE，但似乎在解压或复制过程中丢失。请重新下载完整的启动包。"
    exit 1
}

# ---- 预检查 2：配置文件存在性 ----
check_config() {
    CONFIG_FILE="$SCRIPT_DIR/config/application.yaml"
    if [ ! -f "$CONFIG_FILE" ]; then
        print_error "配置文件 config/application.yaml 不存在或格式错误。"
        echo "建议：请检查 config/ 目录下的配置文件是否完整，可从模板文件 config/application.template.yaml 复制一份并修改。"
        exit 1
    fi
}

# ---- 预检查 3：端口占用检测 ----
check_port() {
    # 使用 ss 命令检测端口占用
    if command -v ss >/dev/null 2>&1; then
        PORT_INFO=$(ss -tlnp "sport = :$SERVER_PORT" 2>/dev/null | awk 'NR>1 {print $NF}')
        if [ -n "$PORT_INFO" ]; then
            PID=$(echo "$PORT_INFO" | grep -oP '(?<=pid=)\d+' | head -1)
            if [ -n "$PID" ]; then
                PROC_NAME=$(ps -p "$PID" -o comm= 2>/dev/null || echo "未知")
                print_error "端口 $SERVER_PORT 已被进程 $PROC_NAME (PID: $PID) 占用。"
                echo "建议：1) 修改 config/application.yaml 中的 server.port 配置项；"
                echo "      2) 或终止占用进程后重试（kill $PID）。"
            else
                print_error "端口 $SERVER_PORT 已被占用。"
                echo "建议：1) 修改 config/application.yaml 中的 server.port 配置项；"
                echo "      2) 或终止占用端口的进程后重试。"
            fi
            exit 1
        fi
    elif command -v netstat >/dev/null 2>&1; then
        if netstat -tlnp 2>/dev/null | grep -q ":$SERVER_PORT "; then
            print_error "端口 $SERVER_PORT 已被占用。"
            echo "建议：1) 修改 config/application.yaml 中的 server.port 配置项；"
            echo "      2) 或终止占用端口的进程后重试。"
            exit 1
        fi
    fi
}

# ---- 预检查 4：磁盘空间检测 ----
check_disk() {
    # 确保数据目录存在以进行磁盘检查
    mkdir -p "$DATA_DIR"

    if command -v df >/dev/null 2>&1; then
        AVAIL_KB=$(df "$DATA_DIR" 2>/dev/null | awk 'NR==2 {print $4}')
        if [ -n "$AVAIL_KB" ]; then
            AVAIL_MB=$((AVAIL_KB / 1024))
            if [ "$AVAIL_MB" -lt "$DISK_SPACE_THRESHOLD_MB" ]; then
                print_warn "数据目录所在磁盘剩余空间不足 ${DISK_SPACE_THRESHOLD_MB}MB（当前仅 ${AVAIL_MB}MB），长期运行可能导致数据库写入失败。"
                echo "建议：清理磁盘空间或将数据目录迁移到空间充足的磁盘。"

                if is_non_interactive; then
                    print_info "检测到非交互模式，自动退出。"
                    exit 2
                fi

                printf "是否继续启动？[y/N] "
                read -r CONFIRM
                case "$CONFIRM" in
                    [Yy]|[Yy][Ee][Ss])
                        print_info "用户选择继续启动。"
                        ;;
                    *)
                        print_info "用户取消启动。"
                        exit 2
                        ;;
                esac
            fi
        fi
    fi
}

# ---- 预检查 5：数据目录写入权限 ----
check_write_permission() {
    mkdir -p "$DATA_DIR"

    if [ ! -w "$DATA_DIR" ]; then
        print_error "数据目录 $DATA_DIR 无写入权限。"
        echo "建议：请检查目录权限（ls -la $DATA_DIR），使用 chmod 添加写入权限，或将数据目录迁移到有写入权限的路径。"
        exit 1
    fi
}

# ---- 预检查 6：已有实例检测 ----
check_existing_instance() {
    PID_FILE="$DATA_DIR/app.pid"

    if [ -f "$PID_FILE" ]; then
        OLD_PID=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
            print_warn "检测到已有实例正在运行（PID: $OLD_PID）。"

            if is_non_interactive; then
                print_info "检测到非交互模式，自动退出。"
                exit 1
            fi

            printf "是否强制重启？[y/N] "
            read -r CONFIRM
            case "$CONFIRM" in
                [Yy]|[Yy][Ee][Ss])
                    print_info "正在终止旧进程（PID: $OLD_PID）..."
                    kill "$OLD_PID" 2>/dev/null || true
                    sleep 2
                    # 如果仍未退出，强制终止
                    if kill -0 "$OLD_PID" 2>/dev/null; then
                        kill -9 "$OLD_PID" 2>/dev/null || true
                        sleep 1
                    fi
                    print_info "旧进程已终止，继续启动。"
                    ;;
                *)
                    print_info "用户取消启动。"
                    exit 2
                    ;;
            esac
        else
            # PID 文件是陈旧的（进程已退出），清理
            rm -f "$PID_FILE"
        fi
    fi
}

# ---- 查找 JAR 文件 ----
find_jar() {
    JAR_FILE=$(ls "$SCRIPT_DIR/lib/"*.jar 2>/dev/null | head -1)
    if [ -z "$JAR_FILE" ]; then
        print_error "未找到应用 JAR 文件（lib/*.jar）。"
        echo "建议：请检查 lib/ 目录下的 JAR 文件是否完整，可能需要重新下载启动包。"
        exit 1
    fi
}

# ---- 优雅退出处理 ----
cleanup() {
    print_info "收到退出信号，正在优雅关闭应用..."
    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        kill "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
    rm -f "$DATA_DIR/app.pid"
    print_info "应用已关闭。"
    exit 0
}

trap cleanup SIGINT SIGTERM

# ---- 主流程 ----
main() {
    START_TIME=$(date +%s)

    echo ""
    print_banner "========================================"
    print_banner "  AList-Media-Sync 一体化启动包"
    print_banner "========================================"
    echo ""

    # 执行预检查
    print_info "正在执行启动前检查..."
    check_jre
    check_config
    check_port
    check_disk
    check_write_permission
    check_existing_instance
    find_jar

    print_info "所有预检查通过。"

    # 创建日志目录
    mkdir -p "$SCRIPT_DIR/logs"

    # 写入 PID 文件
    echo $$ > "$DATA_DIR/app.pid"

    # 组装 JVM 参数
    JVM_ARGS="$JAVA_OPTS -Dserver.port=$SERVER_PORT -Dapp.data-dir=$DATA_DIR -Dlogging.level.root=$LOGGING_LEVEL"

    # 如果设置了 APP_AUTH_PASSWORD 环境变量，传递给 JVM
    if [ -n "$APP_AUTH_PASSWORD" ]; then
        JVM_ARGS="$JVM_ARGS -Dapp.auth.password=$APP_AUTH_PASSWORD"
    fi

    print_info "正在启动应用..."
    echo ""

    # 启动 Java 进程（前台运行，输出同时写入日志文件和控制台）
    "$JAVA_EXEC" $JVM_ARGS -jar "$JAR_FILE" 2>&1 | tee "$SCRIPT_DIR/logs/app.log" &
    APP_PID=$!

    # 等待进程结束
    wait "$APP_PID" 2>/dev/null || true

    # 计算启动耗时
    END_TIME=$(date +%s)
    ELAPSED=$((END_TIME - START_TIME))
    print_info "应用运行结束，总耗时 ${ELAPSED} 秒。"

    # 清理 PID 文件
    rm -f "$DATA_DIR/app.pid"
}

main "$@"
