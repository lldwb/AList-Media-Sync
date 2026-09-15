# storage 模块 — 存储引擎

> 本文档为 US4 架构设计细化文档之一，描述 storage 模块的职责边界、核心类、关键流程与扩展点。总览见 [03-架构设计.md](../03-架构设计.md)。

## 职责边界

storage 模块通过**策略模式**抽象文件存储后端，提供统一的文件操作接口。当前支持两种后端：

- **AList 远程存储**：通过 HTTP REST API 操作 AList 服务器上的文件
- **本地文件系统**：通过 `java.nio.file` 操作本地磁盘文件

storage 模块是文件操作的核心抽象层，sync / transcode / webhook 模块均通过策略接口操作文件，不直接耦合具体存储后端。遵循章程原则 VI（YAGNI），策略接口仅在存在第二个实现时才引入抽象——目前已满足此条件。

## 核心类

### StorageEngineStrategy — 策略接口

定义统一的文件操作契约：

```
listFiles(path)              → List<FileEntry>
listDirectories(path)        → List<DirectoryEntryVO>
getFileInfo(path)            → FileEntry
downloadFile(path, output)   → void
uploadFile(localFile, path)  → void
createDirectory(path)        → void
deleteFile(path)             → void
copyFile(src, dst)           → void  （默认抛 UnsupportedOperationException）
testConnection()             → boolean
type()                       → String  （策略标识，如 "ALIST" / "LOCAL"）
```

`copyFile` 提供默认实现抛 `UnsupportedOperationException`，由支持服务端复制的具体策略覆盖。

### DTO — 策略接口的返回类型

| 类 | 职责 |
|---|------|
| `FileEntry` | 文件条目 record（name / path / isDirectory / size / modifiedTime），`listFiles` 与 `getFileInfo` 的返回类型 |
| `DirectoryEntryVO` | 目录条目 VO（name / path / hasChildren），`listDirectories` 的返回类型 |
| `StorageEngineCreateDTO` / `StorageEngineUpdateDTO` | 引擎创建/更新请求 DTO |
| `StorageEngineVO` | 引擎视图 VO |

> `FileEntry` / `DirectoryEntryVO` 归属本模块的 `storage/dto/`（原在 `sync/dto/sync/`，已迁入）：它们是存储策略接口的契约类型，被 sync、transcode 等模块共同使用，放在任一调用方模块都会造成反向依赖。

### AListStorageStrategy — AList 远程策略

`@Component`，`type()` 返回 `"ALIST"`。通过 Spring `RestClient` 调用 AList REST API：

- **API 端点**：`/api/fs/list`（列目录）、`/api/fs/get`（文件详情）、`/api/fs/put`（上传）、`/api/fs/mkdir`（创建目录）、`/api/fs/remove`（删除）、`/api/fs/copy`（复制）
- **认证**：Token 通过 `engine.getEncryptedToken()` 获取（`CryptoConverter` 自动解密），放入 `Authorization` 请求头
- **分页**：`listFiles` 通过 `fetchAllEntries()` 分页获取所有条目后过滤，PAGE_SIZE=50，循环直到条目数 < 50 或返回空
- **上传**：`MultipartFormData` + `File-Path` header + `As-Task: true`
- **复制**：同引擎复制走服务端 `/api/fs/copy`，避免下载-上传往返

### LocalStorageStrategy — 本地文件策略

`@Component`，`type()` 返回 `"LOCAL"`。通过 `java.nio.file` 操作本地文件系统：

- **路径解析**：`resolvePath()` 将相对路径解析为本地绝对路径（`localPath + relativePath`），解析后校验路径必须位于引擎根目录内（`normalize()` + `startsWith`），拒绝 `..` 与绝对路径逃逸（路径穿越防护）
- **列目录**：`listFiles()` 一次性返回全量排序列表（目录在前、名称升序，忽略分页参数，避免逐页重复扫描的 O(n²)）
- **上传**：自动创建父目录，8KB 缓冲区流式写入
- **删除**：目录递归删除，文件直接删除
- **列子目录**：`listDirectories()` 包含 `hasChildren` 判断（子目录探测）
- **复制**：同盘复制使用 `Files.copy`

### StorageEngineService — 策略分发与管理

- **策略分发**：构造器注入 `List<StorageEngineStrategy>`，按 `type()` 方法构建 `Map<String, StorageEngineStrategy>`，`resolve(engine)` 方法按 `engine.getEngineType().name()` 分发
- **引擎管理**：引擎 CRUD、健康检查、连接测试
- **代理调用**：所有文件操作通过 `resolve(engine)` 获取策略后委托执行

### StorageEngineController — 引擎管理 API

提供引擎 CRUD + 连接测试 RESTful 端点：
- `GET /api/engines` — 引擎列表
- `POST /api/engines` — 创建引擎
- `PUT /api/engines/{id}` — 更新引擎
- `DELETE /api/engines/{id}` — 删除引擎
- `POST /api/engines/{id}/test` — 连接测试

### StorageEngine 实体

JPA 实体，包含 `@Version` 乐观锁（章程原则 II）。字段：
- `engineType`：枚举（ALIST / LOCAL）
- `engineStatus`：枚举（ONLINE / OFFLINE / ERROR）
- `baseUrl`、`localPath`、`encryptedToken`（由 `CryptoConverter` 自动加密/解密）等

## 关键流程

### 引擎注册流程

1. 管理员通过 API 创建引擎（指定类型、连接参数）
2. `StorageEngineService` 持久化引擎实体（Token 字段由 `CryptoConverter` 自动加密）
3. 引擎状态初始化为 OFFLINE
4. 首次健康检查通过后状态变为 ONLINE

### 健康检查流程

1. 调用 `testConnection()` 策略方法
2. AList 策略：发送轻量 API 请求验证 Token 有效性
3. 本地策略：验证路径存在且可读
4. 根据结果更新 `engineStatus`（ONLINE / ERROR）

### 目录浏览流程

1. 调用 `listDirectories(path)` 策略方法
2. AList 策略：`fetchAllEntries()` 分页获取所有条目，过滤目录类型，填充 `hasChildren`
3. 本地策略：`Files.list()` 遍历目录，过滤目录，探测子目录
4. 返回 `List<DirectoryEntryVO>`（含 name / path / hasChildren）

## 扩展点

### 新增存储策略

1. 在 `storage/service/engine/` 下创建新类，实现 `StorageEngineStrategy` 接口
2. 添加 `@Component` 注解，`type()` 返回唯一标识（如 `"S3"`）
3. 在 `StorageEngine.EngineType` 枚举中添加对应值
4. 无需修改 `StorageEngineService` 或任何现有策略——策略分发通过 Spring 自动注入 `List<StorageEngineStrategy>` 实现

**示例场景**：新增 S3 兼容存储支持、新增 WebDAV 存储支持。

## 关联 spec

- `specs/006-storage-engine-refactor/` — 存储引擎重构（策略模式引入）
- `specs/001-alist-media-sync/` — 核心 AList 对接逻辑
- `md/alist/AGENTS.md` — AList REST API 参考文档
- `.specify/memory/constitution.md` §I（分层架构）、§II（数据完整性）、§VI（YAGNI — 策略接口仅在第二个实现出现后引入）
