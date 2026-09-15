package top.lldwb.alistmediasync.common.interceptor;

import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.dto.ApiResult;

/**
 * 认证拦截器
 * <p>
 * 基于 HTTP Basic 认证，凭据与 application.yaml 中配置的用户名/密码（BCrypt 哈希）比对。
 * 不使用 Spring Security（YAGNI 原则），仅约 80 行代码实现所有认证需求。
 * 凭据解析与 BCrypt 校验复用 {@link BasicAuthVerifier}。
 * </p>
 * <p>
 * 密码始终以 {bcrypt} 格式存储（由 PasswordEncryptionPostProcessor 在启动时注入），
 * 拦截器仅处理 {bcrypt} 格式的密码验证。
 * </p>
 * <p>
 * 排除路径：/api/webhooks/**  /actuator/health  /h2-console/**  /v3/api-docs**  /swagger-ui**
 * <p>
 * SpringDoc 端点（/v3/api-docs、/swagger-ui）始终放行，生产环境通过
 * springdoc.*.enabled=false 禁用端点本身实现访问控制（见 application.yaml）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * 免认证路径（精确前缀匹配）。
     * 仅放行录播姬 Webhook 回调（/api/webhooks/recorder）；
     * 同前缀下的事件查询（/api/webhooks/events）等管理接口仍需认证。
     */
    private static final String[] EXCLUDE_PATHS = {
        "/api/webhooks/recorder",
        "/actuator/health",
        "/h2-console",
        // SpringDoc OpenAPI 端点：放行以便开发环境访问 Swagger UI 与 OpenAPI JSON/YAML。
        // 生产环境的访问控制由 springdoc.api-docs.enabled / springdoc.swagger-ui.enabled 控制
        //（通过环境变量 SPRINGDOC_API_DOCS_ENABLED / SPRINGDOC_SWAGGER_UI_ENABLED 设为 false 禁用端点）。
        "/v3/api-docs",
        "/swagger-ui"
    };

    private final BasicAuthVerifier basicAuthVerifier;
    private final JsonMapper objectMapper;

    public AuthInterceptor(AppProperties appProperties, JsonMapper objectMapper) {
        this.basicAuthVerifier = new BasicAuthVerifier(appProperties);
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

        // 从 Authorization 请求头提取凭据，用户名与 BCrypt 密码由 BasicAuthVerifier 校验
        String authHeader = request.getHeader("Authorization");
        BasicAuthVerifier.Result result;
        try {
            result = basicAuthVerifier.verify(authHeader);
        } catch (IllegalArgumentException e) {
            log.warn("认证凭据 Base64 解码失败：{}", e.getMessage());
            sendUnauthorized(response, "认证凭据格式无效");
            return false;
        }

        return switch (result.status()) {
            case SUCCESS -> true;

            case MISSING_CREDENTIALS -> {
                log.debug("请求缺少认证信息：{} {}", request.getMethod(), request.getRequestURI());
                sendUnauthorized(response, "缺少认证信息，请提供 Basic 认证凭据");
                yield false;
            }

            case MALFORMED_CREDENTIALS -> {
                log.warn("认证凭据格式无效：{} {}", request.getMethod(), request.getRequestURI());
                sendUnauthorized(response, "认证格式无效，请使用 username:password 格式");
                yield false;
            }

            case USERNAME_MISMATCH -> {
                log.warn("认证失败，用户名错误：{}（请求路径：{}）", result.username(), request.getRequestURI());
                sendUnauthorized(response, "用户名或密码错误");
                yield false;
            }

            case PASSWORD_NOT_BCRYPT -> {
                log.error("密码格式异常：未检测到 {bcrypt} 前缀，认证系统可能未正确初始化。"
                    + "请联系管理员检查 PasswordEncryptionPostProcessor 配置。");
                sendUnauthorized(response, "认证服务异常，请稍后重试");
                yield false;
            }

            case PASSWORD_MISMATCH -> {
                log.warn("认证失败，密码错误（用户名：{}，请求路径：{}）", result.username(), request.getRequestURI());
                sendUnauthorized(response, "用户名或密码错误");
                yield false;
            }
        };
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            objectMapper.writeValueAsString(ApiResult.error(401, message))
        );
    }
}
