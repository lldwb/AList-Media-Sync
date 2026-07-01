---

description: "文档体系优化的任务列表"
---

# 任务：文档体系优化

**输入**：来自 `/specs/011-docs-system-optimization/` 的设计文档

**前提条件**：plan.md（必需）、spec.md（用户故事必需）、research.md、data-model.md、contracts/

**测试**：本功能为文档体系优化，无 Service/Repository 代码逻辑变更，单元测试不适用。验证以 quickstart.md 的 6 个端到端验证场景为准（文档生成可重复性、链接有效性、冻结完整性等），作为润色阶段的验证任务。

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（例如，US1、US2、US3、US4、US5）
- 在描述中包含确切的文件路径

## 路径约定

- 本功能为 Web 应用（后端为主，前端为静态资源），文档产物集中在 `docs/`，代码改动在 `src/main/java/...` 与 `src/main/resources/`
- 路径均相对仓库根目录

---

## 阶段 1：设置（共享基础设施）

**目的**：创建文档目录结构，为后续文档落地做准备

- [ ] T001 创建 `docs/` 主目录与 `docs/architecture/`、`docs/operations/` 子目录，并在 `docs/` 下创建 `.gitkeep`（若 Git 不跟踪空目录）
- [ ] T002 [P] 创建 `scripts/` 目录（若不存在），用于存放生成脚本

---

## 阶段 2：基础（阻塞性前置条件）

**目的**：完成 SpringDoc 集成与生成流水线，这是 US3（API 文档）与 US1/US2（配置/部署文档引用 API）的共同前置条件

**⚠️ 关键**：在此阶段完成之前，不能开始用户故事的工作（US3 直接依赖生成流水线；US1/US2 的运维文档需引用 API 端点）

- [ ] T003 在 `pom.xml` 新增 `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3` 依赖（compile 范围），并新增 `gen-api-doc` profile 包含 `springdoc-openapi-maven-plugin:1.5` 与 `spring-boot-maven-plugin` 的 start/stop goal
- [ ] T004 在 `src/main/java/top/lldwb/alistmediasync/common/config/OpenApiConfig.java` 创建配置类，声明 `@OpenAPIDefinition`（标题=AList-Media-Sync API、version=${app.version}、描述中文）与 `@SecurityScheme`（type=HTTP、scheme=basic、name=basicAuth）
- [ ] T005 在 `src/main/resources/application.yaml` 新增 `springdoc.*` 配置段：`api-docs.path`、`api-docs.enabled`、`swagger-ui.path`、`swagger-ui.enabled`、`packages-to-scan: top.lldwb.alistmediasync`，enabled 项支持环境变量覆盖（`SPRINGDOC_API_DOCS_ENABLED` / `SPRINGDOC_SWAGGER_UI_ENABLED`）
- [ ] T006 修改 `src/main/java/top/lldwb/alistmediasync/common/interceptor/AuthInterceptor.java`，对 `/v3/api-docs**` 与 `/swagger-ui**` 路径做环境差异化处理：开发环境放行，生产环境通过 `springdoc.*.enabled=false` 禁用或保持认证保护
- [ ] T007 启动应用验证 SpringDoc 与 Spring Boot 4.1.0 兼容性：`./mvnw spring-boot:run`，访问 `/v3/api-docs` 确认返回非空 OpenAPI JSON，访问 `/swagger-ui.html` 确认 UI 渲染；若不兼容执行 contracts/api-doc-generation-contract.md §6 回退方案
- [ ] T008 [P] 为 7 个 Controller 添加类级 `@Tag` 与方法级 `@Operation`/`@ApiResponse` 注解（中文 description、英文 operationId）：`DashboardController`、`DiagnosticController`、`StorageEngineController`、`SyncTaskController`、`TranscodeTaskController`、`WebhookController`、`WebhookEventController`、`WebhookRuleController`
- [ ] T009 [P] 为约 20 个 DTO 添加类级 `@Schema` 与字段级 `@Schema(description, example, requiredMode)` 注解，覆盖 `common/dto/`、`storage/dto/`、`sync/dto/`、`transcode/dto/`、`webhook/dto/` 下所有请求与响应 DTO
- [ ] T010 创建 `scripts/gen-api-doc.sh` 与 `scripts/gen-api-doc.bat`，封装 contracts/api-doc-generation-contract.md §1 的 5 阶段流水线：`mvn verify -Pgen-api-doc` 生成 `target/openapi.yaml` → 调用 `openapi-to-md`（或自研 Node 脚本作为回退）转换为 `docs/05-API接口文档.md` → 写入派生产物声明与生成时间 → 校验输出非空
- [ ] T011 执行 `./scripts/gen-api-doc.sh` 首次生成 `docs/05-API接口文档.md`，确认文件头包含派生产物声明、内容非空、覆盖全部 7 个 Controller 的端点
- [ ] T012 [P] 创建 `scripts/check-annotation-coverage.sh`（与 .bat），扫描 `src/main/java/**/controller/*.java` 与 `**/dto/*.java`，断言每个 Controller 类有 `@Tag`、每个公开方法有 `@Operation`、每个 DTO 类有 `@Schema`，缺失时非零退出码并输出缺失清单

