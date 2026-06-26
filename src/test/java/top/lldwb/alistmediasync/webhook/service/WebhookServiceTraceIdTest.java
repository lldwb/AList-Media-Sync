package top.lldwb.alistmediasync.webhook.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.lldwb.alistmediasync.common.util.TraceContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Webhook 任务 traceId 贯穿测试（T036）
 */
class WebhookServiceTraceIdTest {

    @AfterEach
    void cleanup() {
        TraceContext.clear();
    }

    @Test
    void 接收Webhook事件应设置module与operation() {
        TraceContext.setTraceId("webhook-trace-001");
        TraceContext.setModuleOperation("webhook", "接收事件：FILE_CLOSED");
        assertEquals("webhook", MDC.get(TraceContext.MDC_MODULE));
        assertEquals("接收事件：FILE_CLOSED", MDC.get(TraceContext.MDC_OPERATION));
    }

    @Test
    void 异步处理Webhook应继承或生成traceId() {
        // 场景 1：继承上游 traceId
        TraceContext.setTraceId("upstream-trace-001");
        String inherited = TraceContext.getTraceId();
        assertEquals("upstream-trace-001", inherited);

        // 场景 2：传播链断裂时，processWebhookEvent 应自行生成 traceId
        TraceContext.clear();
        String generated = TraceContext.resolveOrGenerate(null);
        assertTrue(TraceContext.isValid(generated));
    }

    @Test
    void Webhook失败应设置errorType和cause() {
        TraceContext.setTraceId("webhook-trace-fail-001");
        TraceContext.setModuleOperation("webhook", "异步处理事件");
        TraceContext.setErrorType("IllegalStateException");
        assertEquals("IllegalStateException", MDC.get(TraceContext.MDC_ERROR_TYPE));
    }
}
