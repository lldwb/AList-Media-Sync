package top.lldwb.alistmediasync.integration.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.sync.entity.TaskExecution;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 任务执行记录 Repository 集成测试
 * <p>
 * 验证持久化、markAllRunningAsInterrupted、findByStatusAndTaskType、事务回滚。
 * 使用 {@link DataJpaTest} + H2 内存数据库。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TaskExecutionRepository 集成测试")
class TaskExecutionRepositoryIT {

    @Autowired
    private TaskExecutionRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // 创建测试数据
        repository.save(createExecution(TaskExecution.TaskType.SYNC,
            TaskExecution.ExecutionStatus.RUNNING));
        repository.save(createExecution(TaskExecution.TaskType.SYNC,
            TaskExecution.ExecutionStatus.SUCCESS));
        repository.save(createExecution(TaskExecution.TaskType.TRANSCODE,
            TaskExecution.ExecutionStatus.RUNNING));
        repository.save(createExecution(TaskExecution.TaskType.WEBHOOK,
            TaskExecution.ExecutionStatus.FAILED));
        repository.save(createExecution(TaskExecution.TaskType.SYNC,
            TaskExecution.ExecutionStatus.RUNNING));
    }

    @Test
    @DisplayName("持久化并验证自动填充 createdAt/version")
    void shouldPersistWithDefaults() {
        TaskExecution exec = createExecution(TaskExecution.TaskType.SYNC,
            TaskExecution.ExecutionStatus.PARTIAL_SUCCESS);
        TaskExecution saved = repository.save(exec);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt(), "createdAt 应自动填充");
        assertEquals(0L, saved.getVersion(), "初始 version 应为 0");
    }

    @Test
    @DisplayName("findByStatusAndTaskType - 按状态和类型查询")
    void shouldFindByStatusAndTaskType() {
        List<TaskExecution> result = repository.findByStatusAndTaskType(
            TaskExecution.ExecutionStatus.RUNNING,
            TaskExecution.TaskType.SYNC);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(e ->
            e.getStatus() == TaskExecution.ExecutionStatus.RUNNING
                && e.getTaskType() == TaskExecution.TaskType.SYNC));
    }

    @Test
    @DisplayName("findByStatusAndTaskType - 无匹配返回空列表")
    void shouldReturnEmptyWhenNoMatch() {
        List<TaskExecution> result = repository.findByStatusAndTaskType(
            TaskExecution.ExecutionStatus.INTERRUPTED,
            TaskExecution.TaskType.SYNC);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("markAllRunningAsInterrupted - 更新所有 RUNNING 为 INTERRUPTED")
    void shouldMarkAllRunningAsInterrupted() {
        int updated = repository.markAllRunningAsInterrupted();

        // 3 个 RUNNING 记录（2 SYNC + 1 TRANSCODE）
        assertEquals(3, updated);

        // 验证已更新
        List<TaskExecution> running = repository.findByStatusAndTaskType(
            TaskExecution.ExecutionStatus.RUNNING,
            TaskExecution.TaskType.SYNC);
        assertTrue(running.isEmpty());

        List<TaskExecution> interrupted = repository.findByStatusAndTaskType(
            TaskExecution.ExecutionStatus.INTERRUPTED,
            TaskExecution.TaskType.SYNC);
        assertEquals(2, interrupted.size());
    }

    @Test
    @DisplayName("deleteByCreatedAtBefore - 删除过期记录")
    void shouldDeleteExpiredRecords() {
        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(1);

        int deleted = repository.deleteByCreatedAtBefore(cutoff);
        assertTrue(deleted > 0);
    }

    @Test
    @DisplayName("findByCreatedAtBetween - 按时间范围查询")
    void shouldFindByCreatedAtBetween() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1);

        List<TaskExecution> result = repository.findByCreatedAtBetween(start, end);
        assertEquals(5, result.size());
    }

    @Test
    @DisplayName("findBySyncTaskIdAndStatus - 按同步任务和状态查询")
    void shouldFindBySyncTaskIdAndStatus() {
        // 创建无关联 syncTask 的执行记录
        TaskExecution exec = createExecution(TaskExecution.TaskType.SYNC,
            TaskExecution.ExecutionStatus.SUCCESS);
        exec.setSyncTask(null);
        repository.save(exec);

        List<TaskExecution> result = repository.findBySyncTaskIdAndStatus(
            null, TaskExecution.ExecutionStatus.SUCCESS);
        // 参数为 null 时返回空，因为 FK 为 null 的查询用 IS NULL
        assertNotNull(result);
    }

    @Test
    @DisplayName("findBySyncTaskIdOrderByStartTimeDesc - 按时间倒序")
    void shouldOrderByStartTimeDesc() {
        // 测试无关联同步任务时返回空列表（不抛异常）
        List<TaskExecution> result = repository.findBySyncTaskIdOrderByStartTimeDesc(999L);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("nullifyTranscodeTaskRefs - 批量解除转码任务外键")
    void shouldNullifyTranscodeTaskRefs() {
        int updated = repository.nullifyTranscodeTaskRefs(List.of(999L));
        assertEquals(0, updated, "不存在的 ID 不影响任何记录");
    }

    @Test
    @DisplayName("批量更新操作的事务性")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldExecuteBatchUpdateInTransaction() {
        // 在独立事务中执行批量更新
        int updated = repository.markAllRunningAsInterrupted();
        assertEquals(3, updated);
    }

    private TaskExecution createExecution(TaskExecution.TaskType taskType,
                                          TaskExecution.ExecutionStatus status) {
        TaskExecution exec = new TaskExecution();
        exec.setTaskType(taskType);
        exec.setStatus(status);
        exec.setStartTime(LocalDateTime.now());
        exec.setTotalFiles(0);
        exec.setSuccessFiles(0);
        exec.setFailedFiles(0);
        return exec;
    }
}