**检查点**：SpringDoc 集成就绪、注解覆盖完成、生成流水线可用 — 现在可以并行开始用户故事实现

---

## 阶段 3：用户故事 1 — 新开发者快速理解项目并搭建环境（优先级：P1）🎯 MVP

**目标**：交付 README.md 精简 + docs/01 项目概述 + docs/02 开发环境搭建，支撑新开发者 30 分钟内本地启动

**独立测试**：让一位未接触过本项目的开发者仅依据 README.md → docs/01 → docs/02 完成本地启动并访问 Web 管理界面（SC-001）

### 用户故事 1 的实现

- [ ] T013 [P] [US1] 创建 `docs/01-项目概述.md`：项目背景（AList 媒体库自动同步与转码、对接 B 站录播姬 Webhook）、核心功能清单（同步/转码/Webhook/Web 管理/WebSocket/诊断）、技术栈（Spring Boot 4.1.0/Java 21/JPA+H2/React 19/JAVE2）、非目标边界
- [ ] T014 [P] [US1] 创建 `docs/02-开发环境搭建.md`：JDK 21 安装与 JAVA_HOME、Maven Wrapper 使用（`./mvnw`）、前端构建（Node 22 + `npm ci` + `npm run build`）、本地必填配置（`ALIST_BASE_URL`/`ALIST_TOKEN`/`ALIST_CRYPTO_KEY` 与 `app.data-dir`）、三种启动方式（`mvn spring-boot:run`、IDE、`scripts/start.sh`）、常见启动问题排障（端口冲突、H2 初始化失败）
- [ ] T015 [US1] 精简 `README.md` 至约 80 行：项目一句话定位、技术栈徽章、三种部署方式快速开始（Docker/一体化包/源码构建各约 5 行）、指向 `docs/01`~`docs/06` 与 `docs/operations/`、`docs/architecture/` 的导航链接；移除原配置详解章节（迁移至 docs/04）

**检查点**：新开发者可仅凭 README → docs/01 → docs/02 完成本地启动（SC-001 可验证）

---

## 阶段 4：用户故事 2 — 运维人员完成 Docker 部署与配置管理（优先级：P1）

**目标**：交付 docs/06 运维部署 + docs/operations/ 环境变量清单与故障排查，支撑运维 15 分钟内完成 Docker 部署

**独立测试**：让一位运维人员仅依据 docs/06 与 docs/operations/ 在全新 Linux 服务器完成 Docker 部署并通过健康检查（SC-002）

### 用户故事 2 的实现

