# 更新日志

本文件记录 AList-Media-Sync 项目的所有显著变更。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)，
日期采用 ISO 8601 格式（YYYY-MM-DD）。

## 分类说明

- **Added** 新增的功能
- **Changed** 对已有功能的变更
- **Deprecated** 即将移除的功能
- **Removed** 已移除的功能
- **Fixed** 错误修复
- **Security** 安全相关的修复

## [Unreleased]

### Added

- 新增顶层 `ops` 模块（跨模块运维聚合）：`DashboardService`（仪表盘聚合统计）、`CleanupService`（过期记录与临时文件清理）、`DiagnosticService`（诊断包生成）及配套入口层 `DashboardController` / `DiagnosticController` / `SystemMcpTools`
- 新增顶层 `execution` 模块（共享任务执行记录）：`TaskExecution` 实体、`TaskExecutionRepository`、`TaskExecutionVO`，被 sync / transcode / webhook / ops 共用
- 新增依赖倒置接口：`sync/service/PostSyncTranscodeTrigger`（由 `transcode/TranscodeService` 实现）、`common/service/TempFileCleanupTrigger`（由 `ops/CleanupService` 实现）
- 新增共享产物：`common/util/JsonUtils`、`common/util/MapUtils`、`common/interceptor/BasicAuthVerifier`（HTTP 与 WebSocket 认证共用凭据校验）；自 `TranscodeService` / `TranscodeFileProcessor` 按职责拆出 `transcode/service/TranscodeScanner`、`TranscodeStateMachine`、`TranscodeTaskStateWriter`
- 新增模块文档：`ops/AGENTS.md`、`execution/AGENTS.md`

### Changed

- 类型迁移：`FileEntry` / `DirectoryEntryVO` 由 `sync/dto/sync/` 迁至 `storage/dto/`（作为存储策略接口的契约类型，供 sync / transcode 共用）；`ConflictStrategy` / `TargetFormat` 由 `SyncTask` / `TranscodeTask` 的内嵌枚举下沉为 `common/enums/` 共享枚举（`TargetFormat` 两处重复定义合并为一处）
- 包结构扁平化：`storage` / `sync` / `transcode` / `webhook` 四个模块的 `dto/<module>/` 冗余嵌套包扁平化为 `dto/`
- 实体关联降级（切断模块环）：`TaskExecution` 的 `syncTask` / `transcodeTask` / `webhookEvent` 与 `TranscodeTask` 的 `syncTask` / `webhookRule` 由 `@ManyToOne` 实体引用降级为 `Long xxxId` + `@Column`，数据库列名一字未变
- 依赖倒置（切断循环依赖）：`sync` 触发转码改经 `PostSyncTranscodeTrigger` 接口，不再 import `transcode`；`transcode` 两个入口（Controller 与 MCP 工具）清理临时文件改经 `TempFileCleanupTrigger` 接口，不再 import `ops`
- Webhook 建临时同步任务改经 `SyncTaskManageService.createWebhookTempTask`，不再绕过 Service 层直接写入 `sync_task` 表
- 构建配置：JAVE2 版本抽为 `${jave.version}` 属性统一管理；`maven-antrun-plugin` / `maven-assembly-plugin` 版本交回 BOM 管理；`springdoc-openapi` 与 MCP starter 的注释改为如实描述两代 Jackson 并存
- 文档同步：`docs/03-架构设计.md` 的包结构总览与模块依赖关系、`docs/architecture/` 模块细化文档、根 `AGENTS.md` 与各模块 `AGENTS.md` 索引按重构后结构更新

### Removed

- 删除死代码：`common/enums/MessageType`、`common/dto/DiagnosticSummaryVO`、`sync/dto/sync/SyncProgressVO`
- 删除零调用方法：`TempFileManager.normalizeSuffix`、`MagicBytesDetector.detect(byte[])`、`WsSessionManager.broadcastDashboardUpdate` / `getConnectionCount` / `getMaxConnections`、`TranscodeService.getOutputName`（2 处重载）、`DiagnosticService.throwUnchecked` / `DiagnosticIOException`
- 移除 `pom.xml` 中零使用的 `spring-boot-starter-validation-test` 依赖，以及与主源码 Jackson 3 重复的 `com.fasterxml.jackson.core:jackson-databind` 显式声明

