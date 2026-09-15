package top.lldwb.alistmediasync.common.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.socket.WebSocketHandler;
import top.lldwb.alistmediasync.common.config.AppProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WebSocket 握手认证拦截器单元测试
 * <p>
 * 覆盖 WebSocket HTTP Upgrade 握手阶段（{@code beforeHandshake}）的认证边界：
 * 合法凭据放行、缺少/非 Basic 凭据、非法 Base64、凭据缺冒号、用户名错误、
 * 存储密码非 {bcrypt} 形态、密码错误、请求非 ServletServerHttpRequest 类型，
 * 以及 {@code afterHandshake} 不产生副作用。
 * </p>
 * <p>
 * 拒绝升级的具体行为断言为：返回 false（不继续握手）+ 响应状态码被置为 401
 * （前端据此引导重新登录，不退化为 HTTP 轮询）；放行时断言响应与下游 handler 均未被触碰。
 * 凭据校验流程本身（六个状态 + 两类异常）由 {@link BasicAuthVerifierTest} 直接覆盖，
 * 本测试只验证拦截器对校验结果的响应行为。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocket 握手认证拦截器测试")
class WebSocketAuthInterceptorTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String VALID_PASSWORD = "admin123";
    private static final String VALID_BCRYPT_HASH = ENCODER.encode(VALID_PASSWORD);

    private AppProperties appProperties;
    private WebSocketAuthInterceptor interceptor;
    private MockHttpServletRequest servletRequest;
    private ServletServerHttpRequest request;
    private Map<String, Object> attributes;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    @Mock
    private ServerHttpRequest nonServletRequest;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getAuth().setUsername("admin");
        appProperties.getAuth().setPassword("{bcrypt}" + VALID_BCRYPT_HASH);
        interceptor = new WebSocketAuthInterceptor(appProperties);
        servletRequest = new MockHttpServletRequest();
        request = new ServletServerHttpRequest(servletRequest);
        attributes = new HashMap<>();
    }

    // ================================================================
    // 正向场景
    // ================================================================

    @Test
    @DisplayName("合法 Basic 凭据应放行握手，且不写响应、不触碰下游 handler")
    void shouldAllowHandshakeWithValidCredentials() {
        setAuthorization("admin", VALID_PASSWORD);

        assertTrue(interceptor.beforeHandshake(request, response, wsHandler, attributes),
            "凭据合法时应继续握手");

        verifyNoInteractions(response);
        verifyNoInteractions(wsHandler);
        assertTrue(attributes.isEmpty(), "握手阶段不应向 attributes 注入内容");
    }

    // ================================================================
    // 拒绝升级：缺少 / 非 Basic 凭据
    // ================================================================

    @Test
    @DisplayName("无 Authorization 请求头应返回 401 拒绝升级")
    void shouldRejectHandshakeWhenAuthHeaderMissing() {
        assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(wsHandler);
    }

    @Test
    @DisplayName("非 Basic 认证头（Bearer）应返回 401 拒绝升级")
    void shouldRejectHandshakeWhenSchemeIsBearer() {
        servletRequest.addHeader("Authorization", "Bearer token123");

        assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("请求不是 ServletServerHttpRequest 时应返回 401 拒绝升级")
    void shouldRejectHandshakeWhenRequestIsNotServletBased() {
        // 非 Servlet 请求（如 WebFlux 风格实现）读不到 Authorization 头，
        // 实现按「缺少凭据」处理，不抛异常
        assertFalse(interceptor.beforeHandshake(nonServletRequest, response, wsHandler, attributes));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(wsHandler);
    }

    // ================================================================
    // 拒绝升级：凭据形态非法
    // ================================================================

    @Test
    @DisplayName("Basic 后为非法 Base64 应返回 401 拒绝升级（异常兜底分支）")
    void shouldRejectHandshakeWhenBase64Invalid() {
        servletRequest.addHeader("Authorization", "Basic not-valid-base64!!!");

        assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes),
            "校验器抛出的 IllegalArgumentException 应被拦截器兜底为拒绝升级");
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("凭据缺失冒号应返回 401 拒绝升级")
    void shouldRejectHandshakeWhenCredentialsHaveNoColon() {
        servletRequest.addHeader("Authorization", "Basic " + base64("onlyUsername"));

        assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("存储密码非 {bcrypt} 形态应返回 401 拒绝升级")
    void shouldRejectHandshakeWhenPasswordNotBcrypt() {
        appProperties.getAuth().setPassword(VALID_PASSWORD);
        interceptor = new WebSocketAuthInterceptor(appProperties);
        setAuthorization("admin", VALID_PASSWORD);

        assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("{bcrypt} 前缀后哈希形态非法应返回 401 拒绝升级（异常兜底分支）")
    void shouldRejectHandshakeWhenBcryptHashMalformed() {
        appProperties.getAuth().setPassword("{bcrypt}not-a-valid-hash");
        interceptor = new WebSocketAuthInterceptor(appProperties);
        setAuthorization("admin", VALID_PASSWORD);

        assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    // ================================================================
    // 拒绝升级：凭据不匹配
    // ================================================================

    @Test
    @DisplayName("用户名不匹配应返回 401 拒绝升级")
    void shouldRejectHandshakeWhenUsernameMismatch() {
        setAuthorization("wrongUser", VALID_PASSWORD);

        assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(wsHandler);
    }

    @Test
    @DisplayName("密码错误应返回 401 拒绝升级")
    void shouldRejectHandshakeWhenPasswordMismatch() {
        setAuthorization("admin", "wrongPassword");

        assertFalse(interceptor.beforeHandshake(request, response, wsHandler, attributes));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(wsHandler);
    }

    // ================================================================
    // afterHandshake
    // ================================================================

    @Test
    @DisplayName("afterHandshake 应不触碰响应与 handler（含握手异常场景）")
    void shouldDoNothingAfterHandshake() {
        interceptor.afterHandshake(request, response, wsHandler, new RuntimeException("握手异常"));

        verifyNoInteractions(response);
        verifyNoInteractions(wsHandler);
    }

    // ================================================================
    // 测试辅助
    // ================================================================

    private void setAuthorization(String username, String password) {
        servletRequest.addHeader("Authorization", "Basic " + base64(username + ":" + password));
    }

    private static String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
