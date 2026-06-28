# 功能规格：Service 层重复代码与超长方法重构

**功能分支**：`010-service-layer-refactor`

**创建日期**：2026-06-28

**状态**：草案

**输入**：用户描述："Service 层（同步/转码/Webhook）存在路径工具方法重复、traceId 样板重复、JsonMapper 重复实例化、futures 收集逻辑重复、`executeSyncTaskInternal`(224 行) 与 `doProcess`(128 行) 等超长方法以及 4-5 层嵌套等问题，需以零行为变更为前提进行重构。"

## 澄清

### 会话 2026-06-28

- Q: 路径工具方法在 SyncService / TranscodeService / TranscodeFileProcessor 三个类中各自实现且语义不一致（SyncService 多了 `trimLeadingSlash`），统一后应以哪种语义为基准？ → A: 以 SyncService 现行语义为基准（即拼接前一律去掉 `name` 前导斜杠），并补齐另外两处的行为
- Q: 重构是否允许引入新的第三方依赖（如 Guava `Files`、Apache Commons IO）？ → A: 不允许，遵守章程原则 VI（YAGNI），仅使用 JDK 21 内置 API
- Q: `executeSyncTaskInternal` 拆分后，是否需要拆分到单独的子服务（如 `SyncPipelineService`）？ → A: 不拆分类，仅拆分为同类的私有方法，并引入 `SyncContext` 内部数据结构减少长参数列表
- Q: 重构是否需要等待 `Service 层测试覆盖率缺口`（memory 记录）问题先行解决？ → A: 不等待，但每个被改动的 Service 必须新增/补齐"行为对等回归测试"作为重构的护栏
- Q: 重构应以一个 PR 完成还是拆为多个 PR？ → A: 拆为 3 个 PR：工具类抽取 → SyncService 拆分 → TranscodeService/Webhook 收尾

## 用户场景与测试 *（强制）*

### 用户故事 1 — 通用工具类抽取消除重复代码（优先级：P1）

作为后端维护者，当我修改路径拼接、traceId 上下文管理、JSON 序列化逻辑时，我希望仅在一处修改即可影响全部 Service 调用方，而不是在 3 个文件中重复实现相同逻辑。

**为什么是此优先级**：路径拼接的语义不一致已构成潜在 bug（`SyncService.concatPath` 处理前导斜杠而另两处不处理）；`JsonMapper.builder().build()` 在每次 `toJson` 调用时新建实例是性能隐患。这些是"修不动 Service"的根本原因之一。

**独立测试**：删除并替换重复实现后，运行全量测试（`./mvnw test`），所有现有用例继续通过且新增工具类有 ≥95% 行覆盖率。

**验收场景**：

1. **假设** 当前代码中存在 3 处独立 `concatPath`/`getDirPath` 实现，**当** 完成 P1 重构，**则** `common/util/PathUtils` 是唯一实现，其他 3 处全部改为调用 `PathUtils.join` / `PathUtils.parentDir`。
2. **假设** 当前 traceId 设置/清理样板在 5 处重复，**当** 完成 P1 重构，**则** 仅保留 `TraceContext.runWith(module, op, Runnable)` 一处实现，调用方至多 3 行代码。
3. **假设** `TranscodeService.toJson` 每次调用都新建 JsonMapper，**当** 完成 P1 重构，**则** 复用注入的 `JsonMapper` 单例，并通过日志或度量验证调用频次下降为常量级。

---

### 用户故事 2 — 拆分 `executeSyncTaskInternal` 提升可读性与可测性（优先级：P2）

作为后端维护者，当我需要修改同步流程中的某一环节（如冲突策略、FULL 删除多余文件、同引擎/小文件/大文件分发）时，我希望该环节是一个独立的、命名清晰、参数受控的私有方法，而非埋藏在 224 行的巨型方法之中。

**为什么是此优先级**：`executeSyncTaskInternal` 是当前最大的"理解负担源"。拆分后每个子步骤可以被独立单测覆盖，直接缓解 memory 中记录的"Service 层测试覆盖率缺口"。

**独立测试**：通过新增针对 `scanAndDiff`、`deleteExtraTargets`、`syncOneFile`、`finalizeExecution` 的单测验证；外部行为通过现有的 `SyncServiceTest` 全量通过来保证。

**验收场景**：

