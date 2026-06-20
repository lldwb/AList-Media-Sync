package top.lldwb.alistmediasync.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import top.lldwb.alistmediasync.common.entity.CryptoConverter;
import java.time.LocalDateTime;

/**
 * 存储引擎实体
 * <p>
 * 支持多种存储后端（AList 远程存储 / 本地文件系统），通过策略模式动态切换。
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

    /** 引擎类型：ALIST / LOCAL，创建后不可更改 */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EngineType engineType = EngineType.ALIST;

    /** AList 服务器基础 URL（仅 ALIST 类型，如 https://alist.example.com） */
    @Column(length = 500)
    private String baseUrl;

    /**
     * AList API 令牌（仅 ALIST 类型，AES-256-GCM 加密存储）
     */
    @Column(length = 1000)
    @Convert(converter = CryptoConverter.class)
    private String encryptedToken;

    /** 本地文件系统目录路径（仅 LOCAL 类型） */
    @Column(length = 1000)
    private String localPath;

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

    /** 存储引擎类型枚举 */
    public enum EngineType {
        ALIST, LOCAL
    }

    /** 存储引擎状态枚举 */
    public enum EngineStatus {
        ONLINE, OFFLINE, ERROR
    }
}
