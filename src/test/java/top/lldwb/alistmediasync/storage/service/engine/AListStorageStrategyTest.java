package top.lldwb.alistmediasync.storage.service.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import top.lldwb.alistmediasync.common.util.ApiUtil;
import top.lldwb.alistmediasync.storage.dto.DirectoryEntryVO;
import top.lldwb.alistmediasync.storage.dto.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
            strategy.createDirectory(engine, "/new-dir");
            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/mkdir"), eq(Map.of("path", "/new-dir"))));
        }
    }

    @Test
    @DisplayName("deleteFile 应正确拆分 dir + names")
    void deleteFileShouldSplitDirAndName() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            strategy.deleteFile(engine, "/movies/a.mp4");
            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/remove"),
                eq(Map.of("names", List.of("a.mp4"), "dir", "/movies"))));
        }
    }

    @Test
    @DisplayName("deleteFile 对根目录下的文件应将 dir 置为 /")
    void deleteFileShouldUseRootDirForRootLevelFile() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            strategy.deleteFile(engine, "/a.mp4");
            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/remove"),
                eq(Map.of("names", List.of("a.mp4"), "dir", "/"))));
        }
    }

    @Test
    @DisplayName("uploadFile 应走流式 PUT /api/fs/put（asTask=true）")
    void uploadFileShouldCallPutStream() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            var in = new ByteArrayInputStream(new byte[]{1, 2, 3});
            strategy.uploadFile(engine, "/upload/x.mp4", in, 3L);
            apiUtil.verify(() -> ApiUtil.putStream(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/upload/x.mp4"), eq(in), eq(3L), eq(true)));
        }
    }

    // ================================================================
    // 请求构造契约（对照 md/alist/fs/ 下的接口定义）
    // ================================================================

    @Test
    @DisplayName("listFiles 请求体必须含 path/password/page/per_page/refresh 五个字段（缺少任一 AList 返回 400）")
    void listFilesShouldSendAllRequiredFields() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/list"), any()))
                .thenReturn(listResponseOf());

            strategy.listFiles(engine, "/media", 3, 50);

            apiUtil.verify(() -> ApiUtil.post(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/list"),
                eq(Map.of("path", "/media", "password", "", "page", 3, "per_page", 50, "refresh", false))));
        }
    }

    @Test
    @DisplayName("copyFile 源位于根目录时 src_dir 应为 /")
    void copyFileShouldUseRootAsSrcDir() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            strategy.copyFile(engine, "/a.mp4", "/dst/a.mp4");

            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/copy"),
                eq(Map.of("src_dir", "/", "dst_dir", "/dst", "names", List.of("a.mp4")))));
        }
    }

    @Test
    @DisplayName("copyFile 复制到根目录时 dst_dir 应为 /（剥离源文件名后为空）")
    void copyFileShouldUseRootAsDstDir() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            strategy.copyFile(engine, "/src/a.mp4", "/a.mp4");

            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/copy"),
                eq(Map.of("src_dir", "/src", "dst_dir", "/", "names", List.of("a.mp4")))));
        }
    }

    @Test
    @DisplayName("copyFile 目标路径不以源文件名结尾时 dst_dir 保持原样（不剥离文件名）")
    void copyFileShouldKeepDstDirWhenTargetNameDiffers() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            strategy.copyFile(engine, "/src/a.mp4", "/dst/renamed.mp4");

            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/copy"),
                eq(Map.of("src_dir", "/src", "dst_dir", "/dst/renamed.mp4", "names", List.of("a.mp4")))));
        }
    }

    @Test
    @DisplayName("copyFile 应正确构造 src_dir / dst_dir / names")
    void copyFileShouldBuildCorrectBody() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            strategy.copyFile(engine, "/src/a.mp4", "/dst/a.mp4");
            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/copy"),
                eq(Map.of("src_dir", "/src", "dst_dir", "/dst", "names", List.of("a.mp4")))));
        }
    }

    @Test
    @DisplayName("moveFile 应调用 /api/fs/move 并构造 src_dir / dst_dir / names")
    void moveFileShouldCallMoveWithCorrectBody() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            strategy.moveFile(engine, "/src/a.mp4", "/dst/a.mp4");

            apiUtil.verify(() -> ApiUtil.postVoid(eq(restClient), eq("https://alist.example.com"),
                eq("test-token"), eq("/api/fs/move"),
                eq(Map.of("src_dir", "/src", "dst_dir", "/dst", "names", List.of("a.mp4")))));
        }
    }

    // ================================================================
    // 分页契约（per_page=50，page 递增，直到返回条目 < 50 或为空）
    // ================================================================

    @Test
    @DisplayName("listEntries 首页满页（50 条）时应继续请求下一页并合并结果")
    void listEntriesShouldContinuePagingWhenPageIsFull() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/list"),
                    argThat(body -> Integer.valueOf(1).equals(body.get("page")))))
                .thenReturn(listResponse("p1_", 50));
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/list"),
                    argThat(body -> Integer.valueOf(2).equals(body.get("page")))))
                .thenReturn(listResponse("p2_", 7));

            List<FileEntry> result = strategy.listEntries(engine, "/big");

            assertEquals(57, result.size(), "首页 50 条 + 次页 7 条应被合并");
            apiUtil.verify(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/list"),
                argThat(body -> Integer.valueOf(2).equals(body.get("page")))));
        }
    }

    @Test
    @DisplayName("listEntries 首页不满页时应停止翻页（结果不包含后续页数据）")
    void listEntriesShouldStopPagingWhenPageNotFull() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/list"), any()))
                .thenReturn(listResponse("only_", 7));

            List<FileEntry> result = strategy.listEntries(engine, "/small");

            // 若未正确终止翻页，循环会继续取回同一份响应，条数将翻倍
            assertEquals(7, result.size());
        }
    }

    @Test
    @DisplayName("listEntries 对 API 异常应返回空列表（不抛出，避免中断同步链路）")
    void listEntriesShouldReturnEmptyForApiError() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/list"), any()))
                .thenThrow(new RuntimeException("API 异常"));

            assertTrue(strategy.listEntries(engine, "/").isEmpty());
        }
    }

    // ================================================================
    // listDirectories：仅目录 + 去重 + hasChildren
    // ================================================================

    @Test
    @DisplayName("listDirectories 应过滤文件、按 path 去重，并依据子条目计算 hasChildren")
    void listDirectoriesShouldFilterDeduplicateAndComputeHasChildren() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/list"),
                    argThat(body -> "/".equals(body.get("path")))))
                .thenReturn(listResponseOf(
                    fileEntry("movies", "/movies", true),
                    fileEntry("movies", "/movies", true),   // 同路径重复条目
                    fileEntry("empty", "/empty", true),
                    fileEntry("a.mp4", "/a.mp4", false)));
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/list"),
                    argThat(body -> "/movies".equals(body.get("path")))))
                .thenReturn(listResponseOf(fileEntry("sub", "/movies/sub", true)));
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/list"),
                    argThat(body -> "/empty".equals(body.get("path")))))
                .thenReturn(listResponseOf(fileEntry("x.mp4", "/empty/x.mp4", false)));

            List<DirectoryEntryVO> result = strategy.listDirectories(engine, "/");

            assertEquals(2, result.size(), "文件应被过滤、重复目录应按 path 去重");
            assertEquals("movies", result.get(0).name());
            assertEquals("/movies", result.get(0).path());
            assertTrue(result.get(0).hasChildren(), "/movies 下存在子目录 sub");
            assertEquals("empty", result.get(1).name());
            assertFalse(result.get(1).hasChildren(), "/empty 下只有文件，不含子目录");
        }
    }

    // ================================================================
    // 响应解析：data 缺失、virtual_path 回退、modified 时间格式
    // ================================================================

    @Test
    @DisplayName("getFileInfo 响应缺少 data 时应返回 null（而非抛异常）")
    void getFileInfoShouldReturnNullWhenDataMissing() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/get"), any()))
                .thenReturn(Map.of("code", 200, "message", "success"));

            assertNull(strategy.getFileInfo(engine, "/x.mp4"));
        }
    }

    @Test
    @DisplayName("getFileInfo 在 AList 返回 path 为空时应回退到 virtual_path（挂载存储契约）")
    void getFileInfoShouldFallbackToVirtualPath() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/get"), any()))
                .thenReturn(apiResponse(Map.of(
                    "name", "movie.mp4", "path", "", "virtual_path", "/mount/movie.mp4",
                    "is_dir", false, "size", 123L)));

            FileEntry result = strategy.getFileInfo(engine, "/mount/movie.mp4");

            assertEquals("/mount/movie.mp4", result.path(), "path 为空时应回退到 virtual_path");
            assertEquals("movie.mp4", result.name());
            assertEquals(123L, result.size());
        }
    }

    @Test
    @DisplayName("getFileInfo 在 name/path 均缺失时应返回 null")
    void getFileInfoShouldReturnNullWhenNameAndPathMissing() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/get"), any()))
                .thenReturn(apiResponse(Map.of("is_dir", false, "size", 1L)));

            assertNull(strategy.getFileInfo(engine, "/x.mp4"));
        }
    }

    @Test
    @DisplayName("getFileInfo 在 modified 为 epoch 秒时应换算为对应时间点")
    void getFileInfoShouldParseEpochSecondsModified() {
        long epochSeconds = 1_800_000_000L;
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/get"), any()))
                .thenReturn(apiResponse(Map.of(
                    "name", "x.mp4", "path", "/x.mp4", "is_dir", false, "size", 1L,
                    "modified", epochSeconds)));

            FileEntry result = strategy.getFileInfo(engine, "/x.mp4");

            assertEquals(LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault()),
                result.modifiedTime());
        }
    }

    // ================================================================
    // downloadFile：raw_url 缺失的两种形态
    // ================================================================

    @Test
    @DisplayName("downloadFile 在 /api/fs/get 响应缺少 data 时应抛出 RuntimeException 且消息含路径")
    void downloadFileShouldThrowWhenGetReturnsNoData() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/get"), any()))
                .thenReturn(Map.of("code", 200, "message", "success"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> strategy.downloadFile(engine, "/x.mp4"));

            assertTrue(ex.getMessage().contains("/x.mp4"), "实际消息：" + ex.getMessage());
        }
    }

    @Test
    @DisplayName("downloadFile 在 raw_url 为空时应抛出 RuntimeException（不能自行拼接 /d 路径，缺少 sign）")
    void downloadFileShouldThrowWhenRawUrlMissing() {
        try (MockedStatic<ApiUtil> apiUtil = mockStatic(ApiUtil.class)) {
            apiUtil.when(() -> ApiUtil.post(any(), anyString(), anyString(), eq("/api/fs/get"), any()))
                .thenReturn(apiResponse(Map.of(
                    "name", "x.mp4", "path", "/x.mp4", "is_dir", false, "size", 1L)));

            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> strategy.downloadFile(engine, "/x.mp4"));

            assertTrue(ex.getMessage().contains("raw_url"), "实际消息：" + ex.getMessage());
        }
    }

    // ==================== 测试辅助 ====================

    /** 构造带 data 的 AList 成功响应 */
    private static Map<String, Object> apiResponse(Object data) {
        return Map.of("code", 200, "message", "success", "data", data);
    }

    /** 构造 /api/fs/list 响应，content 为给定的条目集合 */
    @SafeVarargs
    private static Map<String, Object> listResponseOf(Map<String, Object>... entries) {
        return apiResponse(Map.of("content", List.of(entries), "total", entries.length));
    }

    /** 构造 AList 文件/目录条目 */
    private static Map<String, Object> fileEntry(String name, String path, boolean isDirectory) {
        return Map.of(
            "name", name,
            "path", path,
            "is_dir", isDirectory,
            "size", isDirectory ? 0L : 1L,
            "modified", "2026-07-23T12:00:00");
    }

    /** 构造含 count 条文件条目的 /api/fs/list 响应 */
    private static Map<String, Object> listResponse(String namePrefix, int count) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(fileEntry(namePrefix + i + ".mp4", "/big/" + namePrefix + i + ".mp4", false));
        }
        return apiResponse(Map.of("content", entries, "total", count));
    }
}
