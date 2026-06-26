# 任务：轻量诊断系统

**输入**：来自 `/specs/009-lightweight-diagnostics/` 的设计文档

**前提条件**：plan.md、spec.md、research.md、data-model.md、contracts/、quickstart.md

**测试**：根据项目章程原则 V，所有 Java 类变更必须同步测试；本任务清单包含对应测试任务。本次重新生成强化了 SC-001（≤ 30 秒）、SC-002（≤ 2 分钟定位）、SC-005（≥ 90% 错误事件结构化）、FR-012（无业务副作用）的量化验证。

**组织方式**：任务按用户故事分组，支持每个故事独立实现和验证。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（US1、US2、US3）
- 每个任务描述包含确切文件路径

---

## 阶段 1：设置（共享准备）

**目的**：确认上下文、建立诊断功能所需的共享文件位置，不改变业务行为。

- [X] T001 读取后端通用模块规则文件 `src/main/java/top/lldwb/alistmediasync/common/AGENTS.md`
- [X] T002 [P] 读取同步模块规则文件 `src/main/java/top/lldwb/alistmediasync/sync/AGENTS.md`
- [X] T003 [P] 读取转码模块规则文件 `src/main/java/top/lldwb/alistmediasync/transcode/AGENTS.md`
- [X] T004 [P] 读取 Webhook 模块规则文件 `src/main/java/top/lldwb/alistmediasync/webhook/AGENTS.md`
- [X] T005 [P] 检查现有日志配置和日志输出路径 `src/main/resources/application.yaml`
- [X] T006 [P] 检查一体化启动脚本中的目录变量与 JVM 参数 `scripts/start.sh`
- [X] T007 [P] 检查一体化启动脚本中的目录变量与 JVM 参数 `scripts/start.bat`

---

## 阶段 2：基础（阻塞性前置条件）

**目的**：建立所有用户故事共享的 traceId、日志上下文、错误日志分流和基础 DTO。

**⚠️ 关键**：完成本阶段前不要开始任何用户故事实现。

### 基础测试

- [X] T008 [P] 在 `src/test/java/top/lldwb/alistmediasync/common/util/TraceContextTest.java` 编写 traceId 生成、合法性校验（8–128 字符、白名单字符集）、请求头继承和非法值拒绝（含空白/换行/控制字符）测试
- [X] T009 [P] 在 `src/test/java/top/lldwb/alistmediasync/common/config/TraceIdFilterTest.java` 编写 `X-Trace-Id` 响应头（成功、业务失败、认证失败、异常路径四类响应都包含）、MDC 设置、请求结束清理、并发不污染测试

### 基础实现

- [X] T010 [P] 在 `src/main/java/top/lldwb/alistmediasync/common/util/TraceContext.java` 实现 traceId 生成、校验、MDC 读写和清理工具
- [X] T011 在 `src/main/java/top/lldwb/alistmediasync/common/config/TraceIdFilter.java` 实现请求级 traceId 过滤器(依赖 T010)，统一处理 `X-Trace-Id` 请求头、响应头和 MDC 生命周期
- [X] T012 在 `src/main/resources/application.yaml` 中补充日志文件目录配置（`logs/app.log`、`logs/error.log`）和包含 traceId 的日志 pattern 配置（`%X{traceId}`）
- [X] T013 在 `src/main/resources/logback-spring.xml` 中配置应用日志和 `logs/error.log` 的 ERROR 分流 appender，确保 ERROR 同时写入两文件
- [X] T014 [P] 在 `src/main/java/top/lldwb/alistmediasync/common/dto/DiagnosticResultVO.java` 定义诊断生成结果视图（packagePath、summaryPath、status、durationMs、missingItems）
- [X] T015 [P] 在 `src/main/java/top/lldwb/alistmediasync/common/dto/DiagnosticSummaryVO.java` 定义诊断摘要视图（appVersion、environment、latestFailure、recommendedFiles、suspectedCause、missingItems）
- [X] T016 在 T010–T015 全部完成后运行 `./mvnw -Dtest=TraceContextTest,TraceIdFilterTest test` 验证基础 traceId 测试通过

**检查点**：所有 HTTP 响应都包含 `X-Trace-Id`；ERROR 日志可同时写入 `logs/error.log`；基础 DTO 就绪。

---

## 阶段 3：用户故事 1 — 一键生成可交给 AI 的诊断包（优先级：P1）🎯 MVP

