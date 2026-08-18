<!-- 派生产物声明：本文件手工引导区由人工维护，生成区由 scripts/gen-api-doc 自动生成 -->
# API 接口文档

> **双区结构说明**：本文件采用"手工引导区 + 生成区"双区结构（见 `specs/011-docs-system-optimization/contracts/api-doc-generation-contract.md` §3.4）。
> - **手工引导区**（本锚点之前）：由人工维护，包含认证方式、统一响应格式、WebSocket 端点、错误码清单、Swagger UI 访问方式等通用约定。
> - **生成区**（`<!-- GENERATED START -->` 与 `<!-- GENERATED END -->` 之间）：由 `scripts/gen-api-doc` 自动生成，请勿手工编辑。

---

## 1. 认证方式

### 1.1 HTTP Basic Auth

所有 `/api/**` 端点均受 `AuthInterceptor` 保护，采用 HTTP Basic 认证。客户端需在请求头中携带 `Authorization: Basic <base64(username:password)>`。

凭据来源于 `application.yaml` 中的 `app.auth.username` 与 `app.auth.password` 配置项（可通过环境变量 `APP_AUTH_USERNAME`、`APP_AUTH_PASSWORD` 覆盖）。密码在启动时由 `PasswordEncryptionPostProcessor` 使用随机盐值进行 BCrypt 加密，仅保存在内存中。

**请求示例**：

```http
GET /api/sync-tasks HTTP/1.1
Authorization: Basic YWRtaW46YWRtaW4xMjM=
```

其中 `YWRtaW46YWRtaW4xMjM=` 是 `admin:admin123` 的 Base64 编码（仅示例，生产环境请使用强密码）。

### 1.2 放行路径

以下路径不受 `AuthInterceptor` 保护，无需认证即可访问：

| 路径 | 说明 |
|---|---|
| `/api/webhooks/**` | Webhook 回调入口，由 Webhook 签名机制独立校验 |
| `/actuator/health` | 健康检查端点，供容器编排探针使用 |
| `/v3/api-docs**` | OpenAPI JSON/YAML 文档端点（生产环境通过 `SPRINGDOC_API_DOCS_ENABLED=false` 禁用） |
| `/swagger-ui**` | Swagger UI 界面（生产环境通过 `SPRINGDOC_SWAGGER_UI_ENABLED=false` 禁用） |
| `/h2-console/**` | H2 数据库控制台（仅开发调试用） |

### 1.3 生产环境访问控制

SpringDoc 端点（`/v3/api-docs`、`/swagger-ui`）始终被 `AuthInterceptor` 放行，生产环境通过以下环境变量禁用端点本身实现访问控制：

```bash
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

> 详见 [04-配置说明.md](04-配置说明.md#5-springdoc-openapi-配置)。

---

## 2. 统一响应格式

所有 REST API 端点均返回 `ApiResult<T>` 格式的 JSON 响应，确保前端能统一处理成功和错误两种情况。

### 2.1 响应结构

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

| 字段 | 类型 | 是否必返回 | 说明 |
|---|---|---|---|
| `code` | `int` | 是 | HTTP 状态码。成功为 `200`，错误为 `4xx` 或 `5xx`。 |
| `message` | `string` | 是 | 提示消息（成功或错误描述）。 |
| `data` | `T` | 否 | 响应数据。成功时返回，可能为 `null`。`@JsonInclude(NON_NULL)` 确保值为 `null` 时字段不序列化。 |
| `traceId` | `string` | 否 | 链路追踪 ID。由 MDC 注入，用于跨日志关联请求链路。 |

### 2.2 成功响应示例

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "每日同步"
  }
}
```

### 2.3 错误响应示例

```json
{
  "code": 401,
  "message": "用户名或密码错误"
}
```

---

## 3. WebSocket 端点

### 3.1 端点地址

```
ws://localhost:8080/ws/events
```

WebSocket 端点为 `/ws/events`，由 `WebSocketConfig` 注册，使用 Spring 原始 WebSocket 支持（不使用 STOMP，遵循 YAGNI 原则）。

### 3.2 认证方式

WebSocket 在 HTTP Upgrade 握手阶段进行认证，由 `WebSocketAuthInterceptor` 拦截。认证方式与 REST API 一致，使用 HTTP Basic Auth：

```javascript
const ws = new WebSocket("ws://localhost:8080/ws/events", [], {
  headers: { Authorization: "Basic " + base64("admin:admin123") }
});
```

浏览器原生 `WebSocket` API 不支持自定义请求头，可通过 URL 查询参数传递 token 或使用第三方库（如 `ws` 包）携带 `Authorization` 头。认证失败时返回 HTTP 401，拒绝 WebSocket 升级请求。

### 3.3 连接数限制

