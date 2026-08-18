package top.lldwb.alistmediasync.storage.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import top.lldwb.alistmediasync.common.mcp.McpToolResult;
import top.lldwb.alistmediasync.storage.dto.storage.StorageEngineCreateDTO;
import top.lldwb.alistmediasync.storage.dto.storage.StorageEngineUpdateDTO;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;

import java.util.Map;

/**
 * 存储引擎模块 MCP 工具（FR-003）
 * <p>
 * 提供存储引擎的查询/创建/更新/删除/连接测试/目录浏览/条目浏览工具。
 * 工具层仅做参数适配与结果封装，业务逻辑复用 {@link StorageEngineService}（FR-009）。
 * </p>
 * <p>
 * 返回结构复用 {@code StorageEngineVO}（VO 层已对 token 脱敏，MUST NOT 回显明文凭据，FR-011）；
 * 目录浏览映射 {@code resolve(engine).listDirectories(...)}，条目浏览映射 {@code listEntries(...)}。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Component
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class StorageEngineMcpTools {

    private final StorageEngineService storageEngineService;
    private final McpToolResult result;

    public StorageEngineMcpTools(StorageEngineService storageEngineService, McpToolResult result) {
        this.storageEngineService = storageEngineService;
        this.result = result;
    }

    @McpTool(name = "storage_engine_list", description = "列出系统当前所有存储引擎及其状态")
    public McpSchema.CallToolResult storageEngineList() {
        return result.run("storage_engine_list", storageEngineService::listAll);
    }

    @McpTool(name = "storage_engine_get", description = "按 ID 查询单个存储引擎详情")
    public McpSchema.CallToolResult storageEngineGet(
            @McpToolParam(description = "存储引擎 ID") Long id) {
        return result.run("storage_engine_get", () -> storageEngineService.getById(id));
    }

    @McpTool(name = "storage_engine_create", description = "创建存储引擎（ALIST 类型需 baseUrl 与 token，LOCAL 类型需 localPath）")
    public McpSchema.CallToolResult storageEngineCreate(
            @McpToolParam(description = "存储引擎名称") String name,
            @McpToolParam(description = "引擎类型：ALIST 或 LOCAL") String engineType,
            @McpToolParam(description = "AList 服务器基础 URL（仅 ALIST 类型必填）", required = false) String baseUrl,
            @McpToolParam(description = "API 令牌（明文，仅 ALIST 类型必填，存储时 AES 加密）", required = false) String token,
            @McpToolParam(description = "本地文件系统目录路径（仅 LOCAL 类型必填）", required = false) String localPath) {
        return result.run("storage_engine_create", () -> {
            StorageEngineCreateDTO dto = new StorageEngineCreateDTO();
            dto.setName(name);
            dto.setEngineType(engineType);
            dto.setBaseUrl(baseUrl);
            dto.setToken(token);
            dto.setLocalPath(localPath);
            return storageEngineService.create(dto);
        });
    }

    @McpTool(name = "storage_engine_update", description = "更新存储引擎（仅更新提供的字段，engineType 创建后不可更改）")
    public McpSchema.CallToolResult storageEngineUpdate(
            @McpToolParam(description = "存储引擎 ID") Long id,
            @McpToolParam(description = "存储引擎名称", required = false) String name,
            @McpToolParam(description = "AList 服务器基础 URL", required = false) String baseUrl,
            @McpToolParam(description = "API 令牌（明文，存储时 AES 加密）", required = false) String token,
            @McpToolParam(description = "本地文件系统目录路径", required = false) String localPath) {
        return result.run("storage_engine_update", () -> {
            StorageEngineUpdateDTO dto = new StorageEngineUpdateDTO();
            dto.setName(name);
            dto.setBaseUrl(baseUrl);
            dto.setToken(token);
            dto.setLocalPath(localPath);
            return storageEngineService.update(id, dto);
        });
    }

    @McpTool(name = "storage_engine_delete", description = "删除存储引擎")
    public McpSchema.CallToolResult storageEngineDelete(
            @McpToolParam(description = "存储引擎 ID") Long id) {
        return result.run("storage_engine_delete", () -> {
            storageEngineService.delete(id);
            return Map.of("deleted", true);
        });
    }

    @McpTool(name = "storage_engine_test_connection", description = "测试存储引擎连接是否可用")
    public McpSchema.CallToolResult storageEngineTestConnection(
            @McpToolParam(description = "存储引擎 ID") Long id) {
        return result.run("storage_engine_test_connection",
            () -> Map.of("connected", storageEngineService.testConnection(id)));
    }

    @McpTool(name = "storage_engine_list_directories", description = "浏览存储引擎指定路径下的子目录列表")
    public McpSchema.CallToolResult storageEngineListDirectories(
            @McpToolParam(description = "存储引擎 ID") Long id,
            @McpToolParam(description = "目录路径（默认 /）", required = false) String path) {
        return result.run("storage_engine_list_directories", () -> {
            StorageEngine engine = storageEngineService.getEntity(id);
            return storageEngineService.resolve(engine).listDirectories(engine, path == null ? "/" : path);
        });
    }

    @McpTool(name = "storage_engine_list_entries", description = "浏览存储引擎指定路径下的文件与目录条目列表")
    public McpSchema.CallToolResult storageEngineListEntries(
            @McpToolParam(description = "存储引擎 ID") Long id,
            @McpToolParam(description = "目录路径（默认 /）", required = false) String path) {
        return result.run("storage_engine_list_entries", () -> {
            StorageEngine engine = storageEngineService.getEntity(id);
            return storageEngineService.resolve(engine).listEntries(engine, path == null ? "/" : path);
        });
    }
}
