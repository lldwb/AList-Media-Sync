# transcode/ — 转码模块

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和后端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

实现媒体文件转码引擎，采用**下载→转码→上传**三步流程和 **8 状态模型**。使用 FFmpeg（JAVE2）进行转码，Semaphore 控制并发数。

## 作用

- **TranscodeService**：转码编排层，三步流程 + 状态机 + 并行处理 + 重试机制
- **TranscodeFileProcessor**：单文件处理器，下载→FFmpeg→上传，Semaphore 并发控制
- **TranscodeTaskController**：转码任务 CRUD + 手动触发 + 重试 API
- **8 状态模型**：PENDING → DOWNLOADING → TRANSCODING → UPLOADING → COMPLETED，每步可独立失败和重试
- **sourceDirectoryTranscode**：源目录转码选项，输出至源文件所在目录

## 模块关联

- 依赖 **storage/** 模块：下载源文件和上传转码产物
- 被 **sync/** 模块调用：同步任务可配置自动转码
- 被 **webhook/** 模块调用：Webhook 规则匹配后触发转码
- 依赖 **common/** 模块：`TempFileManager`、`DiskSpaceChecker`、`MagicBytesDetector`、`WsSessionManager`（状态推送）
