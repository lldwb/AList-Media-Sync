# AGENTS.md — AList-Media-Sync

## AI 工作指令

以下规则约束 AI 在此项目中的行为，优先级高于任何默认行为。**规则与 `.specify/memory/constitution.md`（章程版本 1.11.0）的不可协商原则对齐，冲突时以章程为准**。

> **文件权重体系**：constitution.md（宪法）> AGENTS.md 根级（法律·全局）> 前端/后端 AGENTS.md（行政法规）> 模块 AGENTS.md（地方性法规）。本文件为全局级法律层，覆盖项目的日常修改和 AI 行为约束，前端/后端及各模块 AGENTS.md 在此基础上逐级细化。
> **人类开发者文档体系**（与 AI 上下文体系并列）：根级入口（README/AGENTS/CHANGELOG/CONTRIBUTING）> docs/ 主题文档（系统当前状态）> specs/ 冻结档案（功能设计档案）。详见章程原则 XI。

1. **直接执行，跳过冗余分析** — 收到任务后直接开始实现，不要先输出"我先了解代码库结构"或"让我分析需求"之类的开场白。AGENTS.md 已包含足够的架构上下文，直接动手。
2. **先读模块 AGENTS.md** — 修改某个模块前，先读取该模块目录下的 `AGENTS.md` 了解功能、作用和关联。模块索引见下方"模块 AGENTS.md 索引"章节。
3. **禁止凭空编写规格文档** — 除非用户明确要求编写 spec/plan/tasks，否则不要创建或修改 `specs/` 目录下的任何文件。功能设计通过代码和注释表达。（对应章程原则 VI: YAGNI）
   - `specs/` 为冻结档案，描述设计决策而非当前状态；`docs/` 为系统当前状态，随实现同步更新（对应章程原则 XI）
4. **先读后改，精准定位** — 修改前先阅读目标文件，理解现有实现后再改。不要猜测代码内容。
5. **最小化输出** — 完成任务后简要说明做了什么，不要长篇总结。代码本身就是最好的文档。
6. **遵循现有模式** — 新增代码保持与同模块现有代码一致的风格、命名和注释密度。不要引入新的架构模式除非任务明确要求。
7. **严格遵守分层架构** — Controller 不写业务逻辑，Service 承载核心逻辑，Repository 仅数据持久化。禁止跨层调用。（章程原则 I）
8. **代码变更必须同步测试** — 每次修改 Java 类文件后，MUST 同步修改或新增对应的单元测试。（章程原则 V）
9. **日志规范不可省略** — 所有重要操作 MUST 按 DEBUG/INFO/WARN/ERROR 四级输出日志，API 调用和本地文件操作 MUST 记录输入输出。任务入口 MUST 通过 `TraceContext.runWith(...)` 注入 traceId / module / operation MDC 字段；ERROR 级别日志 MUST 同时写入 `app.log` 与 `error.log`；所有 `/api/**` 响应 MUST 携带 `X-Trace-Id`；日志与诊断包 MUST NOT 出现密码 / Token / 密钥等敏感原始值。（章程原则 VII）
10. **中文优先** — 所有文档、注释、日志消息、提交信息 MUST 使用简体中文。对外 API 字段名和错误信息使用英文。（章程原则 IV）
11. **实现后文档同步** — 实现完成后 MUST 同步更新 `docs/` 对应主题文档（配置→docs/04、API→docs/05、架构→docs/03+architecture/、运维→docs/06+operations/）与 `CHANGELOG.md`（Keep a Changelog 格式）。配置/API 以 docs/04、docs/05 为 SSOT，禁止在其他文档重复维护权威内容。（章程原则 IX、XI）
12. **Git 提交规范** — 提交前 MUST 通过 `git status` + `git diff` 分析改动涉及的功能模块；多模块改动 MUST 按模块拆分为多次独立提交（业务逻辑/配置/文档/测试各归一次），单模块或单文件改动 MUST 合并为一次提交不可强制拆分；`git add` MUST 显式指定文件，禁止 `git add -A` / `git add .`；提交信息 MUST 使用中文，采用「简明摘要 + 必要详细说明」结构，遵循 Conventional Commits 与仓库现有风格。（章程原则 XII）

---

## 章程合规检查清单

以下为 constitution.md 中定义的质量门禁，代码审查时必须逐项验证：

| # | 检查项 | 章程原则 |
|---|--------|---------|
| 1 | 分层架构合规（Controller/Service/Repository 职责清晰） | I |
| 2 | 实体有 `@Version` 乐观锁，写操作有 `@Transactional` | II |
| 3 | 统一 `ApiResult<T>` 封装，DTO 不暴露 Entity | III |
| 4 | 注释、日志、提交信息使用简体中文 | IV |
| 5 | Java 类变更同步更新单元测试 | V |
| 6 | 未引入不必要的第三方依赖 | VI |
| 7 | 日志输出符合四级分级，API 调用和文件操作有日志 | VII |
| 8 | spec.md 状态字段已同步更新 | VIII |
| 9 | `/speckit-implement` 后 README.md 已更新 | IX |
| 10 | `/speckit-constitution` 后 AGENTS.md 已同步 | X |
| 11 | `docs/` 主题文档与 `CHANGELOG.md` 已同步，配置/API 以 SSOT 为准 | IX、XI |
| 12 | Git 提交遵循单一职责拆分、显式 `git add`、结构化中文提交信息 | XII |

---

## 项目文档导航

项目采用三层文档结构（章程原则 XI）：根级入口（导航与速查）> `docs/` 主题文档（系统当前状态）> `specs/` 冻结档案（功能设计档案）。本文件仅保留 AI 协作指令与模块索引。

