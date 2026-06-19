---
description: "刷新编码代理上下文文件中受管理的 Spec Kit 区块"
---

# 更新编码代理上下文

刷新当前编码代理的上下文/指令文件（例如 `CLAUDE.md`、`.github/copilot-instructions.md`、`AGENTS.md`）中受管理的 Spec Kit 区块。

## 行为

脚本读取位于 `.specify/extensions/agent-context/agent-context-config.yml` 的代理上下文扩展配置，以获取以下信息：

- `context_file` — 要管理的编码代理上下文文件路径。
- `context_markers.start` / `.end` — 包围受管理区块的分隔符。当字段缺失时，默认值为 `<!-- SPECKIT START -->` 和 `<!-- SPECKIT END -->`。

然后创建、替换或追加受管理区块，使该区块指向能够被发现的最新计划路径（`specs/<feature>/plan.md`）。

如果 `context_file` 为空或文件无法定位，命令报告无需操作并成功退出。

## 执行

- **Bash**：`.specify/extensions/agent-context/scripts/bash/update-agent-context.sh [plan_path]`
- **PowerShell**：`pwsh -File ".specify/extensions/agent-context/scripts/powershell/update-agent-context.ps1" [plan_path]`

当省略 `plan_path` 时，脚本会自动检测最近修改的 `specs/*/plan.md` 文件。
