# 阶段 0：研究与技术决策

**关联**：[plan.md](./plan.md)

## R-1 行为对等基线的建立方式

**抉择**：在 PR 1 之前运行 `./mvnw test`，把"通过用例数 / 失败用例数 / 跳过用例数"三项数字记录到 `quickstart-results.md` 的"基线"段落。后续每个 PR 提交前后再分别运行一次，三组数字写入同一段落。

**理由**：

- `./mvnw test` 已是项目标准入口；
- 不需要引入新工具或度量管道，符合 YAGNI；
- 三组数字（before / PR1 / PR2 / PR3）足以暴露"不小心改了行为"的回归。

**已考虑的备选**：

- 引入 JaCoCo 覆盖率门禁 — 收益高但配置成本超出本次重构边界，留待后续单独引入；
- 引入 Pitest 变异测试 — 同样超出范围。

## R-2 路径工具方法的语义基准

**抉择**：以 `SyncService.concatPath` 为基准。

**理由**：

- 三个实现中只有 `SyncService` 显式处理 `name` 的前导斜杠（避免 `//` 产生），更稳健；
- AList API 对 `//` 不友好，潜在 bug 也偏向 `SyncService` 现行语义；
- 用户已在澄清问答中确认（见 clarifications.md Q1）。

**已考虑的备选**：

- 直接使用 `java.nio.file.Path.resolve` — 不可行，会引入 OS 路径分隔符差异（Windows 反斜杠），AList 协议要求正斜杠。

## R-3 `JsonMapper` 是否线程安全可复用单例

**抉择**：可以。复用 Spring 自动配置注入的 `JsonMapper` Bean。

**理由**：

- Jackson 官方文档明确：`ObjectMapper`（含其子类 `JsonMapper`）一旦完成 config 配置，序列化/反序列化操作线程安全；
- Spring Boot 4.x 已经自动注入一个共享 `JsonMapper`；
- 当前 `SyncService` 和 `WebhookService` 已经通过构造器注入 `JsonMapper`，**只有 `TranscodeService` 在 `toJson` 中重复 `JsonMapper.builder().build()`**，统一改为复用即可。

**已考虑的备选**：

- 抽取 `JsonUtil` 静态工具类 — 也可行，但需手动持有 `JsonMapper` 实例；不如直接复用 Spring Bean 优雅。最终选择是：在 `TranscodeService` 改为构造器注入；不新建 `JsonUtil`，降低改动面。

## R-4 失败状态映射用什么数据结构

**抉择**：私有静态 `Map<TranscodeStatus, TranscodeStatus>`，通过 `Map.of(...)` 构造不可变实例。

**理由**：

- 直观、可读、O(1) 查询；
- `Map.of` 返回不可变 Map，避免被并发修改；
- 保留 `validateTransition` 调用，不绕过状态机校验（FR-008）。

**已考虑的备选**：

- 在 `TranscodeStatus` 枚举上加 `failureCounterpart()` 方法 — 更 OO，但触动 entity 包，影响范围扩大，违反"最小变更原则"。

## R-5 `ParallelTaskCollector` 的接口形状

**抉择**：

```java
public final class ParallelTaskCollector {
    public record Result<T>(int successCount, List<String> failures, List<T> results) {}

    public static <T> Result<T> collect(
        List<CompletableFuture<T>> futures,
        long timeout,
        TimeUnit unit,
        Function<Integer, String> indexLabeler,  // 用于失败时输出"哪个 future 失败"
        Predicate<T> successPredicate            // 判定 T 是否表示成功（TranscodeResult.success()）
    );
}
```

**理由**：

- 函数式入参把"成功判定"与"失败标签"两个变化点交给调用方，避免与 `TranscodeResult` 等具体类型耦合；
- 返回 record 既给出聚合计数，又保留原始结果（如调用方还需要进一步处理）；
- 超时由调用方传入（保持 10 分钟现状）。

**已考虑的备选**：

- 让 collector 直接修改 `TaskExecution`（写 successFiles / failedFiles / status）— 耦合过强，违反单一职责；最终在调用方根据返回的 `Result` 自行写入 `TaskExecution`。

## R-6 `TraceContext.runWith` 是否支持 `Callable<T>`

**抉择**：暂只提供 `Runnable` 重载，未来需要时再增 `Callable<T>`。

**理由**：

- 现状所有 5 处样板都是 `void` 返回的任务，`Runnable` 足够；
- YAGNI。

## R-7 `SyncContext` 该用 record 还是普通类

**抉择**：package-private record。

**理由**：

- 字段一旦构造完不需变更（execution 自身可变 set 状态，但 ctx 持有的引用不变）；
- record 自带 equals/hashCode/toString，便于调试日志；
- Java 21 record 已稳定。

**风险**：`failedFiles`（`List<String>`）是可变集合，record 字段保存引用，调用方仍可 add。这是有意为之，需要在 contracts 中明示。

## R-8 重构是否需要先升级现有单测

**抉择**：在 PR 2、PR 3 进行 Service 拆分前，**先补齐回归测试**作为护栏，再做拆分。

**理由**：

- "没有测试就不要重构" 是基本工程纪律；
- 现有 `SyncServiceTest` 已 450 行 / 24 用例，覆盖较好，但 SyncMode × ConflictStrategy × sameEngine 三维矩阵需要补齐缺失格；
- 这一步可与 PR 1 并行进行，但合入次序必须在拆分之前。

## R-9 是否引入 Lombok `@UtilityClass`

**抉择**：不引入。手写 `private constructor + static methods` 形式。

**理由**：

- 项目已有 Lombok 但避免在工具类中引入新注解类型，降低阅读门槛；
- 编译期开销可忽略。
