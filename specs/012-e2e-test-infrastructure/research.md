# 研究文档：端到端测试基础设施

**功能**：`012-e2e-test-infrastructure` | **阶段**：0（研究） | **日期**：2026-07-19

**输入**：[plan.md](./plan.md) 技术上下文中的未知项与章程检查的 VI 违规项

本文档解决 plan.md 中的技术未知项，每项给出**决策 / 理由 / 考虑的替代方案**。所有决策对齐章程原则 VI（YAGNI）与用户澄清决策（Q1:A 二进制本地启动、Q2:C 真实录制直播流）。

---

## R1. WireMock 选型与集成方式

**决策**：引入 `wiremock-standalone:3.9.1`（或当前稳定版），通过 JUnit 5 扩展 `@WireMockTest` 注解集成，端口由 WireMock 动态分配（`dynamicPort`），测试类通过 `WireMockRuntimeInfo` 获取实际端口注入 `AListStorageStrategy` 的 RestClient 配置。

**理由**：
- `wiremock-standalone` 是 shaded jar，无传递依赖冲突风险（与 Spring Boot 4.1 / Spring Framework 7.x 兼容）
- `@WireMockTest` 注解自动管理 WireMock 服务器生命周期（启动/停止），无需手写 `@BeforeEach`/`@AfterEach`
- 动态端口分配避免与本地 AList 实例（5344）或 E2E 实例端口冲突
- 桩映射存放在 `src/test/resources/wiremock/alist-mappings/`，JSON 声明式定义，可读性强且可版本控制
- WireMock 支持请求匹配（URL、method、body）、响应模板（Handlebars）、延迟模拟、录制回放，能覆盖 AList 9 个端点的契约 + 错误场景（401/403/404/500）+ 重试触发

**考虑的替代方案**：
- **Spring Cloud Contract WireMock**：引入 Spring Cloud 依赖过重，违反 YAGNI；项目未使用契约驱动开发模式
- **手写 `@RestController` stub 服务**：需重复实现 9 个 AList 端点 + 统一响应包装 + `/ping` text/plain 特殊处理，维护成本高且易与真实契约漂移
- **Mockito mock RestClient**：只能验证"调用了什么"，无法验证"请求是否符合 AList 契约"（如 `File-Path` header URL 编码、`As-Task` header、分页参数），失去契约测试意义

---

## R2. maven-failsafe-plugin 配置与 Maven profile 隔离方案

**决策**：
- 引入 `maven-failsafe-plugin`，绑定 `*IT.java` 命名约定（集成测试）与 `*E2ETest.java`（通过 `<includes>` 配置），在 `integration-test` 与 `verify` 阶段执行
- 定义 3 个 Maven profile：
  - 默认（无 profile）：`mvn test` 仅运行 surefire 单元测试（`*Test.java`，排除 `*IT.java` 与 `*E2ETest.java`），保持快速反馈
  - `-Pintegration`：激活 failsafe 运行 `*IT.java`（Repository + AList 契约），不启动外部二进制
  - `-Pe2e`：激活 failsafe 运行 `*E2ETest.java`，并通过 `E2ELifecycleManager` 自动启动 AList + 录播姬二进制实例
- surefire 在所有 profile 下排除 `*IT.java` 与 `*E2ETest.java`，确保单元测试与慢速测试物理隔离

**理由**：
- failsafe 是 Maven 集成测试标准插件，与 surefire 分离生命周期，支持 `verify` 阶段失败中断构建
- profile 隔离实现 FR-005（端到端测试不污染常规测试），且让 CI 可按需选择测试层级
- `*IT.java` / `*E2ETest.java` 命名约定是 failsafe 社区惯例，IDE 与 CI 均能识别

**考虑的替代方案**：
- **全部用 surefire + `<excludes>` 配置**：无法区分"快速单元测试"与"慢速集成/E2E"，`mvn test` 会被 E2E 拖慢至数分钟，违反 FR-005
- **拆分为独立 Maven 子模块**：过度设计，项目当前单模块结构足以承载测试分层，且会破坏现有包结构
- **手写 shell 脚本驱动测试**：绕过 Maven 生命周期，丢失依赖管理与报告生成，CI 集成困难