- **根级入口**：[`README.md`](README.md)（快速开始）· [`CHANGELOG.md`](CHANGELOG.md)（版本演进）· [`CONTRIBUTING.md`](CONTRIBUTING.md)（贡献指南）· [`.specify/memory/constitution.md`](.specify/memory/constitution.md)（项目章程·12 条不可协商原则）
- **主题文档**（`docs/`，SSOT 所在）：
  - 项目概述：[`docs/01-项目概述.md`](docs/01-项目概述.md)
  - 开发环境搭建：[`docs/02-开发环境搭建.md`](docs/02-开发环境搭建.md)
  - 架构设计：[`docs/03-架构设计.md`](docs/03-架构设计.md) · [`docs/architecture/`](docs/architecture/)（五大模块细化 + 交叉关注点）
  - 配置说明（**配置 SSOT**）：[`docs/04-配置说明.md`](docs/04-配置说明.md)
  - API 接口文档（**API SSOT**，注解生成）：[`docs/05-API接口文档.md`](docs/05-API接口文档.md)
  - 运维部署：[`docs/06-运维部署.md`](docs/06-运维部署.md) · [`docs/operations/`](docs/operations/)（环境变量清单、故障排查与日志）
- **冻结档案**（`specs/`，功能设计档案，描述设计决策而非当前状态）：见下方"引用规格文档"
- **外部对接参考**（`md/`，外部系统 API 契约，以外部契约为事实来源）：见下方"对接系统文档"

---

## 模块 AGENTS.md 索引

每个模块目录下有一份 `AGENTS.md`，描述该模块的功能、作用和模块间关联。修改某模块时先读其 AGENTS.md。前后端各有一份入口 `AGENTS.md` 作为行政法规层。

### 后端

| 层级 | AGENTS.md 路径 | 一句话说明 |
|------|---------------|-----------|
| 行政法规 | `src/main/java/…/AGENTS.md` | 后端入口（技术栈、模块索引、合规要点） |
| 地方性法规 | `src/main/java/…/common/AGENTS.md` | 共享基础设施（配置、认证、加密、工具） |
| 地方性法规 | `src/main/java/…/execution/AGENTS.md` | 共享任务执行记录（实体 + Repository + VO） |
| 地方性法规 | `src/main/java/…/storage/AGENTS.md` | 策略模式存储引擎（AList 远程 + 本地） |
| 地方性法规 | `src/main/java/…/sync/AGENTS.md` | 文件同步引擎（三模式+三阶段） |
| 地方性法规 | `src/main/java/…/transcode/AGENTS.md` | 媒体转码引擎（三步流程+8状态） |
| 地方性法规 | `src/main/java/…/webhook/AGENTS.md` | Webhook 事件接收+规则匹配 |
| 地方性法规 | `src/main/java/…/ops/AGENTS.md` | 跨模块运维聚合（仪表盘、清理、诊断） |

### 前端

| 层级 | AGENTS.md 路径 | 一句话说明 |
|------|---------------|-----------|
| 行政法规 | `src/main/frontend/AGENTS.md` | 前端入口（技术栈、模块索引、路由表） |
| 地方性法规 | `src/main/frontend/src/api/AGENTS.md` | HTTP 请求封装（fetch + Basic Auth） |
| 地方性法规 | `src/main/frontend/src/types/AGENTS.md` | TypeScript 类型定义 |
| 地方性法规 | `src/main/frontend/src/auth/AGENTS.md` | 认证状态管理（Context + 超时） |
| 地方性法规 | `src/main/frontend/src/components/AGENTS.md` | 可复用 UI 组件（布局/表单/基础） |
| 地方性法规 | `src/main/frontend/src/hooks/AGENTS.md` | React Hooks（轮询/分页） |
| 地方性法规 | `src/main/frontend/src/pages/AGENTS.md` | 页面组件（7个路由页面） |
| 地方性法规 | `src/main/frontend/src/router/AGENTS.md` | Hash 路由表 + 认证守卫 |
| 地方性法规 | `src/main/frontend/src/utils/AGENTS.md` | 工具函数（格式化/校验/Cron） |

### 对接系统文档

修改对接相关代码（`storage/service/engine/AListStorageStrategy`、`webhook/service/WebhookService`）前，MUST 先读 `md/` 下对应外部系统的 AGENTS.md 与接口 md，以外部契约为事实来源。

| 层级 | AGENTS.md 路径 | 一句话说明 |
|------|---------------|-----------|
| 行政法规 | `md/AGENTS.md` | 对接系统总入口（对接模式、系统索引、文档同步） |
| 地方性法规 | `md/alist/AGENTS.md` | AList REST API 参考（auth/fs/public/admin/Schemas，源自 Apifox） |
| 地方性法规 | `md/danmuji/AGENTS.md` | 录播姬 Webhook v2 协议参考（事件矩阵、幂等约束） |

---

## 引用规格文档

如需了解详细设计决策，请参阅：

- 核心业务功能：`specs/001-alist-media-sync/plan.md`
- 转码增强功能：`specs/002-transcode-temp-suffix-config/plan.md`
- 容器化部署：`specs/003-docker-deploy/plan.md`
- Web 管理前端：`specs/004-web-management-frontend/plan.md`
- 一体化启动包：`specs/005-standalone-bootstrap/plan.md`
- 存储引擎重构：`specs/006-storage-engine-refactor/plan.md`
- 密码加密与代码组织：`specs/007-password-encryption-and-code-organization/plan.md`
<!-- SPECKIT START -->
- 轻量诊断系统：`specs/009-lightweight-diagnostics/plan.md`
- 文档体系优化：`specs/011-docs-system-optimization/plan.md`
- 端到端测试基础设施：`specs/012-e2e-test-infrastructure/plan.md`
<!-- SPECKIT END -->
