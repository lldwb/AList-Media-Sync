package top.lldwb.alistmediasync.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AListApiClient 单元测试
 * <p>
 * 测试 API 请求封装工具类的异常处理路径。
 * 正常路径依赖集成测试（需要真实 AList 服务）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AListApiClient 单元测试")
class AListApiClientTest {

    private AListApiClient apiClient;
    private static final String BASE_URL = "https://alist.example.com";
    private static final String TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        // 使用 RestClient.create() 创建未配置的实例（会因 DNS 解析失败触发异常）
        apiClient = new AListApiClient(org.springframework.web.client.RestClient.create());
    }

    @Test
    @DisplayName("get() 请求失败应抛出 RuntimeException")
    void getShouldThrowRuntimeExceptionOnError() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            apiClient.get("https://invalid-host-does-not-exist.local", TOKEN, "/api/me"));
        assertTrue(ex.getMessage().contains("AList API GET 请求失败"));
    }

    @Test
    @DisplayName("post() 请求失败应抛出 RuntimeException")
    void postShouldThrowRuntimeExceptionOnError() {
        Map<String, Object> body = Map.of("path", "/test", "password", "");
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            apiClient.post("https://invalid-host-does-not-exist.local", TOKEN, "/api/fs/list", body));
        assertTrue(ex.getMessage().contains("AList API POST 请求失败"));
    }

    @Test
    @DisplayName("postForBytes() 请求失败应抛出 RuntimeException")
    void postForBytesShouldThrowRuntimeExceptionOnError() {
        Map<String, Object> body = Map.of("path", "/test.mp4", "password", "");
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            apiClient.postForBytes("https://invalid-host-does-not-exist.local", TOKEN, "/api/fs/get", body));
        assertTrue(ex.getMessage().contains("AList API 文件下载请求失败"));
    }

    @Test
    @DisplayName("postVoid() 请求失败应抛出 RuntimeException")
    void postVoidShouldThrowRuntimeExceptionOnError() {
        Map<String, Object> body = Map.of("path", "/new-dir");
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            apiClient.postVoid("https://invalid-host-does-not-exist.local", TOKEN, "/api/fs/mkdir", body));
        assertTrue(ex.getMessage().contains("AList API POST 请求失败"));
    }

    @Test
    @DisplayName("putMultipart() 请求失败应抛出 RuntimeException")
    void putMultipartShouldThrowRuntimeExceptionOnError() {
        var parts = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        parts.add("file", "test-content");
        Map<String, String> headers = Map.of("File-Path", "/remote/test.mp4");
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            apiClient.putMultipart("https://invalid-host-does-not-exist.local", TOKEN, "/api/fs/put", parts, headers));
        assertTrue(ex.getMessage().contains("AList API 文件上传请求失败"));
    }
}
