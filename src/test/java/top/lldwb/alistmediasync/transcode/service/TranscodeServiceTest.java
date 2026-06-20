package top.lldwb.alistmediasync.transcode.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.transcode.dto.transcode.TranscodeTaskVO;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;
import top.lldwb.alistmediasync.transcode.repository.TranscodeTaskRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 转码引擎（编排层）单元测试
 * <p>
 * 覆盖 createTask()、listAll()、getById()、retry() 方法。
 * executeAsync() 和 executePostSyncTranscode() 涉及文件系统和 JAVE2，
 * 需要集成测试环境，此处仅验证调用路径。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("转码引擎服务测试")
class TranscodeServiceTest {

    @Mock
    private TranscodeTaskRepository repository;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private StorageEngineRepository storageEngineRepository;

    @Mock
    private AppProperties appProperties;

    @Mock
    private AppProperties.Transcode transcodeConfig;

    @Mock
    private TranscodeFileProcessor fileProcessor;

    @InjectMocks
    private TranscodeService service;

    private TranscodeTask mockTask;
    private StorageEngine targetEngine;

    @BeforeEach
    void setUp() {
        when(appProperties.getTranscode()).thenReturn(transcodeConfig);
        when(transcodeConfig.getTempSuffix()).thenReturn(".tmp");
        when(transcodeConfig.getTempDir()).thenReturn(System.getProperty("java.io.tmpdir"));
        when(transcodeConfig.getDefaultBitrate()).thenReturn(128000);

        targetEngine = new StorageEngine();
        targetEngine.setId(2L);
        targetEngine.setName("目标引擎");
        targetEngine.setBaseUrl("https://target.example.com");
        targetEngine.setEncryptedToken("target-token");

        mockTask = new TranscodeTask();
        mockTask.setId(1L);
        mockTask.setSourceEngineId(1L);
        mockTask.setTargetEngineId(2L);
        mockTask.setSourceFilePath("/videos/test.mp4");
        mockTask.setTargetFilePath("/output/test.mp3");
        mockTask.setTargetFormat(TranscodeTask.TargetFormat.MP3);
        mockTask.setBitrate(128000);
        mockTask.setStatus(TranscodeTask.TranscodeStatus.PENDING);
        mockTask.setProgress(0);

        when(repository.save(any(TranscodeTask.class))).thenAnswer(inv -> {
            TranscodeTask t = inv.getArgument(0);
            if (t.getId() == null) t.setId(1L);
            return t;
        });
    }

    // ================================================================
    // createTask 方法测试
    // ================================================================

    @Test
    @DisplayName("创建任务 — 正常流程返回 Entity")
    void shouldCreateTask() {
        TranscodeTask result = service.createTask(
            1L, 2L, "/videos/test.mp4", "/output/test.mp3",
            TranscodeTask.TargetFormat.MP3, 128000, false);

        assertNotNull(result);
        assertEquals("/videos/test.mp4", result.getSourceFilePath());
        assertEquals(TranscodeTask.TargetFormat.MP3, result.getTargetFormat());
        assertEquals(128000, result.getBitrate());
        assertEquals(TranscodeTask.TranscodeStatus.PENDING, result.getStatus());
        verify(repository).save(any(TranscodeTask.class));
    }

    @Test
    @DisplayName("创建任务 — bitrate 为 null 时使用默认值 128000")
    void shouldUseDefaultBitrateWhenNull() {
        TranscodeTask result = service.createTask(
            1L, 2L, "/videos/test.mp4", "/output/test.mp3",
            TranscodeTask.TargetFormat.MP3, null, false);

        assertEquals(128000, result.getBitrate());
    }

    @Test
    @DisplayName("原目录转码 — sameDirectoryTranscode=true 时自动使用源文件目录")
    void shouldAutoSetTargetPathWhenSameDirectoryTranscodeTrue() {
        TranscodeTask result = service.createTask(
            1L, 2L, "/videos/test.flv", null,
            TranscodeTask.TargetFormat.MP3, 128000, true);

        assertEquals("/videos", result.getTargetFilePath(),
            "应自动使用源文件所在目录作为目标路径");
    }

    @Test
    @DisplayName("原目录转码 — sameDirectoryTranscode=false 时使用指定路径")
    void shouldUseSpecifiedTargetPathWhenSameDirectoryTranscodeFalse() {
        TranscodeTask result = service.createTask(
            1L, 2L, "/videos/test.flv", "/output/",
            TranscodeTask.TargetFormat.MP3, 128000, false);

        assertEquals("/output/", result.getTargetFilePath(),
            "应使用用户指定的目标路径");
    }

