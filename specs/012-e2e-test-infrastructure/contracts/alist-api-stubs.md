# 契约：AList API WireMock 桩

**功能**：`012-e2e-test-infrastructure` | **用途**：定义 `AListStorageStrategyIT` 使用的 WireMock 桩映射契约

**外部契约来源**：[`md/alist/AGENTS.md`](../../../md/alist/AGENTS.md)（AList REST API 参考，87 个接口）

本文档定义集成测试层模拟 AList API 的 WireMock 桩契约。所有桩严格遵循 `md/alist/` 外部契约，确保集成测试验证的是"系统与真实 AList 契约的符合度"而非"系统与手写桩的符合度"。

## 统一响应结构

所有 AList fs/admin 接口响应遵循：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

- HTTP 状态码始终 200（即使业务失败）
- `code != 200` 视为业务失败（如 401 token 失效、404 路径不存在）
- `data` 字段结构因端点而异

## 桩端点清单

桩映射文件存放于 `src/test/resources/wiremock/alist-mappings/`，每个端点一个 JSON 文件。

### 1. 健康探活

| 端点 | 方法 | 请求 | 响应 | 桩要点 |
|------|------|------|------|--------|
| `/ping` | GET | 无 | `pong`（`Content-Type: text/plain`） | 特殊：非 JSON，纯文本响应 |

### 2. Token 校验

| 端点 | 方法 | 请求 | 响应 data |
|------|------|------|-----------|
| `/api/me` | GET | `Authorization: {token}` 头 | `{id, username, ...}` 用户信息对象 |

**错误桩**：token 为 `invalid-token` 时返回 `{code:401, message:"unauthorized"}`。

### 3. 文件系统操作

| 端点 | 方法 | 请求体 | 响应 data |
|------|------|--------|-----------|
| `/api/fs/list` | POST | `{path, password:"", page, per_page, refresh:false}` | `{content:[{name,path,is_dir,size,modified,virtual_path}], total}` |
| `/api/fs/get` | POST | `{path, password:"", refresh:true}` | `{name,path,is_dir,size,modified,raw_url,...}` |
| `/api/fs/mkdir` | POST | `{path}` | 空 |
| `/api/fs/put` | PUT | body=octet-stream，header: `File-Path`(URL-encoded), `As-Task:true`, `Content-Length` | 空 |
| `/api/fs/remove` | POST | `{dir, names:[...]}` | 空 |
| `/api/fs/copy` | POST | `{src_dir, dst_dir, names:[...]}` | 空 |
| `/api/fs/move` | POST | `{src_dir, dst_dir, names:[...]}` | 空 |

## 桩行为契约

### 请求匹配规则

- URL 路径精确匹配（如 `/api/fs/list`）
- HTTP 方法匹配
- 请求体关键字段匹配（`path` 字段值决定响应内容，如 `/e2e-test/sample.mp4` 返回该文件信息）
- `Authorization` 头存在性校验（缺失或无效时返回 401 业务码）

### 响应模板

- 使用 WireMock Handlebars 模板，根据请求 `path` 动态生成响应
- 分页响应：`page=1&per_page=50` 返回 50 条，`page=2` 返回剩余或空
- `raw_url` 字段：`/api/fs/get` 响应中包含模拟直链（指向 WireMock 自身的下载端点）

### 错误场景桩

为验证 `AListStorageStrategy` 的错误处理与 `RetryService` 重试机制，需提供以下错误桩：

| 场景 | 触发条件 | 响应 | 验证点 |
|------|---------|------|--------|
| token 失效 | `Authorization: invalid` | `{code:401, message:"unauthorized"}` | 错误处理不重试 |
| 路径不存在 | `path: /nonexistent` | `{code:404, message:"object not found"}` | 错误处理不重试 |
| 服务器错误 | `path: /trigger-500` | `{code:500, message:"internal error"}` | RetryService 重试 3 次后失败 |
| 超时 | `path: /trigger-timeout` | 延迟 30 秒响应 | RetryService 超时重试 |

### 下载直链特殊处理

`AListStorageStrategy.downloadFile` 先调 `/api/fs/get` 取 `raw_url`，再 GET `raw_url` 取流。桩需：
- `/api/fs/get` 响应中 `raw_url` 指向 WireMock 的 `/download/{path}` 端点
- `/download/{path}` 端点返回二进制流（`Content-Type: application/octet-stream`）

**禁止**：桩不得让 `raw_url` 指向 `/d{path}` 形式（会触发 AList 的 sign 鉴权，与真实行为不符）。

## 桩与真实 AList 的一致性保障

- 桩响应结构定期与 `md/alist/` 契约对比（人工或脚本）
- E2E 测试（真实 AList）与集成测试（WireMock 桩）覆盖相同方法，结果应一致；不一致则提示契约漂移
- AList 版本升级时，先更新 `md/alist/` 契约，再同步更新桩

## 引用

- 外部契约：[`md/alist/AGENTS.md`](../../../md/alist/AGENTS.md)
- 客户端实现：`src/main/java/top/lldwb/alistmediasync/storage/service/engine/AListStorageStrategy.java`
- 重试机制：`src/main/java/top/lldwb/alistmediasync/common/service/RetryService.java`
- 桩存放：`src/test/resources/wiremock/alist-mappings/`
- 测试类：`src/test/java/top/lldwb/alistmediasync/integration/client/AListStorageStrategyIT.java`