最大并发连接数由 `app.websocket.max-connections` 控制（默认 `50`，可通过环境变量 `APP_WEBSOCKET_MAX_CONNECTIONS` 覆盖）。超过上限时拒绝新连接。

### 3.4 消息格式

WebSocket 消息为 JSON 格式，包含 `type` 字段标识消息类型，前端根据 `type` 路由到不同的状态更新逻辑。

### 3.5 MessageType 枚举

| 枚举值 | 说明 |
|---|---|
| `SYNC_PROGRESS` | 同步任务进度变更 |
| `TRANSCODE_PROGRESS` | 转码任务进度变更 |
| `TASK_EVENT` | 任务事件（创建/删除/完成） |
| `WEBHOOK_EVENT` | Webhook 事件接收/处理状态变更 |
| `DASHBOARD_UPDATE` | 仪表板统计数据变更（2 秒防抖合并） |

---

## 4. 错误码清单

| 错误码 | 含义 | 触发场景 |
|---|---|---|
| `200` | 成功 | 请求处理成功 |
| `400` | 参数错误 | 请求参数校验失败（`@Valid` 校验不通过）、请求体格式错误 |
| `401` | 未认证 | 缺少 `Authorization` 头、认证凭据格式无效、用户名或密码错误 |
| `404` | 资源不存在 | 请求的资源 ID 不存在 |
| `429` | 请求过多 | WebSocket 连接数超过上限 |
| `500` | 服务器错误 | 服务端内部异常 |

> 错误响应统一使用 `ApiResult.error(code, message)` 格式返回，`data` 字段可能携带错误详情。

---

## 5. Swagger UI 访问

### 5.1 开发环境

| 资源 | 地址 |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| OpenAPI YAML | http://localhost:8080/v3/api-docs.yaml |

### 5.2 静态导出

通过 Maven Profile `gen-api-doc` 静态导出 OpenAPI 规范文件，用于 CI 文档生成与一致性校验：

```bash
./mvnw verify -Pgen-api-doc
```

输出文件：`target/openapi.json` 与 `target/openapi.yaml`。

### 5.3 生产环境

生产环境通过环境变量禁用 SpringDoc 端点：

```bash
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

---

## 6. MCP 服务器接入（AI 操作接口）

> MCP（Model Context Protocol）服务器为 AI 客户端（如 Claude Code）提供标准化的工具调用入口，覆盖存储引擎、同步任务、转码任务、Webhook、系统运维五大模块共 36 个工具。详细契约见 `specs/013-mcp-server/contracts/`。

### 6.1 启用条件

MCP 服务器**默认禁用**，需同时满足以下条件才提供服务：

| 条件 | 配置 | 默认值 |
|---|---|---|
| 总开关 | `app.mcp.enabled=true`（环境变量 `MCP_ENABLED=true`） | `false` |
| 访问令牌 | `app.mcp.token` 非空（环境变量 `MCP_TOKEN`） | 空 |

`enabled=true` 且令牌为空时应用**启动报错拒绝启用**，防止接口裸奔。

### 6.2 端点与认证

| 维度 | 契约 |
|---|---|
| 端点 | `POST /mcp`（与主应用同端口，Streamable HTTP 传输） |
| 认证 | `Authorization: Bearer <MCP_TOKEN>`，与 Web 管理 Basic Auth 凭据隔离 |
| 认证失败 | HTTP 401 + 统一错误结构，不泄露业务数据 |

### 6.3 客户端接入示例（Claude Code）

```jsonc
// .mcp.json
{
  "mcpServers": {
    "alist-media-sync": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": { "Authorization": "Bearer <MCP_TOKEN>" }
    }
  }
}
```

### 6.4 工具总览（36 个）

| 模块 | 工具前缀 | 数量 | 覆盖操作 |
|---|---|---|---|
| 存储引擎 | `storage_engine_*` | 8 | 列表/详情/创建/更新/删除/连接测试/目录浏览/条目浏览 |
| 同步任务 | `sync_task_*` | 9 | 列表/详情/创建/更新/删除/触发/启停调度/执行历史 |
| 转码任务 | `transcode_task_*` | 8 | 列表/详情/创建/重试/清理临时/批量删失败/批量删完成/重试全部 |
| Webhook | `webhook_rule_*` + `webhook_event_*` | 8 | 规则 CRUD/启停 + 事件分页查询 |
| 系统运维 | `system_*` | 2 | 仪表盘统计/诊断包生成 |
| 流程级快捷 | `sync_flow_*` | 1 | 创建并立即触发一次同步 |

> 工具名称使用英文 snake_case，描述为简体中文；触发长任务的工具采用异步提交（立即返回任务 ID）；Webhook 不提供事件注入工具。

---

<!-- GENERATED START -->
<!-- 生成区由 scripts/gen-api-doc 自动填充，首次执行 T022 后生效 -->
<!-- GENERATED END -->
