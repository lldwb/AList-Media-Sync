#!/bin/bash
# ============================================================
# E2E 环境准备脚本（Linux 备）
# ============================================================
# 用法：
#   ./scripts/e2e/prepare-e2e-env.sh [-f] [-s] [-d]
#
# 从 GitHub Releases 下载 AList（录播姬可选）二进制到 scripts/e2e/bin/，
# SHA256校验，幂等跳过已存在下载。
#
# 参见 specs/012-e2e-test-infrastructure/contracts/env-prep-script.md
# 对齐 scripts/download-jre.sh 的幂等下载模式
# ============================================================

set -e

# ============================================================
# 版本锁定常量
# 实现时请替换为当前稳定版的实际版本号与 SHA256 哈希值。
# 可通过 GitHub Releases 页面获取：
#   https://github.com/alist-org/alist/releases
#   https://github.com/Bililive/BililiveRecorder/releases
# ============================================================
ALIST_VERSION="v3.62.0"
ALIST_SHA256_LINUX="2ce6b4eccdda166eff5c575c956e64d983b1b7ac68b694dc7c159971267ca3c7"
ALIST_DOWNLOAD_URL="https://github.com/AlistGo/alist/releases/download/${ALIST_VERSION}/alist-${ALIST_VERSION}-linux-amd64.tar.gz"

DANMUJI_VERSION="v2.18.0"
DANMUJI_SHA256_LINUX="d300bb9f70752419bf1f70ddae0da42dcf37fe005d5a68ccabc41b4b90f63935"
DANMUJI_DOWNLOAD_URL="https://github.com/Bililive/BililiveRecorder/releases/download/${DANMUJI_VERSION}/BililiveRecorder-CLI-${DANMUJI_VERSION}-linux-x64.zip"

# 退出码
EXIT_SUCCESS=0
EXIT_DOWNLOAD_FAILED=1
EXIT_EXTRACT_FAILED=2

# 脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN_ROOT="${SCRIPT_DIR}/bin"
ALIST_DIR="${BIN_ROOT}/alist"
DANMUJI_DIR="${BIN_ROOT}/danmuji"

# 默认值
FORCE=false
SKIP_ALIST=false
INCLUDE_DANMUJI=false

# 解析参数
while [ $# -gt 0 ]; do
    case "$1" in
        -f|--force)
            FORCE=true
            shift
            ;;
        -s|--skip-alist)
            SKIP_ALIST=true
            shift
            ;;
        -d|--include-danmuji)
            INCLUDE_DANMUJI=true
            shift
            ;;
        *)
            echo "用法：$0 [-f] [-s] [-d]"
            echo "  -f, --force            强制重新下载，覆盖已存在二进制"
            echo "  -s, --skip-alist       跳过 AList 下载"
            echo "  -d, --include-danmuji  下载录播姬二进制（默认跳过）"
            exit 1
            ;;
    esac
done

# ============================================================
# 辅助函数
# ============================================================

info() {
    echo "[信息] $1"
}

warn() {
    echo "[警告] $1"
}

err() {
    echo "[错误] $1" >&2
}

# 计算文件 SHA256
sha256_file() {
    sha256sum "$1" | awk '{print $1}' | tr '[:upper:]' '[:lower:]'
}

