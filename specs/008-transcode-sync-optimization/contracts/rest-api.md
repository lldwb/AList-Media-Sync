# 新增 REST API 契约

**日期**：2026-06-21 | **关联**：[plan.md](../plan.md) | [data-model.md](../data-model.md)

## 1. DELETE /api/transcode-tasks/failed

**说明**：删除所有失败状态的转码任务

**认证**：Basic Auth

**响应**：

```json
// 200 OK
{
  "code": 200,
  "message": "已清理 5 个失败任务",
  "data": { "deletedCount": 5 }
}

// 200 OK（无可清理任务）
{
  "code": 200,
  "message": "没有可操作的失败任务",
  "data": { "deletedCount": 0 }
}
```

**匹配状态**：`DOWNLOAD_FAILED`, `TRANSCODE_FAILED`, `UPLOAD_FAILED`

**副作用**：WebSocket 推送 `TASK_EVENT`（action=BATCH_DELETED, taskType=TRANSCODE, count=N, status=FAILED）

---

## 2. DELETE /api/transcode-tasks/completed

**说明**：删除所有已完成状态的转码任务

**认证**：Basic Auth

**响应**：

```json
// 200 OK
{
  "code": 200,
  "message": "已清理 10 个成功任务",
  "data": { "deletedCount": 10 }
}

// 200 OK（无可清理任务）
{
  "code": 200,
  "message": "没有可清理的成功任务",
  "data": { "deletedCount": 0 }
}
```

**匹配状态**：`COMPLETED`

**副作用**：WebSocket 推送 `TASK_EVENT`（action=BATCH_DELETED, taskType=TRANSCODE, count=N, status=COMPLETED）

---

## 3. POST /api/transcode-tasks/retry-all

**说明**：重试所有失败状态的转码任务（异步执行）

**认证**：Basic Auth

**请求体**：无

**响应**：

```json
// 202 Accepted
{
  "code": 202,
  "message": "已提交 3 个任务进行重试，结果将通过实时更新推送",
  "data": { "submittedCount": 3 }
}

// 200 OK（无可重试任务）
{
  "code": 200,
  "message": "没有可操作的失败任务",
  "data": { "submittedCount": 0 }
}
```

**匹配状态**：`DOWNLOAD_FAILED`, `TRANSCODE_FAILED`, `UPLOAD_FAILED`

**执行模式**：异步——立即返回 202，实际重试在后端异步执行

**副作用**：每个任务重试结果通过 WebSocket `TRANSCODE_PROGRESS` 逐条推送

---

## 4. StorageEngineStrategy.copyFile() — 策略接口扩展

**所属模块**：`storage/service/engine/StorageEngineStrategy.java`

```java
/**
 * 在同存储引擎内复制文件。
 * 默认实现抛出 UnsupportedOperationException，由各策略实现覆盖。
 *
 * @param engine 存储引擎实体
 * @param sourcePath 源文件路径
 * @param targetPath 目标文件路径
 * @throws UnsupportedOperationException 如果策略不支持直接复制
 */
default void copyFile(StorageEngine engine, String sourcePath, String targetPath) {
    throw new UnsupportedOperationException(
        "copyFile 不被当前策略支持：" + engine.getEngineType());
}
```

### 4.1 AListStorageStrategy.copyFile()

**实现**：调用 AList `/api/fs/copy` API

**请求**：
```
POST /api/fs/copy
Authorization: {token}
Content-Type: application/json

{
  "src_dir": "/videos/source/",
  "dst_dir": "/videos/target/",
  "names": ["file1.mp3", "file2.mp4"]
}
```

**前置条件**：目标目录已存在（调用前通过 `createDirectory` 确保）

**批量调用**：按源目录分组，SKIP 文件先过滤再批量请求

### 4.2 LocalStorageStrategy.copyFile()

**实现**：使用 `java.nio.file.Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)`

**前置条件**：目标父目录已存在

---

## 错误响应格式（所有端点通用）

```json
// 400 Bad Request
{
  "code": 400,
  "message": "请求参数校验失败：{具体原因}",
  "data": null
}

// 401 Unauthorized
{
  "code": 401,
  "message": "认证失败，请重新登录",
  "data": null
}

// 429 Too Many Requests（WebSocket 连接数超限）
{
  "code": 429,
  "message": "WebSocket 连接数已达上限（{max}），请稍后重试",
  "data": null
}

// 500 Internal Server Error
{
  "code": 500,
  "message": "服务器内部错误",
  "data": null
}
```
