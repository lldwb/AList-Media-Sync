package top.lldwb.alistmediasync.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiUtil 单元测试
 * <p>
 * 测试 API 请求工具类的异常处理路径。
 * 正常路径依赖集成测试（需要真实 AList 服务）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiUtil 单元测试")
class ApiUtilTest {

    private static final RestClient restClient = RestClient.create();
    private static final String BAD_HOST = "https://invalid-host-does-not-exist.local";
    private static final String TOKEN = "test-token";

    @Test
    @DisplayName("get() 请求失败应抛出 RuntimeException")
    void getShouldThrowRuntimeExceptionOnError() {
        assertThrows(RuntimeException.class, () ->
            ApiUtil.get(restClient, BAD_HOST, TOKEN, "/api/me"));
    }

    @Test
    @DisplayName("post() 请求失败应抛出 RuntimeException")
    void postShouldThrowRuntimeExceptionOnError() {
        Map<String, Object> body = Map.of("path", "/test", "password", "");
        assertThrows(RuntimeException.class, () ->
            ApiUtil.post(restClient, BAD_HOST, TOKEN, "/api/fs/list", body));
    }

    @Test
    @DisplayName("postVoid() 请求失败应抛出 RuntimeException")
    void postVoidShouldThrowRuntimeExceptionOnError() {
        Map<String, Object> body = Map.of("path", "/new-dir");
        assertThrows(RuntimeException.class, () ->
            ApiUtil.postVoid(restClient, BAD_HOST, TOKEN, "/api/fs/mkdir", body));
    }

    @Test
    @DisplayName("putStream() 请求失败应抛出 RuntimeException")
    void putStreamShouldThrowRuntimeExceptionOnError() {
        byte[] data = "hello".getBytes();
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            ApiUtil.putStream(restClient, BAD_HOST, TOKEN,
                "/test.mp4", new ByteArrayInputStream(data), data.length, true));
        assertTrue(ex.getMessage().contains("AList") || ex.getCause() != null);
    }

    @Test
    @DisplayName("putForm() 请求失败应抛出 RuntimeException")
    void putFormShouldThrowRuntimeExceptionOnError() {
        var parts = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        parts.add("file", "test-content");
        Map<String, String> headers = Map.of("File-Path", "/remote/test.mp4");
        assertThrows(RuntimeException.class, () ->
            ApiUtil.putForm(restClient, BAD_HOST, TOKEN, parts, headers));
    }
}
