package top.lldwb.alistmediasync.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 同步任务实体
 * <p>
 * 定义从源存储引擎到目标存储引擎的文件同步任务。
 * 支持三种同步模式和三种调度方式。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sync_task", indexes = {
    @Index(name = "idx_sync_task_enabled", columnList = "enabled")
})
public class SyncTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 任务名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 源存储引擎 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_engine_id", nullable = false)
    private StorageEngine sourceEngine;

    /** 目标存储引擎 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_engine_id", nullable = false)
    private StorageEngine targetEngine;

    /** 源目录路径（AList 中的完整路径，如 /media/videos） */
    @Column(nullable = false, length = 1000)
    private String sourcePath;

    /** 目标目录路径 */
    @Column(nullable = false, length = 1000)
    private String targetPath;

    /** 同步模式 */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SyncMode syncMode = SyncMode.NEW_ONLY;

    /** 是否启用同步后转码 */
    @Column(nullable = false)
    private Boolean transcodeEnabled = false;

    /** 目标格式（转码时使用，如 MP3/MP4/FLV） */
    @Column(length = 10)
    @Enumerated(EnumType.STRING)
    private TargetFormat targetFormat = TargetFormat.MP3;

    /** 冲突处理策略 */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ConflictStrategy conflictStrategy = ConflictStrategy.SKIP;

    /**
     * 排除模式（换行分隔的 glob 模式）
     * 匹配的文件将在同步扫描时跳过
     */
    @Column(length = 2000)
    private String excludePatterns;

    /** 调度类型 */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ScheduleType scheduleType = ScheduleType.MANUAL;

    /** Cron 表达式（当 scheduleType=CRON 时使用） */
    @Column(length = 100)
    private String cronExpression;

    /** 间隔秒数（当 scheduleType=INTERVAL 时使用） */
    private Integer intervalSeconds;

    /** 是否启用定时调度 */
    @Column(nullable = false)
    private Boolean enabled = false;

    /** 上次执行时间 */
    private LocalDateTime lastExecutedAt;

    /** 创建时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** 同步模式枚举 */
    public enum SyncMode {
        /** 仅同步新增文件 */
        NEW_ONLY,
        /** 完全同步（新增 + 删除目标多余文件） */
        FULL,
        /** 移动模式（同步后删除源文件） */
        MOVE
    }

    /** 调度类型枚举 */
    public enum ScheduleType {
        /** Cron 表达式调度 */
        CRON,
        /** 固定间隔调度 */
        INTERVAL,
        /** 仅手动触发 */
        MANUAL
    }

    /** 冲突处理策略枚举 */
    public enum ConflictStrategy {
        /** 覆盖目标文件 */
        OVERWRITE,
        /** 跳过已存在文件 */
        SKIP,
        /** 自动重命名（添加序号后缀） */
        RENAME
    }

    /** 目标转码格式枚举 */
    public enum TargetFormat {
        MP3, MP4, FLV
    }
}
