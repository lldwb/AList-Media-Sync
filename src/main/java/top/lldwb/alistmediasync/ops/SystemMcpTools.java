package top.lldwb.alistmediasync.ops;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import top.lldwb.alistmediasync.common.mcp.McpToolResult;

/**
 * 系统运维模块 MCP 工具（FR-007）
 * <p>
 * 提供仪表盘统计查询与诊断包生成工具（复用 specs/009 诊断系统）。
 * 工具层仅做参数适配与结果封装，业务逻辑复用 {@link DashboardService} 与 {@link DiagnosticService}（FR-009）。
 * </p>
 * <p>
 * {@code system_run_diagnostics} 为只读操作，MUST NOT 触发同步/转码/Webhook 等业务副作用（章程原则 VII §7.10）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Component
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class SystemMcpTools {

    private final DashboardService dashboardService;
    private final DiagnosticService diagnosticService;
    private final McpToolResult result;

    public SystemMcpTools(DashboardService dashboardService, DiagnosticService diagnosticService,
                          McpToolResult result) {
        this.dashboardService = dashboardService;
        this.diagnosticService = diagnosticService;
        this.result = result;
    }

    @McpTool(name = "system_dashboard_stats", description = "查看系统仪表盘统计（任务/文件/存储等关键指标）")
    public McpSchema.CallToolResult systemDashboardStats() {
        return result.run("system_dashboard_stats", dashboardService::getStats);
    }

    @McpTool(name = "system_run_diagnostics", description = "生成诊断包（只读操作，返回诊断包路径与摘要）")
    public McpSchema.CallToolResult systemRunDiagnostics() {
        return result.run("system_run_diagnostics", diagnosticService::generate);
    }
}
