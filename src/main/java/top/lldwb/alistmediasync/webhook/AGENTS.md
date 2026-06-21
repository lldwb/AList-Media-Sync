# webhook/ — Webhook 模块

## 功能

接收录播姬 Webhook v2 事件，通过规则匹配自动触发同步或转码操作。支持事件去重、异步处理和事件历史查询。

## 作用

- **WebhookController**：接收 Webhook v2 事件（无认证路径 `/api/webhooks/**`）
- **WebhookService**：事件接收 + 异步处理（EventId 去重 → 规则匹配 → 执行动作）
- **WebhookRuleService**：规则 CRUD 管理
- **WebhookRuleController** / **WebhookEventController**：规则和事件的查询 API
- **规则动作**：SYNC_ONLY（仅同步）/ TRANSCODE_ONLY（仅转码）/ BOTH（同步+转码）

## 模块关联

- 调用 **sync/** 模块：规则匹配后触发同步任务
- 调用 **transcode/** 模块：规则匹配后触发转码任务
- 依赖 **storage/** 模块：通过策略接口操作文件
- 依赖 **common/** 模块：DTO/VO、认证排除
- **注意**：Webhook 端点在 `AuthInterceptor` 中排除认证，直接暴露
