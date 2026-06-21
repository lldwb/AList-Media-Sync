---
name: "speckit-implement"
description: "通过处理和执行 tasks.md 中定义的所有任务来执行实现计划。"
argument-hint: "可选的实现指导或任务过滤器"
compatibility: "需要包含 .specify/ 目录的 spec-kit 项目结构"
metadata:
  author: "github-spec-kit"
  source: "templates/commands/implement.md"
user-invocable: true
disable-model-invocation: false
---


## 用户输入

```text
$ARGUMENTS
```

**必须**在继续之前考虑用户输入（如果不为空）。

## 预执行检查

**检查扩展钩子（实现之前）**：
- 检查项目根目录中是否存在 `.specify/extensions.yml`。
- 如果存在，读取该文件并查找 `hooks.before_implement` 键下的条目。
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

1. 从仓库根目录运行 `pwsh -File ".specify/scripts/powershell/check-prerequisites.ps1" -Json -RequireTasks -IncludeTasks`，解析 FEATURE_DIR 和 AVAILABLE_DOCS 列表。所有路径必须是绝对路径。对于参数中的单引号，如 "I'm Groot"，使用转义语法：例如 'I'\''m Groot'（或尽可能使用双引号："I'm Groot"）。

2. **检查检查清单状态**（如果 FEATURE_DIR/checklists/ 存在）：
   - 扫描 checklists/ 目录中的所有检查清单文件。
   - 对于每个检查清单，统计：
     - 总项数：所有匹配 `- [ ]` 或 `- [X]` 或 `- [x]` 的行。
     - 已完成项：匹配 `- [X]` 或 `- [x]` 的行。
     - 未完成项：匹配 `- [ ]` 的行。
   - 创建状态表：

     ```text
     | 检查清单 | 总数 | 已完成 | 未完成 | 状态 |
     |----------|------|--------|--------|------|
     | ux.md     | 12   | 12     | 0      | ✓ 通过 |
     | test.md   | 8    | 5      | 3      | ✗ 失败 |
     | security.md | 6  | 6      | 0      | ✓ 通过 |
     ```

   - 计算总体状态：
     - **通过**：所有检查清单的未完成项均为 0。
     - **失败**：一个或多个检查清单有未完成项。

   - **如果有任何检查清单未完成**：
     - 显示带有未完成项计数的表格。
     - **停止**并询问："部分检查清单尚未完成。是否仍要继续实现？（是/否）"
     - 等待用户响应后再继续。
     - 如果用户说"否"或"等待"或"停止"，终止执行。
     - 如果用户说"是"或"继续"或"前进"，继续步骤 3。

   - **如果所有检查清单均已完成**：
     - 显示表格，表明所有检查清单已通过。
     - 自动继续步骤 3。

3. **更新 spec.md 状态为"实现中"**（原则 VIII）：
   - 读取 spec.md，定位 `**状态**` 行
   - 将当前值替换为 `实现中`
   - 写回 spec.md

4. 加载并分析实现上下文：
   - **必需**：读取 tasks.md 以获取完整的任务列表和执行计划。
   - **必需**：读取 plan.md 以获取技术栈、架构和文件结构。
   - **如果存在**：读取 data-model.md 以获取实体和关系。
   - **如果存在**：读取 contracts/ 以获取 API 规格和测试需求。
   - **如果存在**：读取 research.md 以获取技术决策和约束。
   - **如果存在**：读取 .specify/memory/constitution.md 以获取治理约束。
   - **如果存在**：读取 quickstart.md 以获取集成场景。
   - **四层 AGENTS.md 体系**：根据要修改的模块，读取对应层级的 AGENTS.md：
     - 根级 `AGENTS.md` — 全局 AI 工作指令（始终读取）
     - 后端入口 `src/main/java/…/AGENTS.md` — 涉及后端修改时
     - 前端入口 `src/main/frontend/AGENTS.md` — 涉及前端修改时
     - 模块 `AGENTS.md` — 修改具体模块时读取对应模块文件

