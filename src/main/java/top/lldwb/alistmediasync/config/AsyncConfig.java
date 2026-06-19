package top.lldwb.alistmediasync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置
 * <p>
 * 为转码引擎配置专用的有界线程池。
 * 不使用虚拟线程执行转码任务——转码是 CPU 密集型操作，
 * 无限创建虚拟线程会导致 CPU 颠簸。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 转码执行器线程池
     * <ul>
     *   <li>核心线程数：8（由配置注入）</li>
     *   <li>最大线程数：32（由 app.pool.max-size 控制）</li>
     *   <li>队列容量：64</li>
     *   <li>拒绝策略：CallerRunsPolicy（调用者线程执行）</li>
     * </ul>
     */
    @Bean(name = "transcodeExecutor")
    public Executor transcodeExecutor(top.lldwb.alistmediasync.config.AppProperties appProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(appProperties.getPool().getCoreSize());
        executor.setMaxPoolSize(appProperties.getPool().getMaxSize());
        executor.setQueueCapacity(64);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("transcode-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
