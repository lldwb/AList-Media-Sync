# 实现计划：MCP 服务器（AI 操作接口）

**分支**：`013-mcp-server` | **日期**：2026-08-16 | **规格**：[spec.md](./spec.md)

**输入**：来自 `/specs/013-mcp-server/spec.md` 的功能规格

## 摘要

在现有 Spring Boot 应用中嵌入标准 MCP 服务器，使 AI 客户端（如 Claude Code）通过一个 MCP 端点按模块发现并调用工具，操作系统的五大业务模块（存储引擎、同步任务、转码任务、Webhook、系统运维），实现"让 AI 可以操作系统"的目标。

核心交付：
- **MCP 服务器集成**：引入 `spring-ai-starter-mcp-server-webmvc:2.0.0`，端点注册到 `/mcp` 同端口嵌入，兼容 Spring Boot 4.1.0 与 Jackson 3（`tools.jackson`，无 `com.fasterxml.jackson` 混入）
- **5 模块工具集（混合粒度，FR-013）**：操作级细粒度工具约 35 个（镜像现有 Controller 操作面）+ 少量流程级快捷工具，按模块分 5 个 MCP 工具类，复用现有 Service 层（FR-009）
- **独立 Bearer Token 认证（FR-008）**：自定义 `McpAuthInterceptor` 校验 `Authorization: Bearer <token>`，与 Web 管理 Basic Auth 凭据隔离；未认证/无效令牌 100% 拒绝（SC-004）
- **默认禁用（FR-012）**：`app.mcp.enabled=false` 默认关闭，仅显式开启且令牌非空时生效，不影响现有 Web 管理界面与 `/api/**` API（SC-006）
- **可观测性合规（FR-010/FR-011）**：工具调用经 `TraceContext` 注入唯一 traceId，敏感凭据（存储引擎 Token 等）脱敏后返回

技术方法：Spring AI 2.0.0 MCP Server Web MVC Starter 注解式注册（`@McpTool`）复用现有 Service Bean，认证沿用项目自定义 HandlerInterceptor 模式（不引入 Spring Security），长任务工具采用异步提交 + 任务 ID 返回（FR-014），AI 通过查询工具轮询结果。

## 技术上下文

**语言/版本**：Java 21（LTS，虚拟线程已启用 `spring.threads.virtual.enabled=true`）

**主要依赖**：
- 已有：Spring Boot 4.1.0（Spring Framework 7.x）、Spring Data JPA、RestClient、JAVE2（转码）、SLF4J+Logback、Lombok、Jakarta Validation、H2、Spring Security Crypto（BCrypt）、SpringDoc、WebSocket、Jackson 3（`tools.jackson`）
- **本功能新增**：`spring-ai-starter-mcp-server-webmvc:2.0.0`（MCP 服务器集成，Streamable HTTP 传输，`@McpTool` 注解扫描）
- **明确不引入**：`spring-ai-starter-model-openai`（其模型实现显式用 Jackson 2，会破坏本项目 Jackson 3 一致性）、社区 `mcp-security` 模块（WIP 且强依赖 Spring Security）、`modelcontextprotocol/java-sdk`（需手写 ToolSpecification，工作量大）

**存储**：不新增。MCP 工具复用现有 H2 持久化与现有实体（SyncTask、TranscodeTask、StorageEngine、WebhookRule 等），不新建数据表

**测试**：
- 单元测试（新增）：各模块 MCP 工具类（Mock Service，验证参数映射、结果封装、错误处理、敏感字段脱敏）+ `McpAuthInterceptor`（MockMvc 验证 Bearer Token 成功/缺失/无效）+ `McpConfig`（启用/禁用/令牌缺失分支）
- 集成测试（新增）：`McpServerIntegrationTest`（`@SpringBootTest` 启用 MCP profile，以 HTTP 方式调用 `/mcp` 验证 `initialize`/`tools/list`/`tools/call` 可达、认证生效、工具调用透传 traceId）
- 复用现有 `spring-boot-starter-webmvc-test`（JUnit 5 + Mockito + MockMvc），不新增测试依赖

**目标平台**：Windows 开发环境优先；Linux 生产 / Docker 同端口嵌入，无平台差异（MCP 为进程内能力）

**项目类型**：web-service（Spring Boot 后端 + React 前端同端口；本功能聚焦后端 MCP 接入层）

**性能目标**：
- 同步类工具（查询、配置、连接测试）调用响应 < 2 秒
- 长任务工具（触发同步、创建转码）异步提交，立即返回任务 ID（< 200ms 提交开销）
- MCP 端点额外内存/CPU 开销 < 5%（相对现有应用基线）

