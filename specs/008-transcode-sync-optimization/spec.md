# 功能规格：转码与同步模块优化及实时通信改造

**功能分支**：`008-transcode-sync-optimization`

**创建日期**：2026-06-21

继续**状态**：已完成

**输入**：用户描述：

**转码模块：**
1. 创建转码任务页面时，选择"原目录转码(输出至源文件所在目录)"不需要并且前端隐藏"目标文件路径"和"目标存储引擎"的选择
2. 创建转码任务页面时，"原目录转码(输出至源文件所在目录)"改成"源目录转码(输出至源文件所在目录)"
3. 转码任务页面，不显示目录只显示文件，文件应该输出：路径/文件
4. 转码任务页面，添加"清理失败任务"、"成功任务"和"重试所有失败文件"的功能
5. 转码任务时选择源目录转码，文件转码后的路径应该是：原文件路径/文件.指定文件格式

**同步模块：**
1. 如果是相同存储引擎就调用接口的复制方法，如果是不同存储引擎就调用接口的下载→上传（存储在临时文件夹里）

**公共模块：**
1. 对于持续轮询的接口（如 /api/sync-tasks），改成 WebSocket 或者其他实现方式，避免频繁请求

## 澄清

### 会话 2026-06-21

- Q: WebSocket 消息推送的数据粒度——是推送完整实体列表还是仅增量变更？ → A: 增量变更。仅推送变更的实体和变更字段（如 `{ type: "TRANSCODE_PROGRESS", payload: { taskId: 1, progressPercent: 45, status: "TRANSCODING" } }`），前端合并到本地状态。不推送全量列表。
- Q: 批量操作（重试所有失败文件）的 UI 反馈方式？ → A: 按钮加载态 + 结果提示。点击后按钮显示加载态并禁用，API 返回后解除禁用并弹出简短结果提示（如"已重试 5 个任务"），然后自动刷新列表。
- Q: WebSocket 并发连接数上限？ → A: 可配置。在 `application.yaml` 中提供配置项（如 `app.websocket.max-connections`），默认值 50。超过上限时拒绝新连接并返回 HTTP 429 状态码。
- Q: 重试所有失败文件的 API 执行模式（同步等待 vs 异步执行）？ → A: 异步执行。`POST /api/transcode-tasks/retry-all` 立即返回 202 Accepted，实际重试在后端异步执行。每个任务的重试结果通过 WebSocket 逐条推送（`TRANSCODE_PROGRESS` 消息），前端自动更新列表状态，用户无需手动刷新。
- Q: 哪些页面改用 WebSocket，哪些保留轮询？ → A: 所有列表页面全面改为 WebSocket（转码任务列表、同步任务列表、Webhook 事件列表、Dashboard 仪表板）。`usePolling.ts` 删除，不再保留。
- Q: 同引擎复制时是否需要按文件大小分流（大文件回退到下载→上传）？ → A: 不需要。统一使用引擎的 copy 方法（AList `/api/fs/copy`、本地 `Files.copy`），不做大小分流。AList 服务端 copy 是内部操作不经过客户端网络，无 HTTP 超时风险。
- Q: "原目录转码"改为"源目录转码"的变更范围？ → A: UI 文案全面改为"源目录转码"，DTO 字段名从 `sameDirectoryTranscode` 改为 `sourceDirectoryTranscode`。其他 spec 文件（007、001、004 等）不做回改（历史文档保持原样）。
- Q: WebSocket 认证失败或连接不可用时的前端降级策略？ → A: 显示连接错误提示并持续尝试重连（指数退避，最大 30 秒），不退化为 HTTP 轮询。若因认证失败（401），引导用户重新登录（与 REST API 行为一致）。
- Q: 008 的转码任务状态模型依赖——001 的 6 状态模型 vs 006 的 8 状态模型？ → A: 008 以 006 的 8 状态模型（PENDING/DOWNLOADING/DOWNLOAD_FAILED/TRANSCODING/TRANSCODE_FAILED/UPLOADING/UPLOAD_FAILED/COMPLETED）已完成为前提，直接使用 8 状态枚举。批量操作（FR-012~FR-014）依赖 DOWNLOAD_FAILED、TRANSCODE_FAILED、UPLOAD_FAILED 状态。
- Q: WebSocket 握手的认证凭据传递方式（URL 查询参数 vs Authorization 请求头 vs 首条消息）？ → A: 利用 HTTP Upgrade 握手的 `Authorization` 请求头（与 REST API 的 Basic Auth 一致）。前端在建立 WebSocket 连接时，由后端的握手拦截器读取 `Authorization` 请求头进行认证。认证失败时拒绝 WebSocket 升级请求。
- Q: 002（启动时无条件清理）与 006（24 小时定时清理）的临时文件清理策略冲突？ → A: 统一采用 006 的策略——上传成功后立即清理临时文件，失败状态下保留供重试，定时任务清理超过 24 小时的孤立任务和临时文件。替代 002 的启动时无条件清理策略。
- Q: 同步模块"不同引擎下载→上传"流程的中间临时文件存储位置？ → A: 使用独立子目录（如 `{temp-dir}/sync/`），独立的清理策略但同样遵循 24 小时定时清理。不直接使用转码临时目录，因为同步中间文件（完整原文件副本）和转码中间文件（转换后的输出）有不同的语义和生命周期。
- Q: DTO 字段 `sameDirectoryTranscode` → `sourceDirectoryTranscode` 重命名在 007 可能已实现时的兼容处理？ → A: 008 的实现检查现有代码：若 007 已完成则修改为 `sourceDirectoryTranscode`；若 007 尚未实现，则 008 直接使用新字段名，007 的实现者后续看到时自然使用新名称。
- Q: 001 边界情况中的"断点续传/自动重试机制"和"API 不可用时的重试策略（指数退避）"由哪个 spec 承接？002 明确将自动重试推迟到后续功能（FR-010），008 也未涉及自动重试。当前无 spec 覆盖此能力。 → A: 在 008 中纳入自动重试逻辑。重试次数在前端任务列表中显示，最大自动重试次数通过配置文件（`app.retry.max-auto-retries`）配置。重试采用指数退避策略（初始间隔 1 秒，每次翻倍，最大间隔 60 秒）。
- Q: 001 FR-012（转码冲突策略）与 008 FR-022（同步同引擎复制的冲突策略）是否共享同一套冲突策略枚举（OVERWRITE/SKIP/RENAME）？在 006 的转码三步流程中，上传阶段和目标引擎 copy 阶段各自如何使用冲突策略？ → A: 完全共享。同步和转码使用同一 `ConflictStrategy` 枚举（OVERWRITE/SKIP/RENAME），由各自的 Service 在对应步骤（上传/copy）中统一查询策略并执行。共享枚举减少代码重复，符合 DRY 原则。
- Q: 008 新增的类应放置在 007 定义的哪个功能模块包中？具体包括：WebSocket 相关类（WebSocketConfig、WsSessionManager、WsMessage 等）、批量操作 API（TranscodeTaskController 新增端点）、同步复制逻辑（AListStorageStrategy/LocalStorageStrategy 新增 copyFile）、前端 useWebSocket Hook。 → A: WebSocket 相关放入 `common/`（通用基础设施，与 AsyncConfig、WebMvcConfig 同级），批量操作在 `transcode/controller/` 和 `transcode/repository/`，copyFile 在 `storage/service/engine/`，前端 useWebSocket Hook 在原有 hooks 目录。遵循 007 的分包原则。
- Q: 006 FR-008 将 JAVE2 编解码器 codec 设为 null（让 FFmpeg 自动选择）。008 的转码路径修正（FR-006~FR-009）和批量重试（FR-014）是否依赖此变更？若 006 未完成，008 的转码测试会受影响吗？ → A: 008 不依赖 codec=null 变更。路径拼接逻辑（sourceDirectoryTranscode 计算 targetPath）和批量操作（按状态删除/重试）与 JAVE2 编解码器参数完全解耦，可独立测试。
- Q: 008 将 Dashboard 改为 WebSocket 推送（FR-026 定义 DASHBOARD_UPDATE 消息类型），但 004 A7 定义了 `GET /api/dashboard/stats` REST API 用于仪表板初始加载。Dashboard REST API 如何与 WebSocket 共存？ → A: 保留 REST API 用于初始加载，WebSocket 仅推送增量更新。与 008 FR-028 的"页面初始加载仍通过 REST API 获取全量数据"模式一致。
- Q: 005 SC-007 启动包大小 ≤ 150MB、003 SC-002 镜像 ≤ 250MB——006（新增 LocalStorageStrategy）、007（代码目录重组）、008（WebSocket + 批量操作 + copyFile）的新增代码和依赖是否会影响这些大小约束？是否需要更新目标？ → A: 在 008 的 plan 阶段实际测量体积增量后再决策是否调整约束。当前暂不修改 005 和 003 的大小目标。
- Q: 004（侧边栏导航、全局交互）、006（存储引擎文案替换）、008（转码表单 UX、WebSocket 迁移、批量操作按钮）同时修改前端时存在合并冲突风险。如何协调多 spec 对同一前端组件（如 TranscodeTaskForm.tsx、SyncTaskListPage.tsx、侧边栏导航）的并发修改？ → A: 串行实现。按 spec 编号顺序：006（文案+策略模式）→ 007（原目录转码）→ 008（源目录转码 UX 优化 + WebSocket），后续 spec rebase 前一个的变更。006 的存储引擎类型字段变更和 007 的 sourceDirectoryTranscode 字段是 008 表单优化的前置条件。
- Q: 001 边界情况中"系统宕机重启后，未完成的转码任务如何恢复？"——006 定义了 8 状态模型和失败重试逻辑，但"启动时将运行中任务标记为中断并从数据库重新注册定时任务"的实现者是谁？001 FR-004 只提到同步任务手动触发时的去重，未明确说明启动时的状态恢复机制。 → A: 由 001 的启动逻辑统一处理。应用启动时在 common 包中扫描所有 RUNNING 状态的任务（同步和转码），标记为 INTERRUPTED，重新注册定时调度。001 的边界情况已描述此行为，common 包中的全局启动逻辑统一扫描所有任务状态最为合理。
- Q: 008 未生成 plan.md 和 tasks.md，且依赖 004/006/007 的前置完成。跨 spec 的实现顺序和阻塞关系需要明确。 → A: **留待 plan 阶段解决**。008 将在 `/speckit-plan` 中定义与 004/006/007 的精确依赖关系和实现阶段划分。

