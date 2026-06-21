# 任务：转码与同步模块优化及实时通信改造

**输入**：来自 `/specs/008-transcode-sync-optimization/` 的设计文档
**前提条件**：plan.md（必需）、spec.md（用户故事必需）、research.md、data-model.md、contracts/

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（例如，US1、US2、US3...US8）
- 在描述中包含确切的文件路径

## 路径约定

- **后端**：`src/main/java/top/lldwb/alistmediasync/`
- **前端**：`src/main/frontend/src/`
- **测试**：`src/test/java/top/lldwb/alistmediasync/`

---

## 阶段 1：设置（共享基础设施）

**目的**：项目初始化和依赖配置

- [ ] T001 在 `pom.xml` 中添加 `spring-boot-starter-websocket` 依赖
- [ ] T002 [P] 在 `src/main/java/top/lldwb/alistmediasync/common/config/AppProperties.java` 中新增 WebSocket、重试、存储健康检查配置属性
- [ ] T003 [P] 在 `src/main/resources/application.yaml` 中添加 `app.websocket`、`app.retry`、`app.storage.health-check` 配置项

---

## 阶段 2：基础（阻塞性前置条件）

**目的**：在任何用户故事可以开始实现之前必须完成的核心基础设施

**⚠️ 关键**：在此阶段完成之前，不能开始任何用户故事的工作

- [ ] T004 在 `src/main/java/top/lldwb/alistmediasync/common/enums/MessageType.java` 中创建消息类型枚举（SYNC_PROGRESS、TRANSCODE_PROGRESS、TASK_EVENT、WEBHOOK_EVENT、DASHBOARD_UPDATE）
- [ ] T005 [P] 在 `src/main/java/top/lldwb/alistmediasync/common/dto/WsMessage.java` 中创建 WebSocket 消息 DTO record（包含 `type`（String，引用 MessageType 枚举值）、`payload`（Object）、`timestamp`（String）字段，`timestamp` 由 WsSessionManager 在推送时自动填充 `Instant.now().toString()`，不由客户端生成）——与 T004 无编译依赖（type 为 String 类型），可并行
- [ ] T006 在 `src/main/java/top/lldwb/alistmediasync/common/exception/RetryableException.java` 中创建可重试异常标记接口
- [ ] T007 在 `src/main/java/top/lldwb/alistmediasync/common/interceptor/WebSocketAuthInterceptor.java` 中实现 WebSocket 握手认证拦截器（读取 Authorization 头进行 Basic Auth 验证）
- [ ] T008 在 `src/main/java/top/lldwb/alistmediasync/common/config/WebSocketConfig.java` 中配置 WebSocket 端点 `/ws/events`，注册握手拦截器，实现连接数上限控制
- [ ] T009 在 `src/main/java/top/lldwb/alistmediasync/common/service/WsSessionManager.java` 中实现 WebSocket 会话管理与消息广播（含连接数计数、DASHBOARD_UPDATE 2 秒防抖合并：任务状态变更后延迟 2 秒推送，2 秒内多次变更合并为一次 Dashboard 更新）

**检查点**：基础就绪 — WebSocket 基础设施已搭建，现在可以并行开始用户故事实现

---

## 阶段 3：用户故事 8 — WebSocket 实时推送替代轮询（优先级：P1）🎯 MVP

**目标**：前端所有列表页面通过 WebSocket 接收实时增量更新，替代 5 秒 HTTP 轮询

**独立测试**：打开任意管理页面，通过浏览器开发者工具验证建立了 WebSocket 连接，当有任务执行时验证进度通过 WebSocket 实时推送，无额外 HTTP 轮询请求。`usePolling.ts` 已被删除。

### 用户故事 8 的实现

