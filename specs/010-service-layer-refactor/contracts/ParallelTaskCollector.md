# 契约：ParallelTaskCollector

**关联**：[../data-model.md](../data-model.md) E-2

## 公共 API

```java
package top.lldwb.alistmediasync.common.util;

public final class ParallelTaskCollector {
    private ParallelTaskCollector() {}

    public record Result<T>(int successCount, List<String> failures, List<T> results) {}

    public static <T> Result<T> collect(
        List<CompletableFuture<T>> futures,
        long timeout,
        TimeUnit unit,
        Function<Integer, String> indexLabeler,
        Predicate<T> successPredicate
    );
}
```

## 行为契约

### 成功路径

输入：3 个 future 全部正常返回，`successPredicate` 全部为 true。

输出：

- `successCount == 3`
- `failures.isEmpty()`
- `results` 长度 3，顺序与 futures 一致

### 部分失败

输入：3 个 future，第 1 个返回成功、第 2 个抛 `RuntimeException`、第 3 个返回但 `successPredicate` 为 false。

输出：

- `successCount == 1`
- `failures.size() == 2`，每条形如 `indexLabeler.apply(i) + ": " + 错误描述`
- `results` 长度 3（成功项的结果保留，失败项可能为 null）

### 超时

输入：1 个 future 在指定超时内未完成。

输出：

- 该 future 被归为失败
- `failures` 含一条形如 `indexLabeler.apply(i) + ": java.util.concurrent.TimeoutException..."` 的描述
- 其他 future 正常完成不受影响

### 不变量

- `successCount + failures.size() == futures.size()`
- 方法本身不抛异常（除非传入 null 参数）
- 不修改传入的 `futures` 列表

## 测试要求

`ParallelTaskCollectorTest.java` 覆盖：

- 全成功（3 future）
- 全失败（异常 + predicate false 各 1）
- 超时一个
- futures 为空列表（successCount=0、failures=[]）
- `indexLabeler` 抛异常时不影响其他 future 收集
- 行覆盖率 ≥ 95%

## 调用方迁移示例

### TranscodeService.processCandidates 迁移前后

迁移前（约 30 行）：

```java
List<CompletableFuture<TranscodeResult>> futures = ...;
int successCount = 0;
List<String> failures = new ArrayList<>();
for (int i = 0; i < futures.size(); i++) {
    try {
        TranscodeResult r = futures.get(i).get(10, TimeUnit.MINUTES);
        if (r.success()) successCount++;
        else failures.add(r.sourceFileName() + ": " + r.error());
    } catch (Exception e) {
        failures.add(candidates.get(i).name() + ": " + e.getMessage());
    }
}
execution.setSuccessFiles(successCount);
execution.setFailedFiles(failures.size());
...
```

迁移后（约 8 行）：

```java
ParallelTaskCollector.Result<TranscodeResult> r = ParallelTaskCollector.collect(
    futures, 10, TimeUnit.MINUTES,
    i -> candidates.get(i).name(),
    TranscodeResult::success
);
execution.setSuccessFiles(r.successCount());
execution.setFailedFiles(r.failures().size());
if (!r.failures().isEmpty()) {
    execution.setFailureDetails(toJson(r.failures()));
    execution.setStatus(r.successCount() > 0 ? PARTIAL_SUCCESS : FAILED);
}
```
