package top.lldwb.alistmediasync.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MCP 服务器端到端集成测试（research.md R8）
 * <p>
 * 以 HTTP 方式调用 {@code /mcp} 验证：认证生效（SC-004）、协议握手（initialize）、
 * 工具发现（tools/list 返回 36 个工具，SC-002）、工具调用（tools/call 驱动真实业务）、
 * 响应携带 X-Trace-Id（SC-003，原则 VII §7.5）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@SpringBootTest(properties = {
    "app.mcp.enabled=true",
    "app.mcp.token=test-mcp-integration-token-123",
    "spring.ai.mcp.server.enabled=true",
    "app.data-dir=./target/mcp-test-data",
    "spring.datasource.url=jdbc:h2:mem:mcp-test;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@DisplayName("MCP 服务器集成测试")
class McpServerIntegrationTest {

    private static final String TOKEN = "test-mcp-integration-token-123";
    private static final String MCP_ENDPOINT = "/mcp";
    private static final String MCP_SESSION_HEADER = "mcp-session-id";
    /** Streamable HTTP 要求 Accept 同时包含 JSON 与 SSE（MCP 2025-03-26） */
    private static final MediaType[] STREAMABLE_ACCEPT = {
        MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    private String jsonRpc(int id, String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method
            + "\",\"params\":" + (params == null ? "{}" : params) + "}";
    }

    @Test
    @DisplayName("未认证请求应返回 401，不泄露业务数据（SC-004）")
    void shouldRejectUnauthenticated() throws Exception {
        mockMvc.perform(post(MCP_ENDPOINT)
                .accept(STREAMABLE_ACCEPT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRpc(1, "initialize", null)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("无效令牌应返回 401")
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(post(MCP_ENDPOINT)
                .header("Authorization", "Bearer wrong-token")
                .accept(STREAMABLE_ACCEPT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRpc(1, "initialize", null)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正确令牌下 initialize 应建立会话并返回 serverInfo")
    void shouldInitializeSession() throws Exception {
        mockMvc.perform(post(MCP_ENDPOINT)
                .header("Authorization", "Bearer " + TOKEN)
                .accept(STREAMABLE_ACCEPT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRpc(1, "initialize",
                    "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"test-client\",\"version\":\"1.0.0\"}}")))
            .andExpect(status().isOk())
            .andExpect(header().exists(MCP_SESSION_HEADER))
            .andExpect(jsonPath("$.result.serverInfo").exists())
            .andExpect(header().exists("X-Trace-Id"))
            .andExpect(jsonPath("$.result").isNotEmpty());
    }

    @Test
    @DisplayName("tools/list 应返回全部 36 个工具（SC-002）")
    void shouldListAllTools() throws Exception {
        String sessionId = initializeAndGetSession();

        MvcResult result = mockMvc.perform(post(MCP_ENDPOINT)
                .header("Authorization", "Bearer " + TOKEN)
                .header(MCP_SESSION_HEADER, sessionId)
                .accept(STREAMABLE_ACCEPT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRpc(2, "tools/list", null)))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Trace-Id"))
            .andReturn();

        // 非 initialize 消息以 SSE 流返回（Streamable HTTP），从 data 行提取 JSON-RPC 响应
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getContentType()).contains("text/event-stream");
        String json = extractSseData(body);
        JsonNode tools = objectMapper.readTree(json).path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        // 工具总数：storage 8 + sync 9 + transcode 8 + webhook 8 + system 2 + flow 1 = 36
        assertThat(tools.size()).isEqualTo(36);
        assertThat(json).contains("storage_engine_list", "storage_engine_create",
            "sync_task_execute", "sync_task_get_executions", "transcode_task_create",
            "transcode_task_cleanup_temp", "webhook_rule_create", "webhook_event_list",
            "system_dashboard_stats", "system_run_diagnostics", "sync_flow_create_and_execute");
    }

    @Test
    @DisplayName("tools/call 调用 storage_engine_list 应驱动真实业务并返回结果")
    void shouldCallStorageEngineList() throws Exception {
        String sessionId = initializeAndGetSession();

        MvcResult result = mockMvc.perform(post(MCP_ENDPOINT)
                .header("Authorization", "Bearer " + TOKEN)
                .header(MCP_SESSION_HEADER, sessionId)
                .accept(STREAMABLE_ACCEPT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRpc(3, "tools/call",
                    "{\"name\":\"storage_engine_list\",\"arguments\":{}}")))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Trace-Id"))
            .andReturn();

        String json = extractSseData(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode callResult = objectMapper.readTree(json).path("result");
        assertThat(callResult.path("content").get(0).path("type").asText()).isEqualTo("text");
        assertThat(callResult.path("isError").asBoolean()).isFalse();
        // 业务结果应为 JSON 数组（存储引擎列表，空库时为 []）
        String text = callResult.path("content").get(0).path("text").asText();
        JsonNode payload = objectMapper.readTree(text);
        assertThat(payload.isArray()).isTrue();
    }

    /**
     * 从 SSE 流文本中提取 data 行并拼接为 JSON（支持单条 data 与多条 data 拼接）
     */
    private String extractSseData(String sseBody) {
        StringBuilder sb = new StringBuilder();
        for (String line : sseBody.split("\\r?\\n")) {
            if (line.startsWith("data:")) {
                sb.append(line.substring(5).trim());
            }
        }
        assertThat(sb).isNotEmpty();
        return sb.toString();
    }

    /**
     * 执行 initialize 握手并提取 Mcp-Session-Id
     */
    private String initializeAndGetSession() throws Exception {
        MvcResult result = mockMvc.perform(post(MCP_ENDPOINT)
                .header("Authorization", "Bearer " + TOKEN)
                .accept(STREAMABLE_ACCEPT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRpc(1, "initialize",
                    "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"test-client\",\"version\":\"1.0.0\"}}")))
            .andExpect(status().isOk())
            .andExpect(header().exists(MCP_SESSION_HEADER))
            .andReturn();

        String sessionId = result.getResponse().getHeader(MCP_SESSION_HEADER);
        assertThat(sessionId).isNotBlank();
        return sessionId;
    }
}
