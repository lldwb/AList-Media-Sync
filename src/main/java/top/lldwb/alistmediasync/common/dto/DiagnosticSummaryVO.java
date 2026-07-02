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
 * 诊断摘要视图
 * <p>
 * 与 {@code contracts/diagnostics-output-contract.md} 中的 summary.md 必填字段对齐，
 * 用于 Controller 返回结构化摘要数据（FR-006）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "诊断摘要视图")
public class DiagnosticSummaryVO {

    /** 生成时间 */
    @Schema(description = "生成时间", example = "2026-07-02T10:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime generatedAt;

    /** 应用版本 */
    @Schema(description = "应用版本", example = "1.0.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private String appVersion;

    /** 构建或 Git commit 信息 */
    @Schema(description = "构建或 Git commit 信息", example = "aa3567b", requiredMode = Schema.RequiredMode.REQUIRED)
    private String commit;

    /** 部署形态：本地开发、Docker、一体化启动包 */
    @Schema(description = "部署形态：本地开发、Docker、一体化启动包", example = "Docker", requiredMode = Schema.RequiredMode.REQUIRED)
    private String environment;

    /** 诊断自身 traceId */
    @Schema(description = "诊断自身 traceId", example = "diag-550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String traceId;

    /** 最近一次失败概览 */
    @Schema(description = "最近一次失败概览")
    private LatestFailure latestFailure;

    /** 建议优先阅读的证据文件相对路径列表 */
    @Builder.Default
    @Schema(description = "建议优先阅读的证据文件相对路径列表", example = "[\"logs/application.log\",\"config/application.yml\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> recommendedFiles = new ArrayList<>();

    /** 基于日志证据的疑似原因摘要 */
    @Schema(description = "基于日志证据的疑似原因摘要", example = "数据库连接池耗尽导致同步任务超时")
    private String suspectedCause;

    /** 不可读取或缺失的信息列表 */
    @Builder.Default
    @Schema(description = "不可读取或缺失的信息列表", example = "[\"logs/nginx.log\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> missingItems = new ArrayList<>();

    /**
     * 最近失败概览
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "最近一次失败概览")
    public static class LatestFailure {
        /** 关联 traceId */
        @Schema(description = "关联 traceId", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
        private String traceId;
        /** 模块名称 */
        @Schema(description = "模块名称", example = "sync", requiredMode = Schema.RequiredMode.REQUIRED)
        private String module;
        /** 操作名称 */
        @Schema(description = "操作名称", example = "executeSyncTask", requiredMode = Schema.RequiredMode.REQUIRED)
        private String operation;
        /** 错误类型 */
        @Schema(description = "错误类型", example = "SQLException", requiredMode = Schema.RequiredMode.REQUIRED)
        private String errorType;
        /** 脱敏后的错误信息 */
        @Schema(description = "脱敏后的错误信息", example = "Connection is not available, request timed out after 30000ms", requiredMode = Schema.RequiredMode.REQUIRED)
        private String message;
        /** 失败发生时间 */
        @Schema(description = "失败发生时间", example = "2026-07-02T09:15:00", requiredMode = Schema.RequiredMode.REQUIRED)
        private LocalDateTime occurredAt;
    }
}
