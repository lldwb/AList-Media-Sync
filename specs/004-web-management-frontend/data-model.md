# 数据模型：Web 管理前端界面

**功能**：004-web-management-frontend | **日期**：2026-06-19 | **修订**：2026-06-19（迁移至 TypeScript 接口定义）

## 概述

本功能是纯前端 UI 层，不引入新的数据库实体。以下模型描述前端运行时的 TypeScript 类型定义和 React 组件状态结构。

---

## 前端 TypeScript 类型定义

### 1. 后端 API 响应类型（src/main/frontend/src/types/api.ts）

```typescript
// 统一响应包装
interface ApiResult<T> {
  code: number;
  message: string;
  data: T | null;
}

// 存储引擎
interface StorageEngineVO {
  id: number;
  name: string;
  apiBaseUrl: string;
  username: string;
  password: string;  // 后端已脱敏 → "***"
  createdAt: string; // ISO 8601
  updatedAt: string;
}

interface StorageEngineCreateDTO {
  name: string;
  apiBaseUrl: string;
  username: string;
  password: string;
}

interface StorageEngineUpdateDTO {
  name?: string;
  apiBaseUrl?: string;
  username?: string;
  password?: string;
}

// 同步任务
interface SyncTaskVO {
  id: number;
  name: string;
  sourceEngine: StorageEngineVO;
  targetEngine: StorageEngineVO;
  sourcePath: string;
  targetPath: string;
  syncMode: 'INCREMENTAL' | 'FULL' | 'MOVE';
  scheduleType: 'INTERVAL' | 'CRON' | 'MANUAL';
  intervalSeconds?: number;
  cronExpression?: string;
  excludeRules?: string;
  conflictStrategy: 'OVERWRITE' | 'SKIP' | 'RENAME';
  convertToMp3: boolean;
  enabled: boolean;
  lastExecutionTime?: string;
}

// 同步进度（通过轮询 GET /api/sync-tasks/{id} 获取）
interface SyncProgressVO {
  completedFiles: number;
  totalFiles: number;
  currentFile: string;
  syncSpeed: number;  // 文件/秒
  estimatedTimeRemaining: number; // 秒
}

// 任务执行记录
interface TaskExecution {
  id: number;
  syncTaskId: number;
  taskType: 'SYNC' | 'TRANSCODE' | 'WEBHOOK';
  startTime: string;
  endTime?: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'PARTIAL_SUCCESS' | 'INTERRUPTED';
  totalFiles: number;
  successCount: number;
  failureCount: number;
  failureDetails?: string; // JSON 字符串，前端 JSON.parse() 后为 FailureDetail[]
}

interface FailureDetail {
  fileName: string;
  reason: string;
}

// 转码任务
interface TranscodeTaskVO {
  id: number;
  sourceFilePath: string;
  targetFilePath: string;
  targetFormat: 'MP3' | 'MP4' | 'FLV';
  bitrate: number;
  progress: number;  // 0-100
  status: 'PENDING' | 'TRANSCODING' | 'UPLOADING' | 'COMPLETED' | 'FAILED';
  createdAt: string;
  updatedAt: string;
}

interface TranscodeTaskCreateDTO {
  sourceEngineId: number;
  targetEngineId: number;
  sourceFilePath: string;
  targetFilePath: string;
  targetFormat: 'MP3' | 'MP4' | 'FLV';
  bitrate?: number; // 默认 128
}

// Webhook 规则
interface WebhookRuleVO {
  id: number;
  name: string;
  triggerEventType: 'FILE_CLOSED' | 'SESSION_ENDED' | 'ALL';
  roomIdFilter?: number;
  actions: ('SYNC_TO_ALIST' | 'CONVERT_TO_MP3')[];
  targetEngine?: StorageEngineVO;
  targetPath?: string;
  enabled: boolean;
}

// Webhook 事件
interface WebhookEventVO {
  id: number;
  eventId: string;
  eventType: string;
  timestamp: string;
  eventData?: Record<string, unknown>;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  webhookRuleId?: number;
  webhookRuleName?: string;
}

// 仪表板统计
interface DashboardStatsVO {
  activeSyncTasks: number;
  pendingTranscodeTasks: number;
  todayProcessedFiles: number;
  last24hSuccessRate: number;  // 百分比，如 96.5
  totalEngines: number;
  totalWebhookRules: number;
}
```