**目标**：用户通过一次明确操作生成 `diagnostics/latest/summary.md` 和关键证据文件，AI 可直接读取诊断包排查问题。

**入口优先级**：脚本（`diagnose.sh`/`diagnose.bat`）为独立入口，应用不可用时仍可收集文件和环境信息；后端端点（`POST /api/diagnostics/run`）为运行时补充，复用脱敏与摘要生成逻辑。两者互为 fallback：脚本可独立运行，端点提供更精确的应用内上下文。

**独立测试**：触发或模拟一次失败后运行诊断命令，确认 `diagnostics/latest/summary.md` 存在，包含最近失败、traceId、关键证据、缺失信息和下一步建议；生成耗时 ≤ 30 秒（SC-001）。

### 用户故事 1 的测试

- [X] T017 [P] [US1] 在 `src/test/java/top/lldwb/alistmediasync/common/service/DiagnosticServiceTest.java` 编写生成 `summary.md`、复制错误日志摘录、记录缺失信息、`status` 正确反映 COMPLETED/PARTIAL/FAILED 的单元测试
- [X] T018 [P] [US1] 在 `src/test/java/top/lldwb/alistmediasync/common/controller/DiagnosticControllerTest.java` 编写诊断触发端点返回 `ApiResult<DiagnosticResultVO>` 和 `X-Trace-Id` 响应头的 WebMvc 测试
- [X] T019 [P] [US1] 在 `scripts/diagnose-smoke-test.sh` 编写 Linux/Docker 诊断脚本 smoke test（实际执行在 T028 之后）
- [X] T020 [P] [US1] 在 `scripts/diagnose-smoke-test.bat` 编写 Windows 诊断脚本 smoke test（实际执行在 T029 之后）
- [X] T021 [P] [US1] 在 `src/test/java/top/lldwb/alistmediasync/common/service/DiagnosticServicePerformanceTest.java` **新增 SC-001 性能门禁测试**：模拟典型日志/配置规模，断言诊断包生成耗时 ≤ 30000 ms 且 `DiagnosticResultVO.durationMs` 已正确填充
- [X] T022 [P] [US1] 在 `src/test/java/top/lldwb/alistmediasync/common/service/DiagnosticServiceSideEffectTest.java` **新增 FR-012 副作用断言测试**：使用 Spy/Mock 验证诊断生成过程未调用 `SyncService`、`TranscodeService`、`WebhookService` 写入路径，未触发 JPA 写事务

### 用户故事 1 的实现

