package top.lldwb.alistmediasync.storage.service.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import top.lldwb.alistmediasync.storage.dto.DirectoryEntryVO;
import top.lldwb.alistmediasync.storage.dto.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * LocalStorageStrategy 单元测试
 * <p>
 * 使用临时目录验证本地文件系统操作。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("LocalStorageStrategy 单元测试")
class LocalStorageStrategyTest {

    private LocalStorageStrategy strategy;
    private StorageEngine engine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new LocalStorageStrategy();
        engine = new StorageEngine();
        engine.setId(1L);
        engine.setName("本地测试");
        engine.setEngineType(StorageEngine.EngineType.LOCAL);
        engine.setLocalPath(tempDir.toString());
    }

    @Test
    @DisplayName("type() 应返回 LOCAL")
    void typeShouldReturnLOCAL() {
        assertEquals("LOCAL", strategy.type());
    }

    @Test
    @DisplayName("testConnection 对有效临时目录应返回 true")
    void testConnectionShouldReturnTrueForValidTempDir() {
        assertTrue(strategy.testConnection(engine));
    }

    @Test
    @DisplayName("testConnection 对不存在的路径应返回 false")
    void testConnectionShouldReturnFalseForNonExistentPath() {
        engine.setLocalPath("/nonexistent/path/xyz");
        assertFalse(strategy.testConnection(engine));
    }

    @Test
    @DisplayName("testConnection 对指向文件的路径应返回 false（路径存在但不是目录）")
    void testConnectionShouldReturnFalseWhenPathIsFile() throws IOException {
        Path file = Files.createFile(tempDir.resolve("not-a-dir.txt"));
        engine.setLocalPath(file.toString());

        assertFalse(strategy.testConnection(engine));
    }

    @Test
    @DisplayName("testConnection 对不可读目录应返回 false")
    void testConnectionShouldReturnFalseWhenDirectoryNotReadable() {
        // Windows 无法用 ACL 稳定构造"不可读目录"，因此用静态 mock 精确控制 Files.isReadable 的返回：
        // 只要 !isReadable 分支被删除，本用例会得到 true 而失败。
        Path dir = Path.of(engine.getLocalPath());
        try (MockedStatic<Files> files = mockStatic(Files.class)) {
            files.when(() -> Files.exists(dir)).thenReturn(true);
            files.when(() -> Files.isDirectory(dir)).thenReturn(true);
            files.when(() -> Files.isReadable(dir)).thenReturn(false);

            assertFalse(strategy.testConnection(engine));
        }
    }

    @Test
    @DisplayName("testConnection 对不可写目录应返回 false")
    void testConnectionShouldReturnFalseWhenDirectoryNotWritable() {
        // 同上：只要 !isWritable 分支被删除，本用例会得到 true 而失败。
        Path dir = Path.of(engine.getLocalPath());
        try (MockedStatic<Files> files = mockStatic(Files.class)) {
            files.when(() -> Files.exists(dir)).thenReturn(true);
            files.when(() -> Files.isDirectory(dir)).thenReturn(true);
            files.when(() -> Files.isReadable(dir)).thenReturn(true);
            files.when(() -> Files.isWritable(dir)).thenReturn(false);

            assertFalse(strategy.testConnection(engine));
        }
    }

    @Test
    @DisplayName("listFiles 对空目录应返回空列表")
    void listFilesShouldReturnEmptyForEmptyDir() {
        List<FileEntry> result = strategy.listFiles(engine, "/", 1, 50);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listFiles 应列出目录中的文件")
    void listFilesShouldListFilesInDirectory() throws IOException {
        Files.createFile(tempDir.resolve("test.txt"));
        Files.createDirectory(tempDir.resolve("subdir"));

        List<FileEntry> result = strategy.listFiles(engine, "/", 1, 50);
        assertEquals(2, result.size());
        // 目录应排在文件前面
        assertTrue(result.get(0).isDirectory());
        assertEquals("subdir", result.get(0).name());
        assertFalse(result.get(1).isDirectory());
        assertEquals("test.txt", result.get(1).name());
    }

    @Test
    @DisplayName("listFiles 应一次性返回全量（忽略分页参数，避免 O(n²) 重复扫描）")
    void listFilesShouldReturnAllEntriesIgnoringPagination() throws IOException {
        for (int i = 0; i < 5; i++) {
            Files.createFile(tempDir.resolve("file" + i + ".txt"));
        }

        List<FileEntry> page1 = strategy.listFiles(engine, "/", 1, 2);
        assertEquals(5, page1.size(), "本地引擎应一次返回全部条目");

        List<FileEntry> page2 = strategy.listFiles(engine, "/", 2, 2);
        assertEquals(5, page2.size(), "任意页码均返回全量");
    }

    @Test
    @DisplayName("listFiles 对空目录应返回空列表（不受分页参数影响）")
    void listFilesShouldReturnEmptyForEmptyDirWithPagination() throws IOException {
        List<FileEntry> result = strategy.listFiles(engine, "/", 2, 50);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("resolvePath 应拒绝包含 .. 的越界路径")
    void resolvePathShouldRejectTraversal() throws IOException {
        Files.createDirectories(tempDir.resolve("safe"));
        Path outside = tempDir.resolveSibling("outside.txt");

        assertThrows(IllegalArgumentException.class,
            () -> strategy.downloadFile(engine, "/../../outside.txt"));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.deleteFile(engine, "/../" + tempDir.getFileName() + "/.."));
        // 正常路径不受影响
        strategy.uploadFile(engine, "/safe/ok.txt",
            new ByteArrayInputStream("ok".getBytes()), 2);
        assertTrue(Files.exists(tempDir.resolve("safe/ok.txt")));
        // 越界写入应被拒绝（"哨兵文件未被篡改"的行为断言见 uploadFileShouldNotTouchFileOutsideEngineRoot）
        assertThrows(IllegalArgumentException.class,
            () -> strategy.uploadFile(engine, "/../" + outside.getFileName(),
                new ByteArrayInputStream("x".getBytes()), 1));
    }

    @Test
    @DisplayName("越界上传应被拒绝，且引擎根目录之外的哨兵文件既不被创建也不被篡改")
    void uploadFileShouldNotTouchFileOutsideEngineRoot() throws IOException {
        // 引擎根指向 tempDir/root，哨兵文件放在 tempDir/outside.txt（在引擎根之外，但仍在 @TempDir 内便于自动清理）
        Path engineRoot = Files.createDirectory(tempDir.resolve("root"));
        engine.setLocalPath(engineRoot.toString());

        Path sentinel = tempDir.resolve("outside.txt");
        Files.writeString(sentinel, "SENTINEL-ORIGINAL");

        // "/../outside.txt" 归一化后为 tempDir/outside.txt，逃逸出引擎根
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> strategy.uploadFile(engine, "/../outside.txt",
                new ByteArrayInputStream("polluted".getBytes()), 8));
        assertTrue(ex.getMessage().contains("非法路径"), "应由越界防护抛出，实际消息：" + ex.getMessage());

        // 若越界防护失效，uploadFile 会用 "polluted" 覆盖哨兵文件 —— 下面两条断言随即失败
        assertTrue(Files.exists(sentinel), "越界写入被拒绝后哨兵文件不应消失");
        assertEquals("SENTINEL-ORIGINAL", Files.readString(sentinel), "越界写入被拒绝后哨兵文件内容不应被污染");
    }

    @Test
    @DisplayName("getFileInfo 应返回文件信息")
    void getFileInfoShouldReturnFileInfo() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello");

        FileEntry result = strategy.getFileInfo(engine, "/hello.txt");
        assertNotNull(result);
        assertEquals("hello.txt", result.name());
        assertFalse(result.isDirectory());
        assertEquals(5, result.size());
    }

    @Test
    @DisplayName("getFileInfo 对不存在的文件应返回 null")
    void getFileInfoShouldReturnNullForNonExistent() {
        FileEntry result = strategy.getFileInfo(engine, "/nonexistent.txt");
        assertNull(result);
    }

    @Test
    @DisplayName("downloadFile 应返回文件输入流")
    void downloadFileShouldReturnInputStream() throws IOException {
        Files.writeString(tempDir.resolve("data.txt"), "test data");

        try (InputStream is = strategy.downloadFile(engine, "/data.txt")) {
            String content = new String(is.readAllBytes());
            assertEquals("test data", content);
        }
    }

    @Test
    @DisplayName("uploadFile 应写入文件")
    void uploadFileShouldWriteFile() throws IOException {
        byte[] data = "uploaded content".getBytes();
        strategy.uploadFile(engine, "/uploaded.txt", new ByteArrayInputStream(data), data.length);

        String content = Files.readString(tempDir.resolve("uploaded.txt"));
        assertEquals("uploaded content", content);
    }

    @Test
    @DisplayName("createDirectory 应创建目录")
    void createDirectoryShouldCreateDir() {
        strategy.createDirectory(engine, "/newdir");
        assertTrue(Files.isDirectory(tempDir.resolve("newdir")));
    }

    @Test
    @DisplayName("deleteFile 应删除文件")
    void deleteFileShouldDelete() throws IOException {
        Files.writeString(tempDir.resolve("to_delete.txt"), "bye");
        assertTrue(Files.exists(tempDir.resolve("to_delete.txt")));

        strategy.deleteFile(engine, "/to_delete.txt");
        assertFalse(Files.exists(tempDir.resolve("to_delete.txt")));
    }

    @Test
    @DisplayName("listDirectories 应仅返回子目录")
    void listDirectoriesShouldReturnOnlyDirectories() throws IOException {
        Files.createDirectory(tempDir.resolve("music"));
        Files.createDirectory(tempDir.resolve("videos"));
        Files.createFile(tempDir.resolve("readme.md"));

        List<DirectoryEntryVO> result = strategy.listDirectories(engine, "/");
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(d -> d.name().equals("music")));
        assertTrue(result.stream().anyMatch(d -> d.name().equals("videos")));
        assertTrue(result.stream().noneMatch(d -> d.name().equals("readme.md")));
    }

    @Test
    @DisplayName("listDirectories 应对空子目录返回 hasChildren=false")
    void listDirectoriesShouldReturnHasChildrenFalseForEmptyDirs() throws IOException {
        Files.createDirectory(tempDir.resolve("empty"));

        List<DirectoryEntryVO> result = strategy.listDirectories(engine, "/");
        assertEquals(1, result.size());
        assertFalse(result.get(0).hasChildren());
    }

    @Test
    @DisplayName("listDirectories 应对含子目录的目录返回 hasChildren=true")
    void listDirectoriesShouldReturnHasChildrenTrue() throws IOException {
        Path parent = Files.createDirectory(tempDir.resolve("parent"));
        Files.createDirectory(parent.resolve("child"));

        List<DirectoryEntryVO> result = strategy.listDirectories(engine, "/");
        assertEquals(1, result.size());
        assertTrue(result.get(0).hasChildren());
    }

    @Test
    @DisplayName("listEntries 应同时返回目录和文件（目录在前、名称升序）")
    void listEntriesShouldReturnBothDirectoriesAndFiles() throws IOException {
        Files.createDirectory(tempDir.resolve("b_dir"));
        Files.createDirectory(tempDir.resolve("a_dir"));
        Files.createFile(tempDir.resolve("b_file.txt"));
        Files.createFile(tempDir.resolve("a_file.txt"));

        List<FileEntry> result = strategy.listEntries(engine, "/");
        assertEquals(4, result.size());
        // 目录在前并按名称升序
        assertTrue(result.get(0).isDirectory());
        assertEquals("a_dir", result.get(0).name());
        assertTrue(result.get(1).isDirectory());
        assertEquals("b_dir", result.get(1).name());
        // 文件在后并按名称升序
        assertFalse(result.get(2).isDirectory());
        assertEquals("a_file.txt", result.get(2).name());
        assertFalse(result.get(3).isDirectory());
        assertEquals("b_file.txt", result.get(3).name());
    }

    @Test
    @DisplayName("listEntries 对空目录应返回空列表")
    void listEntriesShouldReturnEmptyForEmptyDir() {
        List<FileEntry> result = strategy.listEntries(engine, "/");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listEntries 对不存在的目录应返回空列表")
    void listEntriesShouldReturnEmptyForMissingDir() {
        List<FileEntry> result = strategy.listEntries(engine, "/no-such-dir");
        assertTrue(result.isEmpty());
    }

    // ================================================================
    // deleteFile —— 递归删除目录分支
    // ================================================================

    @Test
    @DisplayName("deleteFile 应递归删除非空目录及其多层子目录")
    void deleteFileShouldRecursivelyDeleteDirectoryTree() throws IOException {
        Path nested = Files.createDirectories(tempDir.resolve("season/2026/07"));
        Files.writeString(nested.resolve("ep01.mp4"), "ep01");
        Files.writeString(tempDir.resolve("season/cover.jpg"), "cover");
        // 同级保留一个文件，验证递归删除不会越出目标目录子树
        Files.writeString(tempDir.resolve("keep.txt"), "keep");

        strategy.deleteFile(engine, "/season");

        assertFalse(Files.exists(tempDir.resolve("season")), "目标目录树应被整体删除");
        assertFalse(Files.exists(nested), "多层嵌套子目录应被删除");
        assertFalse(Files.exists(tempDir.resolve("season/cover.jpg")), "子树内的文件应被删除");
        assertTrue(Files.exists(tempDir.resolve("keep.txt")), "同级的其它文件不应被删除");
    }

    @Test
    @DisplayName("deleteFile 对空目录应直接删除")
    void deleteFileShouldDeleteEmptyDirectory() throws IOException {
        Files.createDirectory(tempDir.resolve("empty-dir"));

        strategy.deleteFile(engine, "/empty-dir");

        assertFalse(Files.exists(tempDir.resolve("empty-dir")));
    }

    @Test
    @DisplayName("deleteFile 对不存在的路径应静默成功（删除幂等）")
    void deleteFileShouldSucceedForMissingPath() throws IOException {
        // Files.deleteIfExists 语义：不存在的路径视为已达成"删除"目标
        strategy.deleteFile(engine, "/missing.txt");

        assertFalse(Files.exists(tempDir.resolve("missing.txt")));
        assertTrue(Files.exists(tempDir), "引擎根目录本身不应被误删");
    }

    // ================================================================
    // copyFile —— 同引擎复制的落地方法（无后台复制端点时的走本地 nio）
    // ================================================================

    @Test
    @DisplayName("copyFile 应复制文件内容且保留源文件")
    void copyFileShouldCopyContentAndKeepSource() throws IOException {
        Files.writeString(tempDir.resolve("src.txt"), "source-content");

        strategy.copyFile(engine, "/src.txt", "/dst.txt");

        assertEquals("source-content", Files.readString(tempDir.resolve("dst.txt")));
        assertTrue(Files.exists(tempDir.resolve("src.txt")), "复制不应移除源文件");
    }

    @Test
    @DisplayName("copyFile 应自动创建目标父目录")
    void copyFileShouldCreateTargetParentDirectories() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "payload");

        strategy.copyFile(engine, "/a.txt", "/new/deep/dir/a.txt");

        assertEquals("payload", Files.readString(tempDir.resolve("new/deep/dir/a.txt")));
    }

    @Test
    @DisplayName("copyFile 对已存在的目标文件应覆盖（REPLACE_EXISTING）")
    void copyFileShouldReplaceExistingTarget() throws IOException {
        Files.writeString(tempDir.resolve("src.txt"), "new");
        // 目标文件更长，确保"覆盖"而不是"截断后残留"
        Files.writeString(tempDir.resolve("dst.txt"), "old-content-that-is-longer");

        strategy.copyFile(engine, "/src.txt", "/dst.txt");

        assertEquals("new", Files.readString(tempDir.resolve("dst.txt")),
            "目标文件应被源内容整体覆盖，而非保留旧内容或残留尾部字节");
    }

    @Test
    @DisplayName("copyFile 源文件不存在时应抛出 RuntimeException 并指明源路径")
    void copyFileShouldThrowWhenSourceMissing() {
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> strategy.copyFile(engine, "/missing.txt", "/dst.txt"));

        assertTrue(ex.getMessage().contains("missing.txt"), "异常信息应包含缺失的源路径，实际：" + ex.getMessage());
        assertFalse(Files.exists(tempDir.resolve("dst.txt")), "复制失败时不应留下目标文件");
    }

    // ================================================================
    // moveFile —— 同引擎移动（重命名/跨目录），避免 copy+delete 的组合
    // ================================================================

    @Test
    @DisplayName("moveFile 应移动文件内容并移除源文件")
    void moveFileShouldMoveContentAndRemoveSource() throws IOException {
        Files.writeString(tempDir.resolve("from.txt"), "move-me");

        strategy.moveFile(engine, "/from.txt", "/to.txt");

        assertEquals("move-me", Files.readString(tempDir.resolve("to.txt")));
        assertFalse(Files.exists(tempDir.resolve("from.txt")), "移动完成后源文件应不存在");
    }

    @Test
    @DisplayName("moveFile 应自动创建目标父目录")
    void moveFileShouldCreateTargetParentDirectories() throws IOException {
        Files.writeString(tempDir.resolve("m.txt"), "moved");

        strategy.moveFile(engine, "/m.txt", "/archive/2026/m.txt");

        assertEquals("moved", Files.readString(tempDir.resolve("archive/2026/m.txt")));
        assertFalse(Files.exists(tempDir.resolve("m.txt")));
    }

    @Test
    @DisplayName("moveFile 对已存在的目标文件应覆盖（REPLACE_EXISTING）")
    void moveFileShouldReplaceExistingTarget() throws IOException {
        Files.writeString(tempDir.resolve("src.txt"), "winner");
        Files.writeString(tempDir.resolve("dst.txt"), "loser-content-that-is-longer");

        strategy.moveFile(engine, "/src.txt", "/dst.txt");

        assertEquals("winner", Files.readString(tempDir.resolve("dst.txt")),
            "目标文件应被源内容整体覆盖");
        assertFalse(Files.exists(tempDir.resolve("src.txt")), "移动完成后源文件应不存在");
    }

    @Test
    @DisplayName("moveFile 源文件不存在时应抛出 RuntimeException")
    void moveFileShouldThrowWhenSourceMissing() {
        assertThrows(RuntimeException.class,
            () -> strategy.moveFile(engine, "/nope.txt", "/dst.txt"));

        assertFalse(Files.exists(tempDir.resolve("dst.txt")), "移动失败时不应留下目标文件");
    }
}
