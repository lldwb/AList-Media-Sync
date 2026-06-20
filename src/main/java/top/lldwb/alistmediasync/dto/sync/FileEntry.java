package top.lldwb.alistmediasync.dto.sync;

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
public record FileEntry(
    String name,
    String path,
    boolean isDirectory,
    long size,
    LocalDateTime modifiedTime
) {}
