package top.lldwb.alistmediasync.transcode.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.enums.ConflictStrategy;
import top.lldwb.alistmediasync.common.enums.TargetFormat;
import top.lldwb.alistmediasync.common.util.TraceContext;
import top.lldwb.alistmediasync.execution.TaskExecution;
import top.lldwb.alistmediasync.execution.TaskExecutionRepository;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.service.PostSyncTranscodeTrigger;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.repository.TranscodeTaskRepository;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 转码任务 traceId 贯穿测试（T035）
 * <p>
 * 直接驱动真实的 {@link TranscodeService}，在<b>依赖被调用的执行路径上</b>抓取 MDC，
 * 断言 {@code executeAsync} / {@code executePostSyncTranscode} / {@code trigger} 三个入口
 * 的 module、operation、traceId 继承与生成语义、失败时的 errorType 以及执行结束后的清理。
 * </p>
 * <p>
 * 仅断言 {@code TraceContext} 自身的读写，测的是工具类而非服务，无法证明服务做了这些设置；
 * 因此所有抓取点都落在服务真实执行路径内部的 mock 依赖回调里。
 * </p>
 * <p>
 * 未覆盖 {@code retry}：该入口自身不调用 {@code TraceContext.runWith}（详见测试报告）。
 * 单文件转码步骤中的 FFmpeg 调用属于 {@link TranscodeFileProcessor} 的职责，不在本测试范围。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("转码任务 traceId 贯穿测试")
class TranscodeServiceTraceIdTest {

    /** 被测转码任务 ID（executeTask 内部按此 ID 重新加载实体） */
    private static final Long TASK_ID = 1L;
    /** 源存储引擎 ID */
    private static final Long SOURCE_ENGINE_ID = 1L;
    /** 目标存储引擎 ID */
    private static final Long TARGET_ENGINE_ID = 2L;
    /** 源文件路径（文件模式下 executeTask 不扫描目录） */
    private static final String SOURCE_FILE_PATH = "/videos/movie.mp4";
    /** 目标路径 */
    private static final String TARGET_FILE_PATH = "/output/movie.mp3";
    /** 临时文件后缀 */
    private static final String TEMP_SUFFIX = ".tmp";

    @Mock
    private TranscodeTaskRepository repository;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private StorageEngineService storageEngineService;

    @Mock
    private AppProperties appProperties;

    @Mock
    private AppProperties.Transcode transcodeConfig;

    @Mock
    private TranscodeFileProcessor fileProcessor;

    @Mock
    private TranscodeScanner scanner;

    @Mock
    private JsonMapper objectMapper;

    @Mock
    private ObjectProvider<TranscodeService> selfProvider;

    @Mock
    private StorageEngineStrategy sourceStrategy;

    @Mock
    private StorageEngineStrategy targetStrategy;

    @InjectMocks
    private TranscodeService service;

    @TempDir
    private Path tempDir;

    private StorageEngine sourceEngine;
    private StorageEngine targetEngine;
    /** 数据库中重新加载出来的转码任务实体（executeTask 以此为执行主体） */
    private TranscodeTask managedTask;

    @BeforeEach
    void setUp() {
        sourceEngine = engine(SOURCE_ENGINE_ID, "源引擎");
        targetEngine = engine(TARGET_ENGINE_ID, "目标引擎");

        managedTask = new TranscodeTask();
        managedTask.setId(TASK_ID);
        managedTask.setSourceEngineId(SOURCE_ENGINE_ID);
        managedTask.setTargetEngineId(TARGET_ENGINE_ID);
        managedTask.setSourceFilePath(SOURCE_FILE_PATH);
        managedTask.setTargetFilePath(TARGET_FILE_PATH);
        managedTask.setTargetFormat(TargetFormat.MP3);
        managedTask.setStatus(TranscodeTask.TranscodeStatus.PENDING);
    }

    @AfterEach
    void cleanup() {
        TraceContext.clear();
    }

    // ================================================================
    // 测试辅助：按需声明桩（严格桩校验下未使用的桩会直接失败）
    // ================================================================

    private static StorageEngine engine(Long id, String name) {
        StorageEngine engine = new StorageEngine();
        engine.setId(id);
        engine.setName(name);
        return engine;
    }

