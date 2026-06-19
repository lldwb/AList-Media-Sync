# 数据模型：Web 管理前端界面

**功能**：004-web-management-frontend | **日期**：2026-06-19

## 概述

本功能是纯前端 UI 层，不引入新的数据库实体。以下模型描述前端运行时的数据结构和状态。

---

## 前端运行时数据

### 1. 认证状态（AuthState）

代表前端认证会话的完整状态。

| 字段 | 类型 | 说明 |
|------|------|------|
| `isAuthenticated` | boolean | 是否已通过认证 |
| `username` | string \| null | 当前登录用户名 |
| `credentials` | string \| null | Base64 编码的 `username:password` |
| `lastActivity` | number \| null | 最后用户交互时间戳（毫秒） |
| `redirectPath` | string \| null | 登录成功后要恢复的页面路径 |

状态转换：

```
[未认证] ──登录成功──> [已认证] ──30分钟无交互/收到401──> [未认证]
    ↑                      │
    └──────登出────────────┘
```

### 2. 页面路由映射（RouteMap）

| 路径 | 页面组件 | 标题 | 所需数据 |
|------|---------|------|---------|
| `/login` | LoginPage | 登录 | 无 |
| `/` | DashboardPage | 系统概览 | `GET /api/dashboard/stats` |
| `/engines` | StorageEnginePage | 存储引擎 | `GET /api/storage-engines` |
| `/sync-tasks` | SyncTaskListPage | 同步任务 | `GET /api/sync-tasks` |
| `/sync-tasks/:id` | SyncTaskDetailPage | 任务详情 | `GET /api/sync-tasks/{id}` + `GET /api/sync-tasks/{id}/executions` |
| `/transcode-tasks` | TranscodeTaskPage | 转码任务 | `GET /api/transcode-tasks` |
| `/webhook-rules` | WebhookRulePage | Webhook 规则 | `GET /api/webhook-rules` |
| `/webhook-events` | WebhookEventPage | Webhook 事件 | `GET /api/webhook-events`（注：后端尚无此端点，需新增） |

### 3. 页面状态模型（PageState）

每个列表页面共享的通用状态结构：

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | Array | 当前页面数据列表 |
| `loading` | boolean | 是否正在加载 |
| `error` | string \| null | 错误消息 |
| `currentPage` | number | 当前页码（1 起始） |
| `pageSize` | number | 每页条数（默认 20） |
| `totalItems` | number | 总记录数（前端分页 = items.length） |
| `selectedItem` | object \| null | 当前选中/编辑的条目 |
| `showForm` | boolean | 是否显示创建/编辑表单 |
| `showDeleteConfirm` | boolean | 是否显示删除确认对话框 |

### 4. 表单模型（FormModels）

**存储引擎表单**：
| 字段 | 校验规则 |
|------|---------|
| `name` | 必填，最长 100 字符 |
| `apiBaseUrl` | 必填，有效 URL 格式（http/https 开头） |
| `username` | 必填 |
| `password` | 必填 |

**同步任务表单**：
| 字段 | 校验规则 |
|------|---------|
| `name` | 必填 |
| `sourceEngineId` | 必填（下拉选择） |
| `targetEngineId` | 必填（下拉选择） |
| `sourcePath` | 必填，以 `/` 开头 |
| `targetPath` | 必填，以 `/` 开头 |
| `syncMode` | 必填，枚举值（INCREMENTAL / FULL / MOVE） |
| `scheduleType` | 必填，枚举值（INTERVAL / CRON / MANUAL） |
| `intervalSeconds` | 条件必填，当 scheduleType=INTERVAL 时，正整数 |
| `cronExpression` | 条件必填，当 scheduleType=CRON 时，合法 cron 格式 |
| `excludeRules` | 可选 |
| `conflictStrategy` | 可选，枚举值（OVERWRITE / SKIP / RENAME） |
| `convertToMp3` | 可选，布尔值 |

**转码任务表单**：
| 字段 | 校验规则 |
|------|---------|
| `sourceEngineId` | 必填（下拉选择） |
| `targetEngineId` | 必填（下拉选择） |
| `sourceFilePath` | 必填 |
| `targetFilePath` | 必填 |
| `targetFormat` | 必填，枚举值（MP3 / MP4 / FLV） |
| `bitrate` | 可选，正整数，默认 128 |

**Webhook 规则表单**：
| 字段 | 校验规则 |
|------|---------|
| `name` | 必填 |
| `triggerEventType` | 必填 |
| `roomIdFilter` | 可选，数字 |
| `actions` | 必填，至少选一项 |
| `targetEngineId` | 条件必填（动作含"同步至 AList"时） |
| `targetPath` | 条件必填 |

---

## 前后端数据映射

### API 响应格式（ApiResult<T>）

所有后端 API 响应均遵循统一格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

前端通过 `api.js` 模块统一处理此格式，提取 `data` 字段。

### 关键 DTO 映射

前端直接使用后端返回的 JSON 对象，无需本地模型转换。

**StorageEngineVO**：
```
id, name, apiBaseUrl, username, password[CLEARED], createdAt, updatedAt
```
注：`password` 字段后端已脱敏，前端接收到的为 `***`。

**SyncTaskVO**：
```
id, name, sourceEngine(StorageEngineVO), targetEngine(StorageEngineVO),
sourcePath, targetPath, syncMode, scheduleType, intervalSeconds,
cronExpression, excludeRules, conflictStrategy, convertToMp3,
enabled, lastExecutionTime
```

**SyncProgressVO**（通过轮询 `GET /api/sync-tasks/{id}` 获取）：
```
completedFiles, totalFiles, currentFile, syncSpeed, estimatedTimeRemaining
```

**TaskExecution**：
```
id, syncTaskId, taskType, startTime, endTime, status,
totalFiles, successCount, failureCount, failureDetails
```
注：`failureDetails` 为 JSON 字符串，前端解析后展示。

**TranscodeTaskVO**：
```
id, sourceFilePath, targetFilePath, targetFormat, bitrate,
progress, status, createdAt, updatedAt
```

**WebhookRuleVO**：
```
id, name, triggerEventType, roomIdFilter, actions,
targetEngine, targetPath, enabled
```

**WebhookEvent**：
```
id, eventId, eventType, timestamp, eventData, status, webhookRuleId
```
