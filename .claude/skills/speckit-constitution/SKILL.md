---
name: "speckit-constitution"
description: "根据交互式或提供的原则输入创建或更新项目章程，确保所有依赖模板保持同步。"
argument-hint: "项目章程的原则或价值观"
compatibility: "需要包含 .specify/ 目录的 spec-kit 项目结构"
metadata:
  author: "github-spec-kit"
  source: "templates/commands/constitution.md"
user-invocable: true
disable-model-invocation: false
---


## 用户输入

```text
$ARGUMENTS
```

**必须**在继续之前考虑用户输入（如果不为空）。

## 预执行检查

**检查扩展钩子（章程更新之前）**：
- 检查项目根目录中是否存在 `.specify/extensions.yml`。
- 如果存在，读取该文件并查找 `hooks.before_constitution` 键下的条目。
- 如果 YAML 无法解析或无效，静默跳过钩子检查并正常继续。
- 过滤掉 `enabled` 显式为 `false` 的钩子。将没有 `enabled` 字段的钩子视为默认启用。
- 对于每个剩余的钩子，**不要**尝试解释或评估钩子的 `condition` 表达式：
  - 如果钩子没有 `condition` 字段，或其值为 null/空，将钩子视为可执行。
  - 如果钩子定义了非空的 `condition`，跳过该钩子，将条件评估交由 HookExecutor 实现处理。
- 从钩子命令名称构造斜杠命令时，将点号（`.`）替换为连字符（`-`）。例如，`speckit.git.commit` → `/speckit-git-commit`。
- 对于每个可执行的钩子，根据其 `optional` 标志输出以下内容：
  - **可选钩子**（`optional: true`）：
    ```
    ## 扩展钩子

    **可选预检钩子**：{extension}
    命令：`/{command}`
    描述：{description}

    提示：{prompt}
    执行方式：`/{command}`
    ```
  - **强制钩子**（`optional: false`）：
    ```
    ## 扩展钩子

    **自动预检钩子**：{extension}
    正在执行：`/{command}`
    EXECUTE_COMMAND：{command}

    请等待钩子命令的结果，然后再继续后续大纲。
    ```
- 如果没有注册钩子或 `.specify/extensions.yml` 不存在，静默跳过。

## 大纲

你正在更新位于 `.specify/memory/constitution.md` 的项目章程。此文件是一个模板，包含方括号中的占位符标记（例如 `[PROJECT_NAME]`、`[PRINCIPLE_1_NAME]`）。你的工作是 (a) 收集/推导具体值，(b) 精确填充模板，以及 (c) 将任何修正传播到依赖制品。

**注意**：如果 `.specify/memory/constitution.md` 尚不存在，它本应在项目设置期间从 `.specify/templates/constitution-template.md` 初始化。如果缺失，请先复制模板。

按以下执行流程操作：

1. 加载位于 `.specify/memory/constitution.md` 的现有章程。
   - 识别每个格式为 `[ALL_CAPS_IDENTIFIER]` 的占位符标记。
   **重要**：用户可能需要比模板中使用的更少或更多的原则。如果指定了数量，请遵守 — 遵循通用模板。你将相应更新文档。

2. 收集/推导占位符的值：
   - 如果用户输入（对话）提供了值，使用它。
   - 否则从现有仓库上下文推断（README、文档、先前的章程版本，如果嵌入的话）。
   - 对于治理日期：`RATIFICATION_DATE` 是原始采纳日期（如果未知则询问或标记 TODO），`LAST_AMENDED_DATE` 是今天（如果进行了更改），否则保留之前的日期。
   - `CONSTITUTION_VERSION` 必须根据语义化版本规则递增：
     - MAJOR：向后不兼容的治理/原则移除或重新定义。
     - MINOR：新增原则/部分或实质性扩展的指导。
     - PATCH：澄清、措辞、拼写修正、非语义细化。
   - 如果版本递增类型模糊，在最终确定前提出推理。

3. 起草更新的章程内容：
   - 将每个占位符替换为具体文本（没有方括号标记残留，除非项目选择尚未定义的有意保留的模板槽 — 明确说明任何保留的理由）。
   - 保留标题层次，注释在替换后可以移除，除非它们仍然添加了澄清性指导。
   - 确保每个原则部分：简洁的名称行，捕获不可协商规则的段落（或要点列表），如非显而易见则附上明确的理由。
   - 确保治理部分列出修订程序、版本控制政策和合规审查预期。

