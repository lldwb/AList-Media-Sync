package top.lldwb.alistmediasync.dto.transcode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import top.lldwb.alistmediasync.entity.TranscodeTask;

/**
 * 转码任务创建 DTO
 *
 * @author AList-Media-Sync
 */
@Data
public class TranscodeTaskCreateDTO {

    /** 源文件在 AList 中的完整路径 */
    @NotBlank(message = "源文件路径不能为空")
    private String sourceFilePath;

    /** 目标文件完整路径 */
    @NotBlank(message = "目标文件路径不能为空")
    private String targetFilePath;

    /** 目标格式（MP3/MP4/FLV） */
    @NotNull(message = "目标格式不能为空")
    private TranscodeTask.TargetFormat targetFormat = TranscodeTask.TargetFormat.MP3;

    /** 音频比特率（bps，默认 128kbps） */
    private Integer bitrate = 128000;

    /** 源存储引擎 ID（从 AList 下载） */
    private Long sourceEngineId;

    /** 目标存储引擎 ID（上传转码结果） */
    @NotNull(message = "目标存储引擎不能为空")
    private Long targetEngineId;
}
