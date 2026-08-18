package top.lldwb.alistmediasync.storage.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import top.lldwb.alistmediasync.common.mcp.McpToolResult;
import top.lldwb.alistmediasync.storage.dto.storage.StorageEngineCreateDTO;
import top.lldwb.alistmediasync.storage.dto.storage.StorageEngineUpdateDTO;
import top.lldwb.alistmediasync.storage.dto.storage.StorageEngineVO;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;
import top.lldwb.alistmediasync.sync.dto.sync.DirectoryEntryVO;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 存储引擎 MCP 工具单元测试
 * <p>
 * 覆盖 8 个工具的参数映射、结果封装、错误处理（统一错误结构 FR-015）、
 * 敏感字段脱敏（FR-011）与 traceId/module/operation 注入（FR-010）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("存储引擎 MCP 工具测试")
class StorageEngineMcpToolsTest {

    private StorageEngineService storageEngineService;
    private StorageEngineStrategy strategy;
    private StorageEngineMcpTools tools;

    @BeforeEach
    void setUp() {
        storageEngineService = mock(StorageEngineService.class);
        strategy = mock(StorageEngineStrategy.class);
        tools = new StorageEngineMcpTools(storageEngineService, new McpToolResult(new JsonMapper()));
    }

    private String textOf(McpSchema.CallToolResult r) {
        return ((McpSchema.TextContent) r.content().get(0)).text();
    }

