---
description: "Service 层重复代码与超长方法重构 — 任务清单"
---

# 任务：Service 层重复代码与超长方法重构

**输入**：来自 `/specs/010-service-layer-refactor/` 的设计文档

**前提条件**：plan.md、spec.md、research.md、data-model.md、contracts/

**测试**：本功能 spec 明确要求 FR-013、FR-014、SC-007 — 每一项重构必须有同步测试，因此本任务清单**强制包含**测试任务。

**组织方式**：按 PR 分组，每个 PR 内再按用户故事（US1 / US2 / US3）排列。`[P]` 标记可并行的任务（不同文件、无依赖）。

## 格式：`[ID] [P?] [Story] 描述`

---

## 阶段 0：基线建立（PR 1 之前的一次性工作）

- [ ] **T000** 在干净 main 分支执行 `./mvnw test`，记录通过用例数 / 失败 / 跳过到 `specs/010-service-layer-refactor/quickstart-results.md`，并记录 git SHA。

---

## PR 1：工具类抽取与卫生清理（US1 + 部分卫生）

**目的**：实现 FR-001~FR-004、FR-011、SC-005（部分）、SC-010。低风险、可独立合入。

### 阶段 1.1：创建工具类

- [ ] **T101** [P] [US1] 新建 `src/main/java/top/lldwb/alistmediasync/common/util/PathUtils.java`，实现 6 个静态方法（参考 contracts/PathUtils.md）。
- [ ] **T102** [P] [US1] 在 `TraceContext.java` 新增 `runWith(String module, String operation, Runnable task)` 方法（参考 contracts/TraceContextRunWith.md）。

### 阶段 1.2：工具类单元测试（先写测试再迁移）

- [ ] **T103** [P] [US1] 新建 `src/test/java/.../common/util/PathUtilsTest.java`，按契约表格穷举 30+ 用例，行覆盖率 ≥95%。
- [ ] **T104** [P] [US1] 在 `TraceContextTest.java`（已有 132 行）追加 `runWith_应在无上游traceId时生成并清理` / `runWith_应继承上游traceId不清理` / `runWith_异常应透传且执行finally` 3 个用例。

### 阶段 1.3：调用方迁移（路径工具）

- [ ] **T110** [US1] 修改 `SyncService.java`：删除私有 `concatPath` / `getDirPath` / `relativePath` / `normalizePath` / `trimLeadingSlash` / `generateUniqueName` 中可复用部分，全部改用 `PathUtils`。
- [ ] **T111** [US1] 修改 `TranscodeService.java`：删除私有 `concatPath` / `getDirPath` / `getOutputName(String)` / `getOutputName(String, TargetFormat)`，统一改用 `PathUtils.join` / `PathUtils.parentDir` / `PathUtils.swapExtension`。
- [ ] **T112** [US1] 修改 `TranscodeFileProcessor.java`：删除私有 `getOutputName` / `getDirPath` / `concatDirAndName`，改用 `PathUtils`。
- [ ] **T113** [US1] 修改 `AListStorageStrategy.buildSrcDstBody` 与 `deleteFile`：路径拆分改用 `PathUtils.parentDir` + `PathUtils.baseName`。

### 阶段 1.4：调用方迁移（TraceContext.runWith）

- [ ] **T120** [US1] 重写 `SyncService.executeSyncTask` 使用 `TraceContext.runWith`（不动 `executeSyncTaskInternal` 本身）。
- [ ] **T121** [US1] 重写 `TranscodeService.executeAsync` 使用 `TraceContext.runWith`。
- [ ] **T122** [US1] 重写 `TranscodeService.executePostSyncTranscode`（注意保留 `owns` 兼容语义，对照场景 1）。
- [ ] **T123** [US1] 重写 `WebhookService.processWebhookEvent` 使用 `TraceContext.runWith`。
- [ ] **T124** [US1] 重写 `ScheduleService.registerSchedule` 内 lambda 使用 `TraceContext.runWith`。

### 阶段 1.5：共享 JsonMapper

- [ ] **T130** [US1] 修改 `TranscodeService`：构造器注入 `JsonMapper`，删除 `toJson` 中 `JsonMapper.builder().build()`；改为复用注入的 Bean。

### 阶段 1.6：卫生修复

- [ ] **T140** [P] [US1] 修复 `SyncService.java:96`：方法签名与注释拆为两行。
- [ ] **T141** [P] [US1] 修复 `WebhookService.java:112-113`：删除重复 `/**`。
- [ ] **T142** [P] [US1] 修复 `TranscodeFileProcessor.downloadStep` L299 的引用丢失（要么不在 step 内 save，要么返回 (Path, TranscodeTask)）。

