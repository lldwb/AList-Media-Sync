# 前端-后端接口契约：Web 管理前端界面

**功能**：004-web-management-frontend | **日期**：2026-06-19 | **修订**：2026-06-19（React + TypeScript Vite 前端）

## 概述

此文档定义 React TypeScript 前端与后端 REST API 之间的接口契约。所有端点均复用现有后端 API，仅仪表板统计端点为新增。

---

## 1. 通用约定

- **开发模式**：Vite 开发服务器（`localhost:5173`）通过 `vite.config.ts` 中的 proxy 配置代理 `/api/*` 请求到 `localhost:8080`
- **生产模式**：前端构建产物位于 `src/main/resources/static/app/`，与后端同源（`/api/*` 相对路径）
- **认证**：所有管理 API 请求通过 `Authorization: Basic <base64(username:password)>` 头认证
- **内容类型**：`Content-Type: application/json`
- **错误响应**：统一 `ApiResult<T>` 格式，`code` 非 200 时 `data` 为 null，`message` 包含错误描述
- **HTTP 状态码**：200（成功）、400（参数错误）、401（未认证）、404（资源不存在）、409（冲突）、500（服务端异常）

---

## 2. 复用现有端点

### 2.1 存储引擎管理

| 方法 | 路径 | 说明 | 请求体 TypeScript 类型 | 响应 `data` TypeScript 类型 |
|------|------|------|--------|------------|
| `GET` | `/api/storage-engines` | 获取所有引擎 | — | `StorageEngineVO[]` |
| `POST` | `/api/storage-engines` | 创建引擎 | `StorageEngineCreateDTO` | `StorageEngineVO` |
| `PUT` | `/api/storage-engines/{id}` | 更新引擎 | `StorageEngineUpdateDTO` | `StorageEngineVO` |
| `DELETE` | `/api/storage-engines/{id}` | 删除引擎 | — | `null` |
| `POST` | `/api/storage-engines/{id}/test` | 测试连接 | — | `{connected: boolean}` |

### 2.2 同步任务管理

| 方法 | 路径 | 说明 | 请求体类型 | 响应 `data` 类型 |
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

| 方法 | 路径 | 说明 | 请求体类型 | 响应 `data` 类型 |
|------|------|------|--------|------------|
| `GET` | `/api/transcode-tasks` | 获取所有转码任务 | — | `TranscodeTaskVO[]` |
| `POST` | `/api/transcode-tasks` | 创建转码任务 | `TranscodeTaskCreateDTO` | `TranscodeTaskVO` |
| `POST` | `/api/transcode-tasks/{id}/retry-upload` | 重试上传 | — | `{taskId, success}` |
| `DELETE` | `/api/transcode-tasks/cleanup-temp` | 清理残留文件 | — | `{deletedCount}` |

### 2.4 Webhook 规则管理

| 方法 | 路径 | 说明 | 请求体类型 | 响应 `data` 类型 |
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
- **前端类型**：`DashboardStatsVO`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "activeSyncTasks": 3,
    "pendingTranscodeTasks": 5,
    "todayProcessedFiles": 1280,
    "last24hSuccessRate": 96.5,
    "totalEngines": 8,
    "totalWebhookRules": 4
  }
}
```

### 3.2 Webhook 事件查询

**`GET /api/webhooks/events?page=1&size=20`**

- **认证**：需要
- **说明**：分页查询所有 Webhook 事件（时间倒序）
- **前端类型**：`WebhookEventVO[]`

> **注**：此端点仅在现有后端无独立 Webhook 事件查询接口时新增。

---

## 4. 前端请求模式

### 4.1 认证请求头注入

```typescript
// src/main/frontend/src/api/client.ts
const creds = sessionStorage.getItem('auth_credentials');
if (creds) {
  headers['Authorization'] = `Basic ${creds}`;
}
```

### 4.2 轮询模式（进度监控）

```typescript
// src/main/frontend/src/hooks/usePolling.ts
const { data, error } = usePolling(
  () => api.get<SyncTaskVO>(`/sync-tasks/${taskId}`),
  5000,
  (task) => ['COMPLETED', 'FAILED', 'PARTIAL_SUCCESS', 'INTERRUPTED']
    .includes(task.lastExecution?.status)
);
```

### 4.3 错误处理统一拦截

```typescript
// src/main/frontend/src/api/client.ts
if (res.status === 401) {
  sessionStorage.removeItem('auth_credentials');
  window.location.hash = '#/login';
  throw new Error('未认证');
}

const result: ApiResult<T> = await res.json();
if (result.code !== 200) {
  throw new Error(result.message || '请求失败');
}
```

---

## 5. Vite 代理配置

```typescript
// src/main/frontend/vite.config.ts
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../resources/static/app',
    emptyOutDir: true,
  },
});
```

---

## 6. 契约约束

- **不修改现有 API 签名**：所有现有端点的 URL、请求体、响应体格式保持不变
- **数据格式**：日期时间使用 ISO 8601 字符串，由 `utils/format.ts` 在前端格式化
- **枚举值**：前后端枚举值保持一致，TypeScript 类型确保编译时一致性
- **空值处理**：后端可选字段可能为 `null`，TypeScript 类型标注 `field?: type` 确保安全访问
