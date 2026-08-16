# 研究文档：MCP 服务器（AI 操作接口）

**功能**：`013-mcp-server` | **阶段**：0（研究） | **日期**：2026-08-16

**输入**：[plan.md](./plan.md) 技术上下文中的未知项（MCP 库选型、认证实现、启用机制）与章程检查的 VI 违规项

本文档解决 MCP 服务器集成中的技术未知项，每项给出**决策 / 理由 / 考虑的替代方案**。所有决策对齐章程原则 VI（YAGNI）、原则 I（分层架构）、原则 VII（日志规范）与用户澄清决策（FR-008 独立 Bearer Token、FR-012 默认禁用、FR-013 混合粒度）。

---

## R1. MCP 服务器库选型

**决策**：引入 `spring-ai-starter-mcp-server-webmvc:2.0.0`（Spring AI MCP Server Boot Starter，Web MVC 变体），在现有 Spring Boot 4.1.0 应用中嵌入 MCP 服务器，复用 Spring Bean 容器自动注册工具。

**理由**：
- **Spring Boot 4.1.0 原生兼容**：Spring AI 2.0.0 的 release notes 明确 "Upgrade to Spring Boot 4.1.0"，其 POM 依赖 `spring-boot-starter-web:4.1.0`、`spring-webmvc:7.0.8`、`jakarta.servlet-api:6.1`，与项目当前版本同代（2026-06 发布）
- **注解式工具注册**：`@McpTool` / `@McpToolParam` 注解 + 自动扫描（`spring.ai.mcp.server.annotation-scanner.enabled` 默认 true），直接写在现有 `@Service` 类上即可复用业务 Bean，按模块分组天然实现，大幅降低约 35 个工具的开发成本
- **同端口嵌入**：自动注册到 `/mcp`（可配置 `spring.ai.mcp.server.streamable-http.mcp-endpoint`），与 Web 管理界面、`/api/**` 同 context，零侵入
- **Streamable HTTP 原生支持**：`protocol=STREAMABLE`，符合 MCP 2025-03-26 规范；SSE 传输自 2.0.0 起弃用

**考虑的替代方案**：
- **modelcontextprotocol/java-sdk 2.0.0**（官方 Java SDK）：需手动构建 `McpServer` 并注册 `HttpServletStreamableServerTransportProvider`，约 35 个工具需手写 `ToolSpecification` 或依赖社区孵化注解模块，工作量大且集成方式不标准；仅当拒绝 Spring AI 依赖树时才值得考虑
- **MCP 服务器独立进程部署**：偏离"嵌入主应用同端口"假设（FR-012），增加部署复杂度，违反 YAGNI
- **手写 HTTP/SSE 端点模拟 MCP 协议**：重复造轮子，协议实现易出错且不兼容标准客户端，明确拒绝

---

## R2. Jackson 3 兼容性验证

**决策**：采用 Spring AI 2.0.0，其 MCP 依赖链全程使用 Jackson 3（`tools.jackson`），与项目现有 JSON 库完全一致，无双 Jackson 风险。

**理由**：
- 项目使用 Spring Boot 4.x 的 Jackson 3（`tools.jackson.databind.json.JsonMapper`，见 `AuthInterceptor`），非 Jackson 2（`com.fasterxml.jackson`）
- 实测 Spring AI 2.0.0 依赖树：`spring-ai-mcp` → `io.modelcontextprotocol.sdk:mcp:2.0.0`（= core + `mcp-json-jackson3`）、`spring-ai-model` → `tools.jackson.core:jackson-databind:3.1.4`，全程 `tools.jackson`，无 `com.fasterxml.jackson` 混入
- 规划阶段 MUST 执行 `mvn dependency:tree` 最终确认无 `com.fasterxml.jackson` 传递依赖

**考虑的替代方案**：
- **java-sdk 的 jackson2 模块**：引入 Jackson 2 到项目，产生双 JSON 库，与项目现有 `JsonMapper` 冲突，明确拒绝
- **手动桥接 Jackson 2/3**：额外适配层，违反 YAGNI

---

## R3. Bearer Token 认证实现

**决策**：新增自定义 `McpAuthInterceptor`（HandlerInterceptor），校验 `Authorization: Bearer <token>` 头与配置的 `app.mcp.token` 精确比对，在 `WebMvcConfig` 注册到 `/mcp` 路径；不引入社区 `mcp-security` 模块。

