package top.lldwb.alistmediasync.sync.dto.sync;

import lombok.Data;

/**
 * 同步进度 VO（实时进度查询）
 *
 * @author AList-Media-Sync
 */
@Data
public class SyncProgressVO {

    /** 关联的执行记录 ID */
    private Long taskExecutionId;

    /** 当前状态 */
    private String status;

    /** 已完成文件数 */
    private Integer completedFiles;

    /** 文件总数 */
    private Integer totalFiles;

    /** 当前处理的文件名 */
    private String currentFile;

    /** 同步速度（字节/秒） */
    private Long bytesPerSecond;

    /** 预估剩余秒数 */
    private Long estimatedRemainingSeconds;
}
