package top.lldwb.alistmediasync.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 磁盘空间检查工具
 * <p>
 * 在转码开始前检查临时目录的可用磁盘空间，
 * 预估输出文件大小的 1.5 倍作为安全阈值。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
public final class DiskSpaceChecker {

    private DiskSpaceChecker() {}

    /**
     * 检查磁盘空间是否充足
     *
     * @param tempDir              临时目录
     * @param estimatedOutputSize  预估输出文件大小（字节）
     * @return 空间充足时返回 true
     * @throws InsufficientDiskSpaceException 空间不足时抛出
     */
    public static boolean checkSufficient(Path tempDir, long estimatedOutputSize) {
        // 预估为 0（如未知时长）→ 跳过检查
        if (estimatedOutputSize <= 0) {
            log.debug("预估输出大小为 0，跳过磁盘空间检查");
            return true;
        }

        // 防止溢出：预估大小超过 Long.MAX_VALUE / 2 时使用 Long.MAX_VALUE
        long requiredSpace;
        if (estimatedOutputSize > Long.MAX_VALUE / 2) {
            requiredSpace = Long.MAX_VALUE;
        } else {
            requiredSpace = (long) (estimatedOutputSize * 1.5);
        }

        try {
            long usableSpace = Files.getFileStore(tempDir).getUsableSpace();

            // getUsableSpace() 返回 0 的兼容处理（某些文件系统/JVM 实现）
            if (usableSpace <= 0) {
                log.warn("无法获取可用磁盘空间（getUsableSpace() 返回 {}），跳过空间检查", usableSpace);
                return true;
            }

            if (usableSpace < requiredSpace) {
                String msg = String.format(
                    "临时目录磁盘空间不足：需要至少 %d 字节（%.2f MB），可用 %d 字节（%.2f MB）",
                    requiredSpace, requiredSpace / 1048576.0,
                    usableSpace, usableSpace / 1048576.0);
                throw new InsufficientDiskSpaceException(msg);
            }

            log.debug("磁盘空间检查通过：需要 {} MB，可用 {} MB",
                requiredSpace / 1048576, usableSpace / 1048576);
            return true;
        } catch (IOException e) {
            log.warn("磁盘空间检查失败：{}，跳过检查", e.getMessage());
            return true; // 测量失败不阻塞转码
        }
    }

    /**
     * 预估输出文件大小
     *
     * @param sourceDurationMs 源文件时长（毫秒）
     * @param targetBitrate    目标比特率（bps）
     * @return 预估大小（字节）
     */
    public static long estimateOutputSize(long sourceDurationMs, int targetBitrate) {
        if (sourceDurationMs <= 0) return 0;
        return (sourceDurationMs / 1000) * (targetBitrate / 8);
    }

    /**
     * 磁盘空间不足异常
     */
    public static class InsufficientDiskSpaceException extends RuntimeException {
        public InsufficientDiskSpaceException(String message) {
            super(message);
        }
    }
}
