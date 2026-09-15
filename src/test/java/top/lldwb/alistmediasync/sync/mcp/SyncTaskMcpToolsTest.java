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
import top.lldwb.alistmediasync.sync.dto.SyncTaskUpdateDTO;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.service.SyncService;
import top.lldwb.alistmediasync.sync.service.SyncTaskManageService;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 同步任务 MCP 工具单元测试
 * <p>
 * 覆盖 9 个工具的参数映射、枚举转换、异步触发（FR-014）、错误处理（FR-015）
 * 与 traceId/module/operation 注入（FR-010）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("同步任务 MCP 工具测试")
class SyncTaskMcpToolsTest {

    private SyncTaskManageService manageService;
    private SyncService syncService;
    private SyncTaskMcpTools tools;

    @BeforeEach
    void setUp() {
        manageService = mock(SyncTaskManageService.class);
        syncService = mock(SyncService.class);
        tools = new SyncTaskMcpTools(manageService, syncService, new McpToolResult(new JsonMapper()));
    }

    private String textOf(McpSchema.CallToolResult r) {
        return ((McpSchema.TextContent) r.content().get(0)).text();
    }

    @Test
    @DisplayName("sync_task_list 成功返回任务列表")
    void shouldListTasks() {
        when(manageService.listAll()).thenReturn(List.of());
        McpSchema.CallToolResult r = tools.syncTaskList();
        assertFalse(r.isError());
        verify(manageService).listAll();
    }

