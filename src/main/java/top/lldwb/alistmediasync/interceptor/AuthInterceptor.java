package top.lldwb.alistmediasync.interceptor;

import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.lldwb.alistmediasync.config.AppProperties;
import top.lldwb.alistmediasync.dto.ApiResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 认证拦截器
 * <p>
 * 基于 HTTP Basic 认证，凭据与 application.yaml 中配置的用户名/密码（BCrypt 哈希）比对。
 * 不使用 Spring Security（YAGNI 原则），仅约 80 行代码实现所有认证需求。
 * </p>
 * <p>
 * 排除路径：/api/webhooks/**  /actuator/health  /h2-console/**
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String[] EXCLUDE_PATHS = {
        "/api/webhooks",
        "/actuator/health",
        "/h2-console"
    };

    private final AppProperties appProperties;
    private final JsonMapper objectMapper;

    public AuthInterceptor(AppProperties appProperties, JsonMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 跳过排除路径
        for (String exclude : EXCLUDE_PATHS) {
            if (path.startsWith(exclude)) {
                return true;
            }
        }

        // 从 Authorization 请求头提取凭据
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            sendUnauthorized(response, "缺少认证信息，请提供 Basic 认证凭据");
            return false;
        }

        try {
            String base64Credentials = authHeader.substring(6);
            byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(decodedBytes, StandardCharsets.UTF_8);
            String[] parts = credentials.split(":", 2);
            if (parts.length != 2) {
                sendUnauthorized(response, "认证格式无效，请使用 username:password 格式");
                return false;
            }

            String username = parts[0];
            String password = parts[1];

            // 验证用户名
            if (!appProperties.getAuth().getUsername().equals(username)) {
                sendUnauthorized(response, "用户名或密码错误");
                return false;
            }

            // 验证 BCrypt 密码（密码以 {bcrypt} 前缀存储）
            String storedPassword = appProperties.getAuth().getPassword();
            if (!storedPassword.startsWith("{bcrypt}")) {
                // 明文比较（仅开发环境回退）
                if (!storedPassword.equals(password)) {
                    sendUnauthorized(response, "用户名或密码错误");
                    return false;
                }
            } else {
                // BCrypt 哈希比较
                String bcryptHash = storedPassword.substring(8);
                // 使用 Spring Security Crypto 的 BCrypt 比较（不引入 Security 依赖，仅用 crypto）
                if (!org.springframework.security.crypto.bcrypt.BCrypt.checkpw(password, bcryptHash)) {
                    sendUnauthorized(response, "用户名或密码错误");
                    return false;
                }
            }

            return true;
        } catch (IllegalArgumentException e) {
            sendUnauthorized(response, "认证凭据格式无效");
            return false;
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            objectMapper.writeValueAsString(ApiResult.error(401, message))
        );
    }
}
