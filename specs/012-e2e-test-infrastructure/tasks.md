---

description: "端到端测试基础设施的实现任务列表"
---

# 任务：端到端测试基础设施

**输入**：来自 `/specs/012-e2e-test-infrastructure/` 的设计文档

**前提条件**：plan.md（必需）、spec.md（用户故事必需）、research.md、data-model.md、contracts/

**测试**：本功能的核心交付物即为测试代码（集成测试 + E2E 测试），测试任务即为主要实现任务，不采用额外 TDD 模式。

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（例如，US1、US2、US3）
- 在描述中包含确切的文件路径

## 路径约定

- **后端测试**：`src/test/java/top/lldwb/alistmediasync/`（integration/e2e/support 分层）
- **测试资源**：`src/test/resources/`（fixtures/wiremock/application-*.yaml）
- **环境脚本**：`scripts/e2e/`（Windows PowerShell 优先 + Linux Bash 备选）

---

## 阶段 1：设置（共享基础设施）

**目的**：测试依赖初始化与目录结构创建

- [ ] T001 创建测试目录结构：`src/test/java/top/lldwb/alistmediasync/{integration/repository,integration/client,e2e,support}/` 与 `src/test/resources/{fixtures/webhook,fixtures/media,fixtures/alist,wiremock/alist-mappings}/`
- [ ] T002 [P] 在 `pom.xml` 添加 `wiremock-standalone` 依赖（test scope，版本锁定，research.md R1）
- [ ] T003 [P] 在 `pom.xml` 添加 `maven-failsafe-plugin` 与 3 个 Maven profile（默认/`-Pintegration`/`-Pe2e`，surefire 排除 `*IT.java`/`*E2ETest.java`，research.md R2）；failsafe plugin 必须配置 `<forkCount>1</forkCount>` 与 `<reuseForks>false</reuseForks>` 以保证 E2E 链路间串行执行（对齐 FR-012、spec 澄清 line 15、research.md R2），禁止并行以避免端口与状态争抢；failsafe plugin 同时配置 `<forkedProcessExitTimeoutInSeconds>900</forkedProcessExitTimeoutInSeconds>` 作为套件级兜底（SC-006，防止 fork 进程挂死导致套件无限阻塞）
- [ ] T004 [P] 在 `.gitignore` 添加 `scripts/e2e/bin/`、`scripts/e2e/data/`、`data-e2e/`、`scripts/e2e/data/ports.json`（二进制与运行时数据不纳入版本控制）

---

## 阶段 2：基础（阻塞性前置条件）

**目的**：所有用户故事依赖的共享测试基础设施

**⚠️ 关键**：在此阶段完成之前，不能开始任何用户故事的工作

