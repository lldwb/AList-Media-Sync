# execution/ — 共享任务执行记录模块

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和后端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

提供跨模块共享的「任务执行记录」实体、Repository 与视图 VO。同步、转码、Webhook 三条链路的每次执行都落一条 `TaskExecution`，是仪表盘统计、执行历史查询与过期清理的统一数据来源。

## 作用

- **TaskExecution**：执行记录实体（表 `task_execution`，含 `@Version` 乐观锁）。字段：`taskType`（SYNC / TRANSCODE / WEBHOOK）、`status`（RUNNING / SUCCESS / FAILED / PARTIAL_SUCCESS / INTERRUPTED）、起止时间、文件统计（总数 / 成功 / 失败）、`failureDetails`（JSON 文本）
- **关联字段为 ID 而非实体引用**：`syncTaskId` / `transcodeTaskId` / `webhookEventId` 均为 `Long` + `@Column`（列名 `sync_task_id` / `transcode_task_id` / `webhook_event_id` 保持不变），切断与 sync / transcode / webhook 实体的模块环
- **TaskExecutionRepository**：派生查询（按状态+类型、按同步任务+状态、按同步任务倒序、按创建时间区间）；批量方法 `markAllRunningAsInterrupted()`（启动时把上次未完成的 RUNNING 记录标记为 INTERRUPTED）、`deleteByCreatedAtBefore()`（过期清理）、`nullifyTranscodeTaskRefs()`（删除转码任务前解除外键引用，保留历史记录）
- **TaskExecutionVO**：执行记录视图 record，用于 API 响应，避免直接暴露 JPA 实体

## 模块关联

- 被 **sync/** 使用：`ScheduleService` 启动时调用 `markAllRunningAsInterrupted()`；`SyncService` 创建并更新同步执行记录；`SyncTaskManageService` 查询执行历史、校验运行中任务；`SyncTaskController` 返回 `TaskExecutionVO`
- 被 **transcode/** 使用：`TranscodeService` / `TranscodeFileProcessor` 记录转码执行，删除任务前调用 `nullifyTranscodeTaskRefs()`
- 被 **webhook/** 使用：`WebhookService` 为每次规则动作创建执行记录
- 被 **ops/** 使用：`DashboardService` 统计活跃同步任务数与 24 小时文件处理量；`CleanupService` 删除过期记录；`DiagnosticService` 读取最近一次失败记录
- **不依赖任何业务模块**（允许依赖 `common`，当前未使用）——本模块只承载实体 / Repository / VO，无 Service 层
- 原属 `sync/` 模块（`sync/entity/TaskExecution`、`sync/repository/TaskExecutionRepository`、`sync/dto/sync/TaskExecutionVO`），因被 sync / transcode / webhook / ops 多方共用而迁出为顶层共享模块
