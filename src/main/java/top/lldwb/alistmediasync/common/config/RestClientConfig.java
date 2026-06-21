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
import top.lldwb.alistmediasync.common.util.AListApiClient;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RestClient 配置
 * <p>
 * Spring Boot 4.x 下 spring-boot-starter-webmvc 不会自动注册
 * RestClient.Builder Bean，需手动提供。
 * 同时配置连接超时、读取超时以及请求/响应日志拦截器。
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

    /**
     * 配置好的 RestClient 实例（含超时和日志拦截器）
     * <p>
     * 供 {@link AListApiClient} 使用，所有通过此 RestClient 的 HTTP 调用
     * 都会自动记录请求和响应日志。
     * </p>
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
     * <p>
     * 保留此 Bean 以兼容测试代码中 Mock RestClient.Builder 的场景。
     * 新代码应优先使用 {@link #restClient()} 或 {@link AListApiClient}。
     * </p>
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * AList API 客户端 Bean
     */
    @Bean
    public AListApiClient alistApiClient() {
        return new AListApiClient(restClient());
    }

    /**
     * HTTP 连接工厂：配置连接超时和读取超时
     * <p>
     * 使用 SimpleClientHttpRequestFactory（基于 java.net.HttpURLConnection），
     * 支持连接超时和读取超时配置。
     * </p>
     */
    private SimpleClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS));
        factory.setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS));
        return factory;
    }

    /**
     * HTTP 请求/响应日志拦截器
     * <p>
     * 实现 ClientHttpRequestInterceptor 接口，在每次 HTTP 调用前后记录：
     * 请求方法、URI、请求头（Authorization 脱敏）、响应状态码、响应头。
     * 日志级别为 DEBUG，生产环境可按需开启。
     * </p>
     */
    static class LoggingInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            logRequest(request);
            long startTime = System.currentTimeMillis();
            try {
                ClientHttpResponse response = execution.execute(request, body);
                logResponse(request, response, startTime);
                return response;
            } catch (IOException e) {
                log.error("HTTP 请求失败：{} {} — 耗时={}ms, 原因：{}",
                    request.getMethod(), request.getURI(),
                    System.currentTimeMillis() - startTime, e.getMessage());
                throw e;
            }
        }

        /**
         * 记录 HTTP 请求信息（Authorization 头脱敏）
         */
        private void logRequest(HttpRequest request) {
            Map<String, List<String>> sanitizedHeaders = new LinkedHashMap<>();
            request.getHeaders().forEach((key, values) -> {
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(key)) {
                    sanitizedHeaders.put(key, List.of("***"));
                } else {
                    sanitizedHeaders.put(key, values);
                }
            });
            log.debug("HTTP 请求：{} {} — headers={}", request.getMethod(), request.getURI(), sanitizedHeaders);
        }

        /**
         * 记录 HTTP 响应信息
         */
        private void logResponse(HttpRequest request, ClientHttpResponse response, long startTime) throws IOException {
            log.debug("HTTP 响应：{} {} — 状态码={}, headers={}, 耗时={}ms",
                request.getMethod(),
                request.getURI(),
                response.getStatusCode(),
                response.getHeaders(),
                System.currentTimeMillis() - startTime);
        }
    }
}
