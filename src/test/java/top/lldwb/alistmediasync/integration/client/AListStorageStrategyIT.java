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
import top.lldwb.alistmediasync.storage.dto.FileEntry;

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

        // 静态 WireMockExtension 的服务器在整个测试类生命周期内复用，请求日志会跨用例累积；
        // 清空日志使 verify(exactly(n)) 的计数只反映当前用例发出的请求。
        wireMock.resetRequests();

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

        assertEquals("fake-file-content", new String(result.readAllBytes()));
    }

    @Test
    @DisplayName("downloadFile — 直链返回 HTTP 500 时抛出 RuntimeException，cause 保留状态码与响应体")
    void downloadFileShouldThrowWhenRawUrlReturnsHttpError() {
        wireMock.stubFor(post("/api/fs/get")
            .willReturn(okJson("""
                {"code":200,"message":"success","data":{
                  "raw_url":"http://localhost:%d/download/broken.mp4"
                }}""".formatted(wireMockPort))));
        wireMock.stubFor(get("/download/broken.mp4")
            .willReturn(serverError().withBody("upstream failure")));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> strategy.downloadFile(engine, "/broken.mp4"));

        assertTrue(ex.getMessage().contains("/broken.mp4"), "实际消息：" + ex.getMessage());
        // AList 直链可能指向网关/对象存储，错误码与响应体是排查的唯一线索，必须保留在异常链中
        assertTrue(ex.getCause() instanceof java.io.IOException, "底层 IOException 应作为 cause 保留");
        assertTrue(ex.getCause().getMessage().contains("HTTP 500"), "cause 应含 HTTP 状态码：" + ex.getCause().getMessage());
        assertTrue(ex.getCause().getMessage().contains("upstream failure"), "cause 应含响应体：" + ex.getCause().getMessage());
    }

    @Test
    @DisplayName("downloadFile — /api/fs/get 返回 data=null 时抛出 RuntimeException")
    void downloadFileShouldThrowWhenGetReturnsNoData() {
        wireMock.stubFor(post("/api/fs/get")
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> strategy.downloadFile(engine, "/no-data.mp4"));

        assertTrue(ex.getMessage().contains("/no-data.mp4"), "实际消息：" + ex.getMessage());
    }

    @Test
    @DisplayName("downloadFile — /api/fs/get 未返回 raw_url 时抛出 RuntimeException（不能自行拼接 /d 路径）")
    void downloadFileShouldThrowWhenRawUrlMissing() {
        wireMock.stubFor(post("/api/fs/get")
            .willReturn(okJson("""
                {"code":200,"message":"success","data":{
                  "name":"x.mp4","path":"/x.mp4","is_dir":false,"size":1}}""")));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> strategy.downloadFile(engine, "/x.mp4"));

        assertTrue(ex.getMessage().contains("raw_url"), "实际消息：" + ex.getMessage());
    }

    // ================================================================
    // 分页契约（per_page=50，page 递增直到返回条目 < 50 或为空）
    // ================================================================

    @Test
    @DisplayName("listEntries — 首页满 50 条时按 page 递增翻页并合并，末页不足 50 即停止")
    void listEntriesShouldPaginateUntilShortPage() {
        wireMock.stubFor(post("/api/fs/list")
            .withRequestBody(containing("\"page\":1"))
            .willReturn(okJson(listPageJson("/big", 0, 50))));
        wireMock.stubFor(post("/api/fs/list")
            .withRequestBody(containing("\"page\":2"))
            .willReturn(okJson(listPageJson("/big", 50, 3))));

        List<FileEntry> result = strategy.listEntries(engine, "/big");

        assertEquals(53, result.size(), "应合并第 1 页 50 条与第 2 页 3 条");
        assertEquals("f0.mp4", result.get(0).name());
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo("/api/fs/list"))
            .withRequestBody(containing("\"page\":2")));
        // 第 2 页不足 50 条，循环应在该页终止，不再请求第 3 页
        wireMock.verify(exactly(0), postRequestedFor(urlEqualTo("/api/fs/list"))
            .withRequestBody(containing("\"page\":3")));
    }

    @Test
    @DisplayName("listEntries — 首页为空时直接返回空列表（不继续翻页）")
    void listEntriesShouldStopImmediatelyWhenFirstPageEmpty() {
        wireMock.stubFor(post("/api/fs/list")
            .withRequestBody(containing("\"page\":1"))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":{\"content\":[],\"total\":0}}")));

        List<FileEntry> result = strategy.listEntries(engine, "/empty-tree");

        assertTrue(result.isEmpty());
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo("/api/fs/list")));
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
        strategy.uploadFile(engine, "/upload/x.mp4", in, 3L);

        wireMock.verify(putRequestedFor(urlEqualTo("/api/fs/put"))
            .withHeader("File-Path", equalTo("/upload/x.mp4"))
            .withHeader("As-Task", equalTo("true"))
            .withHeader("Authorization", equalTo("test-token")));
    }

    @Test
    @DisplayName("uploadFile — File-Path 头应对中文与空格做 URL 编码（AList 服务端解析契约）")
    void uploadFileShouldUrlEncodeFilePathHeader() {
        String encodedPath = "/%E4%B8%8A%E4%BC%A0/%E6%88%91%E7%9A%84%20%E6%96%87%E4%BB%B6.mp4";
        // 编码不符合契约时该桩不会命中，WireMock 返回 404，uploadFile 随即抛异常
        wireMock.stubFor(put("/api/fs/put")
            .withHeader("File-Path", equalTo(encodedPath))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        var in = new ByteArrayInputStream(new byte[]{1});
        strategy.uploadFile(engine, "/上传/我的 文件.mp4", in, 1L);

        wireMock.verify(putRequestedFor(urlEqualTo("/api/fs/put"))
            .withHeader("File-Path", equalTo(encodedPath)));
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

        strategy.createDirectory(engine, "/new-dir");

        wireMock.verify(postRequestedFor(urlEqualTo("/api/fs/mkdir"))
            .withRequestBody(equalToJson("{\"path\":\"/new-dir\"}")));
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

        strategy.deleteFile(engine, "/movies/a.mp4");

        wireMock.verify(postRequestedFor(urlEqualTo("/api/fs/remove"))
            .withRequestBody(equalToJson("{\"names\":[\"a.mp4\"],\"dir\":\"/movies\"}")));
    }

    @Test
    @DisplayName("deleteFile — 根路径文件删除")
    void deleteFileShouldHandleRootPath() {
        wireMock.stubFor(post("/api/fs/remove")
            .withRequestBody(containing("\"dir\":\"/\""))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        strategy.deleteFile(engine, "/rootfile.mp4");

        wireMock.verify(postRequestedFor(urlEqualTo("/api/fs/remove"))
            .withRequestBody(equalToJson("{\"names\":[\"rootfile.mp4\"],\"dir\":\"/\"}")));
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
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        strategy.copyFile(engine, "/src/a.mp4", "/dst/a.mp4");

        wireMock.verify(postRequestedFor(urlEqualTo("/api/fs/copy"))
            .withRequestBody(equalToJson("{\"src_dir\":\"/src\",\"dst_dir\":\"/dst\",\"names\":[\"a.mp4\"]}")));
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
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        strategy.moveFile(engine, "/src/a.mp4", "/dst/a.mp4");

        wireMock.verify(postRequestedFor(urlEqualTo("/api/fs/move"))
            .withRequestBody(equalToJson("{\"src_dir\":\"/src\",\"dst_dir\":\"/dst\",\"names\":[\"a.mp4\"]}")));
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
    @DisplayName("403 无权限时抛出异常，消息保留 code 与 message")
    void shouldHandle403() {
        wireMock.stubFor(post("/api/fs/list")
            .willReturn(okJson("{\"code\":403,\"message\":\"permission denied\",\"data\":null}")));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> strategy.listFiles(engine, "/forbidden", 1, 50));

        // AList 业务错误通过消息传递，code/message 必须原样带出便于定位
        assertTrue(ex.getMessage().contains("code=403"), "实际消息：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("permission denied"), "实际消息：" + ex.getMessage());
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

    @Test
    @DisplayName("HTTP 层 401（非业务码）时抛出异常")
    void shouldHandleHttpLevel401() {
        // AList 通常以 HTTP 200 + 业务 code 表达错误，但反向代理/网关可能直接返回 4xx
        wireMock.stubFor(post("/api/fs/list").willReturn(unauthorized()));

        assertThrows(RuntimeException.class,
            () -> strategy.listFiles(engine, "/", 1, 50));
    }

    @Test
    @DisplayName("listFiles — 响应缺少 data.content 时返回空列表（不抛异常）")
    void listFilesShouldReturnEmptyWhenContentMissing() {
        wireMock.stubFor(post("/api/fs/list")
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":{\"total\":0}}")));

        List<FileEntry> result = strategy.listFiles(engine, "/no-content", 1, 50);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getFileInfo — 响应 data 为 null 时返回 null（而非抛异常）")
    void getFileInfoShouldReturnNullWhenDataMissing() {
        wireMock.stubFor(post("/api/fs/get")
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        assertNull(strategy.getFileInfo(engine, "/no-data.mp4"));
    }

    // ================================================================
    // 请求构造验证
    // ================================================================

    @Test
    @DisplayName("listFiles 请求体恰好包含必需的五个字段：path/password/page/per_page/refresh")
    void listFilesRequestShouldContainRequiredFields() {
        wireMock.stubFor(post("/api/fs/list")
            .withRequestBody(equalToJson(
                "{\"path\":\"/media\",\"password\":\"\",\"page\":2,\"per_page\":50,\"refresh\":false}"))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":{\"content\":[],\"total\":0}}")));

        List<FileEntry> result = strategy.listFiles(engine, "/media", 2, 50);

        // 桩仅在请求体完全匹配时命中；未命中则返回 404 并抛异常
        assertTrue(result.isEmpty());
        wireMock.verify(postRequestedFor(urlEqualTo("/api/fs/list"))
            .withRequestBody(equalToJson(
                "{\"path\":\"/media\",\"password\":\"\",\"page\":2,\"per_page\":50,\"refresh\":false}")));
    }

    @Test
    @DisplayName("getFileInfo 请求包含 refresh=true 并返回实际字段值")
    void getFileInfoRequestShouldContainRefresh() {
        wireMock.stubFor(post("/api/fs/get")
            .withRequestBody(equalToJson("{\"path\":\"/x\",\"password\":\"\",\"refresh\":true}"))
            .willReturn(okJson("""
                {"code":200,"message":"success","data":{
                  "name":"x.mp4","path":"/x.mp4","is_dir":false,"size":2048,
                  "modified":"2026-07-23T12:00:00"}}""")));

        FileEntry result = strategy.getFileInfo(engine, "/x");

        assertEquals("x.mp4", result.name());
        assertEquals("/x.mp4", result.path());
        assertEquals(2048, result.size());
        assertFalse(result.isDirectory());
    }

    @Test
    @DisplayName("fs 接口必须携带 Authorization 头（AList 认证契约）")
    void fsRequestsShouldCarryAuthorizationHeader() {
        wireMock.stubFor(post("/api/fs/list")
            .withHeader("Authorization", equalTo("test-token"))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":{\"content\":[],\"total\":0}}")));
        wireMock.stubFor(post("/api/fs/mkdir")
            .withHeader("Authorization", equalTo("test-token"))
            .willReturn(okJson("{\"code\":200,\"message\":\"success\",\"data\":null}")));

        strategy.listFiles(engine, "/", 1, 50);
        strategy.createDirectory(engine, "/new-dir");

        wireMock.verify(postRequestedFor(urlEqualTo("/api/fs/list"))
            .withHeader("Authorization", equalTo("test-token")));
        wireMock.verify(postRequestedFor(urlEqualTo("/api/fs/mkdir"))
            .withHeader("Authorization", equalTo("test-token")));
    }

    // ==================== 测试辅助 ====================

    /** 构造 /api/fs/list 的分页响应：从 startIndex 开始共 count 条文件条目 */
    private static String listPageJson(String dir, int startIndex, int count) {
        StringBuilder sb = new StringBuilder(256)
            .append("{\"code\":200,\"message\":\"success\",\"data\":{\"content\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            int idx = startIndex + i;
            sb.append("{\"name\":\"f").append(idx).append(".mp4\",\"path\":\"").append(dir)
                .append("/f").append(idx)
                .append(".mp4\",\"is_dir\":false,\"size\":10,\"modified\":\"2026-07-23T12:00:00\"}");
        }
        return sb.append("],\"total\":").append(count).append("}}").toString();
    }
}