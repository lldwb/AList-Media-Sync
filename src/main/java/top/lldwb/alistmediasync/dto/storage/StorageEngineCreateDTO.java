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

    /** AList 服务器基础 URL */
    @NotBlank(message = "服务器地址不能为空")
    private String baseUrl;

    /** 登录用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** API 令牌（明文，存储时 AES 加密） */
    @NotBlank(message = "API 令牌不能为空")
    private String token;
}