- Q: 自动重试如何区分"瞬时故障"（应重试）和"业务逻辑错误"（不应重试）？ → A: 引入 `RetryableException` 标记接口。可重试的异常（如网络超时、API 临时不可用）实现此接口，自动重试逻辑通过 `instanceof RetryableException` 判断是否触发重试。不可重试的异常（如文件不存在 404、格式不支持、权限不足）不实现此接口，直接标记为最终失败。
- Q: `retryCount`（自动重试已执行次数）应存储在 TranscodeTask 实体上还是 TaskExecution 的 failureDetails JSON 中？ → A: TranscodeTask 实体新增 `retryCount` 整型字段。TranscodeTask 是重试的实际执行单元（每个转码文件独立重试），独立转码任务也有 TranscodeTask 记录，不受影响。TaskExecution 的 failureDetails JSON 中同步记录一份用于展示，但主数据源是 TranscodeTask。
- Q: 001 中"移动"模式（MOVE）的语义——源文件同步完成后是否从源存储删除？删除时机？ → A: 整批完成后统一删除源文件。同步任务中所有文件同步成功后，再统一从源存储删除对应的源文件。若任务部分失败，源文件全部保留（不删除任何已成功同步的源文件），确保源端数据完整可重试。
- Q: 本地路径（LOCAL）类型存储引擎是否需要路径安全约束？树状目录浏览是否允许跳出 localPath？ → A: 白名单+黑名单组合。仅允许用户主目录和指定数据目录作为 localPath，黑名单禁止系统目录（`/etc`、`C:\Windows`、`/` 等）。树状目录浏览限制在 localPath 内，不可向上越界访问外部目录。
- Q: WebSocket DASHBOARD_UPDATE 消息的触发时机——每次任务状态变更都推送会产生大量冗余推送和数据库查询，如何控制频率？ → A: 防抖机制。任务状态变更后延迟 2 秒推送，2 秒内的多次变更合并为一次 Dashboard 更新推送。保证数据新鲜度同时避免冗余推送和过度数据库查询。
- Q: 录播姬 Webhook 中 `SessionEnded` 事件的触发时机——收到后是立即处理还是等待所有分段文件落盘？ → A: 收到 `SessionEnded` 后等待 5 秒再处理，给文件系统缓冲时间确保所有分段文件已落盘，然后扫描录播存储引擎中该会话目录下的所有文件进行同步/转码。
- Q: 同步后置转码（SyncTask.transcodeEnabled=true）的转码目标存储引擎、目标路径、目标格式、码率如何确定？ → A: 目标存储引擎和目标路径沿用同步任务的配置，目标格式和码率允许在 SyncTask 中独立配置（新增 `transcodeTargetFormat` 和 `transcodeBitrate` 字段）。未配置时使用系统默认（MP3/128kbps）。
- Q: Webhook 处理规则中"仅同步"动作（SYNC_ONLY）的源端逻辑？SyncService 是否需要区分 Webhook 触发同步和常规同步？ → A: SYNC_ONLY = 从录播存储引擎复制到目标 AList 存储。Webhook 触发的同步跳过"目标已存在则跳过"检查，强制覆盖确保最新版本。SyncService 通过参数标记区分触发来源，Webhook 触发时启用强制覆盖模式。
- Q: 磁盘空间检查在三步流程（下载→转码→上传）下应在何时执行？预估大小如何计算？ → A: 下载前统一检查，需 `源文件大小 + 1.5×预估输出大小` 的可用空间，一次性覆盖下载和转码两个阶段的磁盘需求。
- Q: 上传失败后重试时，临时文件名的状态不明确——转码完成后文件从 `.lldwb` 重命名为 `.mp3`，上传失败重试时应读取哪个文件名？ → A: TempFileManager 统一追踪文件当前名称，上传失败重试时直接读取当前文件名（已重命名为 `.mp3`）。TempFileManager 维护名称映射，不依赖约定后缀推断。
- Q: 同步临时文件（`{temp-dir}/sync/`）是否使用可配置后缀？定时清理和磁盘检查是否覆盖 sync 子目录？ → A: 同步临时文件不使用后缀，由 SyncService 自行管理生命周期——同步完成或失败后立即清理，不纳入定时清理和磁盘空间检查。
- Q: 004 的前端构建阶段是否纳入 003 的 Dockerfile？纳入可能突破 SC-002（≤250MB）约束，不纳入则前端静态资源缺失。 → A: 纳入 Dockerfile，增加 Node.js 多阶段构建，单镜像完整部署。SC-002 的 250MB 体积约束取消，不再强制限制。
- Q: 转码临时文件目录是否需要在 docker-compose.yml 中挂载独立卷？容器重启后临时文件丢失会影响失败重试。 → A: 挂载独立卷（如 `alist-media-sync-temp:/app/temp`），容器重启后临时文件持久化保障重试可靠性。转码任务中断重试时从断点阶段继续：下载阶段中断则重新下载，转码阶段中断则从已下载的源文件重新转码，上传阶段中断则从已转码的文件重新上传。临时文件持久化使断点续传成为可能。
- Q: 006/008 新增的环境变量是否纳入 docker-compose.yml 和 .env 模板？ → A: 全部纳入 docker-compose.yml 和 .env 模板，包括 `APP_TRANSCODE_DEFAULT_BITRATE`、`APP_SERVER_ADDRESS`、`APP_WEBSOCKET_MAX_CONNECTIONS`、`APP_RETRY_MAX_AUTO_RETRIES`，保持开箱即用完整性。
- Q: `ALIST_BASE_URL` 和 `ALIST_TOKEN` 标记为必填但默认值为空，容器不提供时仍会启动成功。是否应校验必填并拒绝启动？ → A: 移除 `ALIST_BASE_URL` 和 `ALIST_TOKEN` 环境变量。AList 连接信息通过存储引擎管理界面配置（006 策略模式），不再通过配置文件/环境变量单独配置。同步更新 003 的 docker-compose.yml、.env 模板及 005 的启动包配置，移除相关条目。
- Q: 004 spec 是否需要更新以支持 006 新增的存储引擎类型、树状目录浏览、Cron 图形化配置？ → A: 004 不更新。这些新字段由 006/008 的前端任务自行实现，004 仅负责基础页面框架。
- Q: 008 新增的 `spring-boot-starter-websocket` 和 `app.retry.max-auto-retries` 配置是否纳入启动包的 `application.yaml` 模板和启动脚本环境变量表？ → A: 全部纳入，保持与 docker-compose.yml 一致的开箱即用策略。
- Q: 005 与 006/007/008 的实现顺序？若 005 在 007 之前，Java 类路径需后续移动。 → A: 005 在 007 之前实现，优先提供独立部署能力。Java 类路径迁移由 007 统一处理，005 的 plan/tasks 使用当前包路径。
- Q: SC-007（压缩 ≤150MB、解压 ≤400MB）在 008 新增 WebSocket 依赖后是否仍可达成？ → A: 取消具体数值约束，改为"启动包大小应尽可能精简，不做硬性限制"。
- Q: StorageEngineStrategy 接口是否在 006 预留 `copyFile()` 默认方法，还是留给 008 扩展？ → A: 006 不预留，008 在接口中新增 `copyFile()` 方法。AListStorageStrategy 和 LocalStorageStrategy 各自独立实现接口的 `copyFile` 方法。
- Q: 转码临时文件目录路径和命名规则？`tempSourcePath` 和 `tempFilePath` 是否在同一子目录？并发模型？ → A: 同一子目录 `{temp-dir}/transcode/{taskId}/{文件路径}/{文件}`，源文件和输出文件按原始路径结构共存。每个任务独占一个并发槽位（下载→转码→上传串行），多任务并行。
- Q: StorageEngine 的 EngineStatus 是仅手动"测试连接"时更新，还是需要定时健康检查？ → A: 手动+定时健康检查。定时检查间隔可配置（`app.storage.health-check-interval`，默认 5 分钟），自动感知引擎状态变化并更新 ONLINE/OFFLINE/ERROR。
- Q: WebhookRule 新增 `recordingEngine` 关联和 `targetPath` → `targetFilePath` 重命名的数据迁移策略？ → A: 手动迁移。启动时检测到旧 schema 拒绝启动，输出提示要求用户手动执行 SQL 迁移脚本。不提供自动迁移，避免数据误操作。
- Q: 自动重试依赖精确失败状态（DOWNLOAD_FAILED/TRANSCODE_FAILED/UPLOAD_FAILED），006 是否已按步骤设置？008 如何确定重试起点？ → A: 008 实现时检查 006 实际代码——若 006 已实现按步骤的精确状态设置则直接使用，若未实现则 008 在 TranscodeFileProcessor 三步流程中补充按步骤的状态设置逻辑。
- Q: 同引擎复制时 AList `/api/fs/copy` 是否应批量调用？部分文件 SKIP 部分覆盖如何处理？ → A: 按源目录分组批量调用 `/api/fs/copy`，减少 API 调用次数。SKIP 的文件先过滤掉再批量调用，仅对需要复制/覆盖的文件发起批量请求。

