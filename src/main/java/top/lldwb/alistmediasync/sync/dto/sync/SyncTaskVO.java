package top.lldwb.alistmediasync.sync.dto.sync;

import lombok.Data;
import top.lldwb.alistmediasync.sync.entity.SyncTask;

import java.time.LocalDateTime;

/**
 * 同步任务视图 VO
 *
 * @author AList-Media-Sync
 */
@Data
public class SyncTaskVO {

    private Long id;
    private String name;
    private Long sourceEngineId;
    private String sourceEngineName;
    private Long targetEngineId;
    private String targetEngineName;
    private String sourcePath;
    private String targetPath;
    private String syncMode;
    private Boolean transcodeEnabled;
    private String targetFormat;
    private String conflictStrategy;
    private String excludePatterns;
    private String scheduleType;
    private String cronExpression;
    private Integer intervalSeconds;
    private Boolean enabled;
    private LocalDateTime lastExecutedAt;
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