- [ ] T016 [P] [US2] 创建 `docs/operations/环境变量清单.md`（SSOT）：全量环境变量表，含 `app.*` 映射变量（如 `DATA_DIR`、`TRANSCODE_TEMP_SUFFIX`）与非 `app.*` 变量（`SERVER_PORT`、`ALIST_BASE_URL`、`ALIST_TOKEN`、`ALIST_CRYPTO_KEY`、`LOGGING_LEVEL`、`LOG_PATH`、`JAVA_OPTS`、`SPRINGDOC_*`），每项标注默认值、是否必填、对应配置属性、示例
- [ ] T017 [P] [US2] 创建 `docs/operations/故障排查与日志.md`：日志级别调整（`LOGGING_LEVEL`）、traceId 串联查询（原则 VII §7.3 的 MDC 字段）、error.log 分流（§7.4）、X-Trace-Id 响应头（§7.5）、常见问题 FAQ（启动失败、转码失败、Webhook 不触发、AList API 调用失败）
- [ ] T018 [US2] 创建 `docs/06-运维部署.md`：部署方式对比表（Docker/一体化包/源码构建）、Docker 部署完整流程（`docker compose up -d`、`.env` 配置、健康检查、卷挂载）、环境变量配置清单引用（链接到 `docs/operations/环境变量清单.md`，不重复）、数据持久化（H2 文件位置、`dataDir` 迁移、日志轮转）、运维注意事项（FFmpeg/JAVE2 依赖、转码并发与内存关系、虚拟线程监控、actuator 端点、诊断包生成、升级回滚与备份）

**检查点**：运维人员可仅凭 docs/06 + docs/operations/ 完成 Docker 部署与健康检查（SC-002 可验证）

---

## 阶段 5：用户故事 3 — 维护者通过单一权威来源查询配置与 API（优先级：P2）

**目标**：交付 docs/04 配置说明（SSOT）+ 确认 docs/05 API 文档可自动生成，支撑维护者 30 秒内查询任意配置或 API

**独立测试**：查询任意 `app.*` 配置项的环境变量与默认值（docs/04），运行生成命令后查询任意端点（docs/05），耗时 < 30 秒（SC-003、SC-004）

### 用户故事 3 的实现

- [ ] T019 [US3] 创建 `docs/04-配置说明.md`（SSOT）：Spring Boot Relaxed Binding 机制说明（`app.data-dir` ↔ `DATA_DIR`、`app.auth.password` ↔ `APP_AUTH_PASSWORD` 的 kebab-case → SCREAMING_SNAKE_CASE 映射规则）、三级配置优先级（命令行 > 环境变量 > application.yaml > 默认值）、完整 `app.*` 配置项表（从 `AppProperties.java` 抽取 19 项，分基础/认证/转码/运行时四组）、非 `app.*` 环境变量引用（链接到 `docs/operations/环境变量清单.md`）、敏感项标记与生产建议（`ALIST_CRYPTO_KEY` 必设、密码避免默认值）
- [ ] T020 [US3] 完善 `docs/05-API接口文档.md` 的引导章节（生成产物之上的手工补充部分）：认证方式（HTTP Basic Auth，`/api/**` 受 AuthInterceptor 保护，`/actuator/health` 开放）、统一响应格式 `ApiResult{code,message,data,traceId}`、WebSocket 端点 `/ws` 与 `MessageType` 枚举、错误码清单、Swagger UI 与 OpenAPI 端点访问方式；标注"端点清单由 scripts/gen-api-doc 自动生成，引导章节手工维护"
- [ ] T021 [US3] 在 `docs/05-API接口文档.md` 顶部创建引导章节后，重新执行 `./scripts/gen-api-doc.sh` 确认生成产物与引导章节正确拼接（若生成工具不支持前置手工章节，调整为生成端点清单 + 手工引导章节合并的方式），验证 SC-004（重新生成 diff 为空）

**检查点**：维护者可在 30 秒内查询任意配置项或 API 端点（SC-003、SC-004 可验证）