### 跨 spec 澄清分析（2026-06-21）

对 `specs/` 下所有 8 个 spec.md 进行 clarify 分析。规则：**后面的 spec 覆盖前面的，冲突无需单独说明；仅列出后续 spec 也未澄清的真正决策点。**

---

#### 001-alist-media-sync

- **Q-001-1**: ~~同步任务中"移动"模式（MOVE）的具体语义未定义。FR-003 列出三种同步模式（仅新增/全同步/移动），但验收场景仅描述了"仅新增"和"全同步"。"移动"模式下源文件同步完成后是否从源存储删除？删除时机是单个文件同步成功后立即删除，还是整批完成后统一删除？移动一半失败时源文件状态如何恢复？~~ → **已澄清**：整批完成后统一删除源文件，部分失败时源文件全部保留。
- **Q-001-2**: ~~录播姬 Webhook 处理中 `FileClosed` 和 `SessionEnded` 事件的语义差异未明确。一个录制会话会产生多个 `FileClosed` 事件（分段录制），`SessionEnded` 表示整个会话结束。当规则配置为 `SessionEnded` 触发时，是否需要等待所有分段文件就绪后批量处理？~~ → **已澄清**：收到 `SessionEnded` 后等待 5 秒再处理，确保所有分段文件落盘后扫描会话目录。
- **Q-001-3**: ~~同步后置转码（SyncTask.transcodeEnabled=true）的转码目标存储引擎、目标路径、目标格式、码率如何确定？SyncTask 实体仅有 `transcodeEnabled` 布尔字段，缺少转码参数配置。~~ → **已澄清**：目标引擎+路径沿用同步任务，格式和码率独立配置（`transcodeTargetFormat`、`transcodeBitrate` 字段），未配置时默认 MP3/128kbps。
- **Q-001-4**: ~~Webhook 处理规则中"仅同步"动作（SYNC_ONLY）的源端逻辑未定义。006 补充了"录播存储引擎"下拉选择器，但 SYNC_ONLY 动作是否意味着从录播存储引擎（本地路径类型）复制到目标 AList 存储？SyncService 是否需要区分 Webhook 触发同步和常规同步？~~ → **已澄清**：从录播存储引擎复制到目标 AList 存储，Webhook 触发时强制覆盖。

---

#### 002-transcode-temp-suffix-config

- **Q-002-1**: ~~FR-011（磁盘空间检查"1.5 倍预估输出"）在 006 三步流程（下载→转码→上传）下不准确——下载阶段需要源文件大小的空间，而非仅转码输出。磁盘检查应在下载前、转码前还是两者之前执行？预估大小如何计算？~~ → **已澄清**：下载前统一检查，需 `源文件大小 + 1.5×预估输出大小` 的可用空间。
- **Q-002-2**: ~~上传失败后重试时，临时文件名的状态不明确。转码完成后文件从 `.lldwb` 后缀重命名为 `.mp3` 目标扩展名，上传失败重试时上传操作应读取哪个文件名？TempFileManager 的文件名追踪逻辑需明确。~~ → **已澄清**：TempFileManager 统一追踪文件当前名称，重试时直接读取当前名（已重命名为 `.mp3`），维护名称映射。
- **Q-002-3**: ~~008 新增 `{temp-dir}/sync/` 子目录用于同步中间文件。同步临时文件是否也使用可配置后缀（如 `.lldwb`）？如果不使用，定时清理如何识别同步临时文件？磁盘空间检查是否覆盖 sync 子目录？~~ → **已澄清**：同步临时文件不使用后缀，由 SyncService 自行管理生命周期（完成/失败后立即清理），不纳入定时清理和磁盘检查。

---

#### 003-docker-deploy

- **Q-003-1**: ~~004 的前端构建阶段是否纳入 003 的 Dockerfile？当前 Dockerfile 仅含 Java 构建，不含 Node.js 前端构建。如果纳入，需增加构建阶段且可能突破 SC-002（≤250MB）镜像大小约束。如果不纳入，前端静态资源在 Docker 镜像中将缺失。~~ → **已澄清**：纳入 Dockerfile，增加 Node.js 多阶段构建，单镜像完整部署。SC-002 的 250MB 体积约束取消。
- **Q-003-2**: ~~转码临时文件目录是否需要在 docker-compose.yml 中挂载独立卷？当前仅挂载 `alist-media-sync-data:/app/data`。容器重启后临时文件丢失会影响 006/008 的失败重试逻辑（失败状态下临时文件需保留供重试）。~~ → **已澄清**：挂载独立卷持久化。转码中断重试从断点阶段继续（下载中断→重新下载，转码中断→从已下载源文件重试转码，上传中断→从已转码文件重试上传）。
- **Q-003-3**: ~~006/008 新增的环境变量（`APP_TRANSCODE_DEFAULT_BITRATE`、`APP_SERVER_ADDRESS`、`APP_WEBSOCKET_MAX_CONNECTIONS`、`APP_RETRY_MAX_AUTO_RETRIES`）是否纳入 docker-compose.yml 和 .env 模板？当前仅包含 6 个基础环境变量。~~ → **已澄清**：全部纳入 docker-compose.yml 和 .env 模板，保持开箱即用完整性。
- **Q-003-4**: ~~`ALIST_BASE_URL` 和 `ALIST_TOKEN` 标记为必填但默认值为空字符串，容器不提供时仍会启动成功。是否应在启动时校验必填并拒绝启动？~~ → **已澄清**：移除这两个环境变量，AList 连接通过存储引擎管理界面配置，不再通过配置文件/环境变量。同步更新 003 和 005 相关配置。

---

#### 004-web-management-frontend

- **Q-004-1**: ~~006 新增了存储引擎类型字段（AList/LOCAL）、树状目录浏览组件、Cron 图形化配置。004 的存储引擎表单（FR-011）和同步任务表单（FR-015）是否需要更新以支持这些新字段？当前 004 spec 未包含类型选择、路径浏览按钮、图形化 Cron 配置。~~ → **已澄清**：004 不更新，新字段由 006/008 前端任务自行实现。

---

#### 005-standalone-bootstrap

- **Q-005-1**: ~~008 新增的 `spring-boot-starter-websocket` 和 `app.retry.max-auto-retries` 配置是否纳入启动包的 `application.yaml` 模板和启动脚本环境变量表？~~ → **已澄清**：全部纳入，保持开箱即用策略一致。
- **Q-005-2**: ~~005 与 006/007/008 的实现顺序未明确。008 澄清了 006→007→008 串行，但 005 的位置未定。若 005 在 007 之前实现，Java 类路径需后续移动；若在 007 之后，plan/tasks 需使用新包路径。~~ → **已澄清**：005 在 007 之前实现，路径迁移由 007 统一处理。
- **Q-005-3**: ~~SC-007（压缩 ≤150MB、解压 ≤400MB）在 008 新增 WebSocket 依赖后是否仍可达成？005 是否需预留体积余量给后续 spec？008 澄清中推迟到 plan 阶段测量，但 005 实现可能先于 008 plan 完成。~~ → **已澄清**：取消具体数值约束，改为"启动包大小应尽可能精简，不做硬性限制"。

---

#### 006-storage-engine-refactor

