package top.lldwb.alistmediasync.transcode.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.common.dto.ApiResult;
import top.lldwb.alistmediasync.transcode.dto.transcode.TranscodeTaskCreateDTO;
import top.lldwb.alistmediasync.transcode.dto.transcode.TranscodeTaskVO;
import top.lldwb.alistmediasync.common.service.CleanupService;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.repository.TranscodeTaskRepository;
import top.lldwb.alistmediasync.transcode.service.TranscodeService;

import java.util.List;
import java.util.Map;

/**
 * 转码任务管理 API
 * <p>
 * 提供转码任务的创建、查询、重试和临时文件清理接口。
 * 支持 8 状态模型和三步流程（下载→转码→上传）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@RestController
@RequestMapping("/api/transcode-tasks")
@RequiredArgsConstructor
public class TranscodeTaskController {

    private final TranscodeService transcodeService;
    private final CleanupService cleanupService;
    private final WsSessionManager wsSessionManager;
    private final TranscodeTaskRepository repository;

    /** 创建独立转码任务（支持源目录转码选项） */
    @PostMapping
    public ApiResult<TranscodeTaskVO> create(@Valid @RequestBody TranscodeTaskCreateDTO dto) {
        // null 视为 false，避免反序列化缺失时 NPE
        boolean sourceDirectoryTranscode = Boolean.TRUE.equals(dto.getSourceDirectoryTranscode());
        // 源目录转码时自动将 targetEngineId 赋值为 sourceEngineId
        Long targetEngineId = sourceDirectoryTranscode
            ? dto.getSourceEngineId() : dto.getTargetEngineId();

        var task = transcodeService.createTask(
            dto.getSourceEngineId(),
            targetEngineId,
            dto.getSourceFilePath(),
            dto.getTargetFilePath(),
            dto.getTargetFormat(),
            dto.getBitrate(),
            sourceDirectoryTranscode
        );
        transcodeService.executeAsync(task);
        // 推送 TASK_EVENT 消息
        wsSessionManager.broadcast("TASK_EVENT", Map.of(
            "action", "CREATED",
            "taskType", "TRANSCODE",
            "taskId", task.getId()
        ));
        return ApiResult.success(TranscodeTaskVO.from(task));
    }

    /** 查询所有转码任务 */
    @GetMapping
    public ApiResult<List<TranscodeTaskVO>> listAll() {
        return ApiResult.success(transcodeService.listAll());
    }

    /** 查询单个转码任务（含实时进度） */
    @GetMapping("/{id}")
    public ApiResult<TranscodeTaskVO> getById(@PathVariable Long id) {
        return ApiResult.success(transcodeService.getById(id));
    }

    /**
     * 重试转码任务（支持从任意失败状态重试）
     * <ul>
     *   <li>DOWNLOAD_FAILED → 重新下载（删除部分下载文件）</li>
     *   <li>TRANSCODE_FAILED → 重新转码（保留源临时文件）</li>
     *   <li>UPLOAD_FAILED → 重新上传（保留源+输出临时文件）</li>
     * </ul>
     */
    @PostMapping("/{id}/retry")
    public ApiResult<Map<String, Object>> retry(@PathVariable Long id) {
        transcodeService.retry(id);
        return ApiResult.success(Map.of("taskId", id, "success", true));
    }

    /** 手动清理残留临时文件 */
    @DeleteMapping("/cleanup-temp")
    public ApiResult<Map<String, Object>> cleanupTemp() {
        long count = cleanupService.manualCleanup();
        return ApiResult.success(Map.of("deletedCount", count));
    }

    /** 删除所有失败状态的转码任务 */
    @DeleteMapping("/failed")
    public ApiResult<Map<String, Object>> deleteFailed() {
        var failedStatuses = List.of(
            TranscodeTask.TranscodeStatus.DOWNLOAD_FAILED,
            TranscodeTask.TranscodeStatus.TRANSCODE_FAILED,
            TranscodeTask.TranscodeStatus.UPLOAD_FAILED
        );
        long count = repository.countByStatusIn(failedStatuses);
        if (count == 0) {
            return ApiResult.success("没有可操作的失败任务", Map.of("deletedCount", 0));
        }
        int deleted = repository.deleteByStatusIn(failedStatuses);
        wsSessionManager.broadcast("TASK_EVENT", Map.of(
            "action", "BATCH_DELETED",
            "taskType", "TRANSCODE",
            "count", deleted,
            "status", "FAILED"
        ));
        return ApiResult.success("已清理 " + deleted + " 个失败任务", Map.of("deletedCount", deleted));
    }

    /** 删除所有已完成状态的转码任务 */
    @DeleteMapping("/completed")
    public ApiResult<Map<String, Object>> deleteCompleted() {
        var completedStatus = List.of(TranscodeTask.TranscodeStatus.COMPLETED);
        long count = repository.countByStatusIn(completedStatus);
        if (count == 0) {
            return ApiResult.success("没有可清理的成功任务", Map.of("deletedCount", 0));
        }
        int deleted = repository.deleteByStatusIn(completedStatus);
        wsSessionManager.broadcast("TASK_EVENT", Map.of(
            "action", "BATCH_DELETED",
            "taskType", "TRANSCODE",
            "count", deleted,
            "status", "COMPLETED"
        ));
        return ApiResult.success("已清理 " + deleted + " 个成功任务", Map.of("deletedCount", deleted));
    }

    /** 重试所有失败状态的转码任务（异步执行，立即返回 202） */
    @PostMapping("/retry-all")
    public ApiResult<Map<String, Object>> retryAll() {
        var failedStatuses = List.of(
            TranscodeTask.TranscodeStatus.DOWNLOAD_FAILED,
            TranscodeTask.TranscodeStatus.TRANSCODE_FAILED,
            TranscodeTask.TranscodeStatus.UPLOAD_FAILED
        );
        var failedTasks = repository.findByStatusIn(failedStatuses);
        if (failedTasks.isEmpty()) {
            return ApiResult.success("没有可操作的失败任务", Map.of("submittedCount", 0));
        }
        // 异步重试所有失败任务
        for (var task : failedTasks) {
            transcodeService.retry(task.getId());
        }
        return ApiResult.of(HttpStatus.ACCEPTED.value(),
            "已提交 " + failedTasks.size() + " 个任务进行重试，结果将通过实时更新推送",
            Map.of("submittedCount", failedTasks.size()));
    }
}
