---

description: "功能实现的任务列表：MCP 服务器（AI 操作接口）"
---

# 任务：MCP 服务器（AI 操作接口）

**输入**：来自 `/specs/013-mcp-server/` 的设计文档

**前提条件**：plan.md（必需）、spec.md（必需）、research.md（已提供）、data-model.md（已提供）、contracts/（已提供）

**测试**：本功能测试为必选（章程原则 V「测试不可省略」+ plan.md R8 三层测试覆盖），每个新增 Java 类 MUST 同步新增单元测试。

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（US1、US2、US3、US4、US5）
- 在描述中包含确切的文件路径

## 路径约定

本功能聚焦后端 MCP 接入层，所有源码位于仓库根目录 `src/main/java/top/lldwb/alistmediasync/` 下（包根前缀以下简写为 `…`），测试位于 `src/test/java/top/lldwb/alistmediasync/`（同包镜像）。

---

## 阶段 1：设置（共享基础设施）

**目的**：引入 MCP 服务器依赖、基础配置项与配置绑定类

- [ ] T001 [P] 在 `pom.xml` 中添加 MCP 服务器依赖：引入 `spring-ai-bom:2.0.0`（`<dependencyManagement>`）并添加 `spring-ai-starter-mcp-server-webmvc`（版本由 BOM 管理），随后执行 `mvn dependency:tree` 确认依赖树全程使用 Jackson 3（`tools.jackson`）、无 `com.fasterxml.jackson` 混入（research.md R2 门禁）
- [ ] T002 [P] 在 `src/main/resources/application.yaml` 的 `app:` 命名空间下新增 `mcp` 配置块：`enabled: false`（默认禁用，FR-012）与 `token: ${MCP_TOKEN:}`（空值默认，FR-008），附中文注释说明环境变量 `MCP_ENABLED` / `MCP_TOKEN` 映射
- [ ] T003 [P] 在 `…/common/config/AppProperties.java` 中新增 `Mcp` 内部类（`@Data`）：字段 `enabled`（boolean，默认 `false`）与 `token`（String，默认空），并在 `AppProperties` 主类新增 `@NotNull private Mcp mcp = new Mcp();`，遵循现有 `Auth` / `Transcode` 等内部类模式（环境变量 Relaxed Binding：`MCP_ENABLED` / `MCP_TOKEN`）

---

## 阶段 2：基础（阻塞性前置条件）

**目的**：MCP 装配开关、Bearer Token 认证、端点注册——任何用户故事开始前 MUST 完成

**⚠️ 关键**：在此阶段完成之前，不能开始任何用户故事的工作（工具类依赖 McpConfig 装配与 McpAuthInterceptor 认证才能被调用）

- [ ] T004 [P] 新建 `…/common/config/McpConfig.java`：`@Configuration` + `@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")`；初始化时校验 `app.mcp.token` 非空，为空/空白则抛出异常拒绝启用并输出清晰中文错误（FR-012）；通过 `@ConditionalOnProperty` 控制 MCP 相关 bean 装配，派生 `spring.ai.mcp.server.enabled`
- [ ] T005 [P] 新建 `…/common/interceptor/McpAuthInterceptor.java`：实现 `HandlerInterceptor`，解析 `Authorization: Bearer <token>` 头并与 `app.mcp.token` 恒定时间比较（防时序侧信道）；缺失令牌/无效令牌/非 Bearer 格式三种情况 MUST 返回 HTTP 401 + 统一错误结构（`{"code":401,"message":"…"}`，中文错误描述，不泄露任何业务数据与配置细节，SC-004）；认证失败日志 WARN 级别，MUST NOT 打印令牌值（原则 VII §7.6）；放行请求由 `TraceIdFilter` 统一注入 `X-Trace-Id`
- [ ] T006 修改 `…/common/config/WebMvcConfig.java`：注入 `McpAuthInterceptor`，在 `addInterceptors` 中 `registry.addInterceptor(mcpAuthInterceptor).addPathPatterns("/mcp")` 精确注册到 MCP 端点路径，独立于 `/api/**` 的 `AuthInterceptor`（依赖 T004、T005 的类）
- [ ] T007 [P] 新建 `…/common/config/McpConfigTest.java`：单元测试覆盖三分支——`enabled=false` 不装配 MCP bean；`enabled=true` + token 空 → 启动抛异常拒绝启用；`enabled=true` + token 非空 → 正常装配（依赖 T004）
- [ ] T008 [P] 新建 `…/common/interceptor/McpAuthInterceptorTest.java`：MockMvc 验证——正确 Bearer Token 放行；缺失令牌/无效令牌/非 Bearer 格式均返回 401；401 响应体不包含业务数据（依赖 T005）

