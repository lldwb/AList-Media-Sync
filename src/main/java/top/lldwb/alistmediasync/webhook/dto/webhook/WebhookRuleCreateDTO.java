package top.lldwb.alistmediasync.webhook.dto.webhook;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule;

/**
 * Webhook 规则创建 DTO
 *
 * @author AList-Media-Sync
 */
@Data
@Schema(description = "Webhook 规则创建请求")
public class WebhookRuleCreateDTO {

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Schema(description = "规则名称", example = "自动同步录播", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /** 触发事件类型 */
    @NotNull(message = "触发事件类型不能为空")
    @Schema(description = "触发事件类型", example = "RECORDING_COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private WebhookRule.WebhookEventType triggerEventType;

    /** 房间号过滤（可选） */
    @Schema(description = "房间号过滤（可选）", example = "12345678")
    private Long roomIdFilter;

    /** 触发后的操作 */
    @NotNull(message = "执行操作不能为空")
    @Schema(description = "触发后的操作", example = "BOTH", requiredMode = Schema.RequiredMode.REQUIRED)
    private WebhookRule.RuleAction action = WebhookRule.RuleAction.BOTH;

    /** 录播存储引擎 ID（源端，TRANSCODE_ONLY/BOTH 时必填） */
    @Min(value = 1, message = "录播存储引擎 ID 必须为正整数")
    @Schema(description = "录播存储引擎 ID（源端，TRANSCODE_ONLY/BOTH 时必填）", example = "1")
    private Long recordingEngineId;

    /** 录播文件路径（源端路径） */
    @Schema(description = "录播文件路径（源端路径）", example = "/recordings")
    private String recordingPath;

    /** 目标存储引擎 ID（SYNC_ONLY / BOTH 时必填） */
    @Min(value = 1, message = "目标存储引擎 ID 必须为正整数")
    @Schema(description = "目标存储引擎 ID（SYNC_ONLY / BOTH 时必填）", example = "2")
    private Long targetEngineId;

    /** 目标文件路径（SYNC_ONLY / BOTH 时必填） */
    @Schema(description = "目标文件路径（SYNC_ONLY / BOTH 时必填）", example = "/media/recordings")
    private String targetFilePath;
}
