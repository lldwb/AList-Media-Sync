package top.lldwb.alistmediasync.dto.sync;

import lombok.Data;
import top.lldwb.alistmediasync.entity.SyncTask;

/**
 * 同步任务更新 DTO（所有字段可选）
 *
 * @author AList-Media-Sync
 */
@Data
public class SyncTaskUpdateDTO {

    private String name;
    private Long sourceEngineId;
    private Long targetEngineId;
    private String sourcePath;
    private String targetPath;
    private SyncTask.SyncMode syncMode;
    private Boolean transcodeEnabled;
    private SyncTask.TargetFormat targetFormat;
    private SyncTask.ConflictStrategy conflictStrategy;
    private String excludePatterns;
    private SyncTask.ScheduleType scheduleType;
    private String cronExpression;
    private Integer intervalSeconds;
}
