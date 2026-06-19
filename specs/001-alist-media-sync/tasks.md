# 任务：AList 媒体同步与转码工具

**输入**：来自 `/specs/001-alist-media-sync/` 的设计文档

**前提条件**：plan.md（必需）、spec.md（用户故事必需）

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（例如，US1、US2、US3、US4）
- 在描述中包含确切的文件路径

## 路径约定

- **项目根目录**：`C:\Users\Administrator\Documents\GitHub\AList-Media-Sync\`
- Java 源代码：`src/main/java/top/lldwb/alistmediasync/`
- 资源文件：`src/main/resources/`
- 测试源代码：`src/test/java/top/lldwb/alistmediasync/`

---

## 阶段 1：设置（共享基础设施）

**目的**：添加核心依赖和基础配置

- [ ] T001 在 `pom.xml` 中添加 JAVE2 依赖（`ws.schild:jave-core:3.5.0`、`jave-nativebin-win64:3.5.0`、`jave-nativebin-linux64:3.5.0`），在 `<dependencies>` 块内与其它依赖并列
- [ ] T002 [P] 在 `src/main/java/top/lldwb/alistmediasync/config/AppProperties.java` 中创建 `@ConfigurationProperties(prefix="app")` 配置绑定类：包含 `dataDir`、`auth`（username/password）、`transcode`（temp-suffix/temp-dir/max-concurrent-transcode/max-suffix-length ）、`pool`（max-size）属性，添加中文注释说明每个字段的用途
- [ ] T003 [P] 在 `src/main/java/top/lldwb/alistmediasync/dto/ApiResult.java` 中创建统一响应包装类：`code`（Integer）、`message`（String）、`data`（泛型 T），添加静态工厂方法 `success(T data)` 和 `error(int code, String message)`

---

## 阶段 2：基础（阻塞性前置条件）

**目的**：所有用户故事依赖的核心基础设施——实体、Repository、认证拦截器、配置

**⚠️ 关键**：在此阶段完成之前，不能开始任何用户故事的工作

### 实体（所有 6 个实体可并行创建）

- [ ] T004 [P] 在 `src/main/java/top/lldwb/alistmediasync/entity/StorageEngine.java` 中创建存储引擎实体：字段 `id`、`name`、`baseUrl`、`username`、`encryptedToken`（AES-256-GCM `@Convert`）、`status`、`createdAt`、`updatedAt`、`@Version`；添加 JPA 注解和中文注释
- [ ] T005 [P] 在 `src/main/java/top/lldwb/alistmediasync/entity/SyncTask.java` 中创建同步任务实体：字段 `id`、`name`、`sourceEngine`（`@ManyToOne`）、`targetEngine`（`@ManyToOne`）、`sourcePath`、`targetPath`、`syncMode`（enum：NEW_ONLY/FULL/MOVE）、`transcodeEnabled`（Boolean）、`conflictStrategy`（enum：OVERWRITE/SKIP/RENAME）、`excludePatterns`（String，换行分隔）、`scheduleType`（enum：CRON/INTERVAL/MANUAL）、`cronExpression`、`intervalSeconds`、`enabled`、`lastExecutedAt`、`@Version`；添加 JPA 注解和中文注释
- [ ] T006 [P] 在 `src/main/java/top/lldwb/alistmediasync/entity/TaskExecution.java` 中创建任务执行记录实体：字段 `id`、`syncTask`（`@ManyToOne`，可为空）、`transcodeTask`（`@ManyToOne`，可为空）、`webhookEvent`（`@ManyToOne`，可为空）、`taskType`（enum：SYNC/TRANSCODE/WEBHOOK）、`startTime`、`endTime`、`status`（enum：RUNNING/SUCCESS/FAILED/PARTIAL_SUCCESS/INTERRUPTED）、`totalFiles`、`successFiles`、`failedFiles`、`failureDetails`（`@Lob` JSON）、`@Version`；添加 JPA 注解
- [ ] T007 [P] 在 `src/main/java/top/lldwb/alistmediasync/entity/WebhookRule.java` 中创建 Webhook 处理规则实体：字段 `id`、`name`、`triggerEventType`（enum：FILE_CLOSED/SESSION_ENDED）、`roomIdFilter`（Long，可为空）、`action`（enum：SYNC_ONLY/TRANSCODE_ONLY/BOTH）、`targetEngine`（`@ManyToOne`）、`targetPath`、`enabled`、`@Version`；添加 JPA 注解
- [ ] T008 [P] 在 `src/main/java/top/lldwb/alistmediasync/entity/WebhookEvent.java` 中创建 Webhook 事件记录实体：字段 `id`、`eventId`（String，唯一键 `@Column(unique=true)`）、`eventType`、`eventTimestamp`、`sessionId`、`roomId`、`relativePath`、`fileName`、`fileSize`、`duration`、`rawData`（`@Lob` TEXT）、`status`（enum：PENDING/PROCESSING/COMPLETED/FAILED/DUPLICATE）、`createdAt`、`@Version`；添加 JPA 注解
- [ ] T009 [P] 在 `src/main/java/top/lldwb/alistmediasync/entity/TranscodeTask.java` 中创建转码任务实体：字段 `id`、`syncTask`（`@ManyToOne`，可为空）、`webhookRule`（`@ManyToOne`，可为空）、`sourceFilePath`、`targetFilePath`、`sourceFormat`（enum：FLV/MP4/M4V）、`targetFormat`（enum：MP3/MP4/FLV）、`bitrate`（Integer，默认 128000）、`progress`（Integer，0-1000 千分比）、`status`（enum：PENDING/SCANNING/TRANSCODING/UPLOADING/COMPLETED/FAILED）、`tempFilePath`、`createdAt`、`@Version`；添加 JPA 注解

### Repository（所有 6 个 Repository 可并行创建）

- [ ] T010 [P] 在 `src/main/java/top/lldwb/alistmediasync/repository/StorageEngineRepository.java` 中创建 JPA Repository 接口，扩展 `JpaRepository<StorageEngine, Long>`
- [ ] T011 [P] 在 `src/main/java/top/lldwb/alistmediasync/repository/SyncTaskRepository.java` 中创建 JPA Repository 接口，扩展 `JpaRepository<SyncTask, Long>`，添加 `findByEnabledTrue()` 查询方法
- [ ] T012 [P] 在 `src/main/java/top/lldwb/alistmediasync/repository/TaskExecutionRepository.java` 中创建 JPA Repository 接口，扩展 `JpaRepository<TaskExecution, Long>`，添加 `findByStatusAndTaskType(TaskExecutionStatus status, TaskType type)` 和按时间范围过滤的查询方法
- [ ] T013 [P] 在 `src/main/java/top/lldwb/alistmediasync/repository/WebhookRuleRepository.java` 中创建 JPA Repository 接口，扩展 `JpaRepository<WebhookRule, Long>`，添加 `findByEnabledTrue()` 和 `findByTriggerEventTypeAndEnabledTrue(WebhookEventType type)` 查询方法
- [ ] T014 [P] 在 `src/main/java/top/lldwb/alistmediasync/repository/WebhookEventRepository.java` 中创建 JPA Repository 接口，扩展 `JpaRepository<WebhookEvent, Long>`，添加 `findByEventId(String eventId)` 去重查询方法
- [ ] T015 [P] 在 `src/main/java/top/lldwb/alistmediasync/repository/TranscodeTaskRepository.java` 中创建 JPA Repository 接口，扩展 `JpaRepository<TranscodeTask, Long>`

### 认证与配置

- [ ] T016 在 `src/main/java/top/lldwb/alistmediasync/interceptor/AuthInterceptor.java` 中创建认证拦截器：实现 `HandlerInterceptor`，在 `preHandle` 中检查 `Authorization: Basic <base64>` 请求头，对比 `AppProperties.auth.username` 和 BCrypt 密码（密码以 `{bcrypt}` 前缀存储在配置中），不匹配返回 HTTP 401 + JSON `ApiResult` 错误；跳过路径 `/api/webhooks/**`、`/actuator/health`、`/h2-console/**`
- [ ] T017 在 `src/main/java/top/lldwb/alistmediasync/config/WebMvcConfig.java` 中创建 Web MVC 配置：实现 `WebMvcConfigurer`，注册 `AuthInterceptor` 到拦截器链，添加 CORS 映射（允许所有来源用于开发阶段）
- [ ] T018 在 `src/main/resources/application.yaml` 中添加核心应用配置（在已有 Docker 配置基础上扩展）：添加 `app.auth.username`、`app.auth.password`（`{bcrypt}` 哈希值）、`app.transcode.temp-suffix`（默认 `.tmp`）、`app.transcode.temp-dir`（默认 `${java.io.tmpdir}/alist-media-sync/transcode`）、`app.transcode.max-concurrent-transcode`（默认 CPU 核心数）、`app.transcode.max-suffix-length`（50）、`app.pool.max-size`（32）、`app.retention-days`（30，历史记录保留天数），配置 `spring.jpa.hibernate.ddl-auto: update`

**检查点**：基础就绪 — 所有实体和 Repository 可用，认证拦截器就绪，可以开始用户故事实现

---

## 阶段 3：用户故事 1 — 多存储间文件同步（优先级：P1）🎯 MVP

**目标**：实现存储引擎 CRUD + AList API 客户端 + 同步任务 CRUD + 同步执行引擎（三模式）+ 调度管理

**独立测试**：配置两个 AList 存储引擎 → 创建同步任务 → 手动触发执行 → 观察目标存储文件与源存储一致

### 用户故事 1 的实现

#### 存储引擎管理（FR-001、FR-002）

- [ ] T019 [P] [US1] 在 `src/main/java/top/lldwb/alistmediasync/dto/storage/StorageEngineCreateDTO.java` 中创建存储引擎创建 DTO：字段 `name`（`@NotBlank`）、`baseUrl`（`@NotBlank @URL`）、`username`（`@NotBlank`）、`token`（`@NotBlank`，明文，存储时加密）
- [ ] T020 [P] [US1] 在 `src/main/java/top/lldwb/alistmediasync/dto/storage/StorageEngineUpdateDTO.java` 中创建存储引擎更新 DTO：字段 `name`、`baseUrl`、`username`、`token`（全部可选，仅允许管理员修改）
- [ ] T021 [P] [US1] 在 `src/main/java/top/lldwb/alistmediasync/dto/storage/StorageEngineVO.java` 中创建存储引擎视图 VO：字段 `id`、`name`、`baseUrl`、`username`（不返回加密后的 token）、`status`、`createdAt`、`updatedAt`；添加静态工厂方法 `from(StorageEngine entity)`
- [ ] T022 [US1] 在 `src/main/java/top/lldwb/alistmediasync/service/StorageEngineService.java` 中实现存储引擎服务：包含 `create`（密码 Token 加密存储）、`update`、`delete`、`getById`、`listAll`、`testConnection`（向 `{baseUrl}/api/me` 发送 GET 请求验证令牌有效性）；所有写操作 `@Transactional`
- [ ] T023 [US1] 在 `src/main/java/top/lldwb/alistmediasync/controller/StorageEngineController.java` 中实现存储引擎管理 API：`POST /api/storage-engines`（创建）、`PUT /api/storage-engines/{id}`（更新）、`DELETE /api/storage-engines/{id}`（删除）、`GET /api/storage-engines`（列表）、`GET /api/storage-engines/{id}`（详情）、`POST /api/storage-engines/{id}/test`（测试连接）；所有端点返回 `ApiResult<T>`

#### AList API 客户端

- [ ] T024 [US1] 在 `src/main/java/top/lldwb/alistmediasync/client/AListClient.java` 中实现 AList API HTTP 客户端：使用 Spring `RestClient`，方法 `listFiles(baseUrl, token, path, page, perPage)` 调用 `POST /api/fs/list` 并分页、`getFileInfo(baseUrl, token, path)` 调用 `POST /api/fs/get`、`downloadFile(baseUrl, token, filePath)` 返回 `InputStream`、`uploadFile(baseUrl, token, remotePath, inputStream, fileSize)` 调用 `PUT /api/fs/put`（设置 `File-Path` 请求头）、`createDirectory(baseUrl, token, path)` 调用 `POST /api/fs/mkdir`、`deleteFile(baseUrl, token, remotePath)` 调用 `POST /api/fs/remove`；添加中文注释说明每个方法对应的 AList API 端点

#### 同步任务管理（FR-003、FR-004、FR-006）

- [ ] T025 [P] [US1] 在 `src/main/java/top/lldwb/alistmediasync/dto/sync/SyncTaskCreateDTO.java` 中创建同步任务创建 DTO：包含规范定义的所有必要字段和 `@NotBlank`/`@NotNull` 验证注解
- [ ] T026 [P] [US1] 在 `src/main/java/top/lldwb/alistmediasync/dto/sync/SyncTaskUpdateDTO.java` 中创建同步任务更新 DTO：所有字段可选，允许部分更新
- [ ] T027 [P] [US1] 在 `src/main/java/top/lldwb/alistmediasync/dto/sync/SyncTaskVO.java` 中创建同步任务视图 VO：字段 `id`、`name`、`sourceEngineName`、`targetEngineName`、`sourcePath`、`targetPath`、`syncMode`、`transcodeEnabled`、`conflictStrategy`、`excludePatterns`、`scheduleType`、`cronExpression`、`intervalSeconds`、`enabled`、`lastExecutedAt`；添加静态工厂方法
- [ ] T028 [US1] 在 `src/main/java/top/lldwb/alistmediasync/service/SyncTaskManageService.java` 中实现同步任务 CRUD 服务：包含 `create`（校验源/目标引擎存在）、`update`、`delete`、`getById`、`listAll`、`enable`、`disable`；手动触发时检查同一任务是否有"运行中"的 TaskExecution，如有则跳过并抛出业务异常"任务正在执行中，请稍后再试"（FR-004）；所有写操作 `@Transactional`；启用/禁用时通知 ScheduleService 注册/注销定时调度
- [ ] T029 [US1] 在 `src/main/java/top/lldwb/alistmediasync/controller/SyncTaskController.java` 中实现同步任务管理 API：`POST /api/sync-tasks`（创建）、`PUT /api/sync-tasks/{id}`（更新）、`DELETE /api/sync-tasks/{id}`（删除）、`GET /api/sync-tasks`（列表，支持按状态/引擎过滤）、`GET /api/sync-tasks/{id}`（详情）、`POST /api/sync-tasks/{id}/execute`（手动触发）、`POST /api/sync-tasks/{id}/enable`（启用）、`POST /api/sync-tasks/{id}/disable`（禁用）

#### 同步执行引擎（FR-005、FR-007）

- [ ] T030 [US1] 在 `src/main/java/top/lldwb/alistmediasync/dto/sync/SyncProgressVO.java` 中创建同步进度 VO：字段 `taskId`、`status`、`completedFiles`、`totalFiles`、`currentFile`、`syncSpeed`（字节/秒）、`estimatedRemainingSeconds`
- [ ] T031 [US1] 在 `src/main/java/top/lldwb/alistmediasync/service/SyncService.java` 中实现同步执行引擎（核心）：
  - 入口方法 `executeSyncTask(SyncTask task)`：检查目标路径冲突（查询其他"运行中"的 SyncTask 是否向同一目标路径写入，如有则拒绝执行并返回错误），创建 `TaskExecution`（status=RUNNING），异步执行同步流程
  - 同步流程（三步）：(1) **扫描阶段**：递归遍历源 AList 目录（深度优先，`listFiles` 分页循环，提取所有文件到此列表，应用排除规则过滤）；(2) **比对阶段**：获取目标目录文件列表，计算 diff——仅新增模式标记增量文件，全同步模式额外标记待删除文件，移动模式标记需移动的文件；(3) **执行阶段**：对 diff 列表中的每个文件执行操作（下载→上传→校验），记录每个文件的成功/失败状态，实时更新 `TaskExecution` 的进度字段
  - 文件操作：`downloadFile`（Stream 流式下载，不落盘直接上传）、`uploadFile`（流式上传到目标）；对大文件（>100MB）使用临时文件暂存避免内存溢出
  - 返回执行结果（TaskExecution，包含失败详情列表）
- [ ] T032 [US1] 在 `src/main/java/top/lldwb/alistmediasync/service/ScheduleService.java` 中实现调度管理服务：`@PostConstruct` 启动时从数据库加载所有 `enabled=true` 的 SyncTask 并注册调度、`registerSchedule(SyncTask task)`（根据 scheduleType 使用 `TaskScheduler.schedule()` 注册 cron/固定间隔任务）、`unregisterSchedule(Long taskId)`（取消已注册的定时任务）、`recoverInterruptedTasks()`（启动时将 RUNNING 状态的 TaskExecution 标记为 INTERRUPTED）

**检查点**：此时，用户故事 1 应完全功能可用——用户可配置存储引擎、创建同步任务、手动/定时执行同步，查看进度和历史

---

## 阶段 4：用户故事 2 — 同步后自动视频转 MP3（优先级：P2）

**目标**：实现 JAVE2 转码引擎（两阶段处理）、独立转码任务、同步后置转码步骤。此阶段同时集成 Feature 002 的全部临时文件管理能力（可配置后缀、本地暂存、磁盘检查、残留清理）。

**独立测试**：创建同步任务并启用"同步后转 MP3" → 放入测试视频文件 → 执行同步 → 检查目标存储生成 MP3 文件

**注意**：此阶段的大多数任务同时涵盖了 Feature 002（`002-transcode-temp-suffix-config`）的需求。标记为 [002] 的任务可在对应的 `specs/002-transcode-temp-suffix-config/tasks.md` 中找到交叉引用。

### 用户故事 2 的实现 — 转码基础设施（Feature 002 集成）

- [ ] T033 [P] [US2] 在 `src/main/java/top/lldwb/alistmediasync/util/MagicBytesDetector.java` 中创建文件魔数检测工具：读取文件前 16 字节，遍历 `Map<String, byte[]>` 魔数签名表（`FLV`→`0x46 0x4C 0x56`、MP4/M4V→ftyp box 特征），返回检测到的格式枚举；若未匹配任何已知格式返回 UNKNOWN
- [ ] T034 [P] [US2] [002] 在 `src/main/java/top/lldwb/alistmediasync/util/TempFileManager.java` 中创建临时文件管理器：方法 `createTempFile(Path tempDir, String originalName, String suffix)` 生成 `{原文件名}.{ext}.{uuid}.{suffix}` 格式路径并创建空文件、`renameToFinal(Path tempFile, String targetExtension)` 去掉临时后缀重命名为目标扩展名、`deleteQuietly(Path file)` 删除文件（失败记录 WARN 日志）、`setFilePermissions(Path file)` 设置 POSIX 0600 / Windows 降级处理、`setDirectoryPermissions(Path dir)` 设置 POSIX 0700 / Windows 降级处理
- [ ] T035 [P] [US2] [002] 在 `src/main/java/top/lldwb/alistmediasync/util/DiskSpaceChecker.java` 中创建磁盘空间检查工具：方法 `checkSufficient(Path tempDir, long estimatedOutputSize)` 获取 `File.getUsableSpace()`，对比 `estimatedOutputSize * 1.5`，不足时抛出 `InsufficientDiskSpaceException`（含中英文错误消息）
- [ ] T036 [US2] [002] 在 `src/main/java/top/lldwb/alistmediasync/util/TempSuffixValidator.java` 中创建后缀配置校验器：实现 `ApplicationRunner`（`getOrder()` 返回 `HIGHEST_PRECEDENCE`，在校验失败时抛出 `IllegalArgumentException` 阻止 Spring 容器启动）；校验规则按照 FR-004 实施——空/仅点号→回退默认值+WARN、含路径分隔符→拒绝启动、超长→截断+WARN、无点号前缀→自动补充

### 用户故事 2 的实现 — 转码引擎（核心）

- [ ] T037 [US2] 在 `src/main/java/top/lldwb/alistmediasync/config/AsyncConfig.java` 中创建异步配置：`@EnableAsync`，定义 `transcodeExecutor` Bean（`ThreadPoolTaskExecutor`：core=8、max=`${app.pool.max-size}`、queue=64、keepAlive=60s、`CallerRunsPolicy`、线程名前缀 `transcode-`、启用 `setWaitForTasksToCompleteOnShutdown(true)`）；明确不使用虚拟线程执行器（转码是 CPU 密集型任务）；添加中文注释说明设计决策
- [ ] T038 [US2] 在 `src/main/java/top/lldwb/alistmediasync/dto/transcode/TranscodeTaskCreateDTO.java` 中创建转码任务创建 DTO：字段 `sourceFilePath`（`@NotBlank`）、`targetFilePath`（`@NotBlank`）、`targetFormat`（enum：MP3/MP4/FLV）、`bitrate`（默认 128000）、`sourceEngineId`（可选，从 AList 存储读取源文件）、`targetEngineId`（`@NotNull`，上传目标）；添加中文注释
- [ ] T039 [P] [US2] 在 `src/main/java/top/lldwb/alistmediasync/dto/transcode/TranscodeTaskVO.java` 中创建转码任务视图 VO：字段 `id`、`sourceFilePath`、`targetFilePath`、`sourceFormat`、`targetFormat`、`bitrate`、`progress`（0-100 百分比）、`status`、`errorMessage`、`createdAt`
- [ ] T040 [US2] 在 `src/main/java/top/lldwb/alistmediasync/service/TranscodeService.java` 中实现转码引擎（核心）：
  - **阶段 1 — 扫描**：`scanSourceDirectory(String sourceDir, String targetDir, ConflictStrategy strategy)` 递归遍历源目录，对每个文件调用 `MagicBytesDetector` 检测格式（仅处理 FLV/MP4/M4V），检查目标文件是否已存在（已存在+skip策略→跳过），收集符合条件的文件到 `List<TranscodeCandidate>`
  - **阶段 2 — 并行转码**：遍历候选列表，通过 `Semaphore(maxConcurrentTranscode)` 控制并发数，每个文件调用 `self.transcodeFile(candidate)`（`@Async("transcodeExecutor")` + `@Lazy @Autowired` 自注入代理），返回 `CompletableFuture<TranscodeResult>`
  - **单文件转码**：`doTranscode(TranscodeCandidate candidate)` 内部流程：
    1. `DiskSpaceChecker.checkSufficient()` 检查磁盘空间
    2. `TempFileManager.createTempFile()` 创建临时输出文件（带配置后缀）
    3. 构建 `MultimediaObject(source)` + `EncodingAttributes`（`AudioAttributes`：codec=libmp3lame、bitrate=128000、channels=2、samplingRate=44100；仅音频时省略 `VideoAttributes`）
    4. `Encoder().encode(src, tempFile, attrs, progressListener)` 执行转码（`EncoderProgressListener` 持续更新 `TranscodeTask.progress` 到数据库）
    5. 转码成功 → `TempFileManager.renameToFinal()` 去掉临时后缀，最终扩展名替换为目标格式
    6. `AListClient.uploadFile()` 上传到目标 AList 存储
    7. 上传成功 → `TempFileManager.deleteQuietly()` 删除本地文件（FR-009）
    8. 上传失败 → 保留本地文件，`TaskExecution.failureDetails` 记录失败原因（FR-010），标记状态为 FAILED
  - **同步后置转码**：`executePostSyncTranscode(SyncTask task, TaskExecution syncExecution)` 仅在同步成功完成后调用
- [ ] T041 [US2] 在 `src/main/java/top/lldwb/alistmediasync/controller/TranscodeTaskController.java` 中实现转码任务管理 API：`POST /api/transcode-tasks`（创建独立转码任务）、`GET /api/transcode-tasks`（列表）、`GET /api/transcode-tasks/{id}`（详情含进度）、`POST /api/transcode-tasks/{id}/retry-upload`（手动重试上传，FR-010）、`DELETE /api/transcode-tasks/cleanup-temp`（手动清理残留临时文件，FR-013）

### 用户故事 2 的实现 — 同步引擎集成转码

- [ ] T042 [US2] 在 `SyncService.executeSyncTask()` 中集成转码后置步骤：同步完成后检查 `SyncTask.transcodeEnabled`，若为 true 则调用 `TranscodeService.executePostSyncTranscode()`；将转码作为一个独立的 `TaskExecution` 记录到数据库中

**检查点**：此时，用户故事 1 和 2 应能独立工作——同步可正常执行，转码引擎（含 Feature 002 全部临时文件管理）功能完全可用

---

## 阶段 5：用户故事 3 — 接收录播姬 Webhook 并自动处理（优先级：P2）

**目标**：实现 Webhook v2 接收端点（EventId 去重、立即响应 HTTP 200、异步处理）+ Webhook 规则 CRUD + 录制文件自动同步和转码

**独立测试**：配置录播姬 Webhook URL 指向本系统 → 录播姬完成一次录制 → 观察本系统是否接收事件、匹配规则并执行同步/转码

### 用户故事 3 的实现

- [ ] T043 [P] [US3] 在 `src/main/java/top/lldwb/alistmediasync/dto/webhook/WebhookRuleCreateDTO.java` 中创建 Webhook 规则创建 DTO：字段 `name`、`triggerEventType`、`roomIdFilter`（可选）、`action`、`targetEngineId`、`targetPath`
- [ ] T044 [P] [US3] 在 `src/main/java/top/lldwb/alistmediasync/dto/webhook/WebhookRuleVO.java` 中创建 Webhook 规则视图 VO
- [ ] T045 [US3] 在 `src/main/java/top/lldwb/alistmediasync/service/WebhookRuleService.java` 中实现 Webhook 规则 CRUD 服务：包含 `create`、`update`、`delete`、`getById`、`listAll`、`enable`、`disable`；所有写操作 `@Transactional`
- [ ] T046 [US3] 在 `src/main/java/top/lldwb/alistmediasync/service/WebhookService.java` 中实现 Webhook 事件处理服务：
  - `receiveWebhookEvent(String eventType, String eventId, String timestamp, Map<String, Object> eventData)`：
    1. 立即保存 `WebhookEvent`（status=PENDING）到数据库
    2. 通过 `findByEventId(eventId)` 去重——若已存在返回 DUPLICATE 状态（FR-020）
    3. 返回 HTTP 202 给 Controller（由 Controller 转换为 HTTP 200 响应）
  - `processWebhookEvent(WebhookEvent event)`（`@Async` 异步执行）：
    1. 更新 event status 为 PROCESSING
    2. 查询匹配的 `WebhookRule`（按 `triggerEventType` 匹配，`roomIdFilter` 为空匹配所有房间，`enabled=true`）
    3. 对每个匹配的规则执行关联动作：
       - `SYNC_ONLY`：构建临时 SyncTask，调用 `SyncService` 同步到目标引擎
       - `TRANSCODE_ONLY`：构建临时 TranscodeTask，调用 `TranscodeService` 转码后上传
       - `BOTH`：顺序执行同步→转码
    4. 将处理结果记录到 `TaskExecution`
    5. 更新 event status 为 COMPLETED 或 FAILED
  - 仅处理 `FileClosed` 和 `SessionEnded` 事件（A5 假设），其他事件类型仅记录入库（status=COMPLETED，不做后续处理）
- [ ] T047 [US3] 在 `src/main/java/top/lldwb/alistmediasync/controller/WebhookController.java` 中实现录播姬 Webhook 接收端点：
  - `POST /api/webhooks/recorder`：接收 JSON 请求体（Webhook v2 格式）、调用 `WebhookService.receiveWebhookEvent()`、**立即**返回 `ApiResult.success("accepted")`（HTTP 2xx，1 秒内响应）（FR-021）
  - 此端点不要求认证（在 `AuthInterceptor` 中已排除 `/api/webhooks/**`）
  - 添加请求体日志记录（DEBUG 级别）
- [ ] T048 [US3] 在 `src/main/java/top/lldwb/alistmediasync/controller/WebhookRuleController.java` 中实现 Webhook 规则管理 API：`POST /api/webhook-rules`、`PUT /api/webhook-rules/{id}`、`DELETE /api/webhook-rules/{id}`、`GET /api/webhook-rules`、`GET /api/webhook-rules/{id}`

**检查点**：此时，用户故事 1、2、3 应各自独立功能可用——录播姬可发送 Webhook 事件触发自动同步或转码

---

## 阶段 6：用户故事 4 — 实时转码状态监控（优先级：P3）

**目标**：提供转码任务实时进度查询 API，包含当前文件、已完成/总数、预估剩余时间

**独立测试**：触发一个大文件转码任务 → 多次查询进度 API → 验证进度百分比递增、预估时间合理

### 用户故事 4 的实现

- [ ] T049 [US4] 在 `TranscodeService` 中完善进度持久化逻辑：在 `EncoderProgressListener.progress(int permil)` 回调中更新 `TranscodeTask.progress` 字段并 `transcodeTaskRepository.save()`（每 5% 步进一次避免频繁 DB 写入）；记录当前处理的文件名到 `TranscodeTask` 的扩展字段
- [ ] T050 [P] [US4] 在 `GET /api/transcode-tasks/{id}` 端点（T041 已创建）中扩展响应内容：包含 `currentFile`、`completedCount`、`totalCount`、`estimatedRemainingSeconds`（基于已处理文件的平均速率计算）；在 `SyncTaskController` 中添加 `GET /api/sync-tasks/{id}/executions` 查询该同步任务的所有历史执行记录
- [ ] T051 [US4] 在 `src/main/java/top/lldwb/alistmediasync/dto/sync/SyncProgressVO.java`（T030 已创建）中完善字段：添加 `filesPerSecond` 平均传输速度、`estimatedRemainingSeconds` 预估剩余时间

**检查点**：此时所有 4 个用户故事应各自独立功能可用

---

## 阶段 7：通用需求与润色

**目的**：影响多个用户故事的跨领域关注点

### 历史记录与清理

- [ ] T052 [P] 在 `TaskExecutionRepository` 中添加按时间范围查询方法：`findByCreatedAtBetween(LocalDateTime start, LocalDateTime end)`、`findByStatusAndTaskType(Status, Type)`；在 `GET /api/sync-tasks/{id}/executions` 中支持时间范围和状态过滤参数（FR-024）
- [ ] T053 在 `src/main/java/top/lldwb/alistmediasync/service/CleanupService.java` 中实现过期记录清理服务：(1) `@Scheduled(cron="0 0 3 * * ?")`（每天凌晨 3 点执行）`cleanExpiredRecords()` 删除超过 `app.retention-days` 天的 `TaskExecution` 和 `WebhookEvent` 记录（FR-026）；(2) `@EventListener(ApplicationReadyEvent.class)` `startupCleanup()` 扫描并清理临时目录中所有带配置后缀的残留文件（FR-013，002 集成）；(3) `manualCleanup()` 作为公开方法供 TranscodeTaskController 调用（手动清理接口）
- [ ] T054 在 `AListMediaSyncApplication.java` 上添加 `@EnableScheduling` 注解以启用定时任务

### 日志与错误处理

- [ ] T055 在以下 Service 层关键方法中添加结构化日志（FR-025）：`SyncService.executeSyncTask()`（开始/完成/失败+原因）、`TranscodeService.doTranscode()`（开始/完成/失败+源文件+目标文件）、`WebhookService.processWebhookEvent()`（接收/去重/处理完成/失败）、`ScheduleService.registerSchedule()`（注册/注销定时任务）、`CleanupService.startupCleanup()`（清理数量）。所有日志消息使用简体中文
- [ ] T056 在 `src/main/java/top/lldwb/alistmediasync/config/` 中添加全局异常处理类 `GlobalExceptionHandler`：`@RestControllerAdvice`，处理 `IllegalArgumentException`（400）、`InsufficientDiskSpaceException`（507）、`DuplicateEventException`（409）、`TaskAlreadyRunningException`（409）、泛型 `Exception`（500），统一返回 `ApiResult` 格式错误响应

### 启动恢复

- [ ] T057 完善 `ScheduleService.recoverInterruptedTasks()` 的 `@PostConstruct` 逻辑：启动时执行 (1) 重置所有 RUNNING 状态的 `TaskExecution` 为 INTERRUPTED；(2) 遍历所有 `enabled=true` 的 `SyncTask` 重新注册定时调度；(3) 记录恢复日志（被中断的任务数、重新注册的调度数）

### 测试（章程 V 要求）

- [ ] T058 [P] 在 `src/test/java/top/lldwb/alistmediasync/service/StorageEngineServiceTest.java` 中编写 Service 层单元测试：测试 CRUD 操作、Token 加密存储、连接测试模拟（目标覆盖率 > 80%）
- [ ] T059 [P] 在 `src/test/java/top/lldwb/alistmediasync/service/TranscodeServiceTest.java` 中编写转码引擎单元测试：测试磁盘空间检查、临时文件创建/重命名/删除、并发上限 Semaphore 控制、JAVE2 转换（使用小测试视频文件）（目标覆盖率 > 80%）
- [ ] T060 [P] 在 `src/test/java/top/lldwb/alistmediasync/service/WebhookServiceTest.java` 中编写 Webhook 服务单元测试：测试 EventId 去重逻辑、规则匹配（含房间号过滤）、异步处理流程（目标覆盖率 > 80%）
- [ ] T061 [P] 在 `src/test/java/top/lldwb/alistmediasync/repository/` 中编写 Repository 层集成测试：至少覆盖 `SyncTaskRepository.findByEnabledTrue()`、`WebhookEventRepository.findByEventId()`、`TaskExecutionRepository` 状态更新查询（目标覆盖率 > 60%）
- [ ] T062 [P] 在 `src/test/java/top/lldwb/alistmediasync/controller/` 中编写 Controller 层契约测试：至少覆盖 `StorageEngineController.testConnection()`、`WebhookController.receiveWebhook()`（验证 1 秒内 HTTP 200 响应）、`TranscodeTaskController.retryUpload()`（目标覆盖率 > 70%）

### 文档

- [ ] T063 [P] 在 `src/main/resources/application.yaml` 中为所有新增配置项添加中文注释，说明每个属性的用途、默认值、合法值范围、Docker 环境变量的对应关系
- [ ] T064 编写 `specs/001-alist-media-sync/quickstart.md`（阶段 1 产物）：包含应用启动步骤、存储引擎配置示例、同步任务创建 walkthrough、Webhook 配置指南

---

## 依赖与执行顺序

### 阶段依赖

- **设置（阶段 1）**：无依赖 — 可立即开始
- **基础（阶段 2）**：依赖设置完成 — 阻塞所有用户故事
- **用户故事 1（阶段 3）**：依赖基础完成。US1 是系统的核心，US2/US3 的技术基础（同步引擎、AListClient）由 US1 提供
- **用户故事 2（阶段 4）**：依赖基础完成 + US1 的 AListClient、StorageEngineService。US2 的转码引擎独立于 US1 的同步管理——可并行开始但需等待 US1 的 AListClient 就绪
- **用户故事 3（阶段 5）**：依赖基础完成 + US1 的 AListClient、StorageEngineService + US2 的 TranscodeService。可与 US2 并行但建议在 US2 后执行（需要转码能力）
- **用户故事 4（阶段 6）**：依赖 US1（同步进度）和 US2（转码进度）。轻量级——可在 US2 完成后快速交付
- **润色（阶段 7）**：依赖所有 4 个用户故事完成

### 用户故事依赖关系

```
基础（阶段 2）
    ↓
用户故事 1 — 多存储间文件同步（P1）🎯 MVP
    ├──→ 用户故事 2 — 同步后自动视频转 MP3（P2）
    │       └──→ 用户故事 3 — 录播姬 Webhook（P2）
    └──→ 用户故事 4 — 实时转码状态监控（P3）
```

- **US1**：独立，不依赖其他故事
- **US2**：依赖 US1 的 `AListClient`、`StorageEngineService`、`SyncService`
- **US3**：依赖 US1 的 `AListClient` + US2 的 `TranscodeService`
- **US4**：轻度依赖 US1 + US2（复用进度查询端点）

### 每个用户故事内部

- DTO 先于 Service
- Service 先于 Controller
- AListClient 先于 SyncService（US1）
- SyncService 先于 TranscodeService.postSyncTranscode（US2）
- TranscodeService 先于 WebhookService（US3）
- 实体创建在阶段 2 集中完成（全部 6 个实体可并行）

### 并行机会

**阶段 2 大量并行**：
- T004-T009（6 个实体）可完全并行
- T010-T015（6 个 Repository）可完全并行
- T016、T017、T018 在实体和 Repository 完成后可并行

**阶段 3-US1 内部并行**：
- T019-T021（3 个 DTO）可并行
- T025-T027（3 个 DTO）可并行
- T023 和 T029（2 个 Controller）在各自 Service 完成后可并行
- T030（SyncProgressVO）可与其他 DTO 并行

**阶段 4-US2 内部并行**：
- T033-T036（4 个工具类）可完全并行
- T038-T039（2 个 DTO）可并行

**阶段 5-US3 内部并行**：
- T043-T044（2 个 DTO）可并行

**阶段 7 润色内部并行**：
- T052、T053、T055、T056 相互独立可并行
- T058-T062（5 个测试类）可完全并行
- T063、T064 可并行

### 与 Feature 002 的并行策略

Feature 001 和 002 在**同一迭代周期中实现**（见 plan.md 设计决策 5）。002 的任务集成在 001 的以下阶段中：

| 002 任务 | 对应 001 阶段 | 说明 |
|---------|-------------|------|
| 002 T002（TempFileManager） | 001 阶段 4 T034 | 临时文件创建/重命名/删除/权限 |
| 002 T003（DiskSpaceChecker） | 001 阶段 4 T035 | 磁盘空间预估检查 |
| 002 T004（TempSuffixValidator） | 001 阶段 4 T036 | 启动时后缀配置校验 |
| 002 T005（CleanupService 增强） | 001 阶段 7 T053 | 启动清理 + 手动清理接口 |
| 002 T006（并发上限 Semaphore） | 001 阶段 4 T040 | TranscodeService 信号量控制 |

**建议**：优先完成 001 的阶段 1-3（MVP），然后 001 和 002 的阶段 4 任务混合执行（因为它们操作相同的文件如 `TranscodeService.java`、`AppProperties.java`）。

---

## 实现策略

### MVP 优先（仅用户故事 1）

1. 完成阶段 1：设置（JAVE2 依赖 + 配置）
2. 完成阶段 2：基础（6 实体 + 6 Repository + 认证 + 配置）
3. 完成阶段 3：用户故事 1（存储引擎管理 + 同步引擎 + 调度）
4. **停止并验证**：独立测试同步功能——创建存储引擎、创建同步任务、手动/定时执行
5. 如果就绪则部署/演示

### 增量交付

1. 完成设置 + 基础 → 基础设施就绪
2. 添加用户故事 1 → 独立测试 → 同步功能可用（MVP！）
3. 添加用户故事 2（含 Feature 002 临时文件管理）→ 转码功能可用
4. 添加用户故事 3 → Webhook 集成可用
5. 添加用户故事 4 → 实时进度监控可用
6. 完成润色 → 测试覆盖达标 + 历史清理 + 文档完备

### 并行团队策略

多个开发人员时：

1. 团队一起完成阶段 1 设置（2 个任务）
2. 团队一起完成阶段 2 基础设施（15 个任务，大量可并行）
3. 基础完成后按依赖关系分工：
   - **开发人员 A**：用户故事 1（核心同步，最大工作量）
   - **开发人员 B**：用户故事 2 的工具类准备（T033-T036），然后等待 US1 的 AListClient 就绪
   - **开发人员 C**：用户故事 3 的 DTO + WebhookRuleService 准备，等待 US2 的 TranscodeService
4. US1 完成后，B 接手 TranscodeService 核心实现（T040），C 接手 WebhookService 实现（T046）
5. 各故事独立完成后统一测试和润色

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [002] 标记 = 此任务同时是 Feature 002（`specs/002-transcode-temp-suffix-config`）的实现任务
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应能独立完成和测试
- 001 和 002 在同一迭代周期中实现（并行代码内聚到相同的 Service 文件）
- 每个任务或逻辑组后提交
- 在任何检查点停止以独立验证故事
- 所有注释、日志消息、提交信息使用简体中文（章程 IV）
- 避免：模糊的任务、同文件冲突、破坏独立性的跨故事依赖
