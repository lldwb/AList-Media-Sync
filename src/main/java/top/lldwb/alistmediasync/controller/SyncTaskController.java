package top.lldwb.alistmediasync.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.dto.ApiResult;
import top.lldwb.alistmediasync.dto.sync.SyncTaskCreateDTO;
import top.lldwb.alistmediasync.dto.sync.SyncTaskUpdateDTO;
import top.lldwb.alistmediasync.dto.sync.SyncTaskVO;
import top.lldwb.alistmediasync.dto.sync.TaskExecutionVO;
import top.lldwb.alistmediasync.service.SyncService;
import top.lldwb.alistmediasync.service.SyncTaskManageService;

import java.util.List;
import java.util.Map;

/**
 * 同步任务管理 API
 * <p>
 * 提供同步任务的 CRUD、手动触发、启用/禁用功能。
 * </p>
 *
 * @author AList-Media-Sync
 */
@RestController
@RequestMapping("/api/sync-tasks")
@RequiredArgsConstructor
public class SyncTaskController {

    private final SyncTaskManageService manageService;
    private final SyncService syncService;

    /** 创建同步任务 */
    @PostMapping
    public ApiResult<SyncTaskVO> create(@Valid @RequestBody SyncTaskCreateDTO dto) {
        return ApiResult.success(manageService.create(dto));
    }

    /** 更新同步任务 */
    @PutMapping("/{id}")
    public ApiResult<SyncTaskVO> update(@PathVariable Long id, @RequestBody SyncTaskUpdateDTO dto) {
        return ApiResult.success(manageService.update(id, dto));
    }

    /** 删除同步任务 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        manageService.delete(id);
        return ApiResult.success();
    }

    /** 查询所有同步任务 */
    @GetMapping
    public ApiResult<List<SyncTaskVO>> listAll() {
        return ApiResult.success(manageService.listAll());
    }

    /** 查询单个同步任务 */
    @GetMapping("/{id}")
    public ApiResult<SyncTaskVO> getById(@PathVariable Long id) {
        return ApiResult.success(manageService.getById(id));
    }

    /** 手动触发同步任务 */
    @PostMapping("/{id}/execute")
    public ApiResult<Map<String, Object>> execute(@PathVariable Long id) {
        manageService.executeManually(id); // 验证任务存在且无运行中的执行
        var task = manageService.getEntity(id);
        syncService.executeSyncTask(task);
        return ApiResult.success("同步任务已触发", Map.of("taskId", id));
    }

    /** 启用定时调度 */
    @PostMapping("/{id}/enable")
    public ApiResult<SyncTaskVO> enable(@PathVariable Long id) {
        return ApiResult.success(manageService.enable(id));
    }

    /** 禁用定时调度 */
    @PostMapping("/{id}/disable")
    public ApiResult<SyncTaskVO> disable(@PathVariable Long id) {
        return ApiResult.success(manageService.disable(id));
    }

    /** 查询同步任务的执行历史 */
    @GetMapping("/{id}/executions")
    public ApiResult<List<TaskExecutionVO>> getExecutions(@PathVariable Long id) {
        return ApiResult.success(manageService.getExecutions(id));
    }
}
