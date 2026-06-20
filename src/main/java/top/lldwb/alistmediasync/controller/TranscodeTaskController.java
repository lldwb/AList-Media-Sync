package top.lldwb.alistmediasync.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.dto.ApiResult;
import top.lldwb.alistmediasync.dto.transcode.TranscodeTaskCreateDTO;
import top.lldwb.alistmediasync.dto.transcode.TranscodeTaskVO;
import top.lldwb.alistmediasync.service.CleanupService;
import top.lldwb.alistmediasync.service.TranscodeService;

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

    /** 创建独立转码任务 */
    @PostMapping
    public ApiResult<TranscodeTaskVO> create(@Valid @RequestBody TranscodeTaskCreateDTO dto) {
        var task = transcodeService.createTask(
            dto.getSourceEngineId(),
            dto.getTargetEngineId(),
            dto.getSourceFilePath(),
            dto.getTargetFilePath(),
            dto.getTargetFormat(),
            dto.getBitrate()
        );
        transcodeService.executeAsync(task);
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
}
