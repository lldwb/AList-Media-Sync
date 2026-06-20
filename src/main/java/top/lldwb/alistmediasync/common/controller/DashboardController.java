package top.lldwb.alistmediasync.common.controller;

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
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取仪表板统计数据
     *
     * @return 聚合统计结果
     */
    @GetMapping("/stats")
    public ApiResult<DashboardStatsVO> getStats() {
        return ApiResult.success(dashboardService.getStats());
    }
}