**理由**：
- 对齐项目现有认证模式：`AuthInterceptor`（约 80 行，无 Spring Security）已证明 HandlerInterceptor 模式可行且满足 YAGNI
- 现有 `AuthInterceptor` 仅拦截 `/api/**`，`/mcp` 不在其范围，新增独立拦截器职责单一（原则 I）
- 社区 `mcp-security` 模块（`org.springaicommunity`）为 WIP 且强依赖 Spring Security，与项目"不引入完整 Security 框架"决策冲突
- Bearer Token 精确比对实现简单、可测试，认证失败返回 401 + 统一错误结构（FR-015）
- 未认证/无效令牌请求在工具执行前被拦截，MUST NOT 泄露业务数据（SC-004）

**考虑的替代方案**：
- **TransportContextExtractor 回调**（Spring AI 提供的传输上下文提取器）：可提取 Authorization 头，但回调内校验逻辑与项目拦截器模式不一致，且不如拦截器可复用、可单独单元测试
- **Filter 实现**（OncePerRequestFilter）：与 `TraceIdFilter` 同层但职责重叠，拦截器更贴近现有认证模式
- **社区 mcp-security 模块**：WIP + 依赖 Spring Security，明确拒绝

---

## R4. 默认禁用机制（FR-012）

**决策**：MCP 服务器默认禁用，通过 `app.mcp.enabled=false`（默认值）控制；仅当 `app.mcp.enabled=true` 且 `app.mcp.token` 已配置非空令牌时，MCP 相关 bean 才注册并生效；令牌缺失或为空时 MUST 拒绝启用并输出清晰错误。

**理由**：
- 满足 FR-012"默认禁用、未配置有效 Bearer Token 时强制不可用"与 SC-004 安全目标
- 通过 `@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")` 控制 `McpConfig` 与工具 bean 的注册，与 Spring AI 自动配置联动（`spring.ai.mcp.server.enabled` 由本配置派生）
- 令牌校验在启动时执行（`McpConfig` 初始化时检查 `app.mcp.token` 非空），避免启动后才发现未配置令牌导致接口裸奔
- 配置项新增至 `AppProperties.Mcp` 内部类（`enabled`、`token`），遵循现有 `app.*` Relaxed Binding 约定（`MCP_ENABLED` / `MCP_TOKEN` 环境变量）

**考虑的替代方案**：
- **始终启用 + 依赖认证拦截**：端点暴露于未配置环境，扩大攻击面，违反默认安全原则
- **通过 profile 控制**：与现有配置体系不一致，运维需额外管理 profile 开关

---

## R5. 工具注册与模块分组（FR-002、FR-013）

**决策**：操作级工具为主（约 35 个，镜像现有 Controller 操作面），按模块分 5 个 MCP 工具类：`storage`、`sync`、`transcode`、`webhook`、`system`；另提供少量流程级快捷工具（如"执行一次同步流程"）作为混合模式的补充（FR-013）。

**理由**：
- 用户澄清决策（2026-08-16）：混合模式——操作级细粒度为主 + 少量流程级快捷工具
- 工具类按模块放入各自业务包（`storage/mcp/`、`sync/mcp/`、`transcode/mcp/`、`webhook/mcp/`、`common/mcp/`），依赖注入对应模块 Service，遵循分层架构（原则 I，工具层仅做参数适配与结果封装，不承载业务逻辑）
- `@McpTool` 注解标注工具方法，`@McpToolParam` 描述参数，工具描述与参数说明使用简体中文（原则 IV），工具名称与字段使用英文（MCP 客户端可读性）
- 流程级工具封装现有 Service 组合（如"创建同步任务并手动触发"），减少 AI 多步编排的 token 消耗

**考虑的替代方案**：
- **纯操作级**：约 35 个工具全部 1:1 镜像 API，AI 灵活但常见流程需多步编排，不符合 FR-013 混合模式
- **纯流程级**：约 10 个流程工具，服务端封装复杂且灵活性低，已由澄清拒绝

---

## R6. 端点路径与同端口嵌入（FR-012）

**决策**：MCP 服务器端点注册到 `/mcp`（配置项 `spring.ai.mcp.server.streamable-http.mcp-endpoint`），与主应用同端口；`McpAuthInterceptor` 在 `WebMvcConfig.addInterceptors` 中注册到 `/mcp` 路径，独立于 `/api/**` 的 `AuthInterceptor`。

**理由**：
- `/mcp` 是 Spring AI MCP Server 默认端点路径，为标准惯例，主流 MCP 客户端（Claude Code 等）可默认发现
- 同端口嵌入避免新端口暴露，与"与主应用同进程同端口"假设一致（FR-012）
- 拦截器路径精确匹配 `/mcp`，不影响现有 `/api/**` 认证与静态资源（原则 I 职责单一）
- MCP 端点响应 `X-Trace-Id` 头由现有 `TraceIdFilter`（`@Order(HIGHEST_PRECEDENCE)`，拦截所有请求）统一注入，满足原则 VII §7.5

