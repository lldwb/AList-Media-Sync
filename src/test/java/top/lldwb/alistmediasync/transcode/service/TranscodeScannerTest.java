package top.lldwb.alistmediasync.transcode.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.common.enums.ConflictStrategy;
import top.lldwb.alistmediasync.storage.dto.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 转码源目录扫描器单元测试
 * <p>
 * 覆盖「源路径 → 待转码候选列表」前置阶段的四条关键分支：目录递归（含目标路径推导）、
 * 非视频文件（扩展名检测为 UNKNOWN）跳过、目标已存在且策略为 SKIP 时跳过、
 * 递归深度上限（10 层）截断。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("转码源目录扫描器测试")
class TranscodeScannerTest {

    /** 与实现一致的递归深度上限，用于断言语义（非直接引用常量，独立声明） */
    private static final int EXPECTED_MAX_DEPTH = 10;

    @Mock
    private StorageEngineService storageEngineService;

    @Mock
    private StorageEngineStrategy sourceStrategy;

    @Mock
    private StorageEngineStrategy targetStrategy;

    private TranscodeScanner scanner;
    private StorageEngine sourceEngine;
    private StorageEngine targetEngine;

    @BeforeEach
    void setUp() {
        scanner = new TranscodeScanner(storageEngineService);

        sourceEngine = new StorageEngine();
        sourceEngine.setId(1L);
        sourceEngine.setName("源引擎");

        targetEngine = new StorageEngine();
        targetEngine.setId(2L);
        targetEngine.setName("目标引擎");
    }

    // ================================================================
    // 路径类型判定
    // ================================================================

    @Test
    @DisplayName("isDirectory — 源引擎为 null 时按文件处理，不访问存储")
    void shouldTreatNullSourceEngineAsFile() {
        assertFalse(scanner.isDirectory(null, "/videos/a.mp4"));

        verifyNoInteractions(storageEngineService);
    }

    @Test
    @DisplayName("isDirectory — 文件信息为目录时返回 true")
    void shouldReturnTrueForDirectory() {
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(sourceStrategy.getFileInfo(sourceEngine, "/videos")).thenReturn(directory("videos"));

        assertTrue(scanner.isDirectory(sourceEngine, "/videos"));
    }

    @Test
    @DisplayName("isDirectory — 文件信息为文件时返回 false")
    void shouldReturnFalseForFile() {
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(sourceStrategy.getFileInfo(sourceEngine, "/videos/a.mp4")).thenReturn(file("a.mp4", 1024));

        assertFalse(scanner.isDirectory(sourceEngine, "/videos/a.mp4"));
    }

    @Test
    @DisplayName("isDirectory — 文件信息为 null 时按文件处理")
    void shouldTreatNullFileInfoAsFile() {
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(sourceStrategy.getFileInfo(sourceEngine, "/videos/unknown")).thenReturn(null);

        assertFalse(scanner.isDirectory(sourceEngine, "/videos/unknown"));
    }

    @Test
    @DisplayName("isDirectory — 查询异常时降级按文件处理")
    void shouldFallbackToFileWhenQueryFails() {
        when(storageEngineService.resolve(sourceEngine)).thenReturn(sourceStrategy);
        when(sourceStrategy.getFileInfo(sourceEngine, "/videos"))
            .thenThrow(new RuntimeException("AList 连接超时"));

        assertFalse(scanner.isDirectory(sourceEngine, "/videos"));
    }

    // ================================================================
    // 目录扫描
    // ================================================================

    @Test
    @DisplayName("扫描 — 只收集视频文件，非视频文件按 UNKNOWN 跳过")
    void shouldCollectOnlyVideoFiles() {
        stubListings(Map.of("/videos", List.of(
            file("video.mp4", 100L),
            file("notes.txt", 10L),
            file("cover.jpg", 20L),
            file("clip.m4v", 300L),
            file("live.flv", 400L))));

        List<TranscodeCandidate> candidates = scan("/videos", "/out", ConflictStrategy.SKIP);

        assertEquals(List.of("video.mp4", "clip.m4v", "live.flv"),
            candidates.stream().map(TranscodeCandidate::name).toList());
        assertEquals(List.of("MP4", "M4V", "FLV"),
            candidates.stream().map(TranscodeCandidate::format).toList());
        assertEquals(300L, candidates.get(1).size());
        assertSame(sourceEngine, candidates.get(0).sourceEngine());
        // 目标冲突探测只对视频文件发生（3 个视频文件 → 3 次探测），非视频文件在探测前已跳过
        verify(targetStrategy, times(3)).getFileInfo(eq(targetEngine), anyString());
    }

    @Test
    @DisplayName("扫描 — 目录全部为非视频文件时返回空候选")
    void shouldReturnEmptyWhenNoVideoFile() {
        stubListings(Map.of("/videos", List.of(
            file("readme.txt", 1L),
            file("audio.mp3", 2L),
            file("data.json", 3L))));

        assertTrue(scan("/videos", "/out", ConflictStrategy.SKIP).isEmpty());
    }

