# common/ — 通用模块

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和后端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

提供跨模块共享的基础设施，包括配置管理、认证拦截、加密工具、异常处理、DTO/VO 定义和工具类。

## 作用

- **配置层**：`AppProperties` 绑定 `app.*` 配置命名空间；`AsyncConfig` 管理转码线程池；`WebMvcConfig` 注册拦截器和 SPA 静态资源映射
- **安全层**：`AuthInterceptor` 实现 HTTP Basic 认证（排除 `/api/webhooks/**` 和 `/actuator/health`）；`CryptoConverter` 使用 AES-256-GCM 加密数据库字段；`PasswordEncryptionPostProcessor` 启动时 BCrypt 加密密码
- **DTO/VO**：`ApiResult<T>` 统一响应体；`DashboardStatsVO` 仪表板统计
- **工具类**：`DiskSpaceChecker` 转码前磁盘检查；`MagicBytesDetector` 文件魔数检测；`TempFileManager` 临时文件管理；`TempSuffixValidator` 临时后缀校验

## 模块关联

- 被 **所有业务模块**（storage/sync/transcode/webhook）依赖
- `AuthInterceptor` 保护除 webhook 和 health 外的所有 API
- `CryptoConverter` 被 `storage/entity/StorageEngine` 的 Token 字段使用
- `TempFileManager` 和 `DiskSpaceChecker` 被 `transcode/` 模块使用
