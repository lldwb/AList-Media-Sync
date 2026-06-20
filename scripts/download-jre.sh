#!/bin/sh
# ============================================================
# JRE 下载脚本（用于 bootstrap profile 打包时下载 JRE）
# ============================================================
# 用法：
#   sh download-jre.sh <目标目录>
#
# 通过 Adoptium API v3 下载对应平台的 Eclipse Temurin JRE 21，
# 解压到指定目标目录，并校验 SHA-256。
#
# 参见 specs/005-standalone-bootstrap/research.md 决策 1
# ============================================================

set -e

TARGET_DIR="$1"

if [ -z "$TARGET_DIR" ]; then
    echo "用法：$0 <目标目录>"
    exit 1
fi

# 如果设置了 jre.local.path 属性，复制本地 JRE 而非下载
if [ -n "$JRE_LOCAL_PATH" ]; then
    echo "[信息] 使用本地 JRE：$JRE_LOCAL_PATH"
    mkdir -p "$TARGET_DIR"
    cp -r "$JRE_LOCAL_PATH"/* "$TARGET_DIR/"
    echo "[信息] JRE 复制完成"
    exit 0
fi

# 检测操作系统
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
case "$OS" in
    mingw*|msys*|cygwin*)
        OS_TYPE="windows"
        ARCH="x64"
        ;;
    linux*)
        OS_TYPE="linux"
        ARCH="x64"
        ;;
    darwin*)
        OS_TYPE="mac"
        ARCH="x64"
        ;;
    *)
        echo "警告：不支持的操作系统 $OS，跳过 JRE 下载。"
        echo "请通过 -Djre.local.path=/path/to/jre 指定本地 JRE 路径。"
        exit 1
        ;;
esac

echo "[信息] 检测到目标平台：$OS_TYPE / $ARCH"

# Adoptium API v3 下载地址（JRE 21 最新版本）
API_URL="https://api.adoptium.net/v3/binary/latest/21/ga/${OS_TYPE}/${ARCH}/jre/hotspot/normal/eclipse"

echo "[信息] 正在从 Adoptium API 获取 JRE 下载地址..."
echo "  $API_URL"

# 获取重定向后的实际下载 URL
FETCH_URL=$(curl -sL -w '%{url_effective}' "$API_URL" -o /dev/null 2>/dev/null || echo "")
if [ -z "$FETCH_URL" ] || [ "$FETCH_URL" = "$API_URL" ]; then
    echo "错误：无法获取 JRE 下载地址，请检查网络连接。"
    echo "提示：可通过 -Djre.local.path=/path/to/jre 指定本地 JRE 路径。"
    exit 1
fi

echo "[信息] 下载地址：$FETCH_URL"

# 创建临时目录
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

# 下载 JRE 归档文件
JRE_ARCHIVE="$TEMP_DIR/jre.tar.gz"
echo "[信息] 正在下载 JRE（约 40MB）..."
curl -L -o "$JRE_ARCHIVE" "$FETCH_URL" --progress-bar

# 下载 SHA-256 校验和
SHA_URL="${FETCH_URL}.sha256.txt"
echo "[信息] 正在下载 SHA-256 校验和..."
curl -sL -o "$TEMP_DIR/jre.sha256" "$SHA_URL" 2>/dev/null || true

# 校验 SHA-256（如果校验和文件存在）
if [ -s "$TEMP_DIR/jre.sha256" ]; then
    echo "[信息] 正在校验 SHA-256..."
    EXPECTED_HASH=$(cat "$TEMP_DIR/jre.sha256" | awk '{print $1}')
    ACTUAL_HASH=$(sha256sum "$JRE_ARCHIVE" | awk '{print $1}')
    if [ "$EXPECTED_HASH" != "$ACTUAL_HASH" ]; then
        echo "错误：JRE 下载文件 SHA-256 校验失败！"
        echo "  期望：$EXPECTED_HASH"
        echo "  实际：$ACTUAL_HASH"
        exit 1
    fi
    echo "[信息] SHA-256 校验通过"
else
    echo "[警告] 无法获取 SHA-256 校验和，跳过校验"
fi

# 解压到目标目录
echo "[信息] 正在解压 JRE..."
mkdir -p "$TARGET_DIR"

# JRE tar.gz 通常包含一个顶级目录（如 jdk-21.x.x-jre/），需要剥离
EXTRACT_DIR="$TEMP_DIR/extract"
mkdir -p "$EXTRACT_DIR"
tar -xzf "$JRE_ARCHIVE" -C "$EXTRACT_DIR"

# 将解压后的内容移动到目标目录（处理顶层目录）
JRE_CONTENT=$(ls "$EXTRACT_DIR" | head -1)
if [ -d "$EXTRACT_DIR/$JRE_CONTENT" ]; then
    cp -r "$EXTRACT_DIR/$JRE_CONTENT"/* "$TARGET_DIR/"
else
    cp -r "$EXTRACT_DIR"/* "$TARGET_DIR/"
fi

# 移除不必要的文件以减小体积
rm -rf "$TARGET_DIR/man" 2>/dev/null || true
rm -f "$TARGET_DIR/src.zip" 2>/dev/null || true

echo "[信息] JRE 下载并解压完成：$TARGET_DIR"
echo "[信息] JRE 版本信息："
if [ -x "$TARGET_DIR/bin/java" ]; then
    "$TARGET_DIR/bin/java" -version 2>&1 | head -3
fi
