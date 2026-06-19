package top.lldwb.alistmediasync.dto.storage;

import lombok.Data;

/**
 * 存储引擎更新 DTO（所有字段可选，仅更新提供的字段）
 *
 * @author AList-Media-Sync
 */
@Data
public class StorageEngineUpdateDTO {

    /** 存储引擎名称 */
    private String name;

    /** AList 服务器基础 URL */
    private String baseUrl;

    /** 登录用户名 */
    private String username;

    /** API 令牌（明文，存储时 AES 加密） */
    private String token;
}
