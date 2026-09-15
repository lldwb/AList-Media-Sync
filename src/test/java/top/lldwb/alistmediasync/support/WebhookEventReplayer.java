package top.lldwb.alistmediasync.support;

import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook 事件重放注入器
 * <p>
 * 读取 {@code src/test/resources/fixtures/webhook/} 下的 v2 协议样本，
 * 以 HTTP POST 注入系统 {@code /api/webhooks/recorder} 端点。
 * 支持参数化：fixtures 路径、重复发送次数（验证幂等）、自定义 EventId。
 * </p>
 * <p>
 * 加载时校验 payload 符合 v2 协议四段式结构（EventType/EventId/EventTimestamp/EventData），
 * 契约漂移时测试失败并报告。
 * </p>
 *
 * @author AList-Media-Sync
 */
public class WebhookEventReplayer {

    /** JSON 对象映射器 */
    private static final JsonMapper MAPPER = new JsonMapper();

    /** 默认 fixtures 目录 */
    private static final String DEFAULT_FIXTURES_DIR = "src/test/resources/fixtures/webhook/";

    private final RestClient restClient;
    private final String baseUrl;

    /**
     * 构造重放器
     *
     * @param restClient 用于发送 HTTP POST 的 RestClient
     * @param baseUrl    系统基础 URL（如 http://localhost:8080）
     */
    public WebhookEventReplayer(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    /**
     * 加载并校验单个 fixture 文件
     * <p>
     * 校验 payload 必须包含 EventType、EventId、EventTimestamp 三个顶层字段，
     * 以及 EventData Map。不符合时抛出 {@link IllegalArgumentException}。
     * </p>
     *
     * @param fileName 文件名（不含路径），如 "fileclosed-event.json"
     * @return 解析后的 payload Map
     * @throws IOException           文件读取失败
     * @throws tools.jackson.core.JacksonException JSON 解析失败（Jackson 3 中为非受检异常）
     * @throws IllegalArgumentException 契约漂移：缺少必需字段
     */
    public Map<String, Object> loadFixture(String fileName) throws IOException {
        Path path = Path.of(DEFAULT_FIXTURES_DIR, fileName);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("fixture 文件不存在：" + path.toAbsolutePath());
        }
        try (InputStream is = Files.newInputStream(path)) {
            Map<String, Object> payload = MAPPER.readValue(is, new TypeReference<>() {});
            validateV2Payload(payload, fileName);
            return payload;
        }
    }

    /**
     * 发送单次 webhook 事件
     *
     * @param payload 事件 payload
     * @return 响应 body（Map 形式）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sendEvent(Map<String, Object> payload) {
        return restClient.post()
            .uri(baseUrl + "/api/webhooks/recorder")
            .body(payload)
            .retrieve()
            .body(Map.class);
    }

    /**
     * 发送事件并验证幂等
     * <p>
     * 重复发送 {@code count} 次，第 1 次应返回 accepted，后续应返回 DUPLICATE 或 accepted
     * （取决于服务端实现）。每次发送后检查响应结构。
     * </p>
     *
     * @param payload 事件 payload
     * @param count   发送次数
     * @return 每次发送的响应 body 列表
     */
    public java.util.List<Map<String, Object>> sendEventIdempotent(Map<String, Object> payload, int count) {
        java.util.List<Map<String, Object>> responses = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> resp = sendEvent(payload);
            responses.add(resp);
        }
        return responses;
    }

    /**
     * 发送带自定义 EventId 的事件
     * <p>
     * 覆盖 payload 中的 EventId 字段后发送。
     * </p>
     *
     * @param payload      原始 payload
     * @param customEventId 自定义 EventId
     * @return 响应 body
     */
    public Map<String, Object> sendEventWithCustomId(Map<String, Object> payload, String customEventId) {
        Map<String, Object> modified = new HashMap<>(payload);
        modified.put("EventId", customEventId);
        return sendEvent(modified);
    }

    /**
     * 校验 v2 协议四段式结构
     *
     * @param payload  事件 payload
     * @param fileName 文件名（用于错误消息）
     * @throws IllegalArgumentException 缺少必需字段时抛出
     */
    private void validateV2Payload(Map<String, Object> payload, String fileName) {
        if (payload == null) {
            throw new IllegalArgumentException("fixture 契约漂移：payload 为 null — " + fileName);
        }

        // 检查必需字段
        if (!payload.containsKey("EventType")) {
            throw new IllegalArgumentException(
                "fixture 契约漂移：缺少 EventType 字段 — " + fileName);
        }
        if (!payload.containsKey("EventId")) {
            throw new IllegalArgumentException(
                "fixture 契约漂移：缺少 EventId 字段 — " + fileName);
        }
        if (!payload.containsKey("EventTimestamp")) {
            throw new IllegalArgumentException(
                "fixture 契约漂移：缺少 EventTimestamp 字段 — " + fileName);
        }
        if (!payload.containsKey("EventData") || !(payload.get("EventData") instanceof Map)) {
            throw new IllegalArgumentException(
                "fixture 契约漂移：缺少 EventData Map 字段 — " + fileName);
        }
    }
}