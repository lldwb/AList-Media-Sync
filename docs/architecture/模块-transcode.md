# transcode 模块 — 媒体转码

> 本文档为 US4 架构设计细化文档之一，描述 transcode 模块的职责边界、核心类、关键流程与扩展点。总览见 [03-架构设计.md](../03-架构设计.md)。

## 职责边界

transcode 模块实现媒体文件转码引擎，核心职责包括：

- **三步流程**：下载源文件 → FFmpeg 转码 → 上传转码产物
- **8 状态模型**：每步可独立失败和重试，中间状态持久化
- **并发控制**：通过 `Semaphore` 限制并发转码数，防止资源耗尽
- **任务管理**：转码任务的 CRUD、手动触发、失败重试
- **状态推送**：通过 WebSocket 实时推送转码状态变更

transcode 模块通过 `StorageEngineStrategy` 接口操作存储后端（下载源文件、上传转码产物），依赖 common 模块的 `TempFileManager`、`DiskSpaceChecker`、`MagicBytesDetector` 等工具。遵循章程原则 I（分层架构）和 II（数据完整性）。

## 核心类

### TranscodeService — 转码编排层

- **任务创建**：`createTask()` 创建转码任务实体，支持 `sourceDirectoryTranscode` 选项（输出至源文件所在目录）
- **三步流程编排**：下载 → 转码 → 上传，每步前后更新状态
- **并行处理**：批量转码时通过 `TranscodeFileProcessor` 并行处理多个文件
- **重试机制**：`retry()` 方法从失败步骤继续，保留已完成步骤的临时文件
- **状态推送**：每次状态变更通过 `WsSessionManager` 广播

### TranscodeFileProcessor — 单文件处理器

负责单个文件的完整转码流程：

1. **获取信号量**：`Semaphore.acquire()`，控制并发数
2. **磁盘检查**：`DiskSpaceChecker` 预估 1.5 倍安全阈值
3. **下载**：通过策略接口下载源文件到临时目录（`TempFileManager` 管理）
4. **魔数检测**：`MagicBytesDetector` 验证文件格式
5. **转码**：调用 JAVE2（FFmpeg）进行转码
6. **上传**：通过策略接口上传转码产物
7. **清理**：删除临时文件
8. **释放信号量**：`Semaphore.release()`

### TranscodeTaskController — 转码任务 API

RESTful 端点：
- `GET /api/transcode/tasks` — 任务列表
- `POST /api/transcode/tasks` — 创建任务
- `GET /api/transcode/tasks/{id}` — 任务详情
- `POST /api/transcode/tasks/{id}/trigger` — 手动触发
- `POST /api/transcode/tasks/{id}/retry` — 重试失败任务

### TranscodeTask 实体

JPA 实体，含 `@Version` 乐观锁。关键字段：
- `TranscodeStatus`：8 状态枚举
- `TargetFormat`：目标格式枚举（MP3 / MP4 / FLV）
- `sourceDirectoryTranscode`：是否输出至源目录
- `currentStep`：当前执行步骤（用于重试时定位）

### DTO

| 类 | 职责 |
|---|------|
| `TranscodeTaskCreateDTO` | 创建请求 DTO（含 sourceDirectoryTranscode 字段） |
| `TranscodeTaskVO` | 任务视图 VO |
| `TranscodeCandidate` | 转码候选文件 record |
| `TranscodeResult` | 转码结果 record |

## 8 状态模型（含编排级失败 FAILED）

```
PENDING(0) → DOWNLOADING(1) → TRANSCODING(3) → UPLOADING(5) → COMPLETED(7)
               ↓(失败)            ↓(失败)          ↓(失败)
          DOWNLOAD_FAILED(2)  TRANSCODE_FAILED(4)  UPLOAD_FAILED(6)
               ↓(重试)            ↓(重试)          ↓(重试)
          DOWNLOADING(1)      TRANSCODING(3)      UPLOADING(5)
```

- **PENDING**：任务已创建，等待执行
- **DOWNLOADING / DOWNLOAD_FAILED**：下载中 / 下载失败
- **TRANSCODING / TRANSCODE_FAILED**：转码中 / 转码失败
- **UPLOADING / UPLOAD_FAILED**：上传中 / 上传失败
- **COMPLETED**：全部完成
- **FAILED**：编排级失败（扫描/收集阶段异常或全部文件失败），非单步失败；可重试