---

## R3. AList 二进制下载方案

**决策**：
- 下载源：AList GitHub Releases（`Xhofe/alist`），版本锁定（如 `v3.x.x`，固定于脚本常量，可升级）
- 平台二进制：Windows `alist-windows-amd64.zip`、Linux `alist-linux-amd64.tar.gz`
- 下载到 `scripts/e2e/bin/alist/`，解压后保留 `alist.exe`/`alist` 可执行文件
- SHA256 校验：下载后校验哈希（哈希值锁定于脚本常量），不符则删除并报错
- 幂等：目标路径已存在可执行文件则跳过下载，仅校验存在性（FR-009 幂等要求）
- 跳过下载：支持 `-Dalist.local.path` 系统属性或环境变量指定本地已有二进制路径（对齐 005-standalone-bootstrap 的 `-Djre.local.path` 模式）
- 启动配置：`scripts/e2e/e2e-config/alist.config.json` 提供 AList 数据目录、端口、初始存储挂载配置；`start-alist.ps1` 启动并等待 `/ping` 返回 `pong` 后视为就绪

**理由**：
- 对齐 005-standalone-bootstrap 的 JRE 下载脚本模式（幂等、版本锁定、本地路径跳过），复用已验证经验
- SHA256 校验防止下载损坏或被篡改
- AList 启动后需初始化管理员密码与存储挂载，`e2e-config/` 模板固化此配置，保证可重复

**考虑的替代方案**：
- **Docker 启动 AList 容器**：用户决策 Q1:A 明确要求二进制本地启动，偏离原意
- **不锁版本，始终下载最新版**：AList API 变更会导致 E2E 漂移，违反可重复性（SC-004）
- **下载源码自行编译**：引入 Go 工具链依赖，过度复杂

---

## R4. 录播姬二进制下载与 E2E 链路自动化方案

**决策**：
- 下载源：录播姬 GitHub Releases（`BililiveRecorder/BililiveRecorder`），版本锁定
- 平台二进制：Windows `BililiveRecorder-Windows.zip`（.NET 自包含）、Linux `BililiveRecorder-Linux.zip`
- 下载到 `scripts/e2e/bin/danmuji/`，SHA256 校验，幂等，支持 `-Ddanmuji.local.path` 跳过
- 启动配置：`scripts/e2e/e2e-config/danmuji.config.toml` 配置录播姬工作目录、WebUI 端口（2356）、webhook URL（指向系统 `/api/webhooks/recorder`）、录制输出目录
- E2E 链路自动化（`DanmujiEventTrigger` 封装）：
  1. `E2ELifecycleManager` 启动录播姬实例，等待 WebUI（端口 2356）就绪
  2. `DanmujiEventTrigger` 通过录播姬 HTTP API（WebUI 后端接口）添加目标房间并开始录制
  3. 等待录制产生文件（轮询录播姬工作目录或 API 状态），最短录制时长可控（如 10 秒）
  4. `DanmujiEventTrigger` 通过录播姬 HTTP API 停止该房间录制 → 录播姬关闭文件 → 自动发送 `FileClosed` webhook 到系统
  5. 系统接收 webhook → `WebhookService.processWebhookEvent` → 触发同步到 AList + 转码
  6. E2E 断言：AList 目标路径出现文件、任务执行历史持久化、traceId 链路一致

**理由**：
- 用户决策 Q2:C 要求真实录制直播流；通过录播姬 HTTP API "停止录制" 实现"手动关闭直播"的自动化等效（停止录制即关闭文件即触发 FileClosed），无需等待直播自然结束
- 录播姬 WebUI（端口 2356）背后有 HTTP API 可编程控制房间添加/录制开始/停止，实现阶段需查阅 rec.danmuji.org 官方文档确认具体端点与认证方式
- webhook URL 在录播姬配置文件中指向系统端点，实现真实链路（录播姬 → 系统）

**考虑的替代方案**：
- **完全模拟 webhook payload（Q2:B）**：用户已拒绝，要求真实录制
- **真实录制并等待直播自然结束（Q2:C 原始形式）**：不可控且耗时，不适合自动化回归；通过"API 停止录制"保留真实性的同时获得可控性
- **录播姬本地文件模拟录制**：录播姬不支持此模式，需真实直播流

