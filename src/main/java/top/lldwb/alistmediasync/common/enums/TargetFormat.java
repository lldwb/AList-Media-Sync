package top.lldwb.alistmediasync.common.enums;

/**
 * 目标转码格式枚举
 * <p>
 * 定义转码输出的目标格式。
 * 原为 {@code SyncTask.TargetFormat} 与 {@code TranscodeTask.TargetFormat} 两处重复定义
 * （常量集与顺序完全一致），因被 sync/transcode/webhook 等多个模块共享，
 * 合并下沉为跨模块共享业务枚举。
 * </p>
 *
 * @author AList-Media-Sync
 */
public enum TargetFormat {
    MP3, MP4, FLV
}
