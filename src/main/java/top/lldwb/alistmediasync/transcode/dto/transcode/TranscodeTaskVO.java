package top.lldwb.alistmediasync.transcode.dto.transcode;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;

import java.time.LocalDateTime;

/**
 * 转码任务视图 VO
 *
 * @author AList-Media-Sync
 */
@Data
@Schema(description = "转码任务视图")
public class TranscodeTaskVO {

    @Schema(description = "转码任务 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(description = "源文件路径", example = "/recordings/live.flv", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceFilePath;
    @Schema(description = "目标文件路径", example = "/media/live.mp4", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetFilePath;
    @Schema(description = "源格式", example = "FLV", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceFormat;
    @Schema(description = "目标格式", example = "MP4", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetFormat;
    /** 音频比特率（bps，null 表示使用系统默认值） */
    @Schema(description = "音频比特率（bps，null 表示使用系统默认值）", example = "128000")
    private Integer bitrate;
    /** 进度（0-100 百分比，由千分比 progress/10 转换） */
    @Schema(description = "进度（0-100 百分比）", example = "75", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer progressPercent;
    /** 转码状态（8 状态模型） */
    @Schema(description = "转码状态（8 状态模型）", example = "TRANSCODING", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    /** 是否可重试（仅失败状态为 true） */
    @Schema(description = "是否可重试（仅失败状态为 true）", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean canRetry;
    /** 已执行自动重试次数 */
    @Schema(description = "已执行自动重试次数", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer retryCount;
    @Schema(description = "错误信息", example = "下载失败：连接超时")
    private String errorMessage;
    @Schema(description = "创建时间", example = "2026-07-02T02:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;

    public static TranscodeTaskVO from(TranscodeTask entity) {
        TranscodeTaskVO vo = new TranscodeTaskVO();
        vo.setId(entity.getId());
        vo.setSourceFilePath(entity.getSourceFilePath());
        vo.setTargetFilePath(entity.getTargetFilePath());
        vo.setSourceFormat(entity.getSourceFormat() != null ? entity.getSourceFormat().name() : null);
        vo.setTargetFormat(entity.getTargetFormat().name());
        vo.setBitrate(entity.getBitrate());
        vo.setProgressPercent(entity.getProgress() / 10); // 千分比 → 百分比
        vo.setStatus(entity.getStatus().name());
        vo.setCanRetry(isRetryable(entity.getStatus()));
        vo.setRetryCount(entity.getRetryCount());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    /** 判断当前状态是否可重试 */
    private static boolean isRetryable(TranscodeTask.TranscodeStatus status) {
        return status == TranscodeTask.TranscodeStatus.DOWNLOAD_FAILED
            || status == TranscodeTask.TranscodeStatus.TRANSCODE_FAILED
            || status == TranscodeTask.TranscodeStatus.UPLOAD_FAILED;
    }
}