- [ ] T005 [P] 创建 `src/test/resources/application-e2e.yaml`：H2 文件模式（`jdbc:h2:file:./data-e2e/alistmediasync`）、`ddl-auto=create-drop`、独立数据目录 `./data-e2e/`、`server.port` 与 `alist.base-url` 占位（由 `E2ELifecycleManager` 动态注入，FR-014）、DEBUG 日志级别
- [ ] T006 [P] 创建 `src/test/resources/fixtures/webhook/fileclosed-event.json` 与 `sessionstarted-event.json`：严格按 `md/danmuji/webhook.md` v2 协议四段式构造（EventType/EventId/EventTimestamp/EventData），`EventId` 用固定 UUID 便于幂等测试（data-model.md §2.1）；MUST 同时提供 webhook fixtures 的 JSON Schema（或等价字段断言），在 `WebhookEventReplayer`（T012）加载 fixtures 时校验 payload 符合 `md/danmuji/webhook.md` v2 协议四段式结构（EventType/EventId/EventTimestamp/EventData），契约漂移时测试失败并报告缺失/多余字段（对应 spec 边界情况 line 94、research.md R5）
- [ ] T007 [P] 创建 `src/test/resources/fixtures/media/sample.mp4`：小尺寸测试媒体（<5MB，短时长低码率），用于转码链路验证（research.md R6）；MUST 在构建或 CI 阶段校验 `src/test/resources/fixtures/` 目录总体积 < 10MB（plan.md 约束），超阈即构建失败以防止大体积媒体污染版本控制（对应 spec 边界情况 line 97）
- [ ] T008 [P] 创建 `src/test/resources/fixtures/alist/list-response.json`：按 `md/alist/` 契约构造（`{code:200, message:"success", data:{content:[...], total}}`，data-model.md §2.3）
- [ ] T009 [P] 创建 `src/test/resources/wiremock/alist-mappings/` 下 AList 9 端点桩映射：含 `/ping`（text/plain）、`/api/fs/list`、`/api/fs/put`（二进制流）、`/api/fs/mkdir`、`/api/fs/move`、`/api/fs/copy`、`/api/fs/remove`、`/api/fs/detail`、统一 `{code,message,data}` 响应结构 + 错误场景（401/403/404/500）
- [ ] T010 创建 `src/test/java/top/lldwb/alistmediasync/support/E2ELifecycleManager.java`：动态端口分配（`ServerSocket(0)` 探测空闲端口，FR-014）、启动 AList 二进制实例、录播姬可选启动（`-Ddanmuji.enabled=true`）、端口注入配置、等待就绪探活、写入 `ports.json`（research.md R8）；当 ServerSocket(0) 探测失败或可分配端口范围耗尽时，MUST 抛出 IllegalStateException 并输出可用端口范围提示与排查建议（如检查系统端口占用、调整 ephemeral port range），以非零退出码中止 E2E 套件，避免后续链路在错误端口状态下运行（对应 spec 边界情况 line 92）
- [ ] T011 创建 `src/test/java/top/lldwb/alistmediasync/e2e/E2ETestBase.java`：`@BeforeAll` 强制清理（保证幂等）、`@AfterEach` 成功清理三级状态（数据库 TRUNCATE + 文件系统 + 临时文件）、失败时保留现场供诊断（FR-009）、链路间清理边界（FR-012，research.md R7）；E2E 链路测试类 MUST 标注 JUnit 5 `@Timeout(value = 15, unit = TimeUnit.MINUTES)`（SC-006 单链路全局超时阈值），超时即标记失败并按 FR-009 保留现场供诊断（不立即清理），由下次运行前强制清理保证幂等

**检查点**：基础就绪 - 现在可以并行开始用户故事实现

---

## 阶段 3：用户故事 1 - 运行端到端测试验证完整业务链路（优先级：P1）🎯 MVP

**目标**：通过 webhook payload 重放驱动，验证从 webhook 接收、系统处理、AList 同步到转码的完整业务链路真实可用，含 traceId 全链路验证

**独立测试**：`mvn verify -Pe2e` 运行 4 个 `*E2ETest.java`，3 条核心链路 + traceId 链路全部通过，断言点 AP1-AP9（data-model.md §3）；连续 3 次运行全部真实通过（SC-004）。运行依赖 AList 二进制可用（通过 US3 的 `prepare-e2e-env.ps1` 下载，或 `ALIST_LOCAL_PATH` 手动指定）

### 用户故事 1 的实现

