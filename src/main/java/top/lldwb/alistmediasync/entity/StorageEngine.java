package top.lldwb.alistmediasync.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 存储引擎实体
 * <p>
 * 代表一个 AList 服务器实例的连接信息。
 * Token 使用 AES-256-GCM 加密存储。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "storage_engine")
public class StorageEngine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 存储引擎名称（用户自定义，便于识别） */
    @Column(nullable = false, length = 100)
    private String name;

    /** AList 服务器基础 URL（如 https://alist.example.com） */
    @Column(nullable = false, length = 500)
    private String baseUrl;

    /** AList 登录用户名 */
    @Column(nullable = false, length = 100)
    private String username;

    /**
     * AList API 令牌（AES-256-GCM 加密存储）
     */
    @Column(nullable = false, length = 1000)
    @Convert(converter = CryptoConverter.class)
    private String encryptedToken;

    /** 引擎状态：ONLINE / OFFLINE / ERROR */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EngineStatus status = EngineStatus.OFFLINE;

    /** 创建时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 乐观锁版本号 */
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

    /** 存储引擎状态枚举 */
    public enum EngineStatus {
        ONLINE, OFFLINE, ERROR
    }
}