    @Test
    @DisplayName("sync_task_get 不存在的 ID 应返回统一错误结构（404）")
    void shouldReturn404WhenTaskNotFound() {
        when(manageService.getById(99L))
            .thenThrow(new NoSuchElementException("同步任务不存在：id=99"));
        McpSchema.CallToolResult r = tools.syncTaskGet(99L);
        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":404"));
    }

    @Test
    @DisplayName("sync_task_create 应将参数映射到 DTO 并转换枚举")
    void shouldMapCreateParams() {
        when(manageService.create(any(SyncTaskCreateDTO.class))).thenReturn(null);

        tools.syncTaskCreate("每日同步", 1L, 2L, "/src", "/dst",
            "FULL", true, "MP4", "OVERWRITE", "*.tmp", "CRON", "0 0 2 * * ?", 3600);

        ArgumentCaptor<SyncTaskCreateDTO> captor = ArgumentCaptor.forClass(SyncTaskCreateDTO.class);
        verify(manageService).create(captor.capture());
        SyncTaskCreateDTO dto = captor.getValue();
        assertEquals("每日同步", dto.getName());
        assertEquals(1L, dto.getSourceEngineId());
        assertEquals(2L, dto.getTargetEngineId());
        assertEquals(SyncTask.SyncMode.FULL, dto.getSyncMode());
        assertEquals(Boolean.TRUE, dto.getTranscodeEnabled());
        assertEquals(TargetFormat.MP4, dto.getTargetFormat());
        assertEquals(ConflictStrategy.OVERWRITE, dto.getConflictStrategy());
        assertEquals(SyncTask.ScheduleType.CRON, dto.getScheduleType());
        assertEquals("0 0 2 * * ?", dto.getCronExpression());
        assertEquals(3600, dto.getIntervalSeconds());
    }

    @Test
    @DisplayName("sync_task_create 可选参数为空时应保留 DTO 默认值")
    void shouldKeepDefaultsWhenOptionalParamsMissing() {
        when(manageService.create(any(SyncTaskCreateDTO.class))).thenReturn(null);

        tools.syncTaskCreate("默认任务", 1L, 2L, "/src", "/dst",
            null, null, null, null, null, null, null, null);

        ArgumentCaptor<SyncTaskCreateDTO> captor = ArgumentCaptor.forClass(SyncTaskCreateDTO.class);
        verify(manageService).create(captor.capture());
        SyncTaskCreateDTO dto = captor.getValue();
        assertEquals(SyncTask.SyncMode.NEW_ONLY, dto.getSyncMode());
        assertEquals(SyncTask.ScheduleType.MANUAL, dto.getScheduleType());
        assertEquals(Boolean.FALSE, dto.getTranscodeEnabled());
    }

    @Test
    @DisplayName("sync_task_create 非法枚举应返回统一错误结构（400）")
    void shouldReturn400ForInvalidEnum() {
        when(manageService.create(any(SyncTaskCreateDTO.class)))
            .thenThrow(new IllegalArgumentException("没有枚举常量"));

        McpSchema.CallToolResult r = tools.syncTaskCreate("任务", 1L, 2L, "/src", "/dst",
            "INVALID", null, null, null, null, null, null, null);

        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":400"));
    }

    @Test
    @DisplayName("sync_task_update 应将可选字段映射到 UpdateDTO")
    void shouldMapUpdateParams() {
        when(manageService.update(any(Long.class), any(SyncTaskUpdateDTO.class))).thenReturn(null);

        tools.syncTaskUpdate(1L, "新名称", null, null, null, null,
            "MOVE", null, null, "RENAME", null, "INTERVAL", null, 60);

        ArgumentCaptor<SyncTaskUpdateDTO> captor = ArgumentCaptor.forClass(SyncTaskUpdateDTO.class);
        verify(manageService).update(eq(1L), captor.capture());
        SyncTaskUpdateDTO dto = captor.getValue();
        assertEquals("新名称", dto.getName());
        assertEquals(SyncTask.SyncMode.MOVE, dto.getSyncMode());
        assertEquals(ConflictStrategy.RENAME, dto.getConflictStrategy());
        assertEquals(SyncTask.ScheduleType.INTERVAL, dto.getScheduleType());
        assertEquals(60, dto.getIntervalSeconds());
    }

    @Test
    @DisplayName("sync_task_execute 应异步提交并立即返回任务 ID（FR-014）")
    void shouldExecuteAsyncAndReturnTaskId() {
        SyncTask task = new SyncTask();
        task.setId(1L);
        when(manageService.getEntity(1L)).thenReturn(task);

        McpSchema.CallToolResult r = tools.syncTaskExecute(1L);

        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"taskId\":1"));
        verify(manageService).executeManually(1L);
        verify(syncService).executeSyncTask(task);
    }

    @Test
    @DisplayName("sync_task_execute 任务运行中应返回统一错误结构（409）")
    void shouldReturn409WhenTaskRunning() {
        doThrow(new IllegalStateException("任务正在执行中，请稍后再试"))
            .when(manageService).executeManually(1L);

        McpSchema.CallToolResult r = tools.syncTaskExecute(1L);

        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":409"));
        verify(syncService, never()).executeSyncTask(any());
    }

    @Test
    @DisplayName("sync_task_enable 与 disable 应切换调度状态")
    void shouldEnableAndDisable() {
        when(manageService.enable(1L)).thenReturn(null);
        when(manageService.disable(1L)).thenReturn(null);

        McpSchema.CallToolResult enabled = tools.syncTaskEnable(1L);
        McpSchema.CallToolResult disabled = tools.syncTaskDisable(1L);

        assertFalse(enabled.isError());
        assertFalse(disabled.isError());
        verify(manageService).enable(1L);
        verify(manageService).disable(1L);
    }

    @Test
    @DisplayName("sync_task_get_executions 应返回执行历史")
    void shouldGetExecutions() {
        when(manageService.getExecutions(1L)).thenReturn(List.of());
        McpSchema.CallToolResult r = tools.syncTaskGetExecutions(1L);
        assertFalse(r.isError());
        verify(manageService).getExecutions(1L);
    }

    @Test
    @DisplayName("工具调用应在 MDC 注入 module=mcp 与 operation=工具名（FR-010）")
    void shouldInjectTraceContext() {
        when(manageService.listAll()).thenAnswer(invocation -> {
            assertEquals("mcp", MDC.get("module"));
            assertEquals("sync_task_list", MDC.get("operation"));
            assertNotNull(MDC.get("traceId"));
            return List.of();
        });

        tools.syncTaskList();

        assertNull(MDC.get("module"));
        assertNull(MDC.get("operation"));
    }
}
