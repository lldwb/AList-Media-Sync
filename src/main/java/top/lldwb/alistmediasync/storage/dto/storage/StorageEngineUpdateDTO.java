package top.lldwb.alistmediasync.storage.dto.storage;

import lombok.Data;

/**
 * 存储引擎更新 DTO（所有字段可选，仅更新提供的字段，engineType 创建后不可更改）
 *
 * @author AList-Media-Sync
 */
@Data
public class StorageEngineUpdateDTO {

    /** 存储引擎名称 */
    private String name;

    /** AList 服务器基础 URL */
    private String baseUrl;

    /** API 令牌（明文，存储时 AES 加密） */
    private String token;

    /** 本地文件系统目录路径 */
    private String localPath;
}
