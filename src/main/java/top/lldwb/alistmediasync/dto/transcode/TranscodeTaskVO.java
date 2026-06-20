package top.lldwb.alistmediasync.dto.transcode;

import lombok.Data;
import top.lldwb.alistmediasync.entity.TranscodeTask;

import java.time.LocalDateTime;

/**
 * 转码任务视图 VO
 *
 * @author AList-Media-Sync
 */
@Data
public class TranscodeTaskVO {

    private Long id;
    private String sourceFilePath;
    private String targetFilePath;
    private String sourceFormat;
    private String targetFormat;
    /** 音频比特率（bps，null 表示使用系统默认值） */
    private Integer bitrate;
    /** 进度（0-100 百分比，由千分比 progress/10 转换） */
    private Integer progressPercent;
    /** 转码状态（8 状态模型） */
    private String status;
    /** 是否可重试（仅失败状态为 true） */
    private Boolean canRetry;
    private String errorMessage;
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
