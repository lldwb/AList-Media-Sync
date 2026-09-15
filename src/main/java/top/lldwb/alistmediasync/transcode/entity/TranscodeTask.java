package top.lldwb.alistmediasync.transcode.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import top.lldwb.alistmediasync.common.enums.TargetFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 转码任务实体
 * <p>
 * 记录视频文件转码为 MP3/MP4/FLV 的任务信息，
 * 采用三步流程（下载→转码→上传）和 8 状态模型。
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
    @Column(name = "sync_task_id")
    private Long syncTaskId;

    /** 关联的 Webhook 规则 ID（可为空） */
    @Column(name = "webhook_rule_id")
    private Long webhookRuleId;

    /** 源文件在源存储引擎中的完整路径 */
    @Column(nullable = false, length = 1000)
    private String sourceFilePath;

    /** 目标文件在目标存储引擎中的完整路径 */
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

    /** 音频比特率（bps，null 时使用系统默认码率 128kbps） */
    private Integer bitrate;

    /**
     * 转码进度（千分比 0-1000）
     * 0 = 未开始，1000 = 完成
     */
    @Column(nullable = false)
    private Integer progress = 0;

    /** 转码状态（8 状态模型） */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TranscodeStatus status = TranscodeStatus.PENDING;

    /** 本地临时转码输出文件路径（上传失败时保留，用于手动重试） */
    @Column(length = 1000)
    private String tempFilePath;

    /** 已下载源文件的临时路径（供转码/上传失败重试使用） */
    @Column(length = 1000)
    private String tempSourcePath;

    /** 错误消息（失败时记录） */
    @Column(length = 2000)
    private String errorMessage;

    /** 自动重试已执行次数（默认 0，每次自动重试递增） */
    @Column(nullable = false)
    private int retryCount = 0;

    /** 源存储引擎 ID（下载源文件） */
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

    /**
     * 转码状态枚举（8 状态模型）
     * <p>
     * 三步流程：下载 → 转码 → 上传，每步可独立失败和重试。
     * </p>
     */
    public enum TranscodeStatus {
        /** 待处理 */
        PENDING(0),
        /** 下载中 */
        DOWNLOADING(1),
        /** 下载失败 */
        DOWNLOAD_FAILED(2),
        /** 转码中 */
        TRANSCODING(3),
        /** 转码失败 */
        TRANSCODE_FAILED(4),
        /** 上传中 */
        UPLOADING(5),
        /** 上传失败 */
        UPLOAD_FAILED(6),
        /** 完成 */
        COMPLETED(7),
        /** 编排级失败（扫描/收集阶段异常或全部文件失败，非单步失败） */
        FAILED(8);

        /**
         * 反馈「失败状态」集合（下载/转码/上传三个失败阶段，不含编排级 FAILED）
         * <p>
         * 用于批量删除失败任务、批量重试前筛选、编排级失败判定等场景，替代各处重复定义的等价集合。
         * </p>
         */
        public static final List<TranscodeStatus> FAILED_STATUSES = List.of(
            DOWNLOAD_FAILED, TRANSCODE_FAILED, UPLOAD_FAILED);

        /**
         * 反馈「可重试状态」集合（三个失败阶段 + 编排级 FAILED）
         * <p>
         * 比 {@link #FAILED_STATUSES} 多一个编排级 {@link #FAILED}：编排级失败同样允许重试，
         * 但按「下载/转码/上传失败」语义批量清理任务时不纳入。
         * </p>
         */
        public static final List<TranscodeStatus> RETRYABLE_STATUSES = List.of(
            DOWNLOAD_FAILED, TRANSCODE_FAILED, UPLOAD_FAILED, FAILED);

        private final int code;

        TranscodeStatus(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        /**
         * 是否为失败状态（三个失败阶段，语义与 {@link #FAILED_STATUSES} 一致）
         *
         * @return 命中失败状态集合时为 {@code true}
         */
        public boolean isFailure() {
            return FAILED_STATUSES.contains(this);
        }

        /**
         * 是否可重试（语义与 {@link #RETRYABLE_STATUSES} 一致）
         *
         * @return 命中可重试状态集合时为 {@code true}
         */
        public boolean isRetryable() {
            return RETRYABLE_STATUSES.contains(this);
        }
    }
}
