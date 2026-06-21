package top.lldwb.alistmediasync.storage.service.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import top.lldwb.alistmediasync.common.util.ApiUtil;
import top.lldwb.alistmediasync.sync.dto.sync.DirectoryEntryVO;
import top.lldwb.alistmediasync.sync.dto.sync.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * AList 存储引擎策略实现
 * <p>
 * 通过 {@link ApiUtil} 静态方法统一封装 HTTP 调用 AList REST API，
 * 实现文件操作。所有 API 请求均经过统一的日志输出和异常处理。
 * 遵循 constitution 原则 VII：写操作（上传/创建/删除）使用 INFO，
 * 读操作（列出/获取/下载）使用 DEBUG。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
public class AListStorageStrategy implements StorageEngineStrategy {

    private final RestClient restClient;

    public AListStorageStrategy(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String type() {
        return "ALIST";
    }

    @Override
    public List<FileEntry> listFiles(StorageEngine engine, String path, int page, int perPage) {
        log.debug("列出文件：引擎={}, path={}, page={}, perPage={}", engine.getName(), path, page, perPage);
        Map<String, Object> body = Map.of(
            "path", path,
            "password", "",
            "page", page,
            "per_page", perPage,
            "refresh", false
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> result = ApiUtil.post(
            restClient, engine.getBaseUrl(), engine.getEncryptedToken(), "/api/fs/list", body);
        List<FileEntry> entries = parseFileList(result);
        log.debug("列出文件完成：path={}, 返回 {} 条", path, entries.size());
        return entries;
    }

    @Override
    public FileEntry getFileInfo(StorageEngine engine, String path) {
        log.debug("获取文件信息：引擎={}, path={}", engine.getName(), path);
        Map<String, Object> body = Map.of(
            "path", path,
            "password", "",
            "page", 1,
            "per_page", 1,
            "refresh", false
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> result = ApiUtil.post(
            restClient, engine.getBaseUrl(), engine.getEncryptedToken(), "/api/fs/get", body);
        return parseFileEntry(result);
    }

    @Override
    public InputStream downloadFile(StorageEngine engine, String path) {
        log.debug("下载文件：引擎={}, path={}", engine.getName(), path);
        Map<String, Object> body = Map.of(
            "path", path,
            "password", ""
        );
        byte[] fileBytes = ApiUtil.postForBytes(
            restClient, engine.getBaseUrl(), engine.getEncryptedToken(), "/api/fs/get", body);
        if (fileBytes != null) {
            log.debug("文件下载完成：path={}, size={}bytes", path, fileBytes.length);
            return new ByteArrayInputStream(fileBytes);
        }
        return null;
    }

    @Override
    public void uploadFile(StorageEngine engine, String remotePath, InputStream inputStream, long fileSize) {
        log.info("上传文件：引擎={}, remotePath={}, size={}bytes", engine.getName(), remotePath, fileSize);
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new org.springframework.core.io.InputStreamResource(inputStream) {
            @Override
            public long contentLength() {
                return fileSize > 0 ? fileSize : -1;
            }
        });

        Map<String, String> extraHeaders = new LinkedHashMap<>();
        extraHeaders.put("File-Path", remotePath);
        extraHeaders.put("As-Task", "true");

        ApiUtil.putMultipart(
            restClient, engine.getBaseUrl(), engine.getEncryptedToken(), "/api/fs/put", parts, extraHeaders);
        log.debug("文件上传完成：{}", remotePath);
    }

    @Override
    public void createDirectory(StorageEngine engine, String path) {
        log.info("创建目录：引擎={}, path={}", engine.getName(), path);
        Map<String, Object> body = Map.of("path", path);
        ApiUtil.postVoid(
            restClient, engine.getBaseUrl(), engine.getEncryptedToken(), "/api/fs/mkdir", body);
    }

    @Override
    public void deleteFile(StorageEngine engine, String path) {
        log.info("删除文件：引擎={}, path={}", engine.getName(), path);
        Map<String, Object> body = Map.of(
            "names", List.of(path.substring(path.lastIndexOf('/') + 1)),
            "dir", path.substring(0, path.lastIndexOf('/') + 1)
        );
        ApiUtil.postVoid(
            restClient, engine.getBaseUrl(), engine.getEncryptedToken(), "/api/fs/remove", body);
    }

    // AList API 单页请求大小（不宜过大，避免触发服务端限制）
    private static final int PAGE_SIZE = 50;

    @Override
    public List<DirectoryEntryVO> listDirectories(StorageEngine engine, String path) {
        log.debug("列出目录：引擎={}, path={}", engine.getName(), path);
        try {
            // 分页获取当前路径下的所有条目，仅过滤出目录
            List<FileEntry> allEntries = fetchAllEntries(engine, path);
            List<FileEntry> directories = allEntries.stream()
                .filter(FileEntry::isDirectory)
                .toList();

            // 使用 LinkedHashMap 保证插入顺序，按路径去重
            Map<String, DirectoryEntryVO> resultMap = new LinkedHashMap<>();
            for (FileEntry f : directories) {
                resultMap.putIfAbsent(f.path(),
                    new DirectoryEntryVO(f.name(), f.path(), hasChildren(engine, f.path())));
            }
            log.debug("列出目录完成：path={}, 共 {} 个目录", path, resultMap.size());
            return List.copyOf(resultMap.values());
        } catch (Exception e) {
            log.error("列出目录失败：{} — {}", path, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public boolean testConnection(StorageEngine engine) {
        log.debug("测试连接：引擎={}, baseUrl={}", engine.getName(), engine.getBaseUrl());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = ApiUtil.get(
                restClient, engine.getBaseUrl(), engine.getEncryptedToken(), "/api/me");
            boolean success = result != null && result.get("code") instanceof Number
                && ((Number) result.get("code")).intValue() == 200;
            if (success) {
                log.info("连接测试成功：{}", engine.getBaseUrl());
            }
            return success;
        } catch (Exception e) {
            log.warn("AList 连接测试失败：{} -> {}", engine.getBaseUrl(), e.getMessage());
            return false;
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 分页获取指定路径下的所有条目
     * <p>
     * AList 服务端可能限制单页返回数量，使用固定 pageSize 循环分页，
     * 直到返回条目数少于请求数（表示已到末尾）或返回空列表。
     * </p>
     */
    private List<FileEntry> fetchAllEntries(StorageEngine engine, String path) {
        log.debug("分页获取所有条目：引擎={}, path={}", engine.getName(), path);
        List<FileEntry> all = new ArrayList<>();
        int page = 1;
        while (true) {
            log.debug("获取第 {} 页，已收集 {} 条", page, all.size());
            List<FileEntry> pageEntries = listFiles(engine, path, page, PAGE_SIZE);
            if (pageEntries.isEmpty()) {
                break;
            }
            all.addAll(pageEntries);
            if (pageEntries.size() < PAGE_SIZE) {
                break; // 最后一页
            }
            page++;
        }
        log.debug("分页获取完成：path={}, 共 {} 条", path, all.size());
        return all;
    }

    /**
     * 检查目录是否包含子目录
     */
    private boolean hasChildren(StorageEngine engine, String path) {
        try {
            List<FileEntry> entries = fetchAllEntries(engine, path);
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
     * <p>
     * AList 对于挂载的存储（如 123云盘、夸克、百度网盘），API 返回的 path 字段为空字符串，
     * 实际路径存储在 virtual_path 中。需要优先使用 path，path 为空时回退到 virtual_path。
     * </p>
     */
    private FileEntry mapToFileEntry(Map<String, Object> map) {
        String name = (String) map.get("name");
        String path = (String) map.get("path");
        // AList 挂载存储的 path 可能为空字符串，此时回退使用 virtual_path
        if (path == null || path.isEmpty()) {
            path = (String) map.get("virtual_path");
        }
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
