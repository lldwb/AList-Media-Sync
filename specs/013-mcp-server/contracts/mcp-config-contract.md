# MCP 配置契约：`app.mcp.*`

**功能**：`013-mcp-server` | **阶段**：1（设计） | **日期**：2026-08-16

**用途**：定义 MCP 服务器的配置项契约。实现后 MUST 同步至 `docs/04-配置说明.md`（配置 SSOT，章程原则 XI §11.2），本契约冻结于 spec 作为设计档案。

---

## 1. 配置项

| 配置项 | 类型 | 默认值 | 环境变量 | 必填 | 说明 |
|--------|------|--------|---------|------|------|
| `app.mcp.enabled` | boolean | `false` | `MCP_ENABLED` | 否 | MCP 服务器总开关，默认禁用（FR-012） |
| `app.mcp.token` | String | 空 | `MCP_TOKEN` | 启用时必填 | MCP 专用 Bearer Token，与 Web 管理 Basic Auth 凭据隔离（FR-008） |

## 2. 生效规则（FR-012）

| `enabled` | `token` | 结果 |
|-----------|---------|------|
| `false`（默认） | 任意 | MCP 服务器不可用，`/mcp` 无响应；现有 Web 管理界面与 `/api/**` 完全不受影响（SC-006） |
| `true` | 空/空白 | 启动报错并拒绝启用（`McpConfig` 初始化校验），防止接口裸奔 |
| `true` | 非空 | MCP 服务器装配到 `/mcp`，认证启用 |

## 3. 关联 Spring AI 配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `spring.ai.mcp.server.streamable-http.mcp-endpoint` | `/mcp` | MCP 端点路径（R6），同端口嵌入 |
| `spring.ai.mcp.server.annotation-scanner.enabled` | `true` | `@McpTool` 注解自动扫描（R1） |
| `spring.ai.mcp.server.enabled` | 派生自 `app.mcp.enabled` | 与总开关联动（R4） |

> 关联配置实现时随 `app.mcp.*` 同步配置，不单独暴露为用户可调项（YAGNI）。

## 4. 配置示例（application.yaml）

```yaml
app:
  mcp:
    enabled: true          # 或环境变量 MCP_ENABLED=true
    token: ${MCP_TOKEN}    # 必填，明文仅存在于环境变量中
```

## 5. 安全约束

- `app.mcp.token` 为敏感凭据：日志、诊断包、配置摘要 MUST NOT 打印其值（原则 VII §7.6）
- 配置摘要日志（启动信息）标注"已配置/未配置"，不打印实际值
- 令牌泄露处置：修改 `MCP_TOKEN` 并重启即可立即失效旧令牌（无持久化缓存）

## 6. 验证规则

- 默认（未配置）启动：MCP 不可用，应用正常启动，无 MCP 相关错误日志
- `enabled=true` + token 空：启动失败，错误信息说明"请配置 app.mcp.token"
- `enabled=true` + token 非空：`/mcp` 可达，认证生效
- 配置文档同步：实现后 `docs/04-配置说明.md` 为 SSOT，本文档保持冻结
