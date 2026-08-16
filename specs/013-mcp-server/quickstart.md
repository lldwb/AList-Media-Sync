# 快速入门验证指南：MCP 服务器（AI 操作接口）

**功能**：`013-mcp-server` | **阶段**：1（设计） | **日期**：2026-08-16

**用途**：验证 MCP 服务器端到端可用——AI 客户端连接、认证、发现工具、调用工具完成真实业务操作。契约与工具详情见 [contracts/](./contracts/) 与 [data-model.md](./data-model.md)。

---

## 1. 前提条件

- 已完成 `013-mcp-server` 功能实现（后端构建通过，`mvn package` 成功）
- 已配置 MCP 开关与令牌（见 [mcp-config-contract.md](./contracts/mcp-config-contract.md)）
- 至少一个存储引擎存在（供同步/转码工具调用；可在 Web 管理界面或经 MCP 工具创建）

## 2. 启用与启动

1. 配置环境变量（或 `application.yaml`）：
   ```bash
   export MCP_ENABLED=true
   export MCP_TOKEN=<自定义强令牌，建议 32+ 字符随机串>
   ```
2. 启动应用：`./mvnw spring-boot:run`（Windows：`mvnw.cmd`）
3. 确认启动日志输出：`MCP 服务器已启用：/mcp`（WARN 级别，token 仅标注"已配置"不打印值）

## 3. 场景 1 — 认证与工具发现

**目标**：验证 `/mcp` 可达、认证生效、36 个工具可被发现（SC-002）。

**命令**（curl 模拟 MCP 协议）：
```bash
# 1) 未认证 → 401，不泄露业务数据（SC-004）
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
# 预期：401

# 2) 带令牌 → 认证通过，返回 MCP 能力（tools/list 列出 36 个工具）
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $MCP_TOKEN" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' | head -c 2000
# 预期：result.tools 数组，含 storage_engine_*/sync_task_*/transcode_task_*/webhook_*/system_*/sync_flow_* 共 36 项
```

**预期结果**：未认证 401；认证后 `tools/list` 返回全部 36 个工具，名称/描述/参数 schema 与 [data-model.md](./data-model.md) 第 3 节一致。

## 4. 场景 2 — AI 客户端接入（Claude Code）

**目标**：真实 AI 客户端连接并调用工具（SC-001，5 分钟内完成）。

**配置** `.mcp.json`（详见 [mcp-auth-contract.md](./contracts/mcp-auth-contract.md)）：
```jsonc
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

**验证对话**：
> 用户：列出所有存储引擎
> AI：调用 `storage_engine_list` → 返回引擎列表（名称/类型/状态）

> 用户：帮我把 /music/source 同步到 /music/target
> AI：调用 `sync_flow_create_and_execute` → 返回 `{taskId}`，随后调用 `sync_task_get_executions` 轮询直至 SUCCESS

**预期结果**：AI 能发现工具、完成"查询 → 创建 → 触发 → 轮询"的完整业务闭环，结果与 Web 管理界面一致。

## 5. 场景 3 — 工具调用可观测性

**目标**：验证工具调用携带唯一 traceId，可通过诊断包追溯（SC-003，原则 VII）。

**验证**：
1. 调用任一工具（如 `storage_engine_test_connection`）
2. 查看 `logs/app.log`，确认该调用日志携带 `[traceId=...] [module=mcp] [operation=storage_engine_test_connection]`
3. 调用 `system_run_diagnostics` 生成诊断包，确认包内日志可定位该调用链路

**预期结果**：100% 工具调用产生含唯一非空 traceId 与 module/operation 字段的日志（SC-003）；诊断包可追溯完整调用链。

## 6. 验证规则速查

| 验证项 | 通过标准 | 对应 |
|--------|---------|------|
| 默认禁用 | 未配置时 `/mcp` 不可用，应用正常启动 | FR-012、SC-006 |
| 认证 | 未认证/无效令牌 401，正确令牌放行 | FR-008、SC-004 |
| 工具发现 | `tools/list` 返回 36 个工具 | SC-002 |
| 工具调用 | 调用工具驱动真实业务（同步/转码） | FR-001、FR-003~007 |
| 敏感脱敏 | 返回与日志无明文 token | FR-011、SC-005 |
| 可观测性 | 日志含 traceId/module/operation | FR-010、SC-003 |
| 长任务 | 触发类工具立即返回任务 ID | FR-014 |
| 回归 | 现有 Web 管理界面与 `/api/**` 测试全通过 | SC-006 |

> 完整工具参数、认证细节、配置项见 [contracts/](./contracts/) 与 [data-model.md](./data-model.md)。
