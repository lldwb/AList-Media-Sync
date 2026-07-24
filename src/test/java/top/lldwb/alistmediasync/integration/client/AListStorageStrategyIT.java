package top.lldwb.alistmediasync.integration.client;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.engine.AListStorageStrategy;
import top.lldwb.alistmediasync.sync.dto.sync.FileEntry;

import java.io.ByteArrayInputStream;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AListStorageStrategy WireMock 集成测试
 * <p>
 * 使用 {@link WireMockExtension} 启动 WireMock 服务器，模拟 AList API 行为。
 * 桩映射参考 {@code src/test/resources/wiremock/alist-mappings/}，但此处直接通过
 * WireMock Java API 注册桩响应以实现精确断言控制。
 * </p>
 * <p>
 * 注意：WireMock 3.9.1 默认启用 HTTP/2，而 {@code RestClient} 默认使用的
 * {@code SimpleClientHttpRequestFactory}（基于 HttpURLConnection）在 Windows 上
 * 对 HTTP/2 协商有问题，会导致 RST_STREAM 连接重置。因此通过
 * {@code http2PlainDisabled(true)} 和 {@code http2TlsDisabled(true)} 禁用 HTTP/2。
 * </p>
 * <p>
 * 覆盖 AListStorageStrategy 9 个公共方法：
 * type、listFiles、getFileInfo、downloadFile、uploadFile、createDirectory、
 * deleteFile、copyFile、moveFile、testConnection。
 * 同时验证错误处理（401/404）和请求构造。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("AListStorageStrategy WireMock 集成测试")
class AListStorageStrategyIT {

