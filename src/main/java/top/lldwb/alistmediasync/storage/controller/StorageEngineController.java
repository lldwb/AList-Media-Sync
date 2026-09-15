package top.lldwb.alistmediasync.storage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.common.dto.ApiResult;
import top.lldwb.alistmediasync.storage.dto.StorageEngineCreateDTO;
import top.lldwb.alistmediasync.storage.dto.StorageEngineUpdateDTO;
import top.lldwb.alistmediasync.storage.dto.StorageEngineVO;
import top.lldwb.alistmediasync.storage.dto.DirectoryEntryVO;
import top.lldwb.alistmediasync.storage.dto.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;

import java.util.List;
import java.util.Map;

/**
 * 存储引擎管理 API
 * <p>
 * 提供存储引擎的 CRUD、连接测试和目录浏览功能。
 * </p>
 *
 * @author AList-Media-Sync
 */
@RestController
@RequestMapping("/api/storage-engines")
@RequiredArgsConstructor
@Tag(name = "存储引擎", description = "存储引擎的 CRUD、连接测试和目录浏览")
public class StorageEngineController {

    private final StorageEngineService service;

    /** 创建存储引擎 */
    @PostMapping
    @Operation(summary = "创建存储引擎", operationId = "create", description = "根据传入的存储引擎配置创建一个新的存储引擎")
    @ApiResponse(responseCode = "200", description = "创建成功，返回新建存储引擎详情")
    public ApiResult<StorageEngineVO> create(@Valid @RequestBody StorageEngineCreateDTO dto) {
        return ApiResult.success(service.create(dto));
    }

    /** 更新存储引擎 */
    @PutMapping("/{id}")
    @Operation(summary = "更新存储引擎", operationId = "update", description = "根据 ID 更新指定存储引擎的配置信息")
    @ApiResponse(responseCode = "200", description = "更新成功，返回更新后的存储引擎详情")
    public ApiResult<StorageEngineVO> update(@PathVariable Long id, @RequestBody StorageEngineUpdateDTO dto) {
        return ApiResult.success(service.update(id, dto));
    }

    /** 删除存储引擎 */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除存储引擎", operationId = "delete", description = "根据 ID 删除指定的存储引擎")
    @ApiResponse(responseCode = "200", description = "删除成功")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.success();
    }

    /** 查询所有存储引擎 */
    @GetMapping
    @Operation(summary = "查询所有存储引擎", operationId = "listAll", description = "返回系统中所有已配置的存储引擎列表")
    @ApiResponse(responseCode = "200", description = "查询成功，返回存储引擎列表")
    public ApiResult<List<StorageEngineVO>> listAll() {
        return ApiResult.success(service.listAll());
    }

    /** 查询单个存储引擎 */
    @GetMapping("/{id}")
    @Operation(summary = "查询单个存储引擎", operationId = "getById", description = "根据 ID 返回指定存储引擎的详细信息")
    @ApiResponse(responseCode = "200", description = "查询成功，返回存储引擎详情")
    public ApiResult<StorageEngineVO> getById(@PathVariable Long id) {
        return ApiResult.success(service.getById(id));
    }

    /** 测试存储引擎连接 */
    @PostMapping("/{id}/test")
    @Operation(summary = "测试存储引擎连接", operationId = "testConnection", description = "根据 ID 测试指定存储引擎的连接是否可用")
    @ApiResponse(responseCode = "200", description = "测试完成，返回连接结果")
    public ApiResult<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean success = service.testConnection(id);
        return ApiResult.success(Map.of("connected", success));
    }

    /**
     * 获取存储引擎的子目录列表（树状目录浏览组件使用）
     *
     * @param id   存储引擎 ID
     * @param path 目录路径（可选，不传时返回根目录）
     * @return 子目录列表（仅目录，不含文件）
     */
    @GetMapping("/{id}/directories")
    @Operation(summary = "获取子目录列表", operationId = "listDirectories", description = "获取指定存储引擎下某路径的子目录列表，供树状目录浏览组件使用，仅返回目录不含文件")
    @ApiResponse(responseCode = "200", description = "查询成功，返回子目录列表")
    public ApiResult<List<DirectoryEntryVO>> listDirectories(
        @PathVariable Long id,
        @RequestParam(required = false, defaultValue = "/") String path) {
        StorageEngine engine = service.getEntity(id);
        StorageEngineStrategy strategy = service.resolve(engine);
        return ApiResult.success(strategy.listDirectories(engine, path));
    }

    /**
     * 获取存储引擎指定目录下的全部条目（文件 + 目录）
     * <p>
     * 文件选择器（如转码任务源/目标文件选择）使用此接口；
     * 与 {@link #listDirectories} 的区别在于返回结果包含文件项，
     * 前端据 {@code isDirectory} 字段决定可否展开 / 可否选中。
     * </p>
     *
     * @param id   存储引擎 ID
     * @param path 目录路径（可选，不传时返回根目录）
     * @return 文件 + 子目录列表
     */
    @GetMapping("/{id}/entries")
    @Operation(summary = "获取目录条目列表", operationId = "listEntries", description = "获取指定存储引擎下某路径的全部条目（文件和目录），供文件选择器使用，前端根据 isDirectory 字段决定可否展开或选中")
    @ApiResponse(responseCode = "200", description = "查询成功，返回文件和子目录列表")
    public ApiResult<List<FileEntry>> listEntries(
        @PathVariable Long id,
        @RequestParam(required = false, defaultValue = "/") String path) {
        StorageEngine engine = service.getEntity(id);
        StorageEngineStrategy strategy = service.resolve(engine);
        return ApiResult.success(strategy.listEntries(engine, path));
    }
}