- [ ] T012 [P] [US1] 创建 `src/test/java/top/lldwb/alistmediasync/support/WebhookEventReplayer.java`：读取 `fixtures/webhook/` 样本，以 HTTP POST 注入系统 `/api/webhooks/recorder` 端点（FR-011 主路径），支持参数化（fixtures 路径、重复发送次数验证幂等、自定义 EventId）
- [ ] T013 [P] [US1] 创建 `src/test/java/top/lldwb/alistmediasync/support/AListTestClient.java`：封装 AList 真实操作（`/ping` 探活、`/api/fs/list` 查询文件落盘、`/api/fs/detail` 验证转码产物）
- [ ] T014 [US1] 创建 `src/test/java/top/lldwb/alistmediasync/e2e/WebhookSyncE2ETest.java`：链路 1 - `WebhookEventReplayer` 重放 FileClosed -> 系统处理 -> 同步到 AList，断言 AP1（webhook 接收）、AP2（事件持久化）、AP3（幂等去重）、AP4（任务创建）、AP5（任务执行）、AP6（文件落盘）
- [ ] T015 [US1] 创建 `src/test/java/top/lldwb/alistmediasync/e2e/ManualSyncE2ETest.java`：链路 2 - 手动同步任务执行，通过 API 触发同步并断言任务执行历史持久化与文件落盘
- [ ] T016 [US1] 创建 `src/test/java/top/lldwb/alistmediasync/e2e/TranscodeE2ETest.java`：链路 3 - 转码任务执行，断言 AP7（转码完成，转码后文件存在 AList，体积合理）
- [ ] T017 [US1] 创建 `src/test/java/top/lldwb/alistmediasync/e2e/TraceIdChainE2ETest.java`：traceId 全链路验证，断言 AP8（`X-Trace-Id` 响应头 + 诊断包 traceId 一致）、AP9（`error.log` 与 `app.log` 双写），复用 `scripts/diagnose.{sh,bat}` 收集诊断包（FR-006，原则 VII §7.3-7.5）
- [ ] T018 [US1] 验证 SC-004：连续 3 次运行 `mvn verify -Pe2e` 全部真实通过，确认环境清理幂等性与链路稳定性

**检查点**：此时，用户故事 1 应完全功能可用且可独立测试

---

## 阶段 4：用户故事 2 - 补全集成测试覆盖 Repository 与外部 API 客户端（优先级：P2）

**目标**：补全 Repository 层与 AList 客户端的集成测试，验证数据持久化与外部契约交互的真实正确性

**独立测试**：`mvn verify -Pintegration` 运行 7 个 `*IT.java`（6 Repository + 1 AList 契约），全部通过，无外部二进制依赖（H2 内存 + WireMock 进程内）

### 用户故事 2 的实现

- [ ] T019 [P] [US2] 创建 `src/test/java/top/lldwb/alistmediasync/integration/repository/StorageEngineRepositoryIT.java`：`@DataJpaTest` 验证 StorageEngine 实体持久化、乐观锁 `@Version`、CRUD 行为
- [ ] T020 [P] [US2] 创建 `src/test/java/top/lldwb/alistmediasync/integration/repository/SyncTaskRepositoryIT.java`：验证 SyncTask 持久化、`@Query` 自定义方法、状态转换
- [ ] T021 [P] [US2] 创建 `src/test/java/top/lldwb/alistmediasync/integration/repository/TaskExecutionRepositoryIT.java`：验证 TaskExecution 持久化、`markAllRunningAsInterrupted` 启动恢复行为、事务回滚
- [ ] T022 [P] [US2] 创建 `src/test/java/top/lldwb/alistmediasync/integration/repository/WebhookEventRepositoryIT.java`：验证 `findByEventId` 幂等去重、EventId 唯一索引、DUPLICATE 状态转换（原则 II）
- [ ] T023 [P] [US2] 创建 `src/test/java/top/lldwb/alistmediasync/integration/repository/WebhookRuleRepositoryIT.java`：验证 WebhookRule 持久化、规则匹配查询
- [ ] T024 [P] [US2] 创建 `src/test/java/top/lldwb/alistmediasync/integration/repository/TranscodeTaskRepositoryIT.java`：验证 TranscodeTask 8 状态转换（specs/002）、乐观锁、级联关系
- [ ] T025 [US2] 创建 `src/test/java/top/lldwb/alistmediasync/integration/client/AListStorageStrategyIT.java`：`@WireMockTest` 契约测试，覆盖 AListStorageStrategy 9 个公共方法（请求构造、响应解析、`File-Path` header URL 编码、`As-Task` header、错误处理 401/403/404/500、重试机制），桩映射引用 T009（research.md R1）

**检查点**：此时，用户故事 1 和 2 应都能独立工作

---

## 阶段 5：用户故事 3 - 一键准备端到端测试环境（优先级：P3）

