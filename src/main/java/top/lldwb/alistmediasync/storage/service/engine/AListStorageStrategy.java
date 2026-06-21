package top.lldwb.alistmediasync.storage.service.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import top.lldwb.alistmediasync.sync.dto.sync.DirectoryEntryVO;
import top.lldwb.alistmediasync.sync.dto.sync.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AList 存储引擎策略实现
 * <p>
 * 通过 HTTP 调用 AList REST API 实现文件操作。
 * 内部使用 Spring RestClient 直接调用 AList API。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AListStorageStrategy implements StorageEngineStrategy {

    private final RestClient.Builder restClientBuilder;

    @Override
    public String type() {
        return "ALIST";
    }

    @Override
    public List<FileEntry> listFiles(StorageEngine engine, String path, int page, int perPage) {
        Map<String, Object> body = Map.of(
            "path", path,
            "password", "",
            "page", page,
            "per_page", perPage,
            "refresh", false
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> result = restClient().post()
            .uri(engine.getBaseUrl() + "/api/fs/list")
            .header("Authorization", engine.getEncryptedToken())
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);

        return parseFileList(result);
    }

    @Override
    public FileEntry getFileInfo(StorageEngine engine, String path) {
        Map<String, Object> body = Map.of(
            "path", path,
            "password", "",
            "page", 1,
            "per_page", 1,
            "refresh", false
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> result = restClient().post()
            .uri(engine.getBaseUrl() + "/api/fs/get")
            .header("Authorization", engine.getEncryptedToken())
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);

        return parseFileEntry(result);
    }

    @Override
    public InputStream downloadFile(StorageEngine engine, String path) {
        Map<String, Object> body = Map.of(
            "path", path,
            "password", ""
        );
        byte[] fileBytes = restClient().post()
            .uri(engine.getBaseUrl() + "/api/fs/get")
            .header("Authorization", engine.getEncryptedToken())
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(body)
            .accept(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
            .retrieve()
            .body(byte[].class);
        return fileBytes != null ? new ByteArrayInputStream(fileBytes) : null;
    }

    @Override
    public void uploadFile(StorageEngine engine, String remotePath, InputStream inputStream, long fileSize) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new org.springframework.core.io.InputStreamResource(inputStream) {
            @Override
            public long contentLength() {
                return fileSize > 0 ? fileSize : -1;
            }
        });

        restClient().put()
            .uri(engine.getBaseUrl() + "/api/fs/put")
            .header("Authorization", engine.getEncryptedToken())
            .header("File-Path", remotePath)
            .header("As-Task", "true")
            .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
            .body(parts)
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public void createDirectory(StorageEngine engine, String path) {
        Map<String, Object> body = Map.of("path", path);
        restClient().post()
            .uri(engine.getBaseUrl() + "/api/fs/mkdir")
            .header("Authorization", engine.getEncryptedToken())
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public void deleteFile(StorageEngine engine, String path) {
        Map<String, Object> body = Map.of(
            "names", List.of(path.substring(path.lastIndexOf('/') + 1)),
            "dir", path.substring(0, path.lastIndexOf('/') + 1)
        );
        restClient().post()
            .uri(engine.getBaseUrl() + "/api/fs/remove")
            .header("Authorization", engine.getEncryptedToken())
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    // AList API 单页最大返回数量
    private static final int MAX_PER_PAGE = 1000;

    @Override
    public List<DirectoryEntryVO> listDirectories(StorageEngine engine, String path) {
        try {
            // 获取所有条目，仅过滤出目录；使用合理分页大小防止 AList API 异常
            List<FileEntry> allEntries = listFiles(engine, path, 1, MAX_PER_PAGE);
            return allEntries.stream()
                .filter(FileEntry::isDirectory)
                .collect(Collectors.toMap(
                    FileEntry::path,
                    f -> new DirectoryEntryVO(f.name(), f.path(), hasChildren(engine, f.path())),
                    (existing, replacement) -> existing  // 路径重复时保留第一个
                ))
                .values()
                .stream()
                .toList();
        } catch (Exception e) {
            log.error("列出目录失败：{} — {}", path, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public boolean testConnection(StorageEngine engine) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restClient().get()
                .uri(engine.getBaseUrl() + "/api/me")
                .header("Authorization", engine.getEncryptedToken())
                .retrieve()
                .body(Map.class);
            return result != null && result.get("code") instanceof Number
                && ((Number) result.get("code")).intValue() == 200;
        } catch (Exception e) {
            log.warn("AList 连接测试失败：{} -> {}", engine.getBaseUrl(), e.getMessage());
            return false;
        }
    }

    // ==================== 私有辅助方法 ====================

    private RestClient restClient() {
        return restClientBuilder.build();
    }

    /**
     * 检查目录是否包含子目录
     */
    private boolean hasChildren(StorageEngine engine, String path) {
        try {
            List<FileEntry> entries = listFiles(engine, path, 1, MAX_PER_PAGE);
            return entries.stream().anyMatch(FileEntry::isDirectory);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 AList API 返回的文件列表
     */
    @SuppressWarnings("unchecked")
    private List<FileEntry> parseFileList(Map<String, Object> result) {
        if (result == null || result.get("data") == null) {
            return Collections.emptyList();
        }
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        Object content = data.get("content");
        if (!(content instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> files = (List<Map<String, Object>>) content;
        return files.stream()
            .map(this::mapToFileEntry)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 解析 AList API 返回的单个文件信息
     */
    @SuppressWarnings("unchecked")
    private FileEntry parseFileEntry(Map<String, Object> result) {
        if (result == null || result.get("data") == null) {
            return null;
        }
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        return mapToFileEntry(data);
    }

    /**
     * 将 AList API 返回的 Map 转换为 FileEntry
     */
    private FileEntry mapToFileEntry(Map<String, Object> map) {
        String name = (String) map.get("name");
        String path = (String) map.get("path");
        if (name == null || path == null) {
            return null;
        }
        boolean isDirectory = Boolean.TRUE.equals(map.get("is_dir"));
        long size = map.get("size") instanceof Number ? ((Number) map.get("size")).longValue() : 0;
        LocalDateTime modifiedTime = parseModifiedTime(map.get("modified"));
        return new FileEntry(name, path, isDirectory, size, modifiedTime);
    }

    /**
     * 解析 AList 的修改时间
     */
    private LocalDateTime parseModifiedTime(Object modified) {
        if (modified instanceof String) {
            try {
                return LocalDateTime.parse((String) modified, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
        if (modified instanceof Number) {
            return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(((Number) modified).longValue() * 1000),
                ZoneId.systemDefault()
            );
        }
        return null;
    }
}
