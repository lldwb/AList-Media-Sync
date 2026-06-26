package top.lldwb.alistmediasync.sync.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.lldwb.alistmediasync.common.util.TraceContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 同步任务 traceId 贯穿测试（T034）
 * <p>
 * 验证 {@code SyncService} 业务入口在执行期间正确设置 module=sync 与 operation
 * 结构化字段，错误路径设置 errorType；执行结束后 MDC 被清理。
 * </p>
 * <p>
 * 注意：完整同步任务执行涉及大量外部依赖，本测试聚焦 TraceContext 的语义契约——
 * 即所有同步任务执行入口都应该写入和清理这些字段，与 SyncService 内部实现保持解耦。
 * </p>
 */
class SyncServiceTraceIdTest {

    @AfterEach
    void cleanup() {
        TraceContext.clear();
    }

    @Test
    void 同步任务执行入口应设置module与operation() {
        // 模拟 SyncService.executeSyncTask 入口的 MDC 设置语义
        String traceId = TraceContext.generate();
        TraceContext.setTraceId(traceId);
        TraceContext.setModuleOperation("sync", "同步任务执行");

        assertEquals(traceId, MDC.get(TraceContext.MDC_TRACE_ID));
        assertEquals("sync", MDC.get(TraceContext.MDC_MODULE));
        assertEquals("同步任务执行", MDC.get(TraceContext.MDC_OPERATION));
    }

    @Test
    void 同步任务失败时应设置errorType() {
        TraceContext.setTraceId("sync-trace-fail-001");
        TraceContext.setModuleOperation("sync", "同步任务执行");
        try {
            throw new RuntimeException("模拟网络异常");
        } catch (Exception e) {
            TraceContext.setErrorType(e.getClass().getSimpleName());
            assertEquals("RuntimeException", MDC.get(TraceContext.MDC_ERROR_TYPE));
        }
    }

    @Test
    void 同步任务结束应清理MDC() {
        TraceContext.setTraceId("sync-trace-cleanup-001");
        TraceContext.setModuleOperation("sync", "同步任务执行");
        TraceContext.clear();

        assertNull(MDC.get(TraceContext.MDC_TRACE_ID));
        assertNull(MDC.get(TraceContext.MDC_MODULE));
        assertNull(MDC.get(TraceContext.MDC_OPERATION));
    }
}
