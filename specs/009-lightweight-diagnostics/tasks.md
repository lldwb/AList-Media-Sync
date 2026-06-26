# 任务：轻量诊断系统

**输入**：来自 `/specs/009-lightweight-diagnostics/` 的设计文档

**前提条件**：plan.md、spec.md、research.md、data-model.md、contracts/、quickstart.md

**测试**：根据项目章程原则 V，所有 Java 类变更必须同步测试；因此本任务清单包含对应测试任务。

**组织方式**：任务按用户故事分组，支持每个故事独立实现和验证。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（US1、US2、US3）
- 每个任务描述包含确切文件路径

## 阶段 1：设置（共享准备）

**目的**：确认上下文、建立诊断功能所需的共享文件位置，不改变业务行为。

- [ ] T001 读取后端通用模块规则文件 `src/main/java/top/lldwb/alistmediasync/common/AGENTS.md`
- [ ] T002 [P] 读取同步模块规则文件 `src/main/java/top/lldwb/alistmediasync/sync/AGENTS.md`
- [ ] T003 [P] 读取转码模块规则文件 `src/main/java/top/lldwb/alistmediasync/transcode/AGENTS.md`
- [ ] T004 [P] 读取 Webhook 模块规则文件 `src/main/java/top/lldwb/alistmediasync/webhook/AGENTS.md`
- [ ] T005 [P] 检查现有日志配置和日志输出路径 `src/main/resources/application.yaml`
- [ ] T006 [P] 检查一体化启动脚本中的目录变量与 JVM 参数 `scripts/start.sh`
- [ ] T007 [P] 检查一体化启动脚本中的目录变量与 JVM 参数 `scripts/start.bat`

---

## 阶段 2：基础（阻塞性前置条件）

**目的**：建立所有用户故事共享的 traceId、日志上下文、错误日志分流和基础 DTO。

**⚠️ 关键**：完成本阶段前不要开始任何用户故事实现。

### 基础测试

- [ ] T008 [P] 在 `src/test/java/top/lldwb/alistmediasync/common/util/TraceContextTest.java` 编写 traceId 生成、合法性校验、请求头继承和非法值拒绝测试
- [ ] T009 [P] 在 `src/test/java/top/lldwb/alistmediasync/common/config/TraceIdFilterTest.java` 编写 `X-Trace-Id` 响应头、MDC 设置和请求结束清理测试

### 基础实现

- [ ] T010 [P] 在 `src/main/java/top/lldwb/alistmediasync/common/util/TraceContext.java` 实现 traceId 生成、校验、MDC 读写和清理工具
- [ ] T011 在 `src/main/java/top/lldwb/alistmediasync/common/config/TraceIdFilter.java` 实现请求级 traceId 过滤器，统一处理 `X-Trace-Id` 请求头、响应头和 MDC 生命周期
- [ ] T012 在 `src/main/resources/application.yaml` 中补充日志文件目录配置和包含 traceId 的日志 pattern 配置
- [ ] T013 在 `src/main/resources/logback-spring.xml` 中配置应用日志和 `logs/error.log` 的 ERROR 分流 appender
- [ ] T014 [P] 在 `src/main/java/top/lldwb/alistmediasync/common/dto/DiagnosticResultVO.java` 定义诊断生成结果视图
- [ ] T015 [P] 在 `src/main/java/top/lldwb/alistmediasync/common/dto/DiagnosticSummaryVO.java` 定义诊断摘要视图
- [ ] T016 运行 `./mvnw -Dtest=TraceContextTest,TraceIdFilterTest test` 验证基础 traceId 测试

**检查点**：所有 HTTP 响应都应包含 `X-Trace-Id`，ERROR 日志具备独立输出基础。

---

## 阶段 3：用户故事 1 — 一键生成可交给 AI 的诊断包（优先级：P1）🎯 MVP

**目标**：用户通过一次明确操作生成 `diagnostics/latest/summary.md` 和关键证据文件，AI 可直接读取诊断包排查问题。

**独立测试**：触发或模拟一次失败后运行诊断命令，确认 `diagnostics/latest/summary.md` 存在，包含最近失败、traceId、关键证据、缺失信息和下一步建议。

### 用户故事 1 的测试

