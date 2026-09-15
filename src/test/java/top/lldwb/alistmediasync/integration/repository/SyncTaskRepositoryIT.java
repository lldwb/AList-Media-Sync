package top.lldwb.alistmediasync.integration.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import top.lldwb.alistmediasync.common.enums.ConflictStrategy;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.repository.SyncTaskRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 同步任务 Repository 集成测试
 * <p>
 * 验证持久化、findByEnabledTrue、findBySyncMode、findByTargetPath、状态转换。
 * 使用 {@link DataJpaTest} + H2 内存数据库。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("SyncTaskRepository 集成测试")
class SyncTaskRepositoryIT {

    @Autowired
    private SyncTaskRepository repository;

    @Autowired
    private StorageEngineRepository engineRepository;

    private StorageEngine sourceEngine;
    private StorageEngine targetEngine;

    @BeforeEach
    void setUp() {
        sourceEngine = new StorageEngine();
        sourceEngine.setName("源引擎");
        sourceEngine.setEngineType(StorageEngine.EngineType.ALIST);
        sourceEngine.setBaseUrl("https://source.example.com");
        sourceEngine.setEncryptedToken("source-token");
        sourceEngine.setStatus(StorageEngine.EngineStatus.ONLINE);
        engineRepository.save(sourceEngine);

        targetEngine = new StorageEngine();
        targetEngine.setName("目标引擎");
        targetEngine.setEngineType(StorageEngine.EngineType.ALIST);
        targetEngine.setBaseUrl("https://target.example.com");
        targetEngine.setEncryptedToken("target-token");
        targetEngine.setStatus(StorageEngine.EngineStatus.ONLINE);
        engineRepository.save(targetEngine);
    }

    @Test
    @DisplayName("保存同步任务并验证关联和默认值")
    void shouldPersistSyncTaskWithDefaults() {
        SyncTask task = createTask("测试同步", "/source", "/target",
            SyncTask.SyncMode.NEW_ONLY, false);

        SyncTask saved = repository.save(task);

        assertNotNull(saved.getId());
        assertEquals("测试同步", saved.getName());
        assertEquals(SyncTask.SyncMode.NEW_ONLY, saved.getSyncMode());
        assertFalse(saved.getEnabled());
        assertEquals(SyncTask.ScheduleType.MANUAL, saved.getScheduleType());
        assertEquals(ConflictStrategy.SKIP, saved.getConflictStrategy());
        assertNotNull(saved.getCreatedAt());
        assertEquals(0L, saved.getVersion());
    }

    @Test
    @DisplayName("findByEnabledTrue - 查询已启用的任务")
    void shouldFindEnabledTasks() {
        repository.save(createTask("禁用任务", "/s1", "/t1",
            SyncTask.SyncMode.NEW_ONLY, false));
        repository.save(createTask("启用任务1", "/s2", "/t2",
            SyncTask.SyncMode.FULL, true));
        repository.save(createTask("启用任务2", "/s3", "/t3",
            SyncTask.SyncMode.MOVE, true));

        List<SyncTask> result = repository.findByEnabledTrue();

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(SyncTask::getEnabled));
    }

    @Test
    @DisplayName("findBySyncMode - 按同步模式查询")
    void shouldFindBySyncMode() {
        repository.save(createTask("任务1", "/s1", "/t1",
            SyncTask.SyncMode.NEW_ONLY, true));
        repository.save(createTask("任务2", "/s2", "/t2",
            SyncTask.SyncMode.FULL, true));
        repository.save(createTask("任务3", "/s3", "/t3",
            SyncTask.SyncMode.MOVE, true));
        repository.save(createTask("任务4", "/s4", "/t4",
            SyncTask.SyncMode.FULL, true));

        List<SyncTask> fullTasks = repository.findBySyncMode(SyncTask.SyncMode.FULL);
        assertEquals(2, fullTasks.size());
        assertTrue(fullTasks.stream().allMatch(t -> t.getSyncMode() == SyncTask.SyncMode.FULL));
    }

    @Test
    @DisplayName("findByTargetPath - 按目标路径查询")
    void shouldFindByTargetPath() {
        repository.save(createTask("任务1", "/source1", "/target/path",
            SyncTask.SyncMode.NEW_ONLY, true));
        repository.save(createTask("任务2", "/source2", "/target/path",
            SyncTask.SyncMode.FULL, true));
        repository.save(createTask("任务3", "/source3", "/other/path",
            SyncTask.SyncMode.MOVE, true));

        List<SyncTask> result = repository.findByTargetPath("/target/path");
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(t -> "/target/path".equals(t.getTargetPath())));
    }

    @Test
    @DisplayName("状态转换 - 从 NEW_ONLY 到 FULL")
    void shouldTransitionSyncMode() {
        SyncTask task = repository.save(createTask("状态测试", "/s", "/t",
            SyncTask.SyncMode.NEW_ONLY, true));

        task.setSyncMode(SyncTask.SyncMode.FULL);
        repository.save(task);

        SyncTask updated = repository.findById(task.getId()).orElseThrow();
        assertEquals(SyncTask.SyncMode.FULL, updated.getSyncMode());
    }

    @Test
    @DisplayName("启用/禁用切换")
    void shouldToggleEnabled() {
        SyncTask task = repository.save(createTask("启用切换", "/s", "/t",
            SyncTask.SyncMode.NEW_ONLY, false));

        task.setEnabled(true);
        repository.save(task);

        SyncTask updated = repository.findById(task.getId()).orElseThrow();
        assertTrue(updated.getEnabled());
        List<SyncTask> enabled = repository.findByEnabledTrue();
        assertEquals(1, enabled.size());
    }

    @Test
    @DisplayName("持久化 ScheduleType 枚举")
    void shouldPersistScheduleType() {
        SyncTask task = createTask("Cron任务", "/s", "/t",
            SyncTask.SyncMode.NEW_ONLY, true);
        task.setScheduleType(SyncTask.ScheduleType.CRON);
        task.setCronExpression("0 0 * * * *");

        SyncTask saved = repository.save(task);
        assertEquals(SyncTask.ScheduleType.CRON, saved.getScheduleType());
        assertEquals("0 0 * * * *", saved.getCronExpression());
    }

    @Test
    @DisplayName("持久化 ConflictStrategy 枚举")
    void shouldPersistConflictStrategy() {
        SyncTask task = createTask("覆盖策略", "/s", "/t",
            SyncTask.SyncMode.NEW_ONLY, true);
        task.setConflictStrategy(ConflictStrategy.OVERWRITE);

        SyncTask saved = repository.save(task);
        assertEquals(ConflictStrategy.OVERWRITE, saved.getConflictStrategy());
    }

    private SyncTask createTask(String name, String sourcePath, String targetPath,
                                SyncTask.SyncMode mode, boolean enabled) {
        SyncTask task = new SyncTask();
        task.setName(name);
        task.setSourceEngine(sourceEngine);
        task.setTargetEngine(targetEngine);
        task.setSourcePath(sourcePath);
        task.setTargetPath(targetPath);
        task.setSyncMode(mode);
        task.setEnabled(enabled);
        return task;
    }
}