- **Q-006-1**: ~~StorageEngineStrategy 接口是否在 006 中预留 `copyFile()` 默认方法（默认抛 UnsupportedOperationException），还是完全留给 008 扩展？当前 006 所有制品均未提及 copyFile，但 008 已明确需要此方法。若 006 不预留，008 需修改已上线接口。~~ → **已澄清**：006 不预留，008 在接口中新增 `copyFile()` 方法，AList/LocalStorageStrategy 各自独立实现。
- **Q-006-2**: ~~转码临时文件目录的具体路径、文件命名规则未定义。`tempSourcePath` 和 `tempFilePath` 是否在同一子目录？三步流程的并发资源占用模型（下载/转码/上传是否共享一个并发槽位）未明确，影响磁盘空间预估。~~ → **已澄清**：`{temp-dir}/transcode/{taskId}/{文件路径}/{文件}`，每任务独占槽位串行，多任务并行。
- **Q-006-3**: ~~StorageEngine 的 EngineStatus（ONLINE/OFFLINE/ERROR）是仅在手动"测试连接"时更新，还是需要后台定时健康检查自动维护？~~ → **已澄清**：手动+定时健康检查，间隔可配置（默认 5 分钟），自动感知引擎状态变化。
- **Q-006-4**: ~~本地路径（LOCAL）类型存储引擎是否需要路径安全约束（禁止配置 `/`、`/etc`、`C:\Windows` 等系统目录）？树状目录浏览是否允许用户跳出 localPath 访问外部目录？~~ → **已澄清**：白名单+黑名单组合，仅允许用户主目录和指定数据目录，黑名单禁止系统目录，树状浏览限制在 localPath 内不可越界。
- **Q-006-5**: ~~WebhookRule 新增 `recordingEngine` 关联的数据迁移策略未定义。现有 Webhook 规则升级后 recordingEngine 如何赋值？`targetPath` → `targetFilePath` 字段重命名是否需要数据库迁移脚本？~~ → **已澄清**：手动迁移，启动时检测旧 schema 拒绝启动并提示用户手动执行 SQL。

---

#### 008-transcode-sync-optimization（本规格）

- **Q-008-1**: ~~自动重试和批量重试依赖精确的失败状态（DOWNLOAD_FAILED/TRANSCODE_FAILED/UPLOAD_FAILED），但当前 TranscodeFileProcessor 的 catch 块仅保存 errorMessage，不区分失败步骤。006 完成后异常处理是否已按步骤设置对应 FAILED 状态？如果未实现，008 的重试机制如何确定重试起点？~~ → **已澄清**：008 实现时检查 006 实际代码，按需适配补充精确状态设置。
- **Q-008-2**: ~~retryCount 应存储在 TranscodeTask 实体上还是 TaskExecution 的 failureDetails JSON 中？~~ → **已澄清**：TranscodeTask 实体新增 `retryCount` 整型字段，TaskExecution.failureDetails 同步记录，详见 FR-026。
- **Q-008-3**: ~~WebSocket DASHBOARD_UPDATE 消息的触发时机未明确。Dashboard 统计数据是聚合计算值，每次任务状态变更都重算推送会产生大量冗余推送和数据库查询。需要防抖/节流机制或仅任务完成时推送。~~ → **已澄清**：防抖机制，任务状态变更后延迟 2 秒推送，2 秒内多次变更合并为一次。
- **Q-008-4**: ~~AList `/api/fs/copy` 接口支持批量复制（`names` 数组），当前 SyncService 是逐文件处理。同引擎复制时是否应按源目录分组批量调用 `/api/fs/copy`（减少 API 调用），还是保持逐文件调用？批量调用时部分文件 SKIP 部分覆盖如何处理？~~ → **已澄清**：按源目录分组批量调用，SKIP 文件先过滤再批量请求。
- **Q-008-5**: ~~自动重试如何区分"瞬时故障"和"业务逻辑错误"？~~ → **已澄清**：引入 `RetryableException` 标记接口，详见 FR-024 和 A10。

---

### 跨 spec 澄清问题汇总

| 编号 | 源 spec | 问题摘要 | 影响的关键决策 |
|------|---------|---------|---------------|
| Q-001-1 | 001 | MOVE 同步模式语义未定义 | **已澄清**—整批完成后统一删除源文件 |
| Q-001-2 | 001 | FileClosed vs SessionEnded 处理差异 | **已澄清**—SessionEnded 后等待 5 秒再处理 |
| Q-001-3 | 001 | 同步后置转码的目标引擎/路径/参数 | **已澄清**—目标引擎+路径沿用同步任务，格式/码率独立配置 |
| Q-001-4 | 001 | Webhook SYNC_ONLY 的源端逻辑 | **已澄清**—从录播存储引擎复制到目标，Webhook 触发时强制覆盖 |
| Q-002-1 | 002 | 磁盘检查在三步流程下的时机和阈值 | **已澄清**—下载前统一检查，需源文件+1.5×预估输出 |
| Q-002-2 | 002 | 上传失败重试时的文件名状态 | **已澄清**—TempFileManager 统一追踪当前名称 |
| Q-002-3 | 002 | 同步临时文件是否使用可配置后缀 | **已澄清**—SyncService 自管生命周期，不纳入定时清理 |
| Q-003-1 | 003 | 前端构建是否纳入 Dockerfile | **已澄清**—纳入，Node.js 多阶段构建，250MB 约束取消 |
| Q-003-2 | 003 | 临时文件目录是否挂载独立卷 | **已澄清**—挂载独立卷，转码中断从断点阶段继续 |
| Q-003-3 | 003 | 新增环境变量是否纳入 compose | **已澄清**—全部纳入 |
| Q-003-4 | 003 | 必填环境变量的启动校验 | **已澄清**—移除 ALIST_BASE_URL/ALIST_TOKEN，改用存储引擎管理界面 |
| Q-004-1 | 004 | 存储引擎类型/树状浏览/Cron 图形化 | **已澄清**—004 不更新，由 006/008 自行实现 |
| Q-005-1 | 005 | 新配置项是否纳入启动包 | **已澄清**—全部纳入 |
| Q-005-2 | 005 | 与 006/007/008 的实现顺序 | **已澄清**—005 在 007 之前，路径迁移由 007 统一处理 |
| Q-005-3 | 005 | 启动包大小约束在 008 后是否可达成 | **已澄清**—取消数值约束，改为"尽可能精简" |
| Q-006-1 | 006 | copyFile 是否在 006 预留 | **已澄清**—006 不预留，008 新增接口方法 |
| Q-006-2 | 006 | 临时文件目录路径和并发模型 | **已澄清**—{taskId}/{文件路径}/{文件}，每任务独占槽位 |
| Q-006-3 | 006 | EngineStatus 维护机制 | **已澄清**—手动+定时（默认5分钟） |
| Q-006-4 | 006 | LOCAL 引擎路径安全约束 | **已澄清**—白名单+黑名单组合，树状浏览限制在 localPath 内 |
| Q-006-5 | 006 | WebhookRule 数据迁移策略 | **已澄清**—手动迁移，拒启+提示SQL |
| Q-008-1 | 008 | 失败状态精确设置 | **已澄清**—008 按需适配，检查 006 代码后补充 |
| Q-008-2 | 008 | retryCount 存储位置 | **已澄清**—TranscodeTask 实体新增字段 |
| Q-008-3 | 008 | Dashboard WebSocket 推送频率 | **已澄清**—防抖机制，延迟 2 秒合并推送 |
| Q-008-4 | 008 | 同引擎复制批量 vs 逐文件 | **已澄清**—按源目录分组批量调用，SKIP 先过滤 |
| Q-008-5 | 008 | 瞬时故障 vs 业务错误区分 | **已澄清**—RetryableException 标记接口 |

## 用户场景与测试 *（强制）*

### 用户故事 1 — 源目录转码选项简化（优先级：P1）

作为系统管理员，我在创建转码任务时选择"源目录转码"后，系统应自动隐藏"目标文件路径"和"目标存储引擎"选项，因为这两个字段在源目录转码场景下没有意义——输出文件将直接放置在源文件所在目录，且使用的存储引擎与源引擎相同。同时，该选项的名称应从"原目录转码"更正为"源目录转码"以更准确地描述其行为。

**为什么是此优先级**：这是对现有功能的 UX 优化和文案修正。当前实现中，即使勾选了源目录转码，用户仍然可以看到"目标文件路径"和"目标存储引擎"的选择框（只是目标文件路径被禁用），这造成了界面冗余和用户困惑。隐藏不需要的字段简化了表单，减少了认知负担。

**独立测试**：打开转码任务创建页面，勾选"源目录转码"复选框，验证"目标文件路径"和"目标存储引擎"字段被隐藏。取消勾选后验证这两个字段重新显示。

**验收场景**：

1. **假设** 用户打开转码任务创建页面，**当** 勾选"源目录转码（输出至源文件所在目录）"复选框，**则** "目标存储引擎"下拉选择框和"目标文件路径"输入框立即隐藏（而非仅禁用），表单仅保留"源存储引擎"、"源文件路径"、"目标格式"、"码率"四个输入项。复选框标签显示为"源目录转码（输出至源文件所在目录）"。
2. **假设** 用户已勾选"源目录转码"，**当** 取消勾选，**则** "目标存储引擎"下拉选择框和"目标文件路径"输入框重新显示，并恢复为必填项。
3. **假设** 用户打开转码任务创建页面，**当** 查看复选框标签，**则** 文字显示为"源目录转码（输出至源文件所在目录）"而非"原目录转码（输出至源文件所在目录）"。
4. **假设** 用户勾选了"源目录转码"，**当** 提交表单，**则** 后端不再要求 `targetEngineId` 必填（因为目标引擎自动使用源引擎），`targetFilePath` 自动使用源文件所在目录。
5. **假设** 用户勾选了"源目录转码"并提交，**当** 转码完成后，**则** 输出文件位于源文件所在目录下，文件名为 `原文件名.目标格式`。

---

### 用户故事 2 — 源目录转码路径计算修正（优先级：P1）

