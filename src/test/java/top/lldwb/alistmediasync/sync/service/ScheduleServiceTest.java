package top.lldwb.alistmediasync.sync.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import top.lldwb.alistmediasync.execution.TaskExecutionRepository;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.repository.SyncTaskRepository;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 定时任务调度管理服务单元测试
 * <p>
 * 覆盖 recoverInterruptedTasks()、registerSchedule()、unregisterSchedule() 三个方法。
 * 不测试实际调度执行（由 TaskScheduler 框架保证），只验证注册/注销逻辑。
 * </p>
 * <p>
 * registerSchedule 的契约是"非法配置 → 不注册调度"，因此所有跳过场景都断言
 * {@code scheduledFutures} 中不存在对应任务（而非仅断言"未抛异常"，后者无法证伪）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("定时任务调度服务测试")
class ScheduleServiceTest {

    @Mock
    private SyncTaskRepository syncTaskRepository;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private SyncService syncService;

    @InjectMocks
    private ScheduleService service;

    private SyncTask cronTask;
    private SyncTask intervalTask;
    private SyncTask manualTask;

    /** 被测类的日志事件捕获器（用于断言跳过注册时的告警文案） */
    private final Logger scheduleServiceLogger =
        (Logger) LoggerFactory.getLogger(ScheduleService.class);
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        logAppender.start();
        scheduleServiceLogger.addAppender(logAppender);

        StorageEngine mockEngine = new StorageEngine();
        mockEngine.setId(1L);
        mockEngine.setName("测试引擎");

        // Cron 调度任务
        cronTask = new SyncTask();
        cronTask.setId(1L);
        cronTask.setName("Cron任务");
        cronTask.setSourceEngine(mockEngine);
        cronTask.setTargetEngine(mockEngine);
        cronTask.setScheduleType(SyncTask.ScheduleType.CRON);
        cronTask.setCronExpression("0 0 */6 * * *");
        cronTask.setSyncMode(SyncTask.SyncMode.NEW_ONLY);
        cronTask.setEnabled(true);

        // 间隔调度任务
        intervalTask = new SyncTask();
        intervalTask.setId(2L);
        intervalTask.setName("间隔任务");
        intervalTask.setSourceEngine(mockEngine);
        intervalTask.setTargetEngine(mockEngine);
        intervalTask.setScheduleType(SyncTask.ScheduleType.INTERVAL);
        intervalTask.setIntervalSeconds(3600);
        intervalTask.setSyncMode(SyncTask.SyncMode.NEW_ONLY);
        intervalTask.setEnabled(true);

