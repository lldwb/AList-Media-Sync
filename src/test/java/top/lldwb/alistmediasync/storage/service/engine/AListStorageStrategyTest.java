package top.lldwb.alistmediasync.storage.service.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;
import top.lldwb.alistmediasync.common.util.ApiUtil;
import top.lldwb.alistmediasync.sync.dto.sync.DirectoryEntryVO;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AListStorageStrategy 单元测试
 * <p>
 * 通过 MockedStatic<ApiUtil> 拦截静态 API 调用，验证策略方法的逻辑正确性。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AListStorageStrategy 单元测试")
class AListStorageStrategyTest {

    @Mock
    private RestClient restClient;

    @InjectMocks
    private AListStorageStrategy strategy;

    private StorageEngine engine;

    @BeforeEach
    void setUp() {
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
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.get(any(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("连接失败"));
            boolean result = strategy.testConnection(engine);
            assertFalse(result);
        }
    }

    @Test
    @DisplayName("testConnection 成功时应返回 true")
    @SuppressWarnings("unchecked")
    void testConnectionShouldReturnTrueOnSuccess() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.get(any(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("code", 200));
            boolean result = strategy.testConnection(engine);
            assertTrue(result);
        }
    }

    @Test
    @DisplayName("listDirectories 应对 API 异常返回空列表")
    void listDirectoriesShouldReturnEmptyForApiError() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("API 异常"));
            List<DirectoryEntryVO> result = strategy.listDirectories(engine, "/");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("listFiles 应正确调用 API 并返回文件列表")
    @SuppressWarnings("unchecked")
    void listFilesShouldCallApiAndReturnEntries() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Map.of(
                    "data", Map.of("content", List.of(
                        Map.of("name", "test.mp4", "path", "/test.mp4", "is_dir", false, "size", 1024, "modified", "2024-01-01T00:00:00")
                    ))
                ));

            List<top.lldwb.alistmediasync.sync.dto.sync.FileEntry> result = strategy.listFiles(engine, "/", 1, 50);
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("test.mp4", result.get(0).name());
        }
    }

    @Test
    @DisplayName("downloadFile 应抛出异常当 raw_url 缺失")
    void downloadFileShouldThrowWhenRawUrlMissing() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            // /api/fs/get 返回元数据但没有 raw_url
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/get"), any()))
                .thenReturn(Map.of("data", Map.of("name", "x.flv", "size", 1024)));

            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> strategy.downloadFile(engine, "/x.flv"));
            assertTrue(ex.getMessage().contains("raw_url 缺失"));
            // 关键：不应再走 postForBytes 拿"字节流"
            apiUtil.verify(() -> ApiUtil.postForBytes(any(), anyString(), anyString(), anyString(), any()),
                never());
        }
    }

    @Test
    @DisplayName("createDirectory 应正确调用 API")
    void createDirectoryShouldCallApi() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            // postVoid 是 void 方法，MockedStatic 默认不执行任何操作

            assertDoesNotThrow(() -> strategy.createDirectory(engine, "/new-dir"));
            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/mkdir"), any()));
        }
    }

    @Test
    @DisplayName("deleteFile 应正确调用 API")
    void deleteFileShouldCallApi() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            // postVoid 是 void 方法，MockedStatic 默认不执行任何操作

            assertDoesNotThrow(() -> strategy.deleteFile(engine, "/dir/file.txt"));
            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/remove"), any()));
        }
    }
}
