package top.lldwb.alistmediasync.sync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import top.lldwb.alistmediasync.sync.dto.sync.FileEntry;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.sync.entity.TaskExecution;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.sync.repository.SyncTaskRepository;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.transcode.service.TranscodeService;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 同步执行引擎单元测试
 * <p>
 * 覆盖 executeSyncTask() 和 getProgress() 的核心场景。
 * 重构后通过 StorageEngineStrategy 策略模式操作文件，测试通过 Mock 策略链验证逻辑。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("同步执行引擎测试")
class SyncServiceTest {

    @Mock
    private StorageEngineService storageEngineService;

    @Mock
    private SyncTaskRepository syncTaskRepository;

    @Mock
    private StorageEngineRepository storageEngineRepository;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private TranscodeService transcodeService;

    @Mock
    private JsonMapper objectMapper;

    @Mock
    private WsSessionManager wsSessionManager;

    @Mock
    private StorageEngineStrategy sourceStrategy;

    @Mock
    private StorageEngineStrategy targetStrategy;

    @InjectMocks
    private SyncService service;

    private SyncTask syncTask;
    private StorageEngine sourceEngine;
    private StorageEngine targetEngine;

    @BeforeEach
    void setUp() throws Exception {
        sourceEngine = new StorageEngine();
        sourceEngine.setId(1L);
        sourceEngine.setName("源引擎");
        sourceEngine.setBaseUrl("https://source.example.com");
        sourceEngine.setEncryptedToken("source-token");

        targetEngine = new StorageEngine();
        targetEngine.setId(2L);
        targetEngine.setName("目标引擎");
        targetEngine.setBaseUrl("https://target.example.com");
        targetEngine.setEncryptedToken("target-token");

        syncTask = new SyncTask();
        syncTask.setId(1L);
        syncTask.setName("测试同步");
        syncTask.setSourceEngine(sourceEngine);
        syncTask.setTargetEngine(targetEngine);
        syncTask.setSourcePath("/videos");
        syncTask.setTargetPath("/backup");
        syncTask.setSyncMode(SyncTask.SyncMode.NEW_ONLY);
        syncTask.setTranscodeEnabled(false);
        syncTask.setConflictStrategy(SyncTask.ConflictStrategy.SKIP);

        // 默认 Mock：StorageEngineService 返回策略
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(storageEngineService.resolve(targetEngine)).thenReturn(targetStrategy);

        // 默认 Mock：save 返回传入的参数
        when(taskExecutionRepository.save(any(TaskExecution.class))).thenAnswer(inv -> {
            TaskExecution exec = inv.getArgument(0);
            if (exec.getId() == null) exec.setId(100L);
            return exec;
        });
        when(taskExecutionRepository.findByStatusAndTaskType(any(), any()))
            .thenReturn(List.of());
        when(syncTaskRepository.save(any(SyncTask.class))).thenReturn(syncTask);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /** 构建 FileEntry 文件 */
    private FileEntry file(String name, long size) {
        return new FileEntry(name, "/test/" + name, false, size, LocalDateTime.now());
    }

    /** 构建 FileEntry 目录 */
    private FileEntry dir(String name) {
        return new FileEntry(name, "/test/" + name, true, 0, LocalDateTime.now());
    }

    /** Mock 策略返回的 listFiles */
    private void mockSourceFiles(FileEntry... files) {
        when(sourceStrategy.listFiles(eq(sourceEngine), anyString(), anyInt(), eq(100)))
            .thenReturn(List.of(files))
            .thenReturn(List.of()); // 分页结束
    }

    private void mockTargetFiles(FileEntry... files) {
        when(targetStrategy.listFiles(eq(targetEngine), anyString(), anyInt(), eq(100)))
            .thenReturn(List.of(files))
            .thenReturn(List.of());
    }

    /** Mock 空目录 */
    private void mockEmptySource() {
        when(sourceStrategy.listFiles(eq(sourceEngine), anyString(), anyInt(), eq(100)))
            .thenReturn(List.of());
    }

    private void mockEmptyTarget() {
        when(targetStrategy.listFiles(eq(targetEngine), anyString(), anyInt(), eq(100)))
            .thenReturn(List.of());
    }

    // ================================================================
    // executeSyncTask — 正常场景
    // ================================================================

    @Test
    @DisplayName("NEW_ONLY 模式 — 目标为空时全部同步")
    void shouldSyncNewFilesInNewOnlyMode() {
        mockSourceFiles(file("video1.mp4", 1000L), file("video2.mp4", 2000L));
        mockEmptyTarget();

        when(sourceStrategy.downloadFile(eq(sourceEngine), anyString()))
            .thenReturn(new ByteArrayInputStream(new byte[0]));
        doNothing().when(targetStrategy).uploadFile(eq(targetEngine), anyString(), any(InputStream.class), anyLong());

        assertDoesNotThrow(() -> service.executeSyncTask(syncTask));

        // 验证下载了 2 次
        verify(sourceStrategy, times(2)).downloadFile(eq(sourceEngine), anyString());
        verify(targetStrategy, times(2)).uploadFile(eq(targetEngine), anyString(), any(InputStream.class), anyLong());
    }

    @Test
    @DisplayName("NEW_ONLY 模式 — 目标已有同名文件且冲突策略为 SKIP 时应跳过")
    void shouldSkipExistingFilesInNewOnlyMode() {
        mockSourceFiles(file("exists.mp4", 1000L));
        mockTargetFiles(file("exists.mp4", 500L));

        assertDoesNotThrow(() -> service.executeSyncTask(syncTask));

        // 不应下载或上传
        verify(sourceStrategy, never()).downloadFile(any(StorageEngine.class), anyString());
        verify(targetStrategy, never()).uploadFile(any(StorageEngine.class), anyString(), any(InputStream.class), anyLong());
    }

    @Test
    @DisplayName("同步完成后应更新任务的最后执行时间")
    void shouldUpdateLastExecutedAtOnCompletion() {
        mockEmptySource();

        service.executeSyncTask(syncTask);

        verify(syncTaskRepository).save(syncTask);
        assertNotNull(syncTask.getLastExecutedAt());
    }

    @Test
    @DisplayName("启用转码时同步成功后应触发后置转码")
    void shouldTriggerPostSyncTranscodeWhenEnabled() {
        syncTask.setTranscodeEnabled(true);
        mockEmptySource();

        service.executeSyncTask(syncTask);

        verify(transcodeService).executePostSyncTranscode(eq(syncTask), any(TaskExecution.class));
    }

    @Test
    @DisplayName("启用转码但执行不是 SUCCESS 时不触发后置转码")
    void shouldNotTriggerTranscodeWhenExecutionNotSuccess() {
        syncTask.setTranscodeEnabled(true);
        // 源有文件，下载抛出异常
        mockSourceFiles(file("bad.mp4", 500L));
        mockEmptyTarget();
        when(sourceStrategy.downloadFile(eq(sourceEngine), anyString()))
            .thenThrow(new RuntimeException("网络中断"));

        service.executeSyncTask(syncTask);

        verify(transcodeService, never()).executePostSyncTranscode(any(), any());
    }

    // ================================================================
    // executeSyncTask — 异常场景
    // ================================================================

    @Test
    @DisplayName("源目录为空时不报错")
    void shouldHandleEmptySource() {
        mockEmptySource();

        assertDoesNotThrow(() -> service.executeSyncTask(syncTask));

        ArgumentCaptor<TaskExecution> captor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository, atLeast(1)).save(captor.capture());
        // 最终状态应为 SUCCESS
        TaskExecution finalExec = captor.getValue();
        assertNotNull(finalExec.getEndTime());
    }

