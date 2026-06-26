package top.lldwb.alistmediasync.sync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import top.lldwb.alistmediasync.common.util.TraceContext;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.entity.TaskExecution;
import top.lldwb.alistmediasync.sync.repository.SyncTaskRepository;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务调度管理服务
 * <p>
 * 在应用启动时从数据库加载所有已启用的同步任务并注册调度。
 * 应用重启后将运行中状态的任务标记为中断。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final SyncTaskRepository syncTaskRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final SyncService syncService;
    private final TaskScheduler taskScheduler = createScheduler();

    /** 已注册的调度任务 future（taskId -> future） */
    private final Map<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    private ThreadPoolTaskScheduler createScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("schedule-");
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 应用启动后恢复定时调度和中断状态
     * 使用 @jakarta.annotation.PostConstruct 确保在 Bean 初始化完成后执行
     */
    @jakarta.annotation.PostConstruct
    public void recoverInterruptedTasks() {
        log.info("正在恢复定时任务调度和中断状态...");

        // 1. 将所有 RUNNING 状态标记为 INTERRUPTED
        int interruptedCount = taskExecutionRepository.markAllRunningAsInterrupted();
        log.info("已将 {} 个运行中的任务标记为中断", interruptedCount);

        // 2. 重新注册所有已启用的定时调度
        var enabledTasks = syncTaskRepository.findByEnabledTrue();
        for (SyncTask task : enabledTasks) {
            registerSchedule(task);
        }
        log.info("已恢复 {} 个定时调度任务", enabledTasks.size());
    }

    /**
     * 注册定时调度
     */
    public void registerSchedule(SyncTask task) {
        if (!task.getEnabled()) {
            return;
        }
        if (scheduledFutures.containsKey(task.getId())) {
            // 已注册，先取消再重新注册
            unregisterSchedule(task.getId());
        }

        Runnable runnable = () -> {
            // 定时触发：分配新的 traceId，便于日志关联（SyncService 入口会沿用此值）
            String traceId = TraceContext.generate();
            try {
                TraceContext.setTraceId(traceId);
                TraceContext.setModuleOperation("sync", "定时调度触发");
                log.debug("定时触发同步任务：{} (traceId={})", task.getName(), traceId);
                syncService.executeSyncTask(task);
            } finally {
                TraceContext.clear();
            }
        };

        ScheduledFuture<?> future;
        switch (task.getScheduleType()) {
            case CRON -> {
                if (task.getCronExpression() == null || task.getCronExpression().isBlank()) {
                    log.warn("Cron 表达式为空，跳过注册：{}", task.getName());
                    return;
                }
                future = taskScheduler.schedule(runnable, new CronTrigger(task.getCronExpression()));
                log.info("已注册 Cron 调度：{} (表达式: {})", task.getName(), task.getCronExpression());
            }
            case INTERVAL -> {
                if (task.getIntervalSeconds() == null || task.getIntervalSeconds() < 10) {
                    log.warn("间隔调度时间过短（< 10s），跳过注册：{}", task.getName());
                    return;
                }
                long intervalMs = task.getIntervalSeconds() * 1000L;
                future = taskScheduler.scheduleAtFixedRate(runnable, java.time.Instant.now(),
                    java.time.Duration.ofMillis(intervalMs));
                log.info("已注册间隔调度：{} (间隔: {} 秒)", task.getName(), task.getIntervalSeconds());
            }
            default -> {
                return; // MANUAL 模式不注册
            }
        }

        scheduledFutures.put(task.getId(), future);
    }

    /**
     * 注销定时调度
     */
    public void unregisterSchedule(Long taskId) {
        ScheduledFuture<?> future = scheduledFutures.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("已注销定时调度：taskId={}", taskId);
        }
    }
}
