# common/ — 通用模块

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和后端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

提供跨模块共享的基础设施，包括配置管理、认证拦截、加密工具、异常处理、DTO/VO 定义和工具类。

## 作用

- **配置层**：`AppProperties` 绑定 `app.*` 配置命名空间；`AsyncConfig` 管理转码线程池；`WebMvcConfig` 注册拦截器和 SPA 静态资源映射
- **安全层**：`AuthInterceptor` 实现 HTTP Basic 认证（排除 `/api/webhooks/recorder` 和 `/actuator/health`），凭据校验逻辑抽取到 `BasicAuthVerifier` 供其与 `WebSocketAuthInterceptor` 共用；`CryptoConverter` 使用 AES-256-GCM 加密数据库字段；`PasswordEncryptionPostProcessor` 启动时 BCrypt 加密密码
- **DTO/VO**：`ApiResult<T>` 统一响应体；`DashboardStatsVO` 仪表板统计
- **工具类**：`DiskSpaceChecker` 转码前磁盘检查；`MagicBytesDetector` 文件魔数检测；`TempFileManager` 临时文件管理；`TempSuffixValidator` 临时后缀校验；`PathUtils` 跨模块路径拼接与拆分工具；`JsonUtils` 对象转 JSON 统一入口（Jackson 3）；`MapUtils` 原始参数 Map 取值；`TraceContext` traceId/MDC 上下文管理（含 `runWith` 便捷入口）
- **依赖倒置接口**：`TempFileCleanupTrigger` 定义「手动清理残留转码临时文件」契约，由 `ops/CleanupService` 实现、`transcode/` 的两个入口注入调用，使 transcode 无需依赖顶层的 ops 模块

## 模块关联

- 被 **所有业务与聚合模块**（storage / sync / transcode / webhook / ops）依赖；`execution/` 当前只含实体 / Repository / VO，尚未 import 本模块（允许依赖）
- 本身**不依赖任何业务模块**，仅提供技术基础设施与跨模块共享抽象
- `AuthInterceptor` 保护除 webhook 回调和 health 外的所有 API
- `CryptoConverter` 被 `storage/entity/StorageEngine` 的 Token 字段使用
- `TempFileManager`、`DiskSpaceChecker`、`MagicBytesDetector` 被 `transcode/` 模块使用
- `TempFileCleanupTrigger` 被 `transcode/controller/TranscodeTaskController` 与 `transcode/mcp/TranscodeTaskMcpTools` 注入（实现方在 `ops/`）
- 诊断与仪表盘能力（原 `DiagnosticService` / `DashboardService` / `CleanupService` 及配套入口层）已迁至顶层 `ops/` 模块
