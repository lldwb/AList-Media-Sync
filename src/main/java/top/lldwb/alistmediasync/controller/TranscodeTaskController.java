package top.lldwb.alistmediasync.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.dto.ApiResult;
import top.lldwb.alistmediasync.dto.transcode.TranscodeTaskCreateDTO;
import top.lldwb.alistmediasync.dto.transcode.TranscodeTaskVO;
import top.lldwb.alistmediasync.entity.TranscodeTask;
import top.lldwb.alistmediasync.repository.TranscodeTaskRepository;
import top.lldwb.alistmediasync.service.CleanupService;
import top.lldwb.alistmediasync.service.TranscodeService;

import java.util.List;
import java.util.Map;

/**
 * 转码任务管理 API
 * <p>
 * 提供转码任务的创建、查询、重试上传和临时文件清理接口。
 * </p>
 *
 * @author AList-Media-Sync
 */
@RestController
@RequestMapping("/api/transcode-tasks")
@RequiredArgsConstructor
public class TranscodeTaskController {

    private final TranscodeService transcodeService;
    private final TranscodeTaskRepository repository;
    private final CleanupService cleanupService;

    /** 创建独立转码任务 */
    @PostMapping
    public ApiResult<TranscodeTaskVO> create(@Valid @RequestBody TranscodeTaskCreateDTO dto) {
        TranscodeTask task = transcodeService.createTask(
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
        List<TranscodeTaskVO> vos = repository.findAll().stream()
            .map(TranscodeTaskVO::from)
            .toList();
        return ApiResult.success(vos);
    }

    /** 查询单个转码任务（含实时进度） */
    @GetMapping("/{id}")
    public ApiResult<TranscodeTaskVO> getById(@PathVariable Long id) {
        TranscodeTask task = repository.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("转码任务不存在：id=" + id));
        return ApiResult.success(TranscodeTaskVO.from(task));
    }

    /** 手动重试上传 */
    @PostMapping("/{id}/retry-upload")
    public ApiResult<Map<String, Object>> retryUpload(@PathVariable Long id) {
        boolean success = transcodeService.retryUpload(id);
        return ApiResult.success(Map.of("taskId", id, "success", success));
    }

    /** 手动清理残留临时文件 */
    @DeleteMapping("/cleanup-temp")
    public ApiResult<Map<String, Object>> cleanupTemp() {
        long count = cleanupService.manualCleanup();
        return ApiResult.success(Map.of("deletedCount", count));
    }
}
