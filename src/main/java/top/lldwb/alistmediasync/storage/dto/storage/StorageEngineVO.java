package top.lldwb.alistmediasync.storage.dto.storage;

import lombok.Data;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.time.LocalDateTime;

/**
 * 存储引擎视图 VO（脱敏，不返回 Token 凭据）
 *
 * @author AList-Media-Sync
 */
@Data
public class StorageEngineVO {

    private Long id;
    private String name;
    private String engineType;
    private String baseUrl;
    private String localPath;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 从实体构建 VO（不暴露 Token） */
    public static StorageEngineVO from(StorageEngine entity) {
        StorageEngineVO vo = new StorageEngineVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setEngineType(entity.getEngineType().name());
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setLocalPath(entity.getLocalPath());
        vo.setStatus(entity.getStatus().name());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
