package top.lldwb.alistmediasync.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 存储引擎创建 DTO
 *
 * @author AList-Media-Sync
 */
@Data
@Schema(description = "存储引擎创建请求")
public class StorageEngineCreateDTO {

    /** 存储引擎名称 */
    @NotBlank(message = "存储引擎名称不能为空")
    @Schema(description = "存储引擎名称", example = "主 AList 服务器", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /** 引擎类型（ALIST / LOCAL），创建后不可更改 */
    @NotBlank(message = "引擎类型不能为空")
    @Schema(description = "引擎类型（ALIST / LOCAL），创建后不可更改", example = "ALIST", requiredMode = Schema.RequiredMode.REQUIRED)
    private String engineType;

    /** AList 服务器基础 URL（仅 ALIST 类型必填） */
    @Schema(description = "AList 服务器基础 URL（仅 ALIST 类型必填）", example = "https://alist.example.com")
    private String baseUrl;

    /** API 令牌（明文，存储时 AES 加密，仅 ALIST 类型必填） */
    @Schema(description = "API 令牌（明文，存储时 AES 加密，仅 ALIST 类型必填）", example = "alskdjflasdkfj123456")
    private String token;

    /** 本地文件系统目录路径（仅 LOCAL 类型必填） */
    @Schema(description = "本地文件系统目录路径（仅 LOCAL 类型必填）", example = "/data/media")
    private String localPath;
}
