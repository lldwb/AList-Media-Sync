# 功能规格：文档体系优化

**功能分支**：`011-docs-system-optimization`

**创建日期**：2026-07-02

**状态**：已排期

**输入**：用户描述："按照已评估的文档规划方案，创建"文档体系优化"功能规格。需求要点：1. 在项目根目录新增 docs/ 目录作为人类开发者文档主目录，建立"根级入口 + docs/ 主题文档 + specs/ 冻结档案"三层结构。2. 需要创建以下文档（按优先级）：CHANGELOG.md、docs/01-项目概述.md、docs/02-开发环境搭建.md、docs/03-架构设计.md、docs/04-配置说明.md（SSOT）、docs/05-API接口文档.md（通过代码注解自动生成）、docs/06-运维部署.md、docs/architecture/模块文档、docs/operations/。3. 精简 AGENTS.md 和 README.md。4. specs/ 历史制品保持冻结不动；md/ 外部对接 API 保持不动。5. 遵循项目章程 10 条原则。"

## 用户场景与测试 *（强制）*

### 用户故事 1 — 新开发者快速理解项目并搭建环境（优先级：P1）

一位新加入项目的后端开发者首次克隆代码库后，希望在最短时间内理解"这个项目做什么、技术栈是什么、如何本地跑起来"。他打开 README.md 获得一句话定位与三种部署方式入口，点击导航进入项目概述文档了解核心功能与技术栈，再进入开发环境搭建文档按步骤完成 JDK 21、Maven Wrapper、前端构建与本地必填环境变量配置，最终成功启动应用并访问 Web 管理界面。

**为什么是此优先级**：新成员上手速度直接决定项目的人力可扩展性。当前文档散落在 AGENTS.md、README.md 与 10 个 spec 的 quickstart.md 中，多头入口导致新人无从选择，是文档体系最严重的可用性问题。

**独立测试**：可以通过"让一位未接触过本项目的开发者仅依据 README.md → docs/01 → docs/02 的链路完成本地启动"完整测试，并交付"30 分钟内成功访问 Web 管理界面"的价值。

**验收场景**：

1. **假设** 一位新开发者克隆了代码库且本机已安装 JDK 21 与 Node 22，**当** 他按 README.md 的导航依次阅读 docs/01-项目概述.md 与 docs/02-开发环境搭建.md，**则** 能在无需查阅任何 spec 或源码的情况下完成本地启动并访问 Web 管理界面
2. **假设** 开发者本地未设置 `ALIST_BASE_URL` 与 `ALIST_TOKEN`，**当** 他阅读 docs/02 中的本地开发配置章节，**则** 能明确知道哪些环境变量为必填、哪些可使用默认值，并理解 `dataDir` 路径的作用
3. **假设** 开发者在启动过程中遇到端口冲突或 H2 数据库初始化失败，**当** 他查阅 docs/02 的常见问题部分，**则** 能找到对应的排障指引
4. **假设** 一位希望参与贡献的开发者首次访问仓库，**当** 他打开根目录的 CONTRIBUTING.md，**则** 能通过链接进入 docs/02 完成环境搭建、了解 Conventional Commits 中文提交规范与 Spec Kit 工作流入口，无需阅读 AGENTS.md 即可开始贡献

---

### 用户故事 2 — 运维人员完成 Docker 部署与配置管理（优先级：P1）

一位负责生产部署的运维人员需要将项目以 Docker 形态部署到 Linux 服务器。他阅读运维部署文档，依据环境变量清单一次性配置好 `.env` 文件（区分必填与选填、明确敏感项），执行 `docker compose up -d`，通过健康检查确认服务就绪，并在后续运维中能够依据故障排查文档处理日志查询、转码失败、Webhook 不触发等常见问题。

**为什么是此优先级**：生产部署是项目交付的最后一公里，配置错误或运维文档缺失会直接导致线上故障。当前环境变量散落在 Dockerfile、.env 模板、application.yaml、AppProperties.java 四处，无单一权威清单，是部署阶段最高频的踩坑来源。

**独立测试**：可以通过"让一位运维人员仅依据 docs/06-运维部署.md 与 docs/operations/ 完成全新服务器的部署与首次健康检查"完整测试，并交付"15 分钟内服务可通过健康检查端点"的价值。

**验收场景**：

