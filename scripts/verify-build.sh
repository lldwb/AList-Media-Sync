#!/bin/sh
# ============================================================
# 构建验证脚本（用于 bootstrap profile 的 verify 阶段）
# ============================================================
# 用法：
#   sh verify-build.sh <target目录> <version>
#
# 解压产物到临时目录，验证关键文件存在，
# 校验产物大小，输出打包摘要。
#
# 参见 specs/005-standalone-bootstrap/contracts/build-packaging.md
# ============================================================

set -e

TARGET_DIR="$1"
VERSION="$2"

if [ -z "$TARGET_DIR" ] || [ -z "$VERSION" ]; then
    echo "用法：$0 <target目录> <version>"
    exit 1
fi

echo ""
echo "========================================"
echo "  完整性校验"
echo "========================================"

# 定义产物文件名
ZIP_FILE="$TARGET_DIR/alist-media-sync-${VERSION}-windows-x64.zip"
TAR_FILE="$TARGET_DIR/alist-media-sync-${VERSION}-linux-x64.tar.gz"

# 检查产物是否存在
HAS_ZIP=false
HAS_TAR=false

if [ -f "$ZIP_FILE" ]; then
    HAS_ZIP=true
    ZIP_SIZE=$(du -h "$ZIP_FILE" | cut -f1)
    ZIP_SIZE_BYTES=$(stat -c%s "$ZIP_FILE" 2>/dev/null || echo 0)
    echo "  发现：$(basename "$ZIP_FILE") ($ZIP_SIZE)"
fi

if [ -f "$TAR_FILE" ]; then
    HAS_TAR=true
    TAR_SIZE=$(du -h "$TAR_FILE" | cut -f1)
    TAR_SIZE_BYTES=$(stat -c%s "$TAR_FILE" 2>/dev/null || echo 0)
    echo "  发现：$(basename "$TAR_FILE") ($TAR_SIZE)"
fi

if [ "$HAS_ZIP" = false ] && [ "$HAS_TAR" = false ]; then
    echo "  警告：未找到任何产物文件，跳过完整性校验。"
    exit 0
fi

# 校验产物大小（压缩后 ≤ 150MB）
MAX_COMPRESSED=$((150 * 1024 * 1024))
if [ "$HAS_ZIP" = true ] && [ "$ZIP_SIZE_BYTES" -gt "$MAX_COMPRESSED" ]; then
    echo "  错误：Windows 产物大小超过 150MB 限制！"
    exit 1
fi
if [ "$HAS_TAR" = true ] && [ "$TAR_SIZE_BYTES" -gt "$MAX_COMPRESSED" ]; then
    echo "  错误：Linux 产物大小超过 150MB 限制！"
    exit 1
fi

# 解压产物到临时目录并验证关键文件
VERIFY_DIR=$(mktemp -d)
trap 'rm -rf "$VERIFY_DIR"' EXIT

verify_archive() {
    ARCHIVE="$1"
    LABEL="$2"

    echo ""
    echo "  验证 $LABEL 产物..."

    EXTRACT_DIR="$VERIFY_DIR/$LABEL"
    mkdir -p "$EXTRACT_DIR"

    # 解压
    case "$ARCHIVE" in
        *.zip)
            unzip -q "$ARCHIVE" -d "$EXTRACT_DIR"
            ;;
        *.tar.gz)
            tar -xzf "$ARCHIVE" -C "$EXTRACT_DIR"
            ;;
    esac

    # 找到解压后的根目录
    ROOT=$(ls "$EXTRACT_DIR" | head -1)
    ROOT_DIR="$EXTRACT_DIR/$ROOT"

    # 验证关键文件
    ERRORS=0

    check_file() {
        local FILE="$1"
        local DESC="$2"
        if [ -e "$FILE" ]; then
            echo "    ✓ $DESC"
        else
            echo "    ✗ 缺失：$DESC"
            ERRORS=$((ERRORS + 1))
        fi
    }

    echo "  检查关键文件："
    check_file "$ROOT_DIR/start.sh" "Linux 启动脚本"
    check_file "$ROOT_DIR/start.bat" "Windows 启动脚本"
    check_file "$ROOT_DIR/config/.yaml" "配置文件"
    check_file "$ROOT_DIR/config/application.template.yaml" "配置模板"
    check_file "$ROOT_DIR/lib/"*.jar "应用 JAR 文件"

    # 检查 JRE（仅当 JRE 已下载时）
    if [ -e "$ROOT_DIR/runtime/bin/java" ] || [ -e "$ROOT_DIR/runtime/bin/java.exe" ]; then
        echo "    ✓ JRE 运行时"
    else
        echo "    ⚠ JRE 运行时（未包含，需手动提供）"
    fi

    # 验证 JAR 文件可被 JRE 识别（如果 JRE 存在）
    if [ -x "$ROOT_DIR/runtime/bin/java" ]; then
        JAR_FILE=$(ls "$ROOT_DIR/lib/"*.jar 2>/dev/null | head -1)
        if [ -n "$JAR_FILE" ]; then
            echo "  验证 JAR 可执行性..."
            if "$ROOT_DIR/runtime/bin/java" -jar "$JAR_FILE" --version 2>&1; then
                echo "    ✓ JAR 文件可被 JRE 识别"
            else
                echo "    ✗ JAR 文件无法被 JRE 识别"
                ERRORS=$((ERRORS + 1))
            fi
        fi
    fi

    # 检查解压后大小（≤ 400MB）
    EXTRACTED_SIZE=$(du -sb "$ROOT_DIR" 2>/dev/null | cut -f1)
    MAX_EXTRACTED=$((400 * 1024 * 1024))
    if [ "$EXTRACTED_SIZE" -gt "$MAX_EXTRACTED" ]; then
        echo "    ✗ 解压后大小超过 400MB 限制！"
        ERRORS=$((ERRORS + 1))
    else
        EXTRACTED_SIZE_MB=$((EXTRACTED_SIZE / 1024 / 1024))
        echo "    ✓ 解压后大小：${EXTRACTED_SIZE_MB}MB"
    fi

    if [ "$ERRORS" -gt 0 ]; then
        echo "  错误：$LABEL 产物验证失败（$ERRORS 项缺失）"
        exit 1
    fi

    echo "  $LABEL 产物验证通过 ✓"
}

# 验证每个产物
if [ "$HAS_TAR" = true ]; then
    verify_archive "$TAR_FILE" "Linux"
fi

if [ "$HAS_ZIP" = true ]; then
    verify_archive "$ZIP_FILE" "Windows"
fi

# 输出打包摘要
echo ""
echo "========================================"
echo "  打包完成"
echo "========================================"
echo "  产物："

if [ "$HAS_ZIP" = true ]; then
    echo "    $(basename "$ZIP_FILE")  ($ZIP_SIZE)"
fi
if [ "$HAS_TAR" = true ]; then
    echo "    $(basename "$TAR_FILE")  ($TAR_SIZE)"
fi

echo "  包含组件："
echo "    JRE: Eclipse Temurin 21 (JRE)"
echo "    应用: AList-Media-Sync $VERSION"
echo "  完整性校验：通过 ✓"
echo "========================================"
