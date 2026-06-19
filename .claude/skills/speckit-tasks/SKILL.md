---
name: "speckit-tasks"
description: "基于可用的设计制品为功能生成可操作的、依赖排序的 tasks.md。"
argument-hint: "可选的任务生成约束"
compatibility: "需要包含 .specify/ 目录的 spec-kit 项目结构"
metadata:
  author: "github-spec-kit"
  source: "templates/commands/tasks.md"
user-invocable: true
disable-model-invocation: false
---


## 用户输入

```text
$ARGUMENTS
```

**必须**在继续之前考虑用户输入（如果不为空）。

## 预执行检查

**检查扩展钩子（任务生成之前）**：
- 检查项目根目录中是否存在 `.specify/extensions.yml`。
- 如果存在，读取该文件并查找 `hooks.before_tasks` 键下的条目。
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

1. **设置**：从仓库根目录运行 `pwsh -File ".specify/scripts/powershell/setup-tasks.ps1" -Json`，解析 FEATURE_DIR、TASKS_TEMPLATE 和 AVAILABLE_DOCS 列表。`FEATURE_DIR` 和 `TASKS_TEMPLATE` 在提供时必须是绝对路径。`AVAILABLE_DOCS` 是 `FEATURE_DIR` 下可用文档名称/相对路径的列表（例如 `research.md` 或 `contracts/`）。对于参数中的单引号，如 "I'm Groot"，使用转义语法：例如 'I'\''m Groot'（或尽可能使用双引号："I'm Groot"）。

2. **加载设计文档**：从 FEATURE_DIR 读取：
   - **必需**：plan.md（技术栈、库、结构）、spec.md（带有优先级的用户故事）。
   - **可选**：data-model.md（实体）、contracts/（接口契约）、research.md（决策）、quickstart.md（测试场景）。
   - **如果存在**：加载 `.specify/memory/constitution.md` 以获取项目原则和治理约束。
   - 注意：并非所有项目都有所有文档。根据可用的内容生成任务。

3. **执行任务生成工作流**：
   - 加载 plan.md 并提取技术栈、库、项目结构。
   - 加载 spec.md 并提取带有优先级的用户故事（P1、P2、P3 等）。
   - 如果 data-model.md 存在：提取实体并映射到用户故事。
   - 如果 contracts/ 存在：将接口契约映射到用户故事。
   - 如果 research.md 存在：提取决策用于设置任务。
   - 按用户故事组织生成任务（参见下方的任务生成规则）。
   - 生成显示用户故事完成顺序的依赖图。
   - 创建每个用户故事的并行执行示例。
   - 验证任务完整性（每个用户故事都有所有需要的任务，可独立测试）。

4. **生成 tasks.md**：从 TASKS_TEMPLATE（来自上述 JSON 输出）读取任务模板并将其用作结构。如果 TASKS_TEMPLATE 为空，回退到 `.specify/templates/tasks-template.md`。填充：
   - 来自 plan.md 的正确功能名称。
   - 阶段 1：设置任务（项目初始化）。
   - 阶段 2：基础任务（所有用户故事的阻塞性前置条件）。
   - 阶段 3+：每个用户故事一个阶段（按 spec.md 中的优先级顺序）。
   - 每个阶段包括：故事目标、独立测试标准、测试（如果请求）、实现任务。
   - 最终阶段：润色与跨领域关注点。
   - 所有任务必须遵循严格的检查清单格式（参见下方的任务生成规则）。
   - 每个任务有清晰的文件路径。
   - 显示故事完成顺序的依赖部分。
   - 每个故事的并行执行示例。
   - 实现策略部分（MVP 优先，增量交付）。

## 强制执行后钩子

**在向用户报告完成之前，必须完成此部分。**

检查项目根目录中是否存在 `.specify/extensions.yml`。
- 如果不存在，或 `hooks.after_tasks` 下没有注册钩子，跳过到完成报告。
- 如果存在，读取该文件并查找 `hooks.after_tasks` 键下的条目。
- 如果 YAML 无法解析或无效，静默跳过钩子检查并继续到完成报告。
- 过滤掉 `enabled` 显式为 `false` 的钩子。将没有 `enabled` 字段的钩子视为默认启用。
- 对于每个剩余的钩子，**不要**尝试解释或评估钩子的 `condition` 表达式：
  - 如果钩子没有 `condition` 字段，或其值为 null/空，将钩子视为可执行。
  - 如果钩子定义了非空的 `condition`，跳过该钩子，将条件评估交由 HookExecutor 实现处理。
