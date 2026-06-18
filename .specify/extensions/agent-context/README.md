# 编码代理上下文扩展

此内置扩展管理当前集成的**编码代理上下文/指令文件**（例如 `CLAUDE.md`、`.github/copilot-instructions.md`、`AGENTS.md`、`GEMINI.md` 等）。

它拥有以可配置的开始/结束标记（默认值：`<!-- SPECKIT START -->` / `<!-- SPECKIT END -->`）为界限的受管理区块的生命周期。

## 为什么是扩展？

并非每个 Spec Kit 用户都希望 Spec Kit 写入编码代理的上下文文件。将此行为提取到专用扩展中，用户可以：

- 通过 `specify extension disable agent-context` **完全退出** — 此时 Spec Kit 将永远不会创建或修改代理上下文文件。
- 通过编辑 `.specify/extensions/agent-context/agent-context-config.yml` **自定义标记** — 无论是 Python 层还是内置脚本都遵循相同的 `context_markers` 值。
- 通过 `/speckit.agent-context.update` **按需刷新**，或通过 `extension.yml` 中声明的钩子（`after_specify`、`after_plan`）自动刷新。

## 命令

| 命令 | 描述 |
|------|------|
| `speckit.agent-context.update` | 用当前计划路径刷新代理上下文文件中的受管理区块。 |

## 配置

所有配置通过扩展自身的配置文件进行，位于
`.specify/extensions/agent-context/agent-context-config.yml`：

```yaml
# 由此扩展管理的编码代理上下文文件的路径
context_file: CLAUDE.md

# 受管理 Spec Kit 区块的分隔符
context_markers:
  start: "<!-- SPECKIT START -->"
  end: "<!-- SPECKIT END -->"
```

- `context_file` — 编码代理上下文文件的项目相对路径，由 `specify init` 和 `specify integration install` 写入。
- `context_markers.start` / `.end` — 受管理区块周围的分隔符。编辑这些以使用自定义标记。

## 要求

内置更新脚本需要 **Python 3** 和 **PyYAML** 用于 YAML/更新处理（PowerShell 在可用时也可以使用 `ConvertFrom-Yaml`）。

PyYAML 随 `specify` CLI 一起提供，通常通过相同的 `python3` 解释器可用。如果钩子报告 *"缺少 PyYAML … 在当前 Python 环境中不可用"*，这意味着系统 `python3` 与用于安装 Spec Kit 的不同。要解决，请运行：

```bash
pip install pyyaml
# 或指定 Spec Kit 使用的特定解释器：
/path/to/speckit-python -m pip install pyyaml
```

## 禁用

```bash
specify extension disable agent-context
```

禁用后，Spec Kit 跳过上下文文件的创建、更新和移除（门禁位于 `upsert_context_section()` 和 `remove_context_section()` 内）。