作为系统管理员，当我对一个目录执行源目录转码时，转码后的文件应该保留在原始的子目录结构中。例如源路径 `/videos/sub/recording.flv` 转码为 MP3 后，输出文件路径应为 `/videos/sub/recording.mp3`（即 `原文件路径/原文件名.指定格式`），而非简单地将所有输出文件放入同一个目标目录。

**为什么是此优先级**：这是源目录转码的核心语义修正。当前实现中，源目录转码时目标路径的计算逻辑存在歧义——`targetFilePath` 被设为源文件所在目录，但后续文件名拼接逻辑可能导致输出路径不正确。需要确保输出路径严格遵循 `源文件所在目录/原文件名（不含扩展名）.目标扩展名` 的规则。

**独立测试**：创建一个源目录转码任务，源路径为 `/videos/sub/recording.flv`，目标格式 MP3，验证输出文件路径为 `/videos/sub/recording.mp3`。

**验收场景**：

1. **假设** 用户创建源目录转码任务，源文件路径为 `/videos/sub/recording.flv`，目标格式为 MP3，**当** 转码执行，**则** 输出文件完整路径为 `/videos/sub/recording.mp3`（即源文件所在目录 / 源文件名（不含扩展名）.mp3）。
2. **假设** 用户创建源目录转码任务，源文件路径为 `/media/music/song.m4v`，目标格式为 MP3，**当** 转码执行，**则** 输出文件完整路径为 `/media/music/song.mp3`。
3. **假设** 用户创建源目录转码任务，源路径为目录 `/videos/`（目录模式），**当** 转码执行，**则** 目录下每个视频文件都在其原始子路径下生成对应格式的转码文件。例如 `/videos/sub/a.flv` → `/videos/sub/a.mp3`。

---

### 用户故事 3 — 转码任务列表仅显示文件（优先级：P2）

作为系统管理员，我在查看转码任务列表时，希望只看到文件级别的任务条目，而不是混杂目录和文件。每个文件应显示其完整路径（格式为"路径/文件"），而不单独列出目录条目。

**为什么是此优先级**：转码的最小执行单元是文件，显示目录条目对用户没有实际意义。当前实现中，转码任务可能同时显示目录和文件，造成列表混乱。此改进让列表更清晰，每行代表一个可操作的转码文件。

**独立测试**：创建多个转码任务（包含目录模式和文件模式），验证转码任务列表中每行都显示为"路径/文件名"的格式，无纯目录行。

**验收场景**：

1. **假设** 存在多个转码任务，**当** 用户打开转码任务列表页面，**则** 列表中每行都显示文件路径（格式为 `路径/文件名`），不显示纯目录条目。
2. **假设** 用户创建了一个目录转码任务（源路径为 `/videos/`），**当** 转码任务执行后查看列表，**则** 目录下每个文件作为独立的转码任务行显示，每行显示完整的 `路径/文件名`（如 `/videos/sub/recording.flv`）。
3. **假设** 转码任务列表中，"源路径"列和"目标路径"列，**当** 显示内容，**则** 格式均为 `完整路径/文件名.扩展名`。

---

### 用户故事 4 — 转码任务批量操作（优先级：P2）

作为系统管理员，我希望在转码任务列表页面能够一键清理所有失败任务、一键清理所有成功任务，以及一键重试所有失败文件，而不需要逐个手动操作。

**为什么是此优先级**：当批量转码大量文件时，可能出现多个失败任务。逐个手动重试或清理效率极低。批量操作大幅提升了运维效率。

**独立测试**：创建多个转码任务（包含成功和失败状态），点击"清理失败任务"按钮验证所有失败任务被移除，点击"清理成功任务"按钮验证所有成功任务被移除，点击"重试所有失败文件"按钮验证所有可重试的失败任务进入重试流程。

**验收场景**：

1. **假设** 转码任务列表中存在 5 个失败状态的任务，**当** 用户点击"清理失败任务"按钮，**则** 弹出确认对话框："确定要清理所有失败任务吗？此操作不可撤销。"，确认后所有失败状态的任务被删除，列表刷新。
2. **假设** 转码任务列表中存在 10 个已完成（COMPLETED）状态的任务，**当** 用户点击"清理成功任务"按钮，**则** 弹出确认对话框，确认后所有已完成状态的任务被删除，列表刷新。
3. **假设** 转码任务列表中存在 3 个可重试的失败任务（DOWNLOAD_FAILED / TRANSCODE_FAILED / UPLOAD_FAILED），**当** 用户点击"重试所有失败文件"按钮，**则** 弹出确认对话框："确定要重试所有失败文件吗？"，确认后按钮进入加载态显示"重试中..."，API 返回 202 后提示"已提交 3 个任务进行重试，结果将通过实时更新推送"，列表通过 WebSocket 自动刷新显示最新状态。
4. **假设** 转码任务列表中没有任何失败任务，**当** 用户点击"清理失败任务"或"重试所有失败文件"，**则** 显示提示"没有可操作的失败任务"。
5. **假设** 转码任务列表中没有任何成功任务，**当** 用户点击"清理成功任务"，**则** 显示提示"没有可清理的成功任务"。

---

### 用户故事 5 — 同步模块同引擎复制优化（优先级：P1）

作为系统管理员，当同步任务的源存储引擎和目标存储引擎是同一个引擎时，我希望系统直接调用存储引擎的复制（copy）方法，而不是执行"下载到临时文件再上传"的低效流程。

**为什么是此优先级**：同引擎场景下（如 AList 内部两个目录之间同步），下载→上传流程需要经过网络传输临时文件，效率极低。直接调用引擎的复制接口（如 AList 的 `/api/fs/copy`）可以显著提升性能，减少带宽消耗和处理时间。

**独立测试**：创建一个源和目标为同一 AList 引擎的同步任务，触发同步，验证文件通过 AList 的 `/api/fs/copy` API 完成复制而非下载再上传。创建一个源和目标为不同引擎的同步任务，验证仍使用现有的下载→上传流程。

**验收场景**：

1. **假设** 同步任务的源引擎和目标引擎为同一个 AList 引擎（engine.id 相同），**当** 执行同步，**则** 系统调用 AList 的 `/api/fs/copy` 接口直接复制文件，不经过本地下载和上传步骤。
2. **假设** 同步任务的源引擎和目标引擎为同一个 LOCAL 引擎（engine.id 相同），**当** 执行同步，**则** 系统使用 `java.nio.file.Files.copy` 直接复制文件，不经过流式读写。
3. **假设** 同步任务的源引擎和目标引擎为不同引擎（engine.id 不同），**当** 执行同步，**则** 系统仍然使用现有的下载→上传流程（小文件流式、大文件磁盘暂存），行为不变。
4. **假设** 同引擎复制时目标文件已存在且冲突策略为 SKIP，**当** 执行同步，**则** 系统跳过该文件（与现有冲突策略行为一致）。

---

> **注**：用户故事编号 6 已被有意跳过——原计划中独立的前端 WebSocket 迁移故事在规划阶段合并入用户故事 8（WebSocket 实时推送替代轮询），因为前端迁移与后端 WebSocket 基础设施不可分割。为保持编号稳定性，不重新编号后续故事。

### 用户故事 7 — 同步/转码失败自动重试（优先级：P2）

作为系统管理员，当同步任务或转码任务因网络波动、API 临时不可用等瞬时故障导致失败时，我希望系统能够自动重试失败的操作，无需我手动介入。重试次数应在前端任务列表中可见，最大自动重试次数可通过配置文件调整。

**为什么是此优先级**：自动重试能显著减少因瞬时故障导致的手动运维工作。001 边界情况中已定义此需求（"应支持重试策略（指数退避），超时后标记任务失败"），但此前无 spec 承接实现。与 008 中已有的手动批量重试（FR-014）互补——手动重试覆盖已确认的失败，自动重试覆盖瞬时故障。

**独立测试**：配置最大自动重试次数为 3，模拟网络中断导致同步失败，验证系统自动重试并在重试次数用尽后标记为最终失败。

**验收场景**：

1. **假设** 配置文件中 `app.retry.max-auto-retries` 设置为 3，**当** 同步任务中某个文件因网络超时传输失败，**则** 系统自动对该文件执行重试（指数退避：初始间隔 1 秒，每次翻倍，最大间隔 60 秒），最多重试 3 次。全部失败后标记该文件为最终失败。
2. **假设** 某文件在第 2 次自动重试时成功，**当** 重试完成，**则** 系统继续处理下一个文件，该文件的重试次数（2）记录在任务执行详情中。
3. **假设** 转码任务的上传步骤因网络问题失败，**当** 自动重试机制触发，**则** 任务状态保持为 UPLOAD_FAILED 但系统在后台自动重新执行上传（无需回到 UPLOADING 状态），重试成功后状态变为 COMPLETED。
4. **假设** 用户查看任务执行详情，**当** 查看失败文件列表，**则** 每个失败文件显示已重试次数和最大重试次数（如"重试 2/3"）。
5. **假设** 用户手动触发了重试（FR-014 的"重试所有失败文件"），**当** 手动重试执行，**则** 手动重试不计入自动重试次数限制，手动重试始终执行。

---

### 用户故事 8 — WebSocket 实时推送替代轮询（优先级：P1）

