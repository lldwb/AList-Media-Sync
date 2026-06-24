#!/usr/bin/env bash
# 解析 _llms_index.txt 并按分类下载所有 md
set -u
BASE_DIR="C:/Users/Administrator/Documents/GitHub/AList-Media-Sync/md/alist"
INDEX="$BASE_DIR/_llms_index.txt"

# 跳过非条目行（标题、空行）
grep -E '^- ' "$INDEX" | while IFS= read -r line; do
    # 提取分类: "- " 之后到 " [" 之前
    category="${line#- }"
    category="${category%% \[*}"
    # 提取名称: "[" 到 "]"
    name="${line#*[}"
    name="${name%%]*}"
    # 提取 URL: "(" 到 ")"
    url="${line#*\(}"
    url="${url%%\)*}"

    # 分类 " > " -> "/"
    folder="${category// > //}"
    target_dir="$BASE_DIR/$folder"
    mkdir -p "$target_dir"

    # 名称中的非法文件名字符替换
    safe_name="${name//\//_}"
    safe_name="${safe_name//:/_}"
    safe_name="${safe_name//\*/_}"
    safe_name="${safe_name//\?/_}"
    safe_name="${safe_name//\"/_}"
    safe_name="${safe_name//</_}"
    safe_name="${safe_name//>/_}"
    safe_name="${safe_name//|/_}"

    out="$target_dir/$safe_name.md"
    echo "Downloading: $folder / $safe_name.md"
    curl -fsSL "$url" -o "$out" || echo "  FAILED: $url"
done

echo "Done."
