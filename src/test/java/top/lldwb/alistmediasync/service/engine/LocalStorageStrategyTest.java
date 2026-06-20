package top.lldwb.alistmediasync.service.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.lldwb.alistmediasync.dto.sync.DirectoryEntryVO;
import top.lldwb.alistmediasync.dto.sync.FileEntry;
import top.lldwb.alistmediasync.entity.StorageEngine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    @DisplayName("listFiles 应支持分页")
    void listFilesShouldSupportPagination() throws IOException {
        for (int i = 0; i < 5; i++) {
            Files.createFile(tempDir.resolve("file" + i + ".txt"));
        }

        List<FileEntry> page1 = strategy.listFiles(engine, "/", 1, 2);
        assertEquals(2, page1.size());

        List<FileEntry> page2 = strategy.listFiles(engine, "/", 2, 2);
        assertEquals(2, page2.size());
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
}
