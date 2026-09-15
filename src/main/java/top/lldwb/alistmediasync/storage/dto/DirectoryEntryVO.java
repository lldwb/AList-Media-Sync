package top.lldwb.alistmediasync.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 目录条目 VO（树状目录浏览组件使用）
 *
 * @param name        目录名称
 * @param path        完整路径
 * @param hasChildren 是否包含子目录
 * @author AList-Media-Sync
 */
@Schema(description = "目录条目视图（树状目录浏览组件使用）")
public record DirectoryEntryVO(
    @Schema(description = "目录名称", example = "music", requiredMode = Schema.RequiredMode.REQUIRED)
    String name,

    @Schema(description = "完整路径", example = "/data/media/music", requiredMode = Schema.RequiredMode.REQUIRED)
    String path,

    @Schema(description = "是否包含子目录", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean hasChildren
) {}
