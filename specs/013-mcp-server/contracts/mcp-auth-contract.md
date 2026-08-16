# MCP 认证契约：Bearer Token

**功能**：`013-mcp-server` | **阶段**：1（设计） | **日期**：2026-08-16

**用途**：定义 MCP 服务器 `/mcp` 端点的认证请求/响应契约。实现方式见 [plan.md](../plan.md) R3 与 `McpAuthInterceptor`。

---

## 1. 认证方式

MCP 服务器采用**独立 Bearer Token** 认证（FR-008，2026-08-16 澄清），与 Web 管理界面的 Basic Auth 凭据完全隔离。

| 维度 | 契约 |
|------|------|
| 请求头 | `Authorization: Bearer <app.mcp.token>` |
| 令牌来源 | 配置项 `app.mcp.token`（环境变量 `MCP_TOKEN`），见 [mcp-config-contract.md](./mcp-config-contract.md) |
| 校验方式 | 与服务端配置令牌**恒定时间比较**（防时序侧信道） |
| 覆盖范围 | 所有 `/mcp` 请求（含 MCP 协议握手 `initialize`），认证先于协议处理 |
| 独立于 | `/api/**` 的 Basic Auth（`AuthInterceptor`），互不影响 |

## 2. 请求示例

```
POST /mcp
Host: localhost:8080
Content-Type: application/json
Authorization: Bearer <app.mcp.token>
X-Trace-Id: <可选，由客户端生成或服务端生成>

{"jsonrpc":"2.0","id":1,"method":"initialize","params":{...}}
```

## 3. 响应契约

### 3.1 认证成功

放行至 MCP 协议处理，返回标准 JSON-RPC 响应；响应头 MUST 携带 `X-Trace-Id`（由 `TraceIdFilter` 统一注入，原则 VII §7.5）。

### 3.2 认证失败（SC-004）

| 场景 | HTTP 状态 | 响应体 |
|------|----------|--------|
| 缺失令牌 | 401 | `{"code":401,"message":"缺少 MCP 认证令牌","data":null}` |
| 无效令牌 | 401 | `{"code":401,"message":"MCP 认证失败，令牌无效","data":null}` |
| 非 Bearer 格式 | 401 | `{"code":401,"message":"MCP 认证头格式无效","data":null}` |

**约束**：
- 401 响应 MUST NOT 泄露任何业务数据（SC-004）
- 401 响应 MUST NOT 泄露令牌是否已配置等配置细节（避免信息泄露）
- 认证失败日志使用 WARN 级别，记录来源与时间，MUST NOT 打印令牌值（原则 VII §7.6）

## 4. 客户端接入示例（Claude Code）

```jsonc
// .mcp.json 或 claude mcp add 配置
{
  "mcpServers": {
    "alist-media-sync": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Bearer <app.mcp.token>"
      }
    }
  }
}
```

## 5. 验证规则

- 缺失令牌、无效令牌、非 Bearer 格式三种情况 MUST 均返回 401，且无业务数据泄露
- 正确令牌 MUST 放行至 `tools/list` / `tools/call`
- 令牌未配置（`app.mcp.token` 为空）时 MCP 服务器 MUST 不可用（FR-012，启动即拒绝启用）
- 令牌值 MUST NOT 出现在日志、诊断包、配置摘要中（FR-011、SC-005）
