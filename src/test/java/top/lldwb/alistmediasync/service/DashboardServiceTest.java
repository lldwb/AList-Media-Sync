package top.lldwb.alistmediasync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.dto.DashboardStatsVO;
import top.lldwb.alistmediasync.entity.TaskExecution;
import top.lldwb.alistmediasync.entity.TranscodeTask;
import top.lldwb.alistmediasync.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.repository.TaskExecutionRepository;
import top.lldwb.alistmediasync.repository.TranscodeTaskRepository;
import top.lldwb.alistmediasync.repository.WebhookRuleRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 仪表板聚合统计查询服务单元测试
 * <p>
 * 覆盖 getStats() 方法的正常、边界和空数据场景。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("仪表板统计服务测试")
class DashboardServiceTest {

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private TranscodeTaskRepository transcodeTaskRepository;

    @Mock
    private StorageEngineRepository storageEngineRepository;

    @Mock
    private WebhookRuleRepository webhookRuleRepository;

    @InjectMocks
    private DashboardService service;

    @BeforeEach
    void setUp() {
        // 默认 Mock 空数据
        when(taskExecutionRepository.findByStatusAndTaskType(
            TaskExecution.ExecutionStatus.RUNNING, TaskExecution.TaskType.SYNC))
            .thenReturn(List.of());
        when(transcodeTaskRepository.findByStatus(any()))
            .thenReturn(List.of());
        when(taskExecutionRepository.findByCreatedAtBetween(any(), any()))
            .thenReturn(List.of());
        when(storageEngineRepository.count()).thenReturn(0L);
        when(webhookRuleRepository.count()).thenReturn(0L);
    }

    // ================================================================
    // 正常场景
    // ================================================================

    @Test
    @DisplayName("有活跃任务时应返回正确统计")
    void shouldReturnCorrectStatsWithActiveTasks() {
        // 模拟 2 个运行中的同步任务
        TaskExecution running1 = createExecution(TaskExecution.ExecutionStatus.RUNNING,
            TaskExecution.TaskType.SYNC, 50, 30, 5);
        TaskExecution running2 = createExecution(TaskExecution.ExecutionStatus.RUNNING,
            TaskExecution.TaskType.SYNC, 20, 10, 0);
        when(taskExecutionRepository.findByStatusAndTaskType(
            TaskExecution.ExecutionStatus.RUNNING, TaskExecution.TaskType.SYNC))
            .thenReturn(List.of(running1, running2));

        // 模拟 3 个等待中的转码任务
        TranscodeTask pending = new TranscodeTask();
        pending.setStatus(TranscodeTask.TranscodeStatus.PENDING);
        when(transcodeTaskRepository.findByStatus(TranscodeTask.TranscodeStatus.PENDING))
            .thenReturn(List.of(pending, pending, pending));

        // 模拟 24h 内处理了 150 个文件，成功 145
        TaskExecution done = createExecution(TaskExecution.ExecutionStatus.SUCCESS,
            TaskExecution.TaskType.SYNC, 100, 100, 0);
        TaskExecution partial = createExecution(TaskExecution.ExecutionStatus.PARTIAL_SUCCESS,
            TaskExecution.TaskType.TRANSCODE, 50, 45, 5);
        when(taskExecutionRepository.findByCreatedAtBetween(any(), any()))
            .thenReturn(List.of(done, partial));

        when(storageEngineRepository.count()).thenReturn(5L);
        when(webhookRuleRepository.count()).thenReturn(4L);

        DashboardStatsVO result = service.getStats();

        assertEquals(2L, result.activeSyncTasks());
        assertEquals(3L, result.pendingTranscodeTasks());
        assertEquals(145L, result.todayProcessedFiles());
        // 145/150 ≈ 96.7%
        assertTrue(result.last24hSuccessRate() >= 96.0 && result.last24hSuccessRate() <= 97.0);
        assertEquals(5L, result.totalEngines());
        assertEquals(4L, result.totalWebhookRules());
    }

    // ================================================================
    // 空数据场景
    // ================================================================

