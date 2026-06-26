package top.lldwb.alistmediasync.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import top.lldwb.alistmediasync.common.util.ApiUtil;
import top.lldwb.alistmediasync.common.util.SensitiveDataMasker;
import top.lldwb.alistmediasync.common.util.TraceContext;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * RestClient 配置
 * <p>
 * Spring Boot 4.x 下 spring-boot-starter-webmvc 不会自动注册
 * RestClient.Builder Bean，需手动提供。
 * 同时配置连接超时、读取超时以及请求/响应日志拦截器。
 * </p>
 * <p>
 * 日志拦截器在记录请求/响应时：
 * <ul>
 *   <li>从 MDC 读取当前 traceId，附加到日志上下文以串联外部 AList 调用；</li>
 *   <li>对 Authorization、Cookie、X-API-Key 等敏感请求头统一脱敏为 {@code ***REDACTED***}；</li>
 *   <li>对 URL 查询串中的敏感参数统一脱敏（与 {@link SensitiveDataMasker} 行为一致）。</li>
 * </ul>
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Configuration
public class RestClientConfig {

    /** 连接超时时间（秒） */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    /** 读取超时时间（秒） */
    private static final int READ_TIMEOUT_SECONDS = 30;

    /** 请求头脱敏白名单（小写） */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
        "authorization", "cookie", "set-cookie",
        "x-auth-token", "x-api-key", "proxy-authorization"
    );

    /**
     * 配置好的 RestClient 实例（含超时和日志拦截器）
     */
    @Bean
    public RestClient restClient() {
        return RestClient.builder()
            .requestFactory(clientHttpRequestFactory())
            .requestInterceptor(new LoggingInterceptor())
            .build();
    }

    /**
     * RestClient.Builder Bean（向后兼容）
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * HTTP 连接工厂：配置连接超时和读取超时
     */
    private SimpleClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS));
        factory.setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS));
        return factory;
    }

    /**
     * HTTP 请求/响应日志拦截器
     */
    static class LoggingInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            String traceId = TraceContext.getTraceId();
            logRequest(request, traceId);
            long startTime = System.currentTimeMillis();
            try {
                ClientHttpResponse response = execution.execute(request, body);
                logResponse(request, response, startTime, traceId);
                return response;
            } catch (IOException e) {
                log.error("HTTP 请求失败：{} {} — 耗时={}ms, traceId={}, 原因：{}",
                    request.getMethod(), SensitiveDataMasker.maskUrl(String.valueOf(request.getURI())),
                    System.currentTimeMillis() - startTime, traceId, e.getMessage());
                throw e;
            }
        }

        /**
         * 记录 HTTP 请求信息（敏感头统一脱敏；查询串敏感参数脱敏）
         */
        private void logRequest(HttpRequest request, String traceId) {
            Map<String, List<String>> sanitizedHeaders = new LinkedHashMap<>();
            request.getHeaders().forEach((key, values) -> {
                if (SENSITIVE_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                    sanitizedHeaders.put(key, List.of(SensitiveDataMasker.REDACTED));
                } else {
                    sanitizedHeaders.put(key, values);
                }
            });
            log.debug("HTTP 请求：{} {} — traceId={}, headers={}",
                request.getMethod(),
                SensitiveDataMasker.maskUrl(String.valueOf(request.getURI())),
                traceId,
                sanitizedHeaders);
        }

        /**
         * 记录 HTTP 响应信息（敏感响应头也脱敏）
         */
        private void logResponse(HttpRequest request, ClientHttpResponse response,
                                 long startTime, String traceId) throws IOException {
            Map<String, List<String>> sanitizedHeaders = new LinkedHashMap<>();
            response.getHeaders().forEach((key, values) -> {
                if (SENSITIVE_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                    sanitizedHeaders.put(key, List.of(SensitiveDataMasker.REDACTED));
                } else {
                    sanitizedHeaders.put(key, values);
                }
            });
            log.debug("HTTP 响应：{} {} — 状态码={}, traceId={}, headers={}, 耗时={}ms",
                request.getMethod(),
                SensitiveDataMasker.maskUrl(String.valueOf(request.getURI())),
                response.getStatusCode(),
                traceId,
                sanitizedHeaders,
                System.currentTimeMillis() - startTime);
        }
    }
}
