# transcode/ — 转码模块

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和后端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

实现媒体文件转码引擎，采用**下载→转码→上传**三步流程和 **8 状态模型**。使用 FFmpeg（JAVE2）进行转码，Semaphore 控制并发数。

## 作用

- **TranscodeService**：转码编排层（门面），三步流程 + 并行处理 + 重试机制，对外方法签名不变；同时实现 `sync` 模块的 `PostSyncTranscodeTrigger` 接口
- **TranscodeScanner**：源路径扫描器（自 `TranscodeService` 按职责切出），判断源路径是目录还是单文件、递归扫描、魔数过滤、按冲突策略跳过目标已存在文件；只扫描、不改状态
- **TranscodeStateMachine**：8 状态模型的状态转换规则与失败/可重试判定（静态无状态），供编排层与单文件处理器复用
- **TranscodeTaskStateWriter**：任务状态持久化 + 进度推送（自 `TranscodeFileProcessor` 按职责切出），负责实体重载、保存、FFmpeg 进度落库与 WebSocket 广播
- **TranscodeFileProcessor**：单文件处理器，下载→FFmpeg→上传，Semaphore 并发控制
- **TranscodeTaskController**：转码任务 CRUD + 手动触发 + 重试 API
- **8 状态模型**：PENDING → DOWNLOADING → TRANSCODING → UPLOADING → COMPLETED，每步可独立失败和重试
- **sourceDirectoryTranscode**：源目录转码选项，输出至源文件所在目录

## 模块关联

- 依赖 **storage/** 模块：下载源文件和上传转码产物
- 依赖 **execution/** 模块：`TaskExecution` 实体与 `TaskExecutionRepository` 记录转码执行
- **实现** `sync/service/PostSyncTranscodeTrigger` 接口：这是依赖倒置的**实现边**——本模块为实现该接口需 import `sync` 的接口与 `SyncTask`，而 `sync` **不 import 本模块**，从而切断 sync ↔ transcode 循环
- 被 **webhook/** 模块调用：规则匹配后经 `TranscodeService.createTask` + `executeAsync` 触发转码
- 依赖 **common/** 模块：`TempFileManager`、`DiskSpaceChecker`、`MagicBytesDetector`、`WsSessionManager`（状态推送）
- 两个入口层（`TranscodeTaskController`、`TranscodeTaskMcpTools`）注入 **common** 的 `TempFileCleanupTrigger` 接口实现「手动清理临时文件」；实现方在 `ops/CleanupService`，本模块**不 import `ops`**
- `TranscodeTask.webhookRuleId`：Webhook 规则关联字段已由实体引用降级为 `Long` + `@Column(name = "webhook_rule_id")`（数据库列名不变），当前**零读写**，Webhook 触发的转码任务不再回填该字段
