# WebSocket 消息契约

**日期**：2026-06-21 | **关联**：[plan.md](../plan.md) | [data-model.md](../data-model.md)

## 连接端点

```
ws://{host}:{port}/ws/events
```

## 认证

HTTP Upgrade 握手阶段通过 `Authorization` 请求头传递 Basic Auth 凭据：
```
Authorization: Basic base64(username:password)
```

认证失败 → HTTP 401，拒绝 WebSocket 升级请求。

## 通用消息格式

```json
{
  "type": "消息类型枚举值",
  "payload": { /* 增量数据载荷 */ },
  "timestamp": "ISO 8601 时间戳"
}
```

## 消息类型定义

### 1. SYNC_PROGRESS — 同步任务进度

**触发时机**：同步任务状态或进度变更

**payload 字段**：

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `taskId` | number | 是 | 同步任务 ID |
| `executionId` | number | 否 | 执行记录 ID |
| `status` | string | 是 | 任务状态（PENDING/RUNNING/COMPLETED/FAILED） |
| `successFiles` | number | 是 | 成功同步文件数 |
| `failedFiles` | number | 是 | 失败文件数 |
| `totalFiles` | number | 是 | 总文件数 |
| `progressPercent` | number | 否 | 进度百分比（0-100） |

**示例**：
```json
{
  "type": "SYNC_PROGRESS",
  "payload": {
    "taskId": 1,
    "executionId": 42,
    "status": "RUNNING",
    "successFiles": 45,
    "failedFiles": 2,
    "totalFiles": 100,
    "progressPercent": 47
  },
  "timestamp": "2026-06-21T10:30:00"
}
```

---

### 2. TRANSCODE_PROGRESS — 转码任务进度

**触发时机**：转码任务状态或进度变更

**payload 字段**：

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `taskId` | number | 是 | 转码任务 ID |
| `status` | string | 是 | 转码状态（PENDING/DOWNLOADING/TRANSCODING/UPLOADING/COMPLETED/各类失败） |
| `progressPercent` | number | 否 | 进度百分比（0-100） |
| `retryCount` | number | 否 | 已重试次数 |
| `errorMessage` | string | 否 | 失败时的错误消息 |

**示例**：
```json
{
  "type": "TRANSCODE_PROGRESS",
  "payload": {
    "taskId": 1,
    "status": "TRANSCODING",
    "progressPercent": 45,
    "retryCount": 0
  },
  "timestamp": "2026-06-21T10:30:00"
}
```

---

### 3. TASK_EVENT — 任务事件

**触发时机**：任务创建、删除、完成

**payload 字段**：

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `action` | string | 是 | 事件动作（CREATED/DELETED/COMPLETED/BATCH_DELETED） |
| `taskType` | string | 是 | 任务类型（SYNC/TRANSCODE） |
| `taskId` | number | 否 | 单个任务 ID（单任务操作时） |
| `count` | number | 否 | 影响数量（批量操作时） |
| `status` | string | 否 | 批量删除时的状态筛选 |

**示例（单任务）**：
```json
{
  "type": "TASK_EVENT",
  "payload": {
    "action": "CREATED",
    "taskType": "TRANSCODE",
    "taskId": 1
  },
  "timestamp": "2026-06-21T10:30:00"
}
```

**示例（批量删除）**：
```json
{
  "type": "TASK_EVENT",
  "payload": {
    "action": "BATCH_DELETED",
    "taskType": "TRANSCODE",
    "count": 5,
    "status": "DOWNLOAD_FAILED"
  },
  "timestamp": "2026-06-21T10:30:00"
}
```

---

### 4. WEBHOOK_EVENT — Webhook 事件

**触发时机**：Webhook 事件接收、处理状态变更

**payload 字段**：

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `eventId` | number | 是 | Webhook 事件 ID |
| `eventType` | string | 是 | 事件类型（FILE_CLOSED/SESSION_ENDED 等） |
| `status` | string | 是 | 处理状态（PENDING/PROCESSING/COMPLETED/FAILED） |

**示例**：
```json
{
  "type": "WEBHOOK_EVENT",
  "payload": {
    "eventId": 100,
    "eventType": "SESSION_ENDED",
    "status": "COMPLETED"
  },
  "timestamp": "2026-06-21T10:30:00"
}
```

---

### 5. DASHBOARD_UPDATE — 仪表板更新

**触发时机**：统计数据变更（2 秒防抖合并）

**payload 字段**：

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `totalSyncTasks` | number | 是 | 同步任务总数 |
| `activeSyncTasks` | number | 是 | 活跃同步任务数 |
| `totalTranscodeTasks` | number | 是 | 转码任务总数 |
| `activeTranscodeTasks` | number | 是 | 活跃转码任务数 |
| `todayWebhookEvents` | number | 是 | 今日 Webhook 事件数 |
| `engineOnlineCount` | number | 否 | 在线存储引擎数 |

**示例**：
```json
{
  "type": "DASHBOARD_UPDATE",
  "payload": {
    "totalSyncTasks": 10,
    "activeSyncTasks": 3,
    "totalTranscodeTasks": 50,
    "activeTranscodeTasks": 5,
    "todayWebhookEvents": 12,
    "engineOnlineCount": 3
  },
  "timestamp": "2026-06-21T10:30:00"
}
```

---

## 前端消息处理

### 消息路由规则

```typescript
switch (message.type) {
  case 'SYNC_PROGRESS':
    // 更新 SyncTaskListPage / SyncTaskDetailPage 本地状态
    break;
  case 'TRANSCODE_PROGRESS':
    // 更新 TranscodeTaskListPage 本地状态
    break;
  case 'TASK_EVENT':
    // 根据 taskType 刷新对应列表 / 显示提示
    break;
  case 'WEBHOOK_EVENT':
    // 更新 WebhookEventListPage 本地状态
    break;
  case 'DASHBOARD_UPDATE':
    // 更新 DashboardPage 统计数据
    break;
}
```

### 增量合并策略

前端维护本地状态 Map（key 为 taskId），收到增量消息后浅合并：
```typescript
setTasks(prev => prev.map(t => t.id === payload.taskId ? { ...t, ...payload } : t));
```

### 连接状态管理

```
CONNECTING → OPEN → (onMessage 循环)
                 → CLOSED → (指数退避重连) → CONNECTING
                 → AUTH_FAILED → 跳转登录页
```
