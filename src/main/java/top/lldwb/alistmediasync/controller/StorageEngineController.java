package top.lldwb.alistmediasync.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.dto.ApiResult;
import top.lldwb.alistmediasync.dto.storage.StorageEngineCreateDTO;
import top.lldwb.alistmediasync.dto.storage.StorageEngineUpdateDTO;
import top.lldwb.alistmediasync.dto.storage.StorageEngineVO;
import top.lldwb.alistmediasync.service.StorageEngineService;

import java.util.List;
import java.util.Map;

/**
 * 存储引擎管理 API
 * <p>
 * 提供存储引擎的 CRUD 和连接测试功能。
 * </p>
 *
 * @author AList-Media-Sync
 */
@RestController
@RequestMapping("/api/storage-engines")
@RequiredArgsConstructor
public class StorageEngineController {

    private final StorageEngineService service;

    /** 创建存储引擎 */
    @PostMapping
    public ApiResult<StorageEngineVO> create(@Valid @RequestBody StorageEngineCreateDTO dto) {
        return ApiResult.success(service.create(dto));
    }

    /** 更新存储引擎 */
    @PutMapping("/{id}")
    public ApiResult<StorageEngineVO> update(@PathVariable Long id, @RequestBody StorageEngineUpdateDTO dto) {
        return ApiResult.success(service.update(id, dto));
    }

    /** 删除存储引擎 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.success();
    }

    /** 查询所有存储引擎 */
    @GetMapping
    public ApiResult<List<StorageEngineVO>> listAll() {
        return ApiResult.success(service.listAll());
    }

    /** 查询单个存储引擎 */
    @GetMapping("/{id}")
    public ApiResult<StorageEngineVO> getById(@PathVariable Long id) {
        return ApiResult.success(service.getById(id));
    }

    /** 测试存储引擎连接 */
    @PostMapping("/{id}/test")
    public ApiResult<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean success = service.testConnection(id);
        return ApiResult.success(Map.of("connected", success));
    }
}