## [0.2.1] - 2026-08-25

### Fixed

- 修复转码自动重试无限循环：重试时新建任务导致 `retryCount` 归零、永不达上限，改为复用同一任务记录并支持断点续传（下载/转码/上传按失败步骤继续）
- 修复转码手动重试仅改状态不执行：`retry()` 状态回退后经自代理真正触发 `executeTask()`，任务不再卡死
- 修复编排级转码失败（扫描/收集异常、全部文件失败）任务永久停留在 PENDING，新增 `FAILED` 状态
- 修复下载/上传瞬时故障无法触发自动重试（异常未实现 `RetryableException` 标记），新增 `RetryableIOException`
- 修复转码 `executeTask` 同类自调用导致 `@Transactional` 失效
- 修复本地存储路径穿越漏洞：`resolvePath()` 校验解析路径必须位于引擎根目录内，拒绝 `..` 与绝对路径逃逸
- 修复同步长事务：扫描/下载/上传等网络 I/O 移出事务，仅"加载+创建执行记录"使用短事务，避免数小时持锁与中途失败整体回滚
- 修复同一同步任务并发重复触发（定时调度与手动触发重叠），新增运行中任务集合防重
- 修复删除运行中的同步任务破坏执行线程，运行中禁止删除
- 修复目录扫描分页无上限保护（极端场景死循环），增加 1000 页上限
- 修复 FULL 模式删除目标多余文件失败不计数，删除失败计入失败明细
- 修复本地引擎 `listFiles` 逐页全量扫描排序的 O(n²)，改为一次性返回全量
- 修复 Webhook 并发重发 EventId 竞态导致唯一索引冲突异常，捕获后重查返回既有事件

### Security

- 收紧 Webhook 认证边界：仅 `/api/webhooks/recorder`（录播姬回调）免认证，同前缀下的事件查询等管理接口恢复 Basic Auth 保护

## [0.2.0] - 2026-08-18

### Added

- MCP 服务器（AI 操作接口）（specs/013）：嵌入标准 MCP 服务器（Streamable HTTP 传输），端点 `POST /mcp` 与主应用同端口，使 AI 客户端（如 Claude Code）可按模块发现并调用工具操作系统
- 五大模块 36 个 MCP 工具（specs/013）：存储引擎 8（CRUD/连接测试/目录浏览）、同步任务 9（CRUD/触发/启停/执行历史）、转码任务 8（CRUD/重试/清理/批量）、Webhook 8（规则 CRUD/启停/事件查询）、系统运维 2（仪表盘统计/诊断包生成），另含 1 个流程级快捷工具「创建并立即触发一次同步」
- MCP 独立 Bearer Token 认证（specs/013）：`app.mcp.token`（环境变量 `MCP_TOKEN`）与 Web 管理 Basic Auth 凭据隔离，未认证/无效令牌 100% 拒绝且不泄露业务数据
- MCP 默认禁用机制（specs/013）：`app.mcp.enabled=false` 默认关闭，仅显式开启且令牌非空时提供服务，未配置令牌时启动报错拒绝启用，不影响现有 Web 管理界面与 `/api/**`
- MCP 工具可观测性（specs/013）：每次工具调用注入唯一 traceId 与 module=mcp/operation=工具名 日志，敏感凭据（存储引擎 Token 等）脱敏后返回

## [0.1.0] - 2026-07-25

### Added

- 文档体系优化功能（specs/011）：规范文档系统以支持手工引导区与生成区分界，新增 CONTRIBUTING.md 贡献者入口文档
- 端到端测试基础设施（specs/012）：补全测试金字塔的集成测试层与端到端测试层，新增 6 个 Repository 集成测试（`@DataJpaTest`）、AList 客户端 WireMock 契约测试、3 条核心业务链路 E2E 测试 + traceId 全链路验证
- Maven profile 隔离机制（specs/012）：通过 `maven-failsafe-plugin` 与 3 个 profile（默认/`-Pintegration`/`-Pe2e`）实现单元/集成/端到端测试物理隔离，E2E 串行执行 + 15 分钟超时兜底
- E2E 环境准备脚本（specs/012）：一键下载 AList 二进制（录播姬可选），支持 SHA256 校验、幂等跳过、本地路径跳过（`ALIST_LOCAL_PATH`）、动态端口分配（FR-014）与失败现场保留（FR-009）
- nightly CI 定时运行 E2E 测试（specs/012）：GitHub Actions `schedule.cron` 定时触发 `mvn verify -Pe2e`，PR 流水线不触发 E2E（FR-013）

