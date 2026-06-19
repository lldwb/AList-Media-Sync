# 前端-后端接口契约：Web 管理前端界面

**功能**：004-web-management-frontend | **日期**：2026-06-19

## 概述

此文档定义前端 Web 界面与后端 REST API 之间的接口契约。所有端点均复用现有后端 API，仅仪表板统计端点为新增。

---

## 1. 通用约定

- **基础 URL**：前端页面与后端 API 同源（`/api/*` 相对路径）
- **认证**：所有管理 API 请求通过 `Authorization: Basic <base64(username:password)>` 头认证
- **内容类型**：`Content-Type: application/json`
- **错误响应**：统一 `ApiResult<T>` 格式，`code` 非 200 时 `data` 为 null，`message` 包含错误描述
- **HTTP 状态码**：200（成功）、400（参数错误）、401（未认证）、404（资源不存在）、409（冲突）、500（服务端异常）

---

## 2. 复用现有端点

### 2.1 存储引擎管理

| 方法 | 路径 | 说明 | 请求体 | 响应 `data` |
|------|------|------|--------|------------|
| `GET` | `/api/storage-engines` | 获取所有引擎 | — | `StorageEngineVO[]` |
| `POST` | `/api/storage-engines` | 创建引擎 | `StorageEngineCreateDTO` | `StorageEngineVO` |
| `PUT` | `/api/storage-engines/{id}` | 更新引擎 | `StorageEngineUpdateDTO` | `StorageEngineVO` |
| `DELETE` | `/api/storage-engines/{id}` | 删除引擎 | — | `null` |
| `POST` | `/api/storage-engines/{id}/test` | 测试连接 | — | `{connected: boolean}` |

### 2.2 同步任务管理

| 方法 | 路径 | 说明 | 请求体 | 响应 `data` |
|------|------|------|--------|------------|
| `GET` | `/api/sync-tasks` | 获取所有同步任务 | — | `SyncTaskVO[]` |
| `POST` | `/api/sync-tasks` | 创建同步任务 | `SyncTaskCreateDTO` | `SyncTaskVO` |
| `PUT` | `/api/sync-tasks/{id}` | 更新同步任务 | `SyncTaskUpdateDTO` | `SyncTaskVO` |
| `DELETE` | `/api/sync-tasks/{id}` | 删除同步任务 | — | `null` |
| `POST` | `/api/sync-tasks/{id}/execute` | 手动触发 | — | `{taskId: number}` |
| `POST` | `/api/sync-tasks/{id}/enable` | 启用调度 | — | `SyncTaskVO` |
| `POST` | `/api/sync-tasks/{id}/disable` | 禁用调度 | — | `SyncTaskVO` |
| `GET` | `/api/sync-tasks/{id}/executions` | 执行历史 | — | `TaskExecution[]` |

### 2.3 转码任务管理

| 方法 | 路径 | 说明 | 请求体 | 响应 `data` |
|------|------|------|--------|------------|
| `GET` | `/api/transcode-tasks` | 获取所有转码任务 | — | `TranscodeTaskVO[]` |
| `POST` | `/api/transcode-tasks` | 创建转码任务 | `TranscodeTaskCreateDTO` | `TranscodeTaskVO` |
| `POST` | `/api/transcode-tasks/{id}/retry-upload` | 重试上传 | — | `{taskId, success}` |
| `DELETE` | `/api/transcode-tasks/cleanup-temp` | 清理残留文件 | — | `{deletedCount}` |

### 2.4 Webhook 规则管理

