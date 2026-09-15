package top.lldwb.alistmediasync.ops;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.execution.TaskExecutionRepository;
import top.lldwb.alistmediasync.webhook.repository.WebhookEventRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 清理服务单元测试
 * <p>
 * 覆盖 cleanExpiredRecords()、cleanOrphanedTempFiles()、startupCleanup()、manualCleanup() 四个方法。
 * 文件系统操作使用 @TempDir 真实文件系统，不做静态 mock。
 * </p>
 * <p>
 * 断言口径：cutoff 采用 {@link ArgumentCaptor} 断言其等于 {@code now - retentionDays}（±1 分钟），
 * 文件清理断言「过期文件确实被删除 / 新文件确实被保留 / 目录未被误删」，
 * 不使用 assertDoesNotThrow 这类无法证伪的断言。
 * </p>
 * <p>
 * <b>如实记录的现状（疑似缺陷，见各用例注释）</b>：文件过滤条件中的
 * {@code startsWith("src-")} / {@code startsWith("out-")} 前缀分支在当前代码库中无生产者，
 * 真实转码源文件命名为 {@code alist-src-*}（见 TranscodeFileProcessor），不会命中该分支。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("清理服务测试")
class CleanupServiceTest {

    /** 过期时间戳：25 小时前（超过 CleanupService 中 24 小时的孤立文件阈值） */
    private static final long EXPIRED_AGE_MS = 25 * 3600_000L;

    /** cutoff 允许的时钟漂移（秒） */
    private static final long CUTOFF_DRIFT_TOLERANCE_SECONDS = 60;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private AppProperties appProperties;

    @Mock
    private AppProperties.Transcode transcodeConfig;

    @InjectMocks
    private CleanupService service;

    @TempDir
    Path tempDir;

    // ================================================================
    // cleanExpiredRecords 方法测试
    // ================================================================

    @Test
    @DisplayName("定时清理 — cutoff 按保留 30 天计算，两个 Repository 各删除一次且使用同一 cutoff")
    void shouldCleanExpiredRecords() {
        when(appProperties.getRetentionDays()).thenReturn(30);
        when(taskExecutionRepository.deleteByCreatedAtBefore(any())).thenReturn(10);
        when(webhookEventRepository.deleteByCreatedAtBefore(any())).thenReturn(5);

        service.cleanExpiredRecords();

        ArgumentCaptor<LocalDateTime> executionCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> eventCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskExecutionRepository, times(1)).deleteByCreatedAtBefore(executionCutoff.capture());
        verify(webhookEventRepository, times(1)).deleteByCreatedAtBefore(eventCutoff.capture());

