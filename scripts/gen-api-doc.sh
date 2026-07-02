#!/usr/bin/env bash
# ============================================================
# gen-api-doc.sh — API 文档自动生成脚本（Linux/macOS）
# ============================================================
# 5 阶段流水线（详见 specs/011-docs-system-optimization/contracts/api-doc-generation-contract.md §1）：
#   1. mvn verify -Pgen-api-doc 启动应用并抓取 /v3/api-docs.yaml → target/openapi.yaml
#   2. 调用 openapi-to-md 转换为 Markdown（回退：自研 Node 脚本）
#   3. 仅重写 docs/05-API接口文档.md 的生成区（<!-- GENERATED START --> 与 <!-- GENERATED END --> 之间）
#   4. 写入派生产物声明与生成时间
#   5. 校验输出非空
#
# 行为契约：C-001（幂等）、C-003（失败非零退出）、C-005（openapi.yaml 非空校验）、C-006（仅重写生成区）
# 生成区规则：R-Gen-001（仅替换锚点间内容）、R-Gen-002（锚点缺失报错）
# ============================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OPENAPI_YAML="${REPO_ROOT}/target/openapi.yaml"
DOC_FILE="${REPO_ROOT}/docs/05-API接口文档.md"
START_MARKER="<!-- GENERATED START -->"
END_MARKER="<!-- GENERATED END -->"

echo "==> 阶段 1-4：Maven 静态导出 OpenAPI"
cd "${REPO_ROOT}"
./mvnw verify -Pgen-api-doc -DskipTests

# C-005：校验 openapi.yaml 非空
if [ ! -s "${OPENAPI_YAML}" ]; then
  echo "错误：${OPENAPI_YAML} 不存在或为空" >&2
  exit 1
fi

echo "==> 阶段 5：转换 OpenAPI YAML → Markdown"
# 优先使用 openapi-to-md（npm 全局或本地），回退到 npx
TMP_MD="$(mktemp)"
if command -v openapi-to-md >/dev/null 2>&1; then
  openapi-to-md -i "${OPENAPI_YAML}" -o "${TMP_MD}"
elif command -v npx >/dev/null 2>&1; then
  npx --yes openapi-to-md -i "${OPENAPI_YAML}" -o "${TMP_MD}"
else
  echo "错误：未找到 openapi-to-md，且 npx 不可用。请先执行 npm install -g openapi-to-md" >&2
  exit 1
fi

if [ ! -s "${TMP_MD}" ]; then
  echo "错误：转换后的 Markdown 为空" >&2
  rm -f "${TMP_MD}"
  exit 1
fi

# R-Gen-002：校验 docs/05 锚点存在
if [ ! -f "${DOC_FILE}" ]; then
  echo "错误：${DOC_FILE} 不存在。请先由 T021 创建含手工引导区与锚点的骨架文件。" >&2
  rm -f "${TMP_MD}"
  exit 1
fi

if ! grep -q "${START_MARKER}" "${DOC_FILE}" || ! grep -q "${END_MARKER}" "${DOC_FILE}" ]; then
  echo "错误：${DOC_FILE} 缺少 ${START_MARKER} 或 ${END_MARKER} 锚点，拒绝执行以防覆盖手工引导区" >&2
  rm -f "${TMP_MD}"
  exit 1
fi

echo "==> 阶段 5b：重写生成区（保留手工引导区）"
GENERATED_TIME="$(date '+%Y-%m-%d %H:%M:%S %z')"
# 构造生成区内容：派生产物声明 + 生成时间 + 转换后的端点清单
GENERATED_BLOCK="${START_MARKER}
> 本区块由 \`scripts/gen-api-doc\` 自动生成，请勿手工编辑。
> 源真值位于 \`src/main/java/top/lldwb/alistmediasync/**/controller/*.java\`
> 与 \`**/dto/*.java\` 的 OpenAPI 注解中。
> 最后生成时间：${GENERATED_TIME}

$(cat "${TMP_MD}")

${END_MARKER}"

# 用 awk 替换两锚点之间的内容（锚点本身保留）
TMP_OUT="$(mktemp)"
awk -v start="${START_MARKER}" -v end="${END_MARKER}" -v block="${GENERATED_BLOCK}" '
  $0 == start { print block; in_block = 1; next }
  $0 == end   { in_block = 0; next }
  !in_block   { print }
' "${DOC_FILE}" > "${TMP_OUT}"

mv "${TMP_OUT}" "${DOC_FILE}"
rm -f "${TMP_MD}"

# C-001 幂等性提示
echo "==> 完成：${DOC_FILE} 生成区已更新（生成时间：${GENERATED_TIME}）"
echo "    验证幂等性：再次执行后 git diff 应仅时间戳变化"