- [ ] T017 [P] [US1] 在 `src/test/java/top/lldwb/alistmediasync/common/service/DiagnosticServiceTest.java` 编写生成 `summary.md`、复制错误日志摘录和记录缺失信息的单元测试
- [ ] T018 [P] [US1] 在 `src/test/java/top/lldwb/alistmediasync/common/controller/DiagnosticControllerTest.java` 编写诊断触发端点返回 `ApiResult<DiagnosticResultVO>` 和 `X-Trace-Id` 的 WebMvc 测试
- [ ] T019 [P] [US1] 在 `scripts/diagnose-smoke-test.sh` 编写 Linux/Docker 诊断脚本 smoke test
- [ ] T020 [P] [US1] 在 `scripts/diagnose-smoke-test.bat` 编写 Windows 诊断脚本 smoke test

### 用户故事 1 的实现

- [ ] T021 [P] [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 实现诊断包生成编排，创建临时目录并刷新 `diagnostics/latest`
- [ ] T022 [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 实现 `summary.md` 内容生成，覆盖基本信息、最近失败、关键证据、缺失信息和建议下一步
- [ ] T023 [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 实现日志证据收集，输出 `logs/error.log` 和 `logs/app.log` 摘录或缺失说明
- [ ] T024 [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 实现 `environment.txt` 和 `last-run.json` 生成，确保不可获取信息写入缺失列表
- [ ] T025 [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/controller/DiagnosticController.java` 实现受认证保护的诊断触发接口，返回诊断包路径和摘要路径
- [ ] T026 [US1] 在 `scripts/diagnose.sh` 实现本地开发与 Linux/Docker 诊断入口，支持 `--output`、`--trace-id`、`--max-lines`
- [ ] T027 [US1] 在 `scripts/diagnose.bat` 实现 Windows 与一体化启动包诊断入口，支持 `--output`、`--trace-id`、`--max-lines`
- [ ] T028 [US1] 在 `scripts/start.sh` 添加诊断命令提示和诊断目录变量传递，不改变原启动流程
- [ ] T029 [US1] 在 `scripts/start.bat` 添加诊断命令提示和诊断目录变量传递，不改变原启动流程
- [ ] T030 [US1] 在 `docker-compose.yml` 确认或补充 `logs/` 与 `diagnostics/` 的可访问挂载或说明配置
- [ ] T031 [US1] 运行 `./mvnw -Dtest=DiagnosticServiceTest,DiagnosticControllerTest test` 验证诊断服务和端点

**检查点**：用户故事 1 可独立交付；用户可以生成诊断包并把 `summary.md` 交给 AI 排查。

---

## 阶段 4：用户故事 2 — 通过 traceId 串联一次任务的完整日志（优先级：P2）

**目标**：同步、转码、Webhook、手动任务和 HTTP 请求都能通过同一 traceId 关联关键日志，快速还原一次任务的完整执行链路。

**独立测试**：执行一次同步、转码或 Webhook 任务，确认任务开始、关键步骤、失败或完成日志均包含同一个 traceId；并发任务 traceId 不混淆。

### 用户故事 2 的测试

- [ ] T032 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/sync/service/SyncServiceTest.java` 增加同步任务 traceId 贯穿关键日志上下文的测试
- [ ] T033 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/transcode/service/TranscodeServiceTest.java` 增加转码任务 traceId 贯穿关键日志上下文的测试
- [ ] T034 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/webhook/service/WebhookServiceTest.java` 增加 Webhook 事件 traceId 贯穿关键日志上下文的测试
- [ ] T035 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/common/config/GlobalExceptionHandlerTest.java` 编写异常响应仍返回 `X-Trace-Id` 且日志包含错误类别的测试
- [ ] T036 [P] [US2] 在 `src/main/frontend/src/api/client.test.ts` 编写前端失败请求读取并保留 `X-Trace-Id` 的测试

### 用户故事 2 的实现

- [ ] T037 [US2] 在 `src/main/java/top/lldwb/alistmediasync/sync/service/SyncService.java` 为手动触发和定时触发同步任务设置任务级 traceId 并在结束后清理上下文
- [ ] T038 [US2] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeService.java` 为转码任务创建、执行、重试和失败路径设置任务级 traceId
- [ ] T039 [US2] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeFileProcessor.java` 将转码单文件处理日志纳入当前 traceId 上下文
- [ ] T040 [US2] 在 `src/main/java/top/lldwb/alistmediasync/webhook/service/WebhookService.java` 为 Webhook 事件接收、去重、规则匹配和异步处理设置 traceId
- [ ] T041 [US2] 在 `src/main/java/top/lldwb/alistmediasync/common/config/GlobalExceptionHandler.java` 确保异常处理日志包含 traceId、错误类别和可定位原因
- [ ] T042 [US2] 在 `src/main/java/top/lldwb/alistmediasync/common/config/RestClientConfig.java` 确保外部 AList 请求日志包含当前 traceId 且不记录敏感头原文
- [ ] T043 [US2] 在 `src/main/frontend/src/api/client.ts` 读取响应头 `X-Trace-Id`，在请求失败时附加到前端错误对象或错误提示上下文
- [ ] T044 [US2] 在 `src/main/frontend/src/types/api.ts` 增加前端错误上下文中的 traceId 类型字段
- [ ] T045 [US2] 运行 `./mvnw -Dtest=SyncServiceTest,TranscodeServiceTest,WebhookServiceTest,GlobalExceptionHandlerTest test` 验证任务 traceId 链路

**检查点**：用户故事 2 可独立验证；任一任务失败后可通过 traceId 找到同一执行链路的主要日志。

---

## 阶段 5：用户故事 3 — 安全分享诊断信息（优先级：P3）

**目标**：诊断包和日志摘要自动隐藏敏感信息，用户可以放心把诊断包提供给 AI 或开发者。

**独立测试**：配置包含密码、Token、密钥、Authorization、Cookie 后生成诊断包，确认原始敏感值不出现在任何诊断输出中，同时字段名和必要上下文仍保留。

### 用户故事 3 的测试

- [ ] T046 [P] [US3] 在 `src/test/java/top/lldwb/alistmediasync/common/util/SensitiveDataMaskerTest.java` 编写字段名脱敏、值模式脱敏、空值识别和 URL 查询参数脱敏测试
- [ ] T047 [P] [US3] 在 `src/test/java/top/lldwb/alistmediasync/common/service/DiagnosticServiceTest.java` 增加 `config.redacted.json` 不泄露密码、Token、Cookie、Authorization 和密钥的测试
- [ ] T048 [P] [US3] 在 `src/test/java/top/lldwb/alistmediasync/common/config/RestClientConfigTest.java` 增加外部请求日志不输出敏感头原文的测试

### 用户故事 3 的实现

- [ ] T049 [P] [US3] 在 `src/main/java/top/lldwb/alistmediasync/common/util/SensitiveDataMasker.java` 实现敏感字段、敏感值、请求头和 URL 查询参数脱敏
- [ ] T050 [US3] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 集成 `SensitiveDataMasker` 生成 `config/config.redacted.json`
- [ ] T051 [US3] 在 `src/main/java/top/lldwb/alistmediasync/common/service/DiagnosticService.java` 区分空配置、缺失配置和已脱敏配置，写入 `emptyKeys`、`missingKeys`、`redactedKeys`
- [ ] T052 [US3] 在 `src/main/java/top/lldwb/alistmediasync/common/config/RestClientConfig.java` 对 Authorization、Cookie、Token 类请求头和响应上下文统一脱敏后再记录日志
- [ ] T053 [US3] 在 `scripts/diagnose.sh` 确保脚本采集环境变量和配置文件时调用或复用脱敏规则，禁止输出原始敏感值
- [ ] T054 [US3] 在 `scripts/diagnose.bat` 确保脚本采集环境变量和配置文件时调用或复用脱敏规则，禁止输出原始敏感值
- [ ] T055 [US3] 运行 `./mvnw -Dtest=SensitiveDataMaskerTest,DiagnosticServiceTest,RestClientConfigTest test` 验证脱敏能力

**检查点**：用户故事 3 可独立验证；诊断包可以安全分享且保留足够排障上下文。

---

## 阶段 6：润色与跨领域关注点

**目的**：完成文档、回归验证和质量门禁。

- [ ] T056 [P] 在 `README.md` 更新诊断系统使用说明、`X-Trace-Id` 响应头说明、日志路径和诊断命令示例
- [ ] T057 [P] 在 `src/main/resources/application.template.yaml` 同步新增日志与诊断相关配置说明
- [ ] T058 [P] 在 `Dockerfile` 确认诊断脚本进入一体化/容器产物并具备可执行权限
- [ ] T059 在 `scripts/verify-build.sh` 增加 `diagnose.sh`、`diagnose.bat`、日志配置和诊断目录产物检查
- [ ] T060 运行 `./mvnw test` 完成后端全量测试
- [ ] T061 运行 `scripts/diagnose-smoke-test.sh` 验证 Linux/Docker 诊断入口
- [ ] T062 运行 `scripts/diagnose-smoke-test.bat` 验证 Windows 诊断入口
- [ ] T063 按 `specs/009-lightweight-diagnostics/quickstart.md` 完成 6 个快速验证场景并记录结果
- [ ] T064 检查 `specs/009-lightweight-diagnostics/spec.md` 状态字段在实现阶段结束时可按流程更新为 `已完成`

---

## 依赖与执行顺序

### 阶段依赖

- **阶段 1 设置**：无依赖，可立即开始。
- **阶段 2 基础**：依赖阶段 1；阻塞所有用户故事。
- **阶段 3 US1**：依赖阶段 2；MVP 范围。
- **阶段 4 US2**：依赖阶段 2；可在 US1 后执行，也可在基础完成后与 US1 并行，但需要避免同文件冲突。
- **阶段 5 US3**：依赖阶段 2；建议在 US1 的 `DiagnosticService` 初版后执行，便于集成脱敏。
- **阶段 6 润色**：依赖计划内目标用户故事完成。

### 用户故事依赖

- **US1（P1）**：基础阶段完成后可开始；不依赖 US2/US3，提供 MVP 诊断包能力。
- **US2（P2）**：基础阶段完成后可开始；提升任务级 traceId 覆盖率。
- **US3（P3）**：基础阶段完成后可开始；与 US1 的诊断输出集成，增强安全分享能力。

### 每个用户故事内部

- 测试任务优先于实现任务。
- 工具/DTO 先于服务。
- 服务先于 Controller 和脚本集成。
- 脚本入口先于 smoke test 执行。
- 每个故事完成后必须独立运行对应测试和 quickstart 场景。

### 并行机会

- T002、T003、T004、T005、T006、T007 可并行读取与检查。
- T008、T009 可并行编写基础测试。
- T014、T015 可并行定义 DTO。
- T017、T018、T019、T020 可并行编写 US1 测试。
- T032、T033、T034、T035、T036 可并行编写 US2 测试。
- T046、T047、T048 可并行编写 US3 测试。
- T056、T057、T058 可并行处理文档、模板和容器产物检查。

---

## 并行示例：用户故事 1

```bash
# 并行编写用户故事 1 的测试
任务："在 src/test/java/top/lldwb/alistmediasync/common/service/DiagnosticServiceTest.java 编写诊断包生成测试"
任务："在 src/test/java/top/lldwb/alistmediasync/common/controller/DiagnosticControllerTest.java 编写诊断端点测试"
任务："在 scripts/diagnose-smoke-test.sh 编写 Linux/Docker smoke test"
任务："在 scripts/diagnose-smoke-test.bat 编写 Windows smoke test"

# 并行实现用户故事 1 的独立入口
任务："在 scripts/diagnose.sh 实现 Linux/Docker 诊断入口"
任务："在 scripts/diagnose.bat 实现 Windows 诊断入口"
```

## 并行示例：用户故事 2

```bash
# 并行覆盖不同业务模块的 traceId 测试
任务："在 SyncServiceTest.java 增加同步任务 traceId 测试"
任务："在 TranscodeServiceTest.java 增加转码任务 traceId 测试"
任务："在 WebhookServiceTest.java 增加 Webhook traceId 测试"
```

## 并行示例：用户故事 3

```bash
# 并行覆盖脱敏规则和集成点
任务："在 SensitiveDataMaskerTest.java 编写脱敏规则测试"
任务："在 DiagnosticServiceTest.java 增加诊断包不泄露敏感值测试"
任务："在 RestClientConfigTest.java 增加外部请求日志脱敏测试"
```

---

## 实现策略

### MVP 优先（仅用户故事 1）

1. 完成阶段 1：设置。
2. 完成阶段 2：基础 traceId、日志上下文和 ERROR 分流。
3. 完成阶段 3：用户故事 1，一键生成诊断包。
4. 停止并验证：运行 `DiagnosticServiceTest`、`DiagnosticControllerTest`、诊断脚本 smoke test，并打开 `diagnostics/latest/summary.md`。
5. MVP 可交付：用户能把诊断包交给 AI 排查。

### 增量交付

1. 设置 + 基础 → 所有 HTTP 请求具备 `X-Trace-Id`。
2. US1 → 生成诊断包，解决 AI 看不到日志问题。
3. US2 → 扩展任务级 traceId，提升跨任务日志定位能力。
4. US3 → 加强脱敏，确保诊断包可安全分享。
5. 润色 → README、模板、Docker、一体化产物和全量验证。

### 质量门禁

- 每个 Java 类变更必须有对应测试任务完成。
- 不新增第三方依赖。
- Controller 不写业务逻辑；诊断编排在 Service，工具逻辑在 util。
- ERROR 日志必须包含 traceId 和错误原因。
- 诊断包不得包含原始敏感值。
- 实现完成后必须更新 README.md。

## 任务统计

- 总任务数：64
- 阶段 1 设置：7
- 阶段 2 基础：9
- 用户故事 1：15
- 用户故事 2：14
- 用户故事 3：10
- 润色与跨领域关注点：9
