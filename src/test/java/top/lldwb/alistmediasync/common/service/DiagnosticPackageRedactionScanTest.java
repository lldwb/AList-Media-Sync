package top.lldwb.alistmediasync.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 诊断包端到端脱敏全包扫描测试（T053）
 * <p>
 * 生成诊断包后递归遍历 {@code diagnostics/latest/**}，断言文件内容不含测试配置中
 * 投入的明文敏感样本字符串，覆盖 SC-004 与 FR-008/FR-009。
 * </p>
 */
class DiagnosticPackageRedactionScanTest {

    @TempDir
    Path workDir;

    /** 投入的敏感样本（必须不在诊断包任何文件中出现原文） */
    private static final String[] SECRET_SAMPLES = {
        "PLAINTEXT-PASSWORD-12345",
        "ALIST-SECRET-TOKEN-abcXYZ-9876543210",
        "CRYPTO-KEY-XYZ-1234567890abcdef",
        "Bearer LEAK-Bearer-TOKEN-VALUE-001",
        "session-cookie-VALUE-leak"
    };

    private DiagnosticService service;

    @BeforeEach
    void setUp() throws IOException {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.data-dir", workDir.resolve("data").toString());
        env.setProperty("app.auth.password", SECRET_SAMPLES[0]);
        env.setProperty("alist.base-url", "https://alist.example.com");
        env.setProperty("alist.token", SECRET_SAMPLES[1]);
        env.setProperty("alist.crypto-key", SECRET_SAMPLES[2]);
        env.setProperty("logging.file.path", workDir.resolve("logs").toString());

        AppProperties props = new AppProperties();
        props.setDataDir(workDir.resolve("data").toString());

        TaskExecutionRepository repository = mock(TaskExecutionRepository.class);
        when(repository.findAll()).thenReturn(List.of());

        service = new DiagnosticService(props, env, Optional.of(repository),
            "0.0.1-redaction-test", workDir.resolve("logs").toString());

        // 准备含敏感字符串的日志文件
        Path logs = workDir.resolve("logs");
        Files.createDirectories(logs);
        String appLogContent = String.join("\n",
            "INFO 启动",
            "Authorization: " + SECRET_SAMPLES[3],
            "Cookie: " + SECRET_SAMPLES[4],
            "{\"password\":\"" + SECRET_SAMPLES[0] + "\"}",
            "请求 URL: https://alist.example.com/api?token=" + SECRET_SAMPLES[1] + "&path=/x"
        );
        Files.writeString(logs.resolve("app.log"), appLogContent);
        Files.writeString(logs.resolve("error.log"), "ERROR " + SECRET_SAMPLES[0] + " 调用失败");
    }

    @Test
    void 诊断包内任何文件都不应包含原始敏感样本() throws IOException {
        service.generate(workDir.resolve("diagnostics"), 100);

        Path latestDir = workDir.resolve("diagnostics/latest");
        assertTrue(Files.exists(latestDir));

        try (Stream<Path> files = Files.walk(latestDir)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try {
                    String content = Files.readString(file);
                    for (String secret : SECRET_SAMPLES) {
                        assertFalse(content.contains(secret),
                            "文件 " + latestDir.relativize(file) + " 含原始敏感样本：" + secret);
                    }
                } catch (IOException e) {
                    fail("读取诊断包文件失败：" + file + " - " + e.getMessage());
                }
            });
        }
    }
}