### 阶段 1.7：文档与门禁

- [ ] **T150** [US1] 更新 `src/main/java/top/lldwb/alistmediasync/common/AGENTS.md`，索引新增的 `PathUtils`。
- [ ] **T151** [US1] 运行 `./mvnw test`，确认用例数 ≥ 基线，记录到 `quickstart-results.md` "PR1 段落"。
- [ ] **T152** [US1] 按 quickstart.md 步骤 2.2 完成本地冒烟。
- [ ] **T153** [US1] 提交 PR 1，标题：`refactor(common): 抽取 PathUtils 与 TraceContext.runWith 消除路径与 traceId 样板重复`。

**PR 1 Definition of Done**：

- T100~T153 全部完成；
- `./mvnw test` 通过用例数 ≥ 基线；
- 章程 10 项检查表勾选；
- 无 README 影响（标注）。

---

## PR 2：拆分 `SyncService.executeSyncTaskInternal`（US2）

**目的**：实现 FR-005、FR-006、SC-001、SC-004。中等风险，依赖 PR 1 合入。

### 阶段 2.1：测试护栏（先补齐再拆分）

- [ ] **T201** [US2] 在 `SyncServiceTest.java` 补齐 SyncMode × ConflictStrategy × sameEngine 矩阵的缺失格用例（至少补充 6 个用例）。
- [ ] **T202** [US2] 新增 `SyncServiceBehaviorParityTest.java`：对同一组输入分别用"重构前快照"和"重构后实现"运行（如不便保留旧实现，至少冻结期望产出的 `TaskExecution` 字段断言）。

### 阶段 2.2：引入数据载体

- [ ] **T210** [US2] 在 `SyncService` 内新增 `SyncContext` package-private record（参考 data-model.md E-4）。
- [ ] **T211** [US2] 在 `SyncService` 内新增 `DiffResult` package-private record（参考 data-model.md E-6）。

### 阶段 2.3：方法拆分（按 data-model.md E-6 清单）

- [ ] **T220** [US2] 抽出 `prepareExecution(SyncTask) : SyncContext`。
- [ ] **T221** [US2] 抽出 `scanAndDiff(SyncContext) : DiffResult`。
- [ ] **T222** [US2] 抽出 `deleteExtraTargets(SyncContext, sourceFiles, destFiles)`。
- [ ] **T223** [US2] 抽出 `syncOneFile(SyncContext, FileInfo, Set<String>, int totalToSync) : boolean`，内部分发 sameEngine / 小文件 / 大文件。
- [ ] **T224** [US2] 抽出 `finalizeExecution(SyncContext, int completed, int total)`，含状态判定与 WebSocket 最终推送。
- [ ] **T225** [US2] 抽出 `triggerPostSyncTranscode(SyncContext)`。
- [ ] **T226** [US2] 重写 `executeSyncTaskInternal` 主方法为 ≤50 行的编排逻辑。

### 阶段 2.4：单测补齐

- [ ] **T230** [P] [US2] 为 `scanAndDiff` 新增单测（NEW_ONLY / FULL / MOVE 三模式）。
- [ ] **T231** [P] [US2] 为 `syncOneFile` 新增单测（同引擎 / 小文件 / 大文件 / OVERWRITE / SKIP / RENAME）。
- [ ] **T232** [P] [US2] 为 `finalizeExecution` 新增单测（全成功 / 全失败 / 部分成功 三种状态判定）。

### 阶段 2.5：门禁

- [ ] **T240** [US2] 运行 `./mvnw test`，确认用例数 ≥ PR1 后基线；记录到 `quickstart-results.md` "PR2 段落"。
- [ ] **T241** [US2] 验证 `wc -l SyncService.java` ≤ 420；`grep` 验证 `executeSyncTaskInternal` ≤ 50 行。
- [ ] **T242** [US2] 按 quickstart.md 步骤 2.5（C1/C2/C3 三组合）完成行为对等手工验证。
- [ ] **T243** [US2] 提交 PR 2，标题：`refactor(sync): 拆分 executeSyncTaskInternal 提升可读性与可测性`。

---

## PR 3：转码与 Webhook 收尾（US3）

**目的**：实现 FR-007、FR-008、FR-009、FR-010、SC-002、SC-003。中等风险，依赖 PR 1 合入。

### 阶段 3.1：测试护栏

