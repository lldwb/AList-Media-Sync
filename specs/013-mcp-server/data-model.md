# 数据模型：MCP 服务器（AI 操作接口）

**功能**：`013-mcp-server` | **阶段**：1（设计） | **日期**：2026-08-16

**说明**：本功能为 MCP 接入层，**不引入新业务实体**（不修改现有 Entity 类，不新增数据库表）。本数据模型聚焦 MCP 侧的"实体"：配置实体（`app.mcp.*`）、Bearer Token 认证实体、MCP 工具契约（工具名/参数/返回结构/Service 映射）。业务实体（SyncTask、TranscodeTask、StorageEngine、WebhookRule 等）的状态与字段作为工具操作的领域对象，此处仅引用其 DTO 字段，不重复定义。

---

## 1. MCP 配置实体

### 1.1 `app.mcp.*` 配置项（`AppProperties.Mcp` 内部类）

| 字段 | 类型 | 默认值 | 环境变量 | 说明 |
|------|------|--------|---------|------|
| enabled | boolean | `false` | `MCP_ENABLED` | MCP 服务器总开关，默认禁用（FR-012）；为 true 且 token 非空时 MCP bean 才装配 |
| token | String | 空 | `MCP_TOKEN` | MCP 专用访问令牌（Bearer Token），与 Web 管理 Basic Auth 凭据隔离（FR-008）；空值时强制不可用 |

**验证规则**：
- `enabled=true` 且 `token` 为空/空白 → 启动报错并拒绝启用 MCP（防止接口裸奔，FR-012）
- `enabled=false`（默认）→ MCP 端点 `/mcp` 不可用，现有 Web 管理界面与 `/api/**` 完全不受影响（SC-006）
- token 属敏感凭据：日志、诊断包、配置摘要 MUST NOT 打印其值（原则 VII §7.6）

### 1.2 Spring AI MCP 关联配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `spring.ai.mcp.server.streamable-http.mcp-endpoint` | `/mcp` | MCP 端点路径（R6），同端口嵌入 |
| `spring.ai.mcp.server.annotation-scanner.enabled` | `true` | `@McpTool` 注解自动扫描（R1），随 `app.mcp.enabled` 联动生效 |
| `spring.ai.mcp.server.enabled` | 派生自 `app.mcp.enabled` | 与总开关保持一致（R4） |

---

## 2. Bearer Token 认证实体

### 2.1 `McpAuthInterceptor` 认证规则

| 维度 | 规则 |
|------|------|
| 请求头 | `Authorization: Bearer <token>` |
| 校验 | `app.mcp.token` 恒定时间比较（避免时序侧信道） |
| 匹配路径 | `/mcp`（`WebMvcConfig.addInterceptors` 精确注册，独立于 `/api/**` 的 `AuthInterceptor`） |
| 成功 | 放行至 MCP 端点，`X-Trace-Id` 由 `TraceIdFilter` 统一注入 |
| 失败 | HTTP 401 + 统一错误结构（`code:401, message:..."`），MUST NOT 泄露业务数据（SC-004） |
| 放行 | MCP 握手期 `initialize` 等协议请求同样受认证约束（认证先于协议处理） |

**验证规则**：缺失令牌、错误令牌、非 Bearer 格式三种情况 MUST 均返回 401；正确令牌 MUST 放行。

---

## 3. MCP 工具契约

工具按模块分 5 个工具类，操作级细粒度为主 + 流程级快捷工具（FR-013 混合模式）。工具名称使用英文 snake_case（MCP 客户端可读性），描述与参数说明使用简体中文（原则 IV）。

### 3.1 存储引擎模块（`StorageEngineMcpTools`，注入 `StorageEngineService`）

| 工具名 | 参数 | 返回 | 映射 Service 方法 |
|--------|------|------|------------------|
| `storage_engine_list` | 无 | `List<StorageEngineVO>` | `listAll()` |
| `storage_engine_get` | `id: long` | `StorageEngineVO` | `getById(id)` |
| `storage_engine_create` | `name`, `engineType`(ALIST/LOCAL), `baseUrl?`, `token?`, `localPath?` | `StorageEngineVO` | `create(StorageEngineCreateDTO)` |
| `storage_engine_update` | `id`, `name?`, `baseUrl?`, `token?`, `localPath?` | `StorageEngineVO` | `update(id, StorageEngineUpdateDTO)` |
| `storage_engine_delete` | `id` | `{deleted: true}` | `delete(id)` |
| `storage_engine_test_connection` | `id` | `{connected: boolean}` | `testConnection(id)` |
| `storage_engine_list_directories` | `id`, `path?`(默认 `/`) | `List<DirectoryEntryVO>` | `resolve(engine).listDirectories(...)` |
| `storage_engine_list_entries` | `id`, `path?`(默认 `/`) | `List<FileEntry>` | `resolve(engine).listEntries(...)` |