    @Test
    @DisplayName("系统空闲时应返回全零默认值")
    void shouldReturnDefaultsWhenIdle() {
        DashboardStatsVO result = service.getStats();

        assertEquals(0L, result.activeSyncTasks());
        assertEquals(0L, result.pendingTranscodeTasks());
        assertEquals(0L, result.todayProcessedFiles());
        assertEquals(100.0, result.last24hSuccessRate(), "无执行记录时应显示 100%");
        assertEquals(0L, result.totalEngines());
        assertEquals(0L, result.totalWebhookRules());
    }

    // ================================================================
    // 边界场景
    // ================================================================

    @Test
    @DisplayName("全部文件成功时成功率应为 100%")
    void shouldReturn100PercentWhenAllSuccess() {
        TaskExecution allSuccess = createExecution(TaskExecution.ExecutionStatus.SUCCESS,
            TaskExecution.TaskType.SYNC, 200, 200, 0);
        when(taskExecutionRepository.findByCreatedAtBetween(any(), any()))
            .thenReturn(List.of(allSuccess));

        DashboardStatsVO result = service.getStats();

        assertEquals(100.0, result.last24hSuccessRate());
        assertEquals(200L, result.todayProcessedFiles());
    }

    @Test
    @DisplayName("全部文件失败时成功率应为 0%")
    void shouldReturn0PercentWhenAllFailed() {
        TaskExecution allFailed = createExecution(TaskExecution.ExecutionStatus.FAILED,
            TaskExecution.TaskType.SYNC, 50, 0, 50);
        when(taskExecutionRepository.findByCreatedAtBetween(any(), any()))
            .thenReturn(List.of(allFailed));

        DashboardStatsVO result = service.getStats();

        assertEquals(0.0, result.last24hSuccessRate());
    }

    @Test
    @DisplayName("转码任务包含多种非完成状态时应正确汇总")
    void shouldAggregateAllPendingTranscodeStatuses() {
        when(transcodeTaskRepository.findByStatus(TranscodeTask.TranscodeStatus.PENDING))
            .thenReturn(List.of(new TranscodeTask()));
        when(transcodeTaskRepository.findByStatus(TranscodeTask.TranscodeStatus.SCANNING))
            .thenReturn(List.of(new TranscodeTask(), new TranscodeTask()));
        when(transcodeTaskRepository.findByStatus(TranscodeTask.TranscodeStatus.TRANSCODING))
            .thenReturn(List.of(new TranscodeTask()));
        when(transcodeTaskRepository.findByStatus(TranscodeTask.TranscodeStatus.UPLOADING))
            .thenReturn(List.of(new TranscodeTask(), new TranscodeTask(), new TranscodeTask()));

        DashboardStatsVO result = service.getStats();

        // 1 + 2 + 1 + 3 = 7
        assertEquals(7L, result.pendingTranscodeTasks());
    }

    @Test
    @DisplayName("执行记录中 totalFiles 为 null 时不抛 NPE")
    void shouldHandleNullTotalFiles() {
        TaskExecution execWithNulls = new TaskExecution();
        execWithNulls.setStatus(TaskExecution.ExecutionStatus.SUCCESS);
        execWithNulls.setTaskType(TaskExecution.TaskType.SYNC);
        execWithNulls.setTotalFiles(null);
        execWithNulls.setSuccessFiles(null);
        execWithNulls.setFailedFiles(null);
        when(taskExecutionRepository.findByCreatedAtBetween(any(), any()))
            .thenReturn(List.of(execWithNulls));

        DashboardStatsVO result = service.getStats();

        assertEquals(0L, result.todayProcessedFiles());
        assertEquals(100.0, result.last24hSuccessRate()); // totalFiles=0 走默认值
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private TaskExecution createExecution(TaskExecution.ExecutionStatus status,
                                           TaskExecution.TaskType type,
                                           int total, int success, int failed) {
        TaskExecution exec = new TaskExecution();
        exec.setStatus(status);
        exec.setTaskType(type);
        exec.setTotalFiles(total);
        exec.setSuccessFiles(success);
        exec.setFailedFiles(failed);
        exec.setStartTime(LocalDateTime.now().minusHours(1));
        exec.setCreatedAt(LocalDateTime.now().minusHours(1));
        return exec;
    }
}
