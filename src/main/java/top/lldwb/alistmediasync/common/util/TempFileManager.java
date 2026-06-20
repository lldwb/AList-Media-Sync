package top.lldwb.alistmediasync.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;

/**
 * 临时文件管理器
 * <p>
 * 负责转码过程中临时文件的创建、重命名、删除和权限设置。
 * 临时文件命名格式：{原文件名}.{扩展名}.{uuid}.{后缀}
 * 确保并发安全（UUID 全局唯一）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
public final class TempFileManager {

    private TempFileManager() {}

    /**
     * 规范化后缀：确保以点号开头、无路径分隔符、长度合法
     *
     * @param rawSuffix 原始后缀配置
     * @param maxLength 最大长度
     * @return 规范化后的后缀
     */
    public static String normalizeSuffix(String rawSuffix, int maxLength) {
        String suffix = rawSuffix.trim();

        // 空或仅点号 → 回退 .tmp
        if (suffix.isEmpty() || suffix.equals(".")) {
            log.warn("临时文件后缀无效（\"{}\"），使用默认值 .tmp", rawSuffix);
            return ".tmp";
        }

        // 仅空白字符 → 回退
        if (suffix.isBlank()) {
            log.warn("临时文件后缀仅包含空白字符，使用默认值 .tmp");
            return ".tmp";
        }

        // 长度截断
        if (suffix.length() > maxLength) {
            String truncated = suffix.substring(0, maxLength);
            log.warn("临时文件后缀超过最大长度 {}，已截断为：{}", maxLength, truncated);
            suffix = truncated;
        }

        // 补充点号前缀
        if (!suffix.startsWith(".")) {
            suffix = "." + suffix;
        }

        return suffix;
    }

    /**
     * 构建临时文件名
     *
     * @param originalName 原始文件名
     * @param suffix       规范化后的后缀
     * @return 格式如 video.mp4.a1b2c3d4.lldwb 的文件名
     */
    public static String buildTempFileName(String originalName, String suffix) {
        int dotIdx = originalName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? originalName : originalName;
        String extension = dotIdx > 0 ? originalName.substring(dotIdx) : "";
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return baseName + extension + "." + uuid + suffix;
    }

    /**
     * 创建临时文件
     *
     * @param tempDir          临时目录
     * @param originalFileName 原始文件名
     * @param suffix           规范化后的后缀
     * @return 临时文件路径
     */
    public static Path createTempFile(Path tempDir, String originalFileName, String suffix) throws IOException {
        // 确保临时目录存在
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
            setDirectoryPermissions(tempDir);
        }

        String tempFileName = buildTempFileName(originalFileName, suffix);
        Path tempFile = tempDir.resolve(tempFileName);
        Files.createFile(tempFile);
        setFilePermissions(tempFile);
        log.debug("临时文件已创建：{}", tempFile);
        return tempFile;
    }

    /**
     * 重命名为最终文件（去掉临时后缀，替换为目标扩展名）
     *
     * @param tempFilePath    临时文件路径
     * @param targetExtension 目标扩展名（不含点号，如 "mp3"）
     * @return 重命名后的文件路径
     */
    public static Path renameToFinal(Path tempFilePath, String targetExtension) throws IOException {
        String fileName = tempFilePath.getFileName().toString();
        // 去掉最后的临时后缀（如 .lldwb → .mp3）
        int lastDot = fileName.lastIndexOf('.');
        String nameWithoutSuffix = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
        // 替换最后的扩展名
        int secondLastDot = nameWithoutSuffix.lastIndexOf('.');
        String finalName;
        if (secondLastDot > 0) {
            finalName = nameWithoutSuffix.substring(0, secondLastDot + 1) + targetExtension;
        } else {
            finalName = nameWithoutSuffix + "." + targetExtension;
        }
        Path finalPath = tempFilePath.resolveSibling(finalName);
        Files.move(tempFilePath, finalPath);
        log.debug("文件已重命名：{} -> {}", tempFilePath.getFileName(), finalName);
        return finalPath;
    }

    /**
     * 静默删除文件（失败仅记录 WARN 日志，不抛异常）
     *
     * @param filePath 文件路径
     */
    public static void deleteQuietly(Path filePath) {
        if (filePath == null) return;
        try {
            if (Files.deleteIfExists(filePath)) {
                log.debug("文件已删除：{}", filePath);
            }
        } catch (IOException e) {
            log.warn("删除文件失败：{}，原因：{}", filePath, e.getMessage());
        }
    }

    /**
     * 设置 POSIX 文件权限（0600：仅运行用户可读写）
     * Windows 系统降级处理
     */
    public static void setFilePermissions(Path filePath) {
        try {
            if (isPosix()) {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(filePath, perms);
            }
        } catch (IOException e) {
            log.debug("设置文件权限失败（非 POSIX 系统可忽略）：{}", e.getMessage());
        }
    }

    /**
     * 设置 POSIX 目录权限（0700：仅运行用户可读写执行）
     */
    public static void setDirectoryPermissions(Path dirPath) {
        try {
            if (isPosix()) {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
                Files.setPosixFilePermissions(dirPath, perms);
            }
        } catch (IOException e) {
            log.debug("设置目录权限失败（非 POSIX 系统可忽略）：{}", e.getMessage());
        }
    }

    private static boolean isPosix() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("linux") || os.contains("mac") || os.contains("unix");
    }
}
