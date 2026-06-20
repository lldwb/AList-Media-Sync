package top.lldwb.alistmediasync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.dto.sync.SyncTaskCreateDTO;
import top.lldwb.alistmediasync.dto.sync.SyncTaskUpdateDTO;
import top.lldwb.alistmediasync.dto.sync.SyncTaskVO;
import top.lldwb.alistmediasync.dto.sync.TaskExecutionVO;
import top.lldwb.alistmediasync.entity.SyncTask;
import top.lldwb.alistmediasync.entity.StorageEngine;
import top.lldwb.alistmediasync.entity.TaskExecution;
import top.lldwb.alistmediasync.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.repository.SyncTaskRepository;
import top.lldwb.alistmediasync.repository.TaskExecutionRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 同步任务 CRUD 管理服务单元测试
 * <p>
 * 覆盖所有 10 个 public 方法（create、update、delete、getById、listAll、
 * enable、disable、executeManually、getEntity、getExecutions）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("同步任务管理服务测试")
class SyncTaskManageServiceTest {

    @Mock
    private SyncTaskRepository repository;

    @Mock
    private StorageEngineRepository storageEngineRepository;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private ScheduleService scheduleService;

    @InjectMocks
    private SyncTaskManageService service;

    private SyncTaskCreateDTO createDTO;
    private SyncTaskUpdateDTO updateDTO;
    private SyncTask mockTask;
    private StorageEngine mockSourceEngine;
    private StorageEngine mockTargetEngine;

    @BeforeEach
    void setUp() {
        mockSourceEngine = new StorageEngine();
        mockSourceEngine.setId(1L);
        mockSourceEngine.setName("源引擎");

        mockTargetEngine = new StorageEngine();
        mockTargetEngine.setId(2L);
        mockTargetEngine.setName("目标引擎");

        createDTO = new SyncTaskCreateDTO();
        createDTO.setName("测试同步任务");
        createDTO.setSourceEngineId(1L);
        createDTO.setTargetEngineId(2L);
        createDTO.setSourcePath("/source");
        createDTO.setTargetPath("/target");
        createDTO.setSyncMode(SyncTask.SyncMode.NEW_ONLY);
        createDTO.setScheduleType(SyncTask.ScheduleType.MANUAL);
        createDTO.setConflictStrategy(SyncTask.ConflictStrategy.SKIP);
        createDTO.setTranscodeEnabled(false);

        updateDTO = new SyncTaskUpdateDTO();
        updateDTO.setName("更新后的任务名称");

        mockTask = new SyncTask();
        mockTask.setId(1L);
        mockTask.setName("测试同步任务");
        mockTask.setSourceEngine(mockSourceEngine);
        mockTask.setTargetEngine(mockTargetEngine);
        mockTask.setSourcePath("/source");
        mockTask.setTargetPath("/target");
        mockTask.setSyncMode(SyncTask.SyncMode.NEW_ONLY);
        mockTask.setScheduleType(SyncTask.ScheduleType.MANUAL);
        mockTask.setConflictStrategy(SyncTask.ConflictStrategy.SKIP);
        mockTask.setTranscodeEnabled(false);
        mockTask.setEnabled(false);
    }

    // ================================================================
    // create 方法测试
    // ================================================================

    @Test
    @DisplayName("创建任务 — 正常流程应返回 VO，创建后默认禁用")
    void shouldCreateTaskAsDisabled() {
        when(storageEngineRepository.getReferenceById(1L)).thenReturn(mockSourceEngine);
        when(storageEngineRepository.getReferenceById(2L)).thenReturn(mockTargetEngine);
        when(repository.save(any(SyncTask.class))).thenAnswer(inv -> {
            SyncTask saved = inv.getArgument(0);
            assertFalse(saved.getEnabled(), "创建后应默认禁用");
            saved.setId(1L);
            return saved;
        });

        SyncTaskVO result = service.create(createDTO);

        assertNotNull(result);
        assertEquals("测试同步任务", result.getName());
        verify(repository).save(any(SyncTask.class));
    }

    // ================================================================
    // update 方法测试
    // ================================================================

