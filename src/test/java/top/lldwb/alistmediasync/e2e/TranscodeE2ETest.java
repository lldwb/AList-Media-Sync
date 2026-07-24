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
 * 链路3：转码 E2E 测试
 * <p>
 * 验证转码流程完整执行（AP7）：
 * 创建转码任务（mp4 -> mp3），轮询目标 AList 直到转码产物出现。
 * 断言：转码产物存在且 getFileDetail 返回有效数据。
 * </p>
 * <p>
 * 仅在 {@code RUN_E2E=true} 环境变量下执行，需真实 AList 二进制 + JAVE2 ffmpeg。
 * 数据预置由 {@link E2ETestBase#setupE2EData()} 完成（sample.mp4 已上传到 /e2e-test/sample.mp4）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@EnabledIfEnvironmentVariable(named = "RUN_E2E", matches = "true")
@DisplayName("链路3：转码 E2E 测试")
class TranscodeE2ETest extends E2ETestBase {

    /**
     * AP7：转码完成后产物出现在目标 AList
     * <p>
     * 创建 mp4->mp3 转码任务，轮询目标路径 /e2e-test-transcoded/output.mp3 直到产物出现。
     * </p>
     */
    @Test
    @DisplayName("AP7 - 转码完成后产物出现在目标 AList")
    void shouldTranscodeFileAndVerify() {
        AListTestClient alistClient = new AListTestClient("http://localhost:" + alistPort, alistToken);
        assertTrue(alistClient.ping(), "AList 服务应可达");

        // 创建转码任务（源 /e2e-test/sample.mp4 -> 目标 /e2e-test-transcoded/，系统自动用源文件名+目标格式后缀生成 sample.mp3）
        Map<String, Object> task = Map.of(
            "sourceEngineId", sourceEngineId,
            "targetEngineId", targetEngineId,
            "sourceFilePath", "/e2e-test/sample.mp4",
            "targetFilePath", "/e2e-test-transcoded",
            "targetFormat", "MP3",
            "bitrate", 128000
        );
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(task, basicAuth());
        ResponseEntity<Map> createResp = testRestTemplate.postForEntity("/api/transcode-tasks", req, Map.class);
        assertEquals(200, createResp.getStatusCode().value(), "创建转码任务应成功");
        Long taskId = extractId(createResp.getBody());
        assertNotNull(taskId, "应返回转码任务 ID");

        // 轮询转码产物出现（转码产物为 sample.mp3，系统用源文件名 + 目标格式后缀）
        boolean found = await(() -> fileExists(alistClient.getFileDetail("/e2e-test-transcoded/sample.mp3")),
            Duration.ofSeconds(120), Duration.ofSeconds(3));
        assertTrue(found, "120 秒内转码产物 sample.mp3 应出现在目标 AList（AP7 转码完成）");
    }

    /**
     * 查询转码任务列表
     */
    @Test
    @DisplayName("查询转码任务列表")
    void shouldListTranscodeTasks() {
        HttpEntity<Void> req = new HttpEntity<>(basicAuth());
        ResponseEntity<Map> resp = testRestTemplate.exchange("/api/transcode-tasks", HttpMethod.GET, req, Map.class);
        assertNotNull(resp.getBody());
        assertEquals(200, resp.getStatusCode().value());
    }

    /**
     * 检查 AList getFileDetail 响应是否表示文件存在（code=200 且 data 非空）
     */
    private static boolean fileExists(Map<String, Object> detail) {
        if (detail == null) return false;
        Object code = detail.get("code");
        if (!(code instanceof Number n) || n.intValue() != 200) return false;
        Object data = detail.get("data");
        return data instanceof Map<?, ?> d && d.get("name") != null;
    }
}
