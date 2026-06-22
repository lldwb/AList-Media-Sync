package top.lldwb.alistmediasync.transcode.dto.transcode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;

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

    /**
     * 目标文件完整路径
     * <p>
     * 当 {@code sourceDirectoryTranscode=true} 时可选（后端自动使用源文件目录）。
     * 当 {@code sourceDirectoryTranscode=false} 时必填。
     * </p>
     */
    private String targetFilePath;

    /** 目标格式（MP3/MP4/FLV） */
    @NotNull(message = "目标格式不能为空")
    private TranscodeTask.TargetFormat targetFormat = TranscodeTask.TargetFormat.MP3;

    /** 音频比特率（bps，默认 128kbps） */
    private Integer bitrate = 128000;

    /** 源存储引擎 ID（从 AList 下载） */
    private Long sourceEngineId;

    /**
     * 目标存储引擎 ID（上传转码结果）
     * <p>
     * 当 {@code sourceDirectoryTranscode=true} 时可为空（后端自动使用源引擎 ID）。
     * 当 {@code sourceDirectoryTranscode=false} 时必填。
     * </p>
     */
    private Long targetEngineId;

    /**
     * 源目录转码选项（默认 false）
     * <p>
     * 启用时转码输出文件自动放置在源文件所在目录，无需手动指定目标路径。
     * 此时 targetFilePath 和 targetEngineId 可为空，后端自动计算。
     * </p>
     */
    private boolean sourceDirectoryTranscode = false;
}
