# 审查检查清单：Service 层重构

**关联**：[../spec.md](../spec.md) | [../plan.md](../plan.md) | [../tasks.md](../tasks.md)

**用途**：每个 PR 合入前必须勾选本清单全部项；任一未通过则不允许合入。

---

## 通用合规

- [ ] CR-001 提交信息使用简体中文（章程原则 IV）
- [ ] CR-002 注释、日志使用简体中文（章程原则 IV）
- [ ] CR-003 未引入任何新的第三方依赖（章程原则 VI，FR-015）
- [ ] CR-004 改动 Java 类对应的 `*Test.java` 已同步修改或新增（章程原则 V，FR-013）
- [ ] CR-005 章程 10 项检查表已逐项确认（参见 plan.md "章程检查"）
- [ ] CR-006 `./mvnw test` 通过用例数 ≥ 基线（无回归）
- [ ] CR-007 本 PR 不包含与重构无关的功能性改动（保持纯重构）
- [ ] CR-008 PR 描述中包含"零行为变更"声明与影响范围说明

---

## 分层架构（章程原则 I）

- [ ] LA-001 新增工具类放在 `common/util/`，不放业务模块
- [ ] LA-002 工具类不依赖 Repository、Service、Controller（无反向依赖）
- [ ] LA-003 `SyncContext` / `DiffResult` 等 record 仅在 `SyncService` 内部 package-private 可见

---

## 日志规范（章程原则 VII）

- [ ] LG-001 重构前后日志中的 traceId / module / operation 字段不丢失
- [ ] LG-002 重构前后 INFO 级别日志的关键字段（任务名、路径、状态）保持不变
- [ ] LG-003 任务入口仍通过 `TraceContext.runWith` 注入 MDC（或现有等价机制）
- [ ] LG-004 ERROR 级日志仍同时写入 `app.log` 与 `error.log`
- [ ] LG-005 `/api/**` 响应仍携带 `X-Trace-Id` header（不应受重构影响，但需冒烟确认）
- [ ] LG-006 日志中不出现密码 / Token / 密钥等敏感原始值

---

## PR 1 专属（工具类抽取）

- [ ] P1-001 `PathUtils` 6 个方法全部实现，行为符合 contracts/PathUtils.md 表格
- [ ] P1-002 `PathUtilsTest` 行覆盖率 ≥95%（FR-014、SC-007）
- [ ] P1-003 `SyncService` / `TranscodeService` / `TranscodeFileProcessor` 内的私有路径工具方法已全部删除
- [ ] P1-004 `AListStorageStrategy.buildSrcDstBody` 与 `deleteFile` 内的路径拆分已迁移到 `PathUtils`
- [ ] P1-005 `TraceContext.runWith` 实现符合 contracts/TraceContextRunWith.md 三个场景
- [ ] P1-006 `TraceContextTest` 新增 runWith 用例（≥3 个）
- [ ] P1-007 5 处 traceId 样板代码全部替换为 `runWith` 调用
- [ ] P1-008 `TranscodeService` 删除 `JsonMapper.builder().build()`，改为构造器注入 `JsonMapper`
- [ ] P1-009 `SyncService.java:96` 格式问题已修复
- [ ] P1-010 `WebhookService.java:112-113` 重复 javadoc 已修复
- [ ] P1-011 `common/AGENTS.md` 已同步索引新工具类
- [ ] P1-012 `TranscodeFileProcessor.downloadStep` 的引用丢失问题已修复

---

## PR 2 专属（SyncService 拆分）

- [ ] P2-001 `SyncService.java` 总行数 ≤ 420（SC-001）
- [ ] P2-002 `executeSyncTaskInternal` 行数 ≤ 50（SC-004）
- [ ] P2-003 至少拆出 6 个私有方法（FR-005 全部）
- [ ] P2-004 `SyncContext` record 字段与 data-model.md E-4 一致
- [ ] P2-005 `DiffResult` record 字段与 data-model.md E-6 一致
- [ ] P2-006 `SyncServiceTest` SyncMode × ConflictStrategy 矩阵补齐
- [ ] P2-007 新增 `scanAndDiff` / `syncOneFile` / `finalizeExecution` 单测
- [ ] P2-008 重构前后同任务产生的 `TaskExecution` 字段一致（quickstart C1/C2/C3）
- [ ] P2-009 WebSocket 推送的 message type 与 payload 字段集合不变
- [ ] P2-010 同步后置转码（`triggerPostSyncTranscode`）条件判断与原逻辑一致

---

## PR 3 专属（转码与 Webhook）

- [ ] P3-001 `TranscodeService.java` 总行数 ≤ 560（SC-002）
- [ ] P3-002 `TranscodeFileProcessor.java` 总行数 ≤ 400（SC-003）
- [ ] P3-003 `doProcess` 行数 ≤ 40（SC-003）
- [ ] P3-004 `ParallelTaskCollector` 实现符合 contracts/ParallelTaskCollector.md
- [ ] P3-005 `ParallelTaskCollectorTest` 行覆盖率 ≥95%
- [ ] P3-006 `TranscodeService.processCandidates` 与 `executeTaskInternal` 均改用 `ParallelTaskCollector`
- [ ] P3-007 `FAILURE_STATUS_MAP` 用 `Map.of` 不可变构造，3 项映射齐全
- [ ] P3-008 `handleFailure` 中保留 `validateTransition` 调用（不绕过状态机）
- [ ] P3-009 `WebhookService.buildEphemeralSyncTask` 已抽出，SYNC_ONLY 与 BOTH 分支各 ≤3 行
- [ ] P3-010 重构前后转码任务的 8 状态流转与状态机一致

---

## 性能与回归

- [ ] PF-001 `JsonMapper` 单例复用已确认（启动后不再每次 toJson 都新建）
- [ ] PF-002 大文件同步（>100MB）路径未被破坏（手工或自动化抽测一次）
- [ ] PF-003 并发转码（≥2 文件）路径未被破坏，Semaphore 控制行为不变
- [ ] PF-004 `./mvnw test` 总耗时未明显增加（>20% 视为异常）

---

## 文档与发布

- [ ] DP-001 PR 描述包含本 checklist 链接与勾选截图（或 markdown 直接列出）
- [ ] DP-002 如有新增公共工具类，对应模块 `AGENTS.md` 已索引
- [ ] DP-003 spec.md 状态字段在 PR 合入后由 `/speckit-implement` 流程更新为"已完成"
- [ ] DP-004 README.md 无影响（本次重构是内部重构，章程原则 IX 已说明）