---

## 阶段 6：用户故事 4 — 技术负责人评估架构与模块边界（优先级：P2）

**目标**：交付 docs/03 架构设计 + docs/architecture/ 五大模块与交叉关注点文档，支撑架构审查决策

**独立测试**：审查者仅依据 docs/03 与 docs/architecture/ 判断一处改动是否违反分层或模块边界（SC-006 覆盖 5 模块）

### 用户故事 4 的实现

- [ ] T022 [P] [US4] 创建 `docs/architecture/模块-common.md`：职责边界（配置/认证/工具/异常处理/诊断）、核心类（AppProperties/AsyncConfig/WebMvcConfig/WebSocketConfig/TraceIdFilter/AuthInterceptor/CryptoConverter/GlobalExceptionHandler）、关键流程、扩展点、关联 spec
- [ ] T023 [P] [US4] 创建 `docs/architecture/模块-storage.md`：职责边界（存储引擎策略模式）、核心类（StorageEngineController/Service/AListStrategy/LocalStrategy）、关键流程、扩展点（新增存储策略）、关联 spec
- [ ] T024 [P] [US4] 创建 `docs/architecture/模块-sync.md`：职责边界（同步三模式 NEW_ONLY/FULL/MOVE + 调度 CRON/INTERVAL）、核心类（SyncTaskController/SyncService/ScheduleService/SyncTaskManageService）、关键流程、扩展点、关联 spec
- [ ] T025 [P] [US4] 创建 `docs/architecture/模块-transcode.md`：职责边界（下载→转码→上传三步流程 + 8 状态模型）、核心类（TranscodeTaskController/TranscodeService/TranscodeFileProcessor）、关键流程、扩展点（并发控制 Semaphore）、关联 spec
- [ ] T026 [P] [US4] 创建 `docs/architecture/模块-webhook.md`：职责边界（接收录播姬 v2 事件 → 规则匹配 → 触发同步/转码）、核心类（WebhookController/WebhookService/WebhookRuleService）、关键流程、扩展点、关联 spec
- [ ] T027 [P] [US4] 创建 `docs/architecture/交叉关注点.md`：认证（AuthInterceptor + BCrypt + PasswordEncryptionPostProcessor）、加密（CryptoConverter + CryptoKeyEnvironmentPostProcessor + AES-256）、虚拟线程（Java 21 启用配置）、诊断（diagnose.sh + /api/diagnostics/run + Web 入口）、日志（原则 VII traceId/MDC/error.log/X-Trace-Id/脱敏）
- [ ] T028 [US4] 创建 `docs/03-架构设计.md`：分层架构图（Controller → Service → Repository → Entity，遵循章程原则 I）、包结构总览（common/storage/sync/transcode/webhook 五大模块）、核心类职责说明（AppProperties 为配置层唯一入口、各 Config 类职责、TraceIdFilter+TraceContext+MDC 全链路追踪、AuthInterceptor+WebSocketAuthInterceptor 认证、CryptoConverter 加密存储、GlobalExceptionHandler 统一异常→ApiResult）、模块间依赖关系图、交叉关注点链接到 `docs/architecture/交叉关注点.md`

**检查点**：审查者可依据 docs/03 + docs/architecture/ 判断模块边界与分层合规（SC-006 可验证）

---

## 阶段 7：用户故事 5 — 项目演进可追溯（优先级：P3）

**目标**：交付 CHANGELOG.md，支撑外部用户在不查阅 git log 的情况下判断功能版本

**独立测试**：用户仅依据 CHANGELOG.md 判断某功能从哪个版本开始支持（SC-008）

### 用户故事 5 的实现

- [ ] T029 [US5] 创建 `CHANGELOG.md`：遵循 Keep a Changelog 格式，按版本倒序排列，包含 Added/Changed/Fixed/Removed 分类；通过 `git log` 回溯补全 0.0.1-SNAPSHOT 历史版本（001-010 spec 对应的功能），无法精确还原的标注"历史版本"；顶部预留 Unreleased 区块供后续迭代