### 2. 认证状态（src/main/frontend/src/auth/AuthContext.tsx）

```typescript
interface AuthState {
  username: string;
  credentials: string; // Base64(username:password)
}

interface AuthContextValue {
  auth: AuthState | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  redirectPath: string | null;
  setRedirectPath: (path: string | null) => void;
}
```

状态转换：

```
[auth === null] ──login() 成功──> [auth !== null] ──logout() 或 30min 无交互 或 收到 401──> [auth === null]
     ↑                              │
     └──────── 页面加载时 sessionStorage 有凭据 ──┘
```

### 3. 页面路由映射

| 路径 | 页面组件 | 标题 | 所需数据 |
|------|---------|------|---------|
| `/login` | `LoginPage` | 登录 | 无 |
| `/` | `DashboardPage` | 系统概览 | `GET /api/dashboard/stats` |
| `/engines` | `EngineListPage` | 存储引擎 | `GET /api/storage-engines` |
| `/sync-tasks` | `SyncTaskListPage` | 同步任务 | `GET /api/sync-tasks` |
| `/sync-tasks/:id` | `SyncTaskDetailPage` | 任务详情 | `GET /api/sync-tasks/{id}` + `GET /api/sync-tasks/{id}/executions` |
| `/transcode-tasks` | `TranscodeTaskListPage` | 转码任务 | `GET /api/transcode-tasks` |
| `/webhook-rules` | `WebhookRuleListPage` | Webhook 规则 | `GET /api/webhook-rules` |
| `/webhook-events` | `WebhookEventListPage` | Webhook 事件 | `GET /api/webhooks/events` |

### 4. 通用页面状态 hook

```typescript
// src/main/frontend/src/hooks/usePagination.ts
interface PaginationState<T> {
  items: T[];
  currentPage: number;
  totalPages: number;
  pageSize: number;      // 默认 20
  goToPage: (page: number) => void;
  currentItems: T[];     // 当前页数据切片
}

// 列表页面通用状态模式
interface ListPageState<T> {
  items: T[];
  loading: boolean;
  error: string | null;
  showForm: boolean;
  editingItem: T | null;
  showDeleteConfirm: T | null;
}
```

### 5. 表单类型

**存储引擎表单值**：
```typescript
interface EngineFormValues {
  name: string;          // 必填，max 100
  apiBaseUrl: string;    // 必填，URL 格式 (http/https)
  username: string;      // 必填
  password: string;      // 必填
}
```

**同步任务表单值**：
```typescript
interface SyncTaskFormValues {
  name: string;
  sourceEngineId: number;
  targetEngineId: number;
  sourcePath: string;      // 必填，以 / 开头
  targetPath: string;      // 必填，以 / 开头
  syncMode: 'INCREMENTAL' | 'FULL' | 'MOVE';
  scheduleType: 'INTERVAL' | 'CRON' | 'MANUAL';
  intervalSeconds?: number; // scheduleType === 'INTERVAL' 时必填
  cronExpression?: string;  // scheduleType === 'CRON' 时必填
  excludeRules?: string;
  conflictStrategy: 'OVERWRITE' | 'SKIP' | 'RENAME';
  convertToMp3: boolean;
}
```

**转码任务表单值**：
```typescript
interface TranscodeTaskFormValues {
  sourceEngineId: number;
  targetEngineId: number;
  sourceFilePath: string;
  targetFilePath: string;
  targetFormat: 'MP3' | 'MP4' | 'FLV';
  bitrate: number;  // 默认 128
}
```

**Webhook 规则表单值**：
```typescript
interface WebhookRuleFormValues {
  name: string;
  triggerEventType: 'FILE_CLOSED' | 'SESSION_ENDED' | 'ALL';
  roomIdFilter?: number;
  actions: ('SYNC_TO_ALIST' | 'CONVERT_TO_MP3')[];
  targetEngineId?: number;  // actions 包含 'SYNC_TO_ALIST' 时必填
  targetPath?: string;      // actions 包含 'SYNC_TO_ALIST' 时必填
}
```

---

## 前后端数据映射

前后端通过 HTTP JSON 通信。前端 `api/client.ts` 统一处理 `ApiResult<T>` 解析，提取 `data` 字段并返回类型化对象。所有日期时间以 ISO 8601 字符串传输，由 `utils/format.ts` 在前端格式化展示。
