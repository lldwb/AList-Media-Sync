package top.lldwb.alistmediasync.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Webhook 处理规则实体
 * <p>
 * 定义当录播姬 Webhook 事件到达时，系统应执行的操作。
 * 支持按事件类型和房间号过滤，可触发同步、转码或两者。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "webhook_rule", indexes = {
    @Index(name = "idx_webhook_rule_enabled", columnList = "enabled")
})
public class WebhookRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 规则名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 触发事件类型 */
    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private WebhookEventType triggerEventType;

    /**
     * 房间号过滤（可为空，为空表示匹配所有房间）
     */
    private Long roomIdFilter;

    /** 触发后的操作 */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RuleAction action = RuleAction.BOTH;

    /** 同步/转码的目标存储引擎 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_engine_id", nullable = false)
    private StorageEngine targetEngine;

    /** 目标目录路径 */
    @Column(nullable = false, length = 1000)
    private String targetPath;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean enabled = true;

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

    /** Webhook 事件类型枚举（录播姬 v2 协议） */
    public enum WebhookEventType {
        FILE_OPENED,
        FILE_CLOSED,
        FILE_RENAMED,
        SESSION_STARTED,
        SESSION_ENDED,
        SPACE_FULL,
        OTHER
    }

    /** 规则操作枚举 */
    public enum RuleAction {
        /** 仅同步 */
        SYNC_ONLY,
        /** 仅转码 */
        TRANSCODE_ONLY,
        /** 同步 + 转码 */
        BOTH
    }
}
