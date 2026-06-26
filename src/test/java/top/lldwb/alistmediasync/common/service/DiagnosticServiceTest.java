package top.lldwb.alistmediasync.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.dto.DiagnosticResultVO;
import top.lldwb.alistmediasync.sync.entity.TaskExecution;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link DiagnosticService} 单元测试
 * <p>
 * 覆盖 T017 / T021 / T022 / T051：
 * <ul>
 *   <li>生成 summary.md</li>
 *   <li>复制错误日志摘录、记录缺失信息</li>
 *   <li>status 正确反映 COMPLETED / PARTIAL / FAILED</li>
 *   <li>durationMs 已被填充（SC-001 性能门禁基础）</li>
 *   <li>config.redacted.json 不泄露 password/token/cookie/authorization/key（T051）</li>
 *   <li>emptyKeys / missingKeys / redactedKeys 分类正确（T051）</li>
 * </ul>
 * </p>
 */
class DiagnosticServiceTest {

    @TempDir
    Path workDir;

    private MockEnvironment env;
    private AppProperties appProperties;
    private TaskExecutionRepository repository;
    private DiagnosticService service;

    @BeforeEach
    void setUp() throws IOException {
        env = new MockEnvironment();
        env.setProperty("app.data-dir", workDir.resolve("data").toString());
        env.setProperty("app.auth.username", "admin");
        env.setProperty("app.auth.password", "{bcrypt}$2a$10$secret");
        env.setProperty("alist.base-url", "https://alist.example.com");
        env.setProperty("alist.token", "alist-secret-token-XYZ-abc-1234567890");
        env.setProperty("alist.crypto-key", "this-is-a-secret-crypto-key-value");
        env.setProperty("server.port", "8080");
        env.setProperty("logging.level.root", "INFO");
        env.setProperty("logging.file.path", workDir.resolve("logs").toString());

        appProperties = new AppProperties();
        appProperties.setDataDir(workDir.resolve("data").toString());

        repository = mock(TaskExecutionRepository.class);
        when(repository.findAll()).thenReturn(List.of());

        service = new DiagnosticService(
            appProperties, env, Optional.of(repository),
            "0.0.1-test",
            workDir.resolve("logs").toString()
        );

        Files.createDirectories(workDir.resolve("logs"));
    }

    @Test
    void 缺失日志时应生成PARTIAL状态并列出缺失项() {
        DiagnosticResultVO result = service.generate(workDir.resolve("diagnostics"), 100);

        assertEquals(DiagnosticResultVO.Status.PARTIAL, result.getStatus());
        assertTrue(result.getMissingItems().stream().anyMatch(m -> m.contains("error.log")));
        assertTrue(result.getMissingItems().stream().anyMatch(m -> m.contains("app.log")));
        assertTrue(result.getDurationMs() >= 0, "durationMs 应被填充");
        assertNotNull(result.getTraceId());
    }

    @Test
    void 完整日志时应生成COMPLETED状态() throws IOException {
        Path logs = workDir.resolve("logs");
        Files.writeString(logs.resolve("app.log"), "INFO 正常日志\nERROR 模拟错误\n");
        Files.writeString(logs.resolve("error.log"), "ERROR 模拟错误\n");

        // 模拟一个最近失败的 TaskExecution
        TaskExecution e = new TaskExecution();
        e.setId(1L);
        e.setTaskType(TaskExecution.TaskType.SYNC);
        e.setStatus(TaskExecution.ExecutionStatus.FAILED);
        e.setStartTime(LocalDateTime.now().minusMinutes(5));
        e.setEndTime(LocalDateTime.now().minusMinutes(4));
        e.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        e.setFailureDetails("模拟失败：连接超时");
        when(repository.findAll()).thenReturn(List.of(e));

        DiagnosticResultVO result = service.generate(workDir.resolve("diagnostics"), 100);

        assertEquals(DiagnosticResultVO.Status.COMPLETED, result.getStatus());
        assertTrue(Files.exists(Path.of(result.getSummaryPath())), "summary.md 必须存在");
        assertTrue(Files.exists(workDir.resolve("diagnostics/latest/logs/error.log")));
        assertTrue(Files.exists(workDir.resolve("diagnostics/latest/logs/app.log")));
        assertTrue(Files.exists(workDir.resolve("diagnostics/latest/config/config.redacted.json")));
        assertTrue(Files.exists(workDir.resolve("diagnostics/latest/environment.txt")));
        assertTrue(Files.exists(workDir.resolve("diagnostics/latest/last-run.json")));
    }

    @Test
    void summary包含必填字段() throws IOException {
        Files.writeString(workDir.resolve("logs/app.log"), "INFO test\n");
        Files.writeString(workDir.resolve("logs/error.log"), "ERROR test\n");

        DiagnosticResultVO result = service.generate(workDir.resolve("diagnostics"), 100);

        String summary = Files.readString(Path.of(result.getSummaryPath()));
        assertTrue(summary.contains("# 诊断摘要"));
        assertTrue(summary.contains("## 基本信息"));
        assertTrue(summary.contains("## 最近一次失败"));
        assertTrue(summary.contains("## 关键证据"));
        assertTrue(summary.contains("## 缺失信息"));
        assertTrue(summary.contains("## 建议下一步"));
        assertTrue(summary.contains("Trace ID："));
    }

    @Test
    void config_redacted_json_不应包含原始敏感值() throws IOException {
        Files.writeString(workDir.resolve("logs/app.log"), "started\n");
        Files.writeString(workDir.resolve("logs/error.log"), "n/a\n");

        service.generate(workDir.resolve("diagnostics"), 100);

        String config = Files.readString(workDir.resolve("diagnostics/latest/config/config.redacted.json"));
        assertFalse(config.contains("alist-secret-token-XYZ-abc-1234567890"),
            "原始 alist.token 不应出现：" + config);
        assertFalse(config.contains("this-is-a-secret-crypto-key-value"),
            "原始 alist.crypto-key 不应出现：" + config);
        assertFalse(config.contains("$2a$10$secret"),
            "原始 BCrypt 密码不应出现");
        assertTrue(config.contains("alist.token"), "字段名必须保留");
        assertTrue(config.contains("***REDACTED***"), "应使用统一脱敏占位");
    }

    @Test
    void config_redacted_json_应区分emptyKeys_missingKeys_redactedKeys() throws IOException {
        env.setProperty("app.empty-config", "");  // 空值
        // alist.base-url 与 alist.token 都已设置，不进入 missingKeys

        Files.writeString(workDir.resolve("logs/app.log"), "x\n");
        Files.writeString(workDir.resolve("logs/error.log"), "x\n");

        service.generate(workDir.resolve("diagnostics"), 100);

        String config = Files.readString(workDir.resolve("diagnostics/latest/config/config.redacted.json"));
        assertTrue(config.contains("\"redactedKeys\""));
        assertTrue(config.contains("\"emptyKeys\""));
        assertTrue(config.contains("\"missingKeys\""));
        assertTrue(config.contains("\"app.empty-config\""), "emptyKeys 应包含空值字段");
        assertTrue(config.contains("EMPTY"), "空值应标记为 EMPTY 而非 REDACTED");
    }
}
