package top.lldwb.alistmediasync.common.dto;

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
public class DiagnosticSummaryVO {

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /** 应用版本 */
    private String appVersion;

    /** 构建或 Git commit 信息 */
    private String commit;

    /** 部署形态：本地开发、Docker、一体化启动包 */
    private String environment;

    /** 诊断自身 traceId */
    private String traceId;

    /** 最近一次失败概览 */
    private LatestFailure latestFailure;

    /** 建议优先阅读的证据文件相对路径列表 */
    @Builder.Default
    private List<String> recommendedFiles = new ArrayList<>();

    /** 基于日志证据的疑似原因摘要 */
    private String suspectedCause;

    /** 不可读取或缺失的信息列表 */
    @Builder.Default
    private List<String> missingItems = new ArrayList<>();

    /**
     * 最近失败概览
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatestFailure {
        /** 关联 traceId */
        private String traceId;
        /** 模块名称 */
        private String module;
        /** 操作名称 */
        private String operation;
        /** 错误类型 */
        private String errorType;
        /** 脱敏后的错误信息 */
        private String message;
        /** 失败发生时间 */
        private LocalDateTime occurredAt;
    }
}
