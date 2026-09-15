package top.lldwb.alistmediasync.common.enums;

/**
 * 冲突处理策略枚举
 * <p>
 * 定义同步任务在目标文件已存在时的处理方式。
 * 原为 {@code SyncTask.ConflictStrategy} 内嵌枚举，因被 sync/transcode/webhook
 * 等多个模块共享，下沉为跨模块共享业务枚举。
 * </p>
 *
 * @author AList-Media-Sync
 */
public enum ConflictStrategy {

    /** 覆盖目标文件 */
    OVERWRITE,

    /** 跳过已存在文件 */
    SKIP,

    /** 自动重命名（添加序号后缀） */
    RENAME
}
