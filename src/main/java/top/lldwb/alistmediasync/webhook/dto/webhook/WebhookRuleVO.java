package top.lldwb.alistmediasync.webhook.dto.webhook;

import lombok.Data;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule;

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
    private Long recordingEngineId;
    private String recordingEngineName;
    private String recordingPath;
    private Long targetEngineId;
    private String targetEngineName;
    private String targetFilePath;
    private Boolean enabled;
    private LocalDateTime createdAt;

    public static WebhookRuleVO from(WebhookRule entity) {
        WebhookRuleVO vo = new WebhookRuleVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setTriggerEventType(entity.getTriggerEventType().name());
        vo.setRoomIdFilter(entity.getRoomIdFilter());
        vo.setAction(entity.getAction().name());
        if (entity.getRecordingEngine() != null) {
            vo.setRecordingEngineId(entity.getRecordingEngine().getId());
            vo.setRecordingEngineName(entity.getRecordingEngine().getName());
        }
        vo.setRecordingPath(entity.getRecordingPath());
        vo.setTargetEngineId(entity.getTargetEngine().getId());
        vo.setTargetEngineName(entity.getTargetEngine().getName());
        vo.setTargetFilePath(entity.getTargetFilePath());
        vo.setEnabled(entity.getEnabled());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