    // 编程式 WireMock 扩展，禁用 HTTP/2 避免 SimpleClientHttpRequestFactory 的 RST_STREAM
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig()
            .dynamicPort()
            .http2PlainDisabled(true)
            .http2TlsDisabled(true))
        .build();

    private AListStorageStrategy strategy;
    private StorageEngine engine;
    private String baseUrl;

    private int wireMockPort;

    @BeforeEach
    void setUp() {
        wireMockPort = wireMock.getRuntimeInfo().getHttpPort();
        baseUrl = "http://localhost:" + wireMockPort;

        // 创建指向 WireMock 的 RestClient
        RestClient restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();

        strategy = new AListStorageStrategy(restClient);

        engine = new StorageEngine();
        engine.setId(1L);
        engine.setName("测试AList");
        engine.setEngineType(StorageEngine.EngineType.ALIST);
        engine.setBaseUrl(baseUrl);
        engine.setEncryptedToken("test-token");
    }

    @Test
    @DisplayName("type() 应返回 ALIST")
    void shouldReturnType() {
        assertEquals("ALIST", strategy.type());
    }

    // ================================================================
    // testConnection 测试
    // ================================================================

    @Test
    @DisplayName("testConnection — ping 和 me 都成功返回 true")
    void testConnectionShouldReturnTrue() {
        // /ping 返回 pong
        wireMock.stubFor(get("/ping").willReturn(ok("pong")));
        // /api/me 返回成功
        wireMock.stubFor(get("/api/me")
            .withHeader("Authorization", equalTo("test-token"))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":{}}")));

        assertTrue(strategy.testConnection(engine));
    }

    @Test
    @DisplayName("testConnection — ping 阶段失败返回 false")
    void testConnectionShouldReturnFalseWhenPingFails() {
        wireMock.stubFor(get("/ping").willReturn(serverError()));

        assertFalse(strategy.testConnection(engine));
    }

    @Test
    @DisplayName("testConnection — token 校验失败（401）返回 false")
    void testConnectionShouldReturnFalseWhenTokenInvalid() {
        wireMock.stubFor(get("/ping").willReturn(ok("pong")));
        wireMock.stubFor(get("/api/me")
            .withHeader("Authorization", equalTo("test-token"))
            .willReturn(okJson("{\"code\":401,\"message\":\"unauthorized\",\"data\":null}")));

        assertFalse(strategy.testConnection(engine));
    }

    // ================================================================
    // listFiles 测试
    // ================================================================

    @Test
    @DisplayName("listFiles 应正确调用 /api/fs/list 并返回文件列表")
    void listFilesShouldReturnEntries() {
        wireMock.stubFor(post("/api/fs/list")
            .withRequestBody(containing("\"path\":\"/e2e-test\""))
            .willReturn(okJson("""
                {"code":200,"message":"success","data":{"content":[
                  {"name":"test.mp4","path":"/e2e-test/test.mp4","is_dir":false,"size":1024,"modified":"2026-07-23T12:00:00"},
                  {"name":"folder","path":"/e2e-test/folder","is_dir":true,"size":0,"modified":"2026-07-23T12:00:00"}
                ],"total":2}}""")));

        List<FileEntry> result = strategy.listFiles(engine, "/e2e-test", 1, 50);

        assertEquals(2, result.size());
        assertEquals("test.mp4", result.get(0).name());
        assertEquals("/e2e-test/test.mp4", result.get(0).path());
        assertFalse(result.get(0).isDirectory());
        assertEquals(1024, result.get(0).size());
        assertTrue(result.get(1).isDirectory());
    }

    @Test
    @DisplayName("listFiles — 空响应返回空列表")
    void listFilesShouldReturnEmptyForEmptyContent() {
        wireMock.stubFor(post("/api/fs/list")
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":{\"content\":[],\"total\":0}}")));

        List<FileEntry> result = strategy.listFiles(engine, "/empty", 1, 50);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listFiles — 过滤 Synology 虚拟条目")
    void listFilesShouldFilterSynologyVirtualEntries() {
        wireMock.stubFor(post("/api/fs/list")
            .willReturn(okJson("""
                {"code":200,"message":"success","data":{"content":[
                  {"name":"real.flv","path":"/real.flv","is_dir":false,"size":1},
                  {"name":"real.flv@SynoEAStream","path":"/real.flv@SynoEAStream","is_dir":false,"size":0}
                ],"total":2}}""")));

        List<FileEntry> result = strategy.listFiles(engine, "/", 1, 50);

        assertEquals(1, result.size());
        assertEquals("real.flv", result.get(0).name());
    }

    // ================================================================
    // getFileInfo 测试
    // ================================================================

    @Test
    @DisplayName("getFileInfo 应正确调用 /api/fs/get 并返回文件信息")
    void getFileInfoShouldReturnFileEntry() {
        wireMock.stubFor(post("/api/fs/get")
            .withRequestBody(containing("\"path\":\"/e2e-test/test.mp4\""))
            .willReturn(okJson("""
                {"code":200,"message":"success","data":{
                  "name":"test.mp4","path":"/e2e-test/test.mp4","is_dir":false,
                  "size":5242880,"modified":"2026-07-23T12:05:00","raw_url":"http://localhost:%d/download/test.mp4"
                }}""".formatted(wireMockPort))));

        FileEntry result = strategy.getFileInfo(engine, "/e2e-test/test.mp4");

        assertNotNull(result);
        assertEquals("test.mp4", result.name());
        assertEquals("/e2e-test/test.mp4", result.path());
        assertEquals(5242880, result.size());
    }

    @Test
    @DisplayName("getFileInfo — 文件不存在时抛出异常")
    void getFileInfoShouldThrowForNotFound() {
        wireMock.stubFor(post("/api/fs/get")
            .withRequestBody(containing("\"path\":\"/nonexistent\""))
            .willReturn(okJson("{\"code\":404,\"message\":\"object not found\",\"data\":null}")));

        assertThrows(RuntimeException.class,
            () -> strategy.getFileInfo(engine, "/nonexistent"));
    }

    // ================================================================
    // downloadFile 测试
    // ================================================================

    @Test
    @DisplayName("downloadFile 应返回文件输入流")
    void downloadFileShouldReturnInputStream() throws Exception {
        // /api/fs/get 返回 raw_url
        wireMock.stubFor(post("/api/fs/get")
            .withRequestBody(containing("\"path\":\"/e2e-test/test.mp4\""))
            .willReturn(okJson("""
                {"code":200,"message":"success","data":{
                  "raw_url":"http://localhost:%d/download/e2e-test/test.mp4"
                }}""".formatted(wireMockPort))));

        // raw_url 下载端点
        wireMock.stubFor(get("/download/e2e-test/test.mp4")
            .willReturn(ok("fake-file-content")));

        java.io.InputStream result = strategy.downloadFile(engine, "/e2e-test/test.mp4");

        assertNotNull(result);
        byte[] content = result.readAllBytes();
        assertEquals("fake-file-content", new String(content));
    }

    // ================================================================
    // uploadFile 测试
    // ================================================================

    @Test
    @DisplayName("uploadFile 应调用 PUT /api/fs/put 流式上传")
    void uploadFileShouldCallPut() {
        wireMock.stubFor(put("/api/fs/put")
            .withHeader("File-Path", containing("upload"))
            .withHeader("As-Task", equalTo("true"))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        var in = new ByteArrayInputStream(new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> strategy.uploadFile(engine, "/upload/x.mp4", in, 3L));
    }

    @Test
    @DisplayName("uploadFile — 上传失败时抛出异常")
    void uploadFileShouldThrowOnFailure() {
        wireMock.stubFor(put("/api/fs/put")
            .willReturn(okJson("{\"code\":500,\"message\":\"internal error\",\"data\":null}")));

        var in = new ByteArrayInputStream(new byte[]{1});
        assertThrows(RuntimeException.class,
            () -> strategy.uploadFile(engine, "/upload/fail.mp4", in, 1L));
    }

    // ================================================================
    // createDirectory 测试
    // ================================================================

    @Test
    @DisplayName("createDirectory 应调用 POST /api/fs/mkdir")
    void createDirectoryShouldCallMkdir() {
        wireMock.stubFor(post("/api/fs/mkdir")
            .withRequestBody(containing("\"path\":\"/new-dir\""))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        assertDoesNotThrow(() -> strategy.createDirectory(engine, "/new-dir"));
    }

    // ================================================================
    // deleteFile 测试
    // ================================================================

    @Test
    @DisplayName("deleteFile 应调用 POST /api/fs/remove")
    void deleteFileShouldCallRemove() {
        wireMock.stubFor(post("/api/fs/remove")
            .withRequestBody(containing("\"names\""))
            .withRequestBody(containing("\"dir\":\"/movies\""))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        assertDoesNotThrow(() -> strategy.deleteFile(engine, "/movies/a.mp4"));
    }

    @Test
    @DisplayName("deleteFile — 根路径文件删除")
    void deleteFileShouldHandleRootPath() {
        wireMock.stubFor(post("/api/fs/remove")
            .withRequestBody(containing("\"dir\":\"/\""))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        assertDoesNotThrow(() -> strategy.deleteFile(engine, "/rootfile.mp4"));
    }

    // ================================================================
    // copyFile 测试
    // ================================================================

    @Test
    @DisplayName("copyFile 应调用 POST /api/fs/copy 并正确构造请求体")
    void copyFileShouldCallCopy() {
        wireMock.stubFor(post("/api/fs/copy")
            .withRequestBody(containing("\"src_dir\":\"/src\""))
            .withRequestBody(containing("\"dst_dir\":\"/dst\""))
            .withRequestBody(containing("\"names\""))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        assertDoesNotThrow(() -> strategy.copyFile(engine, "/src/a.mp4", "/dst/a.mp4"));
    }

    // ================================================================
    // moveFile 测试
    // ================================================================

    @Test
    @DisplayName("moveFile 应调用 POST /api/fs/move 并正确构造请求体")
    void moveFileShouldCallMove() {
        wireMock.stubFor(post("/api/fs/move")
            .withRequestBody(containing("\"src_dir\":\"/src\""))
            .withRequestBody(containing("\"dst_dir\":\"/dst\""))
            .withRequestBody(containing("\"names\""))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        assertDoesNotThrow(() -> strategy.moveFile(engine, "/src/a.mp4", "/dst/a.mp4"));
    }

    // ================================================================
    // 错误处理测试
    // ================================================================

    @Test
    @DisplayName("401 认证失败时抛出异常")
    void shouldHandle401() {
        wireMock.stubFor(post("/api/fs/list")
            .willReturn(okJson("{\"code\":401,\"message\":\"unauthorized\",\"data\":null}")));

        assertThrows(RuntimeException.class,
            () -> strategy.listFiles(engine, "/", 1, 50));
    }

    @Test
    @DisplayName("404 路径不存在时抛出异常")
    void shouldHandle404() {
        wireMock.stubFor(post("/api/fs/list")
            .willReturn(okJson("{\"code\":404,\"message\":\"path not found\",\"data\":null}")));

        assertThrows(RuntimeException.class,
            () -> strategy.listFiles(engine, "/notexist", 1, 50));
    }

    @Test
    @DisplayName("错误响应码 500 时抛出异常")
    void shouldHandle500() {
        wireMock.stubFor(post("/api/fs/list")
            .willReturn(okJson("{\"code\":500,\"message\":\"internal error\",\"data\":null}")));

        assertThrows(RuntimeException.class,
            () -> strategy.listFiles(engine, "/", 1, 50));
    }

    // ================================================================
    // 请求构造验证
    // ================================================================

    @Test
    @DisplayName("listFiles 请求包含必需字段：path/password/page/per_page/refresh")
    void listFilesRequestShouldContainRequiredFields() {
        wireMock.stubFor(post("/api/fs/list")
            .withRequestBody(matchingJsonPath("$.path"))
            .withRequestBody(matchingJsonPath("$.password"))
            .withRequestBody(matchingJsonPath("$.page"))
            .withRequestBody(matchingJsonPath("$.per_page"))
            .withRequestBody(matchingJsonPath("$.refresh"))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":{\"content\":[],\"total\":0}}")));

        assertDoesNotThrow(() -> strategy.listFiles(engine, "/", 1, 50));
    }

    @Test
    @DisplayName("getFileInfo 请求包含 refresh=true")
    void getFileInfoRequestShouldContainRefresh() {
        wireMock.stubFor(post("/api/fs/get")
            .withRequestBody(matchingJsonPath("$[?(@.refresh == true)]"))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":{\"name\":\"x\",\"path\":\"/x\",\"is_dir\":false,\"size\":0}}")));

        FileEntry result = strategy.getFileInfo(engine, "/x");
        assertNotNull(result);
    }
}