- [ ] T010 [US8] 在 `src/main/frontend/src/types/index.ts` 中新增 WsMessage、MessageType、WsPayload 等 TypeScript 类型定义
- [ ] T011 [US8] 在 `src/main/frontend/src/hooks/useWebSocket.ts` 中实现 WebSocket 连接管理 Hook（建立连接、消息分发、断线指数退避重连 1s→30s、认证失败跳转登录、页面卸载断开）
- [ ] T012 [US8] 在 `src/main/frontend/src/pages/TranscodeTaskListPage.tsx` 中移除 `usePolling` 调用，改为 `useWebSocket` 接收 TRANSCODE_PROGRESS / TASK_EVENT 消息，保留初始 REST 加载
- [ ] T013 [P] [US8] 在 `src/main/frontend/src/pages/SyncTaskListPage.tsx` 中移除 `usePolling` 调用，改为 `useWebSocket` 接收 SYNC_PROGRESS 消息，保留初始 REST 加载
- [ ] T014 [P] [US8] 在 `src/main/frontend/src/pages/SyncTaskDetailPage.tsx` 中移除 `usePolling` 调用，改为 `useWebSocket` 接收 SYNC_PROGRESS 消息，保留初始 REST 加载
- [ ] T015 [P] [US8] 在 `src/main/frontend/src/pages/WebhookEventListPage.tsx` 中移除 `usePolling` 调用，改为 `useWebSocket` 接收 WEBHOOK_EVENT 消息，保留初始 REST 加载
- [ ] T016 [P] [US8] 在 `src/main/frontend/src/pages/DashboardPage.tsx` 中移除 `usePolling` 调用，改为 `useWebSocket` 接收 DASHBOARD_UPDATE 消息（保留 REST 初始加载 `GET /api/dashboard/stats`）
- [ ] T017 [US8] 在 `src/main/frontend/src/api/client.ts` 中新增 WebSocket 消息类型对应的状态更新辅助函数
- [ ] T018 [US8] 删除 `src/main/frontend/src/hooks/usePolling.ts` 文件
- [ ] T019 [US8] 在 `src/main/java/top/lldwb/alistmediasync/sync/service/SyncService.java` 中同步执行状态变更时调用 WsSessionManager 推送 SYNC_PROGRESS 消息
- [ ] T020 [P] [US8] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeFileProcessor.java` 中转码状态变更时调用 WsSessionManager 推送 TRANSCODE_PROGRESS 消息
- [ ] T021 [P] [US8] 在 `src/main/java/top/lldwb/alistmediasync/transcode/controller/TranscodeTaskController.java` 中创建/删除任务时推送 TASK_EVENT 消息
- [ ] T022 [P] [US8] 在 `src/main/java/top/lldwb/alistmediasync/webhook/service/WebhookService.java` 中 Webhook 事件状态变更时推送 WEBHOOK_EVENT 消息
- [ ] T023 [P] [US8] 在 `src/main/java/top/lldwb/alistmediasync/sync/controller/SyncTaskController.java` 中创建/删除同步任务时推送 TASK_EVENT 消息
- [ ] T024 [US8] 为 `WebSocketConfig.java`、`WsSessionManager.java`、`WebSocketAuthInterceptor.java` 编写单元测试 `src/test/java/top/lldwb/alistmediasync/common/WebSocketConfigTest.java`（含 DASHBOARD_UPDATE 防抖验证：2 秒内多次状态变更仅推送一次、连接数上限 429 拒绝）
- [ ] T024a [US8] 为 `SyncService.java` 中 WebSocket 推送逻辑（T019）编写单元测试，验证 SYNC_PROGRESS 消息在同步状态变更时正确推送
- [ ] T024b [P] [US8] 为 `TranscodeFileProcessor.java` 中 WebSocket 推送逻辑（T020）编写单元测试，验证 TRANSCODE_PROGRESS 消息在转码状态变更时正确推送
- [ ] T024c [P] [US8] 为 `WebhookService.java` 中 WebSocket 推送逻辑（T022）编写单元测试，验证 WEBHOOK_EVENT 消息在事件状态变更时正确推送

**检查点**：WebSocket 实时推送全面替代轮询，所有列表页面 30 秒内 HTTP 请求减少 80%+

---

## 阶段 4：用户故事 5 — 同步模块同引擎复制优化（优先级：P1）

**目标**：同存储引擎间同步直接调用 copy 方法，避免下载→上传低效流程

**独立测试**：创建源和目标为同一引擎的同步任务，触发同步，验证通过 copyFile 完成（AList 用 `/api/fs/copy`，本地用 `Files.copy`）。不同引擎仍用原来的下载→上传流程。

### 用户故事 5 的实现

- [ ] T025 [US5] 在 `src/main/java/top/lldwb/alistmediasync/storage/service/engine/StorageEngineStrategy.java` 中新增 `copyFile(StorageEngine engine, String sourcePath, String targetPath)` 默认方法（抛 UnsupportedOperationException）
- [ ] T026 [P] [US5] 在 `src/main/java/top/lldwb/alistmediasync/storage/service/engine/AListStorageStrategy.java` 中实现 copyFile()：调用 AList `/api/fs/copy` API（POST，body 含 src_dir、dst_dir、names），按源目录分组批量调用，SKIP 文件先过滤
- [ ] T027 [P] [US5] 在 `src/main/java/top/lldwb/alistmediasync/storage/service/engine/LocalStorageStrategy.java` 中实现 copyFile()：使用 `java.nio.file.Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)`
- [ ] T028 [US5] 在 `src/main/java/top/lldwb/alistmediasync/sync/service/SyncService.java` 的 `executeSyncTask()` 中新增同引擎检测逻辑：`sourceEngine.getId().equals(targetEngine.getId())` 时调用 `targetStrategy.copyFile()` 替代下载→上传，调用前确保目标父目录存在
- [ ] T029 [US5] 在 `src/main/java/top/lldwb/alistmediasync/sync/service/SyncService.java` 中同引擎复制时处理冲突策略（使用共享 ConflictStrategy 枚举：SKIP 检查目标存在则跳过，OVERWRITE 直接覆盖，RENAME 生成不重名路径）。RENAME 策略路径生成规则：在目标文件名（不含扩展名）后追加 `_1`、`_2`... 后缀直至目标路径不存在冲突，扩展名保持不变（如 `recording.mp3` → `recording_1.mp3` → `recording_2.mp3`）
- [ ] T030 [US5] 同引擎复制进度追踪与错误处理保持与现有同步流程一致（成功/失败计数、TaskExecution 记录、失败详情记录日志）
- [ ] T031 [US5] 为 `AListStorageStrategy.copyFile()` 和 `LocalStorageStrategy.copyFile()` 编写单元测试 `src/test/java/top/lldwb/alistmediasync/storage/StorageEngineCopyTest.java`
- [ ] T032 [US5] 为 `SyncService` 同引擎复制逻辑编写单元测试 `src/test/java/top/lldwb/alistmediasync/sync/SyncServiceCopyTest.java`

**检查点**：同引擎同步性能提升 ≥ 50%，不同引擎行为不变

---

## 阶段 5：用户故事 7 — 同步/转码失败自动重试（优先级：P2）

**目标**：因瞬时故障（网络超时、API 临时不可用）失败的操作自动以指数退避策略重试，业务错误不重试

**独立测试**：配置 `app.retry.max-auto-retries=3`，模拟网络中断触发同步/转码失败，验证自动重试 3 次（指数退避）后用尽标记为最终失败。业务错误（如文件不存在）不重试。

### 用户故事 7 的实现

- [ ] T033 [US7] 在 `src/main/java/top/lldwb/alistmediasync/common/config/AppProperties.java` 中新增 RetryConfig 内部类（maxAutoRetries、initialInterval、maxInterval），确保 `app.retry.*` 配置绑定
- [ ] T034 [US7] 在 `src/main/java/top/lldwb/alistmediasync/common/service/RetryService.java` 中实现自动重试调度服务：`ScheduledExecutorService` 调度，指数退避公式 `min(1000*2^(attempt-1), 60000)`，通过 `instanceof RetryableException` 判断是否重试
- [ ] T035 [US7] 在 `src/main/java/top/lldwb/alistmediasync/transcode/entity/TranscodeTask.java` 中新增 `retryCount` 字段（`int`，`@Column(nullable=false)`，默认 0）
- [ ] T036 [US7] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeFileProcessor.java` 三步流程（下载/转码/上传）的 catch 块中：捕获 RetryableException 时调用 RetryService 调度自动重试并递增 retryCount，非 RetryableException 直接标记最终失败
- [ ] T037 [US7] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeFileProcessor.java` 中确保失败时按步骤精确设置对应 FAILED 状态（DOWNLOAD_FAILED / TRANSCODE_FAILED / UPLOAD_FAILED），检查 006 代码状态后补充
- [ ] T038 [US7] 在 `src/main/java/top/lldwb/alistmediasync/sync/service/SyncService.java` 的同步文件处理中集成自动重试：捕获 RetryableException 时调度重试，非 RetryableException 直接标记最终失败
- [ ] T039 [US7] 在 `src/main/java/top/lldwb/alistmediasync/sync/entity/TaskExecution.java` 的 failureDetails JSON 构建逻辑中新增 `retryCount` 和 `maxRetries` 字段
- [ ] T040 [US7] 在 `src/main/java/top/lldwb/alistmediasync/transcode/dto/transcode/TranscodeTaskVO.java` 中新增 `retryCount` 字段，确保前端可展示"重试 N/M"
- [ ] T041 [US7] 在 `src/main/java/top/lldwb/alistmediasync/transcode/controller/TranscodeTaskController.java` 的手动重试端点中确保手动重试不计入自动重试次数限制（始终执行）
- [ ] T042 [US7] 为 `RetryService.java` 编写单元测试 `src/test/java/top/lldwb/alistmediasync/common/RetryServiceTest.java`（验证指数退避计算、RetryableException 判断、重试用尽行为）
- [ ] T043 [US7] 为 `TranscodeFileProcessor` 自动重试逻辑编写单元测试 `src/test/java/top/lldwb/alistmediasync/transcode/TranscodeFileProcessorRetryTest.java`
- [ ] T043a [US7] 为 `SyncService` 自动重试逻辑（T038）编写单元测试 `src/test/java/top/lldwb/alistmediasync/sync/SyncServiceRetryTest.java`，验证 RetryableException 触发重试、非 RetryableException 直接标记失败、retryCount 递增

**检查点**：瞬时故障自动重试 3 次内恢复成功率 ≥ 80%，业务错误不重试

---

## 阶段 6：用户故事 1 — 源目录转码选项简化（优先级：P1）

**目标**：勾选"源目录转码"后隐藏"目标文件路径"和"目标存储引擎"字段，文案从"原"改为"源"

**独立测试**：打开转码任务创建页面，勾选复选框验证字段隐藏，取消勾选验证字段恢复，文案显示正确。

### 用户故事 1 的实现

- [ ] T044 [US1] 在 `src/main/frontend/src/pages/TranscodeTaskForm.tsx` 中将复选框标签文案从"原目录转码（输出至源文件所在目录）"改为"源目录转码（输出至源文件所在目录）"
- [ ] T045 [US1] 在 `src/main/frontend/src/pages/TranscodeTaskForm.tsx` 中实现：勾选"源目录转码"时隐藏（非禁用）"目标存储引擎"和"目标文件路径"字段及其标签，取消勾选时重新显示并恢复必填。边界情况：勾选"源目录转码"但未选择源存储引擎时，提交表单应提示"请先选择源存储引擎"
- [ ] T046 [US1] 在 `src/main/frontend/src/pages/TranscodeTaskForm.tsx` 中修改表单提交逻辑：当 `sourceDirectoryTranscode=true` 时不传 `targetEngineId`（或传 null），`targetFilePath` 传空字符串
- [ ] T047 [US1] 在 `src/main/java/top/lldwb/alistmediasync/transcode/dto/transcode/TranscodeTaskCreateDTO.java` 中将 `sameDirectoryTranscode` 字段重命名为 `sourceDirectoryTranscode`，将 `targetEngineId` 的 `@NotNull` 改为条件校验（`sourceDirectoryTranscode=true` 时可为空）
- [ ] T048 [US1] 在 `src/main/java/top/lldwb/alistmediasync/transcode/controller/TranscodeTaskController.java` 的创建任务端点中，当 `sourceDirectoryTranscode=true` 时自动将 `targetEngineId` 赋值为 `sourceEngineId`
- [ ] T049 [US1] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeService.java` 的 `createTask()` 方法中更新字段引用（sameDirectoryTranscode → sourceDirectoryTranscode）
- [ ] T050 [US1] 为 `TranscodeTaskCreateDTO` 条件校验编写单元测试 `src/test/java/top/lldwb/alistmediasync/transcode/TranscodeTaskCreateDTOValidationTest.java`

