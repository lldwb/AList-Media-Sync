package top.lldwb.alistmediasync.sync.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import top.lldwb.alistmediasync.common.enums.ConflictStrategy;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.execution.TaskExecution;
import top.lldwb.alistmediasync.execution.TaskExecutionRepository;
import top.lldwb.alistmediasync.storage.dto.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.repository.SyncTaskRepository;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 同步执行引擎单元测试
 * <p>
 * 覆盖 executeSyncTask() 与 getProgress() 的核心场景。重构后通过 StorageEngineStrategy
 * 策略模式操作文件，测试通过 Mock 策略链验证逻辑。
 * </p>
 * <p>
 * 不使用类级 LENIENT：每个用例通过 {@code stub*} 辅助方法只声明自身真正依赖的桩，
 * 未使用的桩会直接令用例失败，避免再次出现"Mock 缺失却全绿"的假绿。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("同步执行引擎测试")
class SyncServiceTest {

    @Mock
    private StorageEngineService storageEngineService;

    @Mock
    private SyncTaskRepository syncTaskRepository;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private PostSyncTranscodeTrigger postSyncTranscodeTrigger;

    @Mock
    private JsonMapper objectMapper;

    @Mock
    private WsSessionManager wsSessionManager;

    @Mock
    private StorageEngineStrategy sourceStrategy;

    @Mock
    private StorageEngineStrategy targetStrategy;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @InjectMocks
    private SyncService service;

    private SyncTask syncTask;
    private StorageEngine sourceEngine;
    private StorageEngine targetEngine;

