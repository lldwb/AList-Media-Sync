package top.lldwb.alistmediasync.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import top.lldwb.alistmediasync.common.enums.ConflictStrategy;
import top.lldwb.alistmediasync.common.enums.TargetFormat;
import top.lldwb.alistmediasync.sync.entity.SyncTask;

/**
 * 同步任务创建 DTO
 *
 * @author AList-Media-Sync
 */
@Data
@Schema(description = "同步任务创建请求")
public class SyncTaskCreateDTO {

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Schema(description = "任务名称", example = "每日音乐同步", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /** 源存储引擎 ID */
    @NotNull(message = "源存储引擎不能为空")
    @Schema(description = "源存储引擎 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceEngineId;

    /** 目标存储引擎 ID */
    @NotNull(message = "目标存储引擎不能为空")
    @Schema(description = "目标存储引擎 ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetEngineId;

    /** 源目录路径 */
    @NotBlank(message = "源目录路径不能为空")
    @Schema(description = "源目录路径", example = "/music/source", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourcePath;

    /** 目标目录路径 */
    @NotBlank(message = "目标目录路径不能为空")
    @Schema(description = "目标目录路径", example = "/music/target", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetPath;

    /** 同步模式（默认 NEW_ONLY） */
    @Schema(description = "同步模式（默认 NEW_ONLY）", example = "NEW_ONLY")
    private SyncTask.SyncMode syncMode = SyncTask.SyncMode.NEW_ONLY;

    /** 是否启用同步后转码 */
    @Schema(description = "是否启用同步后转码", example = "false")
    private Boolean transcodeEnabled = false;

    /** 目标转码格式 */
    @Schema(description = "目标转码格式", example = "MP3")
    private TargetFormat targetFormat = TargetFormat.MP3;

    /** 冲突处理策略 */
    @Schema(description = "冲突处理策略", example = "SKIP")
    private ConflictStrategy conflictStrategy = ConflictStrategy.SKIP;

    /** 排除模式（换行分隔） */
    @Schema(description = "排除模式（换行分隔）", example = "*.tmp\n*.bak")
    private String excludePatterns;

    /** 调度类型 */
    @Schema(description = "调度类型", example = "MANUAL")
    private SyncTask.ScheduleType scheduleType = SyncTask.ScheduleType.MANUAL;

    /** Cron 表达式 */
    @Schema(description = "Cron 表达式", example = "0 0 2 * * ?")
    private String cronExpression;

    /** 间隔秒数 */
    @Schema(description = "间隔秒数", example = "3600")
    private Integer intervalSeconds;
}
