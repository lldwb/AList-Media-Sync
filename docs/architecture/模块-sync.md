# sync 模块 — 同步任务

> 本文档为 US4 架构设计细化文档之一，描述 sync 模块的职责边界、核心类、关键流程与扩展点。总览见 [03-架构设计.md](../03-架构设计.md)。

## 职责边界

sync 模块实现文件同步引擎，核心职责包括：

- **三种同步模式**：NEW_ONLY（仅新增）、FULL（全量覆盖）、MOVE（文件移动）
- **两种调度方式**：CRON（Cron 表达式）、INTERVAL（固定间隔）
- **任务管理**：同步任务的 CRUD、手动触发、进度查询
- **定时调度**：`@PostConstruct` 恢复持久化的调度任务，运行时动态增删
- **实时进度**：通过 WebSocket 推送同步进度，无需 HTTP 轮询

sync 模块通过 `StorageEngineStrategy` 接口操作源和目标存储，同引擎复制走 `copyFile` 实现服务端复制（避免下载-上传往返）。遵循章程原则 I（分层架构）和 II（数据完整性）。

## 核心类

### SyncService — 核心同步引擎

三阶段执行模型：

1. **扫描阶段**：`scanDirectory()` 递归遍历源目录，应用 Glob 排除规则，收集待同步文件列表
2. **比对阶段**：构建目标文件集合，按 `SyncMode` 计算差异集
   - NEW_ONLY：仅同步目标不存在的文件
   - FULL：全量覆盖，目标已存在的文件也重新同步
   - MOVE：源文件移动到目标路径（同引擎走 `copyFile` + 源端删除）
3. **执行阶段**：流式下载→上传
   - 大文件（>100MB）：临时文件中转，防止内存溢出
   - 小文件（≤100MB）：直接流式传输，减少磁盘 I/O

### SyncTaskManageService — 任务管理

同步任务 CRUD 管理逻辑：
- 创建任务时持久化实体和调度配置
- 更新任务时同步更新调度
- 删除任务时取消调度
- 查询任务列表和详情

### ScheduleService — 调度管理

- `@PostConstruct` 启动时恢复所有 `enabled=true` 任务的调度
- CRON 模式：通过 Spring `TaskScheduler` 注册 Cron 触发器
- INTERVAL 模式：注册固定速率触发器
- 运行时动态增删调度，与任务 CRUD 联动

### SyncTaskController — 同步任务 API

RESTful 端点：
- `GET /api/sync/tasks` — 任务列表
- `POST /api/sync/tasks` — 创建任务
- `PUT /api/sync/tasks/{id}` — 更新任务
- `DELETE /api/sync/tasks/{id}` — 删除任务
- `POST /api/sync/tasks/{id}/trigger` — 手动触发
- `GET /api/sync/tasks/{id}/progress` — 进度查询

### 实体与 DTO

| 类 | 职责 |
|---|------|
| `SyncTask` | 同步任务实体，含 `@Version` 乐观锁。字段：SyncMode / ScheduleType / ConflictStrategy / 排除规则 |
| `TaskExecution` | 任务执行记录实体。TaskType：SYNC / TRANSCODE / WEBHOOK；ExecutionStatus：PENDING / RUNNING / COMPLETED / FAILED / INTERRUPTED |
| `SyncTaskCreateDTO` / `UpdateDTO` | 创建/更新请求 DTO |
| `SyncTaskVO` | 任务视图 VO |
| `SyncProgressVO` | 同步进度视图（总文件数 / 已完成 / 成功 / 失败 / 当前文件） |
| `TaskExecutionVO` | 执行记录视图 |
| `FileEntry` | 文件条目 record（name / path / isDirectory / size / modifiedTime） |
| `DirectoryEntryVO` | 目录条目 VO（name / path / hasChildren） |

### Repository

| 类 | 职责 |
|---|------|
| `SyncTaskRepository` | Spring Data JPA 接口，含 `findByEnabledTrue`、`findBySyncMode` 等派生查询 |
| `TaskExecutionRepository` | 含 `markAllRunningAsInterrupted()` 批量更新方法，启动时将上次未完成的任务标记为 INTERRUPTED |

## 关键流程

### 任务调度流程

1. `ScheduleService` 在 `@PostConstruct` 阶段查询所有 `enabled=true` 的同步任务
2. 按 `scheduleType` 注册触发器（CRON 或 INTERVAL）
3. 到达触发时间时，调用 `SyncService` 执行同步
4. 创建 `TaskExecution` 记录，状态为 RUNNING
5. 同步完成后更新 `TaskExecution` 状态为 COMPLETED 或 FAILED
6. 通过 `WsSessionManager` 广播进度更新

### 文件扫描流程

1. 通过 `StorageEngineStrategy.listFiles()` 递归遍历源目录
2. 应用 Glob 排除规则（如 `*.tmp`、`*.part`）
3. 收集文件列表（含路径、大小、修改时间）
4. 返回 `List<FileEntry>`

### 同步执行流程

1. **扫描**：`scanDirectory()` 获取源目录所有待同步文件
2. **比对**：获取目标目录已有文件集合，按 `SyncMode` 计算差异
3. **执行**：逐个文件执行同步
   - 同引擎：调用 `copyFile()` 服务端复制
   - 跨引擎：下载到本地（大文件走临时文件）→ 上传到目标
   - MOVE 模式：复制完成后删除源文件
4. **进度推送**：每完成一个文件，通过 `WsSessionManager` 广播 `SyncProgressVO`
5. **异常处理**：单个文件失败不中断整体同步，记录失败原因，继续下一个

### 进度推送流程

1. `SyncService` 维护 `activeExecutions` ConcurrentHashMap，实时追踪正在执行的任务
2. 每完成一个文件，更新进度数据并调用 `WsSessionManager.broadcast()` 推送 `WsMessage(type=SYNC_PROGRESS, payload=SyncProgressVO)`
3. 前端通过 WebSocket `/ws/events` 实时接收进度，无需轮询
4. 任务完成后从 `activeExecutions` 移除

## 扩展点

- **新增同步模式**：在 `SyncMode` 枚举中添加值，在 `SyncService` 比对阶段实现对应逻辑
- **新增调度方式**：在 `ScheduleType` 枚举中添加值，在 `ScheduleService` 中实现对应触发器注册逻辑
- **新增冲突策略**：在 `ConflictStrategy` 枚举中添加值，在执行阶段实现对应处理逻辑

## 关联 spec

- `specs/001-alist-media-sync/` — 核心同步业务
- `specs/006-storage-engine-refactor/` — 存储引擎重构（策略接口对接）
- `.specify/memory/constitution.md` §I（分层架构）、§II（数据完整性 — `@Version` / `@Transactional` / 中间状态持久化）、§VII（日志规范 — traceId / MDC / 进度推送日志）
