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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    private final AppProperties appProperties;

    public WebSocketAuthInterceptor(AppProperties appProperties) {
        this.appProperties = appProperties;
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

        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            log.debug("WebSocket 握手缺少认证信息");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            String base64Credentials = authHeader.substring(6);
            byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(decodedBytes, StandardCharsets.UTF_8);
            String[] parts = credentials.split(":", 2);

            if (parts.length != 2) {
                log.warn("WebSocket 握手认证凭据格式无效");
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }

            String username = parts[0];
            String password = parts[1];

            // 验证用户名
            if (!appProperties.getAuth().getUsername().equals(username)) {
                log.warn("WebSocket 握手认证失败：用户名错误（{}）", username);
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }

            // 验证 BCrypt 密码
            String storedPassword = appProperties.getAuth().getPassword();
            if (!storedPassword.startsWith("{bcrypt}")) {
                log.error("WebSocket 握手认证：密码格式异常，未检测到 {bcrypt} 前缀");
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }

            String bcryptHash = storedPassword.substring(8);
            if (!org.springframework.security.crypto.bcrypt.BCrypt.checkpw(password, bcryptHash)) {
                log.warn("WebSocket 握手认证失败：密码错误（用户名：{}）", username);
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }

            log.info("WebSocket 握手认证成功：用户 {}", username);
            return true;

        } catch (IllegalArgumentException e) {
            log.warn("WebSocket 握手认证凭据 Base64 解码失败：{}", e.getMessage());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
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