    @Test
    @DisplayName("下载失败时记录失败文件但继续处理其余文件")
    void shouldContinueAfterDownloadFailure() {
        mockSourceFiles(file("good.mp4", 500L), file("bad.mp4", 500L));
        mockEmptyTarget();

        // 第一个下载成功，第二个抛出异常
        when(sourceStrategy.downloadFile(eq(sourceEngine), contains("good")))
            .thenReturn(new ByteArrayInputStream(new byte[0]));
        when(sourceStrategy.downloadFile(eq(sourceEngine), contains("bad")))
            .thenThrow(new RuntimeException("网络中断"));

        doNothing().when(targetStrategy).uploadFile(eq(targetEngine), anyString(), any(InputStream.class), anyLong());

        assertDoesNotThrow(() -> service.executeSyncTask(syncTask));

        // good.mp4 下载并上传了
        verify(sourceStrategy, atLeast(1)).downloadFile(eq(sourceEngine), contains("good"));
        verify(targetStrategy).uploadFile(eq(targetEngine), contains("good"), any(InputStream.class), anyLong());
        // 最终执行仍被保存
        verify(taskExecutionRepository, atLeast(2)).save(any(TaskExecution.class));
    }

    @Test
    @DisplayName("存在冲突运行中任务时仍正常执行（仅警告）")
    void shouldWarnButContinueWhenConflictingTasksExist() {
        TaskExecution running = new TaskExecution();
        running.setId(99L);
        running.setStatus(TaskExecution.ExecutionStatus.RUNNING);
        when(taskExecutionRepository.findByStatusAndTaskType(
            TaskExecution.ExecutionStatus.RUNNING, TaskExecution.TaskType.SYNC))
            .thenReturn(List.of(running));

        mockEmptySource();

        assertDoesNotThrow(() -> service.executeSyncTask(syncTask));
    }

    // ================================================================
    // 排除规则测试
    // ================================================================

    @Test
    @DisplayName("排除规则 — 排除 .tmp 后缀文件")
    void shouldExcludeTmpFiles() {
        syncTask.setExcludePatterns("*.tmp");
        mockSourceFiles(file("video.mp4", 1000L), file("temp.tmp", 500L));
        mockEmptyTarget();

        when(sourceStrategy.downloadFile(eq(sourceEngine), anyString()))
            .thenReturn(new ByteArrayInputStream(new byte[0]));
        doNothing().when(targetStrategy).uploadFile(eq(targetEngine), anyString(), any(InputStream.class), anyLong());

        service.executeSyncTask(syncTask);

        // 只同步 video.mp4，temp.tmp 被排除
        verify(sourceStrategy, times(1)).downloadFile(eq(sourceEngine), anyString());
    }

