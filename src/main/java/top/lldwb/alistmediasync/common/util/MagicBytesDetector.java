package top.lldwb.alistmediasync.common.util;

/**
 * 文件格式检测工具
 * <p>
 * 通过文件扩展名判断视频文件的实际格式（{@link #detectByExtension(String)}）。
 * </p>
 *
 * @author AList-Media-Sync
 */
public final class MagicBytesDetector {

    private MagicBytesDetector() {}

    /**
     * 从文件扩展名猜测格式（回退方案）
     */
    public static String detectByExtension(String fileName) {
        if (fileName == null) return "UNKNOWN";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".flv")) return "FLV";
        if (lower.endsWith(".mp4")) return "MP4";
        if (lower.endsWith(".m4v")) return "M4V";
        return "UNKNOWN";
    }
}
