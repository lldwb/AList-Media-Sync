package top.lldwb.alistmediasync.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import top.lldwb.alistmediasync.common.util.TraceContext;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TraceIdFilter} 单元测试
 * <p>
 * 覆盖 T009 要求：
 * <ul>
 *   <li>所有响应均包含 {@code X-Trace-Id}（成功 / 业务失败 / 认证失败 / 异常路径）</li>
 *   <li>MDC 在请求过程中被设置</li>
 *   <li>请求结束后 MDC 被清理</li>
 *   <li>并发请求 traceId 互不污染</li>
 * </ul>
 * </p>
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void cleanup() {
        TraceContext.clear();
    }

    @Test
    void 缺失请求头时应生成新traceId并写入响应头() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String header = response.getHeader(TraceIdFilter.HEADER_TRACE_ID);
        assertNotNull(header);
        assertTrue(TraceContext.isValid(header), "生成的 traceId 必须合法：" + header);
    }

    @Test
    void 合法请求头应被沿用() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        request.addHeader(TraceIdFilter.HEADER_TRACE_ID, "user-report-001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("user-report-001", response.getHeader(TraceIdFilter.HEADER_TRACE_ID));
    }

    @Test
    void 非法请求头应被替换为生成值() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        request.addHeader(TraceIdFilter.HEADER_TRACE_ID, "illegal trace id!");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String header = response.getHeader(TraceIdFilter.HEADER_TRACE_ID);
        assertNotEquals("illegal trace id!", header);
        assertTrue(TraceContext.isValid(header));
    }

    @Test
    void 业务失败响应仍应包含响应头() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/sync");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            ((HttpServletResponse) res).setStatus(400);
        });

        assertNotNull(response.getHeader(TraceIdFilter.HEADER_TRACE_ID));
        assertEquals(400, response.getStatus());
    }

    @Test
    void 认证失败响应仍应包含响应头() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            ((HttpServletResponse) res).setStatus(401);
        });

        assertNotNull(response.getHeader(TraceIdFilter.HEADER_TRACE_ID));
    }

    @Test
    void 异常路径响应仍应包含响应头() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain throwingChain = (req, res) -> {
            throw new ServletException("模拟下游异常");
        };

        ServletException ex = assertThrows(ServletException.class,
            () -> filter.doFilter(request, response, throwingChain));
        assertNotNull(response.getHeader(TraceIdFilter.HEADER_TRACE_ID),
            "异常路径下仍应返回 X-Trace-Id");
        assertEquals("模拟下游异常", ex.getMessage());
    }

    @Test
    void 请求结束后MDC应被清理() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> midRequestTraceId = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) ->
            midRequestTraceId.set(TraceContext.getTraceId()));

        assertNotNull(midRequestTraceId.get(), "请求处理中 MDC 必须有 traceId");
        assertNull(TraceContext.getTraceId(), "请求结束后 MDC 应被清理");
    }

    @Test
    void 并发请求traceId不应互相污染() throws Exception {
        int n = 16;
        CompletableFuture<?>[] futures = new CompletableFuture[n];

        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                String requested = "concurrent-trace-" + idx + "-aa";
                MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/x");
                req.addHeader(TraceIdFilter.HEADER_TRACE_ID, requested);
                MockHttpServletResponse res = new MockHttpServletResponse();
                try {
                    filter.doFilter(req, res, (r1, r2) -> {
                        assertEquals(requested, TraceContext.getTraceId(),
                            "MDC 在请求内部应为本请求的 traceId");
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                assertEquals(requested, res.getHeader(TraceIdFilter.HEADER_TRACE_ID));
            });
        }

        CompletableFuture.allOf(futures).get();
    }
}