- 从钩子命令名称构造斜杠命令时，将点号（`.`）替换为连字符（`-`）。例如，`speckit.git.commit` → `/speckit-git-commit`。
- 对于每个可执行的钩子，根据其 `optional` 标志输出以下内容：
  - **强制钩子**（`optional: false`）— **必须为每个强制钩子输出 `EXECUTE_COMMAND:`**：
    ```
    ## 扩展钩子

    **自动钩子**：{extension}
    正在执行：`/{command}`
    EXECUTE_COMMAND：{command}
    ```
  - **可选钩子**（`optional: true`）：
    ```
    ## 扩展钩子

    **可选钩子**：{extension}
    命令：`/{command}`
    描述：{description}

    提示：{prompt}
    执行方式：`/{command}`
    ```

## 完成报告

输出生成的 tasks.md 路径和摘要：
- 总任务数。
- 每个用户故事的任务数。
- 识别到的并行机会。
- 每个故事的独立测试标准。
- 建议的 MVP 范围（通常仅用户故事 1）。
- 格式验证：确认所有任务遵循检查清单格式（复选框、ID、标签、文件路径）。

任务生成的上下文：$ARGUMENTS

tasks.md 应可立即执行 — 每个任务必须足够具体，使 LLM 可以在没有额外上下文的情况下完成它。

## 任务生成规则

**关键**：任务必须按用户故事组织，以支持独立的实现和测试。

**测试是可选的**：仅在功能规格中明确请求或用户要求 TDD 方法时生成测试任务。

### 检查清单格式（必需）

每个任务必须严格遵循以下格式：

```text
- [ ] [TaskID] [P?] [Story?] 描述及文件路径
```

**格式组成**：

1. **复选框**：始终以 `- [ ]` 开头（Markdown 复选框）。
2. **任务 ID**：顺序编号（T001、T002、T003...），按执行顺序排列。
3. **[P] 标记**：仅在任务可并行时包含（不同文件，不依赖未完成的任务）。
4. **[Story] 标签**：仅对用户故事阶段任务必需。
   - 格式：[US1]、[US2]、[US3] 等（映射到 spec.md 中的用户故事）。
   - 设置阶段：无故事标签。
   - 基础阶段：无故事标签。
   - 用户故事阶段：必须有故事标签。
   - 润色阶段：无故事标签。
5. **描述**：清晰的行动及确切文件路径。

**示例**：

- ✅ 正确：`- [ ] T001 按实现计划创建项目结构`
- ✅ 正确：`- [ ] T005 [P] 在 src/middleware/auth.py 中实现认证中间件`
- ✅ 正确：`- [ ] T012 [P] [US1] 在 src/models/user.py 中创建 User 模型`
- ✅ 正确：`- [ ] T014 [US1] 在 src/services/user_service.py 中实现 UserService`
- ❌ 错误：`- [ ] Create User model`（缺少 ID 和 Story 标签）
- ❌ 错误：`T001 [US1] Create model`（缺少复选框）
- ❌ 错误：`- [ ] [US1] Create User model`（缺少任务 ID）
- ❌ 错误：`- [ ] T001 [US1] Create model`（缺少文件路径）

### 任务组织

1. **从用户故事（spec.md）** — 主要组织方式：
   - 每个用户故事（P1、P2、P3...）获得自己的阶段。
   - 将所有相关组件映射到其故事：
     - 该故事需要的模型。
     - 该故事需要的服务。
     - 该故事需要的接口/UI。
     - 如果请求了测试：该故事特定的测试。
   - 标记故事依赖（大多数故事应独立）。

2. **从契约**：
   - 将每个接口契约映射到其服务的用户故事。
   - 如果请求了测试：每个接口契约 → 在该故事阶段的实现之前有契约测试任务 [P]。

3. **从数据模型**：
   - 将每个实体映射到需要它的用户故事。
   - 如果实体服务于多个故事：放在最早的故事或设置阶段。
   - 关系 → 相应故事阶段的服务层任务。

4. **从设置/基础设施**：
   - 共享基础设施 → 设置阶段（阶段 1）。
   - 基础/阻塞性任务 → 基础阶段（阶段 2）。
   - 故事特定设置 → 在该故事的阶段内。

### 阶段结构

- **阶段 1**：设置（项目初始化）。
- **阶段 2**：基础（阻塞性前置条件 — 必须在用户故事之前完成）。
- **阶段 3+**：按优先级顺序的用户故事（P1、P2、P3...）。
  - 每个故事内：测试（如果请求）→ 模型 → 服务 → 端点 → 集成。
  - 每个阶段应是一个完整的、可独立测试的增量。
- **最终阶段**：润色与跨领域关注点。

## 完成当

- [ ] tasks.md 已生成，包含所有阶段、任务 ID 和文件路径。
- [ ] 扩展钩子已根据强制执行后钩子中的规则派遣或跳过。
- [ ] 已向用户报告完成情况，包含任务计数、故事分解和 MVP 范围。
