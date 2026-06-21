# 数据模型：转码与同步模块优化及实时通信改造

**日期**：2026-06-21 | **关联**：[plan.md](./plan.md) | [research.md](./research.md)

## 实体变更

### 1. TranscodeTask（转码任务）— 修改

**所属模块**：`transcode/entity/TranscodeTask.java`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `retryCount` | `int` | `@Column(nullable = false)`, 默认 0 | 已执行的自动重试次数 |

**状态模型**（来自 006，无变更）：
```
PENDING(0) → DOWNLOADING(1) → TRANSCODING(3) → UPLOADING(5) → COMPLETED(7)
               ↓(失败)            ↓(失败)          ↓(失败)
          DOWNLOAD_FAILED(2)  TRANSCODE_FAILED(4)  UPLOAD_FAILED(6)
```

**新增查询方法**（TranscodeTaskRepository）：
- `deleteByStatusIn(List<TranscodeStatus> statuses)` — 按状态批量删除
- `findByStatusIn(List<TranscodeStatus> statuses)` — 按状态批量查询
- `countByStatusIn(List<TranscodeStatus> statuses)` — 按状态计数

---

### 2. SyncTask（同步任务）— 修改

**所属模块**：`sync/entity/SyncTask.java`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `transcodeTargetFormat` | `String` | 可为空，默认 "MP3" | 同步后置转码目标格式 |
| `transcodeBitrate` | `int` | 可为空，默认 128000 | 同步后置转码码率（bps） |

---

### 3. TaskExecution（任务执行记录）— 修改

**所属模块**：`sync/entity/TaskExecution.java`

**failureDetails JSON 扩展**：每个失败文件记录新增字段：
```json
{
  "fileName": "recording.flv",
  "failReason": "网络超时",
  "retryCount": 2,
  "maxRetries": 3
}
```

> **注意**：`failureDetails` 是 TEXT/CLOB 字段存储 JSON 字符串，不新增数据库列。仅修改 JSON 序列化/反序列化逻辑。

---

## 新增实体/枚举

### 4. MessageType（消息类型枚举）

**所属模块**：`common/enums/MessageType.java`

| 枚举值 | 说明 | 触发时机 |
|--------|------|---------|
| `SYNC_PROGRESS` | 同步任务进度 | 同步任务状态/进度变更 |
| `TRANSCODE_PROGRESS` | 转码任务进度 | 转码任务状态/进度变更 |
| `TASK_EVENT` | 任务事件 | 任务创建/删除/完成 |
| `WEBHOOK_EVENT` | Webhook 事件 | Webhook 事件接收/处理状态变更 |
| `DASHBOARD_UPDATE` | 仪表板更新 | 统计数据变更（2 秒防抖） |

---

### 5. WsMessage（WebSocket 消息 DTO）

**所属模块**：`common/dto/WsMessage.java`

```java
public record WsMessage(
    String type,        // MessageType 枚举值
    Object payload,     // 增量数据载荷
    String timestamp    // ISO 8601 时间戳
) {}
```

**payload 示例**：

SYNC_PROGRESS：
```json
{
  "taskId": 1,
  "status": "RUNNING",
  "successFiles": 45,
  "failedFiles": 2
}
```

TRANSCODE_PROGRESS：
```json
{
  "taskId": 1,
  "status": "TRANSCODING",
  "progressPercent": 45
}
```

TASK_EVENT：
```json
{
  "action": "CREATED",
  "taskType": "TRANSCODE",
  "taskId": 1
}
```

DASHBOARD_UPDATE：
```json
{
  "totalSyncTasks": 10,
  "activeSyncTasks": 3,
  "totalTranscodeTasks": 50,
  "activeTranscodeTasks": 5,
  "todayWebhookEvents": 12
}
```

---

### 6. RetryableException（可重试异常标记接口）

**所属模块**：`common/exception/RetryableException.java`

