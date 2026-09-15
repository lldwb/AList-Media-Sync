# webhook 模块 — Webhook 事件处理

> 本文档为 US4 架构设计细化文档之一，描述 webhook 模块的职责边界、核心类、关键流程与扩展点。总览见 [03-架构设计.md](../03-架构设计.md)。

## 职责边界

webhook 模块负责接收录播姬 Webhook v2 事件，通过规则匹配自动触发同步或转码操作。核心职责包括：

- **事件接收**：接收录播姬 Webhook v2 事件（仅 `POST /api/webhooks/recorder` 免认证）
- **事件去重**：基于 EventId 幂等去重，防止重复处理；并发重发由唯一索引兜底（捕获 `DataIntegrityViolationException` 后重查返回既有事件）
- **规则匹配**：按事件类型和元数据匹配预配置规则
- **任务触发**：根据规则动作触发同步和/或转码任务
- **事件管理**：规则 CRUD、事件历史查询

webhook 模块是系统的外部事件入口，调用 sync 和 transcode 模块触发任务执行。遵循章程原则 I（分层架构）和 III（RESTful API 契约）。

## 核心类

### WebhookController — 事件接收端点

- `POST /api/webhooks/recorder` — 接收录播姬 Webhook v2 事件（录播姬回调，免认证）
- **无认证路径**：在 `AuthInterceptor` 中仅排除 `/api/webhooks/recorder`（精确前缀）；同前缀下的管理接口（如 `GET /api/webhooks/events` 事件查询）仍需 Basic Auth
- 解析 Webhook v2 事件载荷，委托 `WebhookService` 处理

### WebhookService — 事件处理核心

异步处理流程：

1. **事件去重**：基于 EventId 检查是否已处理过，已存在则跳过（WARN 级别日志记录命中去重）
2. **事件持久化**：将事件存入 `WebhookEvent` 实体
3. **规则匹配**：遍历所有启用的规则，按事件类型和元数据进行匹配
4. **执行动作**：根据匹配规则的 `action` 触发对应任务
5. **异步处理**：事件处理通过 `@Async` 异步执行，快速返回响应给录播姬

### WebhookRuleService — 规则管理

- 规则 CRUD 逻辑
- 规则启用/禁用
- 规则匹配条件校验

### WebhookRuleController — 规则管理 API

RESTful 端点：
- `GET /api/webhooks/rules` — 规则列表
- `POST /api/webhooks/rules` — 创建规则
- `PUT /api/webhooks/rules/{id}` — 更新规则
- `DELETE /api/webhooks/rules/{id}` — 删除规则

### WebhookEventController — 事件查询 API

- `GET /api/webhooks/events` — 事件历史列表
- `GET /api/webhooks/events/{id}` — 事件详情

### 实体与 DTO

| 类 | 职责 |
|---|------|
| `WebhookEvent` | 事件实体。WebhookEventType（事件类型枚举）、EventStatus（PENDING / MATCHED / PROCESSED / SKIPPED / FAILED） |
| `WebhookRule` | 规则实体，含 `@Version` 乐观锁。action：SYNC_ONLY / TRANSCODE_ONLY / BOTH |
| `WebhookRuleCreateDTO` | 创建规则请求 DTO |
| `WebhookRuleVO` | 规则视图 VO |
| `WebhookEventVO` | 事件视图 VO |

### Repository

| 类 | 职责 |
|---|------|
| `WebhookRuleRepository` | Spring Data JPA 接口，含 `findByEnabledTrue` 等派生查询 |
| `WebhookEventRepository` | 含 `findByEventId` 用于去重查询 |

## 关键流程

### 事件接收流程

1. 录播姬发送 Webhook v2 事件到 `POST /api/webhooks/events`
2. `WebhookController` 解析事件载荷，提取 EventId、事件类型、元数据
3. 委托 `WebhookService` 异步处理
4. 立即返回 200 OK（不等处理完成，避免录播姬超时重试）

### EventId 去重流程

1. 从事件载荷提取 EventId
2. 查询 `WebhookEventRepository.findByEventId(eventId)`
3. 已存在 → 记录 WARN 日志（"重复事件已跳过"），返回 SKIPPED 状态
4. 不存在 → 持久化事件，状态为 PENDING，继续处理

### 规则匹配流程

1. 查询所有 `enabled=true` 的规则
2. 逐条匹配事件类型与规则条件
3. 匹配成功 → 记录匹配的规则，事件状态更新为 MATCHED
4. 无匹配规则 → 事件状态更新为 SKIPPED
5. 多条规则匹配 → 按优先级或顺序执行

### 任务触发流程

根据匹配规则的 `action` 字段：

- **SYNC_ONLY**：经 `SyncTaskManageService.createWebhookTempTask(...)` 构造并持久化「临时同步任务」（`enabled` 保持默认 `false`，不注册调度），再在事务提交后调用 `SyncService.executeSyncTask` 触发执行
- **TRANSCODE_ONLY**：调用 `TranscodeService.createTask` + `executeAsync` 创建并触发转码任务
- **BOTH**：走 SYNC_ONLY 路径建临时任务，并以 `transcodeEnabled=true` + `TargetFormat.MP3` 提交；同步成功后再经 `PostSyncTranscodeTrigger` 触发后置转码

> 临时同步任务的构造与持久化统一收归 `SyncTaskManageService`，避免 Webhook 模块绕过 Service 层直接写入 `sync_task` 表。

每次规则动作都会创建一条 `TaskExecution` 记录（`execution/` 模块）记录动作结果，并在结束时写入 SUCCESS 或 FAILED。

## 扩展点

- **新增规则动作**：在 `WebhookRule.action` 枚举中添加值，在 `WebhookService` 中实现对应触发逻辑
- **新增匹配条件**：在规则实体中添加条件字段，在匹配逻辑中实现对应判断
- **新增事件类型**：在 `WebhookEventType` 枚举中添加值，对应录播姬新事件类型

## 关联 spec

- `specs/001-alist-media-sync/` — 核心 Webhook 业务
- `md/danmuji/AGENTS.md` — 录播姬 Webhook v2 协议参考（事件矩阵、幂等约束）
- `.specify/memory/constitution.md` §I（分层架构）、§II（数据完整性 — EventId 幂等去重）、§III（RESTful API 契约）、§VII（日志规范 — 去重命中 WARN 日志）
