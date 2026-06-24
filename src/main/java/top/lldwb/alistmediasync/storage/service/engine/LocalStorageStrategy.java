package top.lldwb.alistmediasync.storage.service.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lldwb.alistmediasync.sync.dto.sync.DirectoryEntryVO;
import top.lldwb.alistmediasync.sync.dto.sync.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

/**
 * 本地路径存储引擎策略实现
 * <p>
 * 使用 java.nio.file.Files 和 Path 操作本地文件系统。
 * localPath 必须指向已存在且可读写的目录。
 * 遵循 constitution 原则 VII：所有本地目录操作记录输入路径和输出结果。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
public class LocalStorageStrategy implements StorageEngineStrategy {

    @Override
    public String type() {
        return "LOCAL";
    }

    @Override
    public List<FileEntry> listFiles(StorageEngine engine, String path, int page, int perPage) {
        Path dir = resolvePath(engine, path);
        log.debug("列出本地文件：引擎={}, path={}, page={}, perPage={}", engine.getName(), dir, page, perPage);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("本地目录不存在或不是目录：{}", dir);
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<FileEntry> allEntries = stream
                .map(p -> toFileEntry(p, engine.getLocalPath()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FileEntry::isDirectory).reversed()
                    .thenComparing(FileEntry::name))
                .toList();

            // 空目录：直接返回，避免与"分页越界"混淆
            if (allEntries.isEmpty()) {
                log.debug("列出本地文件完成：path={}, 目录为空", dir);
                return Collections.emptyList();
            }

            // 简单分页
            int fromIndex = (page - 1) * perPage;
            if (fromIndex >= allEntries.size()) {
                log.debug("列出本地文件完成：path={}, 总数={}, 请求 page={} 超出范围", dir, allEntries.size(), page);
                return Collections.emptyList();
            }
            int toIndex = Math.min(fromIndex + perPage, allEntries.size());
            List<FileEntry> result = allEntries.subList(fromIndex, toIndex);
            log.debug("列出本地文件完成：path={}, 总数={}, 返回 {} 条", dir, allEntries.size(), result.size());
            return result;
        } catch (IOException e) {
            log.error("列出本地文件失败：{} — {}", dir, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public FileEntry getFileInfo(StorageEngine engine, String path) {
        Path filePath = resolvePath(engine, path);
        log.debug("获取本地文件信息：引擎={}, path={}", engine.getName(), filePath);
        if (!Files.exists(filePath)) {
            log.debug("本地文件不存在：{}", filePath);
            return null;
        }
        return toFileEntry(filePath, engine.getLocalPath());
    }

    @Override
    public InputStream downloadFile(StorageEngine engine, String path) {
        Path filePath = resolvePath(engine, path);
        log.debug("读取本地文件：引擎={}, path={}", engine.getName(), filePath);
        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            log.error("读取本地文件失败：path={} — {}", filePath, e.getMessage(), e);
            throw new RuntimeException("读取本地文件失败：" + filePath, e);
        }
    }

    @Override
    public void uploadFile(StorageEngine engine, String remotePath, InputStream inputStream, long fileSize) {
        Path targetPath = resolvePath(engine, remotePath);
        log.info("写入本地文件：引擎={}, path={}, size={}bytes", engine.getName(), targetPath, fileSize);
        try {
            // 确保父目录存在
            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream os = Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            log.debug("本地文件写入完成：{}", targetPath);
        } catch (IOException e) {
            log.error("写入本地文件失败：path={}, size={}bytes — {}", targetPath, fileSize, e.getMessage(), e);
            throw new RuntimeException("写入本地文件失败：" + targetPath, e);
        }
    }

    @Override
    public void createDirectory(StorageEngine engine, String path) {
        Path dirPath = resolvePath(engine, path);
        log.info("创建本地目录：引擎={}, path={}", engine.getName(), dirPath);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            log.error("创建本地目录失败：path={} — {}", dirPath, e.getMessage(), e);
            throw new RuntimeException("创建本地目录失败：" + dirPath, e);
        }
    }

    @Override
    public void deleteFile(StorageEngine engine, String path) {
        Path filePath = resolvePath(engine, path);
        log.info("删除本地文件/目录：引擎={}, path={}", engine.getName(), filePath);
        try {
            if (Files.isDirectory(filePath)) {
                // 递归删除目录
                try (Stream<Path> walk = Files.walk(filePath)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                log.error("递归删除子节点失败：path={} — {}", p, e.getMessage(), e);
                                throw new RuntimeException("删除失败：" + p, e);
                            }
                        });
                }
            } else {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException e) {
            log.error("删除本地文件失败：path={} — {}", filePath, e.getMessage(), e);
            throw new RuntimeException("删除本地文件失败：" + filePath, e);
        }
    }

    @Override
    public List<DirectoryEntryVO> listDirectories(StorageEngine engine, String path) {
        Path dir = resolvePath(engine, path);
        log.debug("列出本地子目录：引擎={}, path={}", engine.getName(), dir);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("本地目录不存在或不是目录：{}", dir);
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<DirectoryEntryVO> result = stream
                .filter(Files::isDirectory)
                .map(p -> {
                    String name = p.getFileName().toString();
                    String relativePath = toRelativePath(p, engine.getLocalPath());
                    boolean hasChildren = false;
                    try (Stream<Path> children = Files.list(p)) {
                        hasChildren = children.anyMatch(Files::isDirectory);
                    } catch (IOException ignored) {
                    }
                    return new DirectoryEntryVO(name, "/" + relativePath, hasChildren);
                })
                .sorted(Comparator.comparing(DirectoryEntryVO::name))
                .toList();
            log.debug("列出本地子目录完成：path={}, 共 {} 个", dir, result.size());
            return result;
        } catch (IOException e) {
            log.error("列出本地子目录失败：{} — {}", dir, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<FileEntry> listEntries(StorageEngine engine, String path) {
        Path dir = resolvePath(engine, path);
        log.debug("列出本地全部条目：引擎={}, path={}", engine.getName(), dir);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("本地目录不存在或不是目录：{}", dir);
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            // 直接走 toFileEntry，目录在前、文件在后，名称升序
            List<FileEntry> result = stream
                .map(p -> toFileEntry(p, engine.getLocalPath()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FileEntry::isDirectory).reversed()
                    .thenComparing(FileEntry::name))
                .toList();
            log.debug("列出本地全部条目完成：path={}, 共 {} 个", dir, result.size());
            return result;
        } catch (IOException e) {
            log.error("列出本地全部条目失败：{} — {}", dir, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean testConnection(StorageEngine engine) {
        Path dir = Path.of(engine.getLocalPath());
        log.debug("测试本地连接：引擎={}, path={}", engine.getName(), dir);
        if (!Files.exists(dir)) {
            log.warn("本地路径不存在：{}", dir);
            return false;
        }
        if (!Files.isDirectory(dir)) {
            log.warn("本地路径不是目录：{}", dir);
            return false;
        }
        if (!Files.isReadable(dir)) {
            log.warn("本地目录不可读：{}", dir);
            return false;
        }
        if (!Files.isWritable(dir)) {
            log.warn("本地目录不可写：{}", dir);
            return false;
        }
        log.info("本地连接测试成功：{}", dir);
        return true;
    }

    @Override
    public void copyFile(StorageEngine engine, String sourcePath, String targetPath) {
        Path source = resolvePath(engine, sourcePath);
        Path target = resolvePath(engine, targetPath);
        log.info("本地同引擎复制：src={}, dst={}", source, target);
        try {
            // 确保目标父目录存在
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("本地复制完成：{} -> {}", source, target);
        } catch (IOException e) {
            log.error("本地文件复制失败：src={}, dst={} — {}", source, target, e.getMessage(), e);
            throw new RuntimeException("本地文件复制失败：" + source + " -> " + target, e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将相对路径解析为本地绝对路径
     */
    private Path resolvePath(StorageEngine engine, String path) {
        String relativePath = path != null ? path : "";
        // 去除开头的 "/"，与 localPath 拼接
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        if (relativePath.isEmpty()) {
            return Path.of(engine.getLocalPath());
        }
        return Path.of(engine.getLocalPath(), relativePath);
    }

    /**
     * 计算相对于 localPath 的路径
     */
    private String toRelativePath(Path absolutePath, String localPath) {
        Path localRoot = Path.of(localPath);
        Path relative = localRoot.relativize(absolutePath);
        return relative.toString().replace('\\', '/');
    }

    /**
     * 将本地 Path 转换为 FileEntry
     */
    private FileEntry toFileEntry(Path path, String localPath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            String name = path.getFileName().toString();
            String relativePath = toRelativePath(path, localPath);
            return new FileEntry(
                name,
                "/" + relativePath,
                attrs.isDirectory(),
                attrs.isDirectory() ? 0 : attrs.size(),
                LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault())
            );
        } catch (IOException e) {
            return null;
        }
    }
}
