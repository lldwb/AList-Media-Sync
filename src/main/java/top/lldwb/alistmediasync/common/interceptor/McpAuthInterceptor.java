package top.lldwb.alistmediasync.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.dto.ApiResult;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * MCP 服务器认证拦截器
 * <p>
 * 基于独立 Bearer Token 认证（FR-008），与 Web 管理界面 Basic Auth（AuthInterceptor）凭据完全隔离。
 * 校验 {@code Authorization: Bearer <token>} 与配置的 {@code app.mcp.token} 恒定时间比较（防时序侧信道）。
 * 认证失败返回 HTTP 401 + 统一错误结构，MUST NOT 泄露任何业务数据与配置细节（SC-004）。
 * </p>
 * <p>
 * 仅当 MCP 服务器启用（app.mcp.enabled=true）时装配，注册到 {@code /mcp} 路径（WebMvcConfig）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class McpAuthInterceptor implements HandlerInterceptor {

    private final AppProperties appProperties;
    private final JsonMapper objectMapper;

    public McpAuthInterceptor(AppProperties appProperties, JsonMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("MCP 请求认证头缺失或格式无效：{} {}（来源：{}）", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr());
            sendUnauthorized(response, "缺少 MCP 认证令牌");
            return false;
        }

        String providedToken = authHeader.substring("Bearer ".length()).trim();
        String expectedToken = appProperties.getMcp().getToken();

        // 恒定时间比较，避免时序侧信道泄露令牌信息
        if (!constantTimeEquals(providedToken, expectedToken)) {
            log.warn("MCP 认证失败，令牌无效：{} {}（来源：{}）", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr());
            sendUnauthorized(response, "MCP 认证失败，令牌无效");
            return false;
        }

        // 认证通过，放行至 MCP 协议处理（X-Trace-Id 由 TraceIdFilter 统一注入）
        return true;
    }

    /**
     * 恒定时间字符串比较
     */
    private boolean constantTimeEquals(String provided, String expected) {
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        if (providedBytes.length != expectedBytes.length) {
            return false;
        }
        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResult.error(401, message)));
    }
}
