package top.lldwb.alistmediasync.dto.storage;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 存储引擎创建 DTO
 *
 * @author AList-Media-Sync
 */
@Data
public class StorageEngineCreateDTO {

    /** 存储引擎名称 */
    @NotBlank(message = "存储引擎名称不能为空")
    private String name;

    /** 引擎类型（ALIST / LOCAL），创建后不可更改 */
    @NotBlank(message = "引擎类型不能为空")
    private String engineType;

    /** AList 服务器基础 URL（仅 ALIST 类型必填） */
    private String baseUrl;

    /** API 令牌（明文，存储时 AES 加密，仅 ALIST 类型必填） */
    private String token;

    /** 本地文件系统目录路径（仅 LOCAL 类型必填） */
    private String localPath;
}