**检查点**：CHANGELOG 可支撑版本功能查询（SC-008 可验证）

---

## 阶段 8：润色与跨领域关注点

**目的**：精简 AGENTS.md、更新文档引用、运行端到端验证、确保章程合规

- [ ] T030 精简 `AGENTS.md`：移除"项目架构总览"章节（已迁移至 docs/03），仅保留 AI 协作指令、章程引用（`.specify/memory/constitution.md`）、Spec Kit 工作流说明；目标约 100 行；保持 SPECKIT 标记区指向当前 plan.md
- [ ] T031 [P] 校验 `docs/04-配置说明.md` 与 `AppProperties.java`、`application.yaml` 一致（VR-001）：对照 19 个 `app.*` 配置项的字段名、默认值、环境变量映射，修正不一致
- [ ] T032 [P] 校验 `docs/operations/环境变量清单.md` 与 `application.yaml`、`Dockerfile`、`.env` 模板一致（VR-001）：对照非 `app.*` 环境变量，修正不一致
- [ ] T033 [P] 校验冻结文件完整性（VR-003）：确认 `specs/001..010/` 与 `md/` 下所有文件无修改、删除、重命名，文件数与内容哈希与实现前快照一致
- [ ] T034 [P] 校验文档链接有效性（VR-004）：扫描 `docs/` 间相对链接与指向 `specs/` 的链接，确认无死链；可使用 `scripts/check-doc-links.sh`（若有）或人工抽查
- [ ] T035 运行 quickstart.md 验证场景 1：执行 `./scripts/gen-api-doc.sh` 与 `git diff --exit-code docs/05-API接口文档.md`，确认 SC-004（重新生成 diff 为空）与注解覆盖率 100%
- [ ] T036 运行 quickstart.md 验证场景 2-6：按场景模拟新开发者本地启动、运维 Docker 部署、配置 SSOT 一致性、冻结完整性、文档链接有效性，确认 SC-001/002/006/007/008 与 VR-001/004
- [ ] T037 [P] 在 `README.md` 的"导航"章节确认指向 `docs/01`~`docs/06`、`docs/architecture/`、`docs/operations/`、`CHANGELOG.md` 的链接完整有效
- [ ] T038 章程合规自检：对照 plan.md「章程检查」表，确认原则 I/IV/VI/IX/X 合规；原则 VI 的 5 项新增依赖/配置已在「复杂性追踪」记录；原则 IX 的 README.md 同步已收尾

---

## 依赖与执行顺序

### 阶段依赖

- **设置（阶段 1）**：无依赖 — 可立即开始
- **基础（阶段 2）**：依赖设置完成 — 阻塞所有用户故事（SpringDoc 集成是 US3 前置，API 端点是 US1/US2 文档引用对象）
- **用户故事（阶段 3-7）**：全部依赖基础阶段完成
  - US1（阶段 3）与 US2（阶段 4）可并行（不同文档目录）
  - US3（阶段 5）依赖基础阶段生成流水线就绪
  - US4（阶段 6）可与 US1/US2/US3 并行（不同文档）
  - US5（阶段 7）独立，可与任何故事并行
- **润色（阶段 8）**：依赖所有用户故事完成（AGENTS.md 精简引用 docs/03，验证需所有文档就位）

### 用户故事依赖

- **US1（P1）**：可在基础阶段后开始 — 不依赖其他故事
- **US2（P1）**：可在基础阶段后开始 — 与 US1 独立，可并行
- **US3（P2）**：可在基础阶段后开始 — 依赖生成流水线（基础阶段产物），docs/04 可与 docs/05 并行编写
- **US4（P2）**：可在基础阶段后开始 — 与 US1/US2/US3 独立，可并行
- **US5（P3）**：可在基础阶段后开始 — 完全独立，可随时进行

