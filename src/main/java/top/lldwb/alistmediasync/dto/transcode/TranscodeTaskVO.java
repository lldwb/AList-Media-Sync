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
    private Integer bitrate;
    /** 进度（0-100 百分比，由千分比 progress/10 转换） */
    private Integer progressPercent;
    private String status;
    private String errorMessage;
    private String tempFilePath;
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
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setTempFilePath(entity.getTempFilePath());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
