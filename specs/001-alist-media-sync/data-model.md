# 数据模型：AList 媒体同步与转码工具

**功能**：AList 媒体同步与转码 | **日期**：2026-06-19

## 概述

核心实体 6 个，分别对应存储引擎、同步任务、任务执行记录、Webhook 处理规则、Webhook 事件记录、转码任务。实体间的关系由 plan.md 中的概要图定义。

---

## 1. StorageEngine（存储引擎）

代表一个 AList/OpenList 实例。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | `Long` | PK, AUTO_INCREMENT | 主键 |
| `name` | `String(100)` | NOT NULL | 引擎名称 |
| `baseUrl` | `String(500)` | NOT NULL | AList API 基础 URL（含协议） |
| `username` | `String(100)` | NOT NULL | AList 登录用户名 |
| `encryptedToken` | `String(500)` | NOT NULL, `@Convert` | AES-256-GCM 加密后的 AList Token |
| `status` | `EngineStatus` | NOT NULL, DEFAULT `UNKNOWN` | 连接状态枚举：UNKNOWN / CONNECTED / DISCONNECTED |
| `createdAt` | `LocalDateTime` | NOT NULL | 创建时间 |
| `updatedAt` | `LocalDateTime` | NOT NULL | 最后更新时间 |
| `version` | `Long` | NOT NULL, `@Version` | 乐观锁版本号 |

**索引**：`UNIQUE(name)` — 名称唯一。

---

## 2. SyncTask（同步任务）

代表一个同步作业，连接源引擎和目标引擎。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | `Long` | PK, AUTO_INCREMENT | 主键 |
| `name` | `String(200)` | NOT NULL | 任务名称 |
| `sourceEngine` | `StorageEngine` | `@ManyToOne`, NOT NULL | 源存储引擎 |
| `targetEngine` | `StorageEngine` | `@ManyToOne`, NOT NULL | 目标存储引擎 |
| `sourcePath` | `String(1000)` | NOT NULL | 源路径（AList 内路径） |
| `targetPath` | `String(1000)` | NOT NULL | 目标路径（AList 内路径） |
| `syncMode` | `SyncMode` | NOT NULL | 同步模式枚举：NEW_ONLY / FULL / MOVE |
| `transcodeEnabled` | `Boolean` | NOT NULL, DEFAULT false | 是否启用同步后转码 |
| `conflictStrategy` | `ConflictStrategy` | NOT NULL, DEFAULT SKIP | 冲突处理策略：OVERWRITE / SKIP / RENAME |
| `excludePatterns` | `String(2000)` | nullable | 排除规则（换行分隔的文件名/目录模式） |
| `scheduleType` | `ScheduleType` | NOT NULL | 调度类型：CRON / INTERVAL / MANUAL |
| `cronExpression` | `String(100)` | nullable | Cron 表达式（scheduleType=CRON 时必填） |
| `intervalSeconds` | `Long` | nullable | 间隔秒数（scheduleType=INTERVAL 时必填） |
| `enabled` | `Boolean` | NOT NULL, DEFAULT true | 启用状态 |
| `lastExecutedAt` | `LocalDateTime` | nullable | 上次执行时间 |
| `version` | `Long` | NOT NULL, `@Version` | 乐观锁版本号 |

**索引**：`INDEX(enabled)` — 快速查询已启用任务。

---

## 3. TaskExecution（任务执行记录）

代表一次任务执行的完整记录。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | `Long` | PK, AUTO_INCREMENT | 主键 |
| `syncTask` | `SyncTask` | `@ManyToOne`, nullable | 关联的同步任务 |
| `transcodeTask` | `TranscodeTask` | `@ManyToOne`, nullable | 关联的转码任务 |
| `webhookEvent` | `WebhookEvent` | `@ManyToOne`, nullable | 关联的 Webhook 事件 |
| `taskType` | `TaskType` | NOT NULL | 任务类型枚举：SYNC / TRANSCODE / WEBHOOK |
| `startTime` | `LocalDateTime` | NOT NULL | 开始时间 |
| `endTime` | `LocalDateTime` | nullable | 结束时间 |
| `status` | `ExecutionStatus` | NOT NULL | 状态枚举：RUNNING / SUCCESS / FAILED / PARTIAL_SUCCESS / INTERRUPTED |
| `totalFiles` | `Integer` | NOT NULL, DEFAULT 0 | 总文件数 |
| `successFiles` | `Integer` | NOT NULL, DEFAULT 0 | 成功文件数 |
| `failedFiles` | `Integer` | NOT NULL, DEFAULT 0 | 失败文件数 |
| `failureDetails` | `String` | `@Lob`, nullable | 失败详情（JSON 数组格式） |
| `createdAt` | `LocalDateTime` | NOT NULL | 记录创建时间 |
| `version` | `Long` | NOT NULL, `@Version` | 乐观锁版本号 |

**索引**：`INDEX(status, taskType)`、`INDEX(createdAt)`、`INDEX(syncTask)`。

---

## 4. WebhookRule（Webhook 处理规则）

