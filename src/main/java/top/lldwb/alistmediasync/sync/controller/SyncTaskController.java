package top.lldwb.alistmediasync.sync.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.common.dto.ApiResult;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.sync.dto.SyncTaskCreateDTO;
import top.lldwb.alistmediasync.sync.dto.SyncTaskUpdateDTO;
import top.lldwb.alistmediasync.sync.dto.SyncTaskVO;
import top.lldwb.alistmediasync.execution.TaskExecutionVO;
import top.lldwb.alistmediasync.sync.service.SyncService;
import top.lldwb.alistmediasync.sync.service.SyncTaskManageService;

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
@Tag(name = "同步任务", description = "同步任务的 CRUD、手动触发和启用/禁用")
public class SyncTaskController {

    private final SyncTaskManageService manageService;
    private final SyncService syncService;
    private final WsSessionManager wsSessionManager;

    /** 创建同步任务 */
    @PostMapping
    @Operation(summary = "创建同步任务", operationId = "create", description = "根据传入的同步任务配置创建一个新的同步任务，并通过 WebSocket 广播任务创建事件")
    @ApiResponse(responseCode = "200", description = "创建成功，返回新建同步任务详情")
    public ApiResult<SyncTaskVO> create(@Valid @RequestBody SyncTaskCreateDTO dto) {
        var result = manageService.create(dto);
        wsSessionManager.broadcast("TASK_EVENT", Map.of(
            "action", "CREATED",
            "taskType", "SYNC",
            "taskId", result.getId()
        ));
        return ApiResult.success(result);
    }

    /** 更新同步任务 */
    @PutMapping("/{id}")
    @Operation(summary = "更新同步任务", operationId = "update", description = "根据 ID 更新指定同步任务的配置信息")
    @ApiResponse(responseCode = "200", description = "更新成功，返回更新后的同步任务详情")
    public ApiResult<SyncTaskVO> update(@PathVariable Long id, @RequestBody SyncTaskUpdateDTO dto) {
        return ApiResult.success(manageService.update(id, dto));
    }

    /** 删除同步任务 */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除同步任务", operationId = "delete", description = "根据 ID 删除指定的同步任务，并通过 WebSocket 广播任务删除事件")
    @ApiResponse(responseCode = "200", description = "删除成功")
    public ApiResult<Void> delete(@PathVariable Long id) {
        manageService.delete(id);
        wsSessionManager.broadcast("TASK_EVENT", Map.of(
            "action", "DELETED",
            "taskType", "SYNC",
            "taskId", id
        ));
        return ApiResult.success();
    }

    /** 查询所有同步任务 */
    @GetMapping
    @Operation(summary = "查询所有同步任务", operationId = "listAll", description = "返回系统中所有同步任务列表")
    @ApiResponse(responseCode = "200", description = "查询成功，返回同步任务列表")
    public ApiResult<List<SyncTaskVO>> listAll() {
        return ApiResult.success(manageService.listAll());
    }

    /** 查询单个同步任务 */
    @GetMapping("/{id}")
    @Operation(summary = "查询单个同步任务", operationId = "getById", description = "根据 ID 返回指定同步任务的详细信息")
    @ApiResponse(responseCode = "200", description = "查询成功，返回同步任务详情")
    public ApiResult<SyncTaskVO> getById(@PathVariable Long id) {
        return ApiResult.success(manageService.getById(id));
    }

    /** 手动触发同步任务 */
    @PostMapping("/{id}/execute")
    @Operation(summary = "手动触发同步任务", operationId = "execute", description = "根据 ID 手动触发指定同步任务的执行，验证任务存在且无运行中的执行后异步执行同步")
    @ApiResponse(responseCode = "200", description = "触发成功，返回任务 ID")
    public ApiResult<Map<String, Object>> execute(@PathVariable Long id) {
        manageService.executeManually(id); // 验证任务存在且无运行中的执行
        var task = manageService.getEntity(id);
        syncService.executeSyncTask(task);
        return ApiResult.success("同步任务已触发", Map.of("taskId", id));
    }

    /** 启用定时调度 */
    @PostMapping("/{id}/enable")
    @Operation(summary = "启用定时调度", operationId = "enable", description = "根据 ID 启用指定同步任务的定时调度")
    @ApiResponse(responseCode = "200", description = "启用成功，返回更新后的同步任务详情")
    public ApiResult<SyncTaskVO> enable(@PathVariable Long id) {
        return ApiResult.success(manageService.enable(id));
    }

    /** 禁用定时调度 */
    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用定时调度", operationId = "disable", description = "根据 ID 禁用指定同步任务的定时调度")
    @ApiResponse(responseCode = "200", description = "禁用成功，返回更新后的同步任务详情")
    public ApiResult<SyncTaskVO> disable(@PathVariable Long id) {
        return ApiResult.success(manageService.disable(id));
    }

    /** 查询同步任务的执行历史 */
    @GetMapping("/{id}/executions")
    @Operation(summary = "查询同步任务执行历史", operationId = "getExecutions", description = "根据 ID 返回指定同步任务的执行历史记录列表")
    @ApiResponse(responseCode = "200", description = "查询成功，返回执行历史列表")
    public ApiResult<List<TaskExecutionVO>> getExecutions(@PathVariable Long id) {
        return ApiResult.success(manageService.getExecutions(id));
    }
}
