package top.lldwb.alistmediasync.dto.sync;

import top.lldwb.alistmediasync.entity.TaskExecution;

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
public record TaskExecutionVO(
    Long id,
    Long syncTaskId,
    Long transcodeTaskId,
    Long webhookEventId,
    String taskType,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String status,
    Integer totalFiles,
    Integer successFiles,
    Integer failedFiles,
    String failureDetails,
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
            entity.getSyncTask() != null ? entity.getSyncTask().getId() : null,
            entity.getTranscodeTask() != null ? entity.getTranscodeTask().getId() : null,
            entity.getWebhookEvent() != null ? entity.getWebhookEvent().getId() : null,
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
