package top.lldwb.alistmediasync.ops;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.lldwb.alistmediasync.common.dto.DashboardStatsVO;
import top.lldwb.alistmediasync.common.mcp.McpToolResult;
import top.lldwb.alistmediasync.common.dto.DiagnosticResultVO;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 系统运维 MCP 工具单元测试
 * <p>
 * 覆盖仪表盘统计与诊断包生成工具的结果封装、错误处理（FR-015）
 * 与 traceId/module/operation 注入（FR-010）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("系统运维 MCP 工具测试")
class SystemMcpToolsTest {

    private DashboardService dashboardService;
    private DiagnosticService diagnosticService;
    private SystemMcpTools tools;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        diagnosticService = mock(DiagnosticService.class);
        tools = new SystemMcpTools(dashboardService, diagnosticService, new McpToolResult(new JsonMapper()));
    }

    private String textOf(McpSchema.CallToolResult r) {
        return ((McpSchema.TextContent) r.content().get(0)).text();
    }

    @Test
    @DisplayName("system_dashboard_stats 应返回仪表盘统计 JSON")
    void shouldReturnDashboardStats() {
        DashboardStatsVO stats = new DashboardStatsVO();
        when(dashboardService.getStats()).thenReturn(stats);

        McpSchema.CallToolResult r = tools.systemDashboardStats();

        assertFalse(r.isError());
        verify(dashboardService).getStats();
    }

    @Test
    @DisplayName("system_run_diagnostics 应返回诊断结果 JSON")
    void shouldRunDiagnostics() {
        DiagnosticResultVO resultVo = new DiagnosticResultVO();
        when(diagnosticService.generate()).thenReturn(resultVo);

        McpSchema.CallToolResult r = tools.systemRunDiagnostics();

        assertFalse(r.isError());
        verify(diagnosticService).generate();
    }

    @Test
    @DisplayName("诊断包生成失败应返回统一错误结构（500）")
    void shouldReturn500WhenDiagnosticsFail() {
        when(diagnosticService.generate())
            .thenThrow(new RuntimeException("诊断包生成失败：磁盘写入异常"));

        McpSchema.CallToolResult r = tools.systemRunDiagnostics();

        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":500"));
        assertTrue(textOf(r).contains("磁盘写入异常"));
    }

    @Test
    @DisplayName("工具调用应在 MDC 注入 module=mcp 与 operation=工具名（FR-010）")
    void shouldInjectTraceContext() {
        when(dashboardService.getStats()).thenAnswer(invocation -> {
            assertEquals("mcp", MDC.get("module"));
            assertEquals("system_dashboard_stats", MDC.get("operation"));
            assertNotNull(MDC.get("traceId"));
            return new DashboardStatsVO();
        });

        tools.systemDashboardStats();

        assertNull(MDC.get("module"));
        assertNull(MDC.get("operation"));
    }
}
