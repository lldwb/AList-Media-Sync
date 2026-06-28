# 数据模型（重构产物清单）

**关联**：[plan.md](./plan.md)

本次重构不引入数据库表或 DTO，但产生若干"代码级实体"。以下逐一列出其字段、方法与不变量。

---

## E-1 `PathUtils`（工具类）

**位置**：`top.lldwb.alistmediasync.common.util.PathUtils`

**性质**：`final class`，私有构造器，纯静态方法。

**方法签名**：

| 方法 | 说明 | null 行为 |
|------|------|----------|
| `join(String dir, String name) : String` | 拼接，去掉 name 前导斜杠，处理 dir 尾斜杠 | `dir == null || ""` 时按 `/` 处理；`name == null` 抛 `IllegalArgumentException` |
| `parentDir(String fullPath) : String` | 提取父目录路径，无父目录返回 `""` | `null` 抛 `IllegalArgumentException` |
| `baseName(String fullPath) : String` | 提取文件名 | `null` 抛 `IllegalArgumentException` |
| `swapExtension(String fileName, String newExt) : String` | 替换扩展名 | `newExt` 自动去除前导 `.` |
| `trimLeadingSlash(String path) : String` | 去除所有前导斜杠 | `null` 返回 `""` |
| `normalize(String path) : String` | 规范化（去尾斜杠、补前导斜杠） | `null`/空 返回 `/` |

**不变量**：

- 不持有状态；
- 不抛 `NullPointerException`（统一抛 `IllegalArgumentException` 或返回安全默认值）；
- 路径分隔符固定为 `/`，不依赖 OS。

---

## E-2 `ParallelTaskCollector`（工具类）

**位置**：`top.lldwb.alistmediasync.common.util.ParallelTaskCollector`

**性质**：`final class`，私有构造器，纯静态方法。

**嵌套类型**：

```java
public record Result<T>(int successCount, List<String> failures, List<T> results) {}
```

**方法签名**：

```java
public static <T> Result<T> collect(
    List<CompletableFuture<T>> futures,
    long timeout,
    TimeUnit unit,
    Function<Integer, String> indexLabeler,
    Predicate<T> successPredicate
);
```

**不变量**：

- `successCount + failures.size() == futures.size()`（每个 future 必有一个结果归类）；
- 即使所有 future 失败也不抛异常，失败信息写入 `failures`；
- 超时按单个 future 计算（与现状一致），到时该 future 归为失败。

---

## E-3 `TraceContext.runWith`（新增方法）

**位置**：`top.lldwb.alistmediasync.common.util.TraceContext` 类内新增

**签名**：

```java
public static void runWith(String module, String operation, Runnable task);
```

**契约**：

1. 进入时：若 `getTraceId() == null`，则 `setTraceId(generate())`，并标记 `ownsTrace = true`；
2. `setModuleOperation(module, operation)`；
3. 调用 `task.run()`；
4. 退出时（finally）：若 `ownsTrace`，则 `clear()`，否则保留外层 traceId。

**不变量**：

- 不吞异常（task 抛出的异常原样向外抛）；
- finally 块保证 MDC 不泄漏到下一个线程；
- 不修改已有 `setTraceId/clear/generate/setModuleOperation` 的语义。

---

## E-4 `SyncContext`（内部 record）

**位置**：`top.lldwb.alistmediasync.sync.service.SyncService` 内部 package-private record

**字段**：

```java
record SyncContext(
    SyncTask task,
    TaskExecution execution,
    StorageEngine sourceEngine,
    StorageEngine targetEngine,
    StorageEngineStrategy sourceStrategy,
    StorageEngineStrategy targetStrategy,
    boolean sameEngine,
    List<String> failedFiles   // 可变集合，受拆分后的方法链共享
) {}
```

**不变量**：

- 构造后 `task` / `execution` 等引用不再替换；
- `failedFiles` 是显式共享的可变集合（List），调用方知悉并发安全只在单任务执行线程内成立；
- `execution.setSuccessFiles(...)` 等修改通过 `execution` 引用直接操作，不通过 record 字段替换。

---

## E-5 `TranscodeFileProcessor.FAILURE_STATUS_MAP`

**位置**：`top.lldwb.alistmediasync.transcode.service.TranscodeFileProcessor` 私有静态字段

**定义**：

```java
private static final Map<TranscodeStatus, TranscodeStatus> FAILURE_STATUS_MAP = Map.of(
    TranscodeStatus.DOWNLOADING, TranscodeStatus.DOWNLOAD_FAILED,
    TranscodeStatus.TRANSCODING, TranscodeStatus.TRANSCODE_FAILED,
    TranscodeStatus.UPLOADING,   TranscodeStatus.UPLOAD_FAILED
);
```

**使用规则**：

- 仅在 `doProcess` catch 块中查表使用；
- 调用前必须确认当前 status 在 Map 的 key 集合中，否则抛 `IllegalStateException`；
- 切换后必须调用 `validateTransition(prev, next)`（与现行状态机校验路径一致）。

---

## E-6 `SyncService` 拆分后的私有方法清单

| 方法 | 输入 | 输出 | 职责 |
|------|------|------|------|
| `prepareExecution(SyncTask task) : SyncContext` | task | SyncContext | 创建 TaskExecution、解析两端 strategy、判断 sameEngine |
| `scanAndDiff(SyncContext ctx) : DiffResult` | ctx | (sourceFiles, destPaths, toSync) | 阶段 1+2 扫描与差异计算 |
| `deleteExtraTargets(SyncContext ctx, List<FileInfo> sourceFiles, List<FileInfo> destFiles) : void` | — | — | 仅 FULL 模式调用 |
| `syncOneFile(SyncContext ctx, FileInfo file, Set<String> destPaths, int total) : boolean` | — | 是否成功 | 单文件分发：同引擎/小文件/大文件 |
| `finalizeExecution(SyncContext ctx, int completedCount, int totalToSync) : void` | — | — | 状态判定 + WebSocket 最终推送 |
| `triggerPostSyncTranscode(SyncContext ctx) : void` | — | — | 满足条件时调用 transcodeService.executePostSyncTranscode |

**DiffResult**：`SyncService` 内的 package-private record，字段为 `List<FileInfo> sourceFiles, Set<String> destRelativePaths, List<FileInfo> toSync`。

---

## E-7 `TranscodeFileProcessor` 拆分后的私有方法清单

| 方法 | 职责 |
|------|------|
| `createTaskRecord(...) : TranscodeTask` | 创建并保存初始 TranscodeTask + 推送 WebSocket |
| `runDownloadStep(TranscodeCandidate, TranscodeTask) : Path` | 包装现有 downloadStep |
| `runTranscodeStep(...) : Path` | 创建临时输出 + 调用 doTranscode + 重命名 |
| `runUploadStep(...) : void` | 包装现有 uploadStep |
| `markCompleted(TranscodeTask) : void` | 清理临时文件 + 设为 COMPLETED + 推送 |
| `handleFailure(TranscodeTask, TranscodeCandidate, Exception, ...) : TranscodeResult` | 重试调度 + 失败状态映射 + 保存 + 推送 |
