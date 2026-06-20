# API 契约：存储引擎重构与体验优化

**日期**：2026-06-20 | **功能**：006-storage-engine-refactor

## 变更的 API 端点

### 存储引擎 CRUD

#### POST /api/storage-engines

创建存储引擎。新增 `engineType` 字段，移除 `username` 字段。

**请求体**：
```json
{
  "name": "我的AList",
  "engineType": "ALIST",
  "baseUrl": "https://alist.example.com",
  "token": "alist-token-here"
}
```

或：
```json
{
  "name": "本地存储",
  "engineType": "LOCAL",
  "localPath": "/data/media"
}
```

**响应**：`ApiResult<StorageEngineVO>`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "我的AList",
    "engineType": "ALIST",
    "baseUrl": "https://alist.example.com",
    "localPath": null,
    "status": "OFFLINE",
    "createdAt": "2026-06-20T10:00:00",
    "updatedAt": "2026-06-20T10:00:00"
  }
}
```

**校验规则**：
- `engineType = ALIST`：`baseUrl` 和 `token` 必填
- `engineType = LOCAL`：`localPath` 必填，目录必须已存在且可读写

---

#### PUT /api/storage-engines/{id}

更新存储引擎。`engineType` 不可更改。

**请求体**：
```json
{
  "name": "新名称",
  "baseUrl": "https://new-alist.example.com",
  "token": "new-token"
}
```

**校验规则**：
- `engineType` 字段如果传入，必须与现有值一致，否则返回 400
- 其他字段可选更新

---

#### GET /api/storage-engines

列表查询。响应中 `StorageEngineVO` 新增 `engineType` 和 `localPath` 字段，移除 `username`。

---

#### GET /api/storage-engines/{id}

详情查询。同上。

---

#### POST /api/storage-engines/{id}/test

测试连接。根据 `engineType` 执行不同的测试逻辑：
- `ALIST`：调用 AList `/api/me` 验证 Token
- `LOCAL`：验证 `localPath` 目录存在且可读写

**响应**：`ApiResult<Boolean>`

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

失败时：
```json
{
  "code": 400,
  "message": "连接测试失败：目录 /data/media 不存在",
  "data": null
}
```

---

### 目录浏览（新增）

#### GET /api/storage-engines/{id}/directories

获取指定存储引擎的子目录列表，供树状目录浏览组件使用。

**参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | String | 否 | 目录路径，不传时返回根目录 |

**响应**：`ApiResult<List<DirectoryEntryVO>>`

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "name": "录播",
      "path": "/录播",
      "hasChildren": true
    },
    {
      "name": "音乐",
      "path": "/音乐",
      "hasChildren": false
    }
  ]
}
```

**行为**：
- 仅返回目录，不返回文件
- AList 类型：通过 `AListStorageStrategy.listDirectories()` 调用 AList API
- LOCAL 类型：通过 `LocalStorageStrategy.listDirectories()` 读取本地文件系统
- 路径不存在时返回空列表

---

### 转码任务

#### POST /api/transcode-tasks

创建转码任务。`bitrate` 改为选填。

**请求体**：
```json
{
  "sourceEngineId": 1,
  "targetEngineId": 2,
  "sourceFilePath": "/录播/2024/test.flv",
  "targetFilePath": "/转码/test.mp3",
  "targetFormat": "MP3",
  "bitrate": null
}
```

**变更**：`bitrate` 为 null 时使用系统默认码率（128 kbps）

---

#### POST /api/transcode-tasks/{id}/retry

重试转码任务。替换原 `retry-upload` 端点，支持从任意失败状态重试。

**响应**：`ApiResult<Void>`

**行为**：
- `DOWNLOAD_FAILED` → 重置为 `DOWNLOADING`，删除部分下载文件
- `TRANSCODE_FAILED` → 重置为 `TRANSCODING`，保留源临时文件
- `UPLOAD_FAILED` → 重置为 `UPLOADING`，保留源+输出临时文件
- 其他状态返回 400

---

#### GET /api/transcode-tasks

列表查询。`TranscodeStatus` 扩展为 8 状态，`bitrate` 可为 null。

**响应字段变更**：
```json
{
  "status": "DOWNLOADING",
  "bitrate": null,
  "canRetry": true
}
```

新增 `canRetry` 字段：仅 `DOWNLOAD_FAILED`/`TRANSCODE_FAILED`/`UPLOAD_FAILED` 为 true。

---

### Webhook 规则

#### POST /api/webhook-rules

创建 Webhook 规则。新增 `recordingEngineId` 和 `recordingPath` 字段，`targetPath` 重命名为 `targetFilePath`。

**请求体**：
```json
{
  "name": "自动转码规则",
  "triggerEventType": "FILE_CLOSED",
  "roomIdFilter": 12345,
  "action": "BOTH",
  "recordingEngineId": 1,
  "recordingPath": "/录播/2024",
  "targetEngineId": 2,
  "targetFilePath": "/转码输出"
}
```

