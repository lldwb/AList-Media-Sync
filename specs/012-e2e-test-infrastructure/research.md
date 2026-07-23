# 研究文档：端到端测试基础设施

**功能**：`012-e2e-test-infrastructure` | **阶段**：0（研究） | **日期**：2026-07-23

**输入**：[plan.md](./plan.md) 技术上下文中的未知项与章程检查的 VI 违规项

本文档解决 plan.md 中的技术未知项，每项给出**决策 / 理由 / 考虑的替代方案**。所有决策对齐章程原则 VI（YAGNI）与用户澄清决策（FR-010 二进制本地启动、FR-011 webhook payload 重放驱动、FR-013 CI nightly 定时、FR-014 动态端口分配、FR-009 失败保留现场）。

---

## R1. WireMock 选型与集成方式

**决策**：引入 `wiremock-standalone:3.9.1`（或当前稳定版），通过 JUnit 5 扩展 `@WireMockTest` 注解集成，端口由 WireMock 动态分配（`dynamicPort`），测试类通过 `WireMockRuntimeInfo` 获取实际端口注入 `AListStorageStrategy` 的 RestClient 配置。

**理由**：
- `wiremock-standalone` 是 shaded jar，无传递依赖冲突风险（与 Spring Boot 4.1 / Spring Framework 7.x 兼容）
- `@WireMockTest` 注解自动管理 WireMock 服务器生命周期（启动/停止），无需手写 `@BeforeEach`/`@AfterEach`
- 动态端口分配避免与本地 AList 实例或 E2E 实例端口冲突（对齐 FR-014 动态端口策略）
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
  - `-Pe2e`：激活 failsafe 运行 `*E2ETest.java`，通过 `E2ELifecycleManager` 自动启动 AList 二进制实例（录播姬二进制可选，默认不启动）
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
- **Docker 启动 AList 容器**：用户决策 FR-010 明确要求二进制本地启动，偏离原意
- **不锁版本，始终下载最新版**：AList API 变更会导致 E2E 漂移，违反可重复性（SC-004）
- **下载源码自行编译**：引入 Go 工具链依赖，过度复杂

---

## R4. webhook payload 重放驱动机制（FR-011 主路径）

**决策**：
- E2E 录播姬事件通过 **webhook payload 重放**驱动：`WebhookEventReplayer` 读取 `src/test/resources/fixtures/webhook/` 下版本化的事件样本（如 `fileclosed-event.json`），直接以 HTTP POST 注入系统 `/api/webhooks/recorder` 端点，驱动真实系统处理 + 真实 AList 同步链路
- 录播姬二进制变为**可选验证**：仅当需验证"录播姬真实发送 webhook 格式"时才启动录播姬实例（通过 `-Ddanmuji.enabled=true` 或 E2E profile 子选项激活），默认 E2E 运行不启动录播姬、不依赖真实直播源
- 重放样本严格按 `md/danmuji/webhook.md` v2 协议构造（含 `EventType`/`EventId`/`EventTimestamp`/`EventData` 四段式），`EventId` 用固定 UUID 便于幂等去重测试
- `WebhookEventReplayer` 支持参数化：指定 fixtures 路径、重复发送次数（验证幂等）、自定义 EventId

**理由**：
- 用户决策（2026-07-23 澄清）完全放弃真实录制直播流，改为重放驱动，消除直播源不稳定对回归可重复性的阻塞（SC-004 要求 3 次真实通过）
- 重放注入的是真实 HTTP 请求（非 Mock），系统接收到的是与真实录播姬格式一致的 webhook，链路真实性由"真实系统处理 + 真实 AList 同步"保证，仅事件来源从"录播姬产生"变为"fixtures 重放"
- 录播姬可选验证保留了"验证真实录播姬 webhook 格式"的能力，但不阻塞主路径

**考虑的替代方案**：
- **真实录制直播流（旧 Q2:C 方案）**：依赖外部直播源稳定性，直播源下线即阻塞回归，与 SC-004 的"3 次真实通过"存在不可调和矛盾，已被 2026-07-23 澄清明确拒绝
- **录播姬本地文件模拟录制**：录播姬不支持此模式，需真实直播流，已不适用
- **完全 Mock WebhookService**：失去"真实系统处理 + 真实 AList 同步"的链路真实性，退化为单元测试，失去 E2E 价值

---

## R5. webhook payload 契约漂移感知

**决策**：
- webhook payload fixtures 作为录播姬 Webhook v2 协议的**契约基准**，固化在版本控制中（`src/test/resources/fixtures/webhook/`）
- 集成测试层新增契约一致性校验：解析 fixtures 并与 `md/danmuji/webhook.md` 文档化的协议结构对比，字段缺失或类型偏差即测试失败
- 当录播姬 Webhook v2 协议新版本发布时，fixtures 与文档同步更新，更新时在 fixtures 文件头注释记录协议版本与变更说明
- 录播姬可选验证路径（R4）启动真实录播姬时，可对比真实事件与 fixtures 格式差异，作为契约漂移的运行时检测手段

