package top.lldwb.alistmediasync.sync.dto.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import top.lldwb.alistmediasync.sync.entity.SyncTask;

/**
 * 同步任务创建 DTO
 *
 * @author AList-Media-Sync
 */
@Data
public class SyncTaskCreateDTO {

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /** 源存储引擎 ID */
    @NotNull(message = "源存储引擎不能为空")
    private Long sourceEngineId;

    /** 目标存储引擎 ID */
    @NotNull(message = "目标存储引擎不能为空")
    private Long targetEngineId;

    /** 源目录路径 */
    @NotBlank(message = "源目录路径不能为空")
    private String sourcePath;

    /** 目标目录路径 */
    @NotBlank(message = "目标目录路径不能为空")
    private String targetPath;

    /** 同步模式（默认 NEW_ONLY） */
    private SyncTask.SyncMode syncMode = SyncTask.SyncMode.NEW_ONLY;

    /** 是否启用同步后转码 */
    private Boolean transcodeEnabled = false;

    /** 目标转码格式 */
    private SyncTask.TargetFormat targetFormat = SyncTask.TargetFormat.MP3;

    /** 冲突处理策略 */
    private SyncTask.ConflictStrategy conflictStrategy = SyncTask.ConflictStrategy.SKIP;

    /** 排除模式（换行分隔） */
    private String excludePatterns;

    /** 调度类型 */
    private SyncTask.ScheduleType scheduleType = SyncTask.ScheduleType.MANUAL;

    /** Cron 表达式 */
    private String cronExpression;

    /** 间隔秒数 */
    private Integer intervalSeconds;
}