**目标**：提供一键下载与启动 AList（录播姬可选）二进制的脚本，降低 E2E 环境搭建成本

**独立测试**：在干净项目目录执行 `.\scripts\e2e\prepare-e2e-env.ps1` 下载 AList 二进制（<10 分钟，SC-001），`start-alist.ps1` 启动并 `/ping` 返回 `pong`；重复执行跳过已存在下载（幂等）

### 用户故事 3 的实现

- [ ] T026 [P] [US3] 创建 `scripts/e2e/prepare-e2e-env.ps1`：下载 AList GitHub Releases 二进制到 `scripts/e2e/bin/alist/`（版本锁定、SHA256 校验、幂等跳过、`ALIST_LOCAL_PATH` 跳过、`-IncludeDanmuji` 可选下载录播姬、`-Force` 强制重下，contracts/env-prep-script.md）
- [ ] T027 [P] [US3] 创建 `scripts/e2e/prepare-e2e-env.sh`：Linux 备选，Bash 实现同 T026 逻辑（FR-013 nightly CI 需要）
- [ ] T028 [P] [US3] 创建 `scripts/e2e/start-alist.ps1`：接收 `-Port`（动态端口，FR-014）启动 `alist.exe server`，写入 PID 到 `alist.pid`，轮询 `/ping` 30 秒内返回 `pong` 视为就绪，初始化存储挂载 `/e2e-test`
- [ ] T029 [P] [US3] 创建 `scripts/e2e/start-danmuji.ps1`：可选验证路径，接收 `-WebuiPort`（动态端口）启动录播姬，生成配置注入 webhookUrl，轮询 WebUI 就绪
- [ ] T030 [US3] 创建 `scripts/e2e/stop-e2e-env.ps1`：基于 `alist.pid`/`danmuji.pid`（容忍录播姬未启动）与 `ports.json` 精准停止进程，`-CleanData` 清理 `scripts/e2e/data/`（不删除 `bin/`）
- [ ] T031 [P] [US3] 创建 `scripts/e2e/e2e-config/alist.config.json` 与 `scripts/e2e/e2e-config/danmuji.config.toml`：外部依赖配置模板（数据目录、存储挂载、webhook URL 占位），纳入版本控制

**检查点**：此时所有用户故事应各自独立功能可用

---

## 阶段 6：润色与跨领域关注点

**目的**：影响多个用户故事的文档同步、CI 集成与验证

- [ ] T032 [P] 更新 `docs/02-开发环境搭建.md`：新增 E2E 环境准备章节（下载脚本用法、`ALIST_LOCAL_PATH`、动态端口说明），对齐 quickstart.md 场景 3（原则 IX、XI）
- [ ] T033 [P] 更新 `docs/06-运维部署.md`：新增测试 profile 用法（`-Pintegration`/`-Pe2e`）与 nightly CI 说明，对齐 contracts/e2e-maven-profile.md（原则 IX、XI）
- [ ] T034 更新 `CHANGELOG.md`：按 Keep a Changelog 格式新增版本条目，Added 分类记录集成测试层、E2E 测试层、环境准备脚本、nightly CI（原则 IX）
- [ ] T035 [P] 创建 `.github/workflows/e2e-nightly.yml`：GitHub Actions `schedule.cron` 定时触发 `mvn verify -Pe2e`，CI 脚本调用 `prepare-e2e-env.sh` 下载 AList 二进制，PR 流水线不触发（FR-013，research.md R10）
- [ ] T036 运行 `quickstart.md` 验证场景 1-8：确认单元/集成/E2E 三层测试均可独立运行，动态端口与失败保留行为符合预期

---

## 依赖与执行顺序

### 阶段依赖

- **设置（阶段 1）**：无依赖 - 可立即开始
- **基础（阶段 2）**：依赖设置完成 - 阻塞所有用户故事
- **用户故事（阶段 3-5）**：全部依赖基础阶段完成
  - US2（集成测试）完全独立，无外部二进制依赖，可与 US1/US3 并行
  - US1（E2E 测试）测试代码编写不依赖 US3，但**运行验证**依赖 AList 二进制可用（US3 的 `prepare-e2e-env.ps1` 或手动 `ALIST_LOCAL_PATH`）
  - US3（环境脚本）独立，可与 US1/US2 并行