**校验规则**：
- `action = TRANSCODE_ONLY` 或 `BOTH` 时：`recordingEngineId` 和 `recordingPath` 必填
- `action = SYNC_ONLY` 时：`recordingEngineId` 和 `recordingPath` 可为空

---

#### GET /api/webhook-rules

列表查询。响应新增 `recordingEngineId`、`recordingEngineName`、`recordingPath` 字段，`targetPath` 重命名为 `targetFilePath`。

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "自动转码规则",
      "triggerEventType": "FILE_CLOSED",
      "roomIdFilter": 12345,
      "action": "BOTH",
      "recordingEngineId": 1,
      "recordingEngineName": "录播AList",
      "recordingPath": "/录播/2024",
      "targetEngineId": 2,
      "targetEngineName": "存储AList",
      "targetFilePath": "/转码输出",
      "enabled": true,
      "createdAt": "2026-06-20T10:00:00"
    }
  ]
}
```

---

### Webhook 地址（新增端点）

#### GET /api/webhooks/address

获取录播姬 Webhook V2 的完整 URL。

**响应**：`ApiResult<String>`

```json
{
  "code": 200,
  "message": "success",
  "data": "http://192.168.1.100:8080/api/webhooks/recorder"
}
```

**行为**：
- 优先使用 `app.server-address` 配置
- 未配置时使用当前请求的 origin

---

## 前端组件契约

### DirectoryTreeSelector

**Props**：
```typescript
interface DirectoryTreeSelectorProps {
  engineId: number;           // 存储引擎 ID
  value?: string;             // 当前选中路径
  onChange: (path: string) => void;  // 路径选择回调
  placeholder?: string;
}
```

**行为**：
- 点击浏览按钮展开树面板
- 点击目录节点实时加载子目录（调用 `/api/storage-engines/{id}/directories?path=xxx`）
- 仅显示目录，不显示文件
- 选中路径自动回填到输入框

### CronBuilder

**Props**：
```typescript
interface CronBuilderProps {
  value: string;              // 当前 Cron 表达式
  onChange: (expression: string) => void;  // 表达式变更回调
}
```

**行为**：
- 五个字段独立选择器（分钟/小时/日/月/周）
- 每字段支持：每(*)、指定值、范围、步进
- 预设快捷模式按钮组
- 实时预览中文描述和下次执行时间
- 与手动输入模式双向同步

---

## DTO 变更汇总

### StorageEngineVO（修改）

```typescript
interface StorageEngineVO {
  id: number;
  name: string;
  engineType: 'ALIST' | 'LOCAL';    // 新增
  baseUrl: string | null;            // 可为 null
  localPath: string | null;          // 新增
  status: 'ONLINE' | 'OFFLINE' | 'ERROR';
  createdAt: string;
  updatedAt: string;
  // 移除: username
}
```

### StorageEngineCreateDTO（修改）

```typescript
interface StorageEngineCreateDTO {
  name: string;
  engineType: 'ALIST' | 'LOCAL';    // 新增
  baseUrl?: string;                   // ALIST 必填
  token?: string;                     // ALIST 必填
  localPath?: string;                 // LOCAL 必填
  // 移除: username
}
```

### TranscodeStatus（修改）

```typescript
type TranscodeStatus =
  | 'PENDING'
  | 'DOWNLOADING'        // 新增
  | 'DOWNLOAD_FAILED'    // 新增
  | 'TRANSCODING'
  | 'TRANSCODE_FAILED'   // 新增
  | 'UPLOADING'
  | 'UPLOAD_FAILED'      // 新增
  | 'COMPLETED';
  // 移除: SCANNING, FAILED
```

### TranscodeTaskVO（修改）

```typescript
interface TranscodeTaskVO {
  id: number;
  sourceFilePath: string;
  targetFilePath: string;
  sourceFormat?: string;
  targetFormat: TargetFormat;
  bitrate: number | null;       // 改为可选
  progressPercent: number;
  status: TranscodeStatus;
  canRetry: boolean;            // 新增
  errorMessage?: string;
  createdAt: string;
  // 移除: tempFilePath（内部实现细节，不暴露给前端）
}
```

### WebhookRuleVO（修改）

```typescript
interface WebhookRuleVO {
  id: number;
  name: string;
  triggerEventType: WebhookEventType;
  roomIdFilter?: number;
  action: RuleAction;
  recordingEngineId?: number;       // 新增
  recordingEngineName?: string;     // 新增
  recordingPath?: string;           // 新增
  targetEngineId: number;
  targetEngineName: string;
  targetFilePath: string;           // 原 targetPath
  enabled: boolean;
  createdAt: string;
}
```

### WebhookRuleCreateDTO（修改）

```typescript
interface WebhookRuleCreateDTO {
  name: string;
  triggerEventType: WebhookEventType;
  roomIdFilter?: number;
  action: RuleAction;
  recordingEngineId?: number;       // 新增
  recordingPath?: string;           // 新增
  targetEngineId: number;
  targetFilePath: string;           // 原 targetPath
}
```

### DirectoryEntryVO（新增）

```typescript
interface DirectoryEntryVO {
  name: string;
  path: string;
  hasChildren: boolean;
}
```
