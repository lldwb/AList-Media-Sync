package top.lldwb.alistmediasync.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仪表板统计响应 DTO
 * <p>
 * 聚合系统关键运行指标，用于前端仪表板页面展示。
 * 所有字段均为只读的统计值，由 DashboardService 聚合查询生成。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "仪表板统计响应数据")
public class DashboardStatsVO {

    /** 活跃同步任务数（当前正在运行的同步任务执行数） */
    @Schema(description = "活跃同步任务数（当前正在运行的同步任务执行数）", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private long activeSyncTasks;

    /** 等待处理的转码任务数（PENDING + TRANSCODING 状态） */
    @Schema(description = "等待处理的转码任务数（PENDING + TRANSCODING 状态）", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private long pendingTranscodeTasks;

    /** 今日已处理的文件总数（最近 24 小时内所有执行的成功文件数汇总） */
    @Schema(description = "今日已处理的文件总数（最近 24 小时内所有执行的成功文件数汇总）", example = "128", requiredMode = Schema.RequiredMode.REQUIRED)
    private long todayProcessedFiles;

    /** 24 小时内成功率（百分比，如 96.5） */
    @Schema(description = "24 小时内成功率（百分比，如 96.5）", example = "96.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private double last24hSuccessRate;

    /** 存储引擎总数 */
    @Schema(description = "存储引擎总数", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private long totalEngines;

    /** Webhook 规则总数 */
    @Schema(description = "Webhook 规则总数", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
    private long totalWebhookRules;
}
