package top.lldwb.alistmediasync.common.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import top.lldwb.alistmediasync.common.util.SensitiveDataMasker;
import top.lldwb.alistmediasync.common.util.TraceContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RestClientConfig 日志拦截器测试（T052）
 * <p>
 * 验证外部请求日志：
 * <ul>
 *   <li>不输出 Authorization、Cookie 等敏感头原文（替换为 ***REDACTED***）</li>
 *   <li>不输出 URL 查询串中的敏感参数原值</li>
 *   <li>携带当前 MDC 中的 traceId 上下文</li>
 * </ul>
 * </p>
 */
class RestClientConfigTest {

    private final Logger restClientLogger =
        (Logger) LoggerFactory.getLogger(RestClientConfig.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        originalLevel = restClientLogger.getLevel();
        restClientLogger.setLevel(Level.DEBUG);
        appender.start();
        restClientLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        restClientLogger.detachAppender(appender);
        restClientLogger.setLevel(originalLevel);
        appender.stop();
        TraceContext.clear();
    }

    @Test
    void 拦截器应脱敏Authorization头并保留traceId() throws IOException {
        TraceContext.setTraceId("rest-trace-001");
        var interceptor = new RestClientConfig.LoggingInterceptor();

        HttpRequest request = new TestHttpRequest(
            URI.create("https://alist.example.com/api/fs/list?token=secret-abc&path=/movies"),
            HttpMethod.GET,
            "Bearer alist-secret-token-12345");
        ClientHttpResponse response = new TestClientHttpResponse(200);

        interceptor.intercept(request, new byte[0], (req, body) -> response);

        // 检查日志中不含敏感原值
        String allLogs = appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);
        assertFalse(allLogs.contains("alist-secret-token-12345"), "Authorization 原值不应出现");
        assertFalse(allLogs.contains("secret-abc"), "URL 查询串中的 token 不应出现");
        assertTrue(allLogs.contains(SensitiveDataMasker.REDACTED), "应使用统一脱敏占位");
        assertTrue(allLogs.contains("rest-trace-001"), "应包含当前 traceId");
    }

    /** 简化 HttpRequest 实现 */
    static class TestHttpRequest implements HttpRequest {
        private final URI uri;
        private final HttpMethod method;
        private final HttpHeaders headers = new HttpHeaders();

        TestHttpRequest(URI uri, HttpMethod method, String authorization) {
            this.uri = uri;
            this.method = method;
            if (authorization != null) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
            headers.set("User-Agent", "test");
        }

        @Override public URI getURI() { return uri; }
        @Override public HttpMethod getMethod() { return method; }
        @Override public HttpHeaders getHeaders() { return headers; }
        @Override public java.util.Map<String, Object> getAttributes() { return java.util.Map.of(); }
    }

    /** 简化 ClientHttpResponse 实现 */
    static class TestClientHttpResponse implements ClientHttpResponse {
        private final int status;
        private final HttpHeaders headers = new HttpHeaders();

        TestClientHttpResponse(int status) {
            this.status = status;
            headers.set("Set-Cookie", "session=secret-abc-xyz");
        }

        @Override public HttpStatusCode getStatusCode() { return HttpStatusCode.valueOf(status); }
        @Override public String getStatusText() { return "OK"; }
        @Override public void close() { }
        @Override public InputStream getBody() {
            return new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
        }
        @Override public HttpHeaders getHeaders() { return headers; }
    }
}
