# storage/ — 存储引擎模块

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和后端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

通过**策略模式**抽象文件存储后端，提供统一的文件操作接口。支持 AList 远程存储和本地文件系统两种后端，可扩展新后端无需修改现有代码。

## 作用

- **策略接口**：`StorageEngineStrategy` 定义 `listFiles/getFileInfo/downloadFile/uploadFile/createDirectory/deleteFile/listDirectories/testConnection/copyFile` 统一方法（`copyFile` 默认实现抛 `UnsupportedOperationException`，由具体策略覆盖）
- **AList 实现**：通过 Spring `RestClient` 调用 AList REST API（`/api/fs/list`, `/api/fs/get`, `/api/fs/put`, `/api/fs/mkdir`, `/api/fs/remove`, `/api/fs/copy`），分页获取，MultipartFormData 上传，同引擎复制走服务端 copy
- **本地实现**：`java.nio.file` 操作本地文件系统，8KB 缓冲区流式写入，目录递归删除，同盘复制使用 `Files.copy`
- **策略分发**：`StorageEngineService` 注入 `List<StorageEngineStrategy>`，按 `engineType` 选择策略
- **引擎管理**：`StorageEngineController` 提供引擎 CRUD + 连接测试 API

## 模块关联

- 被 **sync/** 模块使用：同步任务通过策略接口操作源和目标存储，同引擎走 `copyFile` 实现服务端复制
- 被 **transcode/** 模块使用：转码流程中的下载和上传步骤
- 被 **webhook/** 模块使用：Webhook 触发后的文件操作
- 依赖 **common/** 模块：`CryptoConverter` 加密 Token，`ApiResult` 封装响应
