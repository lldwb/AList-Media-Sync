package top.lldwb.alistmediasync.storage.dto.storage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 存储引擎更新 DTO（所有字段可选，仅更新提供的字段，engineType 创建后不可更改）
 *
 * @author AList-Media-Sync
 */
@Data
@Schema(description = "存储引擎更新请求")
public class StorageEngineUpdateDTO {

    /** 存储引擎名称 */
    @Schema(description = "存储引擎名称", example = "主 AList 服务器")
    private String name;

    /** AList 服务器基础 URL */
    @Schema(description = "AList 服务器基础 URL", example = "https://alist.example.com")
    private String baseUrl;

    /** API 令牌（明文，存储时 AES 加密） */
    @Schema(description = "API 令牌（明文，存储时 AES 加密）", example = "alskdjflasdkfj123456")
    private String token;

    /** 本地文件系统目录路径 */
    @Schema(description = "本地文件系统目录路径", example = "/data/media")
    private String localPath;
}