1. **假设** 一台干净的 Linux 服务器已安装 Docker 与 Docker Compose，**当** 运维人员按 docs/06 的步骤配置 `.env` 并执行 `docker compose up -d`，**则** 服务在 60 秒内通过 `/actuator/health` 健康检查
2. **假设** 运维人员需要调整日志级别或开启 DEBUG 排查问题，**当** 他查阅 docs/operations/环境变量清单.md，**则** 能找到 `LOGGING_LEVEL` 等运维相关变量的完整清单与默认值
3. **假设** 生产环境出现转码任务失败但无业务报错，**当** 运维人员按 docs/operations/故障排查与日志.md 的指引查看 error.log 与 traceId，**则** 能定位到具体失败原因与受影响的任务链路

---

### 用户故事 3 — 维护者通过单一权威来源查询配置与 API（优先级：P2）

一位项目维护者在进行功能开发时需要确认某个 `app.*` 配置项对应的环境变量名、默认值与作用，以及某个 Controller 暴露的接口契约。他打开配置说明文档查阅 Relaxed Binding 映射表，打开 API 接口文档查阅注解自动生成的接口清单，二者均为单一权威来源（SSOT），无需在多个文件间交叉比对。

**为什么是此优先级**：配置与 API 是开发者日常最高频查阅的两类文档。当前配置散落在 4 处、API 契约散落在 6 个 spec 的 contracts/ 子目录，维护者每次都要在多文件间跳转，是开发效率的核心痛点。API 文档通过代码注解自动生成可避免文档与实现的漂移。

**独立测试**：可以通过"让维护者仅查阅 docs/04-配置说明.md 回答任意 app.* 配置项的环境变量与默认值"以及"运行生成命令后查阅 docs/05-API接口文档.md 回答任意端点的路径、方法与响应结构"完整测试，并交付"查询任意配置项或 API 端点耗时 < 30 秒"的价值。

**验收场景**：

1. **假设** 维护者想知道 `app.transcode.max-concurrent-transcode` 对应的环境变量名与默认值，**当** 他查阅 docs/04-配置说明.md 的映射表，**则** 能一次性获得 `TRANSCODE_MAX_CONCURRENT=32` 及其作用说明
2. **假设** 维护者新增了一个 REST 端点并在 Controller 上添加了标准注解，**当** 他执行 API 文档生成命令，**则** 新端点自动出现在 docs/05-API接口文档.md 中，无需手写
3. **假设** 维护者需要确认 `ALIST_CRYPTO_KEY` 是否为必填以及缺失时的行为，**当** 他查阅 docs/04，**则** 能看到"生产必设、缺失时随机生成"的明确标注

---

### 用户故事 4 — 技术负责人评估架构与模块边界（优先级：P2）

一位技术负责人或代码审查者在评审 PR 时需要快速确认某个模块的职责边界、核心类与扩展点。他阅读架构设计文档了解分层结构与核心类职责，按需进入 architecture/ 子目录查阅具体模块（common、storage、sync、transcode、webhook）的细化文档，确认改动是否符合分层原则与模块边界。

**为什么是此优先级**：架构文档是代码审查与架构决策的基础。当前架构总览埋在 AGENTS.md 中（与 AI 指令耦合），模块文档缺失，审查者只能读代码或 spec，效率低且容易遗漏跨模块影响。

**独立测试**：可以通过"让审查者仅依据 docs/03-架构设计.md 与 docs/architecture/ 判断一处改动是否违反分层或模块边界"完整测试，并交付"架构相关审查决策耗时减半"的价值。

**验收场景**：

1. **假设** 审查者需要确认 `AppProperties` 的职责，**当** 他查阅 docs/03-架构设计.md 的核心类职责章节，**则** 能看到"`@ConfigurationProperties(prefix="app")` 绑定所有 app.* 配置，是配置层唯一入口"的明确说明
2. **假设** 审查者需要判断一处文件操作代码应归属哪个模块，**当** 他查阅 docs/architecture/模块-storage.md 与模块-sync.md 的职责边界章节，**则** 能明确区分存储引擎策略与同步任务逻辑的边界
3. **假设** 新增功能涉及认证与加密，**当** 审查者查阅 docs/architecture/交叉关注点.md，**则** 能了解 AuthInterceptor、BCrypt、CryptoConverter、CryptoKeyEnvironmentPostProcessor 的协作关系

---

### 用户故事 5 — 项目演进可追溯（优先级：P3）

任何关注项目演进的人（维护者、用户、二次开发者）希望了解版本变更历史。他阅读 CHANGELOG.md 看到按版本倒序排列的 Added/Changed/Fixed/Removed 分类变更记录，能够快速判断某个版本引入了哪些功能、修复了哪些问题，而无需翻阅 git log。

