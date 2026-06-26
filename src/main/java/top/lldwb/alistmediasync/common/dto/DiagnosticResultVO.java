package top.lldwb.alistmediasync.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 诊断生成结果视图
 * <p>
 * 由 {@code DiagnosticService} 输出，供脚本与 Controller 复用。
 * 与 {@code contracts/diagnostics-command-contract.md} 对齐。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticResultVO {

    /** 本次诊断生成自身的 traceId */
    private String traceId;

    /** 诊断包根目录（如 diagnostics/latest） */
    private String packagePath;

    /** 诊断摘要文件路径（如 diagnostics/latest/summary.md） */
    private String summaryPath;

    /** 诊断生成状态 */
    private Status status;

    /** 诊断生成耗时（毫秒），用于 SC-001 性能门禁 */
    private long durationMs;

    /** 不可读取或缺失的信息列表 */
    @Builder.Default
    private List<String> missingItems = new ArrayList<>();

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /** 诊断生成状态枚举（参见 spec 中诊断包状态模型） */
    public enum Status {
        /** 全部信息均成功收集 */
        COMPLETED,
        /** 部分信息不可用但摘要已生成 */
        PARTIAL,
        /** 无法生成有效摘要 */
        FAILED
    }
}
