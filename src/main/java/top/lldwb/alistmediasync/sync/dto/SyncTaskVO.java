package top.lldwb.alistmediasync.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.lldwb.alistmediasync.sync.entity.SyncTask;

import java.time.LocalDateTime;

/**
 * 同步任务视图 VO
 *
 * @author AList-Media-Sync
 */
@Data
@Schema(description = "同步任务视图")
public class SyncTaskVO {

    @Schema(description = "任务 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(description = "任务名称", example = "每日音乐同步", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(description = "源存储引擎 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceEngineId;
    @Schema(description = "源存储引擎名称", example = "主 AList 服务器", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceEngineName;
    @Schema(description = "目标存储引擎 ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetEngineId;
    @Schema(description = "目标存储引擎名称", example = "本地存储", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetEngineName;
    @Schema(description = "源目录路径", example = "/music/source", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourcePath;
    @Schema(description = "目标目录路径", example = "/music/target", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetPath;
    @Schema(description = "同步模式", example = "NEW_ONLY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String syncMode;
    @Schema(description = "是否启用同步后转码", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean transcodeEnabled;
    @Schema(description = "目标转码格式", example = "MP3")
    private String targetFormat;
    @Schema(description = "冲突处理策略", example = "SKIP", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conflictStrategy;
    @Schema(description = "排除模式（换行分隔）", example = "*.tmp")
    private String excludePatterns;
    @Schema(description = "调度类型", example = "MANUAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String scheduleType;
    @Schema(description = "Cron 表达式", example = "0 0 2 * * ?")
    private String cronExpression;
    @Schema(description = "间隔秒数", example = "3600")
    private Integer intervalSeconds;
    @Schema(description = "是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;
    @Schema(description = "最后执行时间", example = "2026-07-02T02:00:00")
    private LocalDateTime lastExecutedAt;
    @Schema(description = "创建时间", example = "2026-07-01T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;

    /** 从实体构建 VO */
    public static SyncTaskVO from(SyncTask entity) {
        SyncTaskVO vo = new SyncTaskVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setSourceEngineId(entity.getSourceEngine().getId());
        vo.setSourceEngineName(entity.getSourceEngine().getName());
        vo.setTargetEngineId(entity.getTargetEngine().getId());
        vo.setTargetEngineName(entity.getTargetEngine().getName());
        vo.setSourcePath(entity.getSourcePath());
        vo.setTargetPath(entity.getTargetPath());
        vo.setSyncMode(entity.getSyncMode().name());
        vo.setTranscodeEnabled(entity.getTranscodeEnabled());
        vo.setTargetFormat(entity.getTargetFormat() != null ? entity.getTargetFormat().name() : null);
        vo.setConflictStrategy(entity.getConflictStrategy().name());
        vo.setExcludePatterns(entity.getExcludePatterns());
        vo.setScheduleType(entity.getScheduleType().name());
        vo.setCronExpression(entity.getCronExpression());
        vo.setIntervalSeconds(entity.getIntervalSeconds());
        vo.setEnabled(entity.getEnabled());
        vo.setLastExecutedAt(entity.getLastExecutedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