        // 手动触发任务
        manualTask = new SyncTask();
        manualTask.setId(3L);
        manualTask.setName("手动任务");
        manualTask.setSourceEngine(mockEngine);
        manualTask.setTargetEngine(mockEngine);
        manualTask.setScheduleType(SyncTask.ScheduleType.MANUAL);
        manualTask.setSyncMode(SyncTask.SyncMode.NEW_ONLY);
        manualTask.setEnabled(true);
    }

    @AfterEach
    void tearDown() {
        scheduleServiceLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    // ================================================================
    // 测试辅助
    // ================================================================

    /** 读取被测服务内部的已注册调度表（验证"注册 / 未注册"的唯一可信来源） */
    @SuppressWarnings("unchecked")
    private Map<Long, ScheduledFuture<?>> scheduledFutures() {
        try {
            Field field = ScheduleService.class.getDeclaredField("scheduledFutures");
            field.setAccessible(true);
            return (Map<Long, ScheduledFuture<?>>) field.get(service);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法读取 ScheduleService.scheduledFutures", e);
        }
    }

    /** 断言存在指定等级的日志且文案包含关键字 */
    private void assertLogged(ch.qos.logback.classic.Level level, String keyword) {
        List<String> messages = logAppender.list.stream()
            .filter(e -> e.getLevel() == level)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
        assertTrue(messages.stream().anyMatch(m -> m.contains(keyword)),
            "应输出包含「" + keyword + "」的 " + level + " 日志，实际=" + messages);
    }

    // ================================================================
    // recoverInterruptedTasks 方法测试
    // ================================================================

    @Test
    @DisplayName("启动恢复 — 标记中断并重新注册所有已启用任务")
    void shouldRecoverTasksOnStartup() {
        when(taskExecutionRepository.markAllRunningAsInterrupted()).thenReturn(3);
        when(syncTaskRepository.findByEnabledTrue()).thenReturn(
            List.of(cronTask, intervalTask, manualTask));

        service.recoverInterruptedTasks();

        verify(taskExecutionRepository).markAllRunningAsInterrupted();
        verify(syncTaskRepository).findByEnabledTrue();
        Map<Long, ScheduledFuture<?>> registered = scheduledFutures();
        assertTrue(registered.containsKey(cronTask.getId()), "CRON 任务应被重新注册");
        assertTrue(registered.containsKey(intervalTask.getId()), "INTERVAL 任务应被重新注册");
        assertFalse(registered.containsKey(manualTask.getId()), "MANUAL 任务不应注册调度");
    }

    @Test
    @DisplayName("启动恢复 — 无已启用任务时不注册任何调度")
    void shouldHandleNoEnabledTasksOnStartup() {
        when(taskExecutionRepository.markAllRunningAsInterrupted()).thenReturn(0);
        when(syncTaskRepository.findByEnabledTrue()).thenReturn(List.of());

        service.recoverInterruptedTasks();

        verify(taskExecutionRepository).markAllRunningAsInterrupted();
        assertTrue(scheduledFutures().isEmpty(), "无已启用任务时不应注册任何调度");
    }

    // ================================================================
    // registerSchedule 方法测试
    // ================================================================

    @Test
    @DisplayName("注册调度 — 合法 Cron 表达式成功注册")
    void shouldRegisterCronSchedule() {
        service.registerSchedule(cronTask);

        Map<Long, ScheduledFuture<?>> registered = scheduledFutures();
        assertTrue(registered.containsKey(cronTask.getId()), "合法 Cron 任务应被注册");
        assertFalse(registered.get(cronTask.getId()).isCancelled());
    }

    @Test
    @DisplayName("注册调度 — 合法的间隔调度成功注册")
    void shouldRegisterIntervalSchedule() {
        service.registerSchedule(intervalTask);

        Map<Long, ScheduledFuture<?>> registered = scheduledFutures();
        assertTrue(registered.containsKey(intervalTask.getId()), "合法间隔任务应被注册");
        assertFalse(registered.get(intervalTask.getId()).isCancelled());
    }

    @Test
    @DisplayName("注册调度 — 间隔恰好 10 秒（下限）成功注册")
    void shouldRegisterIntervalAtMinimumThreshold() {
        intervalTask.setIntervalSeconds(10);

        service.registerSchedule(intervalTask);

        assertTrue(scheduledFutures().containsKey(intervalTask.getId()),
            "间隔等于下限 10 秒应被注册");
    }

    @Test
    @DisplayName("注册调度 — 禁用的任务应跳过")
    void shouldSkipDisabledTask() {
        cronTask.setEnabled(false);

        service.registerSchedule(cronTask);

        assertTrue(scheduledFutures().isEmpty(), "禁用任务不应注册调度");
        verify(syncService, never()).executeSyncTask(any());
    }

    @Test
    @DisplayName("注册调度 — Cron 表达式为空应跳过并记录警告")
    void shouldSkipEmptyCronExpression() {
        cronTask.setCronExpression("");

        service.registerSchedule(cronTask);

        assertTrue(scheduledFutures().isEmpty(), "空 Cron 表达式不应注册调度");
        assertLogged(ch.qos.logback.classic.Level.WARN, "Cron 表达式为空");
    }

    @Test
    @DisplayName("注册调度 — 间隔秒数小于 10 应跳过")
    void shouldSkipTooShortInterval() {
        intervalTask.setIntervalSeconds(5);

        service.registerSchedule(intervalTask);

        assertTrue(scheduledFutures().isEmpty(), "间隔小于 10 秒不应注册调度");
        assertLogged(ch.qos.logback.classic.Level.WARN, "间隔调度时间过短");
    }

    @Test
    @DisplayName("注册调度 — MANUAL 模式不注册调度")
    void shouldSkipManualScheduleType() {
        service.registerSchedule(manualTask);

        assertTrue(scheduledFutures().isEmpty(), "MANUAL 模式不应注册调度");
        verify(syncService, never()).executeSyncTask(any());
    }

    @Test
    @DisplayName("注册调度 — 同一任务重复注册应先注销旧调度")
    void shouldUnregisterBeforeReregister() {
        service.registerSchedule(cronTask);
        ScheduledFuture<?> firstFuture = scheduledFutures().get(cronTask.getId());
        assertNotNull(firstFuture, "首次注册应生成调度句柄");

        service.registerSchedule(cronTask);

        ScheduledFuture<?> secondFuture = scheduledFutures().get(cronTask.getId());
        assertNotNull(secondFuture, "重复注册后仍应有调度句柄");
        assertNotSame(firstFuture, secondFuture, "重复注册应替换为新的调度句柄");
        assertTrue(firstFuture.isCancelled(), "旧调度句柄应被取消");
        assertEquals(1, scheduledFutures().size(), "重复注册不应残留多个调度句柄");
    }

    // ================================================================
    // unregisterSchedule 方法测试
    // ================================================================

    @Test
    @DisplayName("注销调度 — 已注册的任务正常取消")
    void shouldUnregisterExistingSchedule() {
        service.registerSchedule(cronTask);
        ScheduledFuture<?> future = scheduledFutures().get(cronTask.getId());
        assertNotNull(future);

        service.unregisterSchedule(cronTask.getId());

        assertFalse(scheduledFutures().containsKey(cronTask.getId()), "注销后不应再持有调度句柄");
        assertTrue(future.isCancelled(), "注销应取消调度句柄");
    }

    @Test
    @DisplayName("注销调度 — 未注册的任务不报错且不影响已有调度")
    void shouldHandleUnregisterNonExistent() {
        service.registerSchedule(cronTask);

        service.unregisterSchedule(999L);

        assertFalse(scheduledFutures().containsKey(999L));
        assertTrue(scheduledFutures().containsKey(cronTask.getId()), "注销不存在的任务不应影响已有调度");
    }

    // ================================================================
    // 边界场景
    // ================================================================

    @Test
    @DisplayName("间隔调度 — 秒数为 null 应跳过")
    void shouldSkipNullInterval() {
        intervalTask.setIntervalSeconds(null);

        service.registerSchedule(intervalTask);

        assertTrue(scheduledFutures().isEmpty(), "间隔秒数为 null 不应注册调度");
        assertLogged(ch.qos.logback.classic.Level.WARN, "间隔调度时间过短");
    }

    @Test
    @DisplayName("Cron 调度 — 表达式为 null 应跳过")
    void shouldSkipNullCronExpression() {
        cronTask.setCronExpression(null);

        service.registerSchedule(cronTask);

        assertTrue(scheduledFutures().isEmpty(), "Cron 表达式为 null 不应注册调度");
        assertLogged(ch.qos.logback.classic.Level.WARN, "Cron 表达式为空");
    }

    @Test
    @DisplayName("Cron 调度 — 表达式仅空白字符应跳过")
    void shouldSkipBlankCronExpression() {
        cronTask.setCronExpression("   ");

        service.registerSchedule(cronTask);

        assertTrue(scheduledFutures().isEmpty(), "空白 Cron 表达式不应注册调度");
        assertLogged(ch.qos.logback.classic.Level.WARN, "Cron 表达式为空");
    }
}
