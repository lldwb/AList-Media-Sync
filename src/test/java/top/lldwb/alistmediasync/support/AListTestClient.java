package top.lldwb.alistmediasync.support;

import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * AList 真实操作封装客户端
 * <p>
 * 在 E2E 测试中用于验证 AList 服务端的实际状态：
 * <ul>
 *   <li>{@link #ping()} — 探活</li>
 *   <li>{@link #listFiles(String)} — 查询文件落盘</li>
 *   <li>{@link #getFileDetail(String)} — 验证转码产物</li>
 * </ul>
 * </p>
 * <p>
 * 使用 {@link RestClient} 发送 HTTP 请求。当 token 可用时自动注入 Authorization header，
 * 否则以无认证请求访问 AList /ping 和 /api/fs/* 端点。
 * </p>
 *
 * @author AList-Media-Sync
 */
public class AListTestClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final String token;

    /**
     * 构造 AList 测试客户端（无 token，仅限 /ping 探活）
     *
     * @param baseUrl AList 基础 URL（如 http://localhost:5244）
     */
    public AListTestClient(String baseUrl) {
        this(baseUrl, null);
    }

    /**
     * 构造 AList 测试客户端（带 token）
     *
     * @param baseUrl AList 基础 URL（如 http://localhost:5244）
     * @param token   AList 登录 token，null 表示无认证
     */
    public AListTestClient(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    /**
     * 构造 AList 测试客户端（使用自定义 RestClient）
     *
     * @param restClient 已配置的 RestClient
     * @param baseUrl    AList 基础 URL（如 http://localhost:5244）
     * @param token      AList 登录 token，null 表示无认证
     */
    public AListTestClient(RestClient restClient, String baseUrl, String token) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    /**
     * 探活：GET /ping
     *
     * @return true 表示 AList 服务可达
     */
    public boolean ping() {
        try {
            String response = restClient.get()
                .uri("/ping")
                .retrieve()
                .body(String.class);
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 查询文件列表：POST /api/fs/list
     * <p>
     * 当 {@link #token} 非空时自动注入 Authorization header。
     * </p>
     *
     * @param path 目录路径
     * @return AList API 响应 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listFiles(String path) {
        return restClient.post()
            .uri("/api/fs/list")
            .headers(h -> { if (token != null) h.set("Authorization", token); })
            .body(Map.of("path", path, "password", "", "page", 1, "per_page", 50, "refresh", false))
            .retrieve()
            .body(Map.class);
    }

    /**
     * 获取文件详情：POST /api/fs/get
     *
     * @param path 文件路径
     * @return AList API 响应 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFileDetail(String path) {
        return restClient.post()
            .uri("/api/fs/get")
            .headers(h -> { if (token != null) h.set("Authorization", token); })
            .body(Map.of("path", path, "password", "", "refresh", true))
            .retrieve()
            .body(Map.class);
    }
}