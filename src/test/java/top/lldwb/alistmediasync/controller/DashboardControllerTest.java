package top.lldwb.alistmediasync.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import top.lldwb.alistmediasync.config.AppProperties;
import top.lldwb.alistmediasync.dto.DashboardStatsVO;
import top.lldwb.alistmediasync.interceptor.AuthInterceptor;
import top.lldwb.alistmediasync.service.DashboardService;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 仪表板 API 测试
 * <p>
 * 使用 @WebMvcTest 仅加载 DashboardController 和 Web 基础设施，
 * Mock DashboardService 以验证响应结构。
 * </p>
 *
 * @author AList-Media-Sync
 */
@WebMvcTest(DashboardController.class)
@DisplayName("仪表板 API 测试")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private AuthInterceptor authInterceptor;

    @MockBean
    private AppProperties appProperties;

    @BeforeEach
    void setUp() throws Exception {
        // 模拟认证拦截器放行（测试中不验证 Basic Auth）
        when(authInterceptor.preHandle(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(true);
    }

    @Test
    @DisplayName("GET /api/dashboard/stats 应返回正确结构")
    void shouldReturnDashboardStats() throws Exception {
        DashboardStatsVO mockStats = new DashboardStatsVO(
            2L,  // activeSyncTasks
            3L,  // pendingTranscodeTasks
            150L, // todayProcessedFiles
            96.5, // last24hSuccessRate
            5L,  // totalEngines
            4L   // totalWebhookRules
        );

        when(dashboardService.getStats()).thenReturn(mockStats);

        mockMvc.perform(get("/api/dashboard/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code", is(200)))
            .andExpect(jsonPath("$.message", is("操作成功")))
            .andExpect(jsonPath("$.data.activeSyncTasks", is(2)))
            .andExpect(jsonPath("$.data.pendingTranscodeTasks", is(3)))
            .andExpect(jsonPath("$.data.todayProcessedFiles", is(150)))
            .andExpect(jsonPath("$.data.last24hSuccessRate", is(96.5)))
            .andExpect(jsonPath("$.data.totalEngines", is(5)))
            .andExpect(jsonPath("$.data.totalWebhookRules", is(4)));
    }

    @Test
    @DisplayName("GET /api/dashboard/stats 无执行记录时应返回默认值")
    void shouldReturnDefaultsWhenNoData() throws Exception {
        DashboardStatsVO emptyStats = new DashboardStatsVO(
            0L, 0L, 0L, 100.0, 0L, 0L
        );

        when(dashboardService.getStats()).thenReturn(emptyStats);

        mockMvc.perform(get("/api/dashboard/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code", is(200)))
            .andExpect(jsonPath("$.data.activeSyncTasks", is(0)))
            .andExpect(jsonPath("$.data.last24hSuccessRate", is(100.0)));
    }
}
