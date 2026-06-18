#!/usr/bin/env bash
# update-agent-context.sh
#
# 刷新编码代理上下文文件（例如 CLAUDE.md、.github/copilot-instructions.md、AGENTS.md）
# 中受管理的 Spec Kit 区块。
#
# 从代理上下文扩展配置中读取 `context_file` 和 `context_markers.{start,end}`：
#   .specify/extensions/agent-context/agent-context-config.yml
#
# 用法：update-agent-context.sh [plan_path]
#
# 当省略 `plan_path` 时，脚本选取最近修改的 `specs/*/plan.md`（如果存在），
# 否则生成不含具体计划路径的区块。

set -euo pipefail

PROJECT_ROOT="$(pwd)"
EXT_CONFIG="$PROJECT_ROOT/.specify/extensions/agent-context/agent-context-config.yml"
DEFAULT_START="<!-- SPECKIT START -->"
DEFAULT_END="<!-- SPECKIT END -->"

if [[ ! -f "$EXT_CONFIG" ]]; then
  echo "agent-context: $EXT_CONFIG 未找到；无需操作。" >&2
  exit 0
fi

# 定位合适的 Python 解释器（python3，其次 python）。
_python=""
if command -v python3 >/dev/null 2>&1; then
  _python="python3"
elif command -v python >/dev/null 2>&1 && python --version 2>&1 | grep -q "^Python 3"; then
  _python="python"
fi

if [[ -z "$_python" ]]; then
  echo "agent-context: 在 PATH 中未找到 Python 3；跳过更新。" >&2
  exit 0
fi

# 解析扩展配置一次；输出三个换行分隔的字段：
# context_file、context_markers.start、context_markers.end
if ! _raw_opts="$("$_python" - "$EXT_CONFIG" <<'PY'
import sys
try:
    import yaml
except ImportError:
    print(
        "agent-context: 解析扩展配置需要 PyYAML，但在当前 Python 环境中不可用。\n"
        "  解决方案：pip install pyyaml（或将其安装到 python3 使用的环境中）。\n"
        "  在 PyYAML 可导入之前，上下文文件将不会更新。",
        file=sys.stderr,
    )
    sys.exit(2)
try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        data = yaml.safe_load(fh)
except Exception as exc:
    print(
        f"agent-context: 无法解析 {sys.argv[1]} ({exc})；无法更新上下文。",
        file=sys.stderr,
    )
    sys.exit(2)
if not isinstance(data, dict):
    data = {}
def get_str(obj, *keys):
    node = obj
    for k in keys:
        if isinstance(node, dict) and k in node:
            node = node[k]
        else:
            return ""
    return node if isinstance(node, str) else ""
print(get_str(data, "context_file"))
print(get_str(data, "context_markers", "start"))
print(get_str(data, "context_markers", "end"))
PY
)"; then
  echo "agent-context: 跳过更新（详情见上）。" >&2
  exit 0
fi

_opts_lines=()
while IFS= read -r _line || [[ -n "$_line" ]]; do
  _opts_lines+=("$_line")