    @Test
    @DisplayName("扫描 — 空目录返回空候选")
    void shouldReturnEmptyForEmptyDirectory() {
        stubListings(Map.of());

        assertTrue(scan("/videos", "/out", ConflictStrategy.SKIP).isEmpty());
    }

    @Test
    @DisplayName("扫描 — 递归扫描子目录并推导子目录目标路径")
    void shouldRecurseIntoSubDirectoryAndDeriveTargetPath() {
        stubListings(Map.of(
            "/videos", List.of(directory("2026"), file("root.mp4", 1L)),
            "/videos/2026", List.of(file("live.flv", 2L))));

        List<TranscodeCandidate> candidates = scan("/videos", "/out", ConflictStrategy.SKIP);

        assertEquals(2, candidates.size());
        TranscodeCandidate nested = candidates.stream()
            .filter(c -> "live.flv".equals(c.name())).findFirst().orElseThrow();
        assertEquals("/videos/2026/live.flv", nested.fullPath());
        assertEquals("/out/2026/live.flv", nested.targetPath());
        assertEquals("FLV", nested.format());
    }

    @Test
    @DisplayName("扫描 — 目标已存在且策略为 SKIP 时跳过该文件")
    void shouldSkipWhenTargetExistsAndStrategyIsSkip() {
        stubListings(Map.of("/videos", List.of(file("a.mp4", 1L))));
        // 目标探测路径为「目标目录 / 源文件名换 mp3 扩展名」
        when(targetStrategy.getFileInfo(targetEngine, "/out/a.mp3")).thenReturn(file("a.mp3", 1L));

        List<TranscodeCandidate> candidates = scan("/videos", "/out", ConflictStrategy.SKIP);

        assertTrue(candidates.isEmpty());
        verify(targetStrategy).getFileInfo(targetEngine, "/out/a.mp3");
    }

    @Test
    @DisplayName("扫描 — 目标已存在但策略为 OVERWRITE 时不跳过")
    void shouldNotSkipWhenStrategyIsOverwrite() {
        stubListings(Map.of("/videos", List.of(file("a.mp4", 1L))));
        when(targetStrategy.getFileInfo(targetEngine, "/out/a.mp3")).thenReturn(file("a.mp3", 1L));

        List<TranscodeCandidate> candidates = scan("/videos", "/out", ConflictStrategy.OVERWRITE);

        assertEquals(1, candidates.size());
        assertEquals("a.mp4", candidates.get(0).name());
    }

    @Test
    @DisplayName("扫描 — 目标不存在时不跳过")
    void shouldNotSkipWhenTargetMissing() {
        stubListings(Map.of("/videos", List.of(file("a.mp4", 1L))));
        when(targetStrategy.getFileInfo(targetEngine, "/out/a.mp3")).thenReturn(null);

        assertEquals(1, scan("/videos", "/out", ConflictStrategy.SKIP).size());
    }

    @Test
    @DisplayName("扫描 — 目标探测异常时不跳过（失败降级为可转码）")
    void shouldNotSkipWhenTargetProbeFails() {
        stubListings(Map.of("/videos", List.of(file("a.mp4", 1L))));
        when(targetStrategy.getFileInfo(targetEngine, "/out/a.mp3"))
            .thenThrow(new RuntimeException("目标引擎不可用"));

        assertEquals(1, scan("/videos", "/out", ConflictStrategy.SKIP).size());
    }

    @Test
    @DisplayName("扫描 — 递归深度达到上限（10 层）时停止下探")
    void shouldStopRecursionAtMaxDepth() {
        // 每层目录都返回一个同名子目录，形成无限深目录树
        when(sourceStrategy.listFiles(eq(sourceEngine), anyString(), anyInt(), anyInt()))
            .thenAnswer(inv -> List.of(directory("d")));

        List<TranscodeCandidate> candidates = scanner.scanSourceDirectory(
            sourceEngine, sourceStrategy, targetEngine, targetStrategy,
            "/root", "/out", ConflictStrategy.SKIP);

        assertTrue(candidates.isEmpty());
        // 深度 1..10 各下探一次，第 11 层被截断——若上限被改动，调用次数随之改变
        verify(sourceStrategy, times(EXPECTED_MAX_DEPTH))
            .listFiles(eq(sourceEngine), anyString(), anyInt(), anyInt());
    }

    // ================================================================
    // 测试辅助
    // ================================================================

    private List<TranscodeCandidate> scan(String sourcePath, String targetPath, ConflictStrategy strategy) {
        return scanner.scanSourceDirectory(sourceEngine, sourceStrategy, targetEngine, targetStrategy,
            sourcePath, targetPath, strategy);
    }

    /** 按路径桩化源目录列举结果，未声明的路径返回空目录（避免递归时返回 null） */
    private void stubListings(Map<String, List<FileEntry>> listingsByPath) {
        when(sourceStrategy.listFiles(eq(sourceEngine), anyString(), anyInt(), anyInt()))
            .thenAnswer(invocation -> listingsByPath.getOrDefault(invocation.getArgument(1), List.of()));
    }

    private static FileEntry directory(String name) {
        return new FileEntry(name, null, true, 0L, null);
    }

    private static FileEntry file(String name, long size) {
        return new FileEntry(name, null, false, size, null);
    }
}
