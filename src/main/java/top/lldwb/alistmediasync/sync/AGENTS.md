# sync/ — 同步任务模块

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和后端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

实现文件同步引擎，支持三种同步模式（NEW_ONLY/FULL/MOVE）和两种调度方式（CRON/INTERVAL）。提供同步任务的 CRUD 管理、手动触发、进度查询和定时调度。

## 作用

- **SyncService**：核心同步引擎，三阶段执行（扫描→比对→执行），大文件（>100MB）临时文件中转，小文件流式传输
- **SyncTaskManageService**：同步任务 CRUD 管理
- **ScheduleService**：定时调度管理，`@PostConstruct` 恢复持久化的调度任务
- **SyncTaskController**：同步任务 CRUD + 手动触发 + 进度查询 API
- **进度查询**：通过 `activeExecutions` ConcurrentHashMap 实时追踪正在执行的任务

## 模块关联

- 依赖 **storage/** 模块：通过 `StorageEngineStrategy` 接口操作源和目标存储；同引擎复制走 `copyFile`（服务端复制）
- 可调用 **transcode/** 模块：同步任务可配置 `transcodeEnabled` 自动触发转码
- 依赖 **common/** 模块：DTO/VO、工具类
- 被 **webhook/** 模块调用：Webhook 规则匹配后触发同步任务
- 前端进度通过 **common/service/WsSessionManager** 经 WebSocket（`/ws/events`）实时推送，无需 HTTP 轮询
