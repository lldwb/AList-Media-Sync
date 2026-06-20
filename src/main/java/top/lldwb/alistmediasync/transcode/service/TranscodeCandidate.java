package top.lldwb.alistmediasync.transcode.service;

import top.lldwb.alistmediasync.storage.entity.StorageEngine;

/**
 * 转码候选文件（DTO）
 *
 * @param name         文件名
 * @param fullPath     源完整路径
 * @param targetPath   目标路径
 * @param format       视频格式（由 MagicBytes 检测）
 * @param size         文件大小（字节）
 * @param sourceEngine 关联的源存储引擎（扫描阶段设置）
 */
public record TranscodeCandidate(
        String name,
        String fullPath,
        String targetPath,
        String format,
        long size,
        StorageEngine sourceEngine
) {
}