**检查点**：基础就绪——MCP 装配开关与 Bearer 认证生效，现在可以并行开始用户故事实现

---

## 阶段 3：用户故事 1 — 存储引擎模块工具（优先级：P1）🎯 MVP

**目标**：AI 通过 MCP 服务器管理存储引擎（查询/创建/更新/删除/连接测试）并浏览目录与文件

**独立测试**：连接 MCP 服务器 → 调用 `storage_engine_list` / `storage_engine_create` / `storage_engine_test_connection` / `storage_engine_list_directories` 等工具 → 验证结果与 Web 管理界面一致（spec.md US1）

### 用户故事 1 的实现

- [ ] T009 [P] [US1] 新建 `…/storage/mcp/StorageEngineMcpTools.java`：`@Component` 类注入 `StorageEngineService`（复用 Service 层，禁止直调 Repository，FR-009）；`@McpTool` 标注 8 个工具——`storage_engine_list`、`storage_engine_get`、`storage_engine_create`、`storage_engine_update`、`storage_engine_delete`、`storage_engine_test_connection`、`storage_engine_list_directories`、`storage_engine_list_entries`（目录浏览映射 `resolve(engine).listDirectories(...)`，条目浏览映射 `listEntries(...)`，路径默认 `/`）；`@McpToolParam` 中文描述参数；每个工具方法用 `TraceContext.runWith("mcp", "<工具名>", …)` 注入 traceId/module/operation（FR-010）；创建/更新工具接收 token 明文仅用于 Service 存储、返回复用 `StorageEngineVO`（VO 层已脱敏，MUST NOT 回显明文 token，FR-011）；catch 业务异常（如 `NoSuchElementException` / `IllegalArgumentException`）并转换为统一错误结构（FR-015，`{code,message,data}` 中文错误描述）
- [ ] T010 [US1] 新建 `…/storage/mcp/StorageEngineMcpToolsTest.java`：Mock `StorageEngineService`，验证 8 个工具的参数映射、结果封装、错误处理（不存在 ID → 统一错误结构）、敏感字段脱敏、traceId 注入（依赖 T009）

**检查点**：此时，用户故事 1 应完全功能可用且可独立测试（MVP 停止点）

---

## 阶段 4：用户故事 2 — 同步任务模块工具（优先级：P1）

**目标**：AI 通过 MCP 服务器创建、查询、触发、启停同步任务并查看执行历史

**独立测试**：调用 `sync_task_create` 创建任务 → `sync_task_execute` 触发（异步返回任务 ID）→ 轮询 `sync_task_get_executions` 查看执行历史与状态（spec.md US2）

### 用户故事 2 的实现

- [ ] T011 [P] [US2] 新建 `…/sync/mcp/SyncTaskMcpTools.java`：`@Component` 类注入 `SyncTaskManageService` + `SyncService`；`@McpTool` 标注 9 个工具——`sync_task_list`、`sync_task_get`、`sync_task_create`、`sync_task_update`、`sync_task_delete`、`sync_task_execute`、`sync_task_enable`、`sync_task_disable`、`sync_task_get_executions`；`sync_task_execute` 采用异步提交模式（FR-014）：复用 `manageService.executeManually(id)` + `syncService.executeSyncTask(...)`（异步），工具立即返回 `{taskId}` 不阻塞；`@McpToolParam` 中文参数描述；工具方法用 `TraceContext.runWith("mcp", "<工具名>", …)` 注入 traceId/module/operation；catch 业务异常（如任务运行中 → 409 语义）转换为统一错误结构（FR-015）
- [ ] T012 [US2] 新建 `…/sync/mcp/SyncTaskMcpToolsTest.java`：Mock `SyncTaskManageService` + `SyncService`，验证 9 个工具的参数映射、异步触发立即返回任务 ID（不阻塞等待）、错误处理、traceId 注入（依赖 T011）

**检查点**：此时，用户故事 1 和 2 应都能独立工作

---

## 阶段 5：用户故事 3 — 转码任务模块工具（优先级：P2）

**目标**：AI 通过 MCP 服务器创建转码任务、查询进度、重试失败任务并清理临时文件

