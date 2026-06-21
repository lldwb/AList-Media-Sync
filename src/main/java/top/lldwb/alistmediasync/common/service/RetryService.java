package top.lldwb.alistmediasync.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.exception.RetryableException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 自动重试调度服务
 * <p>
 * 对因瞬时故障（实现 {@link RetryableException} 的异常）失败的操作
 * 进行指数退避自动重试。业务逻辑错误（未实现 RetryableException 的异常）
 * 不进行重试，直接标记为最终失败。
 * </p>
 *
 * <h3>重试策略</h3>
 * <ul>
 *   <li>指数退避公式：min(1000 × 2^(attempt-1), maxIntervalMs)</li>
 *   <li>默认初始间隔 1 秒，默认最大间隔 60 秒</li>
 *   <li>最大重试次数由 app.retry.max-auto-retries 配置，默认 3</li>
 *   <li>重试不占用线程池工作线程（使用 ScheduledExecutorService 调度）</li>
 * </ul>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
public class RetryService {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final int maxAutoRetries;
    private final long initialIntervalMs;
    private final long maxIntervalMs;

    public RetryService(AppProperties appProperties) {
        this.maxAutoRetries = appProperties.getRetry().getMaxAutoRetries();
        this.initialIntervalMs = appProperties.getRetry().getInitialInterval();
        this.maxIntervalMs = appProperties.getRetry().getMaxInterval();
        log.info("重试服务初始化：最大重试次数={}, 初始间隔={}ms, 最大间隔={}ms",
            maxAutoRetries, initialIntervalMs, maxIntervalMs);
    }

    /**
     * 判断异常是否可重试
     *
     * @param throwable 捕获的异常
     * @return true 表示应触发自动重试
     */
    public boolean isRetryable(Throwable throwable) {
        return throwable instanceof RetryableException;
    }

    /**
     * 计算第 attempt 次重试的等待时间（毫秒）
     *
     * @param attempt 重试次数（从 1 开始）
     * @return 等待时间（毫秒）
     */
    public long calculateDelay(int attempt) {
        long delay = initialIntervalMs * (1L << (attempt - 1));
        return Math.min(delay, maxIntervalMs);
    }

    /**
     * 调度自动重试
     *
     * @param attempt      当前重试次数（从 1 开始）
     * @param taskName     任务名称（用于日志）
     * @param retryAction  重试动作
     * @param onMaxRetries 重试用尽时的回调
     */
    public void scheduleRetry(int attempt, String taskName,
                              Runnable retryAction, Runnable onMaxRetries) {
        if (attempt > maxAutoRetries) {
            log.info("自动重试用尽：{}, 已重试 {} 次", taskName, maxAutoRetries);
            onMaxRetries.run();
            return;
        }

        long delay = calculateDelay(attempt);
        log.info("调度自动重试：{}, 第 {}/{} 次, {}ms 后执行", taskName, attempt, maxAutoRetries, delay);

        scheduler.schedule(() -> {
            try {
                log.debug("执行自动重试：{}, 第 {} 次", taskName, attempt);
                retryAction.run();
            } catch (Exception e) {
                log.error("自动重试执行异常：{} — {}", taskName, e.getMessage(), e);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /** 获取最大自动重试次数 */
    public int getMaxAutoRetries() {
        return maxAutoRetries;
    }
}
