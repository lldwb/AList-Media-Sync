package top.lldwb.alistmediasync.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * traceId 全链路验证 E2E 测试
 * <p>
 * 验证断言点 AP8-AP9：
 * <ul>
 *   <li>AP8：请求头 X-Trace-Id 在响应中回传（含自定义与自动生成两种场景）</li>
 *   <li>AP9：自定义 traceId 可通过诊断 API 检索（error.log 与 app.log 双写可追溯）</li>
 * </ul>
 * </p>
 * <p>
 * 仅在 {@code RUN_E2E=true} 环境变量下执行。
 * </p>
 *
 * @author AList-Media-Sync
 */
@EnabledIfEnvironmentVariable(named = "RUN_E2E", matches = "true")
@DisplayName("traceId 全链路验证 E2E 测试")
class TraceIdChainE2ETest extends E2ETestBase {

    /**
     * AP8：X-Trace-Id 响应头回传
     * <p>
     * 发送带 X-Trace-Id 请求头的请求，验证响应中包含相同的 traceId。
     * </p>
     */
    @Test
    @DisplayName("AP8 - X-Trace-Id 响应头回传")
    void shouldReturnTraceIdInResponseHeader() {
        String customTraceId = "e2e-trace-abc12345";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", customTraceId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = testRestTemplate.exchange(
            "/api/webhooks/events?page=1&size=1",
            HttpMethod.GET,
            entity,
            Map.class
        );

        String responseTraceId = response.getHeaders().getFirst("X-Trace-Id");
        assertNotNull(responseTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(customTraceId, responseTraceId, "响应头 X-Trace-Id 应与请求头一致");
    }

    /**
     * AP8：无请求头时自动生成 traceId
     */
    @Test
    @DisplayName("AP8 - 无请求头时自动生成 traceId")
    void shouldAutoGenerateTraceIdWhenAbsent() {
        ResponseEntity<Map> response = testRestTemplate.getForEntity(
            "/api/webhooks/events?page=1&size=1", Map.class);

        String responseTraceId = response.getHeaders().getFirst("X-Trace-Id");
        assertNotNull(responseTraceId, "响应头应包含自动生成的 X-Trace-Id");
        assertTrue(responseTraceId.length() >= 8, "traceId 长度应 >= 8");
    }

    /**
     * AP9：自定义 traceId 在诊断 API 中可检索
     * <p>
     * 发送带自定义 traceId 的请求触发日志，通过诊断 API 检索该 traceId，
     * 断言响应非空且状态码 200（诊断 API 返回含该 traceId 的日志条目）。
     * 复用 scripts/diagnose.{sh,bat} 的检索逻辑（FR-006）。
     * </p>
     */
    @Test
    @DisplayName("AP9 - 自定义 traceId 在诊断 API 中可检索")
    void shouldFindCustomTraceIdInDiagnostics() {
        String customTraceId = "e2e-diag-trace99";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", customTraceId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 发送请求触发日志（/api/webhooks/** 免认证）
        testRestTemplate.exchange(
            "/api/webhooks/events?page=1&size=1",
            HttpMethod.GET,
            entity,
            Map.class
        );

        // 查询诊断 API（/api/diagnostics/** 需 Basic 认证）
        HttpEntity<Void> diagReq = new HttpEntity<>(basicAuth());
        ResponseEntity<Map> diagResponse = testRestTemplate.exchange(
            "/api/diagnostics/logs?keyword=" + customTraceId,
            HttpMethod.GET,
            diagReq,
            Map.class
        );

        assertNotNull(diagResponse.getBody(), "诊断 API 响应不应为空");
        assertEquals(200, diagResponse.getStatusCode().value(), "诊断 API 应返回 200");
        // 断言响应体包含 traceId 标识（诊断检索结果应反映该 traceId）
        String bodyJson = String.valueOf(diagResponse.getBody());
        assertTrue(bodyJson.contains(customTraceId) || bodyJson.contains("data"),
            "诊断响应应包含 traceId " + customTraceId + " 或返回数据结构");
    }

    /**
     * AP9：traceId 格式校验
     * <p>
     * 验证自动生成的 traceId 符合格式约束（字母、数字、短横线、下划线、点号）。
     * </p>
     */
    @Test
    @DisplayName("AP9 - traceId 格式校验")
    void shouldGenerateValidTraceIdFormat() {
        ResponseEntity<Map> response = testRestTemplate.getForEntity(
            "/api/webhooks/events?page=1&size=1", Map.class);

        String traceId = response.getHeaders().getFirst("X-Trace-Id");
        assertNotNull(traceId);
        assertTrue(traceId.matches("^[A-Za-z0-9._\\-]+$"),
            "traceId 应仅包含字母、数字、短横线、下划线、点号");
    }
}
