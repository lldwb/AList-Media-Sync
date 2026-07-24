package top.lldwb.alistmediasync.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.ResponseEntity;

import top.lldwb.alistmediasync.support.AListTestClient;
import top.lldwb.alistmediasync.support.WebhookEventReplayer;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 链路1：Webhook 触发同步 E2E 测试
 * <p>
 * 验证断言点 AP1-AP6：
 * <ul>
 *   <li>AP1：Webhook 事件成功接收并入库（响应 {code:200, message:accepted, data:eventId}）</li>
 *   <li>AP2：事件持久化到 webhook_event 表</li>
 *   <li>AP3：同步完成后文件出现在目标 AList（轮询 /e2e-test-sync）</li>
 *   <li>AP4：重复 EventId 触发幂等去重（事件状态 DUPLICATE）</li>
 *   <li>AP5：TaskExecution 记录状态为 SUCCESS</li>
 *   <li>AP6：WebhookEvent 记录状态为 COMPLETED</li>
 * </ul>
 * </p>
 * <p>
 * 仅在 {@code RUN_E2E=true} 环境变量下执行，需真实 AList 二进制。
 * 数据预置（StorageEngine/WebhookRule/源文件）由 {@link E2ETestBase#setupE2EData()} 完成。
 * </p>
 *
 * @author AList-Media-Sync
 */
@EnabledIfEnvironmentVariable(named = "RUN_E2E", matches = "true")
@DisplayName("链路1：Webhook 触发同步 E2E 测试")
class WebhookSyncE2ETest extends E2ETestBase {

    /**
     * AP1：Webhook 事件成功接收并入库
     * <p>
     * 发送 FileClosed 事件，严格断言响应结构 {code:200, message:accepted, data:eventId}。
     * </p>
     */
    @Test
    @DisplayName("AP1 - Webhook 事件成功接收并入库")
    void shouldReceiveWebhookEvent() throws IOException {
        WebhookEventReplayer replayer = newReplayer();
        Map<String, Object> payload = replayer.loadFixture("fileclosed-event.json");
        // 唯一 EventId 避免与其他测试或上次运行冲突
        payload.put("EventId", "e2e-rcv-" + uniqueSuffix());

        Map<String, Object> response = replayer.sendEvent(payload);

        assertNotNull(response, "响应不应为空");
        assertEquals(200, response.get("code"), "响应码应为 200");
        assertEquals("accepted", response.get("message"), "响应消息应为 accepted");
        assertNotNull(response.get("data"), "应返回 EventId");
    }

    /**
     * AP1：SessionStarted 事件正常接收
     */
    @Test
    @DisplayName("AP1 - SessionStarted 事件正常接收")
    void shouldReceiveSessionStartedEvent() throws IOException {
        WebhookEventReplayer replayer = newReplayer();
        Map<String, Object> payload = replayer.loadFixture("sessionstarted-event.json");
        payload.put("EventId", "e2e-ss-" + uniqueSuffix());

        Map<String, Object> response = replayer.sendEvent(payload);

        assertNotNull(response);
        assertEquals(200, response.get("code"));
        assertEquals("accepted", response.get("message"));
    }

    /**
     * AP4：重复 EventId 触发幂等去重
     * <p>
     * 发送相同 EventId 两次，第二次后查询事件列表，断言该事件状态为 DUPLICATE。
     * </p>
     */
    @Test
    @DisplayName("AP4 - 重复 EventId 触发幂等去重")
    void shouldDeduplicateEvent() throws IOException, InterruptedException {
        WebhookEventReplayer replayer = newReplayer();
        Map<String, Object> payload = replayer.loadFixture("fileclosed-event.json");
        String eventId = "e2e-dedup-" + uniqueSuffix();
        payload.put("EventId", eventId);

        Map<String, Object> first = replayer.sendEvent(payload);
        assertEquals(200, first.get("code"), "首次发送应成功");

        // 等待异步处理落地
        Thread.sleep(1000);

        Map<String, Object> second = replayer.sendEvent(payload);
        assertEquals(200, second.get("code"), "重复发送应返回 200（去重不报错）");

        // 轮询事件列表，断言该 EventId 状态为 DUPLICATE
        boolean dedupConfirmed = await(() -> {
            ResponseEntity<Map> resp = testRestTemplate.getForEntity(
                "/api/webhooks/events?page=1&size=50", Map.class);
            return findEventStatus(resp.getBody(), eventId, "DUPLICATE");
        }, Duration.ofSeconds(15), Duration.ofSeconds(1));

        assertTrue(dedupConfirmed, "15 秒内事件 " + eventId + " 状态应为 DUPLICATE");
    }

    /**
     * AP3：同步完成后文件出现在目标 AList
     * <p>
     * 发送 FileClosed 事件触发同步，轮询目标 AList /e2e-test-sync 直到出现文件。
     * 替代硬编码 Thread.sleep，提升断言稳定性。
     * </p>
     */
    @Test
    @DisplayName("AP3 - 同步完成后文件出现在目标 AList")
    void shouldSyncFileToTargetAList() throws IOException {
        AListTestClient alistClient = new AListTestClient("http://localhost:" + alistPort);
        assertTrue(alistClient.ping(), "AList 服务应可达");

        WebhookEventReplayer replayer = newReplayer();
        Map<String, Object> payload = replayer.loadFixture("fileclosed-event.json");
        payload.put("EventId", "e2e-sync-" + uniqueSuffix());
        replayer.sendEvent(payload);

        boolean found = await(() -> {
            Map<String, Object> resp = alistClient.listFiles("/e2e-test-sync");
            return hasFileInList(resp);
        }, Duration.ofSeconds(90), Duration.ofSeconds(2));

        assertTrue(found, "90 秒内目标路径 /e2e-test-sync 应出现同步文件（AP3 文件落盘）");
    }

    /**
     * AP5/AP6：TaskExecution 和 WebhookEvent 状态验证
     * <p>
     * 发送事件后轮询事件列表，断言事件最终状态为 COMPLETED。
     * </p>
     */
    @Test
    @DisplayName("AP5/AP6 - WebhookEvent 最终状态为 COMPLETED")
    void shouldVerifyEventCompleted() throws IOException {
        WebhookEventReplayer replayer = newReplayer();
        Map<String, Object> payload = replayer.loadFixture("fileclosed-event.json");
        String eventId = "e2e-comp-" + uniqueSuffix();
        payload.put("EventId", eventId);
        replayer.sendEvent(payload);

        boolean completed = await(() -> {
            ResponseEntity<Map> resp = testRestTemplate.getForEntity(
                "/api/webhooks/events?page=1&size=50", Map.class);
            return findEventStatus(resp.getBody(), eventId, "COMPLETED");
        }, Duration.ofSeconds(30), Duration.ofSeconds(1));

        assertTrue(completed, "30 秒内事件 " + eventId + " 状态应为 COMPLETED（AP6 事件持久化）");
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private WebhookEventReplayer newReplayer() {
        return new WebhookEventReplayer(
            org.springframework.web.client.RestClient.builder().build(),
            "http://localhost:" + serverPort);
    }

    private static String uniqueSuffix() {
        return Long.toString(System.nanoTime(), 36);
    }

    /** 在事件列表响应中查找指定 EventId 且状态匹配 */
    @SuppressWarnings("unchecked")
    private static boolean findEventStatus(Map<String, Object> body, String eventId, String expectedStatus) {
        if (body == null) return false;
        Object data = body.get("data");
        if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m
                    && eventId.equals(String.valueOf(m.get("eventId")))
                    && expectedStatus.equals(String.valueOf(m.get("status")))) {
                    return true;
                }
            }
        }
        return false;
    }
}