**脱敏规则**：`token` 参数用于存储（Service 层 AES 加密落库），工具返回结构 MUST NOT 回显明文 token；返回复用 `StorageEngineVO`（VO 层已对 token 脱敏）（FR-011）。

### 3.2 同步任务模块（`SyncTaskMcpTools`，注入 `SyncTaskManageService` + `SyncService`）

| 工具名 | 参数 | 返回 | 映射 Service 方法 |
|--------|------|------|------------------|
| `sync_task_list` | 无 | `List<SyncTaskVO>` | `manageService.listAll()` |
| `sync_task_get` | `id` | `SyncTaskVO` | `manageService.getById(id)` |
| `sync_task_create` | `name`, `sourceEngineId`, `targetEngineId`, `sourcePath`, `targetPath`, `syncMode?`(NEW_ONLY), `transcodeEnabled?`, `targetFormat?`(MP3), `conflictStrategy?`(SKIP), `excludePatterns?`, `scheduleType?`(MANUAL), `cronExpression?`, `intervalSeconds?` | `SyncTaskVO` | `manageService.create(SyncTaskCreateDTO)` |
| `sync_task_update` | `id`, 同上可选字段 | `SyncTaskVO` | `manageService.update(id, SyncTaskUpdateDTO)` |
| `sync_task_delete` | `id` | `{deleted: true}` | `manageService.delete(id)` |
| `sync_task_execute` | `id` | `{taskId: long}` | `manageService.executeManually(id)` + `syncService.executeSyncTask(...)`（异步，FR-014） |
| `sync_task_enable` | `id` | `SyncTaskVO` | `manageService.enable(id)` |
| `sync_task_disable` | `id` | `SyncTaskVO` | `manageService.disable(id)` |
| `sync_task_get_executions` | `id` | `List<TaskExecutionVO>` | `manageService.getExecutions(id)` |

### 3.3 转码任务模块（`TranscodeTaskMcpTools`，注入 `TranscodeService` + `CleanupService`）

| 工具名 | 参数 | 返回 | 映射 Service 方法 |
|--------|------|------|------------------|
| `transcode_task_list` | 无 | `List<TranscodeTaskVO>` | `listAll()` |
| `transcode_task_get` | `id` | `TranscodeTaskVO`（含实时进度） | `getById(id)` |
| `transcode_task_create` | `sourceFilePath`, `targetFilePath?`, `targetFormat`(MP3/MP4/FLV), `bitrate?`, `sourceEngineId?`, `targetEngineId?`, `sourceDirectoryTranscode?` | `TranscodeTaskVO` | `createTask(...)` + `executeAsync(...)`（异步，FR-014） |
| `transcode_task_retry` | `id` | `{taskId, success}` | `retry(id)` |
| `transcode_task_cleanup_temp` | 无 | `{deletedCount: long}` | `cleanupService.manualCleanup()` |
| `transcode_task_delete_failed` | 无 | `{deletedCount: long}` | `deleteByStatusIn(FAILED...)` |
| `transcode_task_delete_completed` | 无 | `{deletedCount: long}` | `deleteByStatusIn(COMPLETED)` |
| `transcode_task_retry_all` | 无 | `{submittedCount: int}` | `findByStatusIn(FAILED...)` + 逐个 `retry(id)`（异步） |

### 3.4 Webhook 模块（`WebhookMcpTools`，注入 `WebhookRuleService` + `WebhookService`）

| 工具名 | 参数 | 返回 | 映射 Service 方法 |
|--------|------|------|------------------|
| `webhook_rule_list` | 无 | `List<WebhookRuleVO>` | `ruleService.listAll()` |
| `webhook_rule_get` | `id` | `WebhookRuleVO` | `ruleService.getById(id)` |
| `webhook_rule_create` | `name`, `triggerEventType`(RECORDING_COMPLETED 等), `roomIdFilter?`, `action`(BOTH), `recordingEngineId?`, `recordingPath?`, `targetEngineId?`, `targetFilePath?` | `WebhookRuleVO` | `ruleService.create(WebhookRuleCreateDTO)` |
| `webhook_rule_update` | `id`, 同上可选字段 | `WebhookRuleVO` | `ruleService.update(id, ...)` |
| `webhook_rule_delete` | `id` | `{deleted: true}` | `ruleService.delete(id)` |
| `webhook_rule_enable` | `id` | `WebhookRuleVO` | `ruleService.enable(id)` |
| `webhook_rule_disable` | `id` | `WebhookRuleVO` | `ruleService.disable(id)` |
| `webhook_event_list` | `page?`(1), `size?`(20) | `List<WebhookEventVO>` | `webhookService.listEvents(page, size)` |