**独立测试**：调用 `transcode_task_create` 创建任务（异步执行）→ `transcode_task_get` 查询实时进度 → `transcode_task_retry` / `transcode_task_cleanup_temp` 处理异常（spec.md US3）

### 用户故事 3 的实现

- [ ] T013 [P] [US3] 新建 `…/transcode/mcp/TranscodeTaskMcpTools.java`：`@Component` 类注入 `TranscodeService` + `CleanupService`；`@McpTool` 标注 8 个工具——`transcode_task_list`、`transcode_task_get`（含实时进度）、`transcode_task_create`、`transcode_task_retry`、`transcode_task_cleanup_temp`（映射 `cleanupService.manualCleanup()`）、`transcode_task_delete_failed`（映射 `deleteByStatusIn(FAILED…)`）、`transcode_task_delete_completed`（映射 `deleteByStatusIn(COMPLETED)`）、`transcode_task_retry_all`（`findByStatusIn(FAILED…)` + 逐个 `retry(id)` 异步）；`transcode_task_create` 异步提交（FR-014）立即返回任务 ID；`@McpToolParam` 中文参数描述；`TraceContext.runWith("mcp", "<工具名>", …)` 注入 traceId/module/operation；错误转换为统一错误结构（FR-015）
- [ ] T014 [US3] 新建 `…/transcode/mcp/TranscodeTaskMcpToolsTest.java`：Mock `TranscodeService` + `CleanupService`，验证 8 个工具的参数映射、异步创建返回任务 ID、批量删除/重试工具、错误处理、traceId 注入（依赖 T013）

**检查点**：此时，所有 P2 之前的用户故事应各自独立功能可用

---

## 阶段 6：用户故事 4 — Webhook 模块工具（优先级：P2）

**目标**：AI 通过 MCP 服务器管理 Webhook 规则（CRUD、启用/禁用）并查看接收事件

**独立测试**：调用 `webhook_rule_create` 创建规则并 `webhook_rule_enable` 启用 → `webhook_event_list` 查询事件记录（事件由外部系统真实触发，spec.md US4）

### 用户故事 4 的实现

- [ ] T015 [P] [US4] 新建 `…/webhook/mcp/WebhookMcpTools.java`：`@Component` 类注入 `WebhookRuleService` + `WebhookService`；`@McpTool` 标注 8 个工具——`webhook_rule_list`、`webhook_rule_get`、`webhook_rule_create`、`webhook_rule_update`、`webhook_rule_delete`、`webhook_rule_enable`、`webhook_rule_disable`、`webhook_event_list`（分页查询，映射 `webhookService.listEvents(page, size)`）；**MUST NOT** 提供事件注入/模拟发送工具（FR-006，范围边界）；`@McpToolParam` 中文参数描述；`TraceContext.runWith("mcp", "<工具名>", …)` 注入 traceId/module/operation；错误转换为统一错误结构（FR-015）
- [ ] T016 [US4] 新建 `…/webhook/mcp/WebhookMcpToolsTest.java`：Mock `WebhookRuleService` + `WebhookService`，验证 8 个工具的参数映射、分页参数、启停状态切换、无事件注入工具（断言工具集不含注入工具）、错误处理、traceId 注入（依赖 T015）

**检查点**：此时，所有 P2 用户故事应各自独立功能可用

---

## 阶段 7：用户故事 5 — 系统运维模块工具（优先级：P3）

**目标**：AI 通过 MCP 服务器查看系统概览统计并触发诊断包生成

**独立测试**：调用 `system_dashboard_stats` 查看关键指标 → `system_run_diagnostics` 生成诊断包并验证只读输出（spec.md US5，复用 specs/009 诊断系统）

### 用户故事 5 的实现

- [ ] T017 [P] [US5] 新建 `…/common/mcp/SystemMcpTools.java`：`@Component` 类注入 `DashboardService` + `DiagnosticService`；`@McpTool` 标注 2 个工具——`system_dashboard_stats`（映射 `dashboardService.getStats()`）、`system_run_diagnostics`（映射 `diagnosticService.generate()`，只读无业务副作用）；`@McpToolParam` 中文参数描述；`TraceContext.runWith("mcp", "<工具名>", …)` 注入 traceId/module/operation；错误转换为统一错误结构（FR-015）
- [ ] T018 [US5] 新建 `…/common/mcp/SystemMcpToolsTest.java`：Mock `DashboardService` + `DiagnosticService`，验证 2 个工具的参数映射、结果封装、错误处理、traceId 注入（依赖 T017）