    @Test
    @DisplayName("排除规则 — 支持换行分隔多个模式")
    void shouldExcludeMultiplePatterns() {
        syncTask.setExcludePatterns("*.tmp\n*.part\n.DS_Store");
        mockSourceFiles(
            file("video.mp4", 1000L),
            file("temp.tmp", 500L),
            file("file.part", 500L),
            file(".DS_Store", 0L)
        );
        mockEmptyTarget();

        when(sourceStrategy.downloadFile(eq(sourceEngine), anyString()))
            .thenReturn(new ByteArrayInputStream(new byte[0]));
        doNothing().when(targetStrategy).uploadFile(eq(targetEngine), anyString(), any(InputStream.class), anyLong());

        service.executeSyncTask(syncTask);

        // 只有 video.mp4 被同步
        verify(sourceStrategy, times(1)).downloadFile(eq(sourceEngine), anyString());
        verify(sourceStrategy).downloadFile(eq(sourceEngine), contains("video.mp4"));
    }

    // ================================================================
    // 冲突策略测试
    // ================================================================

    @Test
    @DisplayName("冲突策略 OVERWRITE — FULL 模式下覆盖目标已有文件")
    void shouldOverwriteExistingFile() {
        syncTask.setSyncMode(SyncTask.SyncMode.FULL);
        syncTask.setConflictStrategy(SyncTask.ConflictStrategy.OVERWRITE);
        mockSourceFiles(file("video.mp4", 1000L));
        mockTargetFiles(file("video.mp4", 500L));

        when(sourceStrategy.downloadFile(eq(sourceEngine), anyString()))
            .thenReturn(new ByteArrayInputStream(new byte[0]));
        doNothing().when(targetStrategy).uploadFile(eq(targetEngine), anyString(), any(InputStream.class), anyLong());

        service.executeSyncTask(syncTask);

        // FULL 模式 + OVERWRITE：目标已有文件仍会执行同步
        verify(sourceStrategy).downloadFile(eq(sourceEngine), anyString());
        verify(targetStrategy).uploadFile(eq(targetEngine), anyString(), any(InputStream.class), anyLong());
    }

    @Test
    @DisplayName("全量扫描异常时执行状态为 FAILED")
    void shouldMarkExecutionFailedOnScanException() {
        when(sourceStrategy.listFiles(eq(sourceEngine), anyString(), anyInt(), eq(100)))
            .thenThrow(new RuntimeException("扫描失败"));

        service.executeSyncTask(syncTask);

        ArgumentCaptor<TaskExecution> captor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository, atLeast(2)).save(captor.capture());
        // 查找状态为 FAILED 的执行记录
        List<TaskExecution> saved = captor.getAllValues();
        boolean hasFailed = saved.stream()
            .anyMatch(e -> e.getStatus() == TaskExecution.ExecutionStatus.FAILED);
        assertTrue(hasFailed, "应存在 FAILED 状态的执行记录");
    }

    // ================================================================
    // getProgress 方法测试
    // ================================================================

    @Test
    @DisplayName("获取进度 — 不在活跃缓存时从数据库查询")
    void shouldGetProgressFromDatabaseWhenNotCached() {
        TaskExecution dbExec = new TaskExecution();
        dbExec.setId(200L);
        dbExec.setStatus(TaskExecution.ExecutionStatus.SUCCESS);
        when(taskExecutionRepository.findById(200L)).thenReturn(Optional.of(dbExec));

        TaskExecution result = service.getProgress(200L);

        assertNotNull(result);
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, result.getStatus());
        verify(taskExecutionRepository).findById(200L);
    }

    @Test
    @DisplayName("获取进度 — 既不在缓存也不在数据库时返回 null")
    void shouldReturnNullWhenProgressNotFound() {
        when(taskExecutionRepository.findById(999L)).thenReturn(Optional.empty());

        TaskExecution result = service.getProgress(999L);

        assertNull(result);
    }

    // ================================================================
    // MOVE 模式测试
    // ================================================================

    @Test
    @DisplayName("MOVE 模式 — 同步成功后删除源文件")
    void shouldDeleteSourceFileInMoveMode() {
        syncTask.setSyncMode(SyncTask.SyncMode.MOVE);
        mockSourceFiles(file("video.mp4", 1000L));
        mockEmptyTarget();

        when(sourceStrategy.downloadFile(eq(sourceEngine), anyString()))
            .thenReturn(new ByteArrayInputStream(new byte[0]));
        doNothing().when(targetStrategy).uploadFile(eq(targetEngine), anyString(), any(InputStream.class), anyLong());
        doNothing().when(sourceStrategy).deleteFile(eq(sourceEngine), anyString());

        service.executeSyncTask(syncTask);

        verify(sourceStrategy).deleteFile(eq(sourceEngine), contains("video.mp4"));
    }
}