## [0.0.1-SNAPSHOT] - 2026-07-02

### Added

- AList 媒体同步与转码工具核心功能（specs/001）：实现 AList 媒体文件的自动同步与转码，支持录播姬 Webhook 触发、转码任务管理、文件同步策略等核心能力
- 转码临时文件可配置后缀（specs/002）：支持自定义转码临时文件后缀与清理策略，避免临时文件污染媒体库
- Docker 容器化部署支持（specs/003）：提供 Dockerfile 与容器化部署方案，支持一键容器化运行
- Web 管理前端界面（specs/004）：基于 React 19 + TypeScript + Vite + Tailwind CSS 实现完整 Web 管理界面，包含转码任务管理、同步任务配置、存储策略管理、目录树选择器等组件
- 一体化启动包（specs/005）：支持前后端一体化打包启动，简化部署流程
- 存储引擎重构与体验优化（specs/006）：将存储引擎重构为策略模式，支持 AList 与本地双类型存储引擎，优化转码任务状态模型为 8 状态统一模型
- 密码加密优化与代码目录重组（specs/007）：引入 BCrypt 密码加密、加密密钥环境后处理器，管理员密码配置改为环境变量支持，重组代码目录结构
- 转码与同步模块优化及实时通信改造（specs/008）：优化转码同步模块并集成 WebSocket 实时通信，实现自动重试与实时推送功能，重构 WebSocket 消息类型定义
- 轻量诊断系统（specs/009）：添加轻量级诊断系统和 traceId 追踪功能，实施 API 调用与本地目录操作日志规范，优化 traceId 生成格式
- Service 层重复代码与超长方法重构（specs/010）：重构服务层重复代码和样板代码，优化同步服务中的目标目录扫描逻辑，添加文件移动功能支持
- 项目构建与环境检查脚本
- 自定义状态码响应方法与存储引擎状态管理
- 文件选择模式支持

### Changed

- 将 Jackson ObjectMapper 替换为 JsonMapper
- 将 AListApiClient 替换为 ApiUtil 工具类
- 重构 AList API 工具类和存储引擎实现
- 重构转码服务支持目录模式和文件模式统一处理
- 优化转码服务的实体管理和状态更新逻辑
- 重构 WebSocket 消息类型定义并优化组件功能
- 优化文件同步逻辑以支持相对路径匹配
- 将 sameDirectoryTranscode 重命名为 sourceDirectoryTranscode
- 重构测试代码以改进依赖注入和模拟配置
- 配置项目编码为 UTF-8
- 改进项目根目录查找逻辑和命令格式化

### Fixed

- 修复 AList 存储引擎连接测试问题
- 修复转码文件输出目录计算逻辑
- 修复 AList 文件下载功能
- 修复 AList 存储引擎文件下载逻辑
- 修复本地存储策略空目录和分页逻辑问题
- 修复转码任务创建 DTO 中布尔字段反序列化问题
- 修复 AList 存储策略中路径解析问题
- 修复 AList 存储引擎分页获取文件问题
- 修复目录树选择器中的重复路径和无限递归问题
- 解决转码过程中乐观锁冲突问题
- 修复 BCrypt 密码加密验证逻辑
- 修复加密转换器密钥管理和 Chrome 开发者工具探测问题
- 修复转码操作的目标存储引擎验证问题
- 修复前端静态资源配置问题
- 修复服务器地址日志输出格式
- 解决覆盖模式下文件同步问题

### Security

- 修复管理员账户密码哈希与验证逻辑
- 将管理员密码配置改为环境变量支持，避免硬编码敏感信息
