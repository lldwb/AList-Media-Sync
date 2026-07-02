package top.lldwb.alistmediasync.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.lldwb.alistmediasync.common.dto.ApiResult;
import top.lldwb.alistmediasync.common.dto.DashboardStatsVO;
import top.lldwb.alistmediasync.common.service.DashboardService;

/**
 * 仪表板统计 API
 * <p>
 * 提供系统概览的数据聚合查询端点。
 * 不修改任何数据，纯查询操作。
 * </p>
 *
 * @author AList-Media-Sync
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "仪表盘", description = "系统概览数据聚合查询")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取仪表板统计数据
     *
     * @return 聚合统计结果
     */
    @GetMapping("/stats")
    @Operation(summary = "获取仪表板统计数据", operationId = "getStats", description = "聚合查询系统概览数据，包括任务总数、状态分布等统计信息")
    @ApiResponse(responseCode = "200", description = "查询成功，返回聚合统计结果")
    public ApiResult<DashboardStatsVO> getStats() {
        return ApiResult.success(dashboardService.getStats());
    }
}
