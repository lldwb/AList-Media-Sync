package top.lldwb.alistmediasync.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import top.lldwb.alistmediasync.common.config.AppProperties;

import java.util.Map;

/**
 * WebSocket 握手认证拦截器
 * <p>
 * 在 WebSocket HTTP Upgrade 握手阶段读取 Authorization 请求头进行 Basic Auth 验证。
 * 认证失败时拒绝 WebSocket 升级请求（返回 HTTP 401），前端不退化为 HTTP 轮询，
 * 而是引导用户重新登录。
 * </p>
 * <p>
 * 认证逻辑与 REST API 的 {@link AuthInterceptor} 保持一致，均基于 BCrypt 密码哈希验证。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final BasicAuthVerifier basicAuthVerifier;

    public WebSocketAuthInterceptor(AppProperties appProperties) {
        this.basicAuthVerifier = new BasicAuthVerifier(appProperties);
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes) {

        // 获取 Authorization 请求头
        String authHeader = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            authHeader = httpRequest.getHeader("Authorization");
        }

        // 用户名与 BCrypt 密码由 BasicAuthVerifier 校验
        BasicAuthVerifier.Result result;
        try {
            result = basicAuthVerifier.verify(authHeader);
        } catch (IllegalArgumentException e) {
            log.warn("WebSocket 握手认证凭据 Base64 解码失败：{}", e.getMessage());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        return switch (result.status()) {
            case SUCCESS -> {
                log.info("WebSocket 握手认证成功：用户 {}", result.username());
                yield true;
            }

            case MISSING_CREDENTIALS -> {
                log.debug("WebSocket 握手缺少认证信息");
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                yield false;
            }

            case MALFORMED_CREDENTIALS -> {
                log.warn("WebSocket 握手认证凭据格式无效");
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                yield false;
            }

            case USERNAME_MISMATCH -> {
                log.warn("WebSocket 握手认证失败：用户名错误（{}）", result.username());
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                yield false;
            }

            case PASSWORD_NOT_BCRYPT -> {
                log.error("WebSocket 握手认证：密码格式异常，未检测到 {bcrypt} 前缀");
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                yield false;
            }

            case PASSWORD_MISMATCH -> {
                log.warn("WebSocket 握手认证失败：密码错误（用户名：{}）", result.username());
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                yield false;
            }
        };
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception) {
        // 握手后无需额外处理
    }
}