**考虑的替代方案**：
- **独立端口**：需额外端口管理、防火墙配置、客户端 URL 配置复杂，违反同端口假设
- **复用 AuthInterceptor 拦截 `/mcp`**：`AuthInterceptor` 仅处理 Basic 认证，与 MCP 的 Bearer Token 语义不符，职责混乱

---

## R7. 长任务异步提交（FR-014）

**决策**：触发长时间任务的工具（同步任务手动触发、转码任务创建、重试全部失败任务）沿用现有 Service 的异步执行机制，工具立即返回任务 ID 与提交状态，任务结果由 AI 通过查询工具（任务详情、执行历史、转码进度）轮询获取；不阻塞 MCP 工具调用。

**理由**：
- 现有 `SyncService.executeSyncTask`、`TranscodeService.executeAsync` 已采用异步执行（@Async / 虚拟线程 + TraceContext 透传），工具层复用即可
- 满足 FR-014 与用户故事验收场景（"工具返回任务 ID，后续通过查询工具追踪结果"）
- 避免 MCP 工具调用长时间阻塞导致客户端超时

**考虑的替代方案**：
- **同步阻塞直到完成**：转码可达分钟级，客户端必然超时，违反 FR-014
- **新建同步执行模型**：重复实现，违反 YAGNI

---

## R8. MCP 工具测试策略（原则 V）

**决策**：三层测试覆盖——① 工具类单元测试（Mock Service，验证参数映射、结果封装、错误处理、敏感字段脱敏）；② 认证拦截器单元测试（MockMvc 验证 Bearer Token 成功/缺失/无效）；③ 集成测试（`@SpringBootTest` + 启用 MCP profile，以 HTTP 方式调用 `/mcp` 验证 `tools/list` 与 `tools/call` 实际可达、认证生效、工具调用透传 traceId）。

**理由**：
- 满足章程原则 V"每次修改 Java 类 MUST 同步新增/更新单元测试"与测试覆盖率目标（Service >80%、Controller >70%）
- MCP 工具类是新的"入口层"，类比 Controller 层，需覆盖正常与异常场景
- 集成测试验证端到端（认证 → 端点 → 工具发现 → 工具调用 → 业务执行 → traceId）与 SC-003/SC-004/SC-005 的可验证性
- 复用现有 `spring-boot-starter-webmvc-test`（JUnit 5 + Mockito + MockMvc），不新增测试依赖

**考虑的替代方案**：
- **仅单元测试**：无法验证 MCP 端点可达、认证生效与协议交互，SC-003/SC-004 不可验证
- **引入专门 MCP 客户端测试库**：额外依赖，HTTP 方式已足以验证，违反 YAGNI

---

## 研究结论

所有技术未知项已解决，无残留 `[需要澄清]` 标记。关键决策汇总：

| 研究项 | 决策 | 章程对齐 |
|--------|------|---------|
| R1 | Spring AI 2.0.0 `spring-ai-starter-mcp-server-webmvc` | VI 违规已证明（复杂性追踪） |
| R2 | Jackson 3 全链路一致（`tools.jackson`），规划期 `dependency:tree` 确认 | 避免双 JSON 库 |
| R3 | 自定义 `McpAuthInterceptor`（Bearer Token 比对） | YAGNI、原则 I、FR-008 |
| R4 | `app.mcp.enabled` 默认 false + 令牌非空启动校验 | FR-012、SC-004 |
| R5 | 5 模块操作级工具 + 少量流程级快捷工具 | FR-002、FR-013 |
| R6 | 端点 `/mcp` 同端口嵌入，拦截器精确匹配 | FR-012、原则 I |
| R7 | 长任务异步提交，AI 轮询查询工具 | FR-014 |
| R8 | 单元测试 + 集成测试三层覆盖 | 原则 V |

**待实现阶段确认的细节**（非阻塞，方向已定）：
- Spring AI 2.0.0 精确发布版本与 `@McpTool` 注解的实际包路径（实现时以 `mvn dependency:tree` 与源码确认为准）
- `/mcp` 端点在 Claude Code 客户端的最小可行性连接验证（实现阶段首个任务执行 `initialize → tools/list → call`）
- 流程级快捷工具的精确定义与 Service 组合方式（实现阶段按工具清单确定）
