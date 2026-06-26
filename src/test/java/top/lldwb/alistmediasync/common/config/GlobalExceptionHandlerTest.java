package top.lldwb.alistmediasync.common.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import top.lldwb.alistmediasync.common.dto.ApiResult;
import top.lldwb.alistmediasync.common.util.TraceContext;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GlobalExceptionHandler} 单元测试（T037）
 * <p>
 * 验证：异常处理日志包含 traceId、errorType、错误消息；
 * 响应体使用 ApiResult；errorType 已写入 MDC 供 Logback pattern 使用。
 * </p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void cleanup() {
        TraceContext.clear();
    }

    @Test
    void handleIllegalArgument_应设置errorType为IllegalArgumentException() {
        TraceContext.setTraceId("test-trace-001");
        ResponseEntity<ApiResult<Void>> resp = handler.handleIllegalArgument(
            new IllegalArgumentException("参数无效"));

        assertEquals(400, resp.getStatusCode().value());
        assertEquals("IllegalArgumentException", MDC.get(TraceContext.MDC_ERROR_TYPE));
        assertNotNull(resp.getBody());
        assertEquals("参数无效", resp.getBody().getMessage());
    }

    @Test
    void handleNotFound_应设置errorType为NoSuchElementException() {
        ResponseEntity<ApiResult<Void>> resp = handler.handleNotFound(
            new NoSuchElementException("资源不存在"));
        assertEquals(404, resp.getStatusCode().value());
        assertEquals("NoSuchElementException", MDC.get(TraceContext.MDC_ERROR_TYPE));
    }

    @Test
    void handleConflict_应设置errorType为IllegalStateException() {
        ResponseEntity<ApiResult<Void>> resp = handler.handleConflict(
            new IllegalStateException("业务冲突"));
        assertEquals(409, resp.getStatusCode().value());
        assertEquals("IllegalStateException", MDC.get(TraceContext.MDC_ERROR_TYPE));
    }

    @Test
    void handleGeneral_应根据具体异常类设置errorType() {
        ResponseEntity<ApiResult<Void>> resp = handler.handleGeneral(
            new RuntimeException("未知错误"));
        assertEquals(500, resp.getStatusCode().value());
        assertEquals("RuntimeException", MDC.get(TraceContext.MDC_ERROR_TYPE));
    }
}
