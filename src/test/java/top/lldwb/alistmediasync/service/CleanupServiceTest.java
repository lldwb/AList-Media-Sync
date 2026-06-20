package top.lldwb.alistmediasync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.config.AppProperties;
import top.lldwb.alistmediasync.repository.TaskExecutionRepository;
import top.lldwb.alistmediasync.repository.WebhookEventRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 清理服务单元测试
 * <p>
 * 覆盖 cleanExpiredRecords()、startupCleanup()、manualCleanup() 三个方法。
 * 文件系统操作使用 @TempDir 真实文件系统。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("清理服务测试")
class CleanupServiceTest {

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private AppProperties appProperties;

    @Mock
    private AppProperties.TranscodeConfig transcodeConfig;

    @InjectMocks
    private CleanupService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(appProperties.getRetentionDays()).thenReturn(30);
        when(appProperties.getTranscode()).thenReturn(transcodeConfig);
        when(transcodeConfig.getTempDir()).thenReturn(tempDir.toString());
        when(transcodeConfig.getTempSuffix()).thenReturn(".tmp");
    }

    // ================================================================
    // cleanExpiredRecords 方法测试
    // ================================================================

    @Test
    @DisplayName("定时清理 — 正常删除过期记录")
    void shouldCleanExpiredRecords() {
        when(taskExecutionRepository.deleteByCreatedAtBefore(any())).thenReturn(10);
        when(webhookEventRepository.deleteByCreatedAtBefore(any())).thenReturn(5);

        assertDoesNotThrow(() -> service.cleanExpiredRecords());

        verify(taskExecutionRepository).deleteByCreatedAtBefore(any());
        verify(webhookEventRepository).deleteByCreatedAtBefore(any());
    }

    @Test
    @DisplayName("定时清理 — 无过期记录时不报错")
    void shouldHandleNoExpiredRecords() {
        when(taskExecutionRepository.deleteByCreatedAtBefore(any())).thenReturn(0);
        when(webhookEventRepository.deleteByCreatedAtBefore(any())).thenReturn(0);

        assertDoesNotThrow(() -> service.cleanExpiredRecords());
    }

    // ================================================================
    // startupCleanup 方法测试
    // ================================================================

    @Test
    @DisplayName("启动清理 — 临时目录不存在时自动创建")
    void shouldCreateTempDirIfNotExists() throws IOException {
        // 使用不存在的子目录
        Path nonExistentDir = tempDir.resolve("new-temp-dir");
        when(transcodeConfig.getTempDir()).thenReturn(nonExistentDir.toString());

        assertDoesNotThrow(() -> service.startupCleanup());
        assertTrue(Files.exists(nonExistentDir), "应自动创建临时目录");
    }

    @Test
    @DisplayName("启动清理 — 清理带后缀的残留文件")
    void shouldCleanResidualFilesOnStartup() throws IOException {
        // 创建测试文件
        Path tmpFile1 = Files.createFile(tempDir.resolve("test1.mp4.tmp"));
        Path tmpFile2 = Files.createFile(tempDir.resolve("test2.flv.tmp"));
        Path normalFile = Files.createFile(tempDir.resolve("normal.txt")); // 不应被删除

        service.startupCleanup();

        assertFalse(Files.exists(tmpFile1), "带 .tmp 后缀的文件应被删除");
        assertFalse(Files.exists(tmpFile2), "带 .tmp 后缀的文件应被删除");
        assertTrue(Files.exists(normalFile), "不带 .tmp 后缀的文件不应被删除");
    }

    @Test
    @DisplayName("启动清理 — 空目录不报错")
    void shouldHandleEmptyTempDir() {
        assertDoesNotThrow(() -> service.startupCleanup());
    }

    // ================================================================
    // manualCleanup 方法测试
    // ================================================================

    @Test
    @DisplayName("手动清理 — 清理带后缀的残留文件并返回数量")
    void shouldManualCleanupAndReturnCount() throws IOException {
        Files.createFile(tempDir.resolve("a.mp4.tmp"));
        Files.createFile(tempDir.resolve("b.avi.tmp"));
        Files.createFile(tempDir.resolve("keep.me")); // 不应被删除

        long count = service.manualCleanup();

        assertEquals(2L, count);
        assertFalse(Files.exists(tempDir.resolve("a.mp4.tmp")));
        assertFalse(Files.exists(tempDir.resolve("b.avi.tmp")));
        assertTrue(Files.exists(tempDir.resolve("keep.me")));
    }

    @Test
    @DisplayName("手动清理 — 临时目录不存在时返回 0")
    void shouldReturnZeroWhenTempDirNotExists() {
        when(transcodeConfig.getTempDir()).thenReturn(tempDir.resolve("nonexistent").toString());

        long count = service.manualCleanup();

        assertEquals(0L, count);
    }

    @Test
    @DisplayName("手动清理 — 无残留文件时返回 0")
    void shouldReturnZeroWhenNoResidualFiles() {
        long count = service.manualCleanup();

        assertEquals(0L, count);
    }

    @Test
    @DisplayName("手动清理 — 自定义后缀正确匹配")
    void shouldRespectCustomSuffix() throws IOException {
        when(transcodeConfig.getTempSuffix()).thenReturn(".lldwb");
        Files.createFile(tempDir.resolve("video.mp4.lldwb"));
        Files.createFile(tempDir.resolve("other.mp4.tmp")); // 不应被删除

        long count = service.manualCleanup();

        assertEquals(1L, count);
        assertFalse(Files.exists(tempDir.resolve("video.mp4.lldwb")));
        assertTrue(Files.exists(tempDir.resolve("other.mp4.tmp")));
    }
}