作为系统架构师，我希望前端通过 WebSocket 接收实时数据更新，而不是通过每 5 秒轮询一次 REST API。这可以减少不必要的 HTTP 请求，降低服务端负载，并提供更实时的用户体验。

**为什么是此优先级**：当前前端对所有列表页面（转码任务列表、同步任务列表）使用 5 秒轮询获取数据更新。当有大量活跃任务时，频繁的 HTTP 请求增加了服务端压力和网络流量。WebSocket 长连接只需在数据变更时推送更新，大幅降低请求频率。这是影响系统整体性能和用户体验的关键改进。

**独立测试**：打开同步任务列表页面，通过浏览器开发者工具验证建立了 WebSocket 连接，当有同步任务执行时，验证任务进度通过 WebSocket 实时推送更新，无额外的 HTTP 轮询请求。打开转码任务列表页面，验证同样通过 WebSocket 接收更新。

**验收场景**：

1. **假设** 用户打开任意管理页面，**当** 页面加载完成，**则** 前端自动与后端建立 WebSocket 连接（连接端点 `/ws/events`），连接成功后前端不再对列表数据进行 HTTP 轮询。
2. **假设** 有一个同步任务正在执行，**当** 任务状态或进度发生变化，**则** 后端通过 WebSocket 推送增量更新消息（消息类型：`SYNC_PROGRESS`，payload 仅含变更的任务字段如 `{ taskId, status, successFiles, failedFiles }`），前端接收后合并到本地状态，无需额外的 HTTP 请求。
3. **假设** 有一个转码任务正在执行，**当** 任务状态或进度发生变化，**则** 后端通过 WebSocket 推送增量更新消息（消息类型：`TRANSCODE_PROGRESS`，payload 仅含变更字段如 `{ taskId, status, progressPercent }`），前端接收后合并到本地状态。
4. **假设** 前端 WebSocket 连接意外断开，**当** 断线后，**则** 前端自动尝试重连（采用指数退避策略，最大重连间隔 30 秒），重连成功后恢复实时更新。重连期间显示"连接中断，正在重连..."提示。认证失败时不降级为 HTTP 轮询，直接引导用户重新登录。
5. **假设** 用户关闭或离开管理页面，**当** 页面卸载，**则** 前端主动关闭 WebSocket 连接，释放服务端资源。
6. **假设** 后端需要通知前端全局状态变化（如任务创建、删除、执行完成），**当** 事件发生，**则** 后端通过 WebSocket 广播对应类型的消息。

---

### 边界情况

- 当用户选择"源目录转码"，但未选择源存储引擎时，"目标存储引擎"和"目标文件路径"应仍然隐藏，但提交时应提示"请先选择源存储引擎"。
- 当转码任务列表为空时，点击"清理失败任务"、"清理成功任务"、"重试所有失败文件"应给出友好提示而非静默无视。
- 当源目录转码的源路径为根目录 `/file.flv` 时，目标路径应同样为 `/file.mp3`。
- WebSocket 服务端如何处理多实例部署？当前为单实例部署（H2 数据库），WebSocket 直接在应用进程内处理，暂不考虑多实例扩展需求。
- WebSocket 连接是否需要认证？需要，与 REST API 使用相同的 Basic Auth 认证机制。WebSocket 握手阶段验证凭据。认证失败时拒绝升级请求，前端不退化为 HTTP 轮询，引导用户重新登录。
- WebSocket 并发连接数上限通过 `app.websocket.max-connections` 配置项控制，默认 50。超过上限时拒绝新连接并返回 HTTP 429。
- 同步同引擎复制时，如果目标目录不存在，是否自动创建？AList 的 `/api/fs/copy` 需要目标目录已存在，因此在复制前必须确保目标父目录存在（通过 `createDirectory` 方法）。
- 同引擎复制的性能预期是什么？对于 AList，`/api/fs/copy` 是服务端内部操作，不经过本地网络，预期比下载→上传快至少一个数量级。
- 同步不同引擎时的临时文件存储在独立子目录（如 `{temp-dir}/sync/`），遵循与转码临时文件相同的 24 小时定时清理策略。同步中间文件（完整原文件副本）和转码中间文件（转换后的输出）使用不同目录以区分语义和生命周期。
- 临时文件清理策略统一为：上传成功后立即清理，失败状态下保留供重试，定时任务清理超过 24 小时的孤立任务和临时文件。此策略替代 002 中定义的启动时无条件清理。
- 源目录转码隐藏"目标存储引擎"后，后端如何处理？后端自动将 `targetEngineId` 设置为与 `sourceEngineId` 相同的值，前端提交时不传 `targetEngineId`，后端在接收到请求后自动赋值。
- 自动重试与手动重试的关系？手动重试（FR-014）不计入自动重试次数限制，始终执行。自动重试用尽后标记为最终失败，用户仍可手动重试。
- 自动重试的指数退避策略如何与并发控制协调？自动重试的等待时间不占用线程池工作线程（使用 `ScheduledExecutorService` 调度），不影响其他文件的正常处理。
- 配置文件未设置 `app.retry.max-auto-retries` 时的默认行为？默认值 3，即每个失败文件最多自动重试 3 次。
- 自动重试过程中系统重启如何处理？重启后所有未完成的自动重试任务失效，任务保持原失败状态。用户可通过手动重试恢复。

## 需求 *（强制）*

### 功能需求

#### 转码模块：源目录转码 UX 优化

- **FR-001**（已废弃，合并至 FR-005）：~~前端复选框标签文案从"原目录转码"改为"源目录转码"，后端 DTO 字段名从 `sameDirectoryTranscode` 改为 `sourceDirectoryTranscode`。FR-002 负责隐藏字段行为。~~
- **FR-002**：当用户勾选"源目录转码"复选框时，前端必须隐藏（而非仅禁用）"目标存储引擎"下拉选择框和"目标文件路径"输入框及其标签。当取消勾选时，这两个字段重新显示并恢复为必填状态。
- **FR-003**：前端提交表单时，当 `sourceDirectoryTranscode=true`，必须不传 `targetEngineId` 字段（或传 null），`targetFilePath` 传空字符串。后端自动将 `targetEngineId` 赋值为 `sourceEngineId`。
- **FR-004**：`TranscodeTaskCreateDTO.java` 中 `targetEngineId` 的 `@NotNull` 校验必须改为条件校验——当 `sourceDirectoryTranscode=true` 时可为空，当 `sourceDirectoryTranscode=false` 时仍为必填。可通过自定义校验注解或手动校验实现。
- **FR-005**：用户界面中复选框的文字从"原目录转码"改为"源目录转码"，后端 DTO 字段名从 `sameDirectoryTranscode` 改为 `sourceDirectoryTranscode`。

#### 转码模块：源目录转码路径计算

- **FR-006**：`TranscodeService.createTask()` 方法中，当 `sourceDirectoryTranscode=true` 时，`targetFilePath` 必须设置为源文件的**完整目录路径**（即 `sourceFilePath` 去掉文件名后的目录部分），而非简单设置为 `/`。
- **FR-007**：`TranscodeFileProcessor` 中的 `uploadStep` 方法必须确保源目录转码的输出文件路径遵循规则：`源文件所在目录 / 源文件名（不含原扩展名）.目标格式扩展名`。对于目录扫描模式，每个文件的输出路径独立计算。
- **FR-008**：`getOutputName()` 方法中的输出文件名生成逻辑必须确认已在 007 中正确实现并通过测试。当前 `TranscodeFileProcessor.java:264-268` 和 `TranscodeService.java:560-563` 已有去扩展名+新扩展名的逻辑，需验证与源目录转码场景的兼容性。
- **FR-009**：`TranscodeCandidate` record 中的 `targetPath` 字段在源目录转码场景下必须等于源文件所在目录路径（即 `fullPath` 去掉文件名部分）。

#### 转码模块：列表仅显示文件

- **FR-010**：转码任务列表页面（`TranscodeTaskListPage.tsx`）中，"源路径"列和"目标路径"列的显示格式必须统一为 `完整路径/文件名.扩展名`，不显示纯目录条目。**已确认**：后端 `TranscodeFileProcessor.process()` 为每个文件创建独立的 `TranscodeTask` 记录，`scanSourceDirectory()` 仅收集文件不收集目录——`listAll()` 返回的每条记录始终对应一个具体文件，前端无需额外过滤逻辑。
- **FR-011**：后端 `TranscodeService.listAll()` 返回的结果必须确保每个 `TranscodeTaskVO` 映射到一个具体文件，目录转码任务中的每个候补文件都生成了独立的 `TranscodeTask` 记录（当前 `TranscodeFileProcessor.process()` 已为此行为）。如果存在任何不关联具体文件的 `TranscodeTask`（如目录级别的父任务），需要过滤掉。

#### 转码模块：批量操作