4. 一致性传播检查清单（将先前的检查清单转换为主动验证）：
   - 读取 `.specify/templates/plan-template.md`，确保任何"章程检查"或规则与更新的原则对齐。
   - 读取 `.specify/templates/spec-template.md` 以对齐范围/需求 — 如果章程添加/移除强制部分或约束，则更新。
   - 读取 `.specify/templates/tasks-template.md`，确保任务分类反映新的或移除的原则驱动任务类型（例如，可观测性、版本控制、测试规范）。
   - 读取 `.specify/templates/commands/*.md` 中的每个命令文件（包括此文件），验证在需要通用指导时没有过时的引用（仅限代理特定名称如 CLAUDE）残留。
   - 读取任何运行时指导文档（例如，`README.md`、`docs/quickstart.md` 或代理特定指导文件（如果存在））。更新对已更改原则的引用。

5. 生成同步影响报告（更新后作为 HTML 注释添加到章程文件顶部）：
   - 版本变更：旧 → 新
   - 已修改原则列表（旧标题 → 新标题，如果重命名）
   - 新增部分
   - 移除部分
   - 需要更新的模板（✅ 已更新 / ⚠ 待处理），附文件路径
   - 如果有任何有意推迟的占位符，列出后续 TODO。

6. 最终输出前的验证：
   - 没有剩余的未解释方括号标记。
   - 版本行与报告匹配。
   - 日期采用 ISO 格式 YYYY-MM-DD。
   - 原则是声明性的、可测试的，不含模糊语言（"应该" → 在适当的地方替换为 MUST/SHOULD 加上理由）。

7. 将完成的章程写回 `.specify/memory/constitution.md`（覆写）。

8. 向用户输出最终摘要，包含：
   - 新版本和递增理由。
   - 任何标记为需要手动跟进的文件。
   - 建议的提交消息（例如，`docs: amend constitution to vX.Y.Z (principle additions + governance update)`）。

格式与风格要求：

- 使用与模板完全一致的 Markdown 标题（不要降级/升级层级）。
- 包装长理由行以保持可读性（理想情况下 <100 字符），但不要用不自然的分行强制实施。
- 各部分之间保留一个空行。
- 避免尾随空白。

如果用户提供部分更新（例如，仅一个原则修订），仍然执行验证和版本决策步骤。

如果关键信息缺失（例如，批准日期确实未知），插入 `TODO(<字段名>): 说明` 并在同步影响报告的延期项下包含。

不要创建新模板；始终在现有的 `.specify/memory/constitution.md` 文件上操作。

## 执行后检查

**检查扩展钩子（章程更新之后）**：
检查项目根目录中是否存在 `.specify/extensions.yml`。
- 如果存在，读取该文件并查找 `hooks.after_constitution` 键下的条目。
- 如果 YAML 无法解析或无效，静默跳过钩子检查并正常继续。
- 过滤掉 `enabled` 显式为 `false` 的钩子。将没有 `enabled` 字段的钩子视为默认启用。
- 对于每个剩余的钩子，**不要**尝试解释或评估钩子的 `condition` 表达式：
  - 如果钩子没有 `condition` 字段，或其值为 null/空，将钩子视为可执行。
  - 如果钩子定义了非空的 `condition`，跳过该钩子，将条件评估交由 HookExecutor 实现处理。
- 从钩子命令名称构造斜杠命令时，将点号（`.`）替换为连字符（`-`）。例如，`speckit.git.commit` → `/speckit-git-commit`。
- 对于每个可执行的钩子，根据其 `optional` 标志输出以下内容：
  - **可选钩子**（`optional: true`）：
    ```
    ## 扩展钩子

    **可选钩子**：{extension}
    命令：`/{command}`
    描述：{description}

    提示：{prompt}
    执行方式：`/{command}`
    ```
  - **强制钩子**（`optional: false`）：
    ```
    ## 扩展钩子

    **自动钩子**：{extension}
    正在执行：`/{command}`
    EXECUTE_COMMAND：{command}
    ```
- 如果没有注册钩子或 `.specify/extensions.yml` 不存在，静默跳过。
