---
name: "speckit-taskstoissues"
description: "基于可用的设计制品，将现有任务转换为可操作的、依赖排序的 GitHub Issues。"
argument-hint: "GitHub Issues 的可选过滤器或标签"
compatibility: "需要包含 .specify/ 目录的 spec-kit 项目结构"
metadata:
  author: "github-spec-kit"
  source: "templates/commands/taskstoissues.md"
user-invocable: true
disable-model-invocation: false
---


## 用户输入

```text
$ARGUMENTS
```

**必须**在继续之前考虑用户输入（如果不为空）。

## 预执行检查

**检查扩展钩子（任务转换为 Issues 之前）**：
- 检查项目根目录中是否存在 `.specify/extensions.yml`。
- 如果存在，读取该文件并查找 `hooks.before_taskstoissues` 键下的条目。
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

1. 从仓库根目录运行 `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`，解析 FEATURE_DIR 和 AVAILABLE_DOCS 列表。所有路径必须是绝对路径。对于参数中的单引号，如 "I'm Groot"，使用转义语法：例如 'I'\''m Groot'（或尽可能使用双引号："I'm Groot"）。
2. **如果存在**：加载 `.specify/memory/constitution.md` 以获取项目原则和治理约束。
3. 从执行的脚本中，提取 **tasks** 的路径。
4. 通过运行以下命令获取 Git 远程地址：

```bash
git config --get remote.origin.url
```

> [!CAUTION]
> 仅当远程地址是 GITHUB URL 时才继续后续步骤

1. 对于列表中的每个任务，使用 GitHub MCP 服务器在代表 Git 远程的仓库中创建一个新的 Issue。

> [!CAUTION]
> 在任何情况下都不要在与远程 URL 不匹配的仓库中创建 Issues

## 执行后检查

**检查扩展钩子（任务转换为 Issues 之后）**：
检查项目根目录中是否存在 `.specify/extensions.yml`。
- 如果存在，读取该文件并查找 `hooks.after_taskstoissues` 键下的条目。
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