- [X] T023 [P] [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 实现诊断包生成编排，创建临时目录并原子刷新 `diagnostics/latest`
- [X] T024 [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 实现 `summary.md` 内容生成，覆盖基本信息、最近失败、关键证据、缺失信息和建议下一步
- [X] T025 [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 实现日志证据收集，输出 `logs/error.log` 和 `logs/app.log` 摘录或缺失说明，并在 `DiagnosticResultVO.durationMs` 中记录耗时（供 T021 断言）
- [X] T026 [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 实现 `environment.txt` 和 `last-run.json` 生成，确保不可获取信息写入 `missingItems` 列表
- [X] T027 [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/controller/DiagnosticController.java` 实现受认证保护的诊断触发接口（`POST /api/diagnostics/run`），返回诊断包路径和摘要路径
- [X] T028 [US1] 在 `scripts/diagnose.sh` 实现本地开发与 Linux/Docker 诊断入口，支持 `--output`、`--trace-id`、`--max-lines`
- [X] T029 [US1] 在 `scripts/diagnose.bat` 实现 Windows 与一体化启动包诊断入口，支持 `--output`、`--trace-id`、`--max-lines`
- [X] T030 [US1] 在 `scripts/start.sh` 添加诊断命令提示和诊断目录变量传递，不改变原启动流程
- [X] T031 [US1] 在 `scripts/start.bat` 添加诊断命令提示和诊断目录变量传递，不改变原启动流程
- [X] T032 [US1] 在 `docker-compose.yml` 中补充 `logs/` 与 `diagnostics/` 目录的挂载配置：在现有 `alist-media-sync-data:/app/data` 卷基础上，新增 `./logs:/app/logs` 和 `./diagnostics:/app/diagnostics` 绑定挂载（或确认 data 卷已覆盖这些路径），确保宿主机可直接读取诊断包
- [X] T033 [US1] 运行 `./mvnw -Dtest=DiagnosticServiceTest,DiagnosticControllerTest,DiagnosticServicePerformanceTest,DiagnosticServiceSideEffectTest test` 验证诊断服务、端点、SC-001 性能门禁与 FR-012 副作用断言；验证诊断包中所有任务执行记录均包含非空 traceId（SC-003）

**检查点**：用户故事 1 可独立交付；生成耗时 ≤ 30 秒；诊断过程零业务副作用；用户可以把 `summary.md` 交给 AI 排查。

---

## 阶段 4：用户故事 2 — 通过 traceId 串联一次任务的完整日志（优先级：P2）

**目标**：同步、转码、Webhook、手动任务和 HTTP 请求都能通过同一 traceId 关联关键日志，快速还原一次任务的完整执行链路；任意失败可在 2 分钟内通过摘要定位（SC-002）；新错误事件 ≥ 90% 包含完整结构化字段（SC-005）。

**独立测试**：执行同步、转码、Webhook 任务，确认任务开始、关键步骤、失败或完成日志均包含同一个 traceId；并发任务 traceId 不混淆；按 SC-002 计时验证；按 SC-005 抽样验证结构化字段覆盖率。

### 用户故事 2 的测试

- [X] T034 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/sync/service/SyncServiceTest.java` 增加同步任务 traceId 贯穿”开始/扫描/比对/执行/完成/失败”关键日志上下文的测试
- [X] T035 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/transcode/service/TranscodeServiceTest.java` 增加转码任务 traceId 贯穿”创建/下载/转码/上传/完成/失败/重试”关键日志上下文的测试
- [X] T036 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/webhook/service/WebhookServiceTest.java` 增加 Webhook 事件 traceId 贯穿”接收/去重/规则匹配/异步处理/完成/失败”关键日志上下文的测试
- [X] T037 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/common/config/GlobalExceptionHandlerTest.java` 编写异常响应仍返回 `X-Trace-Id` 且日志包含错误类别、错误消息、可定位原因的测试
- [X] T038 [P] [US2] 在 `src/main/frontend/src/api/client.test.ts` 编写前端失败请求读取并保留 `X-Trace-Id` 的测试
- [X] T039 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/common/observability/StructuredErrorCoverageTest.java` **新增 SC-005 覆盖率门禁测试**：使用 Logback `ListAppender` 收集 sync/transcode/webhook 三类典型失败路径的 ERROR 事件，统计 `module`、`operation`、`traceId`、`errorType`、`message`、`context.cause` 六字段完整事件占比 ≥ 90%
- [X] T040 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/common/observability/TraceLookupLatencyTest.java` **新增 SC-002 可定位性测试**：模拟 1 次失败 + 100 行混杂日志，断言通过 traceId 在结构化日志中定位失败记录首条命中耗时 ≤ 50 ms，作为”2 分钟定位”的下界证据

### 用户故事 2 的实现

- [X] T041 [US2] 在 `src/main/java/top/lldwb/alistmediasync/sync/service/SyncService.java` 为手动触发和定时触发（含 `ScheduleService` CRON/INTERVAL 调度路径）同步任务设置任务级 traceId 并在结束后清理上下文，关键日志统一包含 `module=sync`、`operation`、`errorType`（失败时）
- [X] T042 [US2] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeService.java` 为转码任务创建、执行、重试和失败路径设置任务级 traceId，并补全 SC-005 要求的结构化字段
- [X] T043 [US2] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeFileProcessor.java` 将转码单文件处理日志纳入当前 traceId 上下文
- [X] T044 [US2] 在 `src/main/java/top/lldwb/alistmediasync/webhook/service/WebhookService.java` 为 Webhook 事件接收、去重、规则匹配和异步处理设置 traceId，并补全 `errorType` 与 `context.cause`
- [X] T045 [US2] 在 `src/main/java/top/lldwb/alistmediasync/common/config/GlobalExceptionHandler.java` 确保异常处理日志包含 traceId、errorType、可定位原因，并保留 `X-Trace-Id` 响应头
- [X] T046 [US2] 在 `src/main/java/top/lldwb/alistmediasync/common/config/RestClientConfig.java` 中为 RestClient 配置请求/响应拦截器，确保外部 AList 请求日志包含当前 traceId（从 MDC 读取）且不记录敏感头原文（Authorization、Cookie 等头值在日志中替换为 `***REDACTED***`）
- [X] T047 [US2] 在 `src/main/frontend/src/api/client.ts` 读取响应头 `X-Trace-Id`，在请求失败时附加到前端错误对象或错误提示上下文
- [X] T048 [US2] 在 `src/main/frontend/src/types/api.ts` 增加前端错误上下文中的 traceId 类型字段
- [X] T049 [US2] 运行 `./mvnw -Dtest=SyncServiceTest,TranscodeServiceTest,WebhookServiceTest,GlobalExceptionHandlerTest,StructuredErrorCoverageTest,TraceLookupLatencyTest test` 验证任务 traceId 链路、SC-005 覆盖率门禁、SC-002 可定位性门禁与 SC-003 任务级 traceId 非空唯一性

**检查点**：用户故事 2 可独立验证；新增/修改错误路径 ≥ 90% 包含完整结构化字段；任意失败可通过 traceId 在结构化日志中定位。

---

## 阶段 5：用户故事 3 — 安全分享诊断信息（优先级：P3）

**目标**：诊断包和日志摘要自动隐藏敏感信息，用户可以放心把诊断包提供给 AI 或开发者。

**独立测试**：配置包含密码、Token、密钥、Authorization、Cookie 后生成诊断包，确认原始敏感值不出现在任何诊断输出中，同时字段名和必要上下文仍保留；空配置不被误标记为已脱敏。

### 用户故事 3 的测试

- [X] T050 [P] [US3] 在 `src/test/java/top/lldwb/alistmediasync/common/util/SensitiveDataMaskerTest.java` 编写字段名脱敏（password/token/secret/key/authorization/cookie/credential）、值模式脱敏、空值识别、URL 查询参数脱敏测试
- [X] T051 [P] [US3] 在 `src/test/java/top/lldwb/alistmediasync/common/service/DiagnosticServiceTest.java` 增加 `config.redacted.json` 不泄露密码、Token、Cookie、Authorization 和密钥的测试，并验证 `emptyKeys`/`missingKeys`/`redactedKeys` 分类正确
- [X] T052 [P] [US3] 在 `src/test/java/top/lldwb/alistmediasync/common/config/RestClientConfigTest.java` 增加外部请求日志不输出敏感头原文的测试
- [X] T053 [P] [US3] 在 `src/test/java/top/lldwb/alistmediasync/common/service/DiagnosticPackageRedactionScanTest.java` **新增端到端脱敏全包扫描测试**：生成诊断包后递归遍历 `diagnostics/latest/**`，断言文件内容不含测试配置中投入的明文敏感样本字符串

### 用户故事 3 的实现

- [X] T054 [P] [US3] 在 `src/main/java/top/lldwb/alistmediasync/common/util/SensitiveDataMasker.java` 实现敏感字段、敏感值、请求头和 URL 查询参数脱敏，统一占位 `***REDACTED***`
- [X] T055 [US3] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 集成 `SensitiveDataMasker` 生成 `config/config.redacted.json`
- [X] T056 [US3] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 区分空配置、缺失配置和已脱敏配置，写入 `emptyKeys`、`missingKeys`、`redactedKeys`
- [X] T057 [US3] 在 `src/main/java/top/lldwb/alistmediasync/common/config/RestClientConfig.java` 对 Authorization、Cookie、Token 类请求头和响应上下文统一脱敏后再记录日志
- [X] T058 [US3] 在 `scripts/diagnose.sh` 确保脚本采集环境变量和配置文件时调用或复用脱敏规则，禁止输出原始敏感值
- [X] T059 [US3] 在 `scripts/diagnose.bat` 确保脚本采集环境变量和配置文件时调用或复用脱敏规则，禁止输出原始敏感值
- [X] T060 [US3] 运行 `./mvnw -Dtest=SensitiveDataMaskerTest,DiagnosticServiceTest,RestClientConfigTest,DiagnosticPackageRedactionScanTest test` 验证脱敏能力与端到端脱敏全包扫描

**检查点**：用户故事 3 可独立验证；诊断包递归扫描不含原始敏感值；保留字段名与上下文。

---

## 阶段 6：润色与跨领域关注点

**目的**：完成文档、量化回归验证和质量门禁。

- [X] T061 [P] 在 `README.md` 更新诊断系统使用说明、`X-Trace-Id` 响应头说明、日志路径和诊断命令示例
- [X] T062 [P] 在 `src/main/resources/application.template.yaml` 同步新增日志与诊断相关配置说明
- [X] T063 [P] 在 `assembly/bootstrap.xml` 的启动脚本 fileSet 中新增 `diagnose.sh` 和 `diagnose.bat` 包含规则；在 `Dockerfile` 的 COPY 指令中新增 `scripts/diagnose.sh` 复制并确保可执行权限（`chmod +x`）
- [X] T064 在 `scripts/verify-build.sh` 增加 `diagnose.sh`、`diagnose.bat`、日志配置文件、`logs/`、`diagnostics/` 产物存在性检查
- [X] T065 运行 `./mvnw test` 完成后端全量测试（结果：279 个测试全部通过）
- [ ] T066 在 T028 完成后运行 `scripts/diagnose-smoke-test.sh` 验证 Linux/Docker 诊断入口（脚本已编写；正式 CI Linux 环境执行）
- [ ] T067 在 T029 完成后运行 `scripts/diagnose-smoke-test.bat` 验证 Windows 诊断入口（脚本已编写；正式 CI Windows 环境执行）
- [X] T068 按 `specs/009-lightweight-diagnostics/quickstart.md` 完成 6 个快速验证场景并在 `specs/009-lightweight-diagnostics/quickstart-results.md` **新增并记录每个场景的实测结果**：场景 4 必须记录 `summary.md` 生成耗时（SC-001 ≤ 30 s），场景 1–6 必须勾选通过/未通过并附 traceId
- [X] T069 在 `specs/009-lightweight-diagnostics/sc-metrics.md` **新增并提交 SC-001/SC-002/SC-003/SC-004/SC-005/SC-006 的量化结果汇总表**：每项注明测量任务来源（T021/T040/T049/T053/T060/T068）与是否达标
- [X] T070 按章程原则 VIII 将 `specs/009-lightweight-diagnostics/spec.md` 状态字段在实现阶段结束时更新为 `已完成`

---

## 依赖与执行顺序

### 阶段依赖

- **阶段 1 设置**：无依赖，可立即开始。
- **阶段 2 基础**：依赖阶段 1；阻塞所有用户故事。
- **阶段 3 US1**：依赖阶段 2；MVP 范围。
- **阶段 4 US2**：依赖阶段 2；可在基础完成后与 US1 并行，但需避免同文件冲突（`DiagnosticService`、`RestClientConfig` 在 US1/US3 也涉及）。
- **阶段 5 US3**：依赖阶段 2；建议在 US1 的 `DiagnosticService` 初版（T023–T026）后启动 T055–T056，便于集成脱敏。
- **阶段 6 润色**：依赖所有期望用户故事完成。

### 关键任务级依赖（明确化）

- T011 依赖 T010 完成。
- T016 依赖 T010–T015 全部完成。
- T021、T022 依赖 T023–T026 完成（被测对象先实现）；测试编写本身可与实现并行起步，但断言执行必须在实现后。
- T019、T020 编写本身可并行；smoke 实际执行（T066、T067）依赖 T028、T029。
- T039、T040 依赖 T041–T046 至少一个失败路径已实现。
- T053 依赖 T055–T056 完成。
- T069 依赖 T021、T040、T049、T053、T060、T068 全部完成。
- T070 依赖 T069 完成。

### 用户故事依赖

- **US1（P1）**：基础阶段完成后可开始；通过 T021/T022 强制 SC-001 与 FR-012 门禁。
- **US2（P2）**：基础阶段完成后可开始；通过 T039/T040 强制 SC-005 与 SC-002 门禁。
- **US3（P3）**：基础阶段完成后可开始；通过 T053 强制端到端脱敏证据。

### 每个用户故事内部

- 测试编写可与实现并行起步，但 T021/T022/T039/T040/T053 这类“门禁测试”必须在对应实现完成后执行；
- 工具/DTO 先于服务；
- 服务先于 Controller 和脚本集成；
- 脚本入口先于 smoke test 执行；
- 每个故事完成后必须独立运行对应测试和 quickstart 场景。

### 并行机会

- T002–T007 可并行读取与检查。
- T008、T009 可并行编写基础测试。
- T014、T015 可并行定义 DTO。
- T017、T018、T019、T020、T021、T022 可并行编写 US1 测试。
- T034–T040 可并行编写 US2 测试。
- T050、T051、T052、T053 可并行编写 US3 测试。
- T061、T062、T063 可并行处理文档、模板和容器产物检查。

---

## 并行示例：用户故事 1

```bash
# 并行编写用户故事 1 的测试
任务："在 DiagnosticServiceTest.java 编写诊断包生成测试"
任务："在 DiagnosticControllerTest.java 编写诊断端点测试"
任务："在 DiagnosticServicePerformanceTest.java 编写 SC-001 ≤ 30 s 性能门禁"
任务："在 DiagnosticServiceSideEffectTest.java 编写 FR-012 无副作用断言"
任务："在 scripts/diagnose-smoke-test.sh 编写 Linux/Docker smoke test"
任务："在 scripts/diagnose-smoke-test.bat 编写 Windows smoke test"

# 并行实现独立入口
任务："在 scripts/diagnose.sh 实现 Linux/Docker 诊断入口"
任务："在 scripts/diagnose.bat 实现 Windows 诊断入口"
```

## 并行示例：用户故事 2

```bash
# 并行覆盖不同业务模块的 traceId 与结构化字段测试
任务："在 SyncServiceTest.java 增加同步任务 traceId 测试"
任务："在 TranscodeServiceTest.java 增加转码任务 traceId 测试"
任务："在 WebhookServiceTest.java 增加 Webhook traceId 测试"
任务："在 StructuredErrorCoverageTest.java 编写 SC-005 ≥ 90% 覆盖率门禁"
任务："在 TraceLookupLatencyTest.java 编写 SC-002 可定位性门禁"
```

## 并行示例：用户故事 3

```bash
# 并行覆盖脱敏规则、集成点与端到端扫描
任务："在 SensitiveDataMaskerTest.java 编写脱敏规则测试"
任务："在 DiagnosticServiceTest.java 增加诊断包不泄露敏感值测试"
任务："在 RestClientConfigTest.java 增加外部请求日志脱敏测试"
任务："在 DiagnosticPackageRedactionScanTest.java 编写端到端脱敏全包扫描测试"
```

---

## 实现策略

### MVP 优先（仅用户故事 1）

1. 完成阶段 1：设置。
2. 完成阶段 2：基础 traceId、日志上下文和 ERROR 分流。
3. 完成阶段 3：用户故事 1，一键生成诊断包，并通过 T021（SC-001 ≤ 30 s）与 T022（FR-012 无副作用）门禁。
4. 停止并验证：运行 `DiagnosticServiceTest`、`DiagnosticControllerTest`、`DiagnosticServicePerformanceTest`、`DiagnosticServiceSideEffectTest` 与诊断脚本 smoke test，并打开 `diagnostics/latest/summary.md`。
5. MVP 可交付：用户能把诊断包交给 AI 排查。

### 增量交付

1. 设置 + 基础 → 所有 HTTP 请求具备 `X-Trace-Id`。
2. US1 → 生成诊断包（带性能与副作用门禁）。
3. US2 → 扩展任务级 traceId（带 SC-002/SC-005 门禁）。
4. US3 → 加强脱敏（带端到端扫描门禁）。
5. 润色 → README、模板、Docker、verify-build、quickstart 实测、SC 指标汇总、状态字段。

### 质量门禁

- 每个 Java 类变更必须有对应测试任务完成（原则 V）。
- 不新增第三方依赖（原则 VI）。
- Controller 不写业务逻辑；诊断编排在 Service，工具逻辑在 util（原则 I）。
- ERROR 日志必须包含 traceId、errorType、message、可定位原因（原则 VII，SC-005）。
- 诊断包不得包含原始敏感值（原则 VII，SC-004）。
- 诊断生成耗时 ≤ 30 s（SC-001）。
- 诊断生成过程零业务副作用（FR-012）。
- 实现完成后必须更新 README.md（原则 IX）。
- 实现完成后 spec.md 状态字段必须更新为 `已完成`（原则 VIII）。

## 任务统计

- 总任务数：70
- 阶段 1 设置：7
- 阶段 2 基础：9
- 用户故事 1：17
- 用户故事 2：16
- 用户故事 3：11
- 润色与跨领域关注点：10

## 新增/强化的验证任务

| 来源问题 | 任务 ID | 类型 |
|----------|---------|------|
| SC-001 30 秒性能 | T021 | 新增性能门禁测试 |
| FR-012 无业务副作用 | T022 | 新增副作用断言测试 |
| SC-005 ≥ 90% 错误结构化 | T039 | 新增覆盖率门禁测试 |
| SC-002 ≤ 2 分钟定位 | T040 | 新增可定位性门禁测试 |
| SC-004 端到端脱敏 | T053 | 新增端到端脱敏全包扫描 |
| 依赖顺序歧义 | T011/T016/T021/T022/T039/T040/T053/T066/T067/T069/T070 | 显式依赖说明 |
| SC 指标汇总 | T068/T069 | 新增 quickstart 实测记录与 SC 指标汇总文档 |