1. **假设** 当前 `executeSyncTaskInternal` 为 224 行单方法，**当** 完成 P2 重构，**则** 该方法不超过 50 行，且拆出至少 6 个职责单一的私有方法。
2. **假设** 现有 `SyncServiceTest`（450 行、24 用例）全部通过，**当** 完成 P2 重构，**则** 测试用例数量与断言数量不减少，且全部继续通过。
3. **假设** 同一 SyncMode + ConflictStrategy 组合，**当** 重构前后分别执行同一任务，**则** 产生的 `TaskExecution` 状态（successFiles、failedFiles、status、failureDetails）一致。

---

### 用户故事 3 — 重构转码与 Webhook 中的剩余重复（优先级：P3）

作为后端维护者，当我维护 `TranscodeService` 与 `WebhookService` 时，我希望并行 futures 收集、临时任务构造、失败状态映射等被重复书写的逻辑只存在一份。

**为什么是此优先级**：这些重复虽不构成 bug，但持续累积阅读成本；放在 P3 是因为风险更高（涉及并发与状态机），需要在 P1、P2 完成、测试基线稳固后再动。

**独立测试**：转码与 Webhook 模块的现有测试全量通过；新增针对 `ParallelTaskCollector` 与失败状态映射表的单测。

**验收场景**：

1. **假设** `TranscodeService.processCandidates` 与 `executeTaskInternal` 各有约 30 行 futures 收集代码，**当** 完成 P3 重构，**则** 两处调用统一的 `ParallelTaskCollector.collect(...)`，且不重复维护超时常量。
2. **假设** `TranscodeFileProcessor.doProcess` catch 块中 if/else 链按状态映射失败状态，**当** 完成 P3 重构，**则** 改为 `Map<TranscodeStatus, TranscodeStatus>` 查表，且保留 `validateTransition` 校验。
3. **假设** `WebhookService.executeRuleAction` 的 `SYNC_ONLY` 与 `BOTH` 分支几乎完全相同，**当** 完成 P3 重构，**则** 提取 `buildEphemeralSyncTask(rule, event, boolean transcodeEnabled)` 辅助方法，分支主体不超过 3 行。

---

### 边界情况

- 若 `PathUtils.join` 收到 `null` / 空串 / `/` / 多重斜杠输入，必须给出确定行为（不抛 NPE，与原 `SyncService.concatPath` 等价）。
- 若 `TraceContext.runWith` 抛出受检异常，需要保留 `clear()` 调用以避免 MDC 泄漏到下一个请求线程。
- 若拆分后的 `syncOneFile` 抛出异常，主循环必须按现行语义把失败计入 `failedFiles` 并继续，不得中断整个任务。
- 若 `JsonMapper` 复用单例后并发场景出错，必须验证 `tools.jackson.databind.json.JsonMapper` 是线程安全的（与 Jackson 文档一致）。
- 重构期间若主分支接收到新的 Service 层变更（如 hotfix），必须先 rebase 并重新执行回归基线。

## 需求 *（强制）*

### 功能需求