**检查点**：此时，全部 5 个用户故事应各自独立功能可用

---

## 阶段 8：流程级快捷工具与端到端集成验证

**目的**：补齐 FR-013 混合模式的流程级快捷工具，并验证 `/mcp` 端到端可达、认证生效、工具调用透传 traceId

### 流程级快捷工具

- [ ] T019 [P] 新建 `…/sync/mcp/SyncFlowMcpTools.java`：`@Component` 类注入 `SyncTaskManageService` + `SyncService`；`@McpTool` 标注流程级快捷工具 `sync_flow_create_and_execute`（FR-013，SC-002 至少 1 个流程级工具）——接收 `sync_task_create` 同参数，一次性完成"创建同步任务 → 立即手动触发执行"，返回 `{taskId}`（组合 `manageService.create(...)` + `manageService.executeManually(id)` + `syncService.executeSyncTask(...)`，异步提交 FR-014）；`@McpToolParam` 中文参数描述；`TraceContext.runWith("mcp", "sync_flow_create_and_execute", …)` 注入 traceId；错误转换为统一错误结构（FR-015）
- [ ] T020 新建 `…/sync/mcp/SyncFlowMcpToolsTest.java`：Mock `SyncTaskManageService` + `SyncService`，验证创建并触发的一次性流程、异步返回任务 ID、错误处理、traceId 注入（依赖 T019）

### 集成测试

- [ ] T021 新建 `…/integration/McpServerIntegrationTest.java`：`@SpringBootTest` + 启用 MCP profile（`app.mcp.enabled=true` + 测试令牌），以 HTTP 方式调用 `POST /mcp` 验证——① 未认证请求返回 401（SC-004）；② 正确令牌下 `initialize` / `tools/list` 可达，`tools/list` 返回全部 36 个工具（SC-002，8+9+8+8+2+1）；③ `tools/call` 调用 `storage_engine_list` 等工具驱动真实业务并返回结果；④ 调用日志携带非空唯一 traceId 与 module/operation（SC-003）（依赖 T009-T020 全部工具类）

**检查点**：MCP 服务器端到端可用——认证、发现、调用、可观测性全链路验证通过

---

## 阶段 9：润色与跨领域关注点

**目的**：影响多个用户故事的改进——文档同步（原则 IX）、回归验证与合规检查

- [ ] T022 运行 `specs/013-mcp-server/quickstart.md` 端到端验证：未认证 401 → 认证后 `tools/list` 36 工具 → `tools/call` 驱动真实业务 → 日志含 traceId/module/operation → 返回与日志无明文 token（覆盖 SC-001~SC-005）
- [ ] T023 [P] 更新 `docs/04-配置说明.md`：新增 `app.mcp.*` 配置项（enabled/token + 环境变量 `MCP_ENABLED`/`MCP_TOKEN`、默认值、生效规则），作为配置 SSOT（原则 XI §11.2）；同时同步 `docs/operations/环境变量清单.md`
- [ ] T024 [P] 更新 `docs/05-API接口文档.md`：新增 MCP 服务器接入说明——端点 `/mcp`、Bearer Token 认证、5 模块工具总览（36 工具）与调用方式（原则 XI，API SSOT）
- [ ] T025 [P] 更新 `docs/03-架构设计.md`：新增 MCP 接入层章节——McpConfig / McpAuthInterceptor / 5+1 工具类职责、分层位置（工具层复用 Service，原则 I）
- [ ] T026 [P] 更新 `docs/06-运维部署.md`：新增 MCP 启用指引——设置 `MCP_ENABLED=true` + `MCP_TOKEN`、启动日志确认、客户端 `.mcp.json` 接入示例（原则 IX）
- [ ] T027 更新 `CHANGELOG.md`：按 Keep a Changelog 格式在顶部新增版本条目，Added 分类记录 MCP 服务器能力（5 模块 36 工具、Bearer 认证、默认禁用）（原则 IX）
- [ ] T028 全量回归验证与合规检查：执行 `mvn test`（现有 Web 管理界面与 `/api/**` 测试全部通过，SC-006 无功能降级）；对照章程逐项检查——分层架构（原则 I）、测试同步（原则 V）、traceId/脱敏（原则 VII）、文档同步（原则 IX）

**检查点**：功能完成——文档与实现一致，回归测试全通过，章程合规

---

## 依赖与执行顺序

### 阶段依赖

