# sync/ — 同步任务模块

## 功能

实现文件同步引擎，支持三种同步模式（NEW_ONLY/FULL/MOVE）和两种调度方式（CRON/INTERVAL）。提供同步任务的 CRUD 管理、手动触发、进度查询和定时调度。

## 作用

- **SyncService**：核心同步引擎，三阶段执行（扫描→比对→执行），大文件（>100MB）临时文件中转，小文件流式传输
- **SyncTaskManageService**：同步任务 CRUD 管理
- **ScheduleService**：定时调度管理，`@PostConstruct` 恢复持久化的调度任务
- **SyncTaskController**：同步任务 CRUD + 手动触发 + 进度查询 API
- **进度查询**：通过 `activeExecutions` ConcurrentHashMap 实时追踪正在执行的任务

## 模块关联

- 依赖 **storage/** 模块：通过 `StorageEngineStrategy` 接口操作源和目标存储
- 可调用 **transcode/** 模块：同步任务可配置 `transcodeEnabled` 自动触发转码
- 依赖 **common/** 模块：DTO/VO、工具类
- 被 **webhook/** 模块调用：Webhook 规则匹配后触发同步任务
- **已知问题**：进度查询当前使用 HTTP 轮询（`/api/sync-tasks`），应考虑改为 WebSocket 或 SSE 推送以降低请求频率
- **待优化**：同引擎同步应调用存储层的 copy 方法（服务端复制），而非下载→上传