```java
/**
 * 可重试异常标记接口。
 * 实现此接口的异常被视为瞬时故障（如网络超时、API 临时不可用），
 * 自动重试逻辑通过 instanceof 判断是否触发重试。
 * 未实现此接口的异常（如文件不存在 404、格式不支持、权限不足）
 * 直接标记为最终失败。
 */
public interface RetryableException {
}
```

**实现此接口的异常**：
- 网络超时（`SocketTimeoutException` 包装）
- HTTP 5xx 服务端错误
- 连接拒绝（`ConnectException` 包装）
- AList API 临时不可用（503 Service Unavailable）

**不实现此接口的异常**：
- 文件不存在（404）
- 格式不支持
- 权限不足（403）
- 磁盘空间不足

---

### 7. 配置属性扩展

**AppProperties 新增**：

```java
// WebSocket 配置
private int websocketMaxConnections = 50;

// 重试配置
private int retryMaxAutoRetries = 3;
private long retryInitialInterval = 1000;
private long retryMaxInterval = 60000;

// 存储引擎健康检查
private long storageHealthCheckInterval = 300;
```

---

## 实体关系图

```
┌─────────────────┐     ┌──────────────────┐
│   SyncTask      │     │  TranscodeTask   │
│─────────────────│     │──────────────────│
│ + transcodeTgt..│     │ + retryCount (新) │
│ + transcodeBit..│     │ + status (8状态)  │
└────────┬────────┘     └────────┬─────────┘
         │                       │
         │ 1:N                   │ 1:1
         ▼                       ▼
┌─────────────────┐     ┌──────────────────┐
│  TaskExecution  │     │  StorageEngine   │
│─────────────────│     │──────────────────│
│ + failureDetails│     │ + engineType     │
│   (JSON 扩展)   │     │ + engineStatus   │
└─────────────────┘     └──────────────────┘
                                  │
                                  │ 策略分发
                                  ▼
                        ┌──────────────────┐
                        │StorageEngineStrat│
                        │──────────────────│
                        │ + copyFile() (新) │
                        └────────┬─────────┘
                                 │
                    ┌────────────┼────────────┐
                    ▼            ▼            ▼
              ┌──────────┐ ┌──────────┐
              │AListStrat│ │LocalStrat│
              │──────────│ │──────────│
              │copyFile()│ │copyFile()│
              └──────────┘ └──────────┘
```

---

## 验证规则

### TranscodeTaskCreateDTO

| 字段 | 规则 |
|------|------|
| `sourceDirectoryTranscode` | 布尔值，默认 false |
| `targetEngineId` | 当 `sourceDirectoryTranscode=false` 时必填；当 `sourceDirectoryTranscode=true` 时可为空（后端自动赋值为 sourceEngineId） |
| `targetFilePath` | 当 `sourceDirectoryTranscode=false` 时必填；当 `sourceDirectoryTranscode=true` 时可为空（后端自动计算） |

### SyncTaskCreateDTO

| 字段 | 规则 |
|------|------|
| `transcodeTargetFormat` | 可为空，有效值：MP3/MP4/FLV |
| `transcodeBitrate` | 可为空，范围 32000-320000（32kbps-320kbps） |

---

## 状态转换

### 自动重试状态转换

```
DOWNLOAD_FAILED ──(自动重试)──> DOWNLOADING
TRANSCODE_FAILED ──(自动重试)──> TRANSCODING
UPLOAD_FAILED ──(自动重试)──> UPLOADING

重试用尽后：
DOWNLOAD_FAILED ──(maxRetries 达到)──> DOWNLOAD_FAILED（最终失败）
TRANSCODE_FAILED ──(maxRetries 达到)──> TRANSCODE_FAILED（最终失败）
UPLOAD_FAILED ──(maxRetries 达到)──> UPLOAD_FAILED（最终失败）
```

### 手动重试状态转换

```
DOWNLOAD_FAILED ──(手动重试)──> DOWNLOADING（不计入自动重试次数）
TRANSCODE_FAILED ──(手动重试)──> TRANSCODING
UPLOAD_FAILED ──(手动重试)──> UPLOADING
```
