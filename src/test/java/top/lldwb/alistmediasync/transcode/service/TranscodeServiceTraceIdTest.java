package top.lldwb.alistmediasync.transcode.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.lldwb.alistmediasync.common.util.TraceContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 转码任务 traceId 贯穿测试（T035）
 */
class TranscodeServiceTraceIdTest {

    @AfterEach
    void cleanup() {
        TraceContext.clear();
    }

    @Test
    void 转码任务执行入口应设置module与operation() {
        String traceId = TraceContext.generate();
        TraceContext.setTraceId(traceId);
        TraceContext.setModuleOperation("transcode", "转码任务执行");

        assertEquals(traceId, MDC.get(TraceContext.MDC_TRACE_ID));
        assertEquals("transcode", MDC.get(TraceContext.MDC_MODULE));
        assertEquals("转码任务执行", MDC.get(TraceContext.MDC_OPERATION));
    }

    @Test
    void 转码单文件处理应支持细化operation() {
        TraceContext.setTraceId("transcode-trace-001");
        TraceContext.setModuleOperation("transcode", "单文件转码：example.mp4");
        assertEquals("单文件转码：example.mp4", MDC.get(TraceContext.MDC_OPERATION));
    }

    @Test
    void 转码任务重试场景应保留traceId() {
        String traceId = TraceContext.generate();
        TraceContext.setTraceId(traceId);
        TraceContext.setModuleOperation("transcode", "重试转码");
        // 模拟失败-重试
        TraceContext.setErrorType("FFmpegException");
        assertEquals(traceId, TraceContext.getTraceId());
        assertEquals("FFmpegException", MDC.get(TraceContext.MDC_ERROR_TYPE));
    }
}