代表一个录播姬 Webhook 的处理配置。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | `Long` | PK, AUTO_INCREMENT | 主键 |
| `name` | `String(200)` | NOT NULL | 规则名称 |
| `triggerEventType` | `WebhookEventType` | NOT NULL | 触发事件类型：FILE_CLOSED / SESSION_ENDED |
| `roomIdFilter` | `Long` | nullable | 房间号过滤条件（null 表示所有房间） |
| `action` | `WebhookAction` | NOT NULL | 后续动作：SYNC_ONLY / TRANSCODE_ONLY / BOTH |
| `targetEngine` | `StorageEngine` | `@ManyToOne`, NOT NULL | 目标存储引擎 |
| `targetPath` | `String(1000)` | NOT NULL | 目标路径 |
| `enabled` | `Boolean` | NOT NULL, DEFAULT true | 启用状态 |
| `version` | `Long` | NOT NULL, `@Version` | 乐观锁版本号 |

**索引**：`INDEX(enabled)`、`INDEX(triggerEventType, enabled)`。

---

## 5. WebhookEvent（Webhook 事件记录）

代表一条接收到的 Webhook 事件。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | `Long` | PK, AUTO_INCREMENT | 主键 |
| `eventId` | `String(255)` | UNIQUE, NOT NULL | 事件唯一标识（去重键） |
| `eventType` | `String(50)` | NOT NULL | 事件类型（SessionStarted/FileOpening/FileClosed/SessionEnded/StreamStarted/StreamEnded） |
| `eventTimestamp` | `String(50)` | NOT NULL | 事件时间戳（原始字符串，保留 Webhook 来源格式） |
| `sessionId` | `String(255)` | NOT NULL | 录制会话 ID |
| `roomId` | `Long` | nullable | 直播间 ID |
| `relativePath` | `String(1000)` | nullable | 录制文件相对路径 |
| `fileName` | `String(500)` | nullable | 录制文件名 |
| `fileSize` | `Long` | nullable | 文件大小（字节） |
| `duration` | `Double` | nullable | 录制时长（秒，FileClosed 事件可能提供） |
| `rawData` | `String` | `@Lob`, NOT NULL | 原始 Webhook JSON 数据（TEXT 类型） |
| `status` | `EventProcessStatus` | NOT NULL, DEFAULT PENDING | 处理状态：PENDING / PROCESSING / COMPLETED / FAILED / DUPLICATE |
| `createdAt` | `LocalDateTime` | NOT NULL | 记录创建时间 |
| `version` | `Long` | NOT NULL, `@Version` | 乐观锁版本号 |

**索引**：`UNIQUE(eventId)` — 去重查询的主索引。

---

## 6. TranscodeTask（转码任务）

代表一个视频转码作业。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | `Long` | PK, AUTO_INCREMENT | 主键 |
| `syncTask` | `SyncTask` | `@ManyToOne`, nullable | 关联的同步任务（同步后置转码时） |
| `webhookRule` | `WebhookRule` | `@ManyToOne`, nullable | 关联的 Webhook 规则（Webhook 触发时） |
| `sourceFilePath` | `String(1000)` | NOT NULL | 源视频文件路径 |
| `targetFilePath` | `String(1000)` | NOT NULL | 目标输出文件路径 |
| `sourceFormat` | `VideoFormat` | NOT NULL | 源视频格式枚举：FLV / MP4 / M4V / UNKNOWN |
| `targetFormat` | `OutputFormat` | NOT NULL | 目标格式枚举：MP3 / MP4 / FLV |
| `bitrate` | `Integer` | NOT NULL, DEFAULT 128000 | 目标码率（bps，默认 128kbps） |
| `progress` | `Integer` | NOT NULL, DEFAULT 0 | 转码进度（0-1000，千分比） |
| `status` | `TranscodeStatus` | NOT NULL, DEFAULT PENDING | 状态枚举：PENDING / SCANNING / TRANSCODING / UPLOADING / COMPLETED / FAILED |
| `tempFilePath` | `String(1000)` | nullable | 临时文件本地路径 |
| `createdAt` | `LocalDateTime` | NOT NULL | 创建时间 |
| `version` | `Long` | NOT NULL, `@Version` | 乐观锁版本号 |

**索引**：`INDEX(status)`、`INDEX(syncTask)`。

---

## 实体关系

```text
StorageEngine 1──N SyncTask 1──N TaskExecution
StorageEngine 1──N WebhookRule 1──N WebhookEvent
SyncTask 1──N TranscodeTask
WebhookRule 1──N TranscodeTask
WebhookEvent 1──N TaskExecution
```

## 全局设计规则

| 规则 | 说明 |
|------|------|
| 乐观锁 | 所有实体强制 `@Version`，防止并发写冲突 |
| 加密存储 | 密码/Token 字段使用 AES-256-GCM `@Converter` 加密 |
| 时间戳 | 统一使用 `LocalDateTime`（不依赖时区，以系统时区为 UTC） |
| 枚举映射 | 所有枚举通过 `@Enumerated(EnumType.STRING)` 存储为字符串 |
| DTO 隔离 | 实体不可直接暴露到 Controller 层——使用专用 VO/DTO 类 |
| 写事务 | 所有 Service 层写操作 `@Transactional` |
| 中文注释 | 所有实体字段和类均附简体中文 JavaDoc 注释 |
