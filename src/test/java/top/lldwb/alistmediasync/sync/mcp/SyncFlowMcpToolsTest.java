package top.lldwb.alistmediasync.sync.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import top.lldwb.alistmediasync.common.enums.ConflictStrategy;
import top.lldwb.alistmediasync.common.enums.TargetFormat;
import top.lldwb.alistmediasync.common.mcp.McpToolResult;
import top.lldwb.alistmediasync.sync.dto.SyncTaskCreateDTO;
import top.lldwb.alistmediasync.sync.dto.SyncTaskVO;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.service.SyncService;
import top.lldwb.alistmediasync.sync.service.SyncTaskManageService;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 同步流程级快捷工具单元测试
 * <p>
 * 覆盖"创建同步任务 → 立即手动触发执行"的一次性流程（FR-013）、异步提交（FR-014）、
 * 错误处理（FR-015）与 traceId 注入（FR-010）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("同步流程级快捷工具测试")
class SyncFlowMcpToolsTest {

    private SyncTaskManageService manageService;
    private SyncService syncService;
    private SyncFlowMcpTools tools;

    @BeforeEach
    void setUp() {
        manageService = mock(SyncTaskManageService.class);
        syncService = mock(SyncService.class);
        tools = new SyncFlowMcpTools(manageService, syncService, new McpToolResult(new JsonMapper()));
    }

    private String textOf(McpSchema.CallToolResult r) {
        return ((McpSchema.TextContent) r.content().get(0)).text();
    }

    @Test
    @DisplayName("sync_flow_create_and_execute 应创建任务并立即异步触发，返回任务 ID（FR-014）")
    void shouldCreateAndExecuteFlow() {
        SyncTaskVO vo = new SyncTaskVO();
        vo.setId(7L);
        SyncTask task = new SyncTask();
        task.setId(7L);
        when(manageService.create(any(SyncTaskCreateDTO.class))).thenReturn(vo);
        when(manageService.getEntity(7L)).thenReturn(task);

        McpSchema.CallToolResult r = tools.syncFlowCreateAndExecute("一键同步", 1L, 2L, "/src", "/dst",
            null, null, null, null, null, null, null, null);

        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"taskId\":7"));
        verify(manageService).create(any(SyncTaskCreateDTO.class));
        verify(manageService).executeManually(7L);
        verify(syncService).executeSyncTask(task);
    }

    @Test
    @DisplayName("sync_flow_create_and_execute 应将可选参数映射到 DTO")
    void shouldMapParams() {
        SyncTaskVO vo = new SyncTaskVO();
        vo.setId(1L);
        when(manageService.create(any(SyncTaskCreateDTO.class))).thenReturn(vo);
        when(manageService.getEntity(1L)).thenReturn(new SyncTask());

        tools.syncFlowCreateAndExecute("一键同步", 1L, 2L, "/src", "/dst",
            "FULL", true, "MP4", "OVERWRITE", "*.tmp", "INTERVAL", null, 3600);

        ArgumentCaptor<SyncTaskCreateDTO> captor = ArgumentCaptor.forClass(SyncTaskCreateDTO.class);
        verify(manageService).create(captor.capture());
        SyncTaskCreateDTO dto = captor.getValue();
        assertEquals(SyncTask.SyncMode.FULL, dto.getSyncMode());
        assertEquals(Boolean.TRUE, dto.getTranscodeEnabled());
        assertEquals(TargetFormat.MP4, dto.getTargetFormat());
        assertEquals(ConflictStrategy.OVERWRITE, dto.getConflictStrategy());
        assertEquals(SyncTask.ScheduleType.INTERVAL, dto.getScheduleType());
        assertEquals(3600, dto.getIntervalSeconds());
    }

    @Test
    @DisplayName("任务运行中触发失败应返回统一错误结构（409）且不执行异步同步")
    void shouldReturn409WhenTaskRunning() {
        SyncTaskVO vo = new SyncTaskVO();
        vo.setId(1L);
        when(manageService.create(any(SyncTaskCreateDTO.class))).thenReturn(vo);
        doThrow(new IllegalStateException("任务正在执行中，请稍后再试")).when(manageService).executeManually(1L);

        McpSchema.CallToolResult r = tools.syncFlowCreateAndExecute("任务", 1L, 2L, "/src", "/dst",
            null, null, null, null, null, null, null, null);

        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":409"));
        verify(syncService, never()).executeSyncTask(any());
    }

    @Test
    @DisplayName("工具调用应在 MDC 注入 module=mcp 与 operation=sync_flow_create_and_execute（FR-010）")
    void shouldInjectTraceContext() {
        SyncTaskVO vo = new SyncTaskVO();
        vo.setId(1L);
        when(manageService.create(any(SyncTaskCreateDTO.class))).thenAnswer(invocation -> {
            assertEquals("mcp", MDC.get("module"));
            assertEquals("sync_flow_create_and_execute", MDC.get("operation"));
            assertNotNull(MDC.get("traceId"));
            return vo;
        });
        when(manageService.getEntity(1L)).thenReturn(new SyncTask());

        tools.syncFlowCreateAndExecute("任务", 1L, 2L, "/src", "/dst",
            null, null, null, null, null, null, null, null);

        assertNull(MDC.get("module"));
        assertNull(MDC.get("operation"));
    }
}
