package top.lldwb.alistmediasync.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "诊断生成结果视图")
public class DiagnosticResultVO {

    /** 本次诊断生成自身的 traceId */
    @Schema(description = "本次诊断生成自身的 traceId", example = "diag-550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String traceId;

    /** 诊断包根目录（如 diagnostics/latest） */
    @Schema(description = "诊断包根目录", example = "diagnostics/latest", requiredMode = Schema.RequiredMode.REQUIRED)
    private String packagePath;

    /** 诊断摘要文件路径（如 diagnostics/latest/summary.md） */
    @Schema(description = "诊断摘要文件路径", example = "diagnostics/latest/summary.md", requiredMode = Schema.RequiredMode.REQUIRED)
    private String summaryPath;

    /** 诊断生成状态 */
    @Schema(description = "诊断生成状态", example = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private Status status;

    /** 诊断生成耗时（毫秒），用于 SC-001 性能门禁 */
    @Schema(description = "诊断生成耗时（毫秒），用于 SC-001 性能门禁", example = "1200", requiredMode = Schema.RequiredMode.REQUIRED)
    private long durationMs;

    /** 不可读取或缺失的信息列表 */
    @Builder.Default
    @Schema(description = "不可读取或缺失的信息列表", example = "[\"logs/application.log\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> missingItems = new ArrayList<>();

    /** 生成时间 */
    @Schema(description = "生成时间", example = "2026-07-02T10:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
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