**理由**：
- 重放方案下 fixtures 是事件来源的事实标准，其契约一致性是 E2E 正确性的基础
- 静态校验（fixtures vs 文档）成本低且可在集成测试层自动执行，无需真实录播姬
- 边界情况"webhook payload fixtures 与录播姬 Webhook v2 协议新版本契约漂移"通过此机制感知

**考虑的替代方案**：
- **不校验，依赖人工维护**：fixtures 易与协议漂移，违反可重复性
- **录制真实录播姬事件生成 fixtures**：需启动录播姬 + 直播源，引入已拒绝的外部依赖

---

## R6. 测试 fixtures 组织与版本控制策略

**决策**：
- fixtures 根目录 `src/test/resources/fixtures/`，按类型分子目录：`webhook/`（录播姬事件 JSON，E2E 重放主路径输入）、`media/`（测试媒体文件）、`alist/`（AList 期望响应 JSON）
- 录播姬 webhook 样本：`fileclosed-event.json`、`sessionstarted-event.json`，严格按 `md/danmuji/webhook.md` v2 协议构造（含 `EventType`/`EventId`/`EventTimestamp`/`EventData` 四段式），`EventId` 用固定 UUID 便于幂等测试
- 测试媒体：`sample.mp4`（<5MB，短时长低码率），用于转码链路验证；体积控制在 <10MB 总量
- AList 期望响应：`list-response.json` 按 `md/alist/` 契约构造（`{code:200, message:"success", data:{content:[...], total}}`）
- fixtures 纳入版本控制（Git LFS 不引入，体积 <10MB 直接提交）

**理由**：
- fixtures 与测试代码同源管理，保证可追溯与可重复
- 录播姬 webhook 样本基于 `md/danmuji/` 契约构造，作为 E2E 重放主路径输入（FR-011），契约一致性由 R5 校验保障
- 小尺寸媒体样本平衡转码验证有效性与版本控制体积

**考虑的替代方案**：
- **运行时动态生成 fixtures**：增加测试复杂度，且无法保证与契约一致
- **Git LFS 管理媒体文件**：引入 LFS 基础设施，<10MB 体积无必要
- **大体积真实媒体**：拖慢转码测试，违反"小尺寸样本"假设

---

## R7. E2E 状态清理与失败现场保留（FR-009）

**决策**：
- `E2ETestBase` 区分"成功后清理"与"失败后保留"两种策略：
  - **成功后清理**：`@AfterEach` 在测试通过时执行三级清理（数据库 `TRUNCATE`、文件系统删除、临时文件删除）
  - **失败后保留**：测试失败时 `@AfterEach` 跳过清理，保留数据库与文件系统现场，输出诊断指引（指向 `data-e2e/` 目录与诊断包入口 `scripts/diagnose.{sh,bat}`）
- **下次运行前强制清理**：`@BeforeAll` 或 `@BeforeEach` 执行强制清理，无论上次是否失败，确保本次运行从干净状态开始（保证幂等，SC-004）
- 链路间清理（FR-012）：每条链路 `@AfterEach`（成功时）清理本链路产生的状态，失败时保留
- E2E 使用独立的 `application-e2e.yaml`，数据目录与生产隔离（`./data-e2e/`），避免污染开发数据
- 清理操作幂等：文件不存在不报错，数据库表已空不报错

**理由**：
- 失败现场对问题定位至关重要，与 specs/009 诊断系统协作，保留现场可供维护者通过诊断包追溯（章程原则 VII 可观测性）
- 下次运行前强制清理保证幂等性（FR-009、SC-004），失败现场不会污染后续运行
- 独立数据目录隔离 E2E 与开发/生产数据，避免误删用户数据

**考虑的替代方案**：
- **失败后立即清理（@AfterEach 无条件执行）**：丢失失败现场，问题难以定位，违反可观测性
- **失败后自动归档到诊断包再清理**：增加测试基础设施复杂度，YAGNI；诊断包已有手动入口（specs/009），保留现场即可
- **不清理，依赖唯一命名避免冲突**：长期运行会积累垃圾文件，数据库膨胀

---

## R8. 动态端口分配（FR-014）

**决策**：
- `E2ELifecycleManager` 启动外部依赖前**动态探测空闲端口**分配给 AList、录播姬（可选）、系统实例，禁止依赖固定端口假设
- 探测方式：`ServerSocket(0)` 让操作系统分配可用端口，或遍历候选端口范围（如 30000-40000）尝试 `Socket.connect` 检测空闲
- 分配的端口注入到各实例配置：AList `alist.config.json`、录播姬 `danmuji.config.toml`（可选）、系统 `application-e2e.yaml` 的 `server.port` 与 `alist.base-url`
- 端口分配结果写入 `scripts/e2e/data/ports.json`（运行时文件，`.gitignore` 忽略），供 `stop-e2e-env.ps1` 与诊断脚本读取
- 探测失败或可分配端口范围耗尽时，报错并输出明确指引（"无可用端口，请检查系统端口占用或扩大候选范围"）

