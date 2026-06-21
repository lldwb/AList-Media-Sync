package top.lldwb.alistmediasync.storage.service.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;
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
 * 使用 Mock RestClient 验证 AList API 调用逻辑。
 * ApiUtil 为静态方法，无法直接 Mock，通过 @Mock RestClient 控制底层 HTTP 调用行为。
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
        // 不 Mock restClient，直接调用会导致 NPE 或连接错误，都被策略层捕获并返回 false
        boolean result = strategy.testConnection(engine);
        assertFalse(result);
    }

    @Test
    @DisplayName("testConnection 成功时应返回 true")
    void testConnectionShouldReturnTrueOnSuccess() {
        // 由于 ApiUtil 是静态方法，需要 Mock RestClient 的链式调用
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString())).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.header(anyString(), anyString())).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of("code", 200));

        boolean result = strategy.testConnection(engine);
        assertTrue(result);
    }

    @Test
    @DisplayName("listDirectories 应对 API 异常返回空列表")
    void listDirectoriesShouldReturnEmptyForApiError() {
        // 不 Mock restClient，ApiUtil 内部会抛出异常，策略层 catch 后返回空列表
        List<DirectoryEntryVO> result = strategy.listDirectories(engine, "/");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listFiles 应正确调用 API 并返回文件列表")
    @SuppressWarnings("unchecked")
    void listFilesShouldCallApiAndReturnEntries() {
        RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.header(anyString(), anyString())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.body(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
            "data", Map.of("content", List.of(
                Map.of("name", "test.mp4", "path", "/test.mp4", "is_dir", false, "size", 1024, "modified", "2024-01-01T00:00:00")
            ))
        ));

        List<top.lldwb.alistmediasync.sync.dto.sync.FileEntry> result = strategy.listFiles(engine, "/", 1, 50);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test.mp4", result.get(0).name());
    }

    @Test
    @DisplayName("createDirectory 应正确调用 API")
    @SuppressWarnings("unchecked")
    void createDirectoryShouldCallApi() {
        RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.header(anyString(), anyString())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.body(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.retrieve()).thenReturn(responseSpec);

        strategy.createDirectory(engine, "/new-dir");
        verify(restClient).post();
    }

    @Test
    @DisplayName("deleteFile 应正确调用 API")
    @SuppressWarnings("unchecked")
    void deleteFileShouldCallApi() {
        RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.header(anyString(), anyString())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.body(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.retrieve()).thenReturn(responseSpec);

        strategy.deleteFile(engine, "/dir/file.txt");
        verify(restClient).post();
    }
}
