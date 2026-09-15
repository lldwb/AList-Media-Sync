package top.lldwb.alistmediasync.execution;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 任务执行记录视图对象
 * <p>
 * 用于 API 响应，封装任务执行历史的关键信息，
 * 避免直接暴露 JPA 实体及其懒加载关联关系。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Schema(description = "任务执行记录视图")
public record TaskExecutionVO(
    @Schema(description = "执行记录 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    Long id,

    @Schema(description = "同步任务 ID", example = "10")
    Long syncTaskId,

    @Schema(description = "转码任务 ID", example = "5")
    Long transcodeTaskId,

    @Schema(description = "Webhook 事件 ID", example = "8")
    Long webhookEventId,

    @Schema(description = "任务类型", example = "SYNC", requiredMode = Schema.RequiredMode.REQUIRED)
    String taskType,

    @Schema(description = "开始时间", example = "2026-07-02T02:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDateTime startTime,

    @Schema(description = "结束时间", example = "2026-07-02T02:05:30")
    LocalDateTime endTime,

    @Schema(description = "执行状态", example = "SUCCESS", requiredMode = Schema.RequiredMode.REQUIRED)
    String status,

    @Schema(description = "文件总数", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    Integer totalFiles,

    @Schema(description = "成功文件数", example = "98", requiredMode = Schema.RequiredMode.REQUIRED)
    Integer successFiles,

    @Schema(description = "失败文件数", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    Integer failedFiles,

    @Schema(description = "失败详情", example = "file45.mp3: connection timeout")
    String failureDetails,

    @Schema(description = "创建时间", example = "2026-07-02T02:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDateTime createdAt
) {

    /**
     * 从实体转换为视图对象
     *
     * @param entity 任务执行记录实体
     * @return 视图对象
     */
    public static TaskExecutionVO from(TaskExecution entity) {
        return new TaskExecutionVO(
            entity.getId(),
            entity.getSyncTaskId(),
            entity.getTranscodeTaskId(),
            entity.getWebhookEventId(),
            entity.getTaskType().name(),
            entity.getStartTime(),
            entity.getEndTime(),
            entity.getStatus().name(),
            entity.getTotalFiles(),
            entity.getSuccessFiles(),
            entity.getFailedFiles(),
            entity.getFailureDetails(),
            entity.getCreatedAt()
        );
    }
}
