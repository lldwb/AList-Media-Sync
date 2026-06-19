package top.lldwb.alistmediasync.dto.webhook;

import lombok.Data;
import top.lldwb.alistmediasync.entity.WebhookRule;

import java.time.LocalDateTime;

/**
 * Webhook 规则视图 VO
 *
 * @author AList-Media-Sync
 */
@Data
public class WebhookRuleVO {

    private Long id;
    private String name;
    private String triggerEventType;
    private Long roomIdFilter;
    private String action;
    private Long targetEngineId;
    private String targetPath;
    private Boolean enabled;
    private LocalDateTime createdAt;

    public static WebhookRuleVO from(WebhookRule entity) {
        WebhookRuleVO vo = new WebhookRuleVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setTriggerEventType(entity.getTriggerEventType().name());
        vo.setRoomIdFilter(entity.getRoomIdFilter());
        vo.setAction(entity.getAction().name());
        vo.setTargetEngineId(entity.getTargetEngine().getId());
        vo.setTargetPath(entity.getTargetPath());
        vo.setEnabled(entity.getEnabled());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