**检查点**：UX 优化完成——表单在勾选后隐藏无关字段，文案正确

---

## 阶段 7：用户故事 2 — 源目录转码路径计算修正（优先级：P1）

**目标**：源目录转码输出路径严格等于 `源文件所在目录/源文件名（不含扩展名）.目标扩展名`

**独立测试**：创建源目录转码任务，源路径 `/videos/sub/recording.flv`，目标格式 MP3，验证输出路径为 `/videos/sub/recording.mp3`。

### 用户故事 2 的实现

- [ ] T051 [US2] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeService.java` 的 `createTask()` 方法中修正 `sourceDirectoryTranscode=true` 时的 `targetFilePath` 计算：设为源文件的完整目录路径（`sourceFilePath` 去掉文件名后的目录部分）
- [ ] T052 [US2] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeFileProcessor.java` 中确保 `uploadStep` 的输出文件路径遵循规则：`源文件所在目录 / 源文件名（不含原扩展名）.目标格式扩展名`，目录模式下每个文件独立计算
- [ ] T053 [US2] 验证 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeFileProcessor.java` 中 `getOutputName()` 方法与源目录转码场景的兼容性（参考已有逻辑行 264-268），确保路径拼接正确
- [ ] T054 [US2] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeCandidate.java` record 中确保 `targetPath` 字段在源目录转码场景下等于源文件所在目录路径
- [ ] T055 [US2] 为源目录转码路径计算编写单元测试 `src/test/java/top/lldwb/alistmediasync/transcode/TranscodePathCalculationTest.java`（覆盖目录模式、根目录文件、子目录文件等场景）

