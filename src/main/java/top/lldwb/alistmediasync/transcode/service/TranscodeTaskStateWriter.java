package top.lldwb.alistmediasync.transcode.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.repository.TranscodeTaskRepository;

import java.util.Map;

/**
 * 转码任务状态写入器
 * <p>
 * 自 {@link TranscodeFileProcessor} 按职责切出的「任务状态持久化 + 进度推送」能力：
 * 实体重载、实体保存、FFmpeg 进度落库、WebSocket 进度广播。
 * 状态流转（setStatus / setProgress 等）仍由调用方编排，本类只负责写库与推送，
 * 不反向依赖 TranscodeFileProcessor。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodeTaskStateWriter {

    private final TranscodeTaskRepository repository;
    private final WsSessionManager wsSessionManager;

    /**
     * 重新加载 TranscodeTask 实体以获取最新版本号
     * <p>
     * 在每次 save() 之后、下一次修改之前调用，避免 detached entity
     * merge 时因版本号过期导致 ObjectOptimisticLockingFailureException。
     * </p>
     */
    public TranscodeTask reloadTask(Long taskId) {
        return repository.findById(taskId)
            .orElseThrow(() -> new IllegalStateException("转码任务不存在：id=" + taskId));
    }

    /**
     * 保存转码任务实体
     */
    public TranscodeTask save(TranscodeTask task) {
        return repository.save(task);
    }

    /**
     * 保存并重新加载，确保后续操作基于最新版本号
     */
    public TranscodeTask saveAndReload(TranscodeTask task) {
        task = repository.save(task);
        return reloadTask(task.getId());
    }

    /**
     * 持久化 FFmpeg 单步进度
     * <p>
     * 进度持久化失败不影响转码主流程，仅记录 DEBUG 日志；
     * 节流阈值由调用方判定。
     * </p>
     *
     * @param taskId 转码任务 ID
     * @param permil 进度（千分比）
     */
    public void persistProgress(Long taskId, int permil) {
        try {
            TranscodeTask managed = repository.findById(taskId).orElse(null);
            if (managed != null) {
                managed.setProgress(permil);
                repository.save(managed);
            }
        } catch (Exception e) {
            log.debug("进度持久化失败（非关键）：{}", e.getMessage());
        }
    }

    /**
     * 通过 WebSocket 推送转码任务进度
     */
    public void pushProgress(TranscodeTask task) {
        wsSessionManager.broadcast("TRANSCODE_PROGRESS", Map.of(
            "taskId", task.getId(),
            "status", task.getStatus().name(),
            "progressPercent", task.getProgress() / 10,
            "retryCount", task.getRetryCount(),
            "errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : ""
        ));
    }
}