- **FR-012**：后端必须新增 `DELETE /api/transcode-tasks/failed` 端点，删除所有状态为 `DOWNLOAD_FAILED`、`TRANSCODE_FAILED`、`UPLOAD_FAILED` 的转码任务，返回删除数量。
- **FR-013**：后端必须新增 `DELETE /api/transcode-tasks/completed` 端点，删除所有状态为 `COMPLETED` 的转码任务，返回删除数量。
- **FR-014**：后端必须新增 `POST /api/transcode-tasks/retry-all` 端点，对所有状态为 `DOWNLOAD_FAILED`、`TRANSCODE_FAILED`、`UPLOAD_FAILED` 的转码任务执行重试操作。端点立即返回 202 Accepted（不等待重试完成），实际重试在后端异步执行。每个任务的重试结果通过 WebSocket `TRANSCODE_PROGRESS` 消息逐条推送，前端自动更新列表状态。
- **FR-015**：`TranscodeTaskRepository` 必须新增按状态批量删除的查询方法 `deleteByStatusIn(List<TranscodeStatus> statuses)` 和按状态查询的 `findByStatusIn(List<TranscodeStatus> statuses)`。
- **FR-016**：前端 `TranscodeTaskListPage.tsx` 必须在页面顶部操作栏添加三个按钮："清理失败任务"、"清理成功任务"、"重试所有失败文件"。每个按钮点击后弹出确认对话框，确认后按钮进入加载态（显示"清理中..."或"重试中..."）并禁用。对于清理操作，API 返回后展示简短结果提示（如"已清理 5 个失败任务"）并自动刷新列表。对于重试操作，API 返回 202 后提示"已提交 N 个任务进行重试"，后续通过 WebSocket 实时更新列表。
- **FR-017**：前端 `client.ts` 中的 API 客户端必须新增对应的接口方法，端点映射如上。

#### 同步模块：同引擎复制

- **FR-018**：`StorageEngineStrategy` 接口必须新增 `copyFile(StorageEngine engine, String sourcePath, String targetPath)` 默认方法，默认实现抛出 `UnsupportedOperationException`，由各策略实现覆盖。
- **FR-019**：`AListStorageStrategy` 必须实现 `copyFile` 方法，调用 AList 的 `/api/fs/copy` 接口（POST 请求，body 包含 `src_dir`、`dst_dir`、`names` 参数）。
- **FR-020**：`LocalStorageStrategy` 必须实现 `copyFile` 方法，使用 `java.nio.file.Files.copy` 进行本地文件复制。
- **FR-021**：`SyncService.executeSyncTask()` 方法必须在执行同步时检测源引擎和目标引擎是否为同一个引擎（`sourceEngine.getId().equals(targetEngine.getId())`）。如果是，对每个待同步文件统一调用 `targetStrategy.copyFile()` 而非下载→上传流程（不做文件大小分流）。必须在调用前确保目标父目录存在。
- **FR-022**：同引擎复制时必须处理冲突策略。使用与转码共享的 `ConflictStrategy` 枚举（OVERWRITE/SKIP/RENAME）。对于 SKIP 策略，在复制前检查目标是否存在，若存在则跳过。对于 OVERWRITE 策略，直接覆盖。对于 RENAME 策略，生成不重名的目标路径后复制——路径生成规则：在目标文件名（不含扩展名）后追加 `_1`、`_2`... 后缀直至目标路径不存在冲突，扩展名保持不变（如 `recording.mp3` → `recording_1.mp3` → `recording_2.mp3`）。
- **FR-023**：同引擎复制的进度追踪和错误处理必须与现有同步流程一致——成功/失败计数、`TaskExecution` 记录更新、失败详情记录。

#### 同步/转码：自动重试

- **FR-024**：系统必须支持对同步和转码任务中因瞬时故障（网络超时、API 临时不可用等）失败的操作进行自动重试。最大自动重试次数通过配置文件 `app.retry.max-auto-retries` 配置，默认值 3。系统必须引入 `RetryableException` 标记接口——仅当捕获的异常实现了 `RetryableException` 时才触发自动重试；未实现此接口的异常（如文件不存在 404、格式不支持、权限不足等业务逻辑错误）直接标记为最终失败，不进行自动重试。
- **FR-025**：自动重试必须采用指数退避策略：初始重试间隔 1 秒，每次翻倍，最大间隔 60 秒。重试间隔公式：`min(1000 * 2^(attempt-1), 60000)` 毫秒。
- **FR-026**：TranscodeTask 实体必须新增 `retryCount` 整型字段，记录该任务已执行的自动重试次数。自动重试每次执行时递增此字段。TaskExecution 的失败详情 JSON 中同步记录 `retryCount` 和 `maxRetries`（如 `{ fileName, failReason, retryCount: 2, maxRetries: 3 }`），用于前端同步任务执行详情展示。转码任务列表中每个任务的 `retryCount` 直接从 TranscodeTask 实体读取。前端展示格式："重试 2/3"。
- **FR-027**：自动重试用尽后，文件标记为最终失败。用户可通过手动重试（FR-014 的"重试所有失败文件"）再次尝试，手动重试不计入自动重试次数限制，始终执行。
- **FR-028**：转码三步流程（下载→转码→上传）中每个步骤的失败均适用自动重试。重试从失败步骤重新开始（不回到 PENDING），遵循 006 定义的重试逻辑（失败状态→对应进行中状态）。

#### 公共模块：WebSocket 替代轮询

- **FR-029**：后端必须引入 Spring WebSocket 支持（`spring-boot-starter-websocket`），配置 `WebSocketConfig` 注册 `/ws/events` 端点，启用原始 WebSocket（不使用 STOMP 以遵循 YAGNI）。WebSocket 并发连接数通过配置项 `app.websocket.max-connections` 控制（默认 50），超过上限时拒绝新连接。
- **FR-030**：后端必须实现 WebSocket 会话管理和消息广播机制。消息格式为 JSON，包含 `type`（消息类型）、`payload`（增量数据载荷，仅含变更字段）、`timestamp`（时间戳）。采用增量变更模式：每次仅推送变更的实体和变更字段（如 `{ taskId, status, progressPercent }`），前端负责将增量合并到本地状态，不推送全量列表。
- **FR-031**：后端必须在以下事件发生时通过 WebSocket 推送消息：同步任务状态/进度变更（`SYNC_PROGRESS`）、转码任务状态/进度变更（`TRANSCODE_PROGRESS`）、任务创建/删除/完成（`TASK_EVENT`）、Webhook 事件接收/处理状态变更（`WEBHOOK_EVENT`）、仪表板统计数据变更（`DASHBOARD_UPDATE`）。
- **FR-032**：前端必须新增 WebSocket 连接管理 Hook（`useWebSocket.ts`），负责建立连接、消息分发、断线重连（指数退避，初始 1 秒，最大 30 秒）、页面卸载时断开。
- **FR-033**：前端所有列表页面（`TranscodeTaskListPage.tsx`、`SyncTaskListPage.tsx`、`WebhookEventListPage.tsx`、`DashboardPage.tsx`）必须移除 `usePolling` 调用，改为使用 `useWebSocket` 接收实时更新。页面初始加载仍通过 REST API 获取全量数据。
- **FR-034**：WebSocket 连接必须携带认证信息。在连接握手阶段，前端通过 HTTP Upgrade 请求的 `Authorization` 请求头传递 Basic Auth 凭据（与 REST API 认证方式一致）。后端 `WebSocketConfig` 需配置握手拦截器读取 `Authorization` 请求头验证凭据，认证失败时拒绝 WebSocket 升级请求。前端收到认证失败后引导用户重新登录（与 REST API 401 行为一致），不降级为 HTTP 轮询。
- **FR-035**：原有的 `usePolling.ts` Hook 文件必须删除。所有列表页面已迁移至 WebSocket，不再需要轮询机制。

#### 同步模块：后置转码配置扩展

- **FR-036**：`SyncTask` 实体必须新增 `transcodeTargetFormat`（String，默认 "MP3"）和 `transcodeBitrate`（int，默认 128000）字段，用于同步后置转码的目标格式和码率独立配置。未配置时使用系统默认值。
- **FR-037**：`SyncTaskCreateDTO` 必须新增 `transcodeTargetFormat` 和 `transcodeBitrate` 字段，格式枚举值 MP3/MP4/FLV，码率范围 32000-320000。

#### 公共模块：清理策略与健康检查

- **FR-038**：临时文件清理策略从"启动时无条件清理"改为"定时任务清理超过 24 小时的孤立任务和临时文件"（替代 002 FR-013），同步临时文件由 SyncService 自管生命周期（完成/失败后立即清理）。
- **FR-039**：存储引擎定时健康检查（`@Scheduled`，间隔由 `app.storage.health-check-interval` 配置，默认 5 分钟），自动更新 EngineStatus（ONLINE/OFFLINE/ERROR）。

### 关键实体

