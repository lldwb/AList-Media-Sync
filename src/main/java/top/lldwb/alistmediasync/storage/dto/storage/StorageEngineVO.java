package top.lldwb.alistmediasync.storage.dto.storage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.time.LocalDateTime;

/**
 * 存储引擎视图 VO（脱敏，不返回 Token 凭据）
 *
 * @author AList-Media-Sync
 */
@Data
@Schema(description = "存储引擎视图（脱敏，不返回 Token 凭据）")
public class StorageEngineVO {

    @Schema(description = "存储引擎 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(description = "存储引擎名称", example = "主 AList 服务器", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(description = "引擎类型", example = "ALIST", requiredMode = Schema.RequiredMode.REQUIRED)
    private String engineType;
    @Schema(description = "AList 服务器基础 URL", example = "https://alist.example.com")
    private String baseUrl;
    @Schema(description = "本地文件系统目录路径", example = "/data/media")
    private String localPath;
    @Schema(description = "引擎状态", example = "ONLINE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    @Schema(description = "创建时间", example = "2026-07-01T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
    @Schema(description = "更新时间", example = "2026-07-02T10:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
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