**检查点**：输出路径严格遵循 `源文件所在目录/文件名.目标扩展名`，端到端测试通过

---

## 阶段 8：用户故事 3 — 转码任务列表仅显示文件（优先级：P2）

**目标**：转码任务列表只显示文件级别条目，格式为"路径/文件名"，不显示纯目录行

**独立测试**：创建包含目录模式的转码任务，验证列表中每行显示"路径/文件名"格式，无纯目录条目。

### 用户故事 3 的实现

- [ ] T056 [US3] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeService.java` 的 `listAll()` 方法中过滤掉不关联具体文件的 TranscodeTask（如目录级别的父任务），确保每条记录对应一个文件
- [ ] T057 [US3] 在 `src/main/java/top/lldwb/alistmediasync/transcode/dto/transcode/TranscodeTaskVO.java` 中确保 `sourcePath` 和 `targetPath` 的格式为 `完整路径/文件名.扩展名`
- [ ] T058 [US3] 在 `src/main/frontend/src/pages/TranscodeTaskListPage.tsx` 中确保源路径列和目标路径列的渲染格式为 `路径/文件名.扩展名`
- [ ] T059 [US3] 为列表过滤逻辑编写单元测试 `src/test/java/top/lldwb/alistmediasync/transcode/TranscodeServiceListTest.java`

**检查点**：列表中每行显示一个文件的完整路径，无目录条目

---

## 阶段 9：用户故事 4 — 转码任务批量操作（优先级：P2）

**目标**：转码任务列表页新增"清理失败任务"、"清理成功任务"、"重试所有失败文件"三个按钮

**独立测试**：在包含成功和失败任务的列表中点击各按钮，验证确认对话框、加载态、结果提示和列表刷新。

### 用户故事 4 的实现

- [ ] T060 [US4] 在 `src/main/java/top/lldwb/alistmediasync/transcode/repository/TranscodeTaskRepository.java` 中新增 `deleteByStatusIn(List<TranscodeStatus> statuses)` 按状态批量删除方法和 `findByStatusIn(List<TranscodeStatus> statuses)` 按状态批量查询方法
- [ ] T061 [US4] 在 `src/main/java/top/lldwb/alistmediasync/transcode/controller/TranscodeTaskController.java` 中新增 `DELETE /api/transcode-tasks/failed` 端点（删除 DOWNLOAD_FAILED/TRANSCODE_FAILED/UPLOAD_FAILED 状态任务，返回删除数量，推送 TASK_EVENT）
- [ ] T062 [US4] 在 `src/main/java/top/lldwb/alistmediasync/transcode/controller/TranscodeTaskController.java` 中新增 `DELETE /api/transcode-tasks/completed` 端点（删除 COMPLETED 状态任务，返回删除数量，推送 TASK_EVENT）
- [ ] T063 [US4] 在 `src/main/java/top/lldwb/alistmediasync/transcode/controller/TranscodeTaskController.java` 中新增 `POST /api/transcode-tasks/retry-all` 端点（异步执行，立即返回 202 Accepted，对 DOWNLOAD_FAILED/TRANSCODE_FAILED/UPLOAD_FAILED 任务执行重试，结果通过 WebSocket TRANSCODE_PROGRESS 逐条推送）
- [ ] T064 [US4] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeService.java` 中实现 `deleteFailedTasks()`、`deleteCompletedTasks()`、`retryAllFailedTasks()` 方法，批量删除使用 `@Transactional`
- [ ] T065 [US4] 在 `src/main/frontend/src/api/client.ts` 中新增 `deleteFailedTranscodeTasks()`、`deleteCompletedTranscodeTasks()`、`retryAllTranscodeTasks()` API 方法
- [ ] T066 [US4] 在 `src/main/frontend/src/pages/TranscodeTaskListPage.tsx` 中新增三个批量操作按钮（"清理失败任务"、"清理成功任务"、"重试所有失败文件"），每个按钮点击后弹出确认对话框，确认后进入加载态并禁用，API 返回后展示结果提示
- [ ] T067 [US4] 在 `src/main/frontend/src/pages/TranscodeTaskListPage.tsx` 中处理边界情况：无失败/成功任务时点击按钮显示"没有可操作的失败任务"/"没有可清理的成功任务"
- [ ] T068 [US4] 为批量操作端点编写单元测试 `src/test/java/top/lldwb/alistmediasync/transcode/TranscodeTaskControllerBatchTest.java`

