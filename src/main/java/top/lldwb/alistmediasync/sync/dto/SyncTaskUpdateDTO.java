package top.lldwb.alistmediasync.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.lldwb.alistmediasync.common.enums.ConflictStrategy;
import top.lldwb.alistmediasync.common.enums.TargetFormat;
import top.lldwb.alistmediasync.sync.entity.SyncTask;

/**
 * 同步任务更新 DTO（所有字段可选）
 *
 * @author AList-Media-Sync
 */
@Data
@Schema(description = "同步任务更新请求（所有字段可选）")
public class SyncTaskUpdateDTO {

    @Schema(description = "任务名称", example = "每日音乐同步")
    private String name;
    @Schema(description = "源存储引擎 ID", example = "1")
    private Long sourceEngineId;
    @Schema(description = "目标存储引擎 ID", example = "2")
    private Long targetEngineId;
    @Schema(description = "源目录路径", example = "/music/source")
    private String sourcePath;
    @Schema(description = "目标目录路径", example = "/music/target")
    private String targetPath;
    @Schema(description = "同步模式", example = "NEW_ONLY")
    private SyncTask.SyncMode syncMode;
    @Schema(description = "是否启用同步后转码", example = "true")
    private Boolean transcodeEnabled;
    @Schema(description = "目标转码格式", example = "MP3")
    private TargetFormat targetFormat;
    @Schema(description = "冲突处理策略", example = "OVERWRITE")
    private ConflictStrategy conflictStrategy;
    @Schema(description = "排除模式（换行分隔）", example = "*.tmp")
    private String excludePatterns;
    @Schema(description = "调度类型", example = "CRON")
    private SyncTask.ScheduleType scheduleType;
    @Schema(description = "Cron 表达式", example = "0 0 2 * * ?")
    private String cronExpression;
    @Schema(description = "间隔秒数", example = "3600")
    private Integer intervalSeconds;
}