    /** 被测类的日志事件捕获器（用于断言告警文案） */
    private final Logger syncServiceLogger =
        (Logger) LoggerFactory.getLogger(SyncService.class);
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        logAppender.start();
        syncServiceLogger.addAppender(logAppender);

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
        syncTask.setConflictStrategy(ConflictStrategy.SKIP);
    }

    @AfterEach
    void tearDown() {
        syncServiceLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    // ================================================================
    // 测试辅助：按需声明桩
    // ================================================================

    /** 事务模板所需的事务管理器 */
    private void stubTransaction() {
        when(transactionManager.getTransaction(any()))
            .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
    }

    /** 异步方法内部按 ID 重新加载任务（规避 LazyInitializationException） */
    private void stubTaskReload() {
        when(syncTaskRepository.findById(syncTask.getId())).thenReturn(Optional.of(syncTask));
    }

    /** 执行记录 save 返回入参并补全 ID */
    private void stubTaskExecutionSave() {
        when(taskExecutionRepository.save(any(TaskExecution.class))).thenAnswer(inv -> {
            TaskExecution exec = inv.getArgument(0);
            if (exec.getId() == null) {
                exec.setId(100L);
            }
            return exec;
        });
    }

    /** 无冲突运行中任务 */
    private void stubNoConflictingExecution() {
        when(taskExecutionRepository.findByStatusAndTaskType(any(), any())).thenReturn(List.of());
    }

    /** 不同引擎时的策略解析 */
    private void stubEngineResolution() {
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(storageEngineService.resolve(targetEngine)).thenReturn(targetStrategy);
    }

    /** 同引擎（source == target）时的策略解析 */
    private void stubSameEngineResolution() {
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
    }

    /** objectMapper 委托给真实 JsonMapper，使 failureDetails 断言的是真实序列化结果 */
    private void stubRealJsonSerialization() {
        JsonMapper realMapper = new JsonMapper();
        try {
            when(objectMapper.writeValueAsString(any()))
                .thenAnswer(inv -> realMapper.writeValueAsString(inv.getArgument(0)));
        } catch (Exception e) {
            throw new IllegalStateException("桩声明失败", e);
        }
    }

    /** executeSyncTask 顺利跑完所需的全部桩（跨引擎场景） */
    private void stubCrossEngineRun() {
        stubTransaction();
        stubTaskReload();
        stubTaskExecutionSave();
        stubNoConflictingExecution();
        stubEngineResolution();
    }

    /** executeSyncTask 顺利跑完所需的全部桩（同引擎场景） */
    private void stubSameEngineRun() {
        stubTransaction();
        stubTaskReload();
        stubTaskExecutionSave();
        stubNoConflictingExecution();
        stubSameEngineResolution();
    }

    /** 构建 FileEntry 文件 */
    private FileEntry file(String name, long size) {
        return new FileEntry(name, "/videos/" + name, false, size, LocalDateTime.now());
    }

    /** 构建指定路径的 FileEntry 文件 */
    private FileEntry file(String name, String path, long size) {
        return new FileEntry(name, path, false, size, LocalDateTime.now());
    }

    /** 构建 FileEntry 目录 */
    private FileEntry dir(String name) {
        return new FileEntry(name, "/videos/" + name, true, 0, LocalDateTime.now());
    }

    /** 构建目标目录下的 FileEntry 文件 */
    private FileEntry targetFile(String name, long size) {
        return new FileEntry(name, "/backup/" + name, false, size, LocalDateTime.now());
    }

    /** Mock 策略返回的 listFiles（首页返回入参，后续页为空） */
    private void mockSourceFiles(FileEntry... files) {
        when(sourceStrategy.listFiles(eq(sourceEngine), anyString(), anyInt(), anyInt()))
            .thenReturn(List.of(files))
            .thenReturn(List.of());
    }

    private void mockTargetFiles(FileEntry... files) {
        when(targetStrategy.listFiles(eq(targetEngine), anyString(), anyInt(), anyInt()))
            .thenReturn(List.of(files))
            .thenReturn(List.of());
    }

    /** Mock 空目录 */
    private void mockEmptySource() {
        when(sourceStrategy.listFiles(eq(sourceEngine), anyString(), anyInt(), anyInt()))
            .thenReturn(List.of());
    }

    private void mockEmptyTarget() {
        when(targetStrategy.listFiles(eq(targetEngine), anyString(), anyInt(), anyInt()))
            .thenReturn(List.of());
    }

    /** 让小文件同步的下载/上传成功 */
    private void stubSmallFileTransfer() {
        when(sourceStrategy.downloadFile(eq(sourceEngine), anyString()))
            .thenReturn(new ByteArrayInputStream(new byte[0]));
    }

    /** 取最后一次保存的执行记录（即终态记录） */
    private TaskExecution lastSavedExecution() {
        ArgumentCaptor<TaskExecution> captor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    /** 列出 java.io.tmpdir 下大文件中转产生的临时文件 */
    private Set<Path> listSyncTempFiles() throws IOException {
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> entries = Files.list(tmpDir)) {
            return entries
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("alist-sync-") && name.endsWith(".tmp");
                })
                .collect(Collectors.toSet());
        }
    }

    // ================================================================
    // executeSyncTask — 正常场景
    // ================================================================

    @Test
    @DisplayName("NEW_ONLY 模式 — 目标为空时全部同步")
    void shouldSyncNewFilesInNewOnlyMode() {
        stubCrossEngineRun();
        mockSourceFiles(file("video1.mp4", 1000L), file("video2.mp4", 2000L));
        mockEmptyTarget();
        stubSmallFileTransfer();

        service.executeSyncTask(syncTask);

        verify(sourceStrategy, times(2)).downloadFile(eq(sourceEngine), anyString());
        verify(targetStrategy).uploadFile(eq(targetEngine), eq("/backup/video1.mp4"),
            any(InputStream.class), eq(1000L));
        verify(targetStrategy).uploadFile(eq(targetEngine), eq("/backup/video2.mp4"),
            any(InputStream.class), eq(2000L));

        TaskExecution finalExecution = lastSavedExecution();
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, finalExecution.getStatus());
        assertEquals(2, finalExecution.getTotalFiles());
        assertEquals(2, finalExecution.getSuccessFiles());
        assertEquals(0, finalExecution.getFailedFiles());
        assertNull(finalExecution.getFailureDetails());
    }

    @Test
    @DisplayName("NEW_ONLY 模式 — 目标已有同名文件且冲突策略为 SKIP 时应跳过")
    void shouldSkipExistingFilesInNewOnlyMode() {
        stubCrossEngineRun();
        mockSourceFiles(file("exists.mp4", 1000L));
        mockTargetFiles(targetFile("exists.mp4", 500L));

        service.executeSyncTask(syncTask);

        // 不应下载或上传
        verify(sourceStrategy, never()).downloadFile(any(StorageEngine.class), anyString());
        verify(targetStrategy, never()).uploadFile(any(StorageEngine.class), anyString(),
            any(InputStream.class), anyLong());
        // 被跳过的文件不计入待同步总数，执行仍为 SUCCESS
        TaskExecution finalExecution = lastSavedExecution();
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, finalExecution.getStatus());
        assertEquals(0, finalExecution.getTotalFiles());
    }

    @Test
    @DisplayName("NEW_ONLY 模式 — 递归子目录同步时保留相对路径")
    void shouldKeepRelativePathWhenSyncingNestedFiles() {
        stubCrossEngineRun();
        when(sourceStrategy.listFiles(eq(sourceEngine), eq("/videos"), anyInt(), anyInt()))
            .thenReturn(List.of(dir("归档")));
        when(sourceStrategy.listFiles(eq(sourceEngine), eq("/videos/归档"), anyInt(), anyInt()))
            .thenReturn(List.of(file("video.flv", "/videos/归档/video.flv", 1000L)));
        mockEmptyTarget();
        stubSmallFileTransfer();

        service.executeSyncTask(syncTask);

        verify(sourceStrategy).downloadFile(sourceEngine, "/videos/归档/video.flv");
        verify(targetStrategy).uploadFile(eq(targetEngine), eq("/backup/归档/video.flv"),
            any(InputStream.class), eq(1000L));
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, lastSavedExecution().getStatus());
    }

    @Test
    @DisplayName("同步完成后应更新任务的最后执行时间")
    void shouldUpdateLastExecutedAtOnCompletion() {
        stubCrossEngineRun();
        mockEmptySource();

        LocalDateTime before = LocalDateTime.now().minusSeconds(5);
        service.executeSyncTask(syncTask);

        ArgumentCaptor<SyncTask> captor = ArgumentCaptor.forClass(SyncTask.class);
        verify(syncTaskRepository).save(captor.capture());
        assertTrue(captor.getValue().getLastExecutedAt().isAfter(before),
            "最后执行时间应被刷新为本次执行时间");
    }

    @Test
    @DisplayName("启用转码时同步成功后应触发后置转码")
    void shouldTriggerPostSyncTranscodeWhenEnabled() {
        syncTask.setTranscodeEnabled(true);
        stubCrossEngineRun();
        mockEmptySource();

        service.executeSyncTask(syncTask);

        ArgumentCaptor<TaskExecution> captor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(postSyncTranscodeTrigger).trigger(eq(syncTask), captor.capture());
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, captor.getValue().getStatus(),
            "只有 SUCCESS 的执行记录才应触发后置转码");
    }

    @Test
    @DisplayName("启用转码但执行不是 SUCCESS 时不触发后置转码")
    void shouldNotTriggerTranscodeWhenExecutionNotSuccess() {
        syncTask.setTranscodeEnabled(true);
        stubCrossEngineRun();
        stubRealJsonSerialization();
        mockSourceFiles(file("bad.mp4", 500L));
        mockEmptyTarget();
        when(sourceStrategy.downloadFile(eq(sourceEngine), anyString()))
            .thenThrow(new RuntimeException("网络中断"));

        service.executeSyncTask(syncTask);

        assertEquals(TaskExecution.ExecutionStatus.FAILED, lastSavedExecution().getStatus());
        verify(postSyncTranscodeTrigger, never()).trigger(any(), any());
    }

    // ================================================================
    // executeSyncTask — 同引擎分支
    // ================================================================

    @Test
    @DisplayName("同引擎同步 — 走服务端复制而非下载上传")
    void shouldUseCopyFileWhenSameEngine() {
        syncTask.setTargetEngine(sourceEngine);
        stubSameEngineRun();
        when(sourceStrategy.listFiles(eq(sourceEngine), eq("/videos"), anyInt(), anyInt()))
            .thenReturn(List.of(file("a.mp4", 1000L)))
            .thenReturn(List.of());
        when(sourceStrategy.listFiles(eq(sourceEngine), eq("/backup"), anyInt(), anyInt()))
            .thenReturn(List.of());

        service.executeSyncTask(syncTask);

        verify(sourceStrategy).copyFile(sourceEngine, "/videos/a.mp4", "/backup/a.mp4");
        verify(sourceStrategy).createDirectory(sourceEngine, "/backup");
        verify(sourceStrategy, never()).downloadFile(any(), anyString());
        verify(sourceStrategy, never()).uploadFile(any(), anyString(), any(), anyLong());
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, lastSavedExecution().getStatus());
    }

    @Test
    @DisplayName("同引擎 MOVE 模式 — 走原生 moveFile 且不再单独删除源文件")
    void shouldUseNativeMoveWhenSameEngineAndMoveMode() {
        syncTask.setTargetEngine(sourceEngine);
        syncTask.setSyncMode(SyncTask.SyncMode.MOVE);
        stubSameEngineRun();
        when(sourceStrategy.listFiles(eq(sourceEngine), eq("/videos"), anyInt(), anyInt()))
            .thenReturn(List.of(file("a.mp4", 1000L)))
            .thenReturn(List.of());
        when(sourceStrategy.listFiles(eq(sourceEngine), eq("/backup"), anyInt(), anyInt()))
            .thenReturn(List.of());

        service.executeSyncTask(syncTask);

        verify(sourceStrategy).moveFile(sourceEngine, "/videos/a.mp4", "/backup/a.mp4");
        verify(sourceStrategy, never()).deleteFile(any(), anyString());
        verify(sourceStrategy, never()).copyFile(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("同引擎同路径 — 目标扫描复用源扫描结果，NEW_ONLY 判定为无需同步")
    void shouldReuseSourceScanWhenSameEngineAndSamePath() {
        syncTask.setTargetEngine(sourceEngine);
        syncTask.setTargetPath("/videos");
        stubSameEngineRun();
        when(sourceStrategy.listFiles(eq(sourceEngine), eq("/videos"), anyInt(), anyInt()))
            .thenReturn(List.of(file("a.mp4", 1000L)))
            .thenReturn(List.of());

        service.executeSyncTask(syncTask);

        // 同引擎同路径只扫描一次（目标目录复用源扫描结果）
        verify(sourceStrategy, times(1)).listFiles(eq(sourceEngine), anyString(), anyInt(), anyInt());
        verify(sourceStrategy, never()).copyFile(any(), anyString(), anyString());
        TaskExecution finalExecution = lastSavedExecution();
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, finalExecution.getStatus());
        assertEquals(0, finalExecution.getTotalFiles());
    }

    // ================================================================
    // executeSyncTask — 大/小文件两条 IO 路径
    // ================================================================

    @Test
    @DisplayName("大文件同步 — 超过 100MB 时落盘中转文件、上传后清理临时文件")
    void shouldStageLargeFileToDiskAndCleanUpTempFile() throws Exception {
        stubCrossEngineRun();
        long largeSize = 200L * 1024 * 1024;
        byte[] payload = "large-file-payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mockSourceFiles(file("big.flv", largeSize));
        mockEmptyTarget();

        // 下载方法在 createTempFile 之后被调用，此刻 tmpdir 的新增文件即本次的中转文件
        Set<Path> tempFilesBefore = listSyncTempFiles();
        List<Path> stagedTempFiles = new ArrayList<>();
        when(sourceStrategy.downloadFile(eq(sourceEngine), anyString())).thenAnswer(inv -> {
            Set<Path> newlyCreated = new HashSet<>(listSyncTempFiles());
            newlyCreated.removeAll(tempFilesBefore);
            stagedTempFiles.addAll(newlyCreated);
            return new ByteArrayInputStream(payload);
        });

        List<byte[]> uploadedContent = new ArrayList<>();
        List<InputStream> uploadedStreams = new ArrayList<>();
        doAnswer(inv -> {
            InputStream in = inv.getArgument(2);
            uploadedStreams.add(in);
            uploadedContent.add(in.readAllBytes());
            return null;
        }).when(targetStrategy).uploadFile(eq(targetEngine), anyString(), any(InputStream.class), anyLong());

        service.executeSyncTask(syncTask);

        // 1) 落盘：大文件路径在下载与上传之间确实创建了磁盘中转文件
        assertEquals(1, stagedTempFiles.size(),
            "大文件路径应创建一个 alist-sync-*.tmp 中转文件，实际=" + stagedTempFiles);
        Path stagedTempFile = stagedTempFiles.get(0);

        // 2) 上传的是中转文件流（而非源下载流），内容与源一致，大小按声明值传递
        verify(targetStrategy).uploadFile(eq(targetEngine), eq("/backup/big.flv"),
            any(InputStream.class), eq(largeSize));
        assertArrayEquals(payload, uploadedContent.get(0));
        assertNotNull(uploadedStreams.get(0));

        // 3) finally 清理：中转临时文件不得残留
        assertFalse(Files.exists(stagedTempFile),
            "finally 应删除中转临时文件：" + stagedTempFile);
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, lastSavedExecution().getStatus());
    }

    @Test
    @DisplayName("大文件同步失败 — 异常仍抛出且中转临时文件被清理")
    void shouldCleanUpTempFileWhenLargeFileUploadFails() throws Exception {
        stubCrossEngineRun();
        stubRealJsonSerialization();
        long largeSize = 150L * 1024 * 1024;

        mockSourceFiles(file("big.flv", largeSize));
        mockEmptyTarget();

        Set<Path> tempFilesBefore = listSyncTempFiles();
        List<Path> stagedTempFiles = new ArrayList<>();
        when(sourceStrategy.downloadFile(eq(sourceEngine), anyString())).thenAnswer(inv -> {
            Set<Path> newlyCreated = new HashSet<>(listSyncTempFiles());
            newlyCreated.removeAll(tempFilesBefore);
            stagedTempFiles.addAll(newlyCreated);
            return new ByteArrayInputStream(new byte[]{1, 2, 3});
        });
        doThrow(new RuntimeException("上传被拒绝")).when(targetStrategy)
            .uploadFile(eq(targetEngine), anyString(), any(InputStream.class), anyLong());

        service.executeSyncTask(syncTask);

        assertEquals(1, stagedTempFiles.size());
        assertFalse(Files.exists(stagedTempFiles.get(0)),
            "上传失败时 finally 同样应清理中转临时文件：" + stagedTempFiles.get(0));
        TaskExecution finalExecution = lastSavedExecution();
        assertEquals(TaskExecution.ExecutionStatus.FAILED, finalExecution.getStatus());
        assertEquals(1, finalExecution.getFailedFiles());
        assertNotNull(finalExecution.getFailureDetails());
        assertTrue(finalExecution.getFailureDetails().contains("big.flv"),
            "失败明细应包含失败文件：" + finalExecution.getFailureDetails());
    }

    @Test
    @DisplayName("小文件同步 — 恰好 100MB 走流式路径，不落盘中转文件")
    void shouldStreamSmallFileWithoutDiskStaging() throws Exception {
        stubCrossEngineRun();
        long exactlyThreshold = 100L * 1024 * 1024; // 边界：不大于 100MB → 流式路径

        mockSourceFiles(file("edge.flv", exactlyThreshold));
        mockEmptyTarget();

        Set<Path> tempFilesBefore = listSyncTempFiles();
        InputStream sourceStream = new ByteArrayInputStream(new byte[]{7, 8, 9});
        when(sourceStrategy.downloadFile(eq(sourceEngine), anyString())).thenReturn(sourceStream);

        List<InputStream> uploadedStreams = new ArrayList<>();
        doAnswer(inv -> {
            uploadedStreams.add(inv.getArgument(2));
            return null;
        }).when(targetStrategy).uploadFile(eq(targetEngine), anyString(), any(InputStream.class), anyLong());

        service.executeSyncTask(syncTask);

        // 流式路径直接把源流交给上传方，未经过任何磁盘中转
        assertSame(sourceStream, uploadedStreams.get(0),
            "小文件应把源下载流直接交给上传方");
        Set<Path> newlyCreated = new HashSet<>(listSyncTempFiles());
        newlyCreated.removeAll(tempFilesBefore);
        assertTrue(newlyCreated.isEmpty(), "小文件路径不应创建中转临时文件：" + newlyCreated);
        verify(targetStrategy).uploadFile(eq(targetEngine), eq("/backup/edge.flv"),
            any(InputStream.class), eq(exactlyThreshold));
    }

    // ================================================================
    // executeSyncTask — 异常场景
    // ================================================================

    @Test
    @DisplayName("源目录为空时执行记录为 SUCCESS 且计数归零")
    void shouldHandleEmptySource() {
        stubCrossEngineRun();
        mockEmptySource();

        service.executeSyncTask(syncTask);

        TaskExecution finalExecution = lastSavedExecution();
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, finalExecution.getStatus());
        assertEquals(0, finalExecution.getTotalFiles());
        assertEquals(0, finalExecution.getSuccessFiles());
        assertEquals(0, finalExecution.getFailedFiles());
        assertNotNull(finalExecution.getEndTime());
    }

    @Test
    @DisplayName("下载失败时记录失败文件但继续处理其余文件")
    void shouldContinueAfterDownloadFailure() {
        stubCrossEngineRun();
        stubRealJsonSerialization();
        mockSourceFiles(file("good.mp4", 500L), file("bad.mp4", 500L));
        mockEmptyTarget();

        when(sourceStrategy.downloadFile(eq(sourceEngine), contains("good")))
            .thenReturn(new ByteArrayInputStream(new byte[0]));
        when(sourceStrategy.downloadFile(eq(sourceEngine), contains("bad")))
            .thenThrow(new RuntimeException("网络中断"));

        service.executeSyncTask(syncTask);

        verify(targetStrategy).uploadFile(eq(targetEngine), contains("good"),
            any(InputStream.class), anyLong());
        verify(targetStrategy, never()).uploadFile(eq(targetEngine), contains("bad"),
            any(InputStream.class), anyLong());

        // 一成功一失败 → PARTIAL_SUCCESS，且失败明细可见
        TaskExecution finalExecution = lastSavedExecution();
        assertEquals(TaskExecution.ExecutionStatus.PARTIAL_SUCCESS, finalExecution.getStatus());
        assertEquals(2, finalExecution.getTotalFiles());
        assertEquals(1, finalExecution.getSuccessFiles());
        assertEquals(1, finalExecution.getFailedFiles());
        assertTrue(finalExecution.getFailureDetails().contains("bad.mp4"),
            "失败明细应包含失败文件：" + finalExecution.getFailureDetails());
    }

    @Test
    @DisplayName("存在冲突运行中任务时仍正常执行（仅警告）")
    void shouldWarnButContinueWhenConflictingTasksExist() {
        stubTransaction();
        stubTaskReload();
        stubTaskExecutionSave();
        stubEngineResolution();

        TaskExecution running = new TaskExecution();
        running.setId(99L);
        running.setStatus(TaskExecution.ExecutionStatus.RUNNING);
        when(taskExecutionRepository.findByStatusAndTaskType(
            TaskExecution.ExecutionStatus.RUNNING, TaskExecution.TaskType.SYNC))
            .thenReturn(List.of(running));

        mockEmptySource();

        service.executeSyncTask(syncTask);

        // 告警不阻断：执行推进到终态
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, lastSavedExecution().getStatus());
        // 告警文案可被运维观测到
        List<String> warnings = logAppender.list.stream()
            .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
        assertTrue(warnings.stream().anyMatch(m -> m.contains("存在正在运行的同步任务")
                && m.contains("测试同步")),
            "应输出冲突告警且包含任务名，实际警告=" + warnings);
    }

    // ================================================================
    // 排除规则测试
    // ================================================================

    @Test
    @DisplayName("排除规则 — 排除 .tmp 后缀文件")
    void shouldExcludeTmpFiles() {
        syncTask.setExcludePatterns("*.tmp");
        stubCrossEngineRun();
        mockSourceFiles(file("video.mp4", 1000L), file("temp.tmp", 500L));
        mockEmptyTarget();
        stubSmallFileTransfer();

        service.executeSyncTask(syncTask);

        verify(sourceStrategy, times(1)).downloadFile(eq(sourceEngine), anyString());
        assertEquals(1, lastSavedExecution().getTotalFiles());
    }

    @Test
    @DisplayName("排除规则 — 支持换行分隔多个模式")
    void shouldExcludeMultiplePatterns() {
        syncTask.setExcludePatterns("*.tmp\n*.part\n.DS_Store");
        stubCrossEngineRun();
        mockSourceFiles(
            file("video.mp4", 1000L),
            file("temp.tmp", 500L),
            file("file.part", 500L),
            file(".DS_Store", 0L)
        );
        mockEmptyTarget();
        stubSmallFileTransfer();

        service.executeSyncTask(syncTask);

        verify(sourceStrategy, times(1)).downloadFile(eq(sourceEngine), anyString());
        verify(sourceStrategy).downloadFile(eq(sourceEngine), contains("video.mp4"));
        assertEquals(1, lastSavedExecution().getTotalFiles());
    }

    @Test
    @DisplayName("排除规则 — ? 只匹配单个字符")
    void shouldMatchSingleCharacterWildcardOnly() {
        syncTask.setExcludePatterns("video?.mp4");
        stubCrossEngineRun();
        mockSourceFiles(file("video1.mp4", 1000L), file("video12.mp4", 1000L));
        mockEmptyTarget();
        stubSmallFileTransfer();

        service.executeSyncTask(syncTask);

        // video1.mp4 被排除，video12.mp4 不匹配 ? 应被同步
        verify(sourceStrategy, never()).downloadFile(eq(sourceEngine), contains("video1.mp4"));
        verify(sourceStrategy).downloadFile(eq(sourceEngine), contains("video12.mp4"));
        assertEquals(1, lastSavedExecution().getTotalFiles());
    }

    // ================================================================
    // 冲突策略测试
    // ================================================================

    @Test
    @DisplayName("冲突策略 OVERWRITE — FULL 模式下覆盖目标已有文件")
    void shouldOverwriteExistingFile() {
        syncTask.setSyncMode(SyncTask.SyncMode.FULL);
        syncTask.setConflictStrategy(ConflictStrategy.OVERWRITE);
        stubCrossEngineRun();
        mockSourceFiles(file("video.mp4", 1000L));
        mockTargetFiles(targetFile("video.mp4", 500L));
        stubSmallFileTransfer();

        service.executeSyncTask(syncTask);

        // FULL 模式 + OVERWRITE：先删除目标再上传
        verify(targetStrategy).deleteFile(targetEngine, "/backup/video.mp4");
        verify(targetStrategy).uploadFile(eq(targetEngine), eq("/backup/video.mp4"),
            any(InputStream.class), eq(1000L));
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, lastSavedExecution().getStatus());
    }

    @Test
    @DisplayName("冲突策略 RENAME — 逐级递增序号直到目标路径不冲突")
    void shouldRenameUntilTargetPathIsFree() {
        syncTask.setSyncMode(SyncTask.SyncMode.FULL);
        syncTask.setConflictStrategy(ConflictStrategy.RENAME);
        stubCrossEngineRun();
        mockSourceFiles(file("video.mp4", 1000L));
        // 目标已存在 video.mp4 与 video (1).mp4 → 重命名应跳到 (2)
        mockTargetFiles(targetFile("video.mp4", 500L), targetFile("video (1).mp4", 500L));
        stubSmallFileTransfer();

        service.executeSyncTask(syncTask);

        verify(targetStrategy).uploadFile(eq(targetEngine), eq("/backup/video (2).mp4"),
            any(InputStream.class), eq(1000L));
        verify(targetStrategy, never()).uploadFile(eq(targetEngine), eq("/backup/video.mp4"),
            any(InputStream.class), anyLong());
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, lastSavedExecution().getStatus());
    }

    @Test
    @DisplayName("冲突策略 RENAME — 目标同名文件不存在冲突时命名为 (1)")
    void shouldRenameToFirstAvailableSuffix() {
        syncTask.setSyncMode(SyncTask.SyncMode.FULL);
        syncTask.setConflictStrategy(ConflictStrategy.RENAME);
        stubCrossEngineRun();
        mockSourceFiles(file("video.mp4", 1000L));
        mockTargetFiles(targetFile("video.mp4", 500L));
        stubSmallFileTransfer();

        service.executeSyncTask(syncTask);

        verify(targetStrategy).uploadFile(eq(targetEngine), eq("/backup/video (1).mp4"),
            any(InputStream.class), eq(1000L));
    }

    @Test
    @DisplayName("FULL 模式 — 删除目标多余文件，删除失败计入失败明细")
    void shouldDeleteExtraTargetFilesInFullMode() {
        syncTask.setSyncMode(SyncTask.SyncMode.FULL);
        stubCrossEngineRun();
        stubRealJsonSerialization();
        mockSourceFiles(file("keep.mp4", 1000L));
        mockTargetFiles(targetFile("keep.mp4", 500L), targetFile("extra.mp4", 500L),
            targetFile("stuck.mp4", 500L));
        // 单一桩覆盖目标引擎的全部删除调用：仅 stuck.mp4 抛异常。
        // 若按路径分别 doThrow，其余路径的删除调用会命中 Mockito 严格模式的
        // "参数不匹配" 告警并被当作异常抛出，导致成功路径也被计入失败明细。
        doAnswer(invocation -> {
            String path = invocation.getArgument(1);
            if ("/backup/stuck.mp4".equals(path)) {
                throw new RuntimeException("权限不足");
            }
            return null;
        }).when(targetStrategy).deleteFile(eq(targetEngine), anyString());

        service.executeSyncTask(syncTask);

        verify(targetStrategy).deleteFile(targetEngine, "/backup/extra.mp4");
        verify(targetStrategy, never()).deleteFile(targetEngine, "/backup/keep.mp4");
        TaskExecution finalExecution = lastSavedExecution();
        assertEquals(TaskExecution.ExecutionStatus.PARTIAL_SUCCESS, finalExecution.getStatus());
        assertEquals(1, finalExecution.getFailedFiles());
        assertTrue(finalExecution.getFailureDetails().contains("stuck.mp4"),
            "删除失败应计入失败明细：" + finalExecution.getFailureDetails());
    }

    @Test
    @DisplayName("全量扫描异常时执行状态为 FAILED")
    void shouldMarkExecutionFailedOnScanException() {
        stubTransaction();
        stubTaskReload();
        stubTaskExecutionSave();
        stubNoConflictingExecution();
        stubEngineResolution();
        when(sourceStrategy.listFiles(eq(sourceEngine), anyString(), anyInt(), anyInt()))
            .thenThrow(new RuntimeException("扫描失败"));

        service.executeSyncTask(syncTask);

        ArgumentCaptor<TaskExecution> captor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository, atLeast(2)).save(captor.capture());
        List<TaskExecution> saved = captor.getAllValues();
        boolean hasFailed = saved.stream()
            .anyMatch(e -> e.getStatus() == TaskExecution.ExecutionStatus.FAILED);
        assertTrue(hasFailed, "应存在 FAILED 状态的执行记录");
    }

    // ================================================================
    // MOVE 模式测试（跨引擎）
    // ================================================================

    @Test
    @DisplayName("MOVE 模式 — 跨引擎同步成功后删除源文件")
    void shouldDeleteSourceFileInMoveMode() {
        syncTask.setSyncMode(SyncTask.SyncMode.MOVE);
        stubCrossEngineRun();
        mockSourceFiles(file("video.mp4", 1000L));
        mockEmptyTarget();
        stubSmallFileTransfer();

        service.executeSyncTask(syncTask);

        verify(targetStrategy).uploadFile(eq(targetEngine), eq("/backup/video.mp4"),
            any(InputStream.class), anyLong());
        verify(sourceStrategy).deleteFile(sourceEngine, "/videos/video.mp4");
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, lastSavedExecution().getStatus());
    }

    @Test
    @DisplayName("MOVE 模式 — 删除源文件失败不阻断执行完成")
    void shouldContinueWhenSourceDeleteFailsInMoveMode() {
        syncTask.setSyncMode(SyncTask.SyncMode.MOVE);
        stubCrossEngineRun();
        mockSourceFiles(file("video.mp4", 1000L));
        mockEmptyTarget();
        stubSmallFileTransfer();
        doThrow(new RuntimeException("源端只读")).when(sourceStrategy)
            .deleteFile(sourceEngine, "/videos/video.mp4");

        service.executeSyncTask(syncTask);

        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, lastSavedExecution().getStatus());
        verify(targetStrategy).uploadFile(eq(targetEngine), eq("/backup/video.mp4"),
            any(InputStream.class), anyLong());
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

        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, result.getStatus());
        verify(taskExecutionRepository).findById(200L);
    }

    @Test
    @DisplayName("获取进度 — 既不在缓存也不在数据库时返回 null")
    void shouldReturnNullWhenProgressNotFound() {
        when(taskExecutionRepository.findById(999L)).thenReturn(Optional.empty());

        TaskExecution result = service.getProgress(999L);

        assertNull(result);
        verify(taskExecutionRepository).findById(999L);
    }
}
