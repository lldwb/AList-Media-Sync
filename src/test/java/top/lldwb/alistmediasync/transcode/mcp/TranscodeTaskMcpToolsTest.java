package top.lldwb.alistmediasync.transcode.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.lldwb.alistmediasync.common.mcp.McpToolResult;
import top.lldwb.alistmediasync.common.service.CleanupService;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.service.TranscodeService;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 转码任务 MCP 工具单元测试
 * <p>
 * 覆盖 8 个工具的参数映射、异步创建（FR-014）、批量删除/重试、错误处理（FR-015）
 * 与 traceId/module/operation 注入（FR-010）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("转码任务 MCP 工具测试")
class TranscodeTaskMcpToolsTest {

    private TranscodeService transcodeService;
    private CleanupService cleanupService;
    private TranscodeTaskMcpTools tools;

    @BeforeEach
    void setUp() {
        transcodeService = mock(TranscodeService.class);
        cleanupService = mock(CleanupService.class);
        tools = new TranscodeTaskMcpTools(transcodeService, cleanupService, new McpToolResult(new JsonMapper()));
    }

    private String textOf(McpSchema.CallToolResult r) {
        return ((McpSchema.TextContent) r.content().get(0)).text();
    }

    private TranscodeTask newTask() {
        TranscodeTask task = new TranscodeTask();
        task.setId(1L);
        task.setSourceFilePath("/recordings/live.flv");
        task.setTargetFilePath("/media/live.mp4");
        task.setTargetFormat(TranscodeTask.TargetFormat.MP4);
        task.setStatus(TranscodeTask.TranscodeStatus.PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        return task;
    }

    @Test
    @DisplayName("transcode_task_list 成功返回任务列表")
    void shouldListTasks() {
        when(transcodeService.listAll()).thenReturn(List.of());
        McpSchema.CallToolResult r = tools.transcodeTaskList();
        assertFalse(r.isError());
        verify(transcodeService).listAll();
    }

    @Test
    @DisplayName("transcode_task_get 不存在的 ID 应返回统一错误结构（404）")
    void shouldReturn404WhenNotFound() {
        when(transcodeService.getById(99L))
            .thenThrow(new NoSuchElementException("转码任务不存在：id=99"));
        McpSchema.CallToolResult r = tools.transcodeTaskGet(99L);
        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":404"));
    }

    @Test
    @DisplayName("transcode_task_create 应创建任务并异步执行（FR-014）")
    void shouldCreateAndExecuteAsync() {
        TranscodeTask task = newTask();
        when(transcodeService.createTask(any(), any(), any(), any(), any(), any(), anyBoolean()))
            .thenReturn(task);

        McpSchema.CallToolResult r = tools.transcodeTaskCreate("/recordings/live.flv", "/media/live.mp4",
            "MP4", 256000, 1L, 2L, false);

        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"id\":1"));
        verify(transcodeService).createTask(1L, 2L, "/recordings/live.flv", "/media/live.mp4",
            TranscodeTask.TargetFormat.MP4, 256000, false);
        verify(transcodeService).executeAsync(task);
    }

    @Test
    @DisplayName("transcode_task_create 非法目标格式应返回统一错误结构（400）")
    void shouldReturn400ForInvalidFormat() {
        when(transcodeService.createTask(any(), any(), any(), any(), any(), any(), anyBoolean()))
            .thenThrow(new IllegalArgumentException("没有枚举常量 WAV"));

        McpSchema.CallToolResult r = tools.transcodeTaskCreate("/src.flv", "/dst.mp4", "WAV",
            null, null, null, null);

        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":400"));
        verify(transcodeService, never()).executeAsync(any());
    }

    @Test
    @DisplayName("transcode_task_retry 返回重试成功标记")
    void shouldRetry() {
        McpSchema.CallToolResult r = tools.transcodeTaskRetry(1L);
        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"success\":true"));
        verify(transcodeService).retry(1L);
    }

    @Test
    @DisplayName("transcode_task_cleanup_temp 返回删除数量")
    void shouldCleanupTemp() {
        when(cleanupService.manualCleanup()).thenReturn(3L);
        McpSchema.CallToolResult r = tools.transcodeTaskCleanupTemp();
        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"deletedCount\":3"));
        verify(cleanupService).manualCleanup();
    }

    @Test
    @DisplayName("transcode_task_delete_failed 应删除失败状态任务")
    void shouldDeleteFailed() {
        when(transcodeService.deleteByStatusIn(anyList())).thenReturn(2);
        McpSchema.CallToolResult r = tools.transcodeTaskDeleteFailed();
        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"deletedCount\":2"));
    }

    @Test
    @DisplayName("transcode_task_delete_completed 应删除已完成任务")
    void shouldDeleteCompleted() {
        when(transcodeService.deleteByStatusIn(anyList())).thenReturn(5);
        McpSchema.CallToolResult r = tools.transcodeTaskDeleteCompleted();
        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"deletedCount\":5"));
    }

    @Test
    @DisplayName("transcode_task_retry_all 应对每个失败任务触发重试并返回提交数")
    void shouldRetryAll() {
        TranscodeTask task1 = newTask();
        task1.setId(10L);
        TranscodeTask task2 = newTask();
        task2.setId(20L);
        when(transcodeService.findByStatusIn(anyList())).thenReturn(List.of(task1, task2));

        McpSchema.CallToolResult r = tools.transcodeTaskRetryAll();

        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"submittedCount\":2"));
        verify(transcodeService).retry(10L);
        verify(transcodeService).retry(20L);
    }

    @Test
    @DisplayName("工具调用应在 MDC 注入 module=mcp 与 operation=工具名（FR-010）")
    void shouldInjectTraceContext() {
        when(transcodeService.listAll()).thenAnswer(invocation -> {
            assertEquals("mcp", MDC.get("module"));
            assertEquals("transcode_task_list", MDC.get("operation"));
            assertNotNull(MDC.get("traceId"));
            return List.of();
        });

        tools.transcodeTaskList();

        assertNull(MDC.get("module"));
        assertNull(MDC.get("operation"));
    }
}
