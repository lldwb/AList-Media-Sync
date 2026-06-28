package top.lldwb.alistmediasync.common.util;

/**
 * 路径工具类
 * <p>
 * 提供跨模块统一的路径拼接、拆分、扩展名替换等静态方法。
 * 所有路径分隔符固定使用正斜杠（{@code /}），不依赖操作系统。
 * </p>
 * <p>
 * 语义基准：以原 {@code SyncService.concatPath} 的行为为准，
 * 拼接前去除 {@code name} 的所有前导斜杠，避免产生 {@code //}。
 * </p>
 *
 * @author AList-Media-Sync
 */
public final class PathUtils {

    private PathUtils() {
    }

    /**
     * 拼接目录路径与文件名。
     * <ul>
     *   <li>{@code dir} 为 {@code null}、空字符串或 {@code "/"} 时，等同于根目录</li>
     *   <li>{@code name} 的前导斜杠会被全部去除</li>
     *   <li>{@code dir} 尾斜杠自动处理</li>
     * </ul>
     *
     * @param dir  目录路径，可为 {@code null}（视为根目录）
     * @param name 文件或子目录名，不可为 {@code null}
     * @return 拼接后的完整路径
     * @throws IllegalArgumentException 如果 {@code name} 为 {@code null}
     */
    public static String join(String dir, String name) {
        if (name == null) {
            throw new IllegalArgumentException("name 不能为 null");
        }
        String normalizedName = trimLeadingSlash(name);
        if (dir == null || dir.isEmpty() || dir.equals("/")) {
            return "/" + normalizedName;
        }
        return dir.endsWith("/") ? dir + normalizedName : dir + "/" + normalizedName;
    }

    /**
     * 提取父目录路径。
     *
     * @param fullPath 完整文件路径
     * @return 父目录路径，无父目录时返回空字符串
     * @throws IllegalArgumentException 如果 {@code fullPath} 为 {@code null}
     */
    public static String parentDir(String fullPath) {
        if (fullPath == null) {
            throw new IllegalArgumentException("fullPath 不能为 null");
        }
        int lastSlash = fullPath.lastIndexOf('/');
        return lastSlash > 0 ? fullPath.substring(0, lastSlash) : "";
    }

    /**
     * 提取路径中的文件名（最后一个 {@code /} 之后的部分）。
     *
     * @param fullPath 完整文件路径
     * @return 文件名，路径为 {@code "/"} 时返回空字符串
     * @throws IllegalArgumentException 如果 {@code fullPath} 为 {@code null}
     */
    public static String baseName(String fullPath) {
        if (fullPath == null) {
            throw new IllegalArgumentException("fullPath 不能为 null");
        }
        int lastSlash = fullPath.lastIndexOf('/');
        return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
    }

    /**
     * 替换文件名的扩展名。
     * <ul>
     *   <li>若原文件名有扩展名，替换为 {@code newExt}</li>
     *   <li>若原文件名无扩展名，直接追加 {@code .newExt}</li>
     *   <li>{@code newExt} 的前导 {@code .} 自动去除</li>
     * </ul>
     *
     * @param fileName 原始文件名（如 {@code video.flv}）
     * @param newExt   新扩展名（如 {@code mp3} 或 {@code .mp3}）
     * @return 替换扩展名后的文件名
     * @throws IllegalArgumentException 如果任一参数为 {@code null}
     */
    public static String swapExtension(String fileName, String newExt) {
        if (fileName == null) {
            throw new IllegalArgumentException("fileName 不能为 null");
        }
        if (newExt == null) {
            throw new IllegalArgumentException("newExt 不能为 null");
        }
        String ext = newExt.startsWith(".") ? newExt.substring(1) : newExt;
        int dotIdx = fileName.lastIndexOf('.');
        // 处理 ".gitignore" 这类以点开头的文件名：没有真正的扩展名
        if (dotIdx <= 0) {
            return fileName + "." + ext;
        }
        return fileName.substring(0, dotIdx) + "." + ext;
    }

    /**
     * 去除路径中的所有前导斜杠。
     *
     * @param path 原始路径
     * @return 去除前导斜杠后的路径，{@code null} 时返回空字符串
     */
    public static String trimLeadingSlash(String path) {
        if (path == null) {
            return "";
        }
        int index = 0;
        while (index < path.length() && path.charAt(index) == '/') {
            index++;
        }
        return path.substring(index);
    }

    /**
     * 规范化路径：去除尾斜杠、确保以 {@code /} 开头。
     * <ul>
     *   <li>{@code null} 或空字符串 → {@code "/"}</li>
     *   <li>不以 {@code /} 开头 → 自动补前导斜杠</li>
     *   <li>以 {@code /} 结尾（且长度 &gt; 1）→ 去除尾斜杠</li>
     *   <li>不处理内部重复斜杠（保持与原 {@code SyncService.normalizePath} 一致）</li>
     * </ul>
     *
     * @param path 原始路径
     * @return 规范化后的路径
     */
    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.replace('\\', '/');
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}
