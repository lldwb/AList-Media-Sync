package top.lldwb.alistmediasync.common.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import top.lldwb.alistmediasync.common.config.AppProperties;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证拦截器单元测试
 * <p>
 * 覆盖：排除路径白名单、缺少认证头、BCrypt 密码验证通过/失败、
 * 非 {bcrypt} 格式密码被拒绝等场景。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("认证拦截器测试")
class AuthInterceptorTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String VALID_BCRYPT_HASH = ENCODER.encode("admin123");

    private AuthInterceptor interceptor;
    private AppProperties appProperties;
    private JsonMapper objectMapper;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getAuth().setUsername("admin");
        appProperties.getAuth().setPassword("{bcrypt}" + VALID_BCRYPT_HASH);
        objectMapper = new JsonMapper();
        interceptor = new AuthInterceptor(appProperties, objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("排除路径 /api/webhooks 应跳过认证")
    void shouldSkipAuthForWebhookPath() throws Exception {
        request.setRequestURI("/api/webhooks/event");
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    @DisplayName("排除路径 /actuator/health 应跳过认证")
    void shouldSkipAuthForActuatorPath() throws Exception {
        request.setRequestURI("/actuator/health");
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    @DisplayName("排除路径 /h2-console 应跳过认证")
    void shouldSkipAuthForH2ConsolePath() throws Exception {
        request.setRequestURI("/h2-console/login.do");
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    @DisplayName("缺少 Authorization 请求头应返回 401")
    void shouldReturn401WhenNoAuthHeader() throws Exception {
        request.setRequestURI("/api/storage");
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("非 Basic 认证请求头应返回 401")
    void shouldReturn401WhenNotBasicAuth() throws Exception {
        request.setRequestURI("/api/storage");
        request.addHeader("Authorization", "Bearer token123");
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("有效的 Basic 认证凭据应通过验证")
    void shouldPassWithValidCredentials() throws Exception {
        request.setRequestURI("/api/sync");
        setBasicAuth(request, "admin", "admin123");
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    @DisplayName("密码错误应返回 401")
    void shouldReturn401WhenWrongPassword() throws Exception {
        request.setRequestURI("/api/sync");
        setBasicAuth(request, "admin", "wrongPassword");
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("用户名错误应返回 401")
    void shouldReturn401WhenWrongUsername() throws Exception {
        request.setRequestURI("/api/sync");
        setBasicAuth(request, "wrongUser", "admin123");
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("非 {bcrypt} 格式的存储密码应返回 401（防御性检查）")
    void shouldReturn401WhenPasswordNotBcryptFormat() throws Exception {
        // 模拟 PasswordEncryptionPostProcessor 未生效的场景
        appProperties.getAuth().setPassword("plainTextPassword");
        interceptor = new AuthInterceptor(appProperties, objectMapper);

        request.setRequestURI("/api/sync");
        setBasicAuth(request, "admin", "plainTextPassword");
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("无效的 Base64 编码应返回 401")
    void shouldReturn401WhenInvalidBase64() throws Exception {
        request.setRequestURI("/api/sync");
        request.addHeader("Authorization", "Basic not-valid-base64!!!");
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("格式无效的 Basic 凭据（缺少冒号）应返回 401")
    void shouldReturn401WhenCredentialsFormatInvalid() throws Exception {
        request.setRequestURI("/api/sync");
        String noColon = Base64.getEncoder().encodeToString("onlyUsername".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + noColon);
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
    }

    private void setBasicAuth(MockHttpServletRequest request, String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + encoded);
    }
}