**检查点**：三个批量操作按钮功能完整，确认对话框、加载态、结果提示和列表刷新均正常工作

---

## 阶段 10：同步后置转码配置扩展

**目的**：SyncTask 实体补充转码配置字段（FR-001→FR-023 实现后的补充）

**独立测试**：创建启用后置转码的同步任务并配置 `transcodeTargetFormat` 和 `transcodeBitrate`，验证后置转码使用正确参数。

- [ ] T069 在 `src/main/java/top/lldwb/alistmediasync/sync/entity/SyncTask.java` 中新增 `transcodeTargetFormat`（String，默认 "MP3"）和 `transcodeBitrate`（int，默认 128000）字段
- [ ] T070 [P] 在 `src/main/java/top/lldwb/alistmediasync/sync/dto/sync/SyncTaskCreateDTO.java` 中新增 `transcodeTargetFormat` 和 `transcodeBitrate` 字段及校验（格式枚举 MP3/MP4/FLV，码率 32000-320000）
- [ ] T071 [P] 在 `src/main/frontend/src/pages/SyncTaskListPage.tsx`（或对应的表单组件）中新增后置转码目标格式和码率配置项（当 `transcodeEnabled=true` 时显示）

---

## 阶段 11：润色与跨领域关注点

**目的**：影响多个用户故事的改进和最终验证