# 下载并解压
download_and_extract() {
    local name="$1"
    local url="$2"
    local expected_hash="$3"
    local target_dir="$4"
    local local_path_env="$5"
    local archive_file

    # 检查本地路径环境变量（对齐 download-jre.sh 的 JRE_LOCAL_PATH 模式）
    if [ -n "$local_path_env" ] && [ -n "$(eval echo \$$local_path_env)" ]; then
        local local_path
        local_path="$(eval echo \$$local_path_env)"
        info "$name：使用本地二进制路径 $local_path"
        if [ ! -e "$local_path" ]; then
            err "$name：本地路径不存在 $local_path"
            return $EXIT_DOWNLOAD_FAILED
        fi
        mkdir -p "$target_dir"
        cp -r "$local_path"/* "$target_dir/"
        info "$name：本地二进制复制完成"
        return $EXIT_SUCCESS
    fi

    if [ "$name" = "AList" ]; then
        local exe_name="alist"
    else
        local exe_name="BililiveRecorder"
    fi
    local exe_path="${target_dir}/${exe_name}"

    # 幂等检查：已存在且未强制
    if [ -f "$exe_path" ] && [ "$FORCE" != "true" ]; then
        info "$name：已存在 $exe_path，跳过下载（使用 -f 强制重新下载）"
        return $EXIT_SUCCESS
    fi

    # 创建目标目录
    mkdir -p "$target_dir"

    # 临时文件
    local temp_dir
    temp_dir="$(mktemp -d)"
    trap 'rm -rf "$temp_dir"' EXIT

    # 判断归档格式
    case "$url" in
        *.tar.gz)
            archive_file="${temp_dir}/${name}.tar.gz"
            ;;
        *.zip)
            archive_file="${temp_dir}/${name}.zip"
            ;;
        *)
            archive_file="${temp_dir}/${name}.archive"
            ;;
    esac

    # 下载
    info "$name：正在下载 $url"
    if ! curl -L -o "$archive_file" "$url" --progress-bar 2>/dev/null; then
        err "$name：下载失败"
        return $EXIT_DOWNLOAD_FAILED
    fi

    # SHA256 校验
    if [ "$expected_hash" != "REPLACE_WITH_ACTUAL_SHA256" ]; then
        info "$name：正在校验 SHA256"
        local actual_hash
        actual_hash="$(sha256_file "$archive_file")"
        if [ "$actual_hash" != "$(echo "$expected_hash" | tr '[:upper:]' '[:lower:]')" ]; then
            err "$name：SHA256 校验失败"
            err "  期望：$expected_hash"
            err "  实际：$actual_hash"
            rm -f "$archive_file"
            return $EXIT_DOWNLOAD_FAILED
        fi
        info "$name：SHA256 校验通过"
    else
        warn "$name：SHA256 为占位值，跳过校验（实现时请填充实际哈希）"
    fi

    # 解压
    info "$name：正在解压到 $target_dir"
    case "$url" in
        *.tar.gz)
            tar -xzf "$archive_file" -C "$target_dir" || {
                err "$name：解压失败"
                return $EXIT_EXTRACT_FAILED
            }
            ;;
        *.zip)
            if command -v unzip >/dev/null 2>&1; then
                unzip -o "$archive_file" -d "$target_dir" >/dev/null 2>&1 || {
                    err "$name：解压失败"
                    return $EXIT_EXTRACT_FAILED
                }
            else
                err "$name：需要 unzip，请先安装"
                return $EXIT_EXTRACT_FAILED
            fi
            ;;
    esac

    # 清理临时文件
    rm -rf "$temp_dir"
    trap '' EXIT

    # 验证可执行文件存在
    if [ ! -f "$exe_path" ]; then
        # 可能在解压后的子目录中，尝试查找
        local found
        found="$(find "$target_dir" -type f -name "$exe_name" 2>/dev/null | head -1)"
        if [ -n "$found" ]; then
            mv "$found" "$exe_path"
        else
            err "$name：解压后未找到 $exe_name"
            return $EXIT_EXTRACT_FAILED
        fi
    fi

    # 添加可执行权限
    chmod +x "$exe_path" 2>/dev/null || true

    info "$name：下载摘要"
    if [ "$name" = "AList" ]; then
        info "  版本：${ALIST_VERSION}"
    else
        info "  版本：${DANMUJI_VERSION}"
    fi
    info "  路径：$exe_path"
    info "  校验：通过"

    return $EXIT_SUCCESS
}

# ============================================================
# 主流程
# ============================================================

# 下载 AList
if [ "$SKIP_ALIST" != "true" ]; then
    info "=== 准备 AList ==="
    if ! download_and_extract "AList" "$ALIST_DOWNLOAD_URL" "$ALIST_SHA256_LINUX" "$ALIST_DIR" "ALIST_LOCAL_PATH"; then
        exit $?
    fi
else
    info "已指定 --skip-alist，跳过 AList 下载"
fi

# 下载录播姬（可选）
if [ "$INCLUDE_DANMUJI" = "true" ]; then
    info "=== 准备录播姬 ==="
    if ! download_and_extract "Danmuji" "$DANMUJI_DOWNLOAD_URL" "$DANMUJI_SHA256_LINUX" "$DANMUJI_DIR" "DANMUJI_LOCAL_PATH"; then
        exit $?
    fi
else
    info "未指定 --include-danmuji，跳过录播姬下载（可选验证路径，默认不需要）"
fi

info "=== E2E 环境准备完成 ==="
exit $EXIT_SUCCESS