---

## R5. 录播姬真实直播源依赖风险与缓解

**决策**：
- E2E 测试需要一个稳定开播的 B 站直播间作为录制目标，优先选择 24 小时轮播台（实现阶段确定具体房间号，写入 `application-e2e.yaml` 配置）
- E2E 前置检查：`E2ETestBase` 在 `@BeforeAll` 中通过 B 站 API 或录播姬连接状态验证直播源可用性，不可用时 `Assumptions.assumeTrue(false)` 跳过测试（而非失败），并在报告中明确跳过原因
- 录制时长控制：最短 10 秒即可触发 FileClosed，避免回归测试耗时过长（对齐假设"选择可稳定开播的低码率直播源"）
- 失败重试：录制启动失败时重试 2 次（不同房间或延迟重试），仍失败则 skip

**理由**：
- 真实直播源是 Q2:C 决策的固有依赖，无法完全消除；通过"前置检查 + skip 机制"避免 E2E 因外部不可控因素误报失败，保护 CI 信号
- 24 小时轮播台降低直播源不可用概率
- `Assumptions.assumeTrue` 是 JUnit 5 标准跳过机制，测试报告会区分"失败"与"跳过"

**考虑的替代方案**：
- **自建测试直播推流源**：引入 OBS/ffmpeg 推流基础设施，复杂度过高，违反 YAGNI
- **直播源不可用时直接 fail**：外部不可控因素导致 CI 频繁误报，降低 E2E 可信度
- **完全脱离直播源（回退 Q2:B）**：违反用户决策

---

## R6. 测试 fixtures 组织与版本控制策略

**决策**：
- fixtures 根目录 `src/test/resources/fixtures/`，按类型分子目录：`webhook/`（录播姬事件 JSON）、`media/`（测试媒体文件）、`alist/`（AList 期望响应 JSON）
- 录播姬 webhook 样本：`fileclosed-event.json`、`sessionstarted-event.json`，严格按 `md/danmuji/webhook.md` v2 协议构造（含 `EventType`/`EventId`/`EventTimestamp`/`EventData` 四段式），`EventId` 用固定 UUID 便于幂等测试
- 测试媒体：`sample.mp4`（<5MB，短时长低码率），用于转码链路验证；体积控制在 <10MB 总量
- AList 期望响应：`list-response.json` 按 `md/alist/` 契约构造（`{code:200, message:"success", data:{content:[...], total}}`）
- fixtures 纳入版本控制（Git LFS 不引入，体积 <10MB 直接提交）

**理由**：
- fixtures 与测试代码同源管理，保证可追溯与可重复
- 录播姬 webhook 样本基于 `md/danmuji/` 契约构造，确保与真实事件格式一致
- 小尺寸媒体样本平衡转码验证有效性与版本控制体积

**考虑的替代方案**：
- **运行时动态生成 fixtures**：增加测试复杂度，且无法保证与契约一致
- **Git LFS 管理媒体文件**：引入 LFS 基础设施，<10MB 体积无必要
- **大体积真实媒体**：拖慢转码测试，违反"小尺寸样本"假设

---

## R7. E2E 状态清理方案（幂等重复运行）

**决策**：
- `E2ETestBase` 在 `@AfterEach` 或 `@AfterAll` 中执行三级清理：
  1. **数据库清理**：`@Sql` 注解或 `JdbcTemplate` 执行 `TRUNCATE TABLE` 清空 `webhook_event`、`sync_task`、`task_execution`、`transcode_task`、`storage_engine`（保留基础配置数据或全清后重新 seed）
  2. **文件系统清理**：删除 AList 数据目录下测试产生的文件、系统 `app.data-dir` 下的同步/转码产物、录播姬工作目录下的录制文件
  3. **临时文件清理**：删除 `app.transcode.temp-dir` 下的临时转码文件
- E2E 使用独立的 `application-e2e.yaml`，数据目录与生产隔离（如 `./data-e2e/`），避免污染开发数据
- 清理操作幂等：文件不存在不报错，数据库表已空不报错
- SC-004 验证：连续 3 次运行 E2E 套件全部通过

