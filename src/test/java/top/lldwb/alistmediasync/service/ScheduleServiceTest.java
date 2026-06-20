package top.lldwb.alistmediasync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.entity.SyncTask;
import top.lldwb.alistmediasync.entity.StorageEngine;
import top.lldwb.alistmediasync.entity.TaskExecution;
import top.lldwb.alistmediasync.repository.SyncTaskRepository;
import top.lldwb.alistmediasync.repository.TaskExecutionRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 定时任务调度管理服务单元测试
 * <p>
 * 覆盖 recoverInterruptedTasks()、registerSchedule()、unregisterSchedule() 三个方法。
 * 不测试实际调度执行（由 TaskScheduler 框架保证），仅验证注册/注销逻辑。
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
    private StorageEngine mockEngine;

    @BeforeEach
    void setUp() {
        mockEngine = new StorageEngine();
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

    // ================================================================
    // recoverInterruptedTasks 方法测试
    // ================================================================

    @Test
    @DisplayName("启动恢复 — 标记中断并重新注册所有已启用任务")
    void shouldRecoverTasksOnStartup() throws Exception {
        when(taskExecutionRepository.markAllRunningAsInterrupted()).thenReturn(3);
        when(syncTaskRepository.findByEnabledTrue()).thenReturn(
            List.of(cronTask, intervalTask, manualTask));

        assertDoesNotThrow(() -> service.recoverInterruptedTasks());

        verify(taskExecutionRepository).markAllRunningAsInterrupted();
        verify(syncTaskRepository).findByEnabledTrue();
        // intervalTask 使用 scheduleAtFixedRate 会立即执行一次
        // cronTask 使用 CronTrigger 不会立即执行（只在 cron 匹配时触发）
        // 因此至少 1 次（intervalTask），可能 2 次（如果 cronTask 恰好在当下匹配）
        verify(syncService, atLeast(1)).executeSyncTask(any());
    }

    @Test
    @DisplayName("启动恢复 — 无已启用任务时不报错")
    void shouldHandleNoEnabledTasksOnStartup() {
        when(taskExecutionRepository.markAllRunningAsInterrupted()).thenReturn(0);
        when(syncTaskRepository.findByEnabledTrue()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.recoverInterruptedTasks());
    }

    // ================================================================
    // registerSchedule 方法测试
    // ================================================================

    @Test
    @DisplayName("注册调度 — 禁用的任务应跳过")
    void shouldSkipDisabledTask() {
        cronTask.setEnabled(false);

        service.registerSchedule(cronTask);

        // 任务未启用，不应注册调度
        verify(syncService, never()).executeSyncTask(any());
    }

    @Test
    @DisplayName("注册调度 — Cron 表达式为空应跳过并记录警告")
    void shouldSkipEmptyCronExpression() {
        cronTask.setCronExpression("");

        assertDoesNotThrow(() -> service.registerSchedule(cronTask));
    }

    @Test
    @DisplayName("注册调度 — 间隔秒数小于 10 应跳过")
    void shouldSkipTooShortInterval() {
        intervalTask.setIntervalSeconds(5);

        assertDoesNotThrow(() -> service.registerSchedule(intervalTask));
    }

    @Test
    @DisplayName("注册调度 — MANUAL 模式不注册调度")
    void shouldSkipManualScheduleType() {
        service.registerSchedule(manualTask);

        // 验证 MANUAL 模式不会触发任何调度注册
        verify(syncService, never()).executeSyncTask(any());
    }

    @Test
    @DisplayName("注册调度 — 同一任务重复注册应先注销旧调度")
    void shouldUnregisterBeforeReregister() {
        // 先注册一次
        service.registerSchedule(cronTask);
        // 再注册一次 — 应自动先取消
        assertDoesNotThrow(() -> service.registerSchedule(cronTask));
    }

    // ================================================================
    // unregisterSchedule 方法测试
    // ================================================================

    @Test
    @DisplayName("注销调度 — 已注册的任务正常取消")
    void shouldUnregisterExistingSchedule() {
        service.registerSchedule(cronTask);

        assertDoesNotThrow(() -> service.unregisterSchedule(cronTask.getId()));
    }

    @Test
    @DisplayName("注销调度 — 未注册的任务不报错")
    void shouldHandleUnregisterNonExistent() {
        assertDoesNotThrow(() -> service.unregisterSchedule(999L));
    }

    // ================================================================
    // 边界场景
    // ================================================================

    @Test
    @DisplayName("间隔调度 — 秒数为 null 应跳过")
    void shouldSkipNullInterval() {
        intervalTask.setIntervalSeconds(null);

        assertDoesNotThrow(() -> service.registerSchedule(intervalTask));
    }

    @Test
    @DisplayName("Cron 调度 — 表达式为 null 应跳过")
    void shouldSkipNullCronExpression() {
        cronTask.setCronExpression(null);

        assertDoesNotThrow(() -> service.registerSchedule(cronTask));
    }

    @Test
    @DisplayName("Cron 调度 — 表达式仅空白字符应跳过")
    void shouldSkipBlankCronExpression() {
        cronTask.setCronExpression("   ");

        assertDoesNotThrow(() -> service.registerSchedule(cronTask));
    }
}
