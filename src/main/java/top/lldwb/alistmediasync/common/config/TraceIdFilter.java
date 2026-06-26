package top.lldwb.alistmediasync.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.lldwb.alistmediasync.common.util.TraceContext;

import java.io.IOException;

/**
 * 请求级 traceId 过滤器
 * <p>
 * 在所有 HTTP 请求进入时：
 * <ol>
 *   <li>读取请求头 {@code X-Trace-Id}，若合法则沿用，否则生成新值。</li>
 *   <li>将 traceId 写入 MDC，使后续日志（Controller/Service/Repository/异常处理）自动携带。</li>
 *   <li>写入响应头 {@code X-Trace-Id}，保证成功、业务失败、认证失败和异常响应都返回该值。</li>
 *   <li>请求结束后清理 MDC，避免线程复用时污染下一个请求。</li>
 * </ol>
 * </p>
 *
 * @author AList-Media-Sync
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 请求/响应头名称 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String candidate = request.getHeader(HEADER_TRACE_ID);
        String traceId = TraceContext.resolveOrGenerate(candidate);

        try {
            TraceContext.setTraceId(traceId);
            // 提前写入响应头，确保异常路径也能被客户端读到
            response.setHeader(HEADER_TRACE_ID, traceId);
            filterChain.doFilter(request, response);
        } finally {
            // 兜底：异常分支或后续 ErrorPageFilter 仍可补写
            if (!response.containsHeader(HEADER_TRACE_ID)) {
                response.setHeader(HEADER_TRACE_ID, traceId);
            }
            TraceContext.clear();
        }
    }
}
