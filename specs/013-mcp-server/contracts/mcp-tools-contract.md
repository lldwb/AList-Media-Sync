# MCP 工具契约：AList-Media-Sync

**功能**：`013-mcp-server` | **阶段**：1（设计） | **日期**：2026-08-16

**用途**：定义 AI 客户端通过 MCP 服务器调用本系统工具时的协议契约。工具清单与参数详情见 [data-model.md](../data-model.md) 第 3 节，此处定义调用约定、错误结构与工具总览。

---

## 1. 传输与协议

| 维度 | 契约 |
|------|------|
| 传输 | Streamable HTTP（MCP 2025-03-26 规范） |
| 端点 | `POST /mcp`（同端口，与 Web 管理界面共享） |
| 协议消息 | JSON-RPC 2.0（`initialize`、`tools/list`、`tools/call` 等标准方法） |
| 认证 | `Authorization: Bearer <token>`（见 [mcp-auth-contract.md](./mcp-auth-contract.md)） |
| 内容类型 | `application/json` |
| 启用条件 | `app.mcp.enabled=true` 且 `app.mcp.token` 非空（见 [mcp-config-contract.md](./mcp-config-contract.md)） |

## 2. 工具总览

工具按 5 个模块分组，共 35 个操作级工具 + 1 个流程级快捷工具（FR-013 混合模式）。

| 模块 | 工具类 | 工具数 | 覆盖操作 |
|------|--------|--------|---------|
| 存储引擎 | `storage_engine_*` | 8 | 列表/详情/创建/更新/删除/连接测试/目录浏览/条目浏览 |
| 同步任务 | `sync_task_*` | 9 | 列表/详情/创建/更新/删除/触发/启停调度/执行历史 |
| 转码任务 | `transcode_task_*` | 8 | 列表/详情/创建/重试/清理临时/批量删失败/批量删完成/重试全部 |
| Webhook | `webhook_rule_*` + `webhook_event_*` | 8 | 规则列表/详情/创建/更新/删除/启停 + 事件分页查询 |
| 系统运维 | `system_*` | 2 | 仪表盘统计/诊断包生成 |
| 流程级 | `sync_flow_*` | 1 | 创建并立即触发一次同步（快捷工具） |

> **范围边界**：不提供 Webhook 事件注入/模拟发送工具（FR-006）。完整参数与返回结构见 [data-model.md](../data-model.md) 第 3 节。

## 3. 工具调用约定

- **工具名称**：英文 snake_case（如 `sync_task_create`），唯一且稳定
- **参数**：工具入参为扁平键值对（`@McpToolParam`），可选参数带默认值，与服务端 DTO 字段一一映射
- **返回**：工具返回结构化 JSON，与现有 Service VO/DTO 字段对齐（脱敏后）
- **异步提交**：触发长任务的工具（`sync_task_execute`、`transcode_task_create`、`transcode_task_retry_all`）立即返回任务 ID/提交数，结果通过查询工具（`sync_task_get_executions`、`transcode_task_get`）轮询（FR-014）
- **敏感字段**：入参 token 仅用于存储，返回与日志 MUST NOT 回显明文凭据（FR-011）

## 4. 错误结构（FR-015）

工具调用失败时返回统一错误，便于 AI 理解并修正参数后重试：

```json
{
  "isError": true,
  "content": [
    {
      "type": "text",
      "text": "{\"code\": <业务码>, \"message\": \"<中文错误描述>\", \"data\": <可选上下文>}"
    }
  ]
}
```

| 场景 | 业务码 | message 示例 |
|------|--------|-------------|
| 参数校验失败 | 400 | "源存储引擎不能为空" |
| 资源不存在 | 404 | "同步任务不存在：id=99" |
| 业务冲突（如任务运行中） | 409 | "同步任务正在执行，无法重复触发" |
| 认证失败 | 401 | 见 [mcp-auth-contract.md](./mcp-auth-contract.md) |
| 内部错误 | 500 | "同步任务创建失败：<原因>" |

> 错误 message 使用简体中文（原则 IV），字段名为英文。错误响应 MUST 携带 `X-Trace-Id` 头（`TraceIdFilter` 统一注入）。

## 5. 一致性约束

- 工具行为与现有 `/api/**` 端点一致（同一 Service 层），不存在"Web 界面能做的操作 MCP 不能做"或反之的漂移
- 工具调用的数据完整性（乐观锁、事务、幂等）由复用 Service 层保证（原则 II）
- 工具发现 `tools/list` 返回的描述、参数 schema 与 [data-model.md](../data-model.md) 第 3 节一致