    @Test
    @DisplayName("更新任务 — 正常更新字段")
    void shouldUpdateTask() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));
        when(repository.save(any(SyncTask.class))).thenReturn(mockTask);

        SyncTaskVO result = service.update(1L, updateDTO);

        assertNotNull(result);
        assertEquals("更新后的任务名称", mockTask.getName());
        verify(repository).save(mockTask);
    }

    @Test
    @DisplayName("更新任务 — 任务不存在应抛出 NoSuchElementException")
    void shouldThrowWhenUpdatingNonExistentTask() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.update(999L, updateDTO));
    }

    // ================================================================
    // delete 方法测试
    // ================================================================

    @Test
    @DisplayName("删除任务 — 先取消调度再删除")
    void shouldCancelScheduleBeforeDelete() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));

        service.delete(1L);

        verify(scheduleService).unregisterSchedule(1L);
        verify(repository).delete(mockTask);
    }

    @Test
    @DisplayName("删除任务 — 任务不存在应抛出 NoSuchElementException")
    void shouldThrowWhenDeletingNonExistentTask() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.delete(999L));
        verify(repository, never()).delete(any());
    }

    // ================================================================
    // getById 方法测试
    // ================================================================

    @Test
    @DisplayName("按 ID 查询 — 正常返回 VO")
    void shouldGetById() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));

        SyncTaskVO result = service.getById(1L);

        assertNotNull(result);
        assertEquals("测试同步任务", result.getName());
    }

    @Test
    @DisplayName("按 ID 查询 — 不存在应抛出 NoSuchElementException")
    void shouldThrowWhenGetByIdNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getById(999L));
    }

    // ================================================================
    // listAll 方法测试
    // ================================================================

    @Test
    @DisplayName("查询全部 — 返回所有任务列表")
    void shouldListAll() {
        SyncTask task2 = new SyncTask();
        task2.setId(2L);
        task2.setName("任务2");
        task2.setSourceEngine(mockSourceEngine);
        task2.setTargetEngine(mockTargetEngine);
        task2.setSyncMode(SyncTask.SyncMode.FULL_SYNC);
        task2.setEnabled(true);

        when(repository.findAll()).thenReturn(List.of(mockTask, task2));

        List<SyncTaskVO> result = service.listAll();

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("查询全部 — 无数据时返回空列表")
    void shouldReturnEmptyList() {
        when(repository.findAll()).thenReturn(List.of());

        List<SyncTaskVO> result = service.listAll();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ================================================================
    // enable 方法测试
    // ================================================================

    @Test
    @DisplayName("启用任务 — 设置 enabled=true 并注册调度")
    void shouldEnableTaskAndRegisterSchedule() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));
        when(repository.save(any(SyncTask.class))).thenReturn(mockTask);

        SyncTaskVO result = service.enable(1L);

        assertNotNull(result);
        assertTrue(mockTask.getEnabled());
        verify(scheduleService).registerSchedule(mockTask);
    }

    @Test
    @DisplayName("启用任务 — 任务不存在应抛出异常")
    void shouldThrowWhenEnablingNonExistentTask() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.enable(999L));
    }

    // ================================================================
    // disable 方法测试
    // ================================================================

    @Test
    @DisplayName("禁用任务 — 设置 enabled=false 并取消调度")
    void shouldDisableTaskAndCancelSchedule() {
        mockTask.setEnabled(true); // 初始启用
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));
        when(repository.save(any(SyncTask.class))).thenReturn(mockTask);

        SyncTaskVO result = service.disable(1L);

        assertNotNull(result);
        assertFalse(mockTask.getEnabled());
        verify(scheduleService).unregisterSchedule(1L);
    }

    @Test
    @DisplayName("禁用任务 — 任务不存在应抛出异常")
    void shouldThrowWhenDisablingNonExistentTask() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.disable(999L));
    }

    // ================================================================
    // executeManually 方法测试
    // ================================================================

    @Test
    @DisplayName("手动触发 — 无运行中执行记录时应正常通过")
    void shouldAllowManualExecutionWhenNoRunning() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));
        when(taskExecutionRepository.findBySyncTaskIdAndStatus(
            1L, TaskExecution.ExecutionStatus.RUNNING))
            .thenReturn(List.of());

        assertDoesNotThrow(() -> service.executeManually(1L));
    }

    @Test
    @DisplayName("手动触发 — 有运行中执行记录时应抛出 IllegalStateException")
    void shouldRejectManualExecutionWhenRunning() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));
        TaskExecution runningExecution = new TaskExecution();
        runningExecution.setId(100L);
        runningExecution.setStatus(TaskExecution.ExecutionStatus.RUNNING);
        when(taskExecutionRepository.findBySyncTaskIdAndStatus(
            1L, TaskExecution.ExecutionStatus.RUNNING))
            .thenReturn(List.of(runningExecution));

        assertThrows(IllegalStateException.class, () -> service.executeManually(1L));
    }

    @Test
    @DisplayName("手动触发 — 任务不存在应抛出 NoSuchElementException")
    void shouldThrowWhenManuallyExecutingNonExistentTask() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.executeManually(999L));
    }

    // ================================================================
    // getEntity 方法测试
    // ================================================================

    @Test
    @DisplayName("获取实体 — 正常返回 Entity")
    void shouldGetEntity() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));

        SyncTask result = service.getEntity(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("获取实体 — 不存在应抛出 NoSuchElementException")
    void shouldThrowWhenGetEntityNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getEntity(999L));
    }

    // ================================================================
    // getExecutions 方法测试
    // ================================================================

    @Test
    @DisplayName("查询执行历史 — 正常返回 VO 列表")
    void shouldGetExecutions() {
        TaskExecution exec1 = new TaskExecution();
        exec1.setId(1L);
        exec1.setSyncTask(mockTask);
        exec1.setTaskType(TaskExecution.TaskType.SYNC);
        exec1.setStatus(TaskExecution.ExecutionStatus.SUCCESS);
        exec1.setTotalFiles(10);
        exec1.setSuccessFiles(10);
        exec1.setFailedFiles(0);

        TaskExecution exec2 = new TaskExecution();
        exec2.setId(2L);
        exec2.setSyncTask(mockTask);
        exec2.setTaskType(TaskExecution.TaskType.SYNC);
        exec2.setStatus(TaskExecution.ExecutionStatus.FAILED);
        exec2.setTotalFiles(5);
        exec2.setSuccessFiles(3);
        exec2.setFailedFiles(2);

        when(taskExecutionRepository.findBySyncTaskIdOrderByStartTimeDesc(1L))
            .thenReturn(List.of(exec1, exec2));

        List<TaskExecutionVO> result = service.getExecutions(1L);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("SUCCESS", result.get(0).status());
        assertEquals("FAILED", result.get(1).status());
        assertEquals(10, result.get(0).totalFiles());
        assertEquals(5, result.get(1).totalFiles());
        // 验证关联 ID 正确传递
        assertEquals(1L, result.get(0).syncTaskId());
    }

    @Test
    @DisplayName("查询执行历史 — 无记录时返回空列表")
    void shouldReturnEmptyExecutionsList() {
        when(taskExecutionRepository.findBySyncTaskIdOrderByStartTimeDesc(1L))
            .thenReturn(List.of());

        List<TaskExecutionVO> result = service.getExecutions(1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