    @Test
    @DisplayName("storage_engine_list 成功返回引擎列表 JSON")
    void shouldListEngines() {
        StorageEngineVO vo = new StorageEngineVO();
        vo.setId(1L);
        vo.setName("主 AList 服务器");
        vo.setEngineType("ALIST");
        vo.setStatus("ONLINE");
        when(storageEngineService.listAll()).thenReturn(List.of(vo));

        McpSchema.CallToolResult r = tools.storageEngineList();

        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"name\":\"主 AList 服务器\""));
        verify(storageEngineService).listAll();
    }

    @Test
    @DisplayName("storage_engine_get 成功返回引擎详情")
    void shouldGetEngineById() {
        StorageEngineVO vo = new StorageEngineVO();
        vo.setId(1L);
        vo.setName("本地存储");
        when(storageEngineService.getById(1L)).thenReturn(vo);

        McpSchema.CallToolResult r = tools.storageEngineGet(1L);

        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"id\":1"));
        verify(storageEngineService).getById(1L);
    }

    @Test
    @DisplayName("storage_engine_get 不存在的 ID 应返回统一错误结构（404）")
    void shouldReturnErrorWhenEngineNotFound() {
        when(storageEngineService.getById(99L))
            .thenThrow(new NoSuchElementException("存储引擎不存在：id=99"));

        McpSchema.CallToolResult r = tools.storageEngineGet(99L);

        assertTrue(r.isError());
        String text = textOf(r);
        assertTrue(text.contains("\"code\":404"));
        assertTrue(text.contains("存储引擎不存在"));
    }

    @Test
    @DisplayName("storage_engine_create 应将参数映射到 CreateDTO")
    void shouldMapCreateParams() {
        when(storageEngineService.create(any(StorageEngineCreateDTO.class)))
            .thenReturn(new StorageEngineVO());

        tools.storageEngineCreate("主 AList 服务器", "ALIST", "https://alist.example.com", "secret-token", null);

        ArgumentCaptor<StorageEngineCreateDTO> captor = ArgumentCaptor.forClass(StorageEngineCreateDTO.class);
        verify(storageEngineService).create(captor.capture());
        StorageEngineCreateDTO dto = captor.getValue();
        assertEquals("主 AList 服务器", dto.getName());
        assertEquals("ALIST", dto.getEngineType());
        assertEquals("https://alist.example.com", dto.getBaseUrl());
        assertEquals("secret-token", dto.getToken());
        assertNull(dto.getLocalPath());
    }

    @Test
    @DisplayName("storage_engine_update 应将参数映射到 UpdateDTO")
    void shouldMapUpdateParams() {
        when(storageEngineService.update(any(Long.class), any(StorageEngineUpdateDTO.class)))
            .thenReturn(new StorageEngineVO());

        tools.storageEngineUpdate(1L, "新名称", "https://new.example.com", null, "/data/new");

        ArgumentCaptor<StorageEngineUpdateDTO> captor = ArgumentCaptor.forClass(StorageEngineUpdateDTO.class);
        verify(storageEngineService).update(eq(1L), captor.capture());
        StorageEngineUpdateDTO dto = captor.getValue();
        assertEquals("新名称", dto.getName());
        assertEquals("/data/new", dto.getLocalPath());
        assertNull(dto.getToken());
    }

    @Test
    @DisplayName("storage_engine_delete 返回 {deleted:true} 并调用删除")
    void shouldDeleteEngine() {
        McpSchema.CallToolResult r = tools.storageEngineDelete(1L);
        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"deleted\":true"));
        verify(storageEngineService).delete(1L);
    }

    @Test
    @DisplayName("storage_engine_test_connection 返回连接结果")
    void shouldTestConnection() {
        when(storageEngineService.testConnection(1L)).thenReturn(true);
        McpSchema.CallToolResult r = tools.storageEngineTestConnection(1L);
        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"connected\":true"));
    }

    @Test
    @DisplayName("storage_engine_list_directories 应调用策略 listDirectories（默认路径 /）")
    void shouldListDirectories() {
        StorageEngine engine = new StorageEngine();
        when(storageEngineService.getEntity(1L)).thenReturn(engine);
        when(storageEngineService.resolve(engine)).thenReturn(strategy);
        when(strategy.listDirectories(engine, "/"))
            .thenReturn(List.of(new DirectoryEntryVO("music", "/music", true)));

        McpSchema.CallToolResult r = tools.storageEngineListDirectories(1L, null);

        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"name\":\"music\""));
        verify(strategy).listDirectories(engine, "/");
    }

    @Test
    @DisplayName("storage_engine_list_entries 应调用策略 listEntries")
    void shouldListEntries() {
        StorageEngine engine = new StorageEngine();
        when(storageEngineService.getEntity(1L)).thenReturn(engine);
        when(storageEngineService.resolve(engine)).thenReturn(strategy);
        when(strategy.listEntries(engine, "/video")).thenReturn(List.of());

        McpSchema.CallToolResult r = tools.storageEngineListEntries(1L, "/video");

        assertFalse(r.isError());
        verify(strategy).listEntries(engine, "/video");
    }

    @Test
    @DisplayName("非法参数（不支持的引擎类型）应返回统一错误结构（400）")
    void shouldReturn400ForInvalidEngineType() {
        when(storageEngineService.create(any(StorageEngineCreateDTO.class)))
            .thenThrow(new IllegalArgumentException("不支持的引擎类型：FTP，支持的类型：ALIST、LOCAL"));

        McpSchema.CallToolResult r = tools.storageEngineCreate("引擎", "FTP", null, null, null);

        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":400"));
        assertTrue(textOf(r).contains("不支持的引擎类型"));
    }

    @Test
    @DisplayName("工具调用应在 MDC 注入 module=mcp 与 operation=工具名及唯一 traceId（FR-010）")
    void shouldInjectTraceContext() {
        when(storageEngineService.listAll()).thenAnswer(invocation -> {
            assertEquals("mcp", MDC.get("module"));
            assertEquals("storage_engine_list", MDC.get("operation"));
            String traceId = MDC.get("traceId");
            assertNotNull(traceId);
            assertTrue(traceId.length() >= 8);
            return List.of();
        });

        tools.storageEngineList();

        // runWith 结束应清理 MDC，防止污染线程池
        assertNull(MDC.get("module"));
        assertNull(MDC.get("operation"));
        assertNull(MDC.get("traceId"));
    }

    @Test
    @DisplayName("返回结果 MUST NOT 泄露明文 token（FR-011，复用脱敏 VO）")
    void shouldNotExposeSensitiveToken() {
        StorageEngineVO vo = new StorageEngineVO();
        vo.setId(1L);
        vo.setName("带令牌引擎");
        vo.setBaseUrl("https://alist.example.com");
        when(storageEngineService.listAll()).thenReturn(List.of(vo));

        McpSchema.CallToolResult r = tools.storageEngineList();
        String text = textOf(r);
        assertFalse(text.contains("token"), "返回结果不应包含 token 字段或明文凭据");
        assertFalse(text.toLowerCase().contains("secret"));
    }
}
