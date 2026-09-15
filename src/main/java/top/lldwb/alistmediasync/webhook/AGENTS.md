# webhook/ — Webhook 模块

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和后端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

接收录播姬 Webhook v2 事件，通过规则匹配自动触发同步或转码操作。支持事件去重、异步处理和事件历史查询。

## 作用

- **WebhookController**：接收 Webhook v2 事件（无认证路径 `/api/webhooks/**`）
- **WebhookService**：事件接收 + 异步处理（EventId 去重 → 规则匹配 → 执行动作）
- **WebhookRuleService**：规则 CRUD 管理
- **WebhookRuleController** / **WebhookEventController**：规则和事件的查询 API
- **规则动作**：SYNC_ONLY（仅同步）/ TRANSCODE_ONLY（仅转码）/ BOTH（同步+转码）

## 模块关联

- 调用 **sync/** 模块：规则匹配后经 `SyncTaskManageService.createWebhookTempTask` 构造并持久化「临时同步任务」（`enabled` 保持默认 `false`，不注册调度），再在事务提交后调用 `SyncService.executeSyncTask` 触发执行
- 调用 **transcode/** 模块：规则匹配后经 `TranscodeService.createTask` + `executeAsync` 触发转码任务
- 依赖 **storage/** 模块：使用 `StorageEngine` 实体与 `StorageEngineRepository`（规则的源/目标引擎取自规则配置，文件操作由 sync / transcode 经策略接口代执行）
- 依赖 **execution/** 模块：`TaskExecution` 实体与 `TaskExecutionRepository` 记录 Webhook 动作的执行结果
- 依赖 **common/** 模块：DTO/VO、认证排除、`MapUtils`（事件原始参数取值）
- **注意**：Webhook 端点在 `AuthInterceptor` 中排除认证，直接暴露
