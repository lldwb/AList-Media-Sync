package top.lldwb.alistmediasync.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * AList API HTTP 客户端（@Deprecated — 使用 {@link top.lldwb.alistmediasync.service.engine.AListStorageStrategy} 替代）
 * <p>
 * 基于 Spring RestClient 封装 AList 的 REST API 调用，
 * 包括文件列表、下载、上传、删除和目录操作。
 * 所有请求通过 AList Token 认证（X-Auth 请求头）。
 * </p>
 * <p>
 * 注意：此客户端已由策略模式接管。新代码应通过 {@code StorageEngineService.resolve()}
 * 获取 {@code StorageEngineStrategy} 实现来操作文件。
 * 保留此类以支持尚未迁移的调用方（如 SyncTaskManageService 中的连接测试）。
 * </p>
 *
 * @author AList-Media-Sync
 * @deprecated 使用 {@link top.lldwb.alistmediasync.service.engine.AListStorageStrategy} 替代
 */
@Deprecated
@Slf4j
@Component
public class AListClient {

    private final RestClient restClient;

    public AListClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    /**
     * 列出目录文件（分页）
     * 对应 AList API：POST /api/fs/list
     *
     * @param baseUrl AList 服务器基础 URL
     * @param token   API Token
     * @param path    目录路径
     * @param page    页码（从 1 开始）
     * @param perPage 每页文件数
     * @return AList API 返回的原始 Map（含 files 数组）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listFiles(String baseUrl, String token, String path, int page, int perPage) {
        Map<String, Object> body = Map.of(
            "path", path,
            "password", "",
            "page", page,
            "per_page", perPage,
            "refresh", false
        );
        return restClient.post()
            .uri(baseUrl + "/api/fs/list")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
    }

    /**
     * 获取文件/目录信息
     * 对应 AList API：POST /api/fs/get
     *
     * @param baseUrl AList 服务器基础 URL
     * @param token   API Token
     * @param path    文件路径
     * @return AList API 返回的文件信息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFileInfo(String baseUrl, String token, String path) {
        Map<String, Object> body = Map.of(
            "path", path,
            "password", "",
            "page", 1,
            "per_page", 1,
            "refresh", false
        );
        return restClient.post()
            .uri(baseUrl + "/api/fs/get")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
    }

    /**
     * 下载文件（返回 InputStream，调用方负责关闭）
     * 对应 AList API：POST /api/fs/get，会返回 raw URL
     * 实际下载通过 AList /d 端点
     *
     * @param baseUrl  AList 服务器基础 URL
     * @param token    API Token
     * @param filePath 文件路径
     * @return 文件输入流
     */
    public InputStream downloadFile(String baseUrl, String token, String filePath) {
        Map<String, Object> body = Map.of(
            "path", filePath,
            "password", ""
        );
        byte[] fileBytes = restClient.post()
            .uri(baseUrl + "/api/fs/get")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .retrieve()
            .body(byte[].class);
        return fileBytes != null ? new java.io.ByteArrayInputStream(fileBytes) : null;
    }

    /**
     * 上传文件（流式上传）
     * 对应 AList API：PUT /api/fs/put
     *
     * @param baseUrl    AList 服务器基础 URL
     * @param token      API Token
     * @param remotePath 远程目标路径
     * @param inputStream 文件输入流
     * @param fileSize   文件大小（字节，-1 表示未知）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> uploadFile(String baseUrl, String token, String remotePath, InputStream inputStream, long fileSize) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new InputStreamResource(inputStream) {
            @Override
            public long contentLength() {
                return fileSize > 0 ? fileSize : -1;
            }
        });

        return restClient.put()
            .uri(baseUrl + "/api/fs/put")
            .header("Authorization", token)
            .header("File-Path", remotePath)
            .header("As-Task", "true")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(parts)
            .retrieve()
            .body(Map.class);
    }

    /**
     * 创建目录
     * 对应 AList API：POST /api/fs/mkdir
     *
     * @param baseUrl AList 服务器基础 URL
     * @param token   API Token
     * @param path    目录路径
     */
    public void createDirectory(String baseUrl, String token, String path) {
        Map<String, Object> body = Map.of("path", path);
        restClient.post()
            .uri(baseUrl + "/api/fs/mkdir")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    /**
     * 删除文件/目录
     * 对应 AList API：POST /api/fs/remove
     *
     * @param baseUrl    AList 服务器基础 URL
     * @param token      API Token
     * @param remotePath 远程路径
     */
    public void deleteFile(String baseUrl, String token, String remotePath) {
        Map<String, Object> body = Map.of(
            "names", List.of(remotePath.substring(remotePath.lastIndexOf('/') + 1)),
            "dir", remotePath.substring(0, remotePath.lastIndexOf('/') + 1)
        );
        restClient.post()
            .uri(baseUrl + "/api/fs/remove")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    /**
     * 测试连接：向 AList /api/me 端点发送 GET 请求验证 Token 有效性
     *
     * @param baseUrl AList 服务器基础 URL
     * @param token   API Token
     * @return 连接成功返回 true
     */
    @SuppressWarnings("unchecked")
    public boolean testConnection(String baseUrl, String token) {
        try {
            Map<String, Object> result = restClient.get()
                .uri(baseUrl + "/api/me")
                .header("Authorization", token)
                .retrieve()
                .body(Map.class);
            return result != null && result.get("code") instanceof Number && ((Number) result.get("code")).intValue() == 200;
        } catch (Exception e) {
            log.warn("AList 连接测试失败：{} -> {}", baseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * 验证用户凭据（用户名+密码）获取 Token
     * 对应 AList API：POST /api/auth/login
     *
     * @param baseUrl  AList 服务器基础 URL
     * @param username 用户名
     * @param password 密码
     * @return API Token
     */
    @SuppressWarnings("unchecked")
    public String login(String baseUrl, String username, String password) {
        Map<String, String> body = Map.of("username", username, "password", password);
        Map<String, Object> result = restClient.post()
            .uri(baseUrl + "/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
        if (result != null && result.get("data") instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            return (String) data.get("token");
        }
        throw new RuntimeException("AList 登录失败：未获取到 Token");
    }
}
