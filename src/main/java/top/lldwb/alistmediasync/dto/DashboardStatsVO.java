package top.lldwb.alistmediasync.dto;

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
public class DashboardStatsVO {

    /** 活跃同步任务数（当前正在运行的同步任务执行数） */
    private long activeSyncTasks;

    /** 等待处理的转码任务数（PENDING + TRANSCODING 状态） */
    private long pendingTranscodeTasks;

    /** 今日已处理的文件总数（最近 24 小时内所有执行的成功文件数汇总） */
    private long todayProcessedFiles;

    /** 24 小时内成功率（百分比，如 96.5） */
    private double last24hSuccessRate;

    /** 存储引擎总数 */
    private long totalEngines;

    /** Webhook 规则总数 */
    private long totalWebhookRules;
}