**理由**：
- 幂等清理是 E2E 可重复运行的基础（FR-009、SC-004）
- 独立数据目录隔离 E2E 与开发/生产数据，避免误删用户数据
- 三级清理覆盖所有可能残留状态的位置

**考虑的替代方案**：
- **每次 E2E 重新下载二进制**：耗时过长，违反 SC-001（10 分钟内完成环境准备）
- **不清理，依赖唯一命名避免冲突**：长期运行会积累垃圾文件，数据库膨胀
- **Docker 容器化隔离（每次新建容器）**：偏离 Q1:A 二进制本地启动决策

---

## R8. 端口冲突检测与避让

**决策**：
- `E2ELifecycleManager` 启动外部依赖前检测端口占用：AList（5344）、录播姬 WebUI（2356）、系统（8080 或 `server.port` 配置值）
- 检测方式：尝试 `Socket.connect` 目标端口，已占用则：
  - 若占用进程是本功能上一次未清理的实例（通过 PID 文件识别）：自动 kill 后重启
  - 若是其他进程：报错并输出明确指引（"端口 5344 被 PID xxx 占用，请释放或修改 `scripts/e2e/e2e-config/` 端口配置"），不强行 kill（避免误杀用户进程）
- PID 文件：`scripts/e2e/bin/alist.pid`、`scripts/e2e/bin/danmuji.pid`，记录启动的进程 PID，用于 `stop-e2e-env.ps1` 精准停止

**理由**：
- 端口冲突是 E2E 环境准备的常见边界情况（spec 边界情况已识别）
- PID 文件机制实现精准停止，避免误杀同名进程
- 不强行 kill 未知进程，遵循"危险操作确认"原则（output style 危险操作机制）

**考虑的替代方案**：
- **全部用随机端口**：AList/录播姬端口配置复杂，且录播姬 webhook 回调需要系统端口固定可达，随机端口增加配置复杂度
- **检测到占用直接 kill**：可能误杀用户其他进程，违反危险操作原则

---

## R9. Testcontainers 明确拒绝理由（补充复杂性追踪）

**决策**：不引入 Testcontainers。

**理由**：
- 集成测试用 H2 嵌入式数据库（项目已选用，`application-test.yaml` 已配置），无需 Testcontainers 启动真实数据库容器
- AList 客户端契约测试用 WireMock（R1），无需 Testcontainers 启动真实 AList 容器
- E2E 用真实二进制本地启动（Q1:A），无需 Testcontainers 编排
- 引入 Testcontainers 会带来 Docker 强依赖，与"Windows 开发环境优先、二进制本地启动"决策冲突，违反 YAGNI

**考虑的替代方案**：无（Testcontainers 在本功能任何层级都无适用场景）。

---

## 研究结论

所有技术未知项已解决，无残留 `[需要澄清]` 标记。关键决策汇总：

| 研究项 | 决策 | 章程对齐 |
|--------|------|---------|
| R1 | WireMock standalone + `@WireMockTest` | VI 违规已证明（复杂性追踪） |
| R2 | failsafe + 3 个 Maven profile | VI 违规已证明（复杂性追踪） |
| R3 | AList GitHub Releases 二进制 + SHA256 + 幂等 | 对齐 005 下载模式 |
| R4 | 录播姬 HTTP API 控制录制-停止-触发 | 对齐 Q2:C |
| R5 | 稳定直播源 + 前置检查 + skip 机制 | 风险缓解 |
| R6 | fixtures 分目录 + 小尺寸 + 版本控制 | 对齐假设 |
| R7 | 三级清理 + 独立数据目录 | 满足 FR-009/SC-004 |
| R8 | 端口检测 + PID 文件 + 不误杀 | 对齐危险操作原则 |
| R9 | 拒绝 Testcontainers | YAGNI |

**待实现阶段确认的细节**（非阻塞，方向已定）：
- 录播姬 HTTP API 具体端点与认证方式（查阅 rec.danmuji.org 官方文档）
- B 站 24 小时轮播台具体房间号（E2E 配置时确定）
- AList 与录播姬的精确版本号锁定（下载脚本实现时确定当前稳定版）
