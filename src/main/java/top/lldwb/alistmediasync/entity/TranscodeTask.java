package top.lldwb.alistmediasync.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 转码任务实体
 * <p>
 * 记录视频文件转码为 MP3/MP4/FLV 的任务信息，
 * 包括源文件路径、目标格式、进度和临时文件路径。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transcode_task")
public class TranscodeTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的同步任务（可为空，独立创建的转码任务不关联同步任务） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sync_task_id")
    private SyncTask syncTask;

    /** 关联的 Webhook 规则（可为空） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_rule_id")
    private WebhookRule webhookRule;

    /** 源文件在 AList 中的完整路径 */
    @Column(nullable = false, length = 1000)
    private String sourceFilePath;

    /** 目标文件在 AList 中的完整路径 */
    @Column(nullable = false, length = 1000)
    private String targetFilePath;

    /** 源文件格式 */
    @Column(length = 10)
    @Enumerated(EnumType.STRING)
    private SourceFormat sourceFormat;

    /** 目标转码格式 */
    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private TargetFormat targetFormat = TargetFormat.MP3;

    /** 音频比特率（bps，默认 128000 = 128kbps） */
    @Column(nullable = false)
    private Integer bitrate = 128000;

    /**
     * 转码进度（千分比 0-1000）
     * 0 = 未开始，1000 = 完成
     */
    @Column(nullable = false)
    private Integer progress = 0;

    /** 转码状态 */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TranscodeStatus status = TranscodeStatus.PENDING;

    /** 本地临时文件路径（上传失败时保留，用于手动重试） */
    @Column(length = 1000)
    private String tempFilePath;

    /** 错误消息（失败时记录） */
    @Column(length = 2000)
    private String errorMessage;

    /** 源存储引擎 ID（从 AList 下载源文件） */
    private Long sourceEngineId;

    /** 目标存储引擎 ID（上传转码结果） */
    @Column(nullable = false)
    private Long targetEngineId;

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

    /** 源文件格式枚举 */
    public enum SourceFormat {
        FLV, MP4, M4V, UNKNOWN
    }

    /** 目标转码格式枚举 */
    public enum TargetFormat {
        MP3, MP4, FLV
    }

    /** 转码状态枚举 */
    public enum TranscodeStatus {
        /** 待处理 */
        PENDING,
        /** 扫描中（阶段 1） */
        SCANNING,
        /** 转码中（阶段 2） */
        TRANSCODING,
        /** 上传中 */
        UPLOADING,
        /** 完成 */
        COMPLETED,
        /** 失败 */
        FAILED
    }
}
