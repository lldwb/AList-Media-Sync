package top.lldwb.alistmediasync.dto.sync;

/**
 * 目录条目 VO（树状目录浏览组件使用）
 *
 * @param name        目录名称
 * @param path        完整路径
 * @param hasChildren 是否包含子目录
 * @author AList-Media-Sync
 */
public record DirectoryEntryVO(
    String name,
    String path,
    boolean hasChildren
) {}
