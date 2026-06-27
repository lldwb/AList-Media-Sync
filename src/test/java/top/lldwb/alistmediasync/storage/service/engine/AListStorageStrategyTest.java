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
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import top.lldwb.alistmediasync.common.util.ApiUtil;
import top.lldwb.alistmediasync.sync.dto.sync.DirectoryEntryVO;
import top.lldwb.alistmediasync.sync.dto.sync.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AListStorageStrategy 单元测试
 * <p>
 * 通过 {@link MockedStatic} 拦截 {@link ApiUtil} 静态方法，验证策略层
 * 是否按对接文档（{@code md/alist/}）构造请求并正确解析响应。
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
    @DisplayName("testConnection — ping 阶段失败应返回 false")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testConnectionShouldReturnFalseWhenPingFails() {
        // 模拟 RestClient 调用链：get() → uri() → retrieve() → toBodilessEntity() 抛异常
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("connection refused"));

        assertFalse(strategy.testConnection(engine));
    }

    @Test
    @DisplayName("testConnection — token 阶段失败应返回 false")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testConnectionShouldReturnFalseWhenTokenInvalid() {
        // ping 阶段成功
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        // token 阶段失败
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.get(any(), anyString(), eq("test-token"), eq("/api/me")))
                .thenThrow(new RuntimeException("code=401, message=unauthorized"));
            assertFalse(strategy.testConnection(engine));
        }
    }

    @Test
    @DisplayName("testConnection — ping + me 都成功应返回 true")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testConnectionShouldReturnTrueOnSuccess() {
        // ping 阶段成功
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        // token 阶段成功
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.get(any(), anyString(), eq("test-token"), eq("/api/me")))
                .thenReturn(Map.of("code", 200));
            assertTrue(strategy.testConnection(engine));
        }
    }

    @Test
    @DisplayName("listDirectories 对 API 异常应返回空列表（不抛出）")
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
    @DisplayName("listFiles 应正确调用 API 并解析 content")
    void listFilesShouldCallApiAndReturnEntries() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/list"), any()))
                .thenReturn(Map.of(
                    "code", 200,
                    "data", Map.of("content", List.of(
                        Map.of("name", "test.mp4", "path", "/test.mp4",
                               "is_dir", false, "size", 1024, "modified", "2024-01-01T00:00:00")
                    ))
                ));

            List<FileEntry> result = strategy.listFiles(engine, "/", 1, 50);
            assertEquals(1, result.size());
            assertEquals("test.mp4", result.get(0).name());
        }
    }

    @Test
    @DisplayName("listFiles 应过滤 Synology 虚拟流条目")
    void listFilesShouldDropSynologyVirtualEntries() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/list"), any()))
                .thenReturn(Map.of(
                    "code", 200,
                    "data", Map.of("content", List.of(
                        Map.of("name", "real.flv", "path", "/real.flv", "is_dir", false, "size", 1),
                        Map.of("name", "real.flv@SynoEAStream", "path", "/real.flv@SynoEAStream", "is_dir", false, "size", 0),
                        Map.of("name", "SYNOINDEX_MEDIA_INFO", "path", "/SYNOINDEX_MEDIA_INFO", "is_dir", false, "size", 0)
                    ))
                ));
            List<FileEntry> result = strategy.listFiles(engine, "/", 1, 50);
            assertEquals(1, result.size());
            assertEquals("real.flv", result.get(0).name());
        }
    }

    @Test
    @DisplayName("createDirectory 应调用 /api/fs/mkdir")
    void createDirectoryShouldCallMkdir() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            assertDoesNotThrow(() -> strategy.createDirectory(engine, "/new-dir"));
            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/mkdir"), eq(Map.of("path", "/new-dir"))));
        }
    }

    @Test
    @DisplayName("deleteFile 应正确拆分 dir + names")
    void deleteFileShouldSplitDirAndName() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            assertDoesNotThrow(() -> strategy.deleteFile(engine, "/movies/a.mp4"));
            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/remove"),
                eq(Map.of("names", List.of("a.mp4"), "dir", "/movies"))));
        }
    }

    @Test
    @DisplayName("uploadFile 应走流式 PUT /api/fs/put")
    void uploadFileShouldCallPutStream() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            var in = new java.io.ByteArrayInputStream(new byte[]{1, 2, 3});
            assertDoesNotThrow(() -> strategy.uploadFile(engine, "/upload/x.mp4", in, 3L));
            apiUtil.verify(() -> ApiUtil.putStream(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/upload/x.mp4"), eq(in), eq(3L), eq(true)));
        }
    }

    @Test
    @DisplayName("copyFile 应正确构造 src_dir / dst_dir / names")
    void copyFileShouldBuildCorrectBody() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            assertDoesNotThrow(() -> strategy.copyFile(engine, "/src/a.mp4", "/dst/a.mp4"));
            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/copy"),
                eq(Map.of("src_dir", "/src", "dst_dir", "/dst", "names", List.of("a.mp4")))));
        }
    }
}
