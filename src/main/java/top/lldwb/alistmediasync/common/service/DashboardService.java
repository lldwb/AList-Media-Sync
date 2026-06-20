package top.lldwb.alistmediasync.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.common.dto.DashboardStatsVO;
import top.lldwb.alistmediasync.sync.entity.TaskExecution;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;
import top.lldwb.alistmediasync.transcode.repository.TranscodeTaskRepository;
import top.lldwb.alistmediasync.webhook.repository.WebhookRuleRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 仪表板聚合统计查询服务
 * <p>
 * 通过现有 Repository 执行聚合查询，计算仪表板所需的各项指标。
 * 严格遵循分层架构：Controller → Service → Repository。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final TaskExecutionRepository taskExecutionRepository;
    private final TranscodeTaskRepository transcodeTaskRepository;
    private final StorageEngineRepository storageEngineRepository;
    private final WebhookRuleRepository webhookRuleRepository;

    /**
     * 获取仪表板统计数据
     *
     * @return 聚合统计结果
     */
    public DashboardStatsVO getStats() {
        // 活跃同步任务数（当前正在运行的任务执行数）
        long activeSyncTasks = taskExecutionRepository
            .findByStatusAndTaskType(
                TaskExecution.ExecutionStatus.RUNNING,
                TaskExecution.TaskType.SYNC
            )
            .size();

        // 等待处理的转码任务数（PENDING + DOWNLOADING + TRANSCODING + UPLOADING）
        long pendingCount = transcodeTaskRepository.findByStatus(TranscodeTask.TranscodeStatus.PENDING).size();
        long downloadingCount = transcodeTaskRepository.findByStatus(TranscodeTask.TranscodeStatus.DOWNLOADING).size();
        long transcodingCount = transcodeTaskRepository.findByStatus(TranscodeTask.TranscodeStatus.TRANSCODING).size();
        long uploadingCount = transcodeTaskRepository.findByStatus(TranscodeTask.TranscodeStatus.UPLOADING).size();
        long pendingTranscodeTasks = pendingCount + downloadingCount + transcodingCount + uploadingCount;

        // 24 小时内处理的文件总数及成功率
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusHours(24);
        List<TaskExecution> recentExecutions = taskExecutionRepository.findByCreatedAtBetween(last24h, now);

        long todayProcessedFiles = 0;
        long totalSuccessFiles = 0;
        long totalFiles = 0;

        for (TaskExecution exec : recentExecutions) {
            if (exec.getSuccessFiles() != null) {
                todayProcessedFiles += exec.getSuccessFiles();
                totalSuccessFiles += exec.getSuccessFiles();
            }
            if (exec.getTotalFiles() != null) {
                totalFiles += exec.getTotalFiles();
            }
        }

        double last24hSuccessRate = 100.0;
        if (totalFiles > 0) {
            last24hSuccessRate = (double) totalSuccessFiles / totalFiles * 100.0;
        } else if (recentExecutions.isEmpty()) {
            last24hSuccessRate = 100.0; // 无执行记录时显示 100%
        }

        // 存储引擎总数
        long totalEngines = storageEngineRepository.count();

        // Webhook 规则总数
        long totalWebhookRules = webhookRuleRepository.count();

        return new DashboardStatsVO(
            activeSyncTasks,
            pendingTranscodeTasks,
            todayProcessedFiles,
            Math.round(last24hSuccessRate * 10.0) / 10.0, // 保留一位小数
            totalEngines,
            totalWebhookRules
        );
    }
}
