# AGENTS.md — AList 对接文档（md/alist/）

## 定位

本目录是 **AList 网盘 REST API 的完整接口参考**，源自 [Apifox 项目 6849786](https://alist-public.apifox.cn/) 公开的 OpenAPI 规范。每个 md 文件包含一个接口的完整 OpenAPI YAML 描述（路径、方法、请求体、响应体、参数）。

后端消费模块：**`storage/service/engine/AListStorageStrategy`**（HTTP 调用 AList REST API 实现 `StorageEngineStrategy` 策略接口）。

> 父级规范：[../AGENTS.md](../AGENTS.md)。本文件为 AList 对接的地方性法规，覆盖接口分组、认证、文档同步、Schemas 等具体细节。

---

## 工作指令

1. **改 `AListStorageStrategy` 前必读对应接口 md** — 例如新增"批量重命名"能力前先读 `fs/批量重命名.md`，确认 `path` 是单个父目录还是文件数组。
2. **不要手动编辑 md** — 文档由 `_download.sh` 从 Apifox 同步，手改会被覆盖。需要新增接口时先在 `_llms_index.txt` 中增条目，再重跑脚本。
3. **认证统一走 Authorization header** — 所有 fs/admin 接口均以 `Authorization: {token}` 形式传入；token 通过 `auth/token获取.md` 接口获取，48 小时过期；项目实际使用永久 token（AList 端通过"管理员设置-其他-令牌"获取）。
4. **路径参数始终绝对路径** — AList 的 `path` 字段从 `/` 开始（如 `/storage1/movies`），不是相对路径。
5. **响应统一结构** — `{ code: 200, message: "success", data: ... }`，`code != 200` 视为失败，`message` 携带错误描述。

---

## 接口分组索引

### `auth/` — 认证

| 接口 | 路径 | 后端使用场景 |
|------|------|-------------|
| [token获取.md](./auth/token获取.md) | `POST /api/auth/login` | 用户名密码换 JWT，48h 过期 |
| [token获取hash.md](./auth/token获取hash.md) | `POST /api/auth/login/hash` | 密码 SHA256（加 `-https://github.com/alist-org/alist` 后缀）后登录 |
| [获取当前用户信息.md](./auth/获取当前用户信息.md) | `GET /api/me` | 校验 token 是否有效 |
| [用户注册.md](./auth/用户注册.md) | — | 项目不使用 |
| [生成2FA密钥.md](./auth/生成2FA密钥.md) | — | 项目不使用 |
| [验证2FA code.md](./auth/验证2FA code.md) | — | 项目不使用 |

> **当前项目实际策略**：使用 AList 管理员后台生成的永久 token，存入 `storage_engine.encrypted_token`（`CryptoConverter` 自动 AES-256-GCM 加解密），不走登录流程。

### `fs/` — 文件系统（核心）

`AListStorageStrategy` 主要消费此分组。

| 接口 | 路径 | 对应策略方法 |
|------|------|-------------|
| [列出文件目录.md](./fs/列出文件目录.md) | `POST /api/fs/list` | `listFiles()` / `listDirectories()` （分页 PAGE_SIZE=50） |
| [获取某个文件_目录信息.md](./fs/获取某个文件_目录信息.md) | `POST /api/fs/get` | `getFileInfo()`、获取下载直链 |
| [获取目录.md](./fs/获取目录.md) | `POST /api/fs/dirs` | 仅取目录列表（不含文件） |
| [搜索文件或文件夹.md](./fs/搜索文件或文件夹.md) | `POST /api/fs/search` | 模糊搜索（项目未使用） |
| [新建文件夹.md](./fs/新建文件夹.md) | `POST /api/fs/mkdir` | `createDirectory()`，上传前自动创建父目录 |
| [重命名文件.md](./fs/重命名文件.md) | `POST /api/fs/rename` | `renameFile()` |
| [批量重命名.md](./fs/批量重命名.md) | `POST /api/fs/batch_rename` | 未使用 |
| [正则重命名.md](./fs/正则重命名.md) | `POST /api/fs/regex_rename` | 未使用 |
| [移动文件.md](./fs/移动文件.md) | `POST /api/fs/move` | `moveFile()` |
| [聚合移动.md](./fs/聚合移动.md) | `POST /api/fs/recursive_move` | 未使用 |
| [复制文件.md](./fs/复制文件.md) | `POST /api/fs/copy` | `copyFile()` |
| [删除文件或文件夹.md](./fs/删除文件或文件夹.md) | `POST /api/fs/remove` | `deleteFile()` |
| [删除空文件夹.md](./fs/删除空文件夹.md) | `POST /api/fs/remove_empty_directory` | 同步清理使用 |
| [添加离线下载.md](./fs/添加离线下载.md) | `POST /api/fs/add_offline_download` | 未使用 |
| [表单上传文件.md](./fs/表单上传文件.md) | `PUT /api/fs/form` | 小文件 multipart 上传 |
| [流式上传文件.md](./fs/流式上传文件.md) | `PUT /api/fs/put` | `uploadFile()`，带 `File-Path` header + `As-Task: true` |

**关键约定（来自当前实现 `AListStorageStrategy`）：**
- `listFiles` 必传 `{ path, password: "", page, per_page, refresh: false }`，缺一字段 AList 会返回 400
- 上传使用 `PUT /api/fs/put`，header 携带 `File-Path`（URL-encoded 绝对路径）和 `As-Task: true`
- 分页：`per_page=50`，循环递增 `page` 直到返回条目 < 50 或为空

### `public/` — 公共接口

| 接口 | 路径 | 用途 |
|------|------|-----|
| [ping检测.md](./public/ping检测.md) | `GET /ping` | 连通性探测（`StorageEngineService.testConnection()`） |
| [获取站点设置.md](./public/获取站点设置.md) | `GET /api/public/settings` | 未使用 |

### `admin/` — 管理员接口（项目未消费）

`admin/` 分组涵盖 AList 管理后台的所有运维能力。**本项目不直接调用这些接口**——AList 的存储、用户、标签、定时任务等由 AList 自身管理。保留文档以备扩展。

| 子分组 | 接口数 | 说明 |
|--------|--------|------|
| `admin/meta/` | 5 | 路径元信息（密码、隐藏、只读） |
| `admin/user/` | 7 | AList 用户管理 |
| `admin/storage/` | 8 | AList 后端存储配置 |
| `admin/driver/` | 3 | AList 驱动模板查询 |
| `admin/setting/` | 7 | 站点设置（含 aria2 / qBittorrent） |
| `admin/task/upload/` | 7 | 上传任务管理 |
| `admin/tag/` | 11 | 文件标签系统 |
| `admin/role/` | 5 | 角色权限 |

### `Schemas/` — 数据结构定义

| 文件 | 描述 |
|------|------|
| [Schemas/PermissionEntry.md](./Schemas/PermissionEntry.md) | 权限条目结构 |
| [Schemas/Role.md](./Schemas/Role.md) | 角色实体结构 |

---

## 文档同步

```bash
cd md/alist
bash _download.sh
```

`_download.sh` 读取 `_llms_index.txt`（按 ` > ` 分级的分类清单），对每行 `- 分类 [名称](URL)` 解析后：
1. `分类` 中的 ` > ` 替换为 `/` 作为目录路径
2. `名称` 中的非法文件名字符（`/ : * ? " < > |`）替换为 `_`
3. `curl -fsSL` 下载到 `分类目录/名称.md`

新增接口的流程：
1. 在 Apifox 项目中复制接口的"导出 LLM 友好 Markdown"URL
2. 编辑 `_llms_index.txt` 增加 `- 分类 [名称](URL): 说明` 条目
3. 重跑 `bash _download.sh`

---

## 错误码速查

| code | 含义 | 处理建议 |
|------|------|---------|
| 200 | 成功 | 正常处理 `data` |
| 401 | token 失效 | 标记 `StorageEngine` 为 `ERROR`，提示重新配置 |
| 403 | 无权限 | 检查 token 对应账号的 AList 权限 |
| 404 | 路径不存在 | 同步前确认源/目标路径 |
| 500 | AList 内部错误 | 走 `RetryService` 重试 |

---

## 当前实现引用

```java
// AListStorageStrategy.java
// 列出文件: md/alist/fs/列出文件目录.md
// 上传文件: md/alist/fs/流式上传文件.md
// 删除路径: md/alist/fs/删除文件或文件夹.md
// 连通性测试: md/alist/public/ping检测.md
```