done < <(printf '%s\n' "$_raw_opts")
if (( ${#_opts_lines[@]} < 3 )); then
  echo "agent-context: 配置解析器输出格式错误；预期 3 行（context_file、marker_start、marker_end），实际得到 ${#_opts_lines[@]} 行；跳过更新。" >&2
  exit 0
fi
CONTEXT_FILE="${_opts_lines[0]}"
MARKER_START="${_opts_lines[1]}"
MARKER_END="${_opts_lines[2]}"

if [[ -z "$CONTEXT_FILE" ]]; then
  echo "agent-context: 扩展配置中未设置 context_file；无需操作。" >&2
  exit 0
fi

# 拒绝 context_file 中的绝对路径、反斜杠分隔符和 '..' 路径段
if [[ "$CONTEXT_FILE" == /* ]] || [[ "$CONTEXT_FILE" =~ ^[A-Za-z]: ]]; then
  echo "agent-context: context_file 必须是项目相对路径；得到 '$CONTEXT_FILE'。" >&2
  exit 1
fi
if [[ "$CONTEXT_FILE" == *\\* ]]; then
  echo "agent-context: context_file 不得包含反斜杠分隔符；得到 '$CONTEXT_FILE'。" >&2
  exit 1
fi
IFS='/' read -ra _cf_parts <<< "$CONTEXT_FILE"
for _seg in "${_cf_parts[@]}"; do
  if [[ "$_seg" == ".." ]]; then
    echo "agent-context: context_file 不得包含 '..' 路径段；得到 '$CONTEXT_FILE'。" >&2
    exit 1
  fi
done
unset _cf_parts _seg

[[ -z "$MARKER_START" ]] && MARKER_START="$DEFAULT_START"
[[ -z "$MARKER_END"   ]] && MARKER_END="$DEFAULT_END"

PLAN_PATH="${1:-}"
if [[ -z "$PLAN_PATH" ]]; then
  # 选取最近修改的位于一级深度下的 plan.md（specs/<feature>/plan.md）。
  # 使用 find + 按修改时间排序以避免路径中空格或 pipefail 导致的 ls/head 脆弱性。
  _plan_abs="$("$_python" - "$PROJECT_ROOT" <<'PY'
import sys, os
from pathlib import Path
specs = Path(sys.argv[1]) / "specs"
plans = sorted(
    specs.glob("*/plan.md"),
    key=lambda p: p.stat().st_mtime,
    reverse=True,
)
print(plans[0] if plans else "")
PY
)"
  if [[ -n "$_plan_abs" ]]; then
    PLAN_PATH="${_plan_abs#"$PROJECT_ROOT/"}"
  fi
fi

CTX_PATH="$PROJECT_ROOT/$CONTEXT_FILE"
mkdir -p "$(dirname "$CTX_PATH")"

# 构建受管理区块
TMP_SECTION="$(mktemp)"
trap 'rm -f "$TMP_SECTION"' EXIT
{
  echo "$MARKER_START"
  echo "如需了解要使用的技术、项目结构、Shell 命令及其他重要信息，请参阅当前计划"
  if [[ -n "$PLAN_PATH" ]]; then
    echo "位于 $PLAN_PATH"
  fi
  echo "$MARKER_END"
} > "$TMP_SECTION"

"$_python" - "$CTX_PATH" "$MARKER_START" "$MARKER_END" "$TMP_SECTION" <<'PY'
import sys, os
ctx_path, start, end, section_path = sys.argv[1:5]
with open(section_path, "r", encoding="utf-8") as fh:
    section = fh.read().rstrip("\n") + "\n"

if os.path.exists(ctx_path):
    with open(ctx_path, "r", encoding="utf-8-sig") as fh:
        content = fh.read()
    s = content.find(start)
    e = content.find(end, s if s != -1 else 0)
    if s != -1 and e != -1 and e > s:
        end_of_marker = e + len(end)
        if end_of_marker < len(content) and content[end_of_marker] == "\r":
            end_of_marker += 1
        if end_of_marker < len(content) and content[end_of_marker] == "\n":
            end_of_marker += 1
        new_content = content[:s] + section + content[end_of_marker:]
    elif s != -1:
        new_content = content[:s] + section
    elif e != -1:
        end_of_marker = e + len(end)
        if end_of_marker < len(content) and content[end_of_marker] == "\r":
            end_of_marker += 1
        if end_of_marker < len(content) and content[end_of_marker] == "\n":
            end_of_marker += 1
        new_content = section + content[end_of_marker:]
    else:
        if content and not content.endswith("\n"):
            content += "\n"
        new_content = (content + "\n" + section) if content else section
else:
    new_content = section

new_content = new_content.replace("\r\n", "\n").replace("\r", "\n")
with open(ctx_path, "wb") as fh:
    fh.write(new_content.encode("utf-8"))
PY

echo "agent-context: 已更新 $CONTEXT_FILE"
