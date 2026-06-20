package top.lldwb.alistmediasync.webhook.dto.webhook;

import lombok.Data;
import top.lldwb.alistmediasync.webhook.entity.WebhookEvent;

import java.time.LocalDateTime;

/**
 * Webhook 事件视图 VO（分页查询用，不含 rawData 大字段）
 *
 * @author AList-Media-Sync
 */
@Data
public class WebhookEventVO {

    private Long id;
    private String eventId;
    private String eventType;
    private LocalDateTime eventTimestamp;
    private String sessionId;
    private Long roomId;
    private String relativePath;
    private String fileName;
    private Long fileSize;
    private Long duration;
    private String status;
    private LocalDateTime createdAt;

    public static WebhookEventVO from(WebhookEvent entity) {
        WebhookEventVO vo = new WebhookEventVO();
        vo.setId(entity.getId());
        vo.setEventId(entity.getEventId());
        vo.setEventType(entity.getEventType().name());
        vo.setEventTimestamp(entity.getEventTimestamp());
        vo.setSessionId(entity.getSessionId());
        vo.setRoomId(entity.getRoomId());
        vo.setRelativePath(entity.getRelativePath());
        vo.setFileName(entity.getFileName());
        vo.setFileSize(entity.getFileSize());
        vo.setDuration(entity.getDuration());
        vo.setStatus(entity.getStatus().name());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
