# 契约：TraceContext.runWith

**关联**：[../data-model.md](../data-model.md) E-3

## 公共 API

```java
package top.lldwb.alistmediasync.common.util;

public final class TraceContext {
    // ... 现有方法保持不变 ...

    /**
     * 在已设置 traceId / module / operation 的上下文中执行任务。
     *
     * <p>语义：</p>
     * <ol>
     *   <li>若当前 MDC 中已有 traceId，则继承之，并不在结束时清理</li>
     *   <li>否则生成新 traceId 并标记为"本次拥有"，结束时清理 MDC</li>
     *   <li>设置 module 与 operation</li>
     *   <li>执行 task.run()</li>
     *   <li>finally 块：仅在"本次拥有"时调用 clear()</li>
     * </ol>
     *
     * <p>异常处理：task 抛出的异常原样向外抛出，不吞异常，但仍执行 finally 清理。</p>
     *
     * @param module    业务模块名（如 "sync" / "transcode" / "webhook"）
     * @param operation 当前操作描述（如 "同步任务执行"）
     * @param task      要执行的任务
     */
    public static void runWith(String module, String operation, Runnable task);
}
```

## 行为契约

### 场景 1：上游已有 traceId

输入：调用方 MDC 已有 `traceId="abc"`。

期望：

- task 执行时 MDC 中 `traceId="abc"` 保持不变
- module / operation 被覆盖为新值
- 退出后 MDC 中 `traceId="abc"` **仍在**（不清理，因为本调用不拥有）
- module / operation 行为暂以"恢复到调用前值"为准，但若现有代码未保存恢复，可保留"覆盖后不还原"语义 — **需在测试中固定**

### 场景 2：上游无 traceId

输入：调用方 MDC 无 traceId（getTraceId() 返回 null）。

期望：

- 进入时生成新 traceId 并设入 MDC
- 退出后 MDC 中 traceId 被清理（clear）
- module / operation 也被清理

### 场景 3：task 抛异常

输入：task 在 run() 中抛 RuntimeException。

期望：

- 异常向外传播
- finally 仍执行 clear / 保留逻辑（场景 1/2 一致）
- 调用方栈帧能正确接收异常

## 测试要求

`TraceContextRunWithTest.java`（或合并到 `TraceContextTest`）覆盖：

- 场景 1：上游 traceId 继承且不清理
- 场景 2：上游无 traceId 自动生成且清理
- 场景 3：异常透传且 finally 执行
- 并发：两个线程分别 runWith 互不污染（参考现有 `TraceContextTest.并发请求traceId不应互相污染`）
- 行覆盖率 ≥ 95%

## 调用方迁移示例

### SyncService.executeSyncTask 迁移前后

迁移前（约 11 行）：

```java
@Async
@Transactional
public void executeSyncTask(SyncTask task) {
    String traceId = TraceContext.getTraceId();
    if (traceId == null) traceId = TraceContext.generate();
    TraceContext.setTraceId(traceId);
    TraceContext.setModuleOperation("sync", "同步任务执行");
    try {
        final Long taskId = task.getId();
        task = syncTaskRepository.findById(taskId)
            .orElseThrow(() -> new NoSuchElementException("..."));
        executeSyncTaskInternal(task);
    } finally {
        TraceContext.clear();
    }
}
```

迁移后（约 7 行）：

```java
@Async
@Transactional
public void executeSyncTask(SyncTask task) {
    TraceContext.runWith("sync", "同步任务执行", () -> {
        final Long taskId = task.getId();
        SyncTask reloaded = syncTaskRepository.findById(taskId)
            .orElseThrow(() -> new NoSuchElementException("..."));
        executeSyncTaskInternal(reloaded);
    });
}
```