- [ ] **T301** [US3] 在 `TranscodeFileProcessorTest.java` 补齐失败状态映射场景（DOWNLOADING→DOWNLOAD_FAILED 等 3 组）。
- [ ] **T302** [US3] 在 `TranscodeServiceTest.java` 补齐 `processCandidates` 与 `executeTaskInternal` 在全成功 / 部分失败 / 全失败 三场景下的断言。
- [ ] **T303** [US3] 在 `WebhookServiceTest.java` 补齐 SYNC_ONLY / TRANSCODE_ONLY / BOTH 三 action 的回归。

### 阶段 3.2：新增工具类

- [ ] **T310** [P] [US3] 新建 `src/main/java/.../common/util/ParallelTaskCollector.java`（参考 contracts/ParallelTaskCollector.md）。
- [ ] **T311** [P] [US3] 新建 `src/test/java/.../common/util/ParallelTaskCollectorTest.java`，行覆盖率 ≥95%。

### 阶段 3.3：TranscodeFileProcessor 拆分

- [ ] **T320** [US3] 在 `TranscodeFileProcessor` 新增 `FAILURE_STATUS_MAP`（参考 data-model.md E-5）。
- [ ] **T321** [US3] 抽出 `createTaskRecord(...)` / `runDownloadStep(...)` / `runTranscodeStep(...)` / `runUploadStep(...)` / `markCompleted(...)` / `handleFailure(...)`。
- [ ] **T322** [US3] 重写 `doProcess` 为 ≤40 行的编排逻辑。
- [ ] **T323** [US3] 在 `handleFailure` 中用 `FAILURE_STATUS_MAP` 替换 if/else 链，保留 `validateTransition` 调用。

### 阶段 3.4：TranscodeService 迁移并行收集

- [ ] **T330** [US3] 将 `TranscodeService.processCandidates` 改为调用 `ParallelTaskCollector.collect`。
- [ ] **T331** [US3] 将 `TranscodeService.executeTaskInternal` 改为调用 `ParallelTaskCollector.collect`。

### 阶段 3.5：WebhookService 提取

- [ ] **T340** [US3] 在 `WebhookService` 新增 `buildEphemeralSyncTask(WebhookRule, WebhookEvent, boolean transcodeEnabled) : SyncTask`。
- [ ] **T341** [US3] 重写 `executeRuleAction` 中 SYNC_ONLY 与 BOTH 分支为各 ≤3 行调用 + 1 行差异（transcodeEnabled 入参）。

### 阶段 3.6：门禁

- [ ] **T350** [US3] 运行 `./mvnw test`，确认用例数 ≥ PR2 后基线；记录到 `quickstart-results.md` "PR3 段落"。
- [ ] **T351** [US3] 验证 `wc -l` 命令：`TranscodeService.java` ≤ 560，`TranscodeFileProcessor.java` ≤ 400，`doProcess` ≤ 40 行。
- [ ] **T352** [US3] 按 quickstart.md 完整冒烟 + 章程合规复检。
- [ ] **T353** [US3] 提交 PR 3，标题：`refactor(transcode,webhook): 拆分 doProcess 并抽取 ParallelTaskCollector`。

---

## 依赖关系图

```
T000 (基线)
  ├─ T101 ─┬─ T103 ─┐
  ├─ T102 ─┴─ T104 ─┤
  └─ ...           │
                   ├─ T110~T113（路径替换，依赖 PathUtils）
                   ├─ T120~T124（runWith 替换，依赖 T102）
                   ├─ T130（共享 JsonMapper）
                   ├─ T140~T142（卫生，独立）
                   ├─ T150~T153（PR1 门禁）
                   │
PR2 依赖 PR1 合入 ──┤
                   ├─ T201~T202（测试护栏）
                   ├─ T210~T211（数据载体）
                   ├─ T220~T226（拆分，串行）
                   ├─ T230~T232（拆后单测）
                   ├─ T240~T243（PR2 门禁）
                   │
PR3 依赖 PR1 合入 ──┘
                   ├─ T301~T303（测试护栏）
                   ├─ T310~T311（工具类，可并行 PR2）
                   ├─ T320~T323（FileProcessor 拆分）
                   ├─ T330~T331（迁移并行收集）
                   ├─ T340~T341（Webhook）
                   └─ T350~T353（PR3 门禁）
```

## 并行运行建议

- PR 1 内：T101/T102/T103/T104 可同时进行（不同文件，无内容依赖）；T140/T141/T142 可同时进行；
- PR 2 内：T230/T231/T232 可同时进行；
- PR 3 内：T310/T311 与 PR 2 可同时进行；T301/T302/T303 之间可同时进行。

## 任务总数

- PR 1：21 项
- PR 2：14 项
- PR 3：18 项
- 基线：1 项
- **总计：54 项**
