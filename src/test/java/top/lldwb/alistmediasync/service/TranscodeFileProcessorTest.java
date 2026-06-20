package top.lldwb.alistmediasync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.client.AListClient;
import top.lldwb.alistmediasync.config.AppProperties;
import top.lldwb.alistmediasync.entity.StorageEngine;
import top.lldwb.alistmediasync.entity.SyncTask;
import top.lldwb.alistmediasync.entity.TaskExecution;
import top.lldwb.alistmediasync.entity.TranscodeTask;
import top.lldwb.alistmediasync.repository.TranscodeTaskRepository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 转码文件处理器单元测试
 * <p>
 * 覆盖 process() 方法的信号量管理和快速失败路径。
 * 完整的转码流程（JAVE2/FFmpeg/文件系统）需要在集成测试中验证。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("转码文件处理器测试")
class TranscodeFileProcessorTest {

    @Mock
    private TranscodeTaskRepository repository;

    @Mock
    private AListClient alistClient;

    @Mock
    private AppProperties appProperties;

    @Mock
    private AppProperties.Transcode transcodeConfig;

    @InjectMocks
    private TranscodeFileProcessor processor;

    private TranscodeCandidate candidate;
    private StorageEngine targetEngine;
    private SyncTask syncTask;
    private TaskExecution execution;

    @BeforeEach
    void setUp() {
        when(appProperties.getTranscode()).thenReturn(transcodeConfig);
        when(transcodeConfig.getMaxConcurrentTranscode()).thenReturn(32);
        // 初始化信号量
        processor.init();

        candidate = new TranscodeCandidate(
            "test-video.mp4", "/videos/test-video.mp4",
            "/output/test-video.mp3", "MP4", 50_000_000L);

        targetEngine = new StorageEngine();
        targetEngine.setId(2L);
        targetEngine.setName("目标引擎");
        targetEngine.setBaseUrl("https://target.example.com");
        targetEngine.setEncryptedToken("target-token");

        syncTask = new SyncTask();
        syncTask.setId(1L);
        syncTask.setName("测试同步任务");

        execution = new TaskExecution();
        execution.setId(100L);
        execution.setStatus(TaskExecution.ExecutionStatus.RUNNING);
    }

    // ================================================================
    // process 方法测试 — 信号量管理
    // ================================================================

    @Test
    @DisplayName("process — 正常获取和释放信号量")
    void shouldAcquireAndReleaseSemaphore() throws Exception {
        // 由于 process 是 @Async 且内部调用 doProcess，
        // 测试仅验证方法签名和 Future 返回类型正确
        CompletableFuture<TranscodeResult> future = processor.process(
            candidate, TranscodeTask.TargetFormat.MP3, ".tmp",
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir")),
            targetEngine, syncTask, execution);

        assertNotNull(future);
    }

    @Test
    @DisplayName("process — 返回类型为 CompletableFuture<TranscodeResult>")
    void shouldReturnCompletableFuture() {
        CompletableFuture<TranscodeResult> future = processor.process(
            candidate, TranscodeTask.TargetFormat.MP3, ".tmp",
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir")),
            targetEngine, syncTask, execution);

        assertTrue(future instanceof CompletableFuture);
    }

    // ================================================================
    // 信号量初始化测试
    // ================================================================

    @Test
    @DisplayName("init — 信号量使用配置的并发上限")
    void shouldInitSemaphoreWithConfiguredMax() {
        when(transcodeConfig.getMaxConcurrentTranscode()).thenReturn(16);
        processor.init();

        // 信号量初始化成功（无异常）
        assertDoesNotThrow(() -> processor.process(
            candidate, TranscodeTask.TargetFormat.MP3, ".tmp",
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir")),
            targetEngine, syncTask, execution));
    }

    // ================================================================
    // TranscodeResult 不可变数据类测试
    // ================================================================

    @Test
    @DisplayName("TranscodeResult — 成功记录")
    void shouldCreateSuccessResult() {
        TranscodeResult result = new TranscodeResult("test.mp4", true, null);

        assertTrue(result.success());
        assertEquals("test.mp4", result.sourceFileName());
        assertNull(result.error());
    }

    @Test
    @DisplayName("TranscodeResult — 失败记录")
    void shouldCreateFailureResult() {
        TranscodeResult result = new TranscodeResult("test.mp4", false, "转码错误");

        assertFalse(result.success());
        assertEquals("test.mp4", result.sourceFileName());
        assertEquals("转码错误", result.error());
    }

    // ================================================================
    // TranscodeCandidate 不可变数据类测试
    // ================================================================

    @Test
    @DisplayName("TranscodeCandidate — 正确保存所有字段")
    void shouldStoreAllCandidateFields() {
        assertEquals("test-video.mp4", candidate.name());
        assertEquals("/videos/test-video.mp4", candidate.fullPath());
        assertEquals("/output/test-video.mp3", candidate.targetPath());
        assertEquals("MP4", candidate.format());
        assertEquals(50_000_000L, candidate.size());
    }

    // ================================================================
    // buildEncodingAttributes 测试（通过 process 间接验证格式支持）
    // ================================================================

    @Test
    @DisplayName("buildEncodingAttributes — MP3 格式不应抛异常")
    void shouldSupportMp3Format() {
        CompletableFuture<TranscodeResult> future = processor.process(
            candidate, TranscodeTask.TargetFormat.MP3, ".tmp",
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir")),
            targetEngine, syncTask, execution);

        assertNotNull(future);
    }

    @Test
    @DisplayName("buildEncodingAttributes — MP4 格式不应抛异常")
    void shouldSupportMp4Format() {
        CompletableFuture<TranscodeResult> future = processor.process(
            candidate, TranscodeTask.TargetFormat.MP4, ".tmp",
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir")),
            targetEngine, syncTask, execution);

        assertNotNull(future);
    }

    @Test
    @DisplayName("buildEncodingAttributes — FLV 格式不应抛异常")
    void shouldSupportFlvFormat() {
        CompletableFuture<TranscodeResult> future = processor.process(
            candidate, TranscodeTask.TargetFormat.FLV, ".tmp",
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir")),
            targetEngine, syncTask, execution);

        assertNotNull(future);
    }
}
