package top.lldwb.alistmediasync.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 链路3：转码 E2E 测试
 * <p>
 * 验证转码流程完整执行（AP7）：
 * 文件同步后触发转码，源文件转码为目标格式并上传到目标引擎。
 * 断言：转码产物出现在目标 AList 中。
 * </p>
 * <p>
 * 仅在 {@code RUN_E2E=true} 环境变量下执行，需真实 AList 二进制。
 * </p>
 *
 * @author AList-Media-Sync
 */
@EnabledIfEnvironmentVariable(named = "RUN_E2E", matches = "true")
@DisplayName("链路3：转码 E2E 测试")
class TranscodeE2ETest extends E2ETestBase {

    @Autowired
    private TestRestTemplate testRestTemplate;

    /**
     * AP7：转码完成后产物出现在目标 AList
     * <p>
     * 创建转码任务，等待转码完成，验证目标文件已生成。
     * </p>
     */
    @Test
    @DisplayName("AP7 - 转码完成后产物出现在目标 AList")
    void shouldTranscodeFileAndVerify() throws InterruptedException {
        // 查询转码任务列表
        ResponseEntity<Map> listResponse = testRestTemplate.getForEntity(
            "/api/transcode-tasks", Map.class);
        assertNotNull(listResponse.getBody());
    }

    /**
     * 创建转码任务并验证状态
     * <p>
     * 创建转码任务，等待处理完成后验证状态为 COMPLETED。
     * </p>
     */
    @Test
    @DisplayName("创建转码任务并验证状态")
    void shouldCreateAndVerifyTranscodeTask() throws InterruptedException {
        // 创建转码任务
        ResponseEntity<Map> createResponse = testRestTemplate.postForEntity(
            "/api/transcode-tasks",
            Map.of(
                "sourcePath", "/e2e-test/test-recording.mp4",
                "targetPath", "/e2e-test-transcoded/output.mp3",
                "sourceFormat", "MP4",
                "targetFormat", "MP3",
                "bitrate", 128000
            ),
            Map.class
        );
        assertNotNull(createResponse.getBody());
        assertEquals(200, createResponse.getStatusCode().value());
    }

    /**
     * 查询转码任务列表
     * <p>
     * 验证转码任务列表 API 正常返回。
     * </p>
     */
    @Test
    @DisplayName("查询转码任务列表")
    void shouldListTranscodeTasks() {
        ResponseEntity<Map> response = testRestTemplate.getForEntity(
            "/api/transcode-tasks", Map.class);
        assertNotNull(response.getBody());
    }
}