### 每个用户故事内部

- 文档创建任务可并行（不同文件）
- 引用其他文档的任务需在被引用文档存在后进行（如 docs/06 引用 docs/operations/环境变量清单）
- US3 的 T021（重新生成验证）依赖 T019/T020 完成

### 并行机会

- 阶段 1：T001 与 T002 可并行
- 阶段 2：T008（Controller 注解）与 T009（DTO 注解）可并行；T012（覆盖率脚本）可与 T010/T011 并行
- 阶段 3-7：US1（T013/T014）与 US2（T016/T017）可并行；US4 的 6 份模块文档（T022-T027）可全部并行；US5 可与任何故事并行
- 阶段 8：T031/T032/T033/T034 校验任务可并行

---

## 并行示例：用户故事 4（架构模块文档）

```bash
# 一起启动用户故事 4 的所有模块文档（不同文件，无依赖）：
任务："创建 docs/architecture/模块-common.md"
任务："创建 docs/architecture/模块-storage.md"
任务："创建 docs/architecture/模块-sync.md"
任务："创建 docs/architecture/模块-transcode.md"
任务："创建 docs/architecture/模块-webhook.md"
任务："创建 docs/architecture/交叉关注点.md"
```

## 并行示例：用户故事 1 与 2（跨故事并行）

```bash
# US1 与 US2 文档目录不同，可并行：
任务（US1）："创建 docs/01-项目概述.md"
任务（US1）："创建 docs/02-开发环境搭建.md"
任务（US2）："创建 docs/operations/环境变量清单.md"
任务（US2）："创建 docs/operations/故障排查与日志.md"
```

---

## 实现策略

### MVP 优先（仅用户故事 1 + 基础阶段）

1. 完成阶段 1：设置
2. 完成阶段 2：基础（SpringDoc 集成 + 生成流水线）
3. 完成阶段 3：用户故事 1（README 精简 + docs/01 + docs/02）
4. **停止并验证**：让新开发者按 README → docs/01 → docs/02 完成本地启动（SC-001）
5. 如果就绪则演示

### 增量交付

1. 完成设置 + 基础 → 生成流水线就绪，docs/05 可自动生成
2. 添加用户故事 1 → 独立测试 → 新开发者可本地启动（MVP！）
3. 添加用户故事 2 → 独立测试 → 运维可 Docker 部署
4. 添加用户故事 3 → 独立测试 → 配置与 API 可 SSOT 查询
5. 添加用户故事 4 → 独立测试 → 架构可审查
6. 添加用户故事 5 → 独立测试 → 版本可追溯
7. 每个故事增加价值而不破坏之前的故事

### 并行团队策略

多个开发人员时：
1. 团队一起完成设置 + 基础（SpringDoc 集成需一人主导，注解增强可分工）
2. 基础完成后：
   - 开发人员 A：用户故事 1（README + docs/01/02）
   - 开发人员 B：用户故事 2（docs/06 + operations/）
   - 开发人员 C：用户故事 4（docs/03 + architecture/ 六份）
   - 开发人员 D：用户故事 3（docs/04 + docs/05 引导）与用户故事 5（CHANGELOG）
3. 各故事独立完成，润色阶段统一校验

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应能独立完成和测试
- 本功能无 Service/Repository 代码逻辑变更，单元测试不适用；验证以 quickstart.md 6 个端到端场景为准
- 冻结边界：specs/001..010/ 与 md/ 不可修改（VR-003）
- 派生产物：docs/05-API接口文档.md 不手工编辑端点清单，源真值在注解中（SC-004）
- SSOT：docs/04（配置说明）、docs/operations/环境变量清单（环境变量）、AppProperties.java（代码层）、application.yaml（运行时默认值）
- 每个任务或逻辑组后提交；在任何检查点停止以独立验证故事
- 避免：模糊的任务、同文件冲突、破坏独立性的跨故事依赖
