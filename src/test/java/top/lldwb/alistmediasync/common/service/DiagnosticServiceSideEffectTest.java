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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FR-012 副作用断言测试（T022）：诊断生成不得调用 sync/transcode/webhook 写入路径，
 * 也不得对 TaskExecutionRepository 触发写操作。
 */
class DiagnosticServiceSideEffectTest {

    @TempDir
    Path workDir;

    private TaskExecutionRepository repository;
    private DiagnosticService service;

    @BeforeEach
    void setUp() throws IOException {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.data-dir", workDir.resolve("data").toString());
        env.setProperty("alist.base-url", "https://alist.example.com");
        env.setProperty("alist.token", "token-xyz-123");
        env.setProperty("logging.file.path", workDir.resolve("logs").toString());

        AppProperties props = new AppProperties();
        props.setDataDir(workDir.resolve("data").toString());

        repository = mock(TaskExecutionRepository.class);
        when(repository.findAll()).thenReturn(List.of());

        service = new DiagnosticService(props, env, Optional.of(repository),
            "0.0.1-sideeffect-test", workDir.resolve("logs").toString());

        Files.createDirectories(workDir.resolve("logs"));
        Files.writeString(workDir.resolve("logs/app.log"), "info\n");
        Files.writeString(workDir.resolve("logs/error.log"), "err\n");
    }

    @Test
    void 诊断生成不应调用Repository任何写方法() {
        DiagnosticResultVO result = service.generate(workDir.resolve("diagnostics"), 100);

        assertNotEquals(DiagnosticResultVO.Status.FAILED, result.getStatus());

        // 只允许调用读取方法
        verify(repository, atMostOnce()).findAll();
        verify(repository, never()).save(any());
        verify(repository, never()).saveAll(any());
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteAll();
        verify(repository, never()).deleteAll(any());
        verify(repository, never()).deleteById(any());
        verify(repository, never()).deleteByCreatedAtBefore(any());
        verify(repository, never()).markAllRunningAsInterrupted();
        verify(repository, never()).nullifyTranscodeTaskRefs(any());

        verifyNoMoreInteractions(ignoreStubs(repository));
    }

    @Test
    void 诊断生成应正确处理空仓库不抛出异常() {
        // 即使 repository 为 Optional.empty 也能优雅降级
        DiagnosticService noRepoService = new DiagnosticService(
            new AppProperties(),
            new MockEnvironment(),
            Optional.empty(),
            "test",
            workDir.resolve("logs").toString()
        );
        DiagnosticResultVO result = noRepoService.generate(workDir.resolve("diag2"), 100);
        // 即使没有 repository，诊断包也应生成（PARTIAL）
        assertNotEquals(DiagnosticResultVO.Status.FAILED, result.getStatus());
        assertTrue(result.getMissingItems().stream().anyMatch(m -> m.contains("TaskExecution")));
    }

    @Test
    void 诊断生成不应修改任务执行实体() {
        TaskExecution e = new TaskExecution();
        e.setId(99L);
        e.setStatus(TaskExecution.ExecutionStatus.FAILED);
        e.setTaskType(TaskExecution.TaskType.SYNC);
        e.setCreatedAt(java.time.LocalDateTime.now());
        e.setFailureDetails("模拟失败");
        when(repository.findAll()).thenReturn(List.of(e));

        TaskExecution.ExecutionStatus originalStatus = e.getStatus();
        Long originalId = e.getId();

        service.generate(workDir.resolve("diagnostics"), 100);

        // 验证实体状态未被修改
        assertEquals(originalStatus, e.getStatus());
        assertEquals(originalId, e.getId());
    }
}