        assertEquals(executionCutoff.getValue(), eventCutoff.getValue(),
            "两个 Repository 应使用同一个 cutoff");
        assertCutoffEquals(executionCutoff.getValue(), 30);
    }

    @Test
    @DisplayName("定时清理 — 保留天数取自配置（7 天）而非默认值，无记录时同样按配置计算 cutoff")
    void shouldComputeCutoffFromRetentionDaysWhenNoExpiredRecords() {
        when(appProperties.getRetentionDays()).thenReturn(7);
        when(taskExecutionRepository.deleteByCreatedAtBefore(any())).thenReturn(0);
        when(webhookEventRepository.deleteByCreatedAtBefore(any())).thenReturn(0);

        service.cleanExpiredRecords();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskExecutionRepository, times(1)).deleteByCreatedAtBefore(cutoff.capture());
        verify(webhookEventRepository, times(1)).deleteByCreatedAtBefore(any());

        assertCutoffEquals(cutoff.getValue(), 7);
        assertTrue(cutoff.getValue().isAfter(LocalDateTime.now().minusDays(8)),
            "cutoff 应基于配置的 7 天计算，而非默认 30 天，实际：" + cutoff.getValue());
    }

    // ================================================================
    // cleanOrphanedTempFiles 方法测试
    // ================================================================

    @Test
    @DisplayName("孤立文件清理 — 超过 24 小时的文件被删除（含子目录），新文件保留")
    void shouldDeleteExpiredOrphanedFilesAndKeepRecentOnes() throws IOException {
        stubTempDir(tempDir);

        Path expired = createFileWithAge(tempDir.resolve("expired-source.mp4"), EXPIRED_AGE_MS);
        Path recent = Files.createFile(tempDir.resolve("recent-source.mp4"));
        Path nestedDir = Files.createDirectories(tempDir.resolve("nested"));
        Path nestedExpired = createFileWithAge(nestedDir.resolve("nested-expired.mp4"), EXPIRED_AGE_MS);

        service.cleanOrphanedTempFiles();

        assertFalse(Files.exists(expired), "超过 24 小时的孤立文件应被删除");
        assertFalse(Files.exists(nestedExpired), "子目录中超过 24 小时的文件应被删除");
        assertTrue(Files.exists(recent), "刚创建的文件不应被删除");
        assertTrue(Files.isDirectory(nestedDir), "目录本身不应被删除");
    }

    @Test
    @DisplayName("孤立文件清理 — 临时目录不存在时早退且不创建目录")
    void shouldSkipOrphanedCleanupWhenTempDirNotExists() {
        Path missingDir = tempDir.resolve("missing-temp-dir");
        stubTempDir(missingDir);

        service.cleanOrphanedTempFiles();

        assertFalse(Files.exists(missingDir),
            "清理孤立文件不应创建目录（创建目录是 startupCleanup 的职责）");
    }

    @Test
    @DisplayName("孤立文件清理 — 删除失败时文件保留（仅告警，不中断整体清理）")
    void shouldKeepFileAndContinueWhenDeleteFails() throws IOException {
        // 仅 Windows 可稳定制造删除失败（DOS 只读属性使 Files.delete 抛 AccessDeniedException）；
        // 须在任何 stub 之前做假设判断，避免跳过时留下未使用的 stub
        Assumptions.assumeTrue(supportsDosReadOnlyAttribute(),
            "当前文件系统不支持 dos:readonly 属性，无法制造删除失败场景");

        stubTempDir(tempDir);

        Path readOnly = createFileWithAge(tempDir.resolve("locked.mp4"), EXPIRED_AGE_MS);
        Path deletable = createFileWithAge(tempDir.resolve("normal-expired.mp4"), EXPIRED_AGE_MS);
        Files.setAttribute(readOnly, "dos:readonly", true);

        try {
            service.cleanOrphanedTempFiles();

            assertTrue(Files.exists(readOnly), "删除失败的文件应保留（仅记录 WARN 日志）");
            assertFalse(Files.exists(deletable), "同一批次中可删除的过期文件仍应被清理");
        } finally {
            // 复位只读属性，否则 @TempDir 清理阶段无法删除该文件
            Files.setAttribute(readOnly, "dos:readonly", false);
        }
    }

    // ================================================================
    // startupCleanup 方法测试
    // ================================================================

    @Test
    @DisplayName("启动清理 — 临时目录不存在时自动创建")
    void shouldCreateTempDirIfNotExists() {
        Path nonExistentDir = tempDir.resolve("new-temp-dir");
        stubTempDir(nonExistentDir);

        service.startupCleanup();

        assertTrue(Files.isDirectory(nonExistentDir), "应自动创建临时目录");
    }

    @Test
    @DisplayName("启动清理 — 清理带后缀的残留文件，其余文件保留")
    void shouldCleanResidualFilesOnStartup() throws IOException {
        stubTranscodeConfig(tempDir, ".tmp");

        Path tmpFile1 = Files.createFile(tempDir.resolve("test1.mp4.tmp"));
        Path tmpFile2 = Files.createFile(tempDir.resolve("test2.flv.tmp"));
        Path normalFile = Files.createFile(tempDir.resolve("normal.txt"));

        service.startupCleanup();

        assertFalse(Files.exists(tmpFile1), "带 .tmp 后缀的文件应被删除");
        assertFalse(Files.exists(tmpFile2), "带 .tmp 后缀的文件应被删除");
        assertTrue(Files.exists(normalFile), "不带 .tmp 后缀的文件不应被删除");
    }

    @Test
    @DisplayName("启动清理 — src- / out- 前缀分支命中并删除，alist-src- 真实命名不命中（如实记录）")
    void shouldCleanLegacySrcAndOutPrefixedFilesOnStartup() throws IOException {
        stubTranscodeConfig(tempDir, ".tmp");

        Path srcPrefixed = Files.createFile(tempDir.resolve("src-machine1.flv"));
        Path outPrefixed = Files.createFile(tempDir.resolve("out-machine1.flv"));
        // 如实记录现状：转码下载的源临时文件实际命名为 alist-src-*（TranscodeFileProcessor），
        // 不匹配 startsWith("src-") 分支，且创建在 java.io.tmpdir 下，不在本目录中
        Path realSourceNamed = Files.createFile(tempDir.resolve("alist-src-123456.mp4"));
        Path normalFile = Files.createFile(tempDir.resolve("keep.me"));

        service.startupCleanup();

        assertFalse(Files.exists(srcPrefixed), "src- 前缀分支应命中并删除该文件");
        assertFalse(Files.exists(outPrefixed), "out- 前缀分支应命中并删除该文件");
        assertTrue(Files.exists(realSourceNamed),
            "alist-src- 前缀不匹配 src- 分支，且后缀不是 .tmp，故被保留（疑似缺陷：清理漏网）");
        assertTrue(Files.exists(normalFile), "既不匹配后缀也不匹配前缀的文件应保留");
    }

    @Test
    @DisplayName("启动清理 — 空目录与子目录均不被误删")
    void shouldHandleEmptyTempDir() throws IOException {
        stubTranscodeConfig(tempDir, ".tmp");

        Path subDir = Files.createDirectories(tempDir.resolve("sub"));
        Path subFile = Files.createFile(subDir.resolve("keep.me"));

        service.startupCleanup();

        assertTrue(Files.isDirectory(tempDir), "临时目录本身不应被删除");
        assertTrue(Files.isDirectory(subDir), "不匹配过滤条件的子目录不应被删除");
        assertTrue(Files.exists(subFile), "不匹配过滤条件的文件不应被删除");
    }

    // ================================================================
    // manualCleanup 方法测试
    // ================================================================

    @Test
    @DisplayName("手动清理 — 清理带后缀的残留文件并返回数量")
    void shouldManualCleanupAndReturnCount() throws IOException {
        stubTranscodeConfig(tempDir, ".tmp");
        Files.createFile(tempDir.resolve("a.mp4.tmp"));
        Files.createFile(tempDir.resolve("b.avi.tmp"));
        Files.createFile(tempDir.resolve("keep.me"));

        long count = service.manualCleanup();

        assertEquals(2L, count);
        assertFalse(Files.exists(tempDir.resolve("a.mp4.tmp")));
        assertFalse(Files.exists(tempDir.resolve("b.avi.tmp")));
        assertTrue(Files.exists(tempDir.resolve("keep.me")));
    }

    @Test
    @DisplayName("手动清理 — src- / out- 前缀分支命中，alist-src- 真实命名漏网（如实记录）")
    void shouldCleanLegacySrcAndOutPrefixedFilesOnManualCleanup() throws IOException {
        stubTranscodeConfig(tempDir, ".tmp");
        Files.createFile(tempDir.resolve("src-machine1.flv"));
        Files.createFile(tempDir.resolve("out-machine1.flv"));
        Files.createFile(tempDir.resolve("alist-src-123456.mp4"));
        Files.createFile(tempDir.resolve("keep.me"));

        long count = service.manualCleanup();

        assertEquals(2L, count, "仅 src- / out- 前缀文件命中过滤条件");
        assertFalse(Files.exists(tempDir.resolve("src-machine1.flv")));
        assertFalse(Files.exists(tempDir.resolve("out-machine1.flv")));
        assertTrue(Files.exists(tempDir.resolve("alist-src-123456.mp4")),
            "alist-src- 前缀不匹配 src- 分支，清理会漏掉真实命名的源临时文件（疑似缺陷）");
        assertTrue(Files.exists(tempDir.resolve("keep.me")));
    }

    @Test
    @DisplayName("手动清理 — 临时目录不存在时返回 0 且不创建目录")
    void shouldReturnZeroWhenTempDirNotExists() {
        Path missingDir = tempDir.resolve("nonexistent");
        stubTempDir(missingDir);

        long count = service.manualCleanup();

        assertEquals(0L, count);
        assertFalse(Files.exists(missingDir), "手动清理不应创建临时目录");
    }

    @Test
    @DisplayName("手动清理 — 空目录返回 0 且目录未被误删")
    void shouldReturnZeroWhenNoResidualFiles() {
        stubTranscodeConfig(tempDir, ".tmp");

        long count = service.manualCleanup();

        assertEquals(0L, count);
        assertTrue(Files.isDirectory(tempDir), "空目录不应被删除");
    }

    @Test
    @DisplayName("手动清理 — 自定义后缀正确匹配")
    void shouldRespectCustomSuffix() throws IOException {
        stubTranscodeConfig(tempDir, ".lldwb");
        Files.createFile(tempDir.resolve("video.mp4.lldwb"));
        Files.createFile(tempDir.resolve("other.mp4.tmp"));

        long count = service.manualCleanup();

        assertEquals(1L, count);
        assertFalse(Files.exists(tempDir.resolve("video.mp4.lldwb")));
        assertTrue(Files.exists(tempDir.resolve("other.mp4.tmp")), "非配置后缀的文件不应被删除");
    }

    // ================================================================
    // 测试辅助
    // ================================================================

    /** 仅配置临时目录（清理孤立文件只读 tempDir，不读 tempSuffix） */
    private void stubTempDir(Path dir) {
        when(appProperties.getTranscode()).thenReturn(transcodeConfig);
        when(transcodeConfig.getTempDir()).thenReturn(dir.toString());
    }

    /** 配置临时目录与临时后缀（启动清理 / 手动清理需要） */
    private void stubTranscodeConfig(Path dir, String suffix) {
        stubTempDir(dir);
        when(transcodeConfig.getTempSuffix()).thenReturn(suffix);
    }

    /** 创建文件并把最后修改时间设置为 {@code ageMs} 毫秒之前 */
    private static Path createFileWithAge(Path file, long ageMs) throws IOException {
        Path created = Files.createFile(file);
        Files.setLastModifiedTime(created, FileTime.fromMillis(System.currentTimeMillis() - ageMs));
        return created;
    }

    /** 断言 cutoff 等于 {@code now - days 天}（允许 ±1 分钟时钟漂移） */
    private static void assertCutoffEquals(LocalDateTime cutoff, int days) {
        LocalDateTime expected = LocalDateTime.now().minusDays(days);
        long driftSeconds = Math.abs(Duration.between(expected, cutoff).getSeconds());

        assertTrue(driftSeconds <= CUTOFF_DRIFT_TOLERANCE_SECONDS,
            "cutoff 应为 now-" + days + "天（±1 分钟），实际 " + cutoff
                + "，偏差 " + driftSeconds + " 秒");
    }

    /** 判断当前文件系统是否支持 DOS 只读属性（Windows 为 true，用于制造删除失败场景） */
    private boolean supportsDosReadOnlyAttribute() {
        try {
            return Files.getFileStore(tempDir).supportsFileAttributeView("dos");
        } catch (IOException e) {
            return false;
        }
    }
}