- **FR-001**：必须新增 `top.lldwb.alistmediasync.common.util.PathUtils`，提供 `join` / `parentDir` / `baseName` / `swapExtension` / `trimLeadingSlash` / `normalize` 至少六个静态方法。
- **FR-002**：必须在 `TraceContext` 中新增 `runWith(String module, String operation, Runnable task)` 方法，封装 traceId 获取/生成/设置/清理流程。
- **FR-003**：必须删除 `SyncService` / `TranscodeService` / `TranscodeFileProcessor` 中的私有 `concatPath` / `getDirPath` / `getOutputName` / `concatDirAndName` / `trimLeadingSlash`，全部改用 `PathUtils`。
- **FR-004**：必须删除 `TranscodeService.toJson` 与 `SyncService.toJson` 中各自实现的 `JsonMapper.builder().build()`，统一注入 `JsonMapper` Bean 或抽取到 `JsonUtil`。
- **FR-005**：必须将 `SyncService.executeSyncTaskInternal` 拆分为不超过 50 行的入口方法，并至少抽出 `prepareExecution`、`scanAndDiff`、`deleteExtraTargets`、`syncOneFile`、`finalizeExecution`、`triggerPostSyncTranscode` 6 个私有方法。
- **FR-006**：必须在 `SyncService` 内引入 `SyncContext` 内部 record（package-private），封装单次同步任务执行的共享状态（task、execution、source/target engine 与 strategy、sameEngine、failedFiles），避免方法间长参数列表。
- **FR-007**：必须将 `TranscodeFileProcessor.doProcess` 拆分为入口方法（≤40 行）+ `runDownloadStep` / `runTranscodeStep` / `runUploadStep` / `handleFailure` 四个步骤方法。
- **FR-008**：必须将 `TranscodeFileProcessor` catch 块中 `DOWNLOADING/TRANSCODING/UPLOADING → DOWNLOAD_FAILED/TRANSCODE_FAILED/UPLOAD_FAILED` 的 if/else 链替换为不可变 `Map<TranscodeStatus, TranscodeStatus>`，并保留对 `validateTransition` 的调用。
- **FR-009**：必须新增 `common/util/ParallelTaskCollector`，封装"等待 futures + 超时 + 异常归集 + 设置执行记录的 successFiles/failedFiles/status"逻辑；`TranscodeService` 两处重复代码统一改用此工具。
- **FR-010**：必须将 `WebhookService.executeRuleAction` 中 `SYNC_ONLY` 与 `BOTH` 分支重复的 `SyncTask` 构造代码抽取为 `buildEphemeralSyncTask` 私有方法。
- **FR-011**：必须修复 `SyncService.java:96` 方法签名与注释同行的格式问题、`WebhookService.java:112-113` 重复 javadoc 起始 `/**` 的格式问题。
- **FR-012**：所有重构变更必须是"行为对等"的（behavior-preserving），不得改变任何对外 API（包括 HTTP 响应、数据库写入、WebSocket 推送字段、日志关键字段）。
- **FR-013**：每个被改动的 Service 必须同步新增或更新"行为对等回归测试"，覆盖 SyncMode、ConflictStrategy、TranscodeStatus 转换、Webhook Action 等关键分支。
- **FR-014**：每个新增工具类（`PathUtils`、`ParallelTaskCollector`、`TraceContext.runWith`、可选 `JsonUtil`）必须有专属单元测试，行覆盖率 ≥95%。
- **FR-015**：重构必须分 3 个独立 PR 提交，每个 PR 内 `./mvnw test` 必须全部通过；不允许引入新的第三方依赖（章程原则 VI）。

### 关键实体（重构产物）

- **PathUtils**：静态工具类，仅依赖 JDK，无状态。
- **ParallelTaskCollector**：静态工具类（或函数式入口 `collect(...)`），负责收集 `List<CompletableFuture<T>>`、应用超时、聚合成功/失败计数、把结果写入传入的 `TaskExecution`。
- **SyncContext**：`SyncService` 内部 package-private record，封装一次同步执行的共享状态。
- **失败状态映射表**：`TranscodeFileProcessor` 私有静态 `Map<TranscodeStatus, TranscodeStatus>`，将进行中状态映射为对应的失败状态。

## 成功标准 *（强制）*

### 可测量结果

- **SC-001**：`SyncService` 总行数从 513 行降至 ≤420 行（删除工具方法 + 拆分主流程后）。
- **SC-002**：`TranscodeService` 总行数从 656 行降至 ≤560 行。
- **SC-003**：`TranscodeFileProcessor` 总行数从 462 行降至 ≤400 行；`doProcess` 从 128 行降至 ≤40 行。
- **SC-004**：`SyncService.executeSyncTaskInternal` 从 224 行降至 ≤50 行。
- **SC-005**：路径相关方法在 main 源代码中的重复实现数量从 ≥7 处降至 1 处（仅保留 `PathUtils`）。
- **SC-006**：`./mvnw test` 通过的用例数 ≥ 重构前基线（不允许减少），全部通过。
- **SC-007**：`PathUtils`、`ParallelTaskCollector`、`TraceContext.runWith` 新增工具类的单测行覆盖率 ≥95%（由 JaCoCo 报告校验）。
- **SC-008**：重构 PR 提交后，章程 10 项检查清单全部通过（特别是原则 I 分层架构、原则 V 测试同步、原则 IV 中文优先、原则 VI YAGNI、原则 VII 日志规范）。
- **SC-009**：重构期间所有 `INFO` / `WARN` / `ERROR` 日志的关键字段（任务名、路径、状态、错误信息）保持原有内容；不删减原日志，且新增日志符合四级分级。
- **SC-010**：重构完成后 `JsonMapper` 实例化次数从"每次 `toJson` 调用一次"降为"应用启动注入一次"。
