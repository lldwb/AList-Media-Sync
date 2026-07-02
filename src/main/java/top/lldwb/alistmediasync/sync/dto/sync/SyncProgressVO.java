package top.lldwb.alistmediasync.sync.dto.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 同步进度 VO（实时进度查询）
 *
 * @author AList-Media-Sync
 */
@Data
@Schema(description = "同步进度视图（实时进度查询）")
public class SyncProgressVO {

    /** 关联的执行记录 ID */
    @Schema(description = "关联的执行记录 ID", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long taskExecutionId;

    /** 当前状态 */
    @Schema(description = "当前状态", example = "RUNNING", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    /** 已完成文件数 */
    @Schema(description = "已完成文件数", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer completedFiles;

    /** 文件总数 */
    @Schema(description = "文件总数", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalFiles;

    /** 当前处理的文件名 */
    @Schema(description = "当前处理的文件名", example = "song.mp3")
    private String currentFile;

    /** 同步速度（字节/秒） */
    @Schema(description = "同步速度（字节/秒）", example = "1048576")
    private Long bytesPerSecond;

    /** 预估剩余秒数 */
    @Schema(description = "预估剩余秒数", example = "85")
    private Long estimatedRemainingSeconds;
}