- **消息类型枚举（MessageType）**：WebSocket 推送消息的类型标识，包含 `SYNC_PROGRESS`、`TRANSCODE_PROGRESS`、`TASK_EVENT`、`WEBHOOK_EVENT`、`DASHBOARD_UPDATE`。
- **WebSocket 消息（WsMessage）**：通用 WebSocket 消息结构，`{ type: string, payload: object, timestamp: string }`。
- **存储引擎策略接口（StorageEngineStrategy）**：新增 `copyFile(StorageEngine engine, String sourcePath, String targetPath)` 默认方法（与接口其他方法一致，接受 engine 参数以获取连接信息）。现有方法不变。
- **重试配置（RetryConfig）**：应用程序配置实体，包含最大自动重试次数（`max-auto-retries`，默认 3）、初始重试间隔（1 秒）、最大重试间隔（60 秒）。通过 `application.yaml` 中 `app.retry` 前缀配置。
- **可重试异常标记（RetryableException）**：标记接口，实现此接口的异常被视为瞬时故障，触发自动重试。未实现此接口的异常（业务逻辑错误）直接标记为最终失败。核心实现类：
  - `NetworkTimeoutException`：网络连接/读写超时（`java.net.SocketTimeoutException`、`java.net.ConnectException` 的包装）
  - `ApiUnavailableException`：外部 API 返回 5xx 或连接被拒绝（AList API HTTP 500/502/503、RestClientException 的子集）
  - `StorageTemporarilyUnavailableException`：存储引擎暂时不可用（ONLINE→OFFLINE 状态切换期间的操作）
  - **不实现 RetryableException 的异常**：文件不存在（404）、格式不支持、权限不足（403）、磁盘空间不足、数据校验失败等业务逻辑错误
- **任务执行记录（TaskExecution）**：失败详情字段扩展——每个失败文件记录新增 `retryCount`（已重试次数）和 `maxRetries`（最大重试次数）字段，与 TranscodeTask.retryCount 同步。

## 成功标准 *（强制）*

### 可衡量的结果

- **SC-001**：勾选"源目录转码"后，表单上"目标存储引擎"和"目标文件路径"字段被隐藏（DOM 中不可见或移除），表单可视高度减少约 40%。
- **SC-002**：复选框标签显示为"源目录转码（输出至源文件所在目录）"，所有引用该选项的 UI 文案（提示、确认对话框等）均使用"源目录转码"。
- **SC-003**：源目录转码后，输出文件路径严格等于 `源文件所在目录/源文件名（不含扩展名）.目标扩展名`。通过端到端测试验证。
- **SC-004**：转码任务列表中源路径列的显示格式为 `路径/文件名.扩展名`，不显示纯目录行。
- **SC-005**："清理失败任务"、"清理成功任务"、"重试所有失败文件"三个按钮在转码任务列表页面可见且可操作，每个按钮点击后弹出确认对话框。
- **SC-006**：同引擎同步时，文件传输不经过本地磁盘（对于 AList，通过 `/api/fs/copy` 服务端复制；对于 LOCAL，通过系统级文件拷贝）。性能至少提升 50%（同引擎场景）。**验证方法**：使用 10 个 ≥10MB 的媒体文件在同引擎（AList→AList）和异引擎（AList→LOCAL）场景下分别测量同步耗时，同引擎耗时应 ≤ 异引擎耗时的 50%。通过 `SyncServiceCopyTest` 单元测试验证 copyFile 调用路径正确。
- **SC-007**：前端任何列表页面打开后，30 秒内 HTTP 请求数减少 80% 以上（与改前 5 秒轮询对比，改后仅首次加载和用户主动操作产生 HTTP 请求）。**验证方法**：打开转码任务列表页面，在浏览器开发者工具 Network 面板中统计页面加载完成后 30 秒内的 XHR/Fetch 请求数。改前基线：6 次请求（5 秒轮询 × 6 次）；改后预期：≤1 次（仅首次 REST 加载）。通过 `WebSocketConfigTest` 验证连接建立和消息推送正常。
- **SC-008**：WebSocket 连接断线后通过指数退避策略自动重连（初始间隔 1 秒，最大 30 秒），重连成功后前端无需任何手动刷新操作即可恢复数据更新。认证失败（401）时不重连，引导用户重新登录。
- **SC-009**：配置 `app.retry.max-auto-retries=3` 后，瞬时网络故障导致的同步失败文件在 3 次自动重试内恢复成功率 ≥ 80%。**验证方法**：模拟 `NetworkTimeoutException`（断开网络 5 秒后恢复），通过 `SyncServiceRetryTest` 验证重试触发、retryCount 递增、重试成功后状态恢复 COMPLETED。
- **SC-010**：每个失败文件的重试次数在前端任务执行详情中可见（格式："重试 2/3"），用户无需查阅日志即可了解重试状态。

## 假设

- **A1**：AList 的 `/api/fs/copy` 接口行为为服务端内部复制（不经过客户端网络），目标目录必须已存在。
- **A2**：当前单实例部署架构下，WebSocket 会话管理和消息推送在应用进程内完成，无需引入外部消息中间件（如 Redis Pub/Sub）。
- **A3**：前端所有 WebSocket 消费者（转码列表页、同步列表页）可在同一 WebSocket 连接上通过消息类型路由到不同的数据更新逻辑。
- **A4**：所有前端列表页面（转码、同步、Webhook 事件、Dashboard）均已迁移至 WebSocket，`usePolling.ts` 被删除。无页面继续使用轮询机制。
- **A5**：Spring WebSocket 支持通过 `spring-boot-starter-websocket` 自动配置，无需额外的中间件或代理配置。
- **A6**：浏览器兼容性：WebSocket API 在所有现代浏览器中均受支持（Chrome、Firefox、Edge、Safari），无需 polyfill。WebSocket 连接通过 HTTP Upgrade 握手的 `Authorization` 请求头传递 Basic Auth 凭据，后端握手拦截器验证。
- **A7**：同步模块不同引擎场景下的中间文件存储在独立子目录（`{temp-dir}/sync/`），与转码临时文件分隔管理，但遵循相同的 24 小时定时清理策略。
- **A8**：008 的实现以 006（storage-engine-refactor）已完成为前提，转码任务使用 8 状态模型。若 006 尚未实现，008 中的批量操作（依赖 DOWNLOAD_FAILED/TRANSCODE_FAILED/UPLOAD_FAILED 状态）需等待 006 完成后再执行。
- **A9**：DTO 字段 `sourceDirectoryTranscode` 的命名：若 007 已完成（使用 `sameDirectoryTranscode`），008 将其修改为 `sourceDirectoryTranscode`；若 007 尚未实现，008 直接使用新字段名。
- **A10**：自动重试仅适用于瞬时故障（网络超时、API 临时不可用），不适用于业务逻辑错误（如文件损坏、格式不支持）。通过 `RetryableException` 标记接口区分——网络超时、HTTP 5xx、连接拒绝等异常实现此接口触发自动重试；文件不存在（404）、格式不支持、权限不足等异常不实现此接口，直接标记为最终失败。
- **A11**：同步和转码使用共享的 `ConflictStrategy` 枚举（OVERWRITE/SKIP/RENAME），定义在 common 包中，由各 Service 在对应步骤中统一查询执行。

## 与其他规格的关系

### 对 002-transcode-temp-suffix-config 的修改

- **临时文件清理策略**：002 中定义的"启动时无条件清理所有残留临时文件"（FR-013）被替代为"上传成功后立即清理，失败状态下保留供重试，定时任务清理超过 24 小时的孤立任务和临时文件"（与 006 FR-007 统一）。
- **同步模块临时目录**：002 的临时目录（`temp-dir`）新增 `sync/` 子目录用于同步模块的下载→上传中间文件，独立于转码临时文件目录。

### 对 006-storage-engine-refactor 的依赖

- **8 状态模型**：008 的批量操作功能（FR-012~FR-014）直接依赖 006 定义的 8 状态模型（PENDING/DOWNLOADING/DOWNLOAD_FAILED/TRANSCODING/TRANSCODE_FAILED/UPLOADING/UPLOAD_FAILED/COMPLETED）。008 以 006 已完成为前提。
- **临时文件生命周期**：008 遵循 006 中定义的临时文件管理策略（上传成功后立即清理、失败保留、24 小时定时清理），与 006 保持一致。

### 对 007-password-encryption-and-code-organization 的修改

- 007 中新增的"原目录转码"选项（FR-006 ~ FR-009, FR-020 ~ FR-022）在本规格中被进一步优化：
  - 选项名称从"原目录转码"更正为"源目录转码"
  - 勾选后隐藏"目标存储引擎"和"目标文件路径"（而非仅禁用目标文件路径）
  - `targetEngineId` 从必填改为可选（源目录转码时不需要）

### 对 001-alist-media-sync 的修改

- **StorageEngineStrategy 接口**：新增 `copyFile` 默认方法。
- **SyncService**：新增同引擎检测和复制逻辑。
- **TranscodeTaskController**：新增批量清理和批量重试端点。
- **TranscodeTaskRepository**：新增按状态批量操作查询方法。
- **前端 poll→WebSocket**：移除转码/同步列表页的轮询逻辑，新增 WebSocket 连接管理。

### 对 004-web-management-frontend 的依赖

- 前端 `TranscodeTaskForm.tsx`：复选框标签改文案、勾选后隐藏字段。
- 前端 `TranscodeTaskListPage.tsx`：新增批量操作按钮、移除轮询改为 WebSocket。
- 前端 `SyncTaskListPage.tsx`：移除轮询改为 WebSocket。
- 前端 `SyncTaskDetailPage.tsx`：移除轮询改为 WebSocket。
- 前端 `WebhookEventListPage.tsx`：移除轮询改为 WebSocket。
- 前端 `DashboardPage.tsx`：移除轮询改为 WebSocket（保留 REST 初始加载）。
- 前端新增 `useWebSocket.ts` Hook。
- 前端 `client.ts` 类型定义新增批量操作 API 方法和 WebSocket 消息类型。
