package top.lldwb.alistmediasync.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule;

import java.time.LocalDateTime;

/**
 * Webhook 规则视图 VO
 *
 * @author AList-Media-Sync
 */
@Data
@Schema(description = "Webhook 规则视图")
public class WebhookRuleVO {

    @Schema(description = "规则 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(description = "规则名称", example = "自动同步录播", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(description = "触发事件类型", example = "RECORDING_COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String triggerEventType;
    @Schema(description = "房间号过滤", example = "12345678")
    private Long roomIdFilter;
    @Schema(description = "触发后的操作", example = "BOTH", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;
    @Schema(description = "录播存储引擎 ID（源端）", example = "1")
    private Long recordingEngineId;
    @Schema(description = "录播存储引擎名称（源端）", example = "主 AList 服务器")
    private String recordingEngineName;
    @Schema(description = "录播文件路径（源端路径）", example = "/recordings")
    private String recordingPath;
    @Schema(description = "目标存储引擎 ID", example = "2")
    private Long targetEngineId;
    @Schema(description = "目标存储引擎名称", example = "本地存储")
    private String targetEngineName;
    @Schema(description = "目标文件路径", example = "/media/recordings")
    private String targetFilePath;
    @Schema(description = "是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;
    @Schema(description = "创建时间", example = "2026-07-01T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
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