**范围边界**：MUST NOT 提供事件注入/模拟发送工具（FR-006，2026-08-16 澄清），事件仅由外部系统真实触发。

### 3.5 系统运维模块（`SystemMcpTools`，注入 `DashboardService` + `DiagnosticService`）

| 工具名 | 参数 | 返回 | 映射 Service 方法 |
|--------|------|------|------------------|
| `system_dashboard_stats` | 无 | `DashboardStatsVO` | `dashboardService.getStats()` |
| `system_run_diagnostics` | 无 | `DiagnosticResultVO` | `diagnosticService.generate()`（只读，无业务副作用） |

### 3.6 流程级快捷工具（`SyncFlowMcpTools`，FR-013 混合模式的流程级补充）

| 工具名 | 参数 | 返回 | 组合逻辑 |
|--------|------|------|---------|
| `sync_flow_create_and_execute` | 同 `sync_task_create` 参数 | `{taskId: long}` | 一次性完成"创建同步任务 → 立即手动触发执行"（封装 create + execute 两步，供常见场景一键完成） |

> 流程级工具在实现阶段可按需补充（如转码"创建并跟踪"），本规格以至少 1 个流程级工具满足 SC-002。

---

## 4. 敏感字段脱敏规则（FR-011）

| 场景 | 规则 |
|------|------|
| 工具返回结构 | 复用现有 VO/DTO（StorageEngineVO 等已在 VO 层对 token 脱敏），MUST NOT 回显明文凭据 |
| 创建/更新工具入参 | 接收明文 token（仅用于 Service 存储，AES 加密落库），调用日志 MUST 脱敏（`token=***`，原则 VII §7.6） |
| 工具调用日志 | 含敏感关键字（token/password/secret/key/auth/cookie）的字段值替换为 `***` 或长度提示 |

---

## 5. 实体关系图

```
┌────────────────────┐     装配（@ConditionalOnProperty）
│  AppProperties.Mcp  │─────app.mcp.enabled=true 且 token 非空──────┐
│  (enabled/token)    │                                            v
└────────────────────┘                                    ┌──────────────────────┐
                                                          │  McpConfig            │
                    /mcp 请求                              │  (注册 MCP 组件)      │
  AI 客户端 ────────Authorization: Bearer <token>───────►│                      │
   (Claude Code)                                          └──────────┬───────────┘
                                                              Bearer 校验（McpAuthInterceptor）
                                                             通过 ──►  MCP Server（/mcp）
                                                                          │ tools/list / tools/call
                            ┌────────────┬──────────────┬───────────────┼──────────────┬─────────────┐
                            v            v              v               v              v
                     StorageEngine  SyncTask      TranscodeTask    WebhookRule   System
                     McpTools      McpTools       McpTools          McpTools      McpTools
                            │            │              │               │              │
                            └────────────┴──────┬───────┴───────────────┴──────────────┘
                                                 v
                                      现有 Service 层（复用）
                              （TraceContext 注入 traceId/module/operation）
```

---

## 6. 验证规则汇总

| 规则 | 适用实体 | 来源 |
|------|---------|------|
| `app.mcp.enabled=true` 且 token 非空才生效，否则拒绝启用 | AppProperties.Mcp | FR-012、R4 |
| token 恒定时间比较，401 不泄露业务数据 | Bearer Token 认证 | FR-008、SC-004、R3 |
| 工具层复用 Service，禁止直调 Repository | MCP 工具契约 | FR-009、原则 I |
| 每次工具调用注入唯一 traceId/module/operation | MCP 工具调用 | FR-010、原则 VII §7.3 |
| 敏感凭据脱敏覆盖率 100% | 工具返回/日志 | FR-011、SC-005、原则 VII §7.6 |
| 长任务工具异步提交立即返回任务 ID | sync/transcode 工具 | FR-014、R7 |
| 不提供事件注入工具 | Webhook 模块 | FR-006、2026-08-16 澄清 |
| 工具名英文 snake_case、描述中文 | MCP 工具契约 | 原则 IV、R5 |
| 默认禁用不影响现有 Web 管理/API | app.mcp.enabled | FR-012、SC-006 |
