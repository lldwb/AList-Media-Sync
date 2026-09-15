package top.lldwb.alistmediasync.transcode.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.common.dto.ApiResult;
import top.lldwb.alistmediasync.transcode.dto.TranscodeTaskCreateDTO;
import top.lldwb.alistmediasync.transcode.dto.TranscodeTaskVO;
import top.lldwb.alistmediasync.common.service.TempFileCleanupTrigger;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
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
@Tag(name = "转码任务", description = "转码任务的创建、查询、重试和临时文件清理")
public class TranscodeTaskController {

    private final TranscodeService transcodeService;
    private final TempFileCleanupTrigger cleanupService;
    private final WsSessionManager wsSessionManager;

    /** 创建独立转码任务（支持源目录转码选项） */
    @PostMapping
    @Operation(summary = "创建转码任务", operationId = "create", description = "创建独立转码任务，支持三步流程（下载→转码→上传）和源目录转码选项，创建后异步执行并通过 WebSocket 广播事件")
    @ApiResponse(responseCode = "200", description = "创建成功，返回新建转码任务详情")
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
    @Operation(summary = "查询所有转码任务", operationId = "listAll", description = "返回系统中所有转码任务列表")
    @ApiResponse(responseCode = "200", description = "查询成功，返回转码任务列表")
    public ApiResult<List<TranscodeTaskVO>> listAll() {
        return ApiResult.success(transcodeService.listAll());
    }

    /** 查询单个转码任务（含实时进度） */
    @GetMapping("/{id}")
    @Operation(summary = "查询单个转码任务", operationId = "getById", description = "根据 ID 返回指定转码任务的详细信息，包含实时进度")
    @ApiResponse(responseCode = "200", description = "查询成功，返回转码任务详情")
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
    @Operation(summary = "重试转码任务", operationId = "retry", description = "根据 ID 重试转码任务，支持从任意失败状态（下载失败、转码失败、上传失败）重试")
    @ApiResponse(responseCode = "200", description = "重试已触发，返回任务 ID 和成功状态")
    public ApiResult<Map<String, Object>> retry(@PathVariable Long id) {
        transcodeService.retry(id);
        return ApiResult.success(Map.of("taskId", id, "success", true));
    }

    /** 手动清理残留临时文件 */
    @DeleteMapping("/cleanup-temp")
    @Operation(summary = "清理残留临时文件", operationId = "cleanupTemp", description = "手动清理转码过程中残留的临时文件")
    @ApiResponse(responseCode = "200", description = "清理完成，返回删除的文件数量")
    public ApiResult<Map<String, Object>> cleanupTemp() {
        long count = cleanupService.manualCleanup();
        return ApiResult.success(Map.of("deletedCount", count));
    }

    /** 删除所有失败状态的转码任务 */
    @DeleteMapping("/failed")
    @Operation(summary = "删除失败转码任务", operationId = "deleteFailed", description = "删除所有处于失败状态（下载失败、转码失败、上传失败）的转码任务，并通过 WebSocket 广播批量删除事件")
    @ApiResponse(responseCode = "200", description = "删除完成，返回删除的任务数量")
    public ApiResult<Map<String, Object>> deleteFailed() {
        var failedStatuses = TranscodeTask.TranscodeStatus.FAILED_STATUSES;
        long count = transcodeService.countByStatusIn(failedStatuses);
        if (count == 0) {
            return ApiResult.success("没有可操作的失败任务", Map.of("deletedCount", 0));
        }
        int deleted = transcodeService.deleteByStatusIn(failedStatuses);
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
    @Operation(summary = "删除已完成转码任务", operationId = "deleteCompleted", description = "删除所有处于已完成状态的转码任务，并通过 WebSocket 广播批量删除事件")
    @ApiResponse(responseCode = "200", description = "删除完成，返回删除的任务数量")
    public ApiResult<Map<String, Object>> deleteCompleted() {
        var completedStatus = List.of(TranscodeTask.TranscodeStatus.COMPLETED);
        long count = transcodeService.countByStatusIn(completedStatus);
        if (count == 0) {
            return ApiResult.success("没有可清理的成功任务", Map.of("deletedCount", 0));
        }
        int deleted = transcodeService.deleteByStatusIn(completedStatus);
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
    @Operation(summary = "重试所有失败转码任务", operationId = "retryAll", description = "异步重试所有处于失败状态的转码任务，立即返回 202，结果通过 WebSocket 实时推送")
    @ApiResponse(responseCode = "200", description = "已提交重试，返回提交的任务数量")
    public ApiResult<Map<String, Object>> retryAll() {
        var failedStatuses = TranscodeTask.TranscodeStatus.FAILED_STATUSES;
        var failedTasks = transcodeService.findByStatusIn(failedStatuses);
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