**为什么是此优先级**：版本演进轨迹是项目成熟度与可信度的信号。当前缺失 CHANGELOG，版本变化只能通过 git log 追溯，对非核心贡献者不友好。

**独立测试**：可以通过"让一位外部用户仅依据 CHANGELOG.md 判断某功能从哪个版本开始支持"完整测试，并交付"版本变更查询无需 git 技能"的价值。

**验收场景**：

1. **假设** 用户想知道转码临时文件后缀配置从哪个版本引入，**当** 他查阅 CHANGELOG.md，**则** 能在对应版本条目中找到该功能
2. **假设** 维护者完成一次功能迭代，**当** 他按 Keep a Changelog 格式在 CHANGELOG.md 顶部新增版本条目，**则** 该条目包含 Added/Changed/Fixed/Removed 分类且日期明确

---

### 边界情况

- 当文档内容与代码实现出现不一致时会发生什么？—— 配置与 API 文档因采用 SSOT 与注解自动生成机制，不一致会被生成命令暴露；其他文档通过章程原则 IX（实现后文档同步）约束，本规格不引入额外校验机制。
- 当 specs/ 历史制品与新文档存在内容重叠时会发生什么？—— specs/ 保持冻结作为"功能设计档案"，新文档描述"系统当前状态"，二者通过链接互引，不互相覆盖。
- 当新增功能需要新增模块文档时会发生什么？—— 维护者按 architecture/ 现有模块文档的统一结构（职责边界 → 核心类 → 关键流程 → 扩展点 → 关联 spec）补充。
- 当 API 注解生成工具与 Spring Boot 4.1.0 不兼容时会发生什么？—— 已澄清：采用 SpringDoc v3.0.3（基于 Spring Boot 4.0.5 构建，官方支持 4.x 系列），与项目 Spring Boot 4.1.0 同属 4.x，兼容性有官方背书；若 plan 阶段实测发现 4.1.0 与 4.0.5 的小版本差异导致问题，回退到半自动方案（注解保留，生成命令辅以人工校对）。
- 当 CHANGELOG 需要回溯补全 0.0.1 历史版本时会发生什么？—— 通过 git log 回溯整理，0.0.1-SNAPSHOT 之前的版本若无法精确还原则标注"历史版本"。

## 需求 *（强制）*

### 功能需求

