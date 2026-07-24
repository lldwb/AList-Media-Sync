package top.lldwb.alistmediasync.integration.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import jakarta.persistence.OptimisticLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.repository.TranscodeTaskRepository;

import jakarta.persistence.EntityManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 转码任务 Repository 集成测试
 * <p>
 * 验证 findByStatus、8 状态转换、deleteByStatusIn、乐观锁。
 * 使用 {@link DataJpaTest} + H2 内存数据库。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TranscodeTaskRepository 集成测试")
class TranscodeTaskRepositoryIT {

    @Autowired
    private TranscodeTaskRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 创建测试数据：每个状态一个任务
        for (TranscodeTask.TranscodeStatus status : TranscodeTask.TranscodeStatus.values()) {
            repository.save(createTask(status, "/source/" + status + ".mp4",
                "/target/" + status + ".mp3"));
        }
    }

    @Test
    @DisplayName("持久化并验证默认值")
    void shouldPersistWithDefaults() {
        TranscodeTask task = createTask(TranscodeTask.TranscodeStatus.PENDING,
            "/source/default.mp4", "/target/default.mp3");
        TranscodeTask saved = repository.save(task);

        assertNotNull(saved.getId());
        assertEquals(TranscodeTask.TranscodeStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getProgress());
        assertEquals(0, saved.getRetryCount());
        assertNotNull(saved.getCreatedAt());
        assertEquals(0L, saved.getVersion());
    }

    @Test
    @DisplayName("findByStatus - 查询指定状态的转码任务")
    void shouldFindByStatus() {
        List<TranscodeTask> result = repository.findByStatus(
            TranscodeTask.TranscodeStatus.PENDING);

        assertEquals(1, result.size());
        assertEquals(TranscodeTask.TranscodeStatus.PENDING, result.get(0).getStatus());
    }

    @Test
    @DisplayName("findByStatusIn - 批量查询多个状态")
    void shouldFindByStatusIn() {
        List<TranscodeTask> result = repository.findByStatusIn(List.of(
            TranscodeTask.TranscodeStatus.PENDING,
            TranscodeTask.TranscodeStatus.DOWNLOADING,
            TranscodeTask.TranscodeStatus.TRANSCODING));

        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("8 状态转换：PENDING -> DOWNLOADING -> TRANSCODING -> COMPLETED")
    void shouldTransitionThroughAllStates() {
        List<TranscodeTask> pending = repository.findByStatus(
            TranscodeTask.TranscodeStatus.PENDING);
        TranscodeTask task = pending.get(0);

        // PENDING -> DOWNLOADING
        task.setStatus(TranscodeTask.TranscodeStatus.DOWNLOADING);
        repository.save(task);
        entityManager.flush();

        // DOWNLOADING -> TRANSCODING
        task.setStatus(TranscodeTask.TranscodeStatus.TRANSCODING);
        task.setProgress(500);
        repository.save(task);
        entityManager.flush();

        // TRANSCODING -> COMPLETED
        task.setStatus(TranscodeTask.TranscodeStatus.COMPLETED);
        task.setProgress(1000);
        repository.save(task);
        entityManager.flush();

        TranscodeTask finalState = repository.findById(task.getId()).orElseThrow();
        assertEquals(TranscodeTask.TranscodeStatus.COMPLETED, finalState.getStatus());
        assertEquals(1000, finalState.getProgress());
    }

    @Test
    @DisplayName("8 状态转换：DOWNLOADING -> DOWNLOAD_FAILED（失败路径）")
    void shouldTransitionToDownloadFailed() {
        TranscodeTask task = repository.findByStatus(
            TranscodeTask.TranscodeStatus.DOWNLOADING).get(0);

        task.setStatus(TranscodeTask.TranscodeStatus.DOWNLOAD_FAILED);
        task.setErrorMessage("下载超时");
        repository.save(task);

        TranscodeTask updated = repository.findById(task.getId()).orElseThrow();
        assertEquals(TranscodeTask.TranscodeStatus.DOWNLOAD_FAILED, updated.getStatus());
        assertEquals("下载超时", updated.getErrorMessage());
    }

    @Test
    @DisplayName("8 状态转换：TRANSCODING -> TRANSCODE_FAILED（失败路径）")
    void shouldTransitionToTranscodeFailed() {
        TranscodeTask task = repository.findByStatus(
            TranscodeTask.TranscodeStatus.TRANSCODING).get(0);

        task.setStatus(TranscodeTask.TranscodeStatus.TRANSCODE_FAILED);
        task.setErrorMessage("FFmpeg 错误");
        repository.save(task);

        TranscodeTask updated = repository.findById(task.getId()).orElseThrow();
        assertEquals(TranscodeTask.TranscodeStatus.TRANSCODE_FAILED, updated.getStatus());
    }

    @Test
    @DisplayName("8 状态转换：UPLOADING -> UPLOAD_FAILED（失败路径）")
    void shouldTransitionToUploadFailed() {
        TranscodeTask task = repository.findByStatus(
            TranscodeTask.TranscodeStatus.UPLOADING).get(0);

        task.setStatus(TranscodeTask.TranscodeStatus.UPLOAD_FAILED);
        task.setErrorMessage("上传失败：网络错误");
        task.setRetryCount(1);
        repository.save(task);

        TranscodeTask updated = repository.findById(task.getId()).orElseThrow();
        assertEquals(TranscodeTask.TranscodeStatus.UPLOAD_FAILED, updated.getStatus());
        assertEquals(1, updated.getRetryCount());
    }

    @Test
    @DisplayName("deleteByStatusIn - 批量删除指定状态的任务")
    void shouldDeleteByStatusIn() {
        long countBefore = repository.count();
        int deleted = repository.deleteByStatusIn(List.of(
            TranscodeTask.TranscodeStatus.COMPLETED,
            TranscodeTask.TranscodeStatus.DOWNLOAD_FAILED));

        assertEquals(2, deleted);
        long countAfter = repository.count();
        assertEquals(countBefore - 2, countAfter);
    }

    @Test
    @DisplayName("countByStatusIn - 统计指定状态的任务数")
    void shouldCountByStatusIn() {
        long count = repository.countByStatusIn(List.of(
            TranscodeTask.TranscodeStatus.PENDING,
            TranscodeTask.TranscodeStatus.DOWNLOADING));

        assertEquals(2, count);
    }

    @Test
    @DisplayName("findBySyncTaskId - 按同步任务查询")
    void shouldFindBySyncTaskId() {
        List<TranscodeTask> result = repository.findBySyncTaskId(999L);
        assertTrue(result.isEmpty(), "不存在的同步任务应返回空列表");
    }

    @Test
    @DisplayName("乐观锁冲突检测")
    void shouldDetectOptimisticLockConflict() {
        TranscodeTask task = repository.findByStatus(
            TranscodeTask.TranscodeStatus.PENDING).get(0);
        entityManager.flush();
        Long id = task.getId();

        // 加载托管实体，Hibernate 记录 loaded state 中 version=0
        TranscodeTask t2 = repository.findById(id).orElseThrow();

        // 用原生 JDBC 直接递增数据库 version，模拟另一事务已提交
        // jdbcTemplate 绕过 Hibernate 持久化上下文，t2 的 loaded state 仍为旧值 0
        jdbcTemplate.update("UPDATE transcode_task SET version = version + 1 WHERE id = ?", id);

        // 修改 t2 字段使其成为 dirty，flush 时触发版本检查
        t2.setStatus(TranscodeTask.TranscodeStatus.TRANSCODING);

        // flush 时 Hibernate 用 loaded state version=0 做 WHERE 条件
        // 数据库 version 已被 JDBC 改为 1，不匹配，抛出 OptimisticLockException
        assertThrows(OptimisticLockException.class, () -> {
            repository.save(t2);
            entityManager.flush();
        }, "version 冲突时应抛出 OptimisticLockException");
    }

    @Test
    @DisplayName("批量更新 progress 字段")
    void shouldUpdateProgress() {
        TranscodeTask task = repository.findByStatus(
            TranscodeTask.TranscodeStatus.TRANSCODING).get(0);

        task.setProgress(750);
        repository.save(task);

        TranscodeTask updated = repository.findById(task.getId()).orElseThrow();
        assertEquals(750, updated.getProgress());
    }

    @Test
    @DisplayName("更新 errorMessage 和 retryCount")
    void shouldUpdateErrorMessageAndRetryCount() {
        TranscodeTask task = repository.findByStatus(
            TranscodeTask.TranscodeStatus.DOWNLOAD_FAILED).get(0);

        task.setRetryCount(2);
        task.setErrorMessage("重试后仍失败");
        repository.save(task);

        TranscodeTask updated = repository.findById(task.getId()).orElseThrow();
        assertEquals(2, updated.getRetryCount());
        assertEquals("重试后仍失败", updated.getErrorMessage());
    }

    private TranscodeTask createTask(TranscodeTask.TranscodeStatus status,
                                     String sourcePath, String targetPath) {
        TranscodeTask task = new TranscodeTask();
        task.setSourceFilePath(sourcePath);
        task.setTargetFilePath(targetPath);
        task.setSourceFormat(TranscodeTask.SourceFormat.FLV);
        task.setTargetFormat(TranscodeTask.TargetFormat.MP3);
        task.setStatus(status);
        task.setTargetEngineId(1L);
        return task;
    }
}