5. **项目设置验证**：
   - **必需**：根据实际项目设置创建/验证忽略文件：

   **检测与创建逻辑**：
   - 检查以下命令是否成功以确定仓库是否为 git 仓库（如果是，则创建/验证 .gitignore）：

     ```sh
     git rev-parse --git-dir 2>/dev/null
     ```

   - 检查 Dockerfile* 是否存在或 plan.md 中是否提及 Docker → 创建/验证 .dockerignore。
   - 检查 .eslintrc* 是否存在 → 创建/验证 .eslintignore。
   - 检查 eslint.config.* 是否存在 → 确保配置的 `ignores` 条目覆盖必需模式。
   - 检查 .prettierrc* 是否存在 → 创建/验证 .prettierignore。
   - 检查 .npmrc 或 package.json 是否存在 → 创建/验证 .npmignore（如果发布）。
   - 检查 terraform 文件（*.tf）是否存在 → 创建/验证 .terraformignore。
   - 检查是否需要 .helmignore（helm charts 存在）→ 创建/验证 .helmignore。

   **如果忽略文件已存在**：验证其包含基本模式，仅追加缺失的关键模式。
   **如果忽略文件缺失**：使用检测到的技术的完整模式集创建。

   **按技术的常见模式**（来自 plan.md 技术栈）：
   - **Node.js/JavaScript/TypeScript**：`node_modules/`、`dist/`、`build/`、`*.log`、`.env*`
   - **Python**：`__pycache__/`、`*.pyc`、`.venv/`、`venv/`、`dist/`、`*.egg-info/`
   - **Java**：`target/`、`*.class`、`*.jar`、`.gradle/`、`build/`
   - **C#/.NET**：`bin/`、`obj/`、`*.user`、`*.suo`、`packages/`
   - **Go**：`*.exe`、`*.test`、`vendor/`、`*.out`
   - **Ruby**：`.bundle/`、`log/`、`tmp/`、`*.gem`、`vendor/bundle/`
   - **PHP**：`vendor/`、`*.log`、`*.cache`、`*.env`
   - **Rust**：`target/`、`debug/`、`release/`、`*.rs.bk`、`*.rlib`、`*.prof*`、`.idea/`、`*.log`、`.env*`
   - **Kotlin**：`build/`、`out/`、`.gradle/`、`.idea/`、`*.class`、`*.jar`、`*.iml`、`*.log`、`.env*`
   - **C++**：`build/`、`bin/`、`obj/`、`out/`、`*.o`、`*.so`、`*.a`、`*.exe`、`*.dll`、`.idea/`、`*.log`、`.env*`
   - **C**：`build/`、`bin/`、`obj/`、`out/`、`*.o`、`*.a`、`*.so`、`*.exe`、`*.dll`、`autom4te.cache/`、`config.status`、`config.log`、`.idea/`、`*.log`、`.env*`
   - **Swift**：`.build/`、`DerivedData/`、`*.swiftpm/`、`Packages/`
   - **R**：`.Rproj.user/`、`.Rhistory`、`.RData`、`.Ruserdata`、`*.Rproj`、`packrat/`、`renv/`
   - **通用**：`.DS_Store`、`Thumbs.db`、`*.tmp`、`*.swp`、`.vscode/`、`.idea/`

   **工具特定模式**：
   - **Docker**：`node_modules/`、`.git/`、`Dockerfile*`、`.dockerignore`、`*.log*`、`.env*`、`coverage/`
   - **ESLint**：`node_modules/`、`dist/`、`build/`、`coverage/`、`*.min.js`
   - **Prettier**：`node_modules/`、`dist/`、`build/`、`coverage/`、`package-lock.json`、`yarn.lock`、`pnpm-lock.yaml`
   - **Terraform**：`.terraform/`、`*.tfstate*`、`*.tfvars`、`.terraform.lock.hcl`
   - **Kubernetes/k8s**：`*.secret.yaml`、`secrets/`、`.kube/`、`kubeconfig*`、`*.key`、`*.crt`

6. 解析 tasks.md 结构并提取：
   - **任务阶段**：搭建、测试、核心、集成、润色。
   - **任务依赖**：顺序 vs 并行执行规则。
   - **任务详情**：ID、描述、文件路径、并行标记 [P]。
   - **执行流程**：顺序和依赖要求。

7. 按照任务计划执行实现：
   - **逐阶段执行**：完成每个阶段后再进入下一个。
   - **尊重依赖关系**：顺序任务按顺序运行，并行任务 [P] 可以同时运行。
   - **遵循 TDD 方法**：在相应的实现任务之前执行测试任务。
   - **基于文件的协调**：影响相同文件的任务必须顺序运行。
   - **验证检查点**：在继续之前验证每个阶段的完成情况。

8. 实现执行规则：
   - **搭建先行**：初始化项目结构、依赖、配置。
   - **测试先于代码**：如需要，为契约、实体和集成场景编写测试。
   - **核心开发**：实现模型、服务、CLI 命令、端点。
   - **集成工作**：数据库连接、中间件、日志记录、外部服务。
   - **润色和验证**：单元测试、性能优化、文档。

9. 进度跟踪和错误处理：
   - 每个任务完成后报告进度。
   - 如果任何非并行任务失败，停止执行。
   - 对于并行任务 [P]，继续执行成功的任务，报告失败的任务。
   - 提供清晰的错误消息和调试上下文。
   - 如果实现无法继续，建议下一步操作。
   - **重要**：对于已完成的任务，确保在任务文件中将任务标记为 [X]。

10. 完成验证：
   - 验证所有必需任务已完成。
   - 检查实现的功能是否与原始规格匹配。
   - 验证测试通过且覆盖率达到要求。
   - 确认实现遵循技术计划。

注意：此命令假设 tasks.md 中存在完整的任务分解。如果任务不完整或缺失，建议先运行 `/speckit-tasks` 重新生成任务列表。

## 强制执行后钩子

**在向用户报告完成之前，必须完成此部分。**

检查项目根目录中是否存在 `.specify/extensions.yml`。
- 如果不存在，或 `hooks.after_implement` 下没有注册钩子，跳过到完成报告。
- 如果存在，读取该文件并查找 `hooks.after_implement` 键下的条目。
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

报告最终状态，包含已完成工作的摘要。

### 更新 spec.md 状态（原则 VIII）

- **实现开始时**：读取 spec.md，将 `**状态**` 字段更新为 `实现中`，写回 spec.md
- **全部任务完成后**：读取 spec.md，将 `**状态**` 字段更新为 `已完成`，写回 spec.md

## 完成当

- [ ] tasks.md 中的所有任务已完成并标记为 `[X]`。
- [ ] 已根据规格、计划和测试覆盖验证实现。
- [ ] 扩展钩子已根据上述强制执行后钩子的规则派遣或跳过。
- [ ] 已向用户报告完成情况，包含已完成工作的摘要。
