package top.lldwb.alistmediasync.common.util;

import java.util.Map;

/**
 * 文件魔数检测工具
 * <p>
 * 通过读取文件头部字节（魔数）判断视频文件的实际格式。
 * 比基于扩展名的判断更准确，避免文件后缀名欺骗。
 * </p>
 *
 * @author AList-Media-Sync
 */
public final class MagicBytesDetector {

    private MagicBytesDetector() {}

    /**
     * 已知视频格式的魔数签名表
     */
    private static final Map<String, byte[]> MAGIC_SIGNATURES = Map.of(
        "FLV", new byte[]{0x46, 0x4C, 0x56}, // "FLV"
        "MP4", new byte[]{0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70}, // ....ftyp
        "M4V", new byte[]{0x00, 0x00, 0x00, 0x1C, 0x66, 0x74, 0x79, 0x70}  // ....ftyp variant
    );

    /**
     * 检测文件格式
     *
     * @param headerBytes 文件头部字节（至少 16 字节）
     * @return 检测到的格式枚举，未匹配返回 "UNKNOWN"
     */
    public static String detect(byte[] headerBytes) {
        if (headerBytes == null || headerBytes.length < 8) {
            return "UNKNOWN";
        }

        // FLV 魔数检测（前 3 字节）
        if (matchesSignature(headerBytes, MAGIC_SIGNATURES.get("FLV"))) {
            return "FLV";
        }

        // MP4/M4V ftyp box 检测（前 8 字节含 "ftyp" 标识）
        byte[] ftypCheck = new byte[]{0x66, 0x74, 0x79, 0x70}; // "ftyp"
        if (headerBytes.length >= 8) {
            for (int i = 0; i <= headerBytes.length - 4; i++) {
                boolean match = true;
                for (int j = 0; j < 4; j++) {
                    if (headerBytes[i + j] != ftypCheck[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return "MP4"; // MP4 和 M4V 都可视为 MP4 处理
                }
            }
        }

        return "UNKNOWN";
    }

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

    private static boolean matchesSignature(byte[] header, byte[] signature) {
        if (header.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) return false;
        }
        return true;
    }
}
