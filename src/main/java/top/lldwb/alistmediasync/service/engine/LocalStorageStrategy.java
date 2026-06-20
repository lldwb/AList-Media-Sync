package top.lldwb.alistmediasync.service.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lldwb.alistmediasync.dto.sync.DirectoryEntryVO;
import top.lldwb.alistmediasync.dto.sync.FileEntry;
import top.lldwb.alistmediasync.entity.StorageEngine;

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

            // 简单分页
            int fromIndex = (page - 1) * perPage;
            if (fromIndex >= allEntries.size()) {
                return Collections.emptyList();
            }
            int toIndex = Math.min(fromIndex + perPage, allEntries.size());
            return allEntries.subList(fromIndex, toIndex);
        } catch (IOException e) {
            log.error("列出本地文件失败：{}", dir, e);
            return Collections.emptyList();
        }
    }

    @Override
    public FileEntry getFileInfo(StorageEngine engine, String path) {
        Path filePath = resolvePath(engine, path);
        if (!Files.exists(filePath)) {
            return null;
        }
        return toFileEntry(filePath, engine.getLocalPath());
    }

    @Override
    public InputStream downloadFile(StorageEngine engine, String path) {
        Path filePath = resolvePath(engine, path);
        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new RuntimeException("读取本地文件失败：" + filePath, e);
        }
    }

    @Override
    public void uploadFile(StorageEngine engine, String remotePath, InputStream inputStream, long fileSize) {
        Path targetPath = resolvePath(engine, remotePath);
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
        } catch (IOException e) {
            throw new RuntimeException("写入本地文件失败：" + targetPath, e);
        }
    }

    @Override
    public void createDirectory(StorageEngine engine, String path) {
        Path dirPath = resolvePath(engine, path);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException("创建本地目录失败：" + dirPath, e);
        }
    }

    @Override
    public void deleteFile(StorageEngine engine, String path) {
        Path filePath = resolvePath(engine, path);
        try {
            if (Files.isDirectory(filePath)) {
                // 递归删除目录
                try (Stream<Path> walk = Files.walk(filePath)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new RuntimeException("删除失败：" + p, e);
                            }
                        });
                }
            } else {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("删除本地文件失败：" + filePath, e);
        }
    }

    @Override
    public List<DirectoryEntryVO> listDirectories(StorageEngine engine, String path) {
        Path dir = resolvePath(engine, path);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("本地目录不存在或不是目录：{}", dir);
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
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
        } catch (IOException e) {
            log.error("列出本地子目录失败：{}", dir, e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean testConnection(StorageEngine engine) {
        Path dir = Path.of(engine.getLocalPath());
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
        return true;
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