- **FR-001**：系统 MUST 在项目根目录新增 `docs/` 目录作为人类开发者文档主目录，建立"根级入口 + docs/ 主题文档 + specs/ 冻结档案"三层结构
- **FR-002**：系统 MUST 在项目根目录新增 `CHANGELOG.md`，遵循 Keep a Changelog 格式，按版本倒序排列，包含 Added/Changed/Fixed/Removed 分类
- **FR-003**：系统 MUST 创建 `docs/01-项目概述.md`，包含项目背景、核心功能清单、技术栈说明、非目标边界
- **FR-004**：系统 MUST 创建 `docs/02-开发环境搭建.md`，包含 JDK 21 安装与配置、Maven Wrapper 使用、前端构建（Node 22 + npm）、本地开发必填配置（dataDir、ALIST_BASE_URL、ALIST_TOKEN、ALIST_CRYPTO_KEY）、三种启动方式、常见启动问题排障
- **FR-005**：系统 MUST 创建 `docs/03-架构设计.md`，包含分层架构图、包结构总览、核心类职责说明（AppProperties 等）、模块间依赖关系、交叉关注点链接
- **FR-006**：系统 MUST 创建 `docs/04-配置说明.md` 作为配置单一权威来源（SSOT），包含 Spring Boot Relaxed Binding 机制说明、配置优先级、完整 `app.*` 配置项与环境变量映射表、非 `app.*` 环境变量清单、敏感项标记与生产建议
- **FR-007**：系统 MUST 创建 `docs/05-API接口文档.md`，采用通过代码注解自动生成的形式：在 Controller 与 DTO 上添加标准注解（如 `@Tag`、`@Operation`、`@Schema` 等），通过生成命令产出接口文档，文档内容与代码注解保持同步
- **FR-008**：系统 MUST 在 docs/05 中说明注解方案选型（SpringDoc OpenAPI v3.0.3，基于 Spring Boot 4.0.5，官方支持 Spring Boot 4.x）、集成方式（Maven 依赖 + Spring Boot 自动配置）、生成命令（运行时访问 `/v3/api-docs` 与 `/swagger-ui.html`，或通过脚本导出静态文件）、输出位置，并明确标注生成文档为派生产物，源真值在代码注解中
- **FR-009**：系统 MUST 创建 `docs/06-运维部署.md`，包含部署方式对比表、Docker 部署完整流程、环境变量配置清单引用、数据持久化策略、运维注意事项（FFmpeg 依赖、转码并发与内存关系、虚拟线程监控、actuator 端点、诊断包生成、升级回滚与备份）
- **FR-010**：系统 MUST 创建 `docs/architecture/` 子目录，包含五大模块细化文档（common、storage、sync、transcode、webhook）与一份交叉关注点文档，每份模块文档采用统一结构：职责边界 → 核心类 → 关键流程 → 扩展点 → 关联 spec
- **FR-011**：系统 MUST 创建 `docs/operations/` 子目录，包含环境变量清单（全量表，含默认值、是否必填、对应配置属性、示例）与故障排查与日志文档（日志级别调整、traceId 串联查询、常见问题 FAQ）
- **FR-012**：系统 MUST 精简 `AGENTS.md`，移除"项目架构总览"章节（迁移至 docs/03），仅保留 AI 协作指令、章程引用与 Spec Kit 工作流说明
- **FR-013**：系统 MUST 精简 `README.md` 至约 80 行，仅保留项目一句话定位、技术栈徽章、三种部署方式快速开始（各约 5 行）、以及指向 docs/ 各主题的导航链接，移除配置详解章节
- **FR-014**：系统 MUST 保持 `specs/` 历史制品冻结不动，不修改、不删除、不重命名现有 001-010 的任何文件
- **FR-015**：系统 MUST 保持 `md/` 外部对接系统 API 文档不动，不修改、不删除、不重命名
- **FR-016**：所有新增文档 MUST 使用简体中文编写（遵循章程原则 IV），对外 API 字段命名与错误信息保持英文
- **FR-017**：新增文档在描述架构与配置时 MUST 遵循章程原则 I（分层架构）、原则 VI（YAGNI，不引入超出当前所需的内容）、原则 VII（日志规范相关引用需准确）
- **FR-018**：API 注解自动生成方案引入的第三方依赖 MUST 在 plan.md 的「复杂性追踪」中记录并证明合理性（遵循章程原则 VI），优先选择与 Spring Boot 内置能力兼容的方案
- **FR-019**：API 注解自动生成方案采用 SpringDoc OpenAPI v3.0.3（`springdoc-openapi-starter-webmvc-ui`，基于 Spring Boot 4.0.5 构建，官方支持 Spring Boot 4.x），在 Controller 与 DTO 上添加 `@Tag`、`@Operation`、`@Schema` 等标准 OpenAPI 3 注解，通过 SpringDoc 运行时自动生成 OpenAPI JSON/YAML 与 Swagger UI。该依赖 MUST 在 plan.md 的「复杂性追踪」中记录并证明合理性（遵循章程原则 VI）
- **FR-020**：系统 MUST 在项目根目录新增 `CONTRIBUTING.md`（约 60-80 行），作为人类贡献者的参与入口，包含：开发环境搭建（链接到 docs/02，不重复）、分支与提交规范（Conventional Commits 中文格式，遵循章程原则 IV）、Spec Kit 工作流参与方式（链接到 AGENTS.md）、代码审查门禁（链接到 AGENTS.md 章程合规检查清单）、日志与测试规范（链接到 constitution.md 原则 V、VII）。CONTRIBUTING.md MUST NOT 复制章程或 AGENTS.md 内容，仅引用；MUST NOT 引入 CODE_STYLE.md（代码规范由 constitution.md + AGENTS.md 体系承载，避免 SSOT 破坏与 YAGNI 违规）

### 关键实体 *（如果功能涉及数据则包含）*

- **主题文档**：docs/ 目录下的 6 个编号主文档（01-06），每个承担单一职责，面向人类开发者，描述系统当前状态
- **模块文档**：docs/architecture/ 下的 5 份模块细化文档 + 1 份交叉关注点文档，采用统一结构描述各业务模块
- **运维文档**：docs/operations/ 下的环境变量清单与故障排查文档，面向生产部署与运维场景
- **配置 SSOT**：docs/04-配置说明.md 作为配置说明的唯一权威文档，AppProperties.java 作为代码层 SSOT，application.yaml 作为运行时默认值，三者通过约定保持一致
- **API 派生文档**：docs/05-API接口文档.md 为派生产物，源真值在 Controller 与 DTO 的代码注解中，通过生成命令产出
- **变更日志**：CHANGELOG.md，按版本倒序记录项目演进，遵循 Keep a Changelog 格式
- **贡献者入口**：CONTRIBUTING.md（根目录），面向人类贡献者的参与流程总入口，引用 docs/02、AGENTS.md、constitution.md，不复制内容

