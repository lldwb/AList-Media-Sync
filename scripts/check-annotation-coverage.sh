#!/usr/bin/env bash
# ============================================================
# check-annotation-coverage.sh — OpenAPI 注解覆盖率校验
# ============================================================
# 扫描所有 Controller 与 DTO，断言：
#   - 每个 Controller 类有 @Tag
#   - 每个 Controller 公开映射方法（@GetMapping/@PostMapping 等）有 @Operation
#   - 每个 DTO 类有 @Schema
# 缺失时非零退出码并输出缺失清单。
# ============================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="${REPO_ROOT}/src/main/java/top/lldwb/alistmediasync"
EXIT_CODE=0
MISSING_COUNT=0

echo "==> 扫描 Controller 注解覆盖率..."

# Controller 文件：匹配 *Controller.java
while IFS= read -r -d '' file; do
  rel="${file#${REPO_ROOT}/}"

  # 检查类级 @Tag
  if ! grep -q "@Tag" "${file}"; then
    echo "缺失 @Tag（类级）：${rel}"
    EXIT_CODE=1
    MISSING_COUNT=$((MISSING_COUNT + 1))
  fi

  # 检查每个映射方法是否有 @Operation
  # 匹配 @GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping/@RequestMapping(method=...)
  method_lines=$(grep -nE '@(Get|Post|Put|Delete|Patch)Mapping' "${file}" || true)
  if [ -n "${method_lines}" ]; then
    while IFS= read -r line; do
      lineno=$(echo "${line}" | cut -d: -f1)
      # @Operation 通常在映射注解下方（与 @ApiResponse 相邻），向下 5 行内查找
      if ! sed -n "${lineno},$((lineno+5))p" "${file}" | grep -q "@Operation"; then
        echo "缺失 @Operation（方法，行 ${lineno}）：${rel}"
        EXIT_CODE=1
        MISSING_COUNT=$((MISSING_COUNT + 1))
      fi
    done <<< "${method_lines}"
  fi
done < <(find "${SRC_DIR}" -name "*Controller.java" -print0)

echo "==> 扫描 DTO 注解覆盖率..."

# DTO 文件：匹配 dto 目录下的 *.java
while IFS= read -r -d '' file; do
  rel="${file#${REPO_ROOT}/}"
  # 跳过非类文件（如 package-info）
  if ! grep -qE '(class|record|enum) ' "${file}"; then
    continue
  fi
  if ! grep -q "@Schema" "${file}"; then
    echo "缺失 @Schema（类级）：${rel}"
    EXIT_CODE=1
    MISSING_COUNT=$((MISSING_COUNT + 1))
  fi
done < <(find "${SRC_DIR}" -path "*/dto/*.java" -print0)

echo "----------------------------------------"
if [ "${MISSING_COUNT}" -eq 0 ]; then
  echo "✓ 所有 Controller/DTO 注解覆盖率 100%"
else
  echo "✗ 发现 ${MISSING_COUNT} 处注解缺失"
fi
exit "${EXIT_CODE}"