- [ ] T072 [P] 在 `src/main/resources/application.template.yaml` 中同步新增的配置项（`app.websocket.*`、`app.retry.*`、`app.storage.health-check-interval`）
- [ ] T073 [P] 在 `.env` 和 `docker-compose.yml` 中新增环境变量（`APP_WEBSOCKET_MAX_CONNECTIONS`、`APP_RETRY_MAX_AUTO_RETRIES`），移除 `ALIST_BASE_URL` 和 `ALIST_TOKEN`
- [ ] T074 [P] 在 `src/main/java/top/lldwb/alistmediasync/common/service/CleanupService.java` 中将临时文件清理策略从"启动时无条件清理"改为"定时任务清理超过 24 小时的孤立任务和临时文件"（替代 002 FR-013 策略），同步临时文件由 SyncService 自管生命周期
- [ ] T074a [P] 为 `CleanupService` 定时清理逻辑编写单元测试 `src/test/java/top/lldwb/alistmediasync/common/CleanupServiceTest.java`（验证超过 24 小时文件被清理、不足 24 小时文件保留、sync/ 子目录不纳入清理）
- [ ] T075 [P] 在 `src/main/java/top/lldwb/alistmediasync/storage/service/StorageEngineService.java` 中新增定时健康检查（`@Scheduled`，间隔由 `app.storage.health-check-interval` 配置，默认 5 分钟），自动更新 EngineStatus
- [ ] T075a [P] 为 `StorageEngineService` 定时健康检查编写单元测试 `src/test/java/top/lldwb/alistmediasync/storage/StorageEngineHealthCheckTest.java`（验证定时任务触发、ONLINE/OFFLINE/ERROR 状态自动切换）
- [ ] T076 运行 quickstart.md 中的所有验证场景，确认功能端到端可用
- [ ] T077 [P] 更新 `README.md`：新增 WebSocket 配置说明、自动重试配置、同引擎复制说明、新增 REST API 端点文档、新增环境变量表
- [ ] T078 [P] 更新模块 AGENTS.md 文件以反映新增类和方法：
  - [ ] T078a `src/main/java/top/lldwb/alistmediasync/common/AGENTS.md`：新增 WebSocket 配置（WebSocketConfig）、会话管理（WsSessionManager）、消息 DTO（WsMessage）、消息类型枚举（MessageType）、认证拦截器（WebSocketAuthInterceptor）、可重试异常（RetryableException）、重试服务（RetryService）、清理服务变更（CleanupService）
  - [ ] T078b `src/main/java/top/lldwb/alistmediasync/storage/AGENTS.md`：新增 StorageEngineStrategy.copyFile() 方法、AListStorageStrategy/LocalStorageStrategy 的 copyFile 实现、StorageEngineService 健康检查
  - [ ] T078c `src/main/java/top/lldwb/alistmediasync/sync/AGENTS.md`：新增 SyncTask 实体字段（transcodeTargetFormat/transcodeBitrate）、SyncService 同引擎复制逻辑和自动重试集成、TaskExecution failureDetails JSON 扩展
  - [ ] T078d `src/main/java/top/lldwb/alistmediasync/transcode/AGENTS.md`：新增 TranscodeTask.retryCount 字段、批量操作端点（DELETE /failed、DELETE /completed、POST /retry-all）、Repository 批量方法、TranscodeFileProcessor 精确状态设置和自动重试逻辑、DTO 字段重命名（sameDirectoryTranscode→sourceDirectoryTranscode）

