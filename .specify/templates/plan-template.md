# 实现计划：[功能名称]

**分支**：`[###-功能名称]` | **日期**：[日期] | **规格**：[链接]

**输入**：来自 `/specs/[###-功能名称]/spec.md` 的功能规格

**注意**：此模板由 `/speckit-plan` 命令填充。参见 `.specify/templates/plan-template.md` 了解执行工作流。

## 摘要

[从功能规格中提取：主要需求 + 研究中的技术方法]

## 技术上下文

<!--
  需要操作：用项目的技术细节替换此部分中的内容。
  此处的结构以建议性质呈现，用于指导迭代过程。
-->

**语言/版本**：[例如 Python 3.11、Swift 5.9、Rust 1.75 或 需要澄清]

**主要依赖**：[例如 FastAPI、UIKit、LLVM 或 需要澄清]

**存储**：[如适用，例如 PostgreSQL、CoreData、文件 或 不适用]

**测试**：[例如 pytest、XCTest、cargo test 或 需要澄清]

**目标平台**：[例如 Linux server、iOS 15+、WASM 或 需要澄清]

**项目类型**：[例如 library/cli/web-service/mobile-app/compiler/desktop-app 或 需要澄清]

**性能目标**：[领域特定，例如 1000 req/s、10k lines/sec、60 fps 或 需要澄清]

**约束**：[领域特定，例如 <200ms p95、<100MB 内存、离线可用 或 需要澄清]

**规模/范围**：[领域特定，例如 10k 用户、1M LOC、50 screens 或 需要澄清]

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

[根据章程文件确定的门禁]

## 项目结构

### 文档（本功能）

```text
specs/[###-功能]/
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── research.md          # 阶段 0 输出（/speckit-plan 命令）
├── data-model.md        # 阶段 1 输出（/speckit-plan 命令）
├── quickstart.md        # 阶段 1 输出（/speckit-plan 命令）
├── contracts/           # 阶段 1 输出（/speckit-plan 命令）
└── tasks.md             # 阶段 2 输出（/speckit-tasks 命令 — 非 /speckit-plan 创建）
```

### 源代码（仓库根目录）
<!--
  需要操作：用此功能的具体布局替换下方的占位符树。
  删除未使用的选项，并用实际路径（例如，apps/admin、packages/something）展开所选结构。
  交付的计划不得包含"选项"标签。
-->

```text
# [如未使用请删除] 选项 1：单项目（默认）
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [如未使用请删除] 选项 2：Web 应用（当检测到 "前端" + "后端" 时）
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [如未使用请删除] 选项 3：移动端 + API（当检测到 "iOS/Android" 时）
api/
└── [同上述后端结构]

ios/ 或 android/
└── [平台特定结构：功能模块、UI 流程、平台测试]
```

**结构决策**：[记录所选结构并引用上述捕获的实际目录]

## 复杂性追踪

> **仅在章程检查有必须证明合理性的违规时填充**

| 违规 | 为什么需要 | 被拒绝的更简单替代方案及原因 |
|------|-----------|------------------------|
| [例如，第 4 个项目] | [当前需求] | [为什么 3 个项目不够] |
| [例如，Repository 模式] | [具体问题] | [为什么直接 DB 访问不够] |
