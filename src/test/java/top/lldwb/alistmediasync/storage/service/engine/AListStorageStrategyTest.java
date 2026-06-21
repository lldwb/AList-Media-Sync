package top.lldwb.alistmediasync.storage.service.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import top.lldwb.alistmediasync.common.util.AListApiClient;
import top.lldwb.alistmediasync.sync.dto.sync.DirectoryEntryVO;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AListStorageStrategy 单元测试
 * <p>
 * 使用 Mock AListApiClient 验证 AList API 调用逻辑。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AListStorageStrategy 单元测试")
class AListStorageStrategyTest {

    @Mock
    private AListApiClient apiClient;

    private AListStorageStrategy strategy;

    private StorageEngine engine;

    @BeforeEach
    void setUp() {
        strategy = new AListStorageStrategy(apiClient);
        engine = new StorageEngine();
        engine.setId(1L);
        engine.setName("测试AList");
        engine.setEngineType(StorageEngine.EngineType.ALIST);
        engine.setBaseUrl("https://alist.example.com");
        engine.setEncryptedToken("test-token");
    }

    @Test
    @DisplayName("type() 应返回 ALIST")
    void typeShouldReturnALIST() {
        assertEquals("ALIST", strategy.type());
    }

    @Test
    @DisplayName("testConnection 失败时应返回 false")
    void testConnectionShouldReturnFalseOnError() {
        when(apiClient.get(anyString(), anyString(), eq("/api/me")))
            .thenThrow(new RuntimeException("连接超时"));
        boolean result = strategy.testConnection(engine);
        assertFalse(result);
    }

    @Test
    @DisplayName("testConnection 成功时应返回 true")
    void testConnectionShouldReturnTrueOnSuccess() {
        when(apiClient.get(anyString(), anyString(), eq("/api/me")))
            .thenReturn(Map.of("code", 200));
        boolean result = strategy.testConnection(engine);
        assertTrue(result);
    }

    @Test
    @DisplayName("listDirectories 应对 API 异常返回空列表")
    void listDirectoriesShouldReturnEmptyForApiError() {
        when(apiClient.post(anyString(), anyString(), eq("/api/fs/list"), anyMap()))
            .thenThrow(new RuntimeException("网络错误"));
        List<DirectoryEntryVO> result = strategy.listDirectories(engine, "/");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listFiles 应正确调用 API 并返回文件列表")
    void listFilesShouldCallApiAndReturnEntries() {
        Map<String, Object> mockResponse = Map.of(
            "data", Map.of("content", List.of(
                Map.of("name", "test.mp4", "path", "/test.mp4", "is_dir", false, "size", 1024, "modified", "2024-01-01T00:00:00")
            ))
        );
        when(apiClient.post(eq("https://alist.example.com"), eq("test-token"), eq("/api/fs/list"), anyMap()))
            .thenReturn(mockResponse);

        List<top.lldwb.alistmediasync.sync.dto.sync.FileEntry> result = strategy.listFiles(engine, "/", 1, 50);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test.mp4", result.get(0).name());
    }

    @Test
    @DisplayName("createDirectory 应正确调用 API")
    void createDirectoryShouldCallApi() {
        strategy.createDirectory(engine, "/new-dir");
        verify(apiClient).postVoid(eq("https://alist.example.com"), eq("test-token"),
            eq("/api/fs/mkdir"), argThat(body -> "/new-dir".equals(body.get("path"))));
    }

    @Test
    @DisplayName("deleteFile 应正确调用 API")
    void deleteFileShouldCallApi() {
        strategy.deleteFile(engine, "/dir/file.txt");
        verify(apiClient).postVoid(eq("https://alist.example.com"), eq("test-token"),
            eq("/api/fs/remove"), argThat(body ->
                "/dir/".equals(body.get("dir")) && body.get("names") instanceof List));
    }
}
