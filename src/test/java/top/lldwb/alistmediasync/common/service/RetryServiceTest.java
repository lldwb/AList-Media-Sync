package top.lldwb.alistmediasync.common.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.exception.RetryableException;
import top.lldwb.alistmediasync.common.exception.RetryableIOException;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自动重试调度服务单元测试
 * <p>
 * 覆盖 {@link RetryService} 的异常可重试判定（{@code isRetryable}）、
 * 指数退避延迟计算（{@code calculateDelay}）、最大重试次数读取（{@code getMaxAutoRetries}）
 * 与重试调度行为（{@code scheduleRetry}：延迟生效、达上限回调、动作异常被吞、线程池仍可用）。
 * </p>
 * <p>
 * <b>溢出边界（如实记录现状，疑似缺陷）</b>：{@code calculateDelay} 形如
 * {@code initialIntervalMs * (1L << (attempt - 1))}，左侧乘法为 long 运算且无溢出保护。
 * 当 {@code attempt - 1 >= 63}（或乘积超过 long 范围）时结果溢出：本例中
 * 初始间隔为偶数时溢出归零、为奇数时溢出为 {@link Long#MIN_VALUE}（负数），
 * {@code Math.min(delay, maxIntervalMs)} 无法把结果封顶到 {@code maxIntervalMs}。
 * 本测试<b>不断言理想行为</b>，只把实际的延迟值与调度表现如实固定下来。
 * </p>
 * <p>
 * 异步行为一律使用 {@link CountDownLatch}（含超时）验证，不使用 {@code Thread.sleep}。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("重试服务测试")
class RetryServiceTest {

    // ================================================================
    // isRetryable：可重试判定
    // ================================================================

    @Test
    @DisplayName("RetryableIOException 应判定为可重试")
    void shouldTreatRetryableIoExceptionAsRetryable() {
        assertTrue(newService(3, 1000, 60000).isRetryable(new RetryableIOException("下载失败")));
    }

    @Test
    @DisplayName("普通 IOException（业务错误）不应判定为可重试")
    void shouldNotTreatPlainIoExceptionAsRetryable() {
        assertFalse(newService(3, 1000, 60000).isRetryable(new IOException("文件不存在（404）")));
    }

    @Test
    @DisplayName("运行时业务异常不应判定为可重试")
    void shouldNotTreatRuntimeExceptionAsRetryable() {
        assertFalse(newService(3, 1000, 60000).isRetryable(new IllegalStateException("源引擎未配置")));
    }

    @Test
    @DisplayName("判定基于 RetryableException 接口，非 RetryableIOException 的实现类也可重试")
    void shouldTreatAnyRetryableExceptionImplementationAsRetryable() {
        assertTrue(newService(3, 1000, 60000).isRetryable(new CustomRetryableException()));
    }

    @Test
    @DisplayName("异常为 null 时应判定为不可重试（instanceof 判定不抛 NPE）")
    void shouldNotTreatNullAsRetryable() {
        assertFalse(newService(3, 1000, 60000).isRetryable(null));
    }

    // ================================================================
    // getMaxAutoRetries：配置读取
    // ================================================================

    @Test
    @DisplayName("应返回配置的最大自动重试次数")
    void shouldReturnConfiguredMaxAutoRetries() {
        assertEquals(7, newService(7, 1000, 60000).getMaxAutoRetries());
    }

    @Test
    @DisplayName("未显式配置时应返回默认最大自动重试次数 3")
    void shouldReturnDefaultMaxAutoRetries() {
        assertEquals(3, new RetryService(new AppProperties()).getMaxAutoRetries());
    }

    // ================================================================
    // calculateDelay：指数退避延迟计算
    // ================================================================

    @Test
    @DisplayName("延迟应按配置的初始间隔指数增长（1s → 2s → 4s）")
    void shouldGrowExponentiallyFromInitialInterval() {
        RetryService service = newService(3, 1000, 60000);

        assertEquals(1000L, service.calculateDelay(1));
        assertEquals(2000L, service.calculateDelay(2));
        assertEquals(4000L, service.calculateDelay(3));
    }

    @Test
    @DisplayName("延迟超过最大间隔时应封顶为 maxInterval")
    void shouldCapDelayAtMaxInterval() {
        RetryService service = newService(10, 1000, 5000);

        assertEquals(4000L, service.calculateDelay(3), "封顶前仍按指数增长");
        assertEquals(5000L, service.calculateDelay(4), "8000ms 应被封顶为 5000ms");
        assertEquals(5000L, service.calculateDelay(10), "远超上限的尝试次数一律取上限");
    }

    @Test
    @DisplayName("初始间隔大于最大间隔时首次重试即被封顶")
    void shouldCapFirstAttemptWhenInitialIntervalExceedsMax() {
        assertEquals(5000L, newService(3, 10000, 5000).calculateDelay(1));
    }

    @Test
    @DisplayName("attempt 为 0 时当前实现返回 0（移位距离 -1 被掩码为 63 后溢出归零，如实记录）")
    void shouldReturnZeroDelayForAttemptZero() {
        // 如实记录现状：1L << (0 - 1) 的移位距离 -1 被 JVM 掩码为 63，
        // 计算 1000L * Long.MIN_VALUE 溢出归零，故 attempt=0 得到 0（立即执行），
        // 而非未定义/异常。调用方（TranscodeFileProcessor）从 1 开始计数，不触发此路径。
        assertEquals(0L, newService(3, 1000, 60000).calculateDelay(0));
    }

    @Test
    @DisplayName("attempt=64 且初始间隔为偶数时延迟溢出归零（如实记录，未封顶为 maxInterval）")
    void shouldReturnZeroDelayAtOverflowAttemptForEvenInitialInterval() {
        // 如实记录现状（疑似缺陷）：1000 * (1L << 63) 溢出为 0，
        // Math.min(0, 60000) 得不到上限值 60000，重试会立即执行而非退避到 60 秒。
        RetryService service = newService(64, 1000, 60000);

        assertEquals(0L, service.calculateDelay(64), "溢出归零，未返回上限 60000ms");
        assertEquals(0L, service.calculateDelay(63), "同一溢出区间内的尝试次数同样归零");
        assertEquals(1000L, service.calculateDelay(65), "移位距离按 64 取模，退避序列回绕到起始值");
    }

    @Test
    @DisplayName("attempt=64 且初始间隔为奇数时延迟溢出为负数（如实记录，Math.min 无法封顶）")
    void shouldReturnNegativeDelayAtOverflowAttemptForOddInitialInterval() {
        // 如实记录现状（疑似缺陷）：1001 * (1L << 63) 溢出为 Long.MIN_VALUE（负数），
        // Math.min(负值, 60000) 返回负值——指数退避的「封顶」语义在溢出区间失效。
        RetryService service = newService(64, 1001, 60000);

        long delay = service.calculateDelay(64);

        assertEquals(Long.MIN_VALUE, delay);
        assertTrue(delay < 0, "溢出后延迟为负数，实际：" + delay);
    }

    // ================================================================
    // scheduleRetry：调度行为
    // ================================================================

    @Test
    @DisplayName("未达上限时应调度重试动作执行，且不触发用尽回调")
    void shouldScheduleRetryActionWhenAttemptWithinLimit() throws InterruptedException {
        RetryService service = newService(3, 50, 60000);
        CountDownLatch executed = new CountDownLatch(1);
        AtomicInteger maxRetriesCallbackCount = new AtomicInteger();

        service.scheduleRetry(1, "转码任务", executed::countDown, maxRetriesCallbackCount::incrementAndGet);

        assertTrue(executed.await(3, TimeUnit.SECONDS), "重试动作应在退避延迟后被执行");
        assertEquals(0, maxRetriesCallbackCount.get(), "未达上限时不应触发用尽回调");
    }

    @Test
    @DisplayName("重试动作不应在退避延迟到期前执行")
    void shouldNotRunRetryActionBeforeDelayElapses() throws InterruptedException {
        RetryService service = newService(3, 1500, 60000);
        CountDownLatch executed = new CountDownLatch(1);

        service.scheduleRetry(1, "转码任务", executed::countDown, () -> {});

        assertFalse(executed.await(150, TimeUnit.MILLISECONDS),
            "首次重试延迟为 1500ms，不应在 150ms 内执行");
        assertTrue(executed.await(5, TimeUnit.SECONDS),
            "延迟到期后（约 1500ms）应执行");
    }

    @Test
    @DisplayName("attempt 达到最大重试次数时仍会调度（边界：判定为 > 而非 >=）")
    void shouldStillScheduleOnExactMaxAttempt() throws InterruptedException {
        RetryService service = newService(3, 50, 60000);
        CountDownLatch executed = new CountDownLatch(1);
        AtomicInteger maxRetriesCallbackCount = new AtomicInteger();

        service.scheduleRetry(3, "转码任务", executed::countDown, maxRetriesCallbackCount::incrementAndGet);

        assertTrue(executed.await(3, TimeUnit.SECONDS));
        assertEquals(0, maxRetriesCallbackCount.get());
    }

    @Test
    @DisplayName("attempt 超过上限时应触发用尽回调且不再调度重试动作")
    void shouldInvokeOnMaxRetriesWhenAttemptExceedsLimit() throws InterruptedException {
        RetryService service = newService(3, 50, 60000);
        CountDownLatch executed = new CountDownLatch(1);
        AtomicInteger maxRetriesCallbackCount = new AtomicInteger();

        service.scheduleRetry(4, "转码任务", executed::countDown, maxRetriesCallbackCount::incrementAndGet);

        assertEquals(1, maxRetriesCallbackCount.get(), "超过上限应同步触发一次用尽回调");
        assertFalse(executed.await(300, TimeUnit.MILLISECONDS),
            "超过上限后不应再调度任何重试动作");
    }

    @Test
    @DisplayName("重试动作抛异常时应被吞掉且调度器仍可继续调度后续重试")
    void shouldSwallowRetryActionExceptionAndKeepSchedulerUsable() throws InterruptedException {
        RetryService service = newService(3, 50, 60000);
        CountDownLatch firstActionDone = new CountDownLatch(1);

        service.scheduleRetry(1, "失败任务", () -> {
            firstActionDone.countDown();
            throw new IllegalStateException("重试动作内部异常");
        }, () -> {});

        assertTrue(firstActionDone.await(3, TimeUnit.SECONDS), "第一次重试动作应被执行");

        // 若异常未被吞掉，调度线程仍会存活；此处以「后续任务仍能执行」证伪线程池被破坏
        CountDownLatch secondActionDone = new CountDownLatch(1);
        service.scheduleRetry(2, "后续任务", secondActionDone::countDown, () -> {});

        assertTrue(secondActionDone.await(3, TimeUnit.SECONDS),
            "前一个重试动作抛出的异常不应影响后续重试调度");
    }

    @Test
    @DisplayName("attempt=0 时当前实现按立即执行处理（无下界校验，如实记录）")
    void shouldScheduleImmediatelyForAttemptZero() throws InterruptedException {
        // 如实记录现状：实现未校验 attempt 下界，attempt=0 经 calculateDelay 得到 0ms 延迟，
        // 重试被立即执行（生产调用方从 1 开始计数，不触发此路径）。
        RetryService service = newService(3, 1000, 60000);
        CountDownLatch executed = new CountDownLatch(1);

        service.scheduleRetry(0, "转码任务", executed::countDown, () -> {});

        assertTrue(executed.await(1, TimeUnit.SECONDS), "延迟 0ms 应立即执行");
    }

    @Test
    @DisplayName("延迟溢出为负数时被调度器当作立即执行，退避上限被绕过（如实记录）")
    void shouldRunImmediatelyWhenDelayOverflowsToNegative() throws InterruptedException {
        // 如实记录现状（疑似缺陷的运行时表现）：
        // initialInterval=1001（奇数）+ attempt=64 → calculateDelay 溢出为 Long.MIN_VALUE，
        // ScheduledThreadPoolExecutor 把负延迟当作 0 处理（不抛异常），重试立即执行；
        // 若封顶语义生效，延迟应为 maxInterval=60000ms，1 秒内不可能执行。
        RetryService service = newService(64, 1001, 60000);
        CountDownLatch executed = new CountDownLatch(1);

        service.scheduleRetry(64, "转码任务", executed::countDown, () -> {});

        assertTrue(executed.await(1, TimeUnit.SECONDS),
            "负延迟未抛出 IllegalArgumentException，而是被当作 0 延迟立即执行");
        assertEquals(Long.MIN_VALUE, service.calculateDelay(64));
    }

    // ================================================================
    // 测试辅助
    // ================================================================

    /**
     * 构造指定重试参数的 {@link RetryService}
     * <p>
     * 直接构造 {@link AppProperties} 以绕过 Spring 的 {@code @Min} 校验，
     * 从而使用毫秒级间隔（避免测试等待数秒）。
     * </p>
     */
    private static RetryService newService(int maxAutoRetries, long initialIntervalMs, long maxIntervalMs) {
        AppProperties appProperties = new AppProperties();
        appProperties.getRetry().setMaxAutoRetries(maxAutoRetries);
        appProperties.getRetry().setInitialInterval(initialIntervalMs);
        appProperties.getRetry().setMaxInterval(maxIntervalMs);
        return new RetryService(appProperties);
    }

    /** 实现 RetryableException 标记接口的自定义异常（验证判定基于接口而非具体实现类） */
    private static final class CustomRetryableException extends RuntimeException implements RetryableException {
    }
}