| 方法 | 路径 | 说明 | 请求体 | 响应 `data` |
|------|------|------|--------|------------|
| `GET` | `/api/webhook-rules` | 获取所有规则 | — | `WebhookRuleVO[]` |
| `POST` | `/api/webhook-rules` | 创建规则 | `WebhookRuleCreateDTO` | `WebhookRuleVO` |
| `PUT` | `/api/webhook-rules/{id}` | 更新规则 | `WebhookRuleCreateDTO` | `WebhookRuleVO` |
| `DELETE` | `/api/webhook-rules/{id}` | 删除规则 | — | `null` |
| `POST` | `/api/webhook-rules/{id}/enable` | 启用规则 | — | `WebhookRuleVO` |
| `POST` | `/api/webhook-rules/{id}/disable` | 禁用规则 | — | `WebhookRuleVO` |

---

## 3. 新增端点

### 3.1 仪表板统计

**`GET /api/dashboard/stats`**

- **认证**：需要
- **说明**：返回系统概览的聚合统计数据
- **响应 `data`**：

```json
{
  "activeSyncTasks": 3,
  "pendingTranscodeTasks": 5,
  "todayProcessedFiles": 1280,
  "last24hSuccessRate": 96.5,
  "totalEngines": 8,
  "totalWebhookRules": 4
}
```

- **实现要点**（后端）：
  - `activeSyncTasks`：统计当前状态为 `RUNNING` 的 `SyncTask` 对应的 `TaskExecution` 数量
  - `pendingTranscodeTasks`：统计状态为 `PENDING` 或 `TRANSCODING` 的 `TranscodeTask` 数量
  - `todayProcessedFiles`：统计今天所有已完成的 `TaskExecution` 的 `successCount` 总和
  - `last24hSuccessRate`：统计过去 24 小时所有已完成的 `TaskExecution` 的 `successCount / (successCount + failureCount) * 100`，保留 1 位小数
  - `totalEngines`：`StorageEngine` 总数
  - `totalWebhookRules`：`WebhookRule` 总数

### 3.2 Webhook 事件查询（可选）

**`GET /api/webhook-events?page=1&size=20`**

- **认证**：需要
- **说明**：分页查询所有 Webhook 事件（时间倒序）
- **响应 `data`**：`WebhookEvent[]`

> **注**：此端点仅在后端 `WebhookEvent` 无独立查询端点时新增。如 `WebhookRuleService` 已提供相关查询方法，直接复用。

---

## 4. 前端请求模式

### 4.1 认证请求头

```javascript
// 每个 API 请求携带
headers: {
  'Authorization': 'Basic ' + btoa(username + ':' + password),
  'Content-Type': 'application/json'
}
```

### 4.2 轮询模式（进度监控）

```javascript
// 同步任务进度轮询（5 秒间隔）
const POLL_INTERVAL = 5000;

function startPolling(taskId) {
  return setInterval(async () => {
    const task = await api.get(`/api/sync-tasks/${taskId}`);
    updateProgress(task.data);
    if (task.data.status === 'COMPLETED' || task.data.status === 'FAILED') {
      stopPolling();
    }
  }, POLL_INTERVAL);
}
```

### 4.3 错误处理统一拦截

```javascript
// api.js 全局 fetch 封装
async function request(method, path, body = null) {
  const response = await fetch(path, {
    method,
    headers: getHeaders(),
    body: body ? JSON.stringify(body) : null
  });
  
  if (response.status === 401) {
    auth.clearAndRedirect();  // 清除凭据，跳转登录页
    throw new Error('未认证');
  }
  
  const result = await response.json();  // ApiResult<T>
  if (result.code !== 200) {
    throw new Error(result.message || '请求失败');
  }
  
  return result.data;
}
```

---

## 5. 契约约束

- **不修改现有 API 签名**：所有现有端点的 URL、请求体、响应体格式保持不变
- **数据格式**：日期时间使用 ISO 8601 字符串（`2026-06-19T14:30:00`），由后端 Jackson 自动序列化
- **枚举值**：前后端枚举值保持一致（如 `INCREMENTAL`、`FULL`、`MOVE`），后端 `@Enumerated(EnumType.STRING)` 确保映射一致
- **空值处理**：后端可选字段可能为 `null`，前端需做空值防护