---

## 依赖与执行顺序

### 阶段依赖

- **设置（阶段 1）**：无依赖 — 可立即开始
- **基础（阶段 2）**：依赖阶段 1 完成（需要 WebSocket 依赖和配置属性）— 阻塞所有用户故事
- **用户故事 8（阶段 3）**：依赖基础阶段完成（需要 WsSessionManager、WebSocketConfig）— WebSocket 基础设施就绪后所有推送才可用
- **用户故事 5（阶段 4）**：依赖基础阶段完成 — 可与 US8 并行（不同模块）
- **用户故事 7（阶段 5）**：依赖基础阶段完成（需要 RetryableException）— 可与 US8/US5 并行
- **用户故事 1（阶段 6）**：依赖基础阶段完成 — 可与 US8/US5/US7 并行（仅涉及转码表单前端+DTO）
- **用户故事 2（阶段 7）**：依赖 US1 完成（共享 DTO 字段重命名 sourceDirectoryTranscode）— US1 完成后开始
- **用户故事 3（阶段 8）**：依赖基础阶段完成 — 可与 US4 并行
- **用户故事 4（阶段 9）**：依赖基础阶段完成 + US8（需要 WebSocket 推送 TASK_EVENT 和 TRANSCODE_PROGRESS）
- **阶段 10**：依赖 US5 完成（SyncTask 字段与 SyncService 同引擎逻辑配合）
- **润色（阶段 11）**：依赖所有用户故事完成

### 用户故事依赖

- **US8（P1）**：依赖阶段 2 基础 — 是其他 US 的前置（WebSocket 推送在其他 US 中产生副作用）
- **US5（P1）**：依赖阶段 2 基础 — 独立，可并行
- **US7（P2）**：依赖阶段 2 基础 — 独立，可并行
- **US1（P1）**：依赖阶段 2 基础 — 独立，可并行。US2 依赖 US1
- **US2（P1）**：依赖 US1（共享 DTO 字段重命名）
- **US3（P2）**：依赖阶段 2 基础 — 独立，可并行
- **US4（P2）**：依赖 US8（批量操作需要 WebSocket 推送）+ 阶段 2 基础

### 每个用户故事内部

