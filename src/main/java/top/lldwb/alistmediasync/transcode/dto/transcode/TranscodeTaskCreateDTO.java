package top.lldwb.alistmediasync.transcode.dto.transcode;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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
     * <p>
     * 使用包装类型 {@link Boolean} 而非 boolean primitive：
     * Lombok 对 boolean primitive 会生成 {@code isXxx()} getter，
     * 在 Jackson 3.x 下其 JSON 属性名推导可能与字段名冲突，
     * 曾导致前端发送的 {@code "sourceDirectoryTranscode": true} 反序列化为 false。
     * 改为 Boolean 包装类型后 Lombok 生成标准 {@code getXxx()} getter，
     * 反序列化路径无歧义；同时显式标注 {@link JsonProperty}/{@link JsonAlias} 加固命名映射，
     * 并兼容历史字段名 {@code sameDirectoryTranscode}。
     * </p>
     */
    @JsonProperty("sourceDirectoryTranscode")
    @JsonAlias({"sameDirectoryTranscode"})
    private Boolean sourceDirectoryTranscode = false;
}
