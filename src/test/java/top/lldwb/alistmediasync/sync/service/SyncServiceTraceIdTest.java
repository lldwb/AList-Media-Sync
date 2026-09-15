package top.lldwb.alistmediasync.sync.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.common.util.TraceContext;
import top.lldwb.alistmediasync.execution.TaskExecution;
import top.lldwb.alistmediasync.execution.TaskExecutionRepository;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.repository.SyncTaskRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 同步任务 traceId 贯穿测试（T034）
 * <p>
 * 直接驱动真实的 {@link SyncService}，在<b>依赖被调用的执行路径上</b>抓取 MDC，
 * 断言 module=sync、operation=同步任务执行、traceId 的继承/生成语义以及执行结束后的清理。
 * 仅断言 {@code TraceContext} 自身的读写，测的是工具类而非服务，无法证明服务做了这些设置。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("同步任务 traceId 贯穿测试")
class SyncServiceTraceIdTest {

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

    @BeforeEach
    void setUp() {
        sourceEngine = new StorageEngine();
        sourceEngine.setId(1L);
        sourceEngine.setName("源引擎");

        targetEngine = new StorageEngine();
        targetEngine.setId(2L);
        targetEngine.setName("目标引擎");

        syncTask = new SyncTask();
        syncTask.setId(1L);
        syncTask.setName("traceId 测试同步");
        syncTask.setSourceEngine(sourceEngine);
        syncTask.setTargetEngine(targetEngine);
        syncTask.setSourcePath("/videos");
        syncTask.setTargetPath("/backup");
        syncTask.setSyncMode(SyncTask.SyncMode.NEW_ONLY);
    }

    @AfterEach
    void cleanup() {
        TraceContext.clear();
    }

    // ================================================================
    // 测试辅助：按需声明桩（严格桩校验下未使用的桩会直接失败）
    // ================================================================

    private void stubTransaction() {
        when(transactionManager.getTransaction(any()))
            .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
    }

    private void stubTaskReload() {
        when(syncTaskRepository.findById(syncTask.getId())).thenReturn(Optional.of(syncTask));
    }

    private void stubTaskExecutionSave() {
        when(taskExecutionRepository.save(any(TaskExecution.class))).thenAnswer(inv -> {
            TaskExecution exec = inv.getArgument(0);
            if (exec.getId() == null) {
                exec.setId(100L);
            }
            return exec;
        });
    }

    private void stubNoConflictingExecution() {
        when(taskExecutionRepository.findByStatusAndTaskType(any(), any())).thenReturn(List.of());
    }

    private void stubEngineResolution() {
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(storageEngineService.resolve(targetEngine)).thenReturn(targetStrategy);
    }

    /** 抓取扫描阶段（真实执行路径内部）的 MDC 快照 */
    private void captureMdcOnSourceScan(AtomicReference<String> traceId,
                                        AtomicReference<String> module,
                                        AtomicReference<String> operation) {
        when(sourceStrategy.listFiles(eq(sourceEngine), anyString(), anyInt(), anyInt())).thenAnswer(inv -> {
            traceId.set(MDC.get(TraceContext.MDC_TRACE_ID));
            module.set(MDC.get(TraceContext.MDC_MODULE));
            operation.set(MDC.get(TraceContext.MDC_OPERATION));
            return List.of();
        });
    }

    // ================================================================
    // 用例
    // ================================================================