- 后端实体变更先于 Service 逻辑
- Service 逻辑先于 Controller 端点
- Controller 端点先于前端调用
- 前端类型定义先于 Hook/组件实现
- 核心实现先于测试
- 故事完成后再进入下一个优先级

### 并行机会

- 阶段 1 中 T002/T003 可并行
- 阶段 2 中 T004/T005/T006 可并行（不同文件，无依赖；T005.type 为 String 类型，不依赖 T004 的 MessageType 枚举编译）
- US8 中 T012-T016（各页面迁移）可并行，T019-T023（各 Service/Controller 推送逻辑）可并行
- US5 中 T026/T027（两种策略实现）可并行
- US3 和 US4 可并行（转码模块的不同关注点）
- 阶段 11 中 T072/T073/T074/T074a/T075/T075a 可并行

---

## 并行示例：用户故事 8

```bash
# 一起启动各页面的 WebSocket 迁移（不同文件，无依赖）：
任务 T012："在 TranscodeTaskListPage.tsx 中迁移至 WebSocket"
任务 T013："在 SyncTaskListPage.tsx 中迁移至 WebSocket"
任务 T014："在 SyncTaskDetailPage.tsx 中迁移至 WebSocket"
任务 T015："在 WebhookEventListPage.tsx 中迁移至 WebSocket"
任务 T016："在 DashboardPage.tsx 中迁移至 WebSocket"

# 一起启动各模块的 WebSocket 推送逻辑（不同模块，无依赖）：
任务 T019："在 SyncService.java 中推送 SYNC_PROGRESS"
任务 T020："在 TranscodeFileProcessor.java 中推送 TRANSCODE_PROGRESS"
任务 T021："在 TranscodeTaskController.java 中推送 TASK_EVENT"
任务 T022："在 WebhookService.java 中推送 WEBHOOK_EVENT"
任务 T023："在 SyncTaskController.java 中推送 TASK_EVENT"
```

## 并行示例：用户故事 5

```bash
# 一起启动两种策略的 copyFile 实现（不同文件，无依赖）：
任务 T026："在 AListStorageStrategy.java 中实现 copyFile()"
任务 T027："在 LocalStorageStrategy.java 中实现 copyFile()"
```

---

## 实现策略

### MVP 优先（US8 + US5 + US1 + US2）

1. 完成阶段 1：设置（Maven 依赖 + 配置）
2. 完成阶段 2：基础（WebSocket 基础设施 + RetryableException）
3. 完成阶段 3：US8（WebSocket 替代轮询 — 核心基础设施，所有列表页面迁移）
4. 完成阶段 4：US5（同引擎复制优化）
5. 完成阶段 6+7：US1+US2（源目录转码 UX 优化 + 路径修正）
6. **停止并验证**：核心功能全部可用
7. 如就绪则部署/演示

### 增量交付

1. 设置 + 基础 → WebSocket 基础设施就绪
2. 添加 US8 → WebSocket 实时推送全面可用 → 演示（减少 80% HTTP 请求！）
3. 添加 US5 → 同引擎复制优化 → 演示（性能提升 50%+！）
4. 添加 US1+US2 → 转码 UX 优化 → 演示
5. 添加 US7 → 自动重试 → 演示
6. 添加 US3+US4 → 列表优化 + 批量操作 → 演示
7. 添加阶段 10+11 → 同步后置转码配置 + 润色 → 交付
8. 每个故事增加价值而不破坏之前的的故事

### 并行团队策略

多个开发人员时：

1. 团队一起完成设置 + 基础
2. 基础完成后：
   - 开发人员 A：US8（WebSocket — 涉及面最广，优先级最高）
   - 开发人员 B：US5 + US7（同引擎复制 + 自动重试 — 纯后端）
   - 开发人员 C：US1 + US2 + US3 + US4（转码模块 — 前后端混合）
3. US8 完成后开发人员 A 加入开发人员 C 协助 US4（批量操作依赖 WebSocket）

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应能独立完成和测试
- 每个任务或逻辑组后提交
- 在任何检查点停止以独立验证故事
- 避免：模糊的任务、同文件冲突、破坏独立性的跨故事依赖
- **本章程合规**：所有新增/修改 Java 类 MUST 同步编写单元测试（原则 V），所有 API 使用 `ApiResult<T>` 封装（原则 III），所有日志输出遵循四级分级（原则 VII）
