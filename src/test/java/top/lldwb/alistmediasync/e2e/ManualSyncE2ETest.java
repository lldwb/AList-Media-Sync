package top.lldwb.alistmediasync.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import top.lldwb.alistmediasync.support.AListTestClient;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 链路2：手动同步 E2E 测试
 * <p>
 * 验证手动触发同步任务执行，文件从源 AList 同步到目标 AList。
 * 断言：同步任务执行完成后文件出现在目标路径（轮询验证，替代硬编码 sleep）。
 * </p>
 * <p>
 * 仅在 {@code RUN_E2E=true} 环境变量下执行，需真实 AList 二进制。
 * 数据预置由 {@link E2ETestBase#setupE2EData()} 完成。
 * </p>
 *
 * @author AList-Media-Sync
 */
@EnabledIfEnvironmentVariable(named = "RUN_E2E", matches = "true")
@DisplayName("链路2：手动同步 E2E 测试")
class ManualSyncE2ETest extends E2ETestBase {

    /**
     * 手动触发同步任务，轮询验证文件落盘
     */
    @Test
    @DisplayName("手动触发同步任务执行，文件落盘目标 AList")
    void shouldExecuteManualSync() {
        AListTestClient alistClient = new AListTestClient("http://localhost:" + alistPort);
        assertTrue(alistClient.ping(), "AList 服务应可达");

        // 创建同步任务（使用预置引擎，源 /e2e-test -> 目标 /e2e-test-sync-manual）
        Map<String, Object> task = Map.of(
            "name", "E2E手动同步",
            "sourceEngineId", sourceEngineId,
            "targetEngineId", targetEngineId,
            "sourcePath", "/e2e-test",
            "targetPath", "/e2e-test-sync-manual",
            "syncMode", "NEW_ONLY",
            "enabled", true
        );
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(task, basicAuth());
        ResponseEntity<Map> createResp = testRestTemplate.postForEntity("/api/sync-tasks", req, Map.class);
        assertEquals(200, createResp.getStatusCode().value(), "创建同步任务应成功");
        Long taskId = extractId(createResp.getBody());
        assertNotNull(taskId, "应返回任务 ID");

        // 手动触发同步执行
        HttpEntity<Map<String, Object>> triggerReq = new HttpEntity<>(Map.of("taskId", taskId), basicAuth());
        testRestTemplate.postForEntity("/api/sync-tasks/trigger", triggerReq, Map.class);

        // 轮询目标路径文件落盘（替代 Thread.sleep）
        boolean found = await(() -> hasFileInList(alistClient.listFiles("/e2e-test-sync-manual")),
            Duration.ofSeconds(90), Duration.ofSeconds(2));
        assertTrue(found, "90 秒内手动同步目标路径 /e2e-test-sync-manual 应出现文件");
    }

    /**
     * 查询同步任务列表
     */
    @Test
    @DisplayName("查询同步任务列表")
    void shouldListSyncTasks() {
        HttpEntity<Void> req = new HttpEntity<>(basicAuth());
        ResponseEntity<Map> resp = testRestTemplate.exchange("/api/sync-tasks", HttpMethod.GET, req, Map.class);
        assertNotNull(resp.getBody());
        assertEquals(200, resp.getStatusCode().value());
    }
}