    /** 待执行的转码任务（executeAsync 的入参，服务内部会按 ID 重新加载） */
    private TranscodeTask pendingTask() {
        TranscodeTask task = new TranscodeTask();
        task.setId(TASK_ID);
        task.setSourceFilePath(SOURCE_FILE_PATH);
        task.setTargetFilePath(TARGET_FILE_PATH);
        task.setTargetFormat(TargetFormat.MP3);
        task.setStatus(TranscodeTask.TranscodeStatus.PENDING);
        return task;
    }

    /**
     * 桩：自代理返回<b>真实服务实例</b>，使 {@code executeAsync} 的
     * {@code TraceContext.runWith} 包裹到真实的 {@code executeTask} 上
     * （若返回 mock，则 runWith 内只剩一次空调用，无法验证执行路径）。
     */
    private void stubSelfProviderToRealService() {
        when(selfProvider.getObject()).thenReturn(service);
    }

    /** 桩：executeTask 前置——任务重载、引擎解析、源路径判定为文件模式、临时目录配置 */
    private void stubExecuteTaskPrologue() {
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(managedTask));
        when(storageEngineService.getEntity(SOURCE_ENGINE_ID)).thenReturn(sourceEngine);
        when(storageEngineService.getEntity(TARGET_ENGINE_ID)).thenReturn(targetEngine);
        when(scanner.isDirectory(sourceEngine, SOURCE_FILE_PATH)).thenReturn(false);
        when(appProperties.getTranscode()).thenReturn(transcodeConfig);
        when(transcodeConfig.getTempSuffix()).thenReturn(TEMP_SUFFIX);
        when(transcodeConfig.getTempDir()).thenReturn(tempDir.toString());
    }

    /** 桩：执行记录落库回传入参（服务内部会把返回值重新赋给 execution） */
    private void stubExecutionSave() {
        when(taskExecutionRepository.save(any(TaskExecution.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    /** 桩：后置转码前置——执行记录落库、源/目标引擎策略解析 */
    private void stubPostSyncPrologue() {
        stubExecutionSave();
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(storageEngineService.resolve(targetEngine)).thenReturn(targetStrategy);
    }

    /** 桩：失败明细序列化走真实 JsonMapper（保证编排级失败路径真实走完） */
    private void stubFailureDetailsSerialization() {
        JsonMapper realMapper = new JsonMapper();
        when(objectMapper.writeValueAsString(any())).thenAnswer(inv -> {
            try {
                return realMapper.writeValueAsString(inv.getArgument(0));
            } catch (Exception e) {
                throw new IllegalStateException("桩声明失败", e);
            }
        });
    }

    /** 在「单文件处理器被调用」这一执行路径内部抓取 MDC 快照 */
    private void captureMdcAtFileProcessor(AtomicReference<String> traceId,
                                           AtomicReference<String> module,
                                           AtomicReference<String> operation,
                                           TranscodeResult result) {
        when(fileProcessor.process(any(), any(), any(), any(), any(), any(), any()))
            .thenAnswer(inv -> {
                traceId.set(MDC.get(TraceContext.MDC_TRACE_ID));
                module.set(MDC.get(TraceContext.MDC_MODULE));
                operation.set(MDC.get(TraceContext.MDC_OPERATION));
                return CompletableFuture.completedFuture(result);
            });
    }

    /** 在「源目录扫描被调用」这一执行路径内部抓取 MDC 快照 */
    private void captureMdcAtScanner(AtomicReference<String> traceId,
                                     AtomicReference<String> module,
                                     AtomicReference<String> operation,
                                     List<TranscodeCandidate> candidates) {
        when(scanner.scanSourceDirectory(any(), any(), any(), any(), any(), any(), any()))
            .thenAnswer(inv -> {
                traceId.set(MDC.get(TraceContext.MDC_TRACE_ID));
                module.set(MDC.get(TraceContext.MDC_MODULE));
                operation.set(MDC.get(TraceContext.MDC_OPERATION));
                return candidates;
            });
    }

    /** 已同步成功的同步任务（后置转码的触发来源） */
    private SyncTask syncTask() {
        SyncTask syncTask = new SyncTask();
        syncTask.setId(7L);
        syncTask.setName("同步后置转码测试");
        syncTask.setSourceEngine(sourceEngine);
        syncTask.setTargetEngine(targetEngine);
        syncTask.setSourcePath("/videos");
        syncTask.setTargetPath("/output");
        syncTask.setTargetFormat(TargetFormat.MP3);
        syncTask.setConflictStrategy(ConflictStrategy.SKIP);
        return syncTask;
    }

    // ================================================================
    // 用例
    // ================================================================

    @Test
    @DisplayName("executeAsync 执行时应注入 module=transcode/operation=转码任务执行，并继承上游 traceId")
    void shouldInheritUpstreamTraceIdOnExecuteAsync() {
        TraceContext.setTraceId("upstream-transcode-trace-001");
        stubSelfProviderToRealService();
        stubExecuteTaskPrologue();
        stubExecutionSave();

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        AtomicReference<String> capturedModule = new AtomicReference<>();
        AtomicReference<String> capturedOperation = new AtomicReference<>();
        captureMdcAtFileProcessor(capturedTraceId, capturedModule, capturedOperation,
            new TranscodeResult("movie.mp4", true, null));

        service.executeAsync(pendingTask());

        assertEquals("upstream-transcode-trace-001", capturedTraceId.get(),
            "执行路径上应继承上游 traceId（跨线程由 MdcTaskDecorator 传递）");
        assertEquals("transcode", capturedModule.get(), "执行路径上 module 应为 transcode");
        assertEquals("转码任务执行", capturedOperation.get(), "执行路径上 operation 应为「转码任务执行」");
        // 上游 traceId 由外层拥有，服务不应清理
        assertEquals("upstream-transcode-trace-001", MDC.get(TraceContext.MDC_TRACE_ID),
            "非本次生成的 traceId 不应被服务清理");
    }

    @Test
    @DisplayName("executeAsync 无上游 traceId 时应生成合法 traceId 并在执行结束后清理 MDC")
    void shouldGenerateAndClearTraceIdWhenNoUpstreamOnExecuteAsync() {
        TraceContext.clear();
        stubSelfProviderToRealService();
        stubExecuteTaskPrologue();
        stubExecutionSave();

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        captureMdcAtFileProcessor(capturedTraceId, new AtomicReference<>(), new AtomicReference<>(),
            new TranscodeResult("movie.mp4", true, null));

        service.executeAsync(pendingTask());

        assertNotNull(capturedTraceId.get(), "执行路径上必须存在 traceId");
        assertTrue(TraceContext.isValid(capturedTraceId.get()),
            "自动生成的 traceId 应满足格式契约，实际=" + capturedTraceId.get());
        // 本次拥有 traceId → 执行结束后必须清理，避免线程复用时串号
        assertNull(MDC.get(TraceContext.MDC_TRACE_ID), "执行结束后应清理 traceId");
        assertNull(MDC.get(TraceContext.MDC_MODULE), "执行结束后应清理 module");
        assertNull(MDC.get(TraceContext.MDC_OPERATION), "执行结束后应清理 operation");
    }

    @Test
    @DisplayName("executeAsync 转码全部失败时应设置 errorType 结构化字段且上下文不丢失")
    void shouldSetErrorTypeWhenAllFilesFail() {
        TraceContext.clear();
        stubSelfProviderToRealService();
        stubExecuteTaskPrologue();
        stubFailureDetailsSerialization();

        // 失败路径先置 errorType 再落库 FAILED 记录，在落库回调中抓取
        AtomicReference<String> capturedErrorType = new AtomicReference<>();
        AtomicReference<String> capturedModule = new AtomicReference<>();
        AtomicReference<String> capturedOperation = new AtomicReference<>();
        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        when(taskExecutionRepository.save(any(TaskExecution.class))).thenAnswer(inv -> {
            TaskExecution exec = inv.getArgument(0);
            if (exec.getStatus() == TaskExecution.ExecutionStatus.FAILED) {
                capturedErrorType.set(MDC.get(TraceContext.MDC_ERROR_TYPE));
                capturedModule.set(MDC.get(TraceContext.MDC_MODULE));
                capturedOperation.set(MDC.get(TraceContext.MDC_OPERATION));
                capturedTraceId.set(MDC.get(TraceContext.MDC_TRACE_ID));
            }
            return exec;
        });

        // 单文件处理返回失败 → 编排层全部文件失败 → 抛出 RuntimeException
        when(fileProcessor.process(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(
                new TranscodeResult("movie.mp4", false, "FFmpeg 转码失败")));

        service.executeAsync(pendingTask());

        assertEquals("RuntimeException", capturedErrorType.get(),
            "编排级失败（全部文件失败）应将异常类名写入 errorType 结构化字段");
        assertEquals("transcode", capturedModule.get(), "失败路径的 module 仍应为 transcode");
        assertEquals("转码任务执行", capturedOperation.get(),
            "失败路径的 operation 仍应为「转码任务执行」");
        assertTrue(TraceContext.isValid(capturedTraceId.get()),
            "失败路径的 traceId 应仍然存在且合法，实际=" + capturedTraceId.get());
    }

    @Test
    @DisplayName("同步后置转码应注入 module=transcode/operation=同步后置转码，并沿用同步链路的 traceId")
    void shouldInheritSyncTraceIdOnPostSyncTranscode() {
        TraceContext.setTraceId("upstream-sync-trace-001");
        stubPostSyncPrologue();

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        AtomicReference<String> capturedModule = new AtomicReference<>();
        AtomicReference<String> capturedOperation = new AtomicReference<>();
        captureMdcAtScanner(capturedTraceId, capturedModule, capturedOperation, List.of());

        service.executePostSyncTranscode(syncTask(), new TaskExecution());

        assertEquals("upstream-sync-trace-001", capturedTraceId.get(),
            "后置转码应沿用同步链路的 traceId，使同步+转码视作同一次任务链路");
        assertEquals("transcode", capturedModule.get(), "后置转码路径上 module 应为 transcode");
        assertEquals("同步后置转码", capturedOperation.get(),
            "后置转码路径上 operation 应为「同步后置转码」");
        assertEquals("upstream-sync-trace-001", MDC.get(TraceContext.MDC_TRACE_ID),
            "非本次生成的 traceId 不应被服务清理");
    }

    @Test
    @DisplayName("同步后置转码无上游 traceId 时应生成合法 traceId 并在执行结束后清理 MDC")
    void shouldGenerateAndClearTraceIdWhenNoUpstreamOnPostSyncTranscode() {
        TraceContext.clear();
        stubPostSyncPrologue();

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        captureMdcAtScanner(capturedTraceId, new AtomicReference<>(), new AtomicReference<>(), List.of());

        service.executePostSyncTranscode(syncTask(), new TaskExecution());

        assertNotNull(capturedTraceId.get(), "后置转码执行路径上必须存在 traceId");
        assertTrue(TraceContext.isValid(capturedTraceId.get()),
            "自动生成的 traceId 应满足格式契约，实际=" + capturedTraceId.get());
        assertNull(MDC.get(TraceContext.MDC_TRACE_ID), "执行结束后应清理 traceId");
        assertNull(MDC.get(TraceContext.MDC_MODULE), "执行结束后应清理 module");
        assertNull(MDC.get(TraceContext.MDC_OPERATION), "执行结束后应清理 operation");
    }

    @Test
    @DisplayName("经 PostSyncTranscodeTrigger 接口触发时 traceId/module/operation 应贯穿到文件处理流水线")
    void shouldKeepTraceContextThroughTriggerIntoFilePipeline() {
        TraceContext.setTraceId("upstream-sync-trace-002");
        stubPostSyncPrologue();
        when(appProperties.getTranscode()).thenReturn(transcodeConfig);
        when(transcodeConfig.getTempSuffix()).thenReturn(TEMP_SUFFIX);
        when(transcodeConfig.getTempDir()).thenReturn(tempDir.toString());

        TranscodeCandidate candidate = new TranscodeCandidate(
            "movie.mp4", SOURCE_FILE_PATH, "/output/movie.mp4", "MP4", 1024L, sourceEngine);
        when(scanner.scanSourceDirectory(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(candidate));

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        AtomicReference<String> capturedModule = new AtomicReference<>();
        AtomicReference<String> capturedOperation = new AtomicReference<>();
        captureMdcAtFileProcessor(capturedTraceId, capturedModule, capturedOperation,
            new TranscodeResult("movie.mp4", true, null));

        // 依赖倒置路径：SyncService 只依赖接口，此处按接口类型调用
        PostSyncTranscodeTrigger trigger = service;
        trigger.trigger(syncTask(), new TaskExecution());

        assertEquals("upstream-sync-trace-002", capturedTraceId.get(),
            "接口触发路径上应沿用同步链路的 traceId，并贯穿到并行文件处理流水线");
        assertEquals("transcode", capturedModule.get(),
            "文件处理流水线上 module 仍应为 transcode");
        assertEquals("同步后置转码", capturedOperation.get(),
            "文件处理流水线上 operation 仍应为「同步后置转码」");
        assertEquals("upstream-sync-trace-002", MDC.get(TraceContext.MDC_TRACE_ID),
            "非本次生成的 traceId 不应被服务清理");
    }
}
