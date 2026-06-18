---
name: "speckit-plan"
description: "使用计划模板执行实现规划工作流，生成设计制品。"
argument-hint: "规划阶段的可选指导"
compatibility: "需要包含 .specify/ 目录的 spec-kit 项目结构"
metadata:
  author: "github-spec-kit"
  source: "templates/commands/plan.md"
user-invocable: true
disable-model-invocation: false
---


## 用户输入

```text
$ARGUMENTS
```

**必须**在继续之前考虑用户输入（如果不为空）。

## 预执行检查

**检查扩展钩子（规划之前）**：
- 检查项目根目录中是否存在 `.specify/extensions.yml`。
- 如果存在，读取该文件并查找 `hooks.before_plan` 键下的条目。
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

1. **设置**：从仓库根目录运行 `.specify/scripts/powershell/setup-plan.ps1 -Json`，解析 JSON 获取 FEATURE_SPEC、IMPL_PLAN、SPECS_DIR、BRANCH。对于参数中的单引号，如 "I'm Groot"，使用转义语法：例如 'I'\''m Groot'（或尽可能使用双引号："I'm Groot"）。

2. **加载上下文**：读取 FEATURE_SPEC 和 `.specify/memory/constitution.md`。加载 IMPL_PLAN 模板（已复制）。

3. **执行计划工作流**：遵循 IMPL_PLAN 模板中的结构：
   - 填充技术上下文（将未知项标记为"需要澄清"）
   - 从章程填充章程检查部分
   - 评估门禁（如果违规无正当理由则报错）
   - 阶段 0：生成 research.md（解决所有"需要澄清"项）
   - 阶段 1：生成 data-model.md、contracts/、quickstart.md
   - 阶段 1：通过运行代理脚本更新代理上下文
   - 设计后重新评估章程检查

## 强制执行后钩子

**在向用户报告完成之前，必须完成此部分。**

检查项目根目录中是否存在 `.specify/extensions.yml`。
- 如果不存在，或 `hooks.after_plan` 下没有注册钩子，跳过到完成报告。
- 如果存在，读取该文件并查找 `hooks.after_plan` 键下的条目。
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

命令在阶段 2 规划之后结束。报告分支、IMPL_PLAN 路径和生成的制品。

## 阶段

### 阶段 0：大纲与研究

1. **从上述技术上下文中提取未知项**：
   - 对于每个"需要澄清"项 → 研究任务。
   - 对于每个依赖 → 最佳实践任务。
   - 对于每个集成 → 模式任务。

2. **生成并派遣研究代理**：

   ```text
   对于技术上下文中的每个未知项：
     任务："为 {功能上下文} 研究 {未知项}"
   对于每个技术选择：
     任务："查找 {领域} 中 {技术} 的最佳实践"
   ```

3. **整合发现**到 `research.md`，使用格式：
   - 决策：[选择了什么]
   - 理由：[为什么选择]
   - 考虑的替代方案：[还评估了什么]

**输出**：research.md，所有"需要澄清"项已解决。

### 阶段 1：设计与契约

**前提条件：** `research.md` 完成。

1. **从功能规格中提取实体** → `data-model.md`：
   - 实体名称、字段、关系。
   - 来自需求的验证规则。
   - 状态转换（如适用）。

2. **定义接口契约**（如果项目有外部接口）→ `/contracts/`：
   - 识别项目向用户或其他系统暴露的接口。
   - 记录适合项目类型的契约格式。
   - 示例：库的公共 API、CLI 工具的命令模式、Web 服务的端点、解析器的语法、应用程序的 UI 契约。
   - 如果项目纯粹是内部的（构建脚本、一次性工具等），则跳过。

3. **创建快速入门验证指南** → `quickstart.md`：
   - 记录可运行的验证场景，证明功能端到端可用。
   - 包括前提条件、设置命令、测试/运行命令和预期结果。
   - 使用链接或引用指向契约和数据模型详情，而不是重复它们。
   - 不要包含完整的实现代码、模型/服务/控制器主体、迁移或完整的测试套件。
   - 将此制品保持为验证/运行指南；实现细节属于 `tasks.md` 和实现阶段。

4. **代理上下文更新**：
   - 更新 `CLAUDE.md` 中 `<!-- SPECKIT START -->` 和 `<!-- SPECKIT END -->` 标记之间的计划引用，指向步骤 1 中创建的计划文件（IMPL_PLAN 路径）。

**输出**：data-model.md、/contracts/*、quickstart.md、更新后的代理上下文文件。

## 关键规则

- 使用绝对路径进行文件系统操作；在文档和代理上下文文件中使用项目相对路径进行引用。
- 门禁失败或未解决的澄清项报错。

## 完成当

- [ ] 计划工作流已执行，设计制品已生成。
- [ ] 扩展钩子已根据上述强制执行后钩子的规则派遣或跳过。
- [ ] 已向用户报告完成情况，包含分支、计划路径和生成的制品。
