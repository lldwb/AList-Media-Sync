package top.lldwb.alistmediasync.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Webhook 事件记录实体
 * <p>
 * 记录所有从录播姬接收到的 Webhook v2 事件。
 * EventId 作为唯一键用于去重。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "webhook_event", indexes = {
    @Index(name = "idx_webhook_event_event_id", columnList = "eventId", unique = true),
    @Index(name = "idx_webhook_event_type", columnList = "eventType"),
    @Index(name = "idx_webhook_event_created", columnList = "createdAt")
})
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 事件唯一标识（录播姬生成，用于去重） */
    @Column(nullable = false, unique = true, length = 100)
    private String eventId;

    /** 事件类型 */
    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private WebhookEventType eventType;

    /** 事件发生的时间戳（录播姬提供） */
    @Column(nullable = false)
    private LocalDateTime eventTimestamp;

    /** 会话 ID（录播姬录制会话） */
    @Column(length = 100)
    private String sessionId;

    /** 房间号 */
    private Long roomId;

    /** 录制文件的相对路径 */
    @Column(length = 1000)
    private String relativePath;

    /** 录制文件名 */
    @Column(length = 500)
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 录制时长（毫秒） */
    private Long duration;

    /**
     * 原始事件数据（JSON 格式，TEXT 列存储）
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String rawData;

    /** 事件处理状态 */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EventStatus status = EventStatus.PENDING;

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

    /** Webhook 事件类型枚举 */
    public enum WebhookEventType {
        FILE_OPENED,
        FILE_CLOSED,
        FILE_RENAMED,
        SESSION_STARTED,
        SESSION_ENDED,
        SPACE_FULL,
        OTHER
    }

    /** 事件处理状态枚举 */
    public enum EventStatus {
        /** 待处理 */
        PENDING,
        /** 处理中 */
        PROCESSING,
        /** 处理完成 */
        COMPLETED,
        /** 处理失败 */
        FAILED,
        /** 重复事件（EventId 已存在） */
        DUPLICATE
    }
}