设计意图：每个步骤独立可重试，重试时从失败步骤继续执行，已完成步骤的临时文件保留复用，避免重复下载和转码。

## 关键流程

### 任务创建流程

1. 管理员通过 API 创建转码任务（指定源引擎、目标引擎、目标格式等）
2. `TranscodeService.createTask()` 持久化任务实体，状态为 PENDING
3. 手动触发或由 sync/webhook 模块触发执行
4. 创建 `TaskExecution` 记录

### 下载流程

1. 状态变更为 DOWNLOADING
2. `TempFileManager` 创建临时文件（UUID 命名，并发安全）
3. 通过策略接口 `downloadFile()` 下载源文件（网络/IO 瞬时故障包装为 `RetryableIOException`，触发自动重试）
4. `MagicBytesDetector` 验证文件格式
5. 成功 → 状态变更为 TRANSCODING；失败 → 状态变更为 DOWNLOAD_FAILED

### 转码流程

1. 状态变更为 TRANSCODING
2. `DiskSpaceChecker` 检查磁盘空间（预估源文件大小 1.5 倍）
3. JAVE2 调用 FFmpeg 进行转码（根据 `TargetFormat` 设置编码参数）
4. 转码产物写入临时文件（带 `TempSuffixValidator` 校验的临时后缀）
5. 成功 → 状态变更为 UPLOADING；失败 → 状态变更为 TRANSCODE_FAILED

### 上传流程

1. 状态变更为 UPLOADING
2. 通过策略接口 `uploadFile()` 上传转码产物（上传失败同样包装为可重试异常）
3. 如启用 `sourceDirectoryTranscode`，输出至源文件所在目录
4. 清理临时文件
5. 成功 → 状态变更为 COMPLETED；失败 → 状态变更为 UPLOAD_FAILED

### 重试流程

**手动重试（API/MCP `retry`）**：

1. `retry()` 读取任务当前状态，确定从哪步继续
2. DOWNLOAD_FAILED → 状态回退 DOWNLOADING（清理部分下载文件）
3. TRANSCODE_FAILED → 状态回退 TRANSCODING（保留已下载的临时文件）
4. UPLOAD_FAILED → 状态回退 UPLOADING（保留已转码的临时文件）
5. FAILED（编排级）→ 清空错误信息后整体重新执行
6. 状态回退提交后，经自代理调用 `executeTask()` 真正触发执行（此前缺陷：仅改状态不执行，任务卡死）

**自动重试（瞬时故障）**：

1. 下载/上传失败抛 `RetryableIOException`（实现 `RetryableException` 标记），`RetryService.isRetryable` 判定为可重试
2. 未达上限（`app.retry.max-auto-retries`，默认 3）时按指数退避调度重试
3. 重试**复用同一任务记录**并沿用 `retryCount` 递增（此前缺陷：重试新建任务导致计数归零、无限重试刷库）
4. 断点续传：下载失败重试从下载开始；转码失败保留已下载源文件跳过下载；上传失败复用已转码产物直接上传
5. 达上限后标记最终失败（错误信息追加"自动重试用尽"）
6. 成功后重置 `retryCount`

## 扩展点

### 并发控制

- `Semaphore` 并发数由 `app.transcode.max-concurrent-transcode` 配置控制（默认 32）
- 线程池通过 `AsyncConfig` 配置（核 8 / 最大 32 / 队 64，`CallerRunsPolicy`）
- 当并发达到上限时，新任务等待信号量释放
- 如需调整并发能力，修改配置即可，无需改代码

### 新增目标格式

1. 在 `TargetFormat` 枚举中添加值
2. 在转码逻辑中添加对应的 FFmpeg 编码参数配置
3. 如需新增转码引擎（替代 JAVE2），实现新的处理器

## 关联 spec

- `specs/001-alist-media-sync/` — 核心转码业务
- `specs/002-transcode-temp-suffix-config/` — 转码增强（临时后缀、磁盘检查）
- `.specify/memory/constitution.md` §I（分层架构）、§II（数据完整性 — 中间状态持久化）、§VI（YAGNI — JAVE2 封装 FFmpeg 而非直接调用）、§VII（日志规范 — 转码步骤日志 / traceId）