**约束**：
- MCP 服务器默认禁用，`app.mcp.enabled=true` 且 `app.mcp.token` 非空时才生效（FR-012）
- 认证采用独立 Bearer Token，与 Web 管理 Basic Auth 凭据隔离（FR-008）
- MCP 工具 MUST 复用现有 Service 层，禁止直调 Repository 或承载业务逻辑（FR-009、原则 I）
- 工具调用 MUST 经 `TraceContext` 注入 traceId / module / operation，敏感凭据脱敏（FR-010、FR-011、原则 VII）
- 长任务工具 MUST 异步提交立即返回任务 ID（FR-014）
- 不额外限流，复用现有 Service 层并发控制（假设，YAGNI）
- 启用 MCP 后现有 Web 管理界面与 `/api/**` 回归测试全部通过（SC-006）

**规模/范围**：
- 5 个 MCP 工具类（storage/sync/transcode/webhook/system），操作级工具约 35 个 + 少量流程级快捷工具
- 1 个认证拦截器（`McpAuthInterceptor`）、1 个配置类（`McpConfig`）、1 个配置内部类（`AppProperties.Mcp`）
- 工具类单元测试 6 个 + 认证拦截器测试 1 个 + 集成测试 1 个

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

| # | 原则 | 门禁评估 | 状态 |
|---|------|---------|------|
| I | 分层架构 | MCP 工具层仅做参数适配与结果封装，复用现有 Service（`@McpTool` 方法直接注入调用模块 Service），禁止跨层调 Repository；`McpAuthInterceptor` 职责单一（仅认证） | ✅ 通过 |
| II | 数据完整性 | 工具复用现有 `@Transactional` 写操作与 `@Version` 乐观锁实体；本功能不新建实体与表，不改变持久化行为 | ✅ 通过 |
| III | RESTful API 契约 | MCP 为增量接口，不改变现有 `/api/**` 契约；`/mcp` 端点同样由 `TraceIdFilter` 注入 `X-Trace-Id`（原则 VII §7.5）；工具错误返回统一错误结构（FR-015） | ✅ 通过 |
| IV | 中文优先 | 工具描述、参数说明、注释、日志使用简体中文；工具名称与字段使用英文（MCP 客户端可读性与 Spring 惯例） | ✅ 通过 |
| V | 测试不可省略 | 每次新增 Java 类 MUST 同步新增单元测试：6 个工具类测试 + 拦截器测试 + 配置测试；集成测试验证 `/mcp` 端到端可达与认证生效（R8） | ✅ 通过 |
| VI | 简洁至上（YAGNI） | **1 处违规需证明合理性**（见复杂性追踪）：引入 `spring-ai-starter-mcp-server-webmvc:2.0.0`。明确拒绝 java-sdk 手写注册、独立 MCP 进程、社区 mcp-security | ⚠️ 违规已证明（见下表） |
| VII | 日志规范 | 工具调用入口 `TraceContext.runWith(...)` 注入 traceId/module/operation（module 取 `mcp` + 业务模块，operation 取工具名）；敏感凭据按 7.6 节脱敏；ERROR 双写与 `errorType` MDC 遵循既有约定 | ✅ 通过 |
| VIII | 规格状态同步 | plan 完成后将 spec.md 状态从"已澄清"更新为"已计划" | ✅ 通过（本计划收尾执行） |
| IX | 实现后文档同步 | 实现后更新 `docs/04-配置说明.md`（`app.mcp.*` 配置 SSOT）、`docs/05-API接口文档.md`（MCP 工具接口说明）、`docs/03-架构设计.md`（MCP 接入层）、`docs/06-运维部署.md`（MCP 启用指引）、`CHANGELOG.md` 新增版本条目 | ✅ 通过（实现阶段执行） |
| X | 章程更新与 AGENTS.md 同步 | 本功能不涉及章程修订，N/A | ✅ N/A |
| XI | 文档体系结构 | `app.mcp.*` 配置进 `docs/04` SSOT（禁止在 README/Dockerfile/.env 重复维护权威内容）；MCP 工具契约冻结于 `specs/013/contracts/` | ✅ 通过 |
| XII | Git 提交规范 | 实现阶段多模块改动按模块拆分提交（配置/业务代码/测试/文档各归一次），显式 `git add` | ✅ 通过（实现阶段执行） |

**门禁结论**：所有原则通过或违规已证明合理性，可进入阶段 0 研究。

## 项目结构

### 文档（本功能）

```text
specs/013-mcp-server/
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── research.md          # 阶段 0 输出（MCP 集成技术选型，R1-R8）
├── data-model.md        # 阶段 1 输出（MCP 工具契约与配置实体）
├── quickstart.md        # 阶段 1 输出（AI 客户端接入验证指南）
├── contracts/           # 阶段 1 输出（对外契约）
│   ├── mcp-tools-contract.md     # MCP 工具清单契约（工具名/参数/返回结构）
│   ├── mcp-auth-contract.md      # Bearer Token 认证契约（请求/响应）
│   └── mcp-config-contract.md    # app.mcp.* 配置项契约（环境变量/默认值）
└── tasks.md             # 阶段 2 输出（/speckit-tasks 命令 — 非 /speckit-plan 创建）
```

### 源代码（仓库根目录）

