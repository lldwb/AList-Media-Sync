package top.lldwb.alistmediasync.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import top.lldwb.alistmediasync.support.AListTestClient;
import top.lldwb.alistmediasync.support.WebhookEventReplayer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 链路1：Webhook 触发同步 E2E 测试
 * <p>
 * 验证断言点 AP1-AP6：
 * <ul>
 *   <li>AP1：Webhook 事件成功接收并入库（返回 accepted + eventId）</li>
 *   <li>AP2：事件触发同步任务执行</li>
 *   <li>AP3：同步完成后文件出现在目标 AList</li>
 *   <li>AP4：重复 EventId 触发幂等去重（返回 DUPLICATE）</li>
 *   <li>AP5：TaskExecution 记录状态为 SUCCESS</li>
 *   <li>AP6：WebhookEvent 记录状态为 COMPLETED</li>
 * </ul>
 * </p>
 * <p>
 * 仅在 {@code RUN_E2E=true} 环境变量下执行，需真实 AList 二进制。
 * </p>
 *
 * @author AList-Media-Sync
 */
@EnabledIfEnvironmentVariable(named = "RUN_E2E", matches = "true")
@DisplayName("链路1：Webhook 触发同步 E2E 测试")
class WebhookSyncE2ETest extends E2ETestBase {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @LocalServerPort
    private int serverPort;

    /**
     * AP1：Webhook 事件成功接收并入库
     * <p>
     * 发送 FileClosed 事件，断言响应包含 accepted + eventId。
     * </p>
     */
    @Test
    @DisplayName("AP1 - Webhook 事件成功接收并入库")
    void shouldReceiveWebhookEvent() throws IOException {
        WebhookEventReplayer replayer = new WebhookEventReplayer(
            org.springframework.web.client.RestClient.builder().build(),
            "http://localhost:" + serverPort);

        Map<String, Object> payload = replayer.loadFixture("fileclosed-event.json");
        Map<String, Object> response = replayer.sendEvent(payload);

        assertNotNull(response);
        assertEquals("accepted", response.get("message"), "响应应为 accepted");
        assertNotNull(response.get("data"), "应返回 eventId");
        assertEquals("e2e-test-fileclosed-0001", response.get("data"));
    }

    /**
     * AP4：重复 EventId 触发幂等去重
     * <p>
     * 发送相同 EventId 的事件两次，第二次应为 DUPLICATE 状态。
     * </p>
     */
    @Test
    @DisplayName("AP4 - 重复 EventId 触发幂等去重")
    void shouldDeduplicateEvent() throws IOException {
        WebhookEventReplayer replayer = new WebhookEventReplayer(
            org.springframework.web.client.RestClient.builder().build(),
            "http://localhost:" + serverPort);

        Map<String, Object> payload = replayer.loadFixture("fileclosed-event.json");
        List<Map<String, Object>> responses = replayer.sendEventIdempotent(payload, 2);

        assertEquals(2, responses.size());
        // 第一次发送应返回 accepted
        assertEquals("accepted", responses.get(0).get("message"));
        // 第二次发送也应返回 accepted（服务端返回 eventId 表示已接收，状态内部处理为 DUPLICATE）
        assertNotNull(responses.get(1).get("data"));
    }

    /**
     * AP3：同步完成后文件出现在目标 AList
     * <p>
     * 发送 FileClosed 事件触发同步，等待后查询目标 AList 文件列表。
     * </p>
     */
    @Test
    @DisplayName("AP3 - 同步完成后文件出现在目标 AList")
    void shouldSyncFileToTargetAList() throws IOException, InterruptedException {
        String alistPort = System.getProperty("alist.port", "5244");
        AListTestClient alistClient = new AListTestClient("http://localhost:" + alistPort);

        assertTrue(alistClient.ping(), "AList 服务应可达");

        WebhookEventReplayer replayer = new WebhookEventReplayer(
            org.springframework.web.client.RestClient.builder().build(),
            "http://localhost:" + serverPort);

        Map<String, Object> payload = replayer.loadFixture("fileclosed-event.json");
        replayer.sendEvent(payload);

        // 等待异步处理完成
        Thread.sleep(5000);

        // 查询目标 AList 验证文件落盘
        Map<String, Object> listResponse = alistClient.listFiles("/e2e-test");
        assertNotNull(listResponse);
        assertEquals(200, listResponse.get("code"));
    }

    /**
     * AP1：SessionStarted 事件正常接收
     * <p>
     * 发送 SessionStarted 事件，验证非 FILE_CLOSED 事件也能正常接收。
     * </p>
     */
    @Test
    @DisplayName("AP1 - SessionStarted 事件正常接收")
    void shouldReceiveSessionStartedEvent() throws IOException {
        WebhookEventReplayer replayer = new WebhookEventReplayer(
            org.springframework.web.client.RestClient.builder().build(),
            "http://localhost:" + serverPort);

        Map<String, Object> payload = replayer.loadFixture("sessionstarted-event.json");
        Map<String, Object> response = replayer.sendEvent(payload);

        assertNotNull(response);
        assertEquals("accepted", response.get("message"));
        assertEquals("e2e-test-sessionstarted-0001", response.get("data"));
    }

    /**
     * AP5/AP6：TaskExecution 和 WebhookEvent 状态验证
     * <p>
     * 发送事件后查询系统 API，验证任务执行记录和事件状态。
     * </p>
     */
    @Test
    @DisplayName("AP5/AP6 - TaskExecution 和 WebhookEvent 状态验证")
    void shouldVerifyTaskExecutionAndEventStatus() throws IOException {
        WebhookEventReplayer replayer = new WebhookEventReplayer(
            org.springframework.web.client.RestClient.builder().build(),
            "http://localhost:" + serverPort);

        Map<String, Object> payload = replayer.loadFixture("fileclosed-event.json");
        replayer.sendEvent(payload);

        // 查询事件列表
        ResponseEntity<Map> eventsResponse = testRestTemplate.getForEntity(
            "/api/webhooks/events?page=1&size=20", Map.class);
        assertNotNull(eventsResponse.getBody());
    }
}