    @Test
    @DisplayName("同步任务执行入口应设置 module/operation 并继承上游 traceId")
    void shouldInheritUpstreamTraceIdOnExecutionEntry() {
        TraceContext.setTraceId("upstream-sync-trace-001");
        stubTransaction();
        stubTaskReload();
        stubTaskExecutionSave();
        stubNoConflictingExecution();
        stubEngineResolution();

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        AtomicReference<String> capturedModule = new AtomicReference<>();
        AtomicReference<String> capturedOperation = new AtomicReference<>();
        captureMdcOnSourceScan(capturedTraceId, capturedModule, capturedOperation);

        service.executeSyncTask(syncTask);

        assertEquals("upstream-sync-trace-001", capturedTraceId.get(),
            "执行路径上应继承上游 traceId（跨线程由 MdcTaskDecorator 传递）");
        assertEquals("sync", capturedModule.get(), "执行路径上 module 应为 sync");
        assertEquals("同步任务执行", capturedOperation.get(), "执行路径上 operation 应为「同步任务执行」");
        // 上游 traceId 由外层拥有，服务不应清理
        assertEquals("upstream-sync-trace-001", MDC.get(TraceContext.MDC_TRACE_ID),
            "非本次生成的 traceId 不应被服务清理");
    }

    @Test
    @DisplayName("无上游 traceId 时应生成合法 traceId 并在执行结束后清理 MDC")
    void shouldGenerateAndClearTraceIdWhenNoUpstream() {
        TraceContext.clear();
        stubTransaction();
        stubTaskReload();
        stubTaskExecutionSave();
        stubNoConflictingExecution();
        stubEngineResolution();

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        captureMdcOnSourceScan(capturedTraceId, new AtomicReference<>(), new AtomicReference<>());

        service.executeSyncTask(syncTask);

        assertNotNull(capturedTraceId.get(), "执行路径上必须存在 traceId");
        assertTrue(TraceContext.isValid(capturedTraceId.get()),
            "自动生成的 traceId 应满足格式契约，实际=" + capturedTraceId.get());
        // 本次拥有 traceId → 执行结束后必须清理，避免线程复用时串号
        assertNull(MDC.get(TraceContext.MDC_TRACE_ID), "执行结束后应清理 traceId");
        assertNull(MDC.get(TraceContext.MDC_MODULE), "执行结束后应清理 module");
        assertNull(MDC.get(TraceContext.MDC_OPERATION), "执行结束后应清理 operation");
    }

    @Test
    @DisplayName("同步任务失败时应设置 errorType 结构化字段")
    void shouldSetErrorTypeOnFailure() {
        TraceContext.clear();
        stubTransaction();
        stubTaskReload();
        stubNoConflictingExecution();
        stubEngineResolution();

        when(sourceStrategy.listFiles(eq(sourceEngine), anyString(), anyInt(), anyInt()))
            .thenThrow(new RuntimeException("模拟网络异常"));

        // 失败路径先 setErrorType 再落库 FAILED 记录，在落库回调中抓取
        AtomicReference<String> capturedErrorType = new AtomicReference<>();
        AtomicReference<String> capturedModule = new AtomicReference<>();
        when(taskExecutionRepository.save(any(TaskExecution.class))).thenAnswer(inv -> {
            TaskExecution exec = inv.getArgument(0);
            if (exec.getId() == null) {
                exec.setId(100L);
            }
            if (exec.getStatus() == TaskExecution.ExecutionStatus.FAILED) {
                capturedErrorType.set(MDC.get(TraceContext.MDC_ERROR_TYPE));
                capturedModule.set(MDC.get(TraceContext.MDC_MODULE));
            }
            return exec;
        });

        service.executeSyncTask(syncTask);

        assertEquals("RuntimeException", capturedErrorType.get(),
            "失败路径应将异常类名写入 errorType 结构化字段");
        assertEquals("sync", capturedModule.get(), "失败路径的 module 仍应为 sync");
    }

    @Test
    @DisplayName("任务不存在时异常传播且 MDC 仍被清理")
    void shouldClearMdcEvenWhenTaskNotFound() {
        TraceContext.clear();
        stubTransaction();
        when(syncTaskRepository.findById(syncTask.getId())).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () -> service.executeSyncTask(syncTask));

        assertNull(MDC.get(TraceContext.MDC_TRACE_ID), "异常路径同样应清理 traceId");
        assertNull(MDC.get(TraceContext.MDC_MODULE), "异常路径同样应清理 module");
    }
}
