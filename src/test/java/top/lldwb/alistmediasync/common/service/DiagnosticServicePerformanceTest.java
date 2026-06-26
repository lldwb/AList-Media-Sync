package top.lldwb.alistmediasync.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.dto.DiagnosticResultVO;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SC-001 性能门禁测试（T021）：模拟典型日志/配置规模，断言诊断生成耗时 ≤ 30 s。
 */
class DiagnosticServicePerformanceTest {

    @TempDir
    Path workDir;

    private DiagnosticService service;

    @BeforeEach
    void setUp() throws IOException {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.data-dir", workDir.resolve("data").toString());
        env.setProperty("alist.base-url", "https://alist.example.com");
        env.setProperty("alist.token", "token-xyz-123-abc");
        env.setProperty("logging.file.path", workDir.resolve("logs").toString());

        AppProperties props = new AppProperties();
        props.setDataDir(workDir.resolve("data").toString());

        TaskExecutionRepository repository = mock(TaskExecutionRepository.class);
        when(repository.findAll()).thenReturn(List.of());

        service = new DiagnosticService(props, env, Optional.of(repository),
            "0.0.1-perf-test", workDir.resolve("logs").toString());

        // 构造典型日志规模：5000 行日志
        Path logs = workDir.resolve("logs");
        Files.createDirectories(logs);
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            big.append("2026-06-27 00:00:00.123 INFO [thread] [traceId=test-").append(i)
                .append("] some module operation - 模拟日志条目 #").append(i).append('\n');
        }
        Files.writeString(logs.resolve("app.log"), big.toString());
        Files.writeString(logs.resolve("error.log"), big.toString());
    }

    @Test
    void 诊断包生成应在30秒内完成() {
        long start = System.currentTimeMillis();
        DiagnosticResultVO result = service.generate(workDir.resolve("diagnostics"), 2000);
        long elapsed = System.currentTimeMillis() - start;

        assertNotEquals(DiagnosticResultVO.Status.FAILED, result.getStatus(),
            "失败状态会跳过性能验证，结果应至少 COMPLETED 或 PARTIAL");
        assertTrue(result.getDurationMs() >= 0, "durationMs 必须被填充");
        assertTrue(elapsed <= 30_000L, "实际耗时超过 30 s：" + elapsed + "ms");
        assertTrue(result.getDurationMs() <= 30_000L, "DiagnosticResult.durationMs 超过 30 s：" + result.getDurationMs());
    }
}