## 成功标准 *（强制）*

### 可衡量的结果

- **SC-001**：一位未接触过本项目的开发者仅依据 README.md → docs/01 → docs/02 的文档链路，可在 30 分钟内完成本地环境搭建并成功访问 Web 管理界面
- **SC-002**：一位运维人员仅依据 docs/06-运维部署.md 与 docs/operations/，可在 15 分钟内完成全新 Linux 服务器的 Docker 部署并通过健康检查
- **SC-003**：维护者查询任意 `app.*` 配置项的环境变量、默认值与作用，或查询任意 REST 端点的路径、方法与响应结构，耗时均 < 30 秒（对应 docs/04 SSOT 与 docs/05 注解自动生成）
- **SC-004**：API 文档与代码实现的一致性可由生成命令验证——执行生成命令后，docs/05 生成区内容与 Controller/DTO 注解的偏差为 0（无手写漂移）；手工引导区不在校验范围
- **SC-005**：项目根目录文档总入口（README.md）精简至约 80 行，AGENTS.md 精简至约 100 行，二者不再承担对方职责
- **SC-006**：新增文档覆盖项目全部 5 个业务模块（common、storage、sync、transcode、webhook）、全部 17 个 `app.*` 配置项与人类贡献者参与入口（CONTRIBUTING.md），无遗漏
- **SC-007**：specs/ 历史制品（001-010）与 md/ 外部对接文档在本次变更后文件数与内容哈希均不变（冻结验证）
- **SC-008**：CHANGELOG.md 可让外部用户在不查阅 git log 的情况下，判断任一已发布功能从哪个版本开始支持

## 假设

- 假设目标用户为后端开发者、运维人员与技术负责人，具备基本的 Java/Docker 知识，无需从零科普
- 假设本地开发环境已具备 JDK 21 与 Node 22 的安装能力，文档只需说明配置要点而非操作系统级安装教程
- 假设 SpringDoc OpenAPI v3.0.3（基于 Spring Boot 4.0.5 构建）与项目 Spring Boot 4.1.0 兼容；二者同属 4.x 系列，SpringDoc 已官方声明支持 Spring Boot 4.x。若 plan 阶段实测发现小版本差异导致问题，回退为"注解标记 + 半自动生成"方案
- 假设 specs/ 001-010 的历史制品作为"功能设计档案"具有保留价值，无需迁移或合并到新文档体系
- 假设 md/ 目录下的外部对接系统 API 文档（AList、录播姬）为只读参考资料，与本项目文档体系解耦
- 假设 CHANGELOG.md 的 0.0.1-SNAPSHOT 之前历史版本可通过 git log 回溯整理，无法精确还原的部分标注"历史版本"
- 假设配置说明文档（docs/04）的映射表可在 plan 阶段通过解析 AppProperties.java 与 application.yaml 自动抽取，减少手写误差
- 假设 API 注解自动生成命令可集成到 Maven 构建阶段或作为独立脚本执行，具体集成方式在 plan 阶段确定

## 澄清

### 会话 2026-07-02

- Q: API 注解自动生成方案应采用哪种注解库（需兼容 Spring Boot 4.1.0）？ → A: 采用 SpringDoc OpenAPI v3.0.3（`springdoc-openapi-starter-webmvc-ui`）。经查证 GitHub release（v3.0.3，2026-04-11 发布），其 "Changed" 明确写明 "Upgrade Spring Boot to version 4.0.5"，即 v3.x 系列已官方支持 Spring Boot 4.x。项目使用的 4.1.0 与其基于的 4.0.5 同属 4.x 系列，兼容性有官方背书，无需降级方案。此前对 SpringDoc 仅支持 Boot 3.x 的担忧已被消除（源于 SpringDoc 主站 README 仅声明 v2 支持 Boot 3.x，未覆盖 v3 信息）。
- Q: 是否需要新增 CONTRIBUTING.md（如何参与开发）与 CODE_STYLE.md（代码规范）？ → A: 纳入 CONTRIBUTING.md（根目录，最小化引入，约 60-80 行，仅引用 docs/02/AGENTS.md/constitution.md 不复制内容，填补"人类贡献者入口"空白，GitHub 原生集成识别根目录）；不纳入 CODE_STYLE.md（与 constitution.md + AGENTS.md 严重重复，违反 YAGNI 与 SSOT，加剧文档散落）。
