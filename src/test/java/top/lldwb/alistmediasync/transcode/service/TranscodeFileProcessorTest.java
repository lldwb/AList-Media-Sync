package top.lldwb.alistmediasync.transcode.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.enums.TargetFormat;
import top.lldwb.alistmediasync.common.service.RetryService;
import top.lldwb.alistmediasync.common.util.TraceContext;
import top.lldwb.alistmediasync.execution.TaskExecution;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask.TranscodeStatus;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;
import ws.schild.jave.progress.EncoderProgressListener;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 转码文件处理器单元测试
 * <p>
 * 覆盖 process() 的完整三步流水线（下载 → 转码 → 上传）、信号量并发上限的可观测语义、
 * 各步骤失败的状态落点、自动重试调度与重试用尽、转码参数构建与进度回调节流。
 * FFmpeg 真实编码不在单测范围：{@code doTranscode} 以 spy 隔离，其余依赖全部 mock，
 * 不依赖真实网络、真实二进制与执行顺序。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("转码文件处理器测试")
@SuppressWarnings("deprecation") // JAVE2 3.5.0 Encoder/Attributes API
class TranscodeFileProcessorTest {

    private static final long TASK_ID = 1L;

    @Mock
    private StorageEngineService storageEngineService;

    @Mock
    private RetryService retryService;

    @Mock
    private TranscodeTaskStateWriter stateWriter;

    @Mock
    private StorageEngineStrategy sourceStrategy;

    @Mock
    private StorageEngineStrategy targetStrategy;

    @TempDir
    private Path tempDir;

    private AppProperties appProperties;
    private TranscodeFileProcessor processor;

    private TranscodeCandidate candidate;
    private StorageEngine sourceEngine;
    private StorageEngine targetEngine;
    private SyncTask syncTask;
    private TaskExecution execution;

    /** 模拟数据库中的同一条转码任务记录，stateWriter 的 save/reload 均围绕它进行 */
    private TranscodeTask taskRecord;
    /** save / saveAndReload 落库时的状态序列（用于断言状态推进） */
    private final List<TranscodeStatus> savedStatuses = new ArrayList<>();
    /** pushProgress 推送时的状态序列（用于断言进度广播时机） */
    private final List<TranscodeStatus> pushedStatuses = new ArrayList<>();
    /** doTranscode 收到的码率（用于断言使用配置默认码率） */
    private final List<Integer> transcodedBitrates = new ArrayList<>();
    private final List<Long> reloadedTaskIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getTranscode().setTempDir(tempDir.toString());
        appProperties.getTranscode().setTempSuffix(".tmp");
        appProperties.getTranscode().setDefaultBitrate(128_000);
        appProperties.getTranscode().setMaxConcurrentTranscode(32);

        processor = Mockito.spy(new TranscodeFileProcessor(
            storageEngineService, appProperties, retryService, stateWriter));

        sourceEngine = new StorageEngine();
        sourceEngine.setId(1L);
        sourceEngine.setName("源引擎");

        targetEngine = new StorageEngine();
        targetEngine.setId(2L);
        targetEngine.setName("目标引擎");

        candidate = new TranscodeCandidate(
            "test-video.mp4", "/videos/test-video.mp4",
            "/output/test-video.mp3", "MP4", 50_000_000L, sourceEngine);

        syncTask = new SyncTask();
        syncTask.setId(1L);
        syncTask.setName("测试同步任务");

