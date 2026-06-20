package top.lldwb.alistmediasync.sync.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.webhook.entity.WebhookEvent;
import java.time.LocalDateTime;

/**
 * 任务执行记录实体
 * <p>
 * 记录每次同步/转码/Webhook 处理的执行详情，
 * 包括状态、文件统计和失败原因。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "task_execution", indexes = {
    @Index(name = "idx_task_execution_status_type", columnList = "status, taskType"),
    @Index(name = "idx_task_execution_created", columnList = "createdAt")
})
public class TaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的同步任务（可为空，独立转码/Webhook 执行时不关联同步任务） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sync_task_id")
    private SyncTask syncTask;

    /** 关联的转码任务（可为空） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transcode_task_id")
    private TranscodeTask transcodeTask;

    /** 关联的 Webhook 事件（可为空） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_event_id")
    private WebhookEvent webhookEvent;

    /** 任务类型 */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TaskType taskType;

    /** 执行开始时间 */
    @Column(nullable = false)
    private LocalDateTime startTime;

    /** 执行结束时间 */
    private LocalDateTime endTime;

    /** 执行状态 */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ExecutionStatus status;

    /** 处理的文件总数 */
    private Integer totalFiles;

    /** 成功处理的文件数 */
    private Integer successFiles;

    /** 失败的文件数 */
    private Integer failedFiles;

    /**
     * 失败详情（JSON 格式）
     * 记录每个失败文件的路径和原因
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String failureDetails;

    /** 创建时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** 任务类型枚举 */
    public enum TaskType {
        /** 同步任务 */
        SYNC,
        /** 转码任务 */
        TRANSCODE,
        /** Webhook 触发的任务 */
        WEBHOOK
    }

    /** 执行状态枚举 */
    public enum ExecutionStatus {
        /** 运行中 */
        RUNNING,
        /** 成功完成 */
        SUCCESS,
        /** 失败 */
        FAILED,
        /** 部分成功（部分文件处理失败） */
        PARTIAL_SUCCESS,
        /** 中断（系统崩溃/重启导致） */
        INTERRUPTED
    }
}
