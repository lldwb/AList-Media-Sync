package top.lldwb.alistmediasync.storage.service.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import top.lldwb.alistmediasync.common.util.ApiUtil;
import top.lldwb.alistmediasync.sync.dto.sync.DirectoryEntryVO;
import top.lldwb.alistmediasync.sync.dto.sync.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * AList 存储引擎策略实现
 * <p>
 * 严格按 {@code md/alist/} 下的官方对接文档实现 {@link StorageEngineStrategy}，
 * 所有 HTTP 调用统一走 {@link ApiUtil}，由 ApiUtil 负责：
 * <ul>
 *   <li>HTTP 与业务码（{@code code != 200}）的统一校验</li>
 *   <li>token 脱敏 + 入参/出参 DEBUG 日志</li>
 *   <li>异常包装为 {@link RuntimeException} 抛出</li>
 * </ul>
 * 本类只负责将业务语义翻译为符合文档约定的请求体/路径参数，并解析 {@code data} 字段。
 * </p>
 * <p>遵循 constitution 原则 VII：写操作（上传/创建/删除）使用 INFO，读操作（列出/获取/下载）使用 DEBUG。</p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
public class AListStorageStrategy implements StorageEngineStrategy {

    /** AList API 单页请求大小（不宜过大，避免触发服务端限制） */
    private static final int PAGE_SIZE = 50;

    private final RestClient restClient;

    public AListStorageStrategy(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String type() {
        return "ALIST";
    }

    /**
     * 列出文件目录（对接 {@code POST /api/fs/list}）
     * <p>必传字段：path / password / page / per_page / refresh。</p>
     */
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
        Map<String, Object> result = ApiUtil.post(
            restClient, engine.getBaseUrl(), engine.getEncryptedToken(), "/api/fs/list", body);
        List<FileEntry> entries = parseFileList(result);
        log.debug("列出文件完成：path={}, 返回 {} 条", path, entries.size());
        return entries;
    }

    /**
     * 获取文件/目录信息（对接 {@code POST /api/fs/get}）
     * <p>按文档仅需 path/password；项目额外传 refresh=true 以绕过部分挂载驱动的单文件缓存。</p>
     */
    @Override
    public FileEntry getFileInfo(StorageEngine engine, String path) {
        log.debug("获取文件信息：引擎={}, path={}", engine.getName(), path);
        Map<String, Object> body = Map.of(
            "path", path,
            "password", "",
            "refresh", true
        );
        Map<String, Object> result = ApiUtil.post(
            restClient, engine.getBaseUrl(), engine.getEncryptedToken(), "/api/fs/get", body);
        return parseFileEntry(result);
    }

    /**
     * 下载文件（走 AList 下载端点 {@code GET /d{path}}）
     * <p>
     * 不走 {@code /api/fs/get} + raw_url 两步：部分挂载驱动（如 Synology SMB）对单文件
     * fs/get 实现有缺陷，会返回 {@code code=500, message="object not found"}，即便 list 能列出。
     * {@code /d} 端点由 AList 服务端内部解析路径并 302 跳到真实直链，更稳。
     * </p>
     */
    @Override
    public InputStream downloadFile(StorageEngine engine, String path) {
        log.debug("下载文件：引擎={}, path={}", engine.getName(), path);
        String downloadUrl = buildDownloadUrl(engine.getBaseUrl(), path);
        try {
            InputStream in = openWithAuthRedirects(downloadUrl, engine.getEncryptedToken(), 5);
            log.debug("文件下载流已打开：path={}, url={}", path, downloadUrl);
            return in;
        } catch (Exception e) {
            log.error("下载文件失败：path={}, url={}, 原因：{}", path, downloadUrl, e.getMessage(), e);
            throw new RuntimeException("AList 文件下载失败：" + path, e);
        }
    }

