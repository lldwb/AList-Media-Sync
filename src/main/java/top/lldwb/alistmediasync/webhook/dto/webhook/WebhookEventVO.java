package top.lldwb.alistmediasync.webhook.dto.webhook;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.lldwb.alistmediasync.webhook.entity.WebhookEvent;

import java.time.LocalDateTime;

/**
 * Webhook 事件视图 VO（分页查询用，不含 rawData 大字段）
 *
 * @author AList-Media-Sync
 */
@Data
@Schema(description = "Webhook 事件视图（分页查询用，不含 rawData 大字段）")
public class WebhookEventVO {

    @Schema(description = "事件 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(description = "Webhook 事件唯一标识", example = "evt-550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String eventId;
    @Schema(description = "事件类型", example = "RECORDING_COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String eventType;
    @Schema(description = "事件时间戳", example = "2026-07-02T02:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime eventTimestamp;
    @Schema(description = "会话 ID", example = "sess-abc123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;
    @Schema(description = "房间号", example = "12345678", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long roomId;
    @Schema(description = "相对路径", example = "/recordings/2026/07/02/直播录像.flv")
    private String relativePath;
    @Schema(description = "文件名", example = "直播录像.flv")
    private String fileName;
    @Schema(description = "文件大小（字节）", example = "1073741824")
    private Long fileSize;
    @Schema(description = "时长（秒）", example = "3600")
    private Long duration;
    @Schema(description = "处理状态", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    @Schema(description = "创建时间", example = "2026-07-02T02:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
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