        execution = new TaskExecution();
        execution.setId(100L);
        execution.setStatus(TaskExecution.ExecutionStatus.RUNNING);
    }

    @AfterEach
    void tearDown() {
        // 中断标志与 MDC 都是线程级状态，测试结束必须复位，避免污染后续用例
        Thread.interrupted();
        TraceContext.clear();
    }

    // ================================================================
    // process — 正常流水线
    // ================================================================

    @Test
    @DisplayName("process — 正常获取和释放信号量，三步流水线推进到 COMPLETED")
    void shouldAcquireAndReleaseSemaphore() throws Exception {
        stubSuccessfulPipeline();

        CompletableFuture<TranscodeResult> future = process();
        TranscodeResult result = future.get(10, TimeUnit.SECONDS);

        assertTrue(result.success(), "依赖已全部桩化，流水线不应失败：" + result.error());
        assertNull(result.error());
        assertEquals("test-video.mp4", result.sourceFileName());
        // 状态推进：下载 → 转码 → 上传 → 完成（去重后按序断言）
        assertEquals(List.of(TranscodeStatus.DOWNLOADING, TranscodeStatus.TRANSCODING,
            TranscodeStatus.UPLOADING, TranscodeStatus.COMPLETED), distinct(savedStatuses));
        assertEquals(distinct(savedStatuses), distinct(pushedStatuses), "每次状态落库都应伴随进度推送");
        assertEquals(TranscodeStatus.COMPLETED, taskRecord.getStatus());
        assertEquals(1000, taskRecord.getProgress().intValue());
        assertEquals(0, taskRecord.getRetryCount(), "成功后重试计数应重置");
        assertEquals(List.of(128_000), transcodedBitrates, "应使用配置的默认码率");
        // 生产实现有两处重载点：转码完成后取最新版本号（写入产物路径 + UPLOADING），
        // 以及标记 COMPLETED 前再取一次（规避乐观锁冲突）。两处都必须用已落库的 id，不得为 null。
        assertEquals(List.of(TASK_ID, TASK_ID), reloadedTaskIds, "重载必须使用已落库的任务 id");
        // 产物上传路径 = 目标目录 + 源文件名换目标扩展名
        verify(targetStrategy).uploadFile(eq(targetEngine), eq("/output/test-video.mp3"), any(), anyLong());
        // 成功后清理源临时文件与转码产物
        assertFalse(Files.exists(Path.of(taskRecord.getTempFilePath())), "成功后应删除转码产物");
        assertFalse(Files.exists(Path.of(taskRecord.getTempSourcePath())), "成功后应删除源临时文件");
        // 信号量归还
        assertEquals(32, processor.semaphore.availablePermits(), "任务结束后许可必须全部释放");
    }

    @Test
    @DisplayName("process — 返回的 future 承载成功的 TranscodeResult（类型契约）")
    void shouldReturnCompletableFuture() throws Exception {
        stubSuccessfulPipeline();

        CompletableFuture<TranscodeResult> future = process();
        TranscodeResult result = future.get(10, TimeUnit.SECONDS);

        assertFalse(future.isCompletedExceptionally(), "流水线异常不应透出为 future 异常完成");
        assertEquals(TranscodeResult.class, result.getClass());
        assertTrue(result.success());
        assertNull(result.error());
        assertEquals(candidate.name(), result.sourceFileName());
    }

    // ================================================================
    // process — 信号量并发上限
    // ================================================================

    @Test
    @DisplayName("init — 信号量许可数恰好等于配置的并发上限")
    void shouldInitSemaphoreWithConfiguredMax() {
        appProperties.getTranscode().setMaxConcurrentTranscode(16);

        processor.init();

        assertEquals(16, processor.semaphore.availablePermits(),
            "许可数必须等于配置值（写死为 1 或 32 时本断言失败）");
        int acquired = 0;
        while (processor.semaphore.tryAcquire()) {
            acquired++;
        }
        assertEquals(16, acquired, "可获取的许可数应恰好为 16，第 17 次获取必须失败");
        processor.semaphore.release(16);
        assertEquals(16, processor.semaphore.availablePermits());
    }

    @Test
    @DisplayName("process — 并发上限为 1 时第 2 个任务被信号量阻塞，释放后两个任务都成功")
    void shouldBlockSecondTaskWhenConcurrencyLimitIsOne() throws Exception {
        appProperties.getTranscode().setMaxConcurrentTranscode(1);
        // init() 按配置创建 1 个许可；转码步骤由本用例自行接管（阻塞在 latch 上），
        // 因此只桩化引擎策略与下载流，不注册默认的 doTranscode 桩（否则会成为多余桩）
        stubPropertiesOnly();
        stubEngineStrategies();

        CountDownLatch firstInTranscode = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger transcodeCalls = new AtomicInteger();
        doAnswer(invocation -> {
            transcodeCalls.incrementAndGet();
            firstInTranscode.countDown();
            assertTrue(releaseFirst.await(10, TimeUnit.SECONDS), "等待放行超时");
            return null;
        }).when(processor).doTranscode(any(Path.class), any(Path.class),
            any(TargetFormat.class), anyInt(), any(TranscodeTask.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<CompletableFuture<TranscodeResult>> firstTask = this::process;
            Future<CompletableFuture<TranscodeResult>> first = pool.submit(firstTask);
            assertTrue(firstInTranscode.await(10, TimeUnit.SECONDS), "第一个任务未进入转码步骤");
            assertEquals(0, processor.semaphore.availablePermits(), "第一个任务应独占唯一许可");

            CountDownLatch secondStarted = new CountDownLatch(1);
            Callable<CompletableFuture<TranscodeResult>> secondTask = () -> {
                secondStarted.countDown();
                return process();
            };
            Future<CompletableFuture<TranscodeResult>> second = pool.submit(secondTask);
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS), "第二个任务未启动");
            assertEquals(1, transcodeCalls.get(), "许可被占用时第二个任务不得进入转码步骤");

            releaseFirst.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS).success());
            assertTrue(second.get(10, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS).success());
        } finally {
            pool.shutdownNow();
        }

        assertEquals(2, transcodeCalls.get(), "放行后第二个任务应继续执行");
        assertEquals(1, processor.semaphore.availablePermits(), "两个任务结束后许可必须归还");
    }

    @Test
    @DisplayName("process — 信号量获取被中断时返回失败结果并恢复中断标志")
    void shouldReturnFailureWhenSemaphoreAcquireIsInterrupted() {
        processor.semaphore = new Semaphore(0) {
            @Override
            public void acquire() throws InterruptedException {
                throw new InterruptedException("模拟线程中断");
            }
        };

        TranscodeResult result = process().join();
        boolean interruptedFlagRestored = Thread.currentThread().isInterrupted();
        Thread.interrupted(); // 立即复位，避免影响同一用例内的后续断言

        assertFalse(result.success());
        assertEquals("线程被中断", result.error());
        assertEquals("test-video.mp4", result.sourceFileName());
        assertTrue(interruptedFlagRestored, "中断标志必须被恢复（Thread.currentThread().interrupt()）");
        assertEquals("InterruptedException", MDC.get(TraceContext.MDC_ERROR_TYPE));
        assertEquals("transcode", MDC.get(TraceContext.MDC_MODULE));
        assertEquals("单文件转码：test-video.mp4", MDC.get(TraceContext.MDC_OPERATION));
        verifyNoInteractions(stateWriter, storageEngineService);
    }

    // ================================================================
    // process — 失败步骤的状态落点
    // ================================================================

    @Test
    @DisplayName("process — 下载步骤业务失败：置 DOWNLOAD_FAILED 且不调度自动重试")
    void shouldMarkDownloadFailedOnBusinessError() throws Exception {
        stubPropertiesOnly();
        // 源引擎未设置 → 业务异常（非瞬时故障），不应触发自动重试
        TranscodeCandidate orphan = new TranscodeCandidate(
            "test-video.mp4", "/videos/test-video.mp4", "/output/test-video.mp3", "MP4", 0L, null);

        TranscodeResult result = process(orphan).get(10, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertTrue(result.error().contains("源存储引擎未设置"), "错误信息应说明失败原因");
        assertEquals(TranscodeStatus.DOWNLOAD_FAILED, taskRecord.getStatus());
        assertEquals(result.error(), taskRecord.getErrorMessage());
        assertEquals(0, taskRecord.getRetryCount());
        verify(retryService, never()).scheduleRetry(anyInt(), anyString(), any(), any());
        verify(storageEngineService, never()).resolve(any());
    }

    @Test
    @DisplayName("process — 下载返回 null（瞬时故障）：递增 retryCount 并调度自动重试")
    void shouldScheduleAutoRetryForRetryableDownloadFailure() throws Exception {
        stubPropertiesOnly();
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(sourceStrategy.downloadFile(sourceEngine, "/videos/test-video.mp4")).thenReturn(null);
        when(retryService.isRetryable(any())).thenReturn(true);
        when(retryService.getMaxAutoRetries()).thenReturn(3);

        TranscodeResult result = process().get(10, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertTrue(result.error().contains("下载源文件失败"));
        assertEquals(TranscodeStatus.DOWNLOAD_FAILED, taskRecord.getStatus());
        assertEquals(1, taskRecord.getRetryCount());
        verify(retryService).scheduleRetry(eq(1), eq("test-video.mp4"), any(), any());
    }

    @Test
    @DisplayName("process — 自动重试动作复用同一任务记录并从失败步骤继续到 COMPLETED")
    void shouldReuseTaskRecordOnAutoRetry() throws Exception {
        stubPropertiesOnly();
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(storageEngineService.resolve(targetEngine)).thenReturn(targetStrategy);
        when(sourceStrategy.downloadFile(any(), anyString())).thenReturn(null);
        when(retryService.isRetryable(any())).thenReturn(true);
        when(retryService.getMaxAutoRetries()).thenReturn(3);

        process().get(10, TimeUnit.SECONDS);
        TranscodeTask firstRecord = taskRecord;
        assertEquals(TranscodeStatus.DOWNLOAD_FAILED, firstRecord.getStatus());

        ArgumentCaptor<Runnable> retryAction = ArgumentCaptor.forClass(Runnable.class);
        verify(retryService).scheduleRetry(eq(1), eq("test-video.mp4"), retryAction.capture(), any());

        // 第二次下载成功，流水线应继续走完转码与上传
        when(sourceStrategy.downloadFile(any(), anyString()))
            .thenAnswer(invocation -> new ByteArrayInputStream(new byte[16]));
        stubDoTranscode();
        savedStatuses.clear();

        retryAction.getValue().run();

        assertSame(firstRecord, taskRecord, "自动重试必须复用同一任务记录，不得新建任务");
        assertEquals(TranscodeStatus.COMPLETED, taskRecord.getStatus());
        assertEquals(0, taskRecord.getRetryCount(), "成功后重试计数应重置");
        assertEquals(1, savedStatuses.stream().filter(s -> s == TranscodeStatus.TRANSCODING).count(),
            "重试再次进入了转码步骤");
    }

    @Test
    @DisplayName("process — 自动重试用尽：不再调度并在错误信息追加提示")
    void shouldAppendHintWhenAutoRetryExhausted() throws Exception {
        stubPropertiesOnly();
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(sourceStrategy.downloadFile(any(), anyString())).thenReturn(null);
        when(retryService.isRetryable(any())).thenReturn(true);
        when(retryService.getMaxAutoRetries()).thenReturn(3);

        // 首次失败：调度第 1 次自动重试
        process().get(10, TimeUnit.SECONDS);
        ArgumentCaptor<Runnable> retryAction = ArgumentCaptor.forClass(Runnable.class);
        verify(retryService).scheduleRetry(eq(1), eq("test-video.mp4"), retryAction.capture(), any());
        assertEquals(1, taskRecord.getRetryCount());

        // 模拟重试已执行到上限（retryCount = 3）后再失败一次
        taskRecord.setRetryCount(3);
        retryAction.getValue().run();

        assertNotNull(taskRecord.getErrorMessage(), "重试用尽仍需保留原始错误信息");
        assertTrue(taskRecord.getErrorMessage().endsWith("（自动重试用尽）"),
            "重试用尽应在错误信息追加提示，实际：" + taskRecord.getErrorMessage());
        assertEquals(3, taskRecord.getRetryCount(), "达到上限后不得继续递增");
        assertEquals(TranscodeStatus.DOWNLOAD_FAILED, taskRecord.getStatus());
        verify(retryService, times(1)).scheduleRetry(anyInt(), anyString(), any(), any());
    }

    @Test
    @DisplayName("process — 上传失败：置 UPLOAD_FAILED 并保留转码产物供重试")
    void shouldMarkUploadFailedAndKeepArtifact() throws Exception {
        stubSuccessfulPipeline();
        doThrow(new RuntimeException("目标引擎 503")).when(targetStrategy)
            .uploadFile(any(), anyString(), any(), anyLong());

        TranscodeResult result = process().get(10, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertTrue(result.error().contains("上传转码文件失败"));
        assertEquals(TranscodeStatus.UPLOAD_FAILED, taskRecord.getStatus());
        assertTrue(taskRecord.getTempFilePath().endsWith(".mp3"));
        assertTrue(Files.exists(Path.of(taskRecord.getTempFilePath())),
            "上传失败需保留转码产物，否则手动重试需重新转码");
    }

    // ================================================================
    // buildEncodingAttributes — 三种目标格式的参数差异
    // ================================================================

    @ParameterizedTest(name = "[{index}] {0} → 输出格式 {1}，视频参数={2}")
    @CsvSource({"MP3, mp3, false", "MP4, mp4, true", "FLV, flv, true"})
    @DisplayName("buildEncodingAttributes — 输出格式与视频参数的有无按目标格式区分")
    void shouldBuildExpectedOutputFormatAndVideoAttributes(TargetFormat targetFormat,
                                                           String expectedOutputFormat,
                                                           boolean expectVideo) {
        EncodingAttributes attrs = processor.buildEncodingAttributes(targetFormat, 128_000);

        assertEquals(Optional.of(expectedOutputFormat), attrs.getOutputFormat());
        assertEquals(expectVideo, attrs.getVideoAttributes().isPresent(),
            targetFormat + " 的视频参数存在性不符合预期");
        assertTrue(attrs.getAudioAttributes().isPresent(), "三种目标格式都必须携带音频参数");
    }

    @Test
    @DisplayName("buildEncodingAttributes — MP3 不应抛异常且携带完整音频参数（无视频参数）")
    void shouldSupportMp3Format() {
        EncodingAttributes attrs = processor.buildEncodingAttributes(TargetFormat.MP3, 64_000);

        assertEquals(Optional.of("mp3"), attrs.getOutputFormat());
        assertTrue(attrs.getVideoAttributes().isEmpty(), "MP3 为纯音频，不得携带视频参数");
        AudioAttributes audio = attrs.getAudioAttributes().orElseThrow();
        assertEquals(Optional.of(64_000), audio.getBitRate());
        assertEquals(Optional.of(2), audio.getChannels());
        assertEquals(Optional.of(44_100), audio.getSamplingRate());
        assertTrue(audio.getCodec().isEmpty(), "codec 置空以让 FFmpeg 自动选择编码器");
    }

    @Test
    @DisplayName("buildEncodingAttributes — MP4 不应抛异常且视频/音频参数齐备")
    void shouldSupportMp4Format() {
        EncodingAttributes attrs = processor.buildEncodingAttributes(TargetFormat.MP4, 96_000);

        assertEquals(Optional.of("mp4"), attrs.getOutputFormat());
        VideoAttributes video = attrs.getVideoAttributes().orElseThrow();
        assertTrue(video.getCodec().isEmpty(), "codec 置空以让 FFmpeg 自动选择编码器");
        AudioAttributes audio = attrs.getAudioAttributes().orElseThrow();
        assertEquals(Optional.of(96_000), audio.getBitRate());
        assertEquals(Optional.of(2), audio.getChannels());
    }

    @Test
    @DisplayName("buildEncodingAttributes — FLV 不应抛异常且视频/音频参数齐备")
    void shouldSupportFlvFormat() {
        EncodingAttributes attrs = processor.buildEncodingAttributes(TargetFormat.FLV, 32_000);

        assertEquals(Optional.of("flv"), attrs.getOutputFormat());
        assertTrue(attrs.getVideoAttributes().isPresent());
        AudioAttributes audio = attrs.getAudioAttributes().orElseThrow();
        assertEquals(Optional.of(32_000), audio.getBitRate());
        assertEquals(Optional.of(44_100), audio.getSamplingRate());
    }

    // ================================================================
    // TranscodeProgressListener — 50‰ 节流
    // ================================================================

    @Test
    @DisplayName("TranscodeProgressListener — 进度落库按 50‰ 阈值节流（唯一写库保护）")
    void shouldThrottleProgressPersistenceByPermilThreshold() throws Exception {
        EncoderProgressListener listener = newProgressListener(taskWithId(TASK_ID));

        listener.progress(0);    // 0 - 0 = 0 < 50 → 不落库
        listener.progress(10);   // 10 < 50 → 不落库
        listener.progress(49);   // 49 < 50 → 不落库
        verify(stateWriter, never()).persistProgress(anyLong(), anyInt());

        listener.progress(50);   // 50 ≥ 50 → 落库 50
        listener.progress(99);   // 99 - 50 = 49 < 50 → 不落库
        listener.progress(100);  // 100 - 50 = 50 ≥ 50 → 落库 100

        InOrder inOrder = inOrder(stateWriter);
        inOrder.verify(stateWriter).persistProgress(TASK_ID, 50);
        inOrder.verify(stateWriter).persistProgress(TASK_ID, 100);
        verify(stateWriter, times(2)).persistProgress(anyLong(), anyInt());
    }

    @Test
    @DisplayName("TranscodeProgressListener — 构造时绑定任务 id，回调节流不改变落库目标")
    void shouldBindTaskIdToProgressListener() throws Exception {
        EncoderProgressListener listener = newProgressListener(taskWithId(77L));

        listener.progress(60);   // 首发即落库（60 ≥ 50）
        listener.progress(200);  // 200 - 60 = 140 ≥ 50 → 落库

        InOrder inOrder = inOrder(stateWriter);
        inOrder.verify(stateWriter).persistProgress(77L, 60);
        inOrder.verify(stateWriter).persistProgress(77L, 200);
        // 其余回调（sourceInfo / message）为空实现，不应产生副作用
        listener.sourceInfo(null);
        listener.message("转码中");
        verify(stateWriter, times(2)).persistProgress(anyLong(), anyInt());
    }

    // ================================================================
    // TranscodeResult / TranscodeCandidate 不可变数据类
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

    @Test
    @DisplayName("TranscodeCandidate — 正确保存所有字段")
    void shouldStoreAllCandidateFields() {
        assertEquals("test-video.mp4", candidate.name());
        assertEquals("/videos/test-video.mp4", candidate.fullPath());
        assertEquals("/output/test-video.mp3", candidate.targetPath());
        assertEquals("MP4", candidate.format());
        assertEquals(50_000_000L, candidate.size());
        assertSame(sourceEngine, candidate.sourceEngine());
    }

    // ================================================================
    // 测试辅助
    // ================================================================

    private CompletableFuture<TranscodeResult> process() {
        return process(candidate);
    }

    private CompletableFuture<TranscodeResult> process(TranscodeCandidate target) {
        return processor.process(target, TargetFormat.MP3, ".tmp", tempDir,
            targetEngine, syncTask, execution);
    }

    /** 仅桩化配置与状态写入器（供不进入转码步骤的失败用例使用） */
    private void stubPropertiesOnly() {
        processor.init();
        stubStateWriter();
    }

    /** 桩化状态写入器：save/saveAndReload/reloadTask 围绕同一条记录往返，并记录状态序列 */
    private void stubStateWriter() {
        when(stateWriter.save(any(TranscodeTask.class))).thenAnswer(invocation -> {
            TranscodeTask saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(TASK_ID);
            }
            taskRecord = saved;
            savedStatuses.add(saved.getStatus());
            return saved;
        });
        when(stateWriter.saveAndReload(any(TranscodeTask.class))).thenAnswer(invocation -> {
            TranscodeTask saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(TASK_ID);
            }
            taskRecord = saved;
            savedStatuses.add(saved.getStatus());
            return saved;
        });
        when(stateWriter.reloadTask(anyLong())).thenAnswer(invocation -> {
            reloadedTaskIds.add(invocation.getArgument(0));
            return taskRecord;
        });
        doAnswer(invocation -> {
            pushedStatuses.add(((TranscodeTask) invocation.getArgument(0)).getStatus());
            return null;
        }).when(stateWriter).pushProgress(any(TranscodeTask.class));
    }

    /** 桩化完整成功流水线：源/目标策略、下载流、doTranscode（隔离真实 FFmpeg） */
    private void stubSuccessfulPipeline() {
        stubPropertiesOnly();
        stubPipelineDependencies();
    }

    private void stubPipelineDependencies() {
        stubEngineStrategies();
        stubDoTranscode();
    }

    /** 桩化源/目标引擎策略解析与下载流（不含 doTranscode，供自行接管转码步骤的用例使用） */
    private void stubEngineStrategies() {
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(storageEngineService.resolve(targetEngine)).thenReturn(targetStrategy);
        when(sourceStrategy.downloadFile(any(), anyString()))
            .thenAnswer(invocation -> new ByteArrayInputStream(new byte[16]));
    }

    /** 隔离 FFmpeg：真实编码不在单测范围，仅记录调用参数 */
    private void stubDoTranscode() {
        doAnswer(invocation -> {
            transcodedBitrates.add(invocation.getArgument(3));
            return null;
        }).when(processor).doTranscode(any(Path.class), any(Path.class),
            any(TargetFormat.class), anyInt(), any(TranscodeTask.class));
    }

    /** 反射构造私有内部类 TranscodeProgressListener（唯一驱动 50‰ 节流的方式） */
    private EncoderProgressListener newProgressListener(TranscodeTask task) throws Exception {
        Class<?> listenerClass = Arrays.stream(TranscodeFileProcessor.class.getDeclaredClasses())
            .filter(type -> "TranscodeProgressListener".equals(type.getSimpleName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("未找到 TranscodeProgressListener 内部类"));
        Constructor<?> constructor = listenerClass.getDeclaredConstructor(
            TranscodeFileProcessor.class, TranscodeTask.class);
        constructor.setAccessible(true);
        return (EncoderProgressListener) constructor.newInstance(processor, task);
    }

    private static TranscodeTask taskWithId(Long id) {
        TranscodeTask task = new TranscodeTask();
        task.setId(id);
        task.setStatus(TranscodeStatus.TRANSCODING);
        task.setProgress(0);
        return task;
    }

    /** 去重相邻重复项，仅保留状态切换顺序 */
    private static List<TranscodeStatus> distinct(List<TranscodeStatus> statuses) {
        List<TranscodeStatus> result = new ArrayList<>();
        for (TranscodeStatus status : statuses) {
            if (result.isEmpty() || result.get(result.size() - 1) != status) {
                result.add(status);
            }
        }
        return result;
    }
}