- **润色（阶段 6）**：依赖所有期望的用户故事完成

### 用户故事依赖

- **用户故事 1（P1）**：可在基础后开始 - 测试代码独立编写；运行验证需 AList 二进制（US3 或手动提供）
- **用户故事 2（P2）**：可在基础后开始 - 完全独立，无外部依赖，可立即运行验证
- **用户故事 3（P3）**：可在基础后开始 - 独立，与 US1 互为补充（US3 自动化 US1 运行所需的环境）

### 每个用户故事内部

- 支持工具（WebhookEventReplayer/AListTestClient）先于 E2E 测试类
- Repository IT 之间无依赖，可并行
- 基础 fixtures 先于引用它的测试

### 并行机会

- 阶段 1 的 T002/T003/T004 可并行（不同 pom.xml 片段与 .gitignore）
- 阶段 2 的 T005-T009 可并行（不同资源文件）
- US2 的 T019-T024 可并行（6 个独立 RepositoryIT 文件）
- US3 的 T026-T029、T031 可并行（独立脚本与配置文件）
- 阶段 6 的 T032/T033/T035 可并行（不同文档与工作流文件）
- US1、US2、US3 在基础完成后可由不同团队成员并行推进

---

## 并行示例：用户故事 2

```bash
# 一起启动用户故事 2 的所有 Repository 集成测试（6 个独立文件）：
任务："创建 StorageEngineRepositoryIT.java"
任务："创建 SyncTaskRepositoryIT.java"
任务："创建 TaskExecutionRepositoryIT.java"
任务："创建 WebhookEventRepositoryIT.java"
任务："创建 WebhookRuleRepositoryIT.java"
任务："创建 TranscodeTaskRepositoryIT.java"

# Repository IT 完成后，启动 AList 客户端契约测试：
任务："创建 AListStorageStrategyIT.java"（依赖 T009 桩映射）
```

---

## 实现策略

### MVP 优先（用户故事 1 + 必要基础）

1. 完成阶段 1：设置（pom.xml 依赖与 profile）
2. 完成阶段 2：基础（fixtures + E2ELifecycleManager + E2ETestBase）
3. 完成阶段 3：用户故事 1（E2E 测试链路）
4. 手动提供 AList 二进制（`ALIST_LOCAL_PATH`）运行验证
5. **停止并验证**：`mvn verify -Pe2e` 3 链路 + traceId 通过

### 增量交付

1. 完成设置 + 基础 -> 基础就绪
2. 添加用户故事 2 -> `mvn verify -Pintegration` 独立验证（最轻量，无外部依赖）-> 部署/演示
3. 添加用户故事 1 -> 手动或 US3 提供二进制 -> `mvn verify -Pe2e` 验证 -> 部署/演示（MVP！）
4. 添加用户故事 3 -> 一键环境准备自动化 -> 降低新贡献者门槛
5. 完成润色 -> 文档与 CI nightly 就绪

### 并行团队策略

多个开发人员时：

1. 团队一起完成设置 + 基础
2. 基础完成后：
   - 开发人员 A：用户故事 2（集成测试，无外部依赖，可立即验证）
   - 开发人员 B：用户故事 3（环境脚本，独立）
   - 开发人员 C：用户故事 1（E2E 测试，待 US3 或手动二进制后运行验证）
3. 各故事独立完成并集成

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应能独立完成和测试
- 本功能核心交付物为测试代码，被测业务代码（src/main/java）已存在，不修改
- 所有测试注释、fixtures 描述、脚本输出使用简体中文（原则 IV），测试方法名用英文
- E2E 端口动态分配（FR-014），禁止固定端口假设
- E2E 失败保留现场，下次运行前强制清理（FR-009）
- 每个任务或逻辑组后提交
- 在任何检查点停止以独立验证故事
