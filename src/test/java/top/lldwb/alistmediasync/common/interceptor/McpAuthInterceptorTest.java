package top.lldwb.alistmediasync.common.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import top.lldwb.alistmediasync.common.config.AppProperties;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 认证拦截器单元测试
 * <p>
 * 覆盖 Bearer Token 认证的成功/缺失/无效/非 Bearer 格式场景（FR-008、SC-004），
 * 以及 401 响应不泄露业务数据与令牌配置细节。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("MCP 认证拦截器测试")
class McpAuthInterceptorTest {

    private static final String VALID_TOKEN = "test-mcp-token-1234567890";

    private McpAuthInterceptor interceptor;
    private AppProperties appProperties;
    private JsonMapper objectMapper;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getMcp().setToken(VALID_TOKEN);
        objectMapper = new JsonMapper();
        interceptor = new McpAuthInterceptor(appProperties, objectMapper);
        request = new MockHttpServletRequest();
        request.setRequestURI("/mcp");
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("正确 Bearer Token 应放行")
    void shouldPassWithValidBearerToken() throws Exception {
        request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
        assertTrue(interceptor.preHandle(request, response, null));
        assertEquals(200, response.getStatus() == 0 ? 200 : response.getStatus());
    }

    @Test
    @DisplayName("缺少 Authorization 请求头应返回 401")
    void shouldReturn401WhenNoAuthHeader() throws Exception {
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("缺少 MCP 认证令牌"));
    }

    @Test
    @DisplayName("非 Bearer 格式认证头应返回 401")
    void shouldReturn401WhenNotBearerFormat() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("Bearer 前缀但令牌为空应返回 401")
    void shouldReturn401WhenEmptyBearerToken() throws Exception {
        request.addHeader("Authorization", "Bearer ");
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("无效令牌应返回 401")
    void shouldReturn401WhenInvalidToken() throws Exception {
        request.addHeader("Authorization", "Bearer wrong-token");
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("令牌无效"));
    }

    @Test
    @DisplayName("令牌未配置（空）时应返回 401，不泄露配置细节")
    void shouldReturn401WhenTokenNotConfigured() throws Exception {
        appProperties.getMcp().setToken("");
        interceptor = new McpAuthInterceptor(appProperties, objectMapper);

        request.addHeader("Authorization", "Bearer any-token");
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("401 响应体应为统一错误结构且不泄露业务数据（SC-004）")
    void shouldReturnStructuredErrorWithoutBusinessData() throws Exception {
        request.addHeader("Authorization", "Bearer wrong-token");
        interceptor.preHandle(request, response, null);

        String body = response.getContentAsString();
        assertTrue(body.contains("\"code\":401"), "401 响应应包含统一错误码");
        assertTrue(body.contains("\"message\""), "401 响应应包含统一错误消息");
        assertFalse(body.contains(VALID_TOKEN), "401 响应 MUST NOT 泄露配置令牌");
        assertFalse(body.toLowerCase().contains("secret"), "401 响应 MUST NOT 泄露业务数据");
    }
}
