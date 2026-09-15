package top.lldwb.alistmediasync.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 文件条目 DTO（策略模式统一文件信息结构，非持久化实体）
 *
 * @param name         文件/目录名称
 * @param path         完整路径
 * @param isDirectory  是否为目录
 * @param size         文件大小（字节，目录为 0）
 * @param modifiedTime 最后修改时间
 * @author AList-Media-Sync
 */
@Schema(description = "文件条目（策略模式统一文件信息结构）")
public record FileEntry(
    @Schema(description = "文件/目录名称", example = "video.mp4") String name,
    @Schema(description = "完整路径", example = "/media/2026/video.mp4") String path,
    @Schema(description = "是否为目录", example = "false") boolean isDirectory,
    @Schema(description = "文件大小（字节，目录为 0）", example = "1048576") long size,
    @Schema(description = "最后修改时间", example = "2026-07-02T10:30:00") LocalDateTime modifiedTime
) {}