    /**
     * 构造 AList 下载端点 URL：{@code {baseUrl}/d{path}}。
     * <p>对 path 中每个段做 URL 编码，正确处理中文、全角符号、空格。</p>
     */
    private String buildDownloadUrl(String baseUrl, String path) {
        String trimmedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        StringBuilder sb = new StringBuilder(trimmedBase).append("/d");
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) continue;
            sb.append('/').append(
                java.net.URLEncoder.encode(segment, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20")
            );
        }
        return sb.toString();
    }

    /**
     * 打开 HTTP 输入流并手动跟随重定向。
     * <p>
     * Java {@link java.net.HttpURLConnection} 默认不跨协议（http↔https）自动跟随 302，
     * AList 的 /d 端点常会 302 跳转到第三方对象存储（OSS/S3 等）的 https 直链。
     * 出于安全考虑，重定向后不再携带 Authorization 头——避免泄露 AList token；
     * 目标 URL 通常已自带 sign 鉴权。
     * </p>
     */
    private InputStream openWithAuthRedirects(String url, String token, int maxRedirects) throws java.io.IOException {
        String current = url;
        for (int i = 0; i <= maxRedirects; i++) {
            java.net.URL u = new java.net.URL(current);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(false);
            if (i == 0 && token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", token);
            }
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(60_000);
            conn.connect();
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new java.io.IOException("重定向缺少 Location 头：HTTP " + code);
                }
                current = location;
                continue;
            }
            if (code != 200) {
                java.io.InputStream err = conn.getErrorStream();
                String body = err != null ? new String(err.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) : "";
                conn.disconnect();
                throw new java.io.IOException("HTTP " + code + " — " + body);
            }
            return conn.getInputStream();
        }
        throw new java.io.IOException("重定向次数超过上限（" + maxRedirects + "）");
    }

    /**
     * 上传文件（对接 {@code PUT /api/fs/put} 流式上传）
     * <p>
     * 严格按 {@code md/alist/fs/流式上传文件.md}：请求体为 application/octet-stream，
     * 通过 {@code File-Path}（URL-encoded）、{@code As-Task}、{@code Content-Length} header 携带元数据。
     * 相比 multipart 形式，无需将文件物化为内存表单，对大文件更友好。
     * </p>
     */
    @Override
    public void uploadFile(StorageEngine engine, String remotePath, InputStream inputStream, long fileSize) {
        log.info("上传文件：引擎={}, remotePath={}, size={}bytes", engine.getName(), remotePath, fileSize);
        ApiUtil.putStream(restClient, engine.getBaseUrl(), engine.getEncryptedToken(),
            remotePath, inputStream, fileSize, true);
        log.debug("文件上传完成：{}", remotePath);
    }

    /**
     * 新建文件夹（对接 {@code POST /api/fs/mkdir}）
     */
    @Override
    public void createDirectory(StorageEngine engine, String path) {
        log.info("创建目录：引擎={}, path={}", engine.getName(), path);
        ApiUtil.postVoid(restClient, engine.getBaseUrl(), engine.getEncryptedToken(),
            "/api/fs/mkdir", Map.of("path", path));
    }

    /**
     * 删除文件或文件夹（对接 {@code POST /api/fs/remove}）
     * <p>请求体需要 dir（父目录）+ names（文件名数组），文档约定 names 是数组。</p>
     */
    @Override
    public void deleteFile(StorageEngine engine, String path) {
        log.info("删除文件：引擎={}, path={}", engine.getName(), path);
        int slash = path.lastIndexOf('/');
        String dir = slash > 0 ? path.substring(0, slash) : "/";
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        Map<String, Object> body = Map.of("names", List.of(name), "dir", dir);
        ApiUtil.postVoid(restClient, engine.getBaseUrl(), engine.getEncryptedToken(),
            "/api/fs/remove", body);
    }

    /**
     * 复制文件（对接 {@code POST /api/fs/copy}）
     * <p>文档要求 src_dir / dst_dir / names 三字段；通过路径前缀拆分得到 src_dir 和 file name。</p>
     */
    @Override
    public void copyFile(StorageEngine engine, String sourcePath, String targetPath) {
        log.info("AList 同引擎复制：src={}, dst={}", sourcePath, targetPath);
        int srcSlash = sourcePath.lastIndexOf('/');
        String srcDir = srcSlash > 0 ? sourcePath.substring(0, srcSlash) : "/";
        String fileName = srcSlash >= 0 ? sourcePath.substring(srcSlash + 1) : sourcePath;
        // 目标 dst_dir：若 targetPath 以 fileName 结尾，去掉文件名得到父目录；否则视为已经是目录
        String dstDir = targetPath;
        if (dstDir.endsWith("/" + fileName)) {
            dstDir = dstDir.substring(0, dstDir.length() - fileName.length() - 1);
        }
        if (dstDir.isEmpty()) dstDir = "/";
        Map<String, Object> body = Map.of(
            "src_dir", srcDir,
            "dst_dir", dstDir,
            "names", List.of(fileName)
        );
        ApiUtil.postVoid(restClient, engine.getBaseUrl(), engine.getEncryptedToken(),
            "/api/fs/copy", body);
        log.debug("AList 复制完成：{} -> {}", sourcePath, targetPath);
    }

    @Override
    public List<DirectoryEntryVO> listDirectories(StorageEngine engine, String path) {
        log.debug("列出目录：引擎={}, path={}", engine.getName(), path);
        try {
            List<FileEntry> allEntries = fetchAllEntries(engine, path);
            List<FileEntry> directories = allEntries.stream()
                .filter(FileEntry::isDirectory)
                .toList();

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
    public List<FileEntry> listEntries(StorageEngine engine, String path) {
        log.debug("列出全部条目：引擎={}, path={}", engine.getName(), path);
        try {
            List<FileEntry> all = fetchAllEntries(engine, path);
            List<FileEntry> sorted = all.stream()
                .sorted(Comparator.comparing(FileEntry::isDirectory).reversed()
                    .thenComparing(FileEntry::name))
                .toList();
            log.debug("列出全部条目完成：path={}, 共 {} 个", path, sorted.size());
            return sorted;
        } catch (Exception e) {
            log.error("列出全部条目失败：{} — {}", path, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 测试连接：双段式探活
     * <ol>
     *   <li>{@code GET /ping} — 仅验证服务可达，不依赖 token；用于区分"服务挂了"和"token 错了"</li>
     *   <li>{@code GET /api/me} — 验证 token 有效（需要 Authorization 头返回 code=200）</li>
     * </ol>
     * 任一阶段失败即视为不可用，错误信息会指出失败阶段，便于排查。
     */
    @Override
    public boolean testConnection(StorageEngine engine) {
        log.debug("测试连接：引擎={}, baseUrl={}", engine.getName(), engine.getBaseUrl());
        // 阶段 1：/ping 仅探活，不传 token；AList /ping 返回字符串 "pong"（非 JSON），
        // ApiUtil 的 verifyBusinessCode 检测不到 code 字段会跳过校验。
        try {
            ApiUtil.get(restClient, engine.getBaseUrl(), null, "/ping");
        } catch (Exception e) {
            log.warn("AList 连接测试失败（ping 阶段）：{} -> {}", engine.getBaseUrl(), e.getMessage());
            return false;
        }
        // 阶段 2：/api/me 验证 token，ApiUtil 已校验 code==200，正常返回即视为成功
        try {
            ApiUtil.get(restClient, engine.getBaseUrl(), engine.getEncryptedToken(), "/api/me");
            log.info("连接测试成功：{}", engine.getBaseUrl());
            return true;
        } catch (Exception e) {
            log.warn("AList 连接测试失败（token 校验阶段）：{} -> {}", engine.getBaseUrl(), e.getMessage());
            return false;
        }
    }

    // ==================== 私有辅助方法 ====================

    /** 分页获取指定路径下的所有条目 */
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
                break;
            }
            page++;
        }
        log.debug("分页获取完成：path={}, 共 {} 条", path, all.size());
        return all;
    }

    /** 检查目录是否包含子目录 */
    private boolean hasChildren(StorageEngine engine, String path) {
        try {
            List<FileEntry> entries = fetchAllEntries(engine, path);
            return entries.stream().anyMatch(FileEntry::isDirectory);
        } catch (Exception e) {
            return false;
        }
    }

    /** 解析 AList /api/fs/list 返回的文件列表 */
    @SuppressWarnings("unchecked")
    private List<FileEntry> parseFileList(Map<String, Object> result) {
        if (result == null || !(result.get("data") instanceof Map<?, ?> data)) {
            return Collections.emptyList();
        }
        Object content = ((Map<String, Object>) data).get("content");
        if (!(content instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> files = (List<Map<String, Object>>) list;
        return files.stream()
            .map(this::mapToFileEntry)
            .filter(Objects::nonNull)
            .toList();
    }

    /** 解析 AList /api/fs/get 返回的单个文件信息 */
    @SuppressWarnings("unchecked")
    private FileEntry parseFileEntry(Map<String, Object> result) {
        if (result == null || !(result.get("data") instanceof Map<?, ?> data)) {
            return null;
        }
        return mapToFileEntry((Map<String, Object>) data);
    }

    /**
     * 将 AList API 返回的 Map 转换为 FileEntry
     * <p>
     * AList 对挂载存储（如 123云盘、夸克、百度网盘），API 返回的 path 字段为空字符串，
     * 实际路径在 virtual_path 中。优先使用 path，path 为空时回退到 virtual_path。
     * </p>
     */
    private FileEntry mapToFileEntry(Map<String, Object> map) {
        String name = (String) map.get("name");
        String path = (String) map.get("path");
        if (path == null || path.isEmpty()) {
            path = (String) map.get("virtual_path");
        }
        if (name == null || path == null) {
            return null;
        }
        // 过滤 Synology DSM 在 SMB/NFS 共享中暴露的扩展属性虚拟流（不是真实文件）：
        //   - 任何包含 @SynoEAStream 的条目（如 xxx.mp4@SynoEAStream）
        //   - 目录元数据文件 SYNOINDEX_MEDIA_INFO / @eaDir / @SynoResource
        // 这些条目能被 list 列出，但无法通过 fs/get 或下载端点获取流，
        // 同步时会污染失败计数并淹没真实错误，因此在入口直接丢弃。
        if (isSynologyVirtualEntry(name)) {
            return null;
        }
        boolean isDirectory = Boolean.TRUE.equals(map.get("is_dir"));
        long size = map.get("size") instanceof Number n ? n.longValue() : 0;
        LocalDateTime modifiedTime = parseModifiedTime(map.get("modified"));
        return new FileEntry(name, path, isDirectory, size, modifiedTime);
    }

    /** 识别 Synology DSM 扩展属性虚拟条目 */
    private boolean isSynologyVirtualEntry(String name) {
        if (name == null) return false;
        return name.contains("@SynoEAStream")
            || name.equals("SYNOINDEX_MEDIA_INFO")
            || name.equals("@eaDir")
            || name.equals("@SynoResource");
    }

    /** 解析 AList 的修改时间字段（ISO-8601 字符串或 epoch 秒） */
    private LocalDateTime parseModifiedTime(Object modified) {
        if (modified instanceof String s && !s.isEmpty()) {
            try {
                return LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception ignored) {
                // 忽略解析错误，可能是 ISO_OFFSET_DATE_TIME 格式
            }
        }
        if (modified instanceof Number n) {
            return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(n.longValue() * 1000),
                ZoneId.systemDefault()
            );
        }
        return null;
    }
}