**理由**：
- 动态端口分配消除端口占用冲突风险（FR-014），支撑 SC-005 新贡献者搭建成功率目标
- 固定端口假设在开发机（常已运行其他服务）易冲突，动态分配是标准实践
- 端口注入配置保证各实例间端口引用一致（如系统 `alist.base-url` 指向 AList 实际端口）

**考虑的替代方案**：
- **固定端口 + 占用时报错**（旧 R8 方案）：要求端口空闲，新贡献者环境易冲突，违反 SC-005 成功率目标，已被 2026-07-23 澄清（FR-014）明确拒绝
- **固定端口 + 自动 kill 占用进程**：可能误杀用户其他进程，违反危险操作确认原则
- **全部用随机端口但不注入配置**：各实例端口引用不一致，链路断裂

---

## R9. Testcontainers 明确拒绝理由（补充复杂性追踪）

**决策**：不引入 Testcontainers。

**理由**：
- 集成测试用 H2 嵌入式数据库（项目已选用，`application-test.yaml` 已配置），无需 Testcontainers 启动真实数据库容器
- AList 客户端契约测试用 WireMock（R1），无需 Testcontainers 启动真实 AList 容器
- E2E 用真实二进制本地启动（FR-010），无需 Testcontainers 编排
- 引入 Testcontainers 会带来 Docker 强依赖，与"Windows 开发环境优先、二进制本地启动"决策冲突，违反 YAGNI

**考虑的替代方案**：无（Testcontainers 在本功能任何层级都无适用场景）。

---

## R10. CI nightly 集成方案（FR-013）

**决策**：
- E2E 测试套件纳入 CI 仅以**定时任务**方式运行（如 GitHub Actions 的 `schedule.cron`，每日凌晨触发 `mvn verify -Pe2e`）
- PR 流水线**不触发 E2E**，仅运行 `mvn verify -Pintegration`（单元 + 集成测试），避免 60 分钟 E2E 阻塞协作
- nightly CI 运行环境需准备 AList 二进制：CI 脚本调用 `scripts/e2e/prepare-e2e-env.sh`（Linux）下载 AList 二进制，配置 `application-e2e.yaml` 使用 Linux 文件模式 H2
- nightly 失败通过 CI 通知机制（如 GitHub Actions 失败邮件/状态检查）告警，不阻塞 PR 合并
- nightly CI 配置文件（如 `.github/workflows/e2e-nightly.yml`）纳入版本控制

**理由**：
- E2E 总时长约 60 分钟（SC-006），每次 PR 运行成本过高且易阻塞协作，定时运行平衡回归保障与 CI 效率（FR-013）
- 重放方案（R4）使 CI 中运行 E2E 技术可行（无直播源依赖），只需 AList 二进制与 fixtures
- nightly 失败不阻塞 PR，但提供定期回归信号，符合 YAGNI（按需引入 CI 集成而非全量强制）

**考虑的替代方案**：
- **E2E 纳入每次 PR 流水线**：60 分钟耗时阻塞协作，违反 FR-013 与效率目标
- **E2E 仅本地手动运行，不纳入 CI**：放弃 CI 中的定期回归信号，回归保障依赖人工
- **E2E 在 CI 中每次提交运行但只跑部分链路**：增加配置复杂度，且部分链路无法代表全链路可用性

---

## 研究结论

所有技术未知项已解决，无残留 `[需要澄清]` 标记。关键决策汇总：

| 研究项 | 决策 | 章程对齐 |
|--------|------|---------|
| R1 | WireMock standalone + `@WireMockTest` | VI 违规已证明（复杂性追踪） |
| R2 | failsafe + 3 个 Maven profile | VI 违规已证明（复杂性追踪） |
| R3 | AList GitHub Releases 二进制 + SHA256 + 幂等 | 对齐 005 下载模式、FR-010 |
| R4 | webhook payload 重放驱动 + 录播姬可选 | 对齐 FR-011、SC-004 |
| R5 | fixtures 契约基准 + 静态校验 | 契约漂移感知 |
| R6 | fixtures 分目录 + 小尺寸 + 版本控制 | 对齐假设 |
| R7 | 成功清理 + 失败保留 + 下次强制清理 | 满足 FR-009/SC-004 |
| R8 | 动态端口分配 + 配置注入 | 对齐 FR-014、SC-005 |
| R9 | 拒绝 Testcontainers | YAGNI |
| R10 | CI nightly 定时运行 E2E | 对齐 FR-013 |

**待实现阶段确认的细节**（非阻塞，方向已定）：
- AList 与录播姬（可选）的精确版本号锁定（下载脚本实现时确定当前稳定版）
- nightly CI 的具体 cron 时间与通知渠道（实现阶段根据团队习惯确定）
- 动态端口候选范围与探测策略的精确参数（实现阶段性能调优时确定）