    @Test
    @DisplayName("原目录转码 — sameDirectoryTranscode=true 时忽略传入的 targetPath")
    void shouldIgnoreTargetPathWhenSameDirectoryTranscodeTrue() {
        TranscodeTask result = service.createTask(
            1L, 2L, "/videos/test.flv", "/ignored/path/",
            TranscodeTask.TargetFormat.MP3, 128000, true);

        assertEquals("/videos", result.getTargetFilePath(),
            "应忽略传入的 targetPath，使用源文件目录");
    }

    @Test
    @DisplayName("原目录转码 — 源文件在根目录时目标路径为 /")
    void shouldSetTargetPathToRootWhenSourceInRoot() {
        TranscodeTask result = service.createTask(
            1L, 2L, "/test.flv", null,
            TranscodeTask.TargetFormat.MP3, 128000, true);

        assertEquals("/", result.getTargetFilePath(),
            "源文件在根目录时目标路径应为 /");
    }

    @Test
    @DisplayName("原目录转码 — sameDirectoryTranscode=false 且 targetPath 为空时抛出异常")
    void shouldThrowWhenSameDirectoryTranscodeFalseAndTargetPathEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
            service.createTask(1L, 2L, "/videos/test.flv", null,
                TranscodeTask.TargetFormat.MP3, 128000, false));
    }

    // ================================================================
    // listAll 方法测试
    // ================================================================

    @Test
    @DisplayName("查询全部 — 正常返回 VO 列表")
    void shouldListAllTasks() {
        TranscodeTask task2 = new TranscodeTask();
        task2.setId(2L);
        task2.setSourceFilePath("/videos/test2.mp4");
        task2.setTargetFilePath("/output/test2.mp3");
        task2.setTargetFormat(TranscodeTask.TargetFormat.MP4);
        task2.setStatus(TranscodeTask.TranscodeStatus.COMPLETED);

        when(repository.findAll()).thenReturn(List.of(mockTask, task2));

        List<TranscodeTaskVO> result = service.listAll();

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("查询全部 — 无数据时返回空列表")
    void shouldReturnEmptyList() {
        when(repository.findAll()).thenReturn(List.of());

        List<TranscodeTaskVO> result = service.listAll();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ================================================================
    // getById 方法测试
    // ================================================================

    @Test
    @DisplayName("按 ID 查询 — 正常返回 VO")
    void shouldGetById() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));

        TranscodeTaskVO result = service.getById(1L);

        assertNotNull(result);
        assertEquals("/videos/test.mp4", result.getSourceFilePath());
    }

    @Test
    @DisplayName("按 ID 查询 — 不存在应抛出 NoSuchElementException")
    void shouldThrowWhenGetByIdNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getById(999L));
    }

    // ================================================================
    // retry 方法测试
    // ================================================================

    @Test
    @DisplayName("重试 — 非失败状态应抛出异常")
    void shouldRejectRetryForNonFailedTask() {
        mockTask.setStatus(TranscodeTask.TranscodeStatus.COMPLETED);
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));

        assertThrows(IllegalStateException.class, () -> service.retry(1L));
    }

    @Test
    @DisplayName("重试 — 下载失败应回退到 DOWNLOADING")
    void shouldRetryFromDownloadFailed() {
        mockTask.setStatus(TranscodeTask.TranscodeStatus.DOWNLOAD_FAILED);
        mockTask.setTempSourcePath("/tmp/src-test.mp4");
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));

        service.retry(1L);

        assertEquals(TranscodeTask.TranscodeStatus.DOWNLOADING, mockTask.getStatus());
        assertNull(mockTask.getTempSourcePath());
        assertNull(mockTask.getErrorMessage());
    }

    @Test
    @DisplayName("重试 — 任务不存在应抛出 NoSuchElementException")
    void shouldThrowWhenRetryNonExistentTask() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.retry(999L));
    }

    @Test
    @DisplayName("重试 — 转码失败应回退到 TRANSCODING")
    void shouldRetryFromTranscodeFailed() {
        mockTask.setStatus(TranscodeTask.TranscodeStatus.TRANSCODE_FAILED);
        mockTask.setTempSourcePath("/tmp/src-test.mp4");
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));

        service.retry(1L);

        assertEquals(TranscodeTask.TranscodeStatus.TRANSCODING, mockTask.getStatus());
        assertNull(mockTask.getErrorMessage());
    }

    @Test
    @DisplayName("重试 — 上传失败应回退到 UPLOADING")
    void shouldRetryFromUploadFailed() {
        mockTask.setStatus(TranscodeTask.TranscodeStatus.UPLOAD_FAILED);
        mockTask.setTempFilePath("/tmp/out-test.mp3");
        when(repository.findById(1L)).thenReturn(Optional.of(mockTask));

        service.retry(1L);

        assertEquals(TranscodeTask.TranscodeStatus.UPLOADING, mockTask.getStatus());
        assertNull(mockTask.getErrorMessage());
    }
}
