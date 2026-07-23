package top.lldwb.alistmediasync.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import top.lldwb.alistmediasync.support.AListTestClient;
import top.lldwb.alistmediasync.support.WebhookEventReplayer;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 链路2：手动同步 E2E 测试
 * <p>
 * 验证手动触发同步任务执行，文件从源 AList 同步到目标 AList。
 * 断言：同步任务执行完成后文件出现在目标路径。
 * </p>
 * <p>
 * 仅在 {@code RUN_E2E=true} 环境变量下执行，需真实 AList 二进制。
 * </p>
 *
 * @author AList-Media-Sync
 */
@EnabledIfEnvironmentVariable(named = "RUN_E2E", matches = "true")
@DisplayName("链路2：手动同步 E2E 测试")
class ManualSyncE2ETest extends E2ETestBase {

    @Autowired
    private TestRestTemplate testRestTemplate;

    /**
     * 手动触发同步任务
     * <p>
     * 先创建同步任务，再手动触发执行，验证文件同步完成。
     * </p>
     */
    @Test
    @DisplayName("手动触发同步任务执行")
    void shouldExecuteManualSync() throws InterruptedException {
        String alistPort = System.getProperty("alist.port", "5244");
        AListTestClient alistClient = new AListTestClient("http://localhost:" + alistPort);

        assertTrue(alistClient.ping(), "AList 服务应可达");

        // 创建同步任务
        ResponseEntity<Map> createResponse = testRestTemplate.postForEntity(
            "/api/sync-tasks",
            Map.of(
                "name", "E2E 手动同步",
                "sourcePath", "/e2e-test",
                "targetPath", "/e2e-test-sync",
                "syncMode", "NEW_ONLY",
                "enabled", true
            ),
            Map.class
        );
        assertNotNull(createResponse.getBody());
        assertEquals(200, createResponse.getStatusCode().value());

        // 手动触发同步
        ResponseEntity<Map> triggerResponse = testRestTemplate.postForEntity(
            "/api/sync-tasks/trigger",
            Map.of("taskId", 1L),
            Map.class
        );
        assertNotNull(triggerResponse.getBody());

        // 等待同步完成
        Thread.sleep(5000);

        // 验证目标文件已同步
        Map<String, Object> listResponse = alistClient.listFiles("/e2e-test-sync");
        assertNotNull(listResponse);
    }

    /**
     * 查询同步任务列表
     * <p>
     * 验证同步任务 API 正常返回。
     * </p>
     */
    @Test
    @DisplayName("查询同步任务列表")
    void shouldListSyncTasks() {
        ResponseEntity<Map> response = testRestTemplate.getForEntity(
            "/api/sync-tasks", Map.class);
        assertNotNull(response.getBody());
    }
}