```text
src/main/java/top/lldwb/alistmediasync/
├── common/
│   ├── config/
│   │   ├── AppProperties.java        # 【修改】新增 Mcp 内部类（enabled / token）
│   │   └── McpConfig.java            # 【新增】@ConditionalOnProperty 控制 MCP bean 装配 + 启动令牌校验
│   ├── interceptor/
│   │   └── McpAuthInterceptor.java   # 【新增】Bearer Token 认证拦截器（类比 AuthInterceptor）
│   ├── config/WebMvcConfig.java      # 【修改】注册 McpAuthInterceptor 到 /mcp
│   └── mcp/
│       └── SystemMcpTools.java       # 【新增】系统运维模块工具（仪表盘统计/诊断包）
├── storage/mcp/
│   └── StorageEngineMcpTools.java    # 【新增】存储引擎模块工具（CRUD/连接测试/目录浏览）
├── sync/mcp/
│   ├── SyncTaskMcpTools.java         # 【新增】同步任务模块工具（CRUD/触发/启停/执行历史）
│   └── SyncFlowMcpTools.java         # 【新增】流程级快捷工具（如创建并触发一次同步）
├── transcode/mcp/
│   └── TranscodeTaskMcpTools.java    # 【新增】转码任务模块工具（CRUD/重试/清理/批量）
└── webhook/mcp/
    └── WebhookMcpTools.java          # 【新增】Webhook 模块工具（规则 CRUD/启停/事件查询）

src/main/resources/application.yaml   # 【修改】新增 app.mcp.* 配置（默认禁用）

src/test/java/top/lldwb/alistmediasync/
├── common/config/
│   └── McpConfigTest.java            # 【新增】启用/禁用/令牌缺失分支
├── common/interceptor/
│   └── McpAuthInterceptorTest.java   # 【新增】Bearer Token 成功/缺失/无效
├── common/mcp/
│   └── SystemMcpToolsTest.java       # 【新增】系统运维工具单元测试
├── storage/mcp/
│   └── StorageEngineMcpToolsTest.java# 【新增】存储引擎工具单元测试
├── sync/mcp/
│   ├── SyncTaskMcpToolsTest.java     # 【新增】同步任务工具单元测试
│   └── SyncFlowMcpToolsTest.java     # 【新增】流程级工具单元测试
├── transcode/mcp/
│   └── TranscodeTaskMcpToolsTest.java# 【新增】转码工具单元测试
├── webhook/mcp/
│   └── WebhookMcpToolsTest.java      # 【新增】Webhook 工具单元测试
└── integration/
    └── McpServerIntegrationTest.java # 【新增】/mcp 端到端验证（认证/发现/调用/traceId）
```

**结构决策**：
- MCP 工具类按模块放入各业务包下的 `mcp/` 子包，与 `controller/` 并列——Controller 是 HTTP 入口、MCP 工具是协议入口，二者同层复用 Service，保持分层一致性（原则 I）
- 认证拦截器放 `common/interceptor/` 与 `AuthInterceptor` 并列；配置类放 `common/config/` 与 `WebMvcConfig` 并列，遵循现有模块组织
- 工具类通过 `@McpTool` 注解标注方法、`@McpToolParam` 描述参数，Spring AI 自动扫描注册，无需手写 ToolSpecification
- 不新建实体、Repository、DTO 与数据表；工具返回复用现有 Service 的 VO/DTO 或映射为精简结构（脱敏后）
- 测试目录镜像 `src/main/java` 结构，工具类测试放同包路径，集成测试放 `integration/`

## 复杂性追踪

> **仅在章程检查有必须证明合理性的违规时填充**

| 违规 | 为什么需要 | 被拒绝的更简单替代方案及原因 |
|------|-----------|------------------------|
| 引入 `spring-ai-starter-mcp-server-webmvc:2.0.0`（违反原则 VI YAGNI） | 需要标准 MCP 服务器能力：Streamable HTTP 传输（MCP 2025-03-26 规范）、`initialize`/`tools/list`/`tools/call` 协议交互、工具自动注册与参数模式生成。Spring AI 2.0.0 提供注解式 `@McpTool` 自动扫描（约 35 工具零样板）、同端口 `/mcp` 端点、与 Spring Boot 4.1.0 原生兼容、全程 Jackson 3（`tools.jackson`）与项目 JSON 库一致；认证沿用项目自定义拦截器模式，不依赖其社区安全模块 | ① 手写 MCP 协议端点：需自行实现 Streamable HTTP 传输、工具发现、参数校验、结果序列化等协议细节，易出错且不兼容标准 AI 客户端，重复造轮子；② `modelcontextprotocol/java-sdk:2.0.0`：官方 SDK 需手动构建 `McpServer` 与注册 Servlet transport，约 35 个工具需手写 `ToolSpecification`（注解扫描模块为社区孵化 WIP），工作量大且集成方式非标准；③ 独立 MCP 进程部署：偏离"与主应用同端口嵌入"假设（FR-012），需额外端口/进程管理，部署复杂 |