- **设置（阶段 1）**：无依赖 — 可立即开始
- **基础（阶段 2）**：依赖设置完成 — 阻塞所有用户故事（工具类需 McpConfig 装配 + McpAuthInterceptor 认证才能被调用）
- **用户故事（阶段 3-7）**：全部依赖基础阶段完成
  - US1/US2（P1）可随后并行开始；US3/US4（P2）、US5（P3）按优先级推进
- **流程级工具与集成（阶段 8）**：依赖全部用户故事工具类
- **润色（阶段 9）**：依赖所有期望的用户故事与集成验证完成

### 用户故事依赖

- **用户故事 1（P1）**：可在基础（阶段 2）后开始 — 不依赖其他故事
- **用户故事 2（P1）**：可在基础（阶段 2）后开始 — 与 US1 无文件交集，可独立测试
- **用户故事 3（P2）**：可在基础（阶段 2）后开始 — 与 US1/US2 无文件交集，可独立测试
- **用户故事 4（P2）**：可在基础（阶段 2）后开始 — 可独立测试
- **用户故事 5（P3）**：可在基础（阶段 2）后开始 — 可独立测试

### 每个用户故事内部

- 实现类先于其单元测试（测试依赖实现类编译）
- 工具类复用现有 Service（FR-009），不新建实体/Repository/DTO 与数据表
- 故事完成后再进入下一个优先级

### 并行机会

- 阶段 1：T001 / T002 / T003 三个文件互不依赖，可并行
- 阶段 2：T004 / T005 可并行（不同文件）；T007 / T008 各自依赖对应实现后可并行
- 阶段 3-7：US1-US5 的 5 个工具类相互独立（不同模块包），可全部并行
- 阶段 8：T019（流程级工具）与各工具类实现可并行
- 阶段 9：T023 / T024 / T025 / T026 四个文档任务可并行

---

## 并行示例：用户故事 1（MVP）

```bash
# 一起启动基础阶段（阶段 2）的装配与认证实现：
任务："新建 common/config/McpConfig.java"
任务："新建 common/interceptor/McpAuthInterceptor.java"

# 基础就绪后，并行启动 P1 用户故事的工具类：
任务："新建 storage/mcp/StorageEngineMcpTools.java"（US1）
任务："新建 sync/mcp/SyncTaskMcpTools.java"（US2）

# 各工具类实现后，紧跟其单元测试：
任务："新建 storage/mcp/StorageEngineMcpToolsTest.java"（US1）
任务："新建 sync/mcp/SyncTaskMcpToolsTest.java"（US2）
```

---

## 实现策略

### MVP 优先（仅用户故事 1）

1. 完成阶段 1：设置（依赖 + 配置 + 绑定类）
2. 完成阶段 2：基础（McpConfig 装配 + McpAuthInterceptor 认证 + WebMvcConfig 注册，关键 — 阻塞所有故事）
3. 完成阶段 3：用户故事 1（StorageEngineMcpTools，8 工具）
4. **停止并验证**：连接 MCP 服务器，调用存储引擎工具，独立测试用户故事 1
5. 如果就绪则部署/演示

### 增量交付

1. 完成设置 + 基础 → 基础就绪
2. 添加用户故事 1（存储引擎）→ 独立测试 → 部署/演示（MVP！）
3. 添加用户故事 2（同步任务）→ 独立测试 → 部署/演示
4. 添加用户故事 3、4（转码、Webhook，P2）→ 独立测试 → 部署/演示
5. 添加用户故事 5（系统运维，P3）+ 流程级工具 → 独立测试 → 部署/演示
6. 每个故事增加价值而不破坏之前的的故事

### 并行团队策略

多个开发人员时：

1. 团队一起完成设置 + 基础
2. 基础完成后：
   - 开发人员 A：用户故事 1（存储引擎）
   - 开发人员 B：用户故事 2（同步任务）
   - 开发人员 C：用户故事 3、4（转码、Webhook）
   - 开发人员 D：用户故事 5 + 流程级工具
3. 各故事独立完成并集成，最后由集成测试（T021）统一验证

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应能独立完成和测试
- 每个新增 Java 类 MUST 同步新增单元测试（原则 V）
- 每个任务或逻辑组后提交（遵循原则 XII：多模块改动按模块拆分，显式 `git add`，中文提交信息）
- 在任何检查点停止以独立验证故事
- 避免：模糊的任务、同文件冲突、破坏独立性的跨故事依赖
