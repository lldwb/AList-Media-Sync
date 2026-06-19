package top.lldwb.alistmediasync.dto.webhook;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import top.lldwb.alistmediasync.entity.WebhookRule;

/**
 * Webhook 规则创建 DTO
 *
 * @author AList-Media-Sync
 */
@Data
public class WebhookRuleCreateDTO {

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    private String name;

    /** 触发事件类型 */
    @NotNull(message = "触发事件类型不能为空")
    private WebhookRule.WebhookEventType triggerEventType;

    /** 房间号过滤（可选） */
    private Long roomIdFilter;

    /** 触发后的操作 */
    @NotNull(message = "执行操作不能为空")
    private WebhookRule.RuleAction action = WebhookRule.RuleAction.BOTH;

    /** 目标存储引擎 ID */
    @NotNull(message = "目标存储引擎不能为空")
    @Min(value = 1, message = "目标存储引擎 ID 必须为正整数")
    private Long targetEngineId;

    /** 目标目录路径 */
    @NotBlank(message = "目标目录路径不能为空")
    private String targetPath;
}
