package top.lldwb.alistmediasync.common.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.config.TraceIdFilter;
import top.lldwb.alistmediasync.common.dto.DiagnosticResultVO;
import top.lldwb.alistmediasync.common.interceptor.AuthInterceptor;
import top.lldwb.alistmediasync.common.service.DiagnosticService;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link DiagnosticController} WebMvc 测试（T018）
 * <p>
 * 覆盖：返回 {@code ApiResult<DiagnosticResultVO>}，响应头包含 {@code X-Trace-Id}。
 * </p>
 */
@WebMvcTest(DiagnosticController.class)
@Import(TraceIdFilter.class)
@DisplayName("诊断端点 API 测试")
class DiagnosticControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagnosticService diagnosticService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @MockitoBean
    private AppProperties appProperties;

    @BeforeEach
    void setUp() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("POST /api/diagnostics/run 应返回诊断结果并携带 X-Trace-Id")
    void shouldRunDiagnostic() throws Exception {
        DiagnosticResultVO mockResult = DiagnosticResultVO.builder()
            .traceId("diag-test-001")
            .packagePath("diagnostics/latest")
            .summaryPath("diagnostics/latest/summary.md")
            .status(DiagnosticResultVO.Status.COMPLETED)
            .durationMs(1234L)
            .missingItems(List.of())
            .generatedAt(LocalDateTime.now())
            .build();
        when(diagnosticService.generate()).thenReturn(mockResult);

        mockMvc.perform(post("/api/diagnostics/run"))
            .andExpect(status().isOk())
            .andExpect(header().exists(TraceIdFilter.HEADER_TRACE_ID))
            .andExpect(jsonPath("$.code", is(200)))
            .andExpect(jsonPath("$.data.status", is("COMPLETED")))
            .andExpect(jsonPath("$.data.packagePath", is("diagnostics/latest")))
            .andExpect(jsonPath("$.data.summaryPath", is("diagnostics/latest/summary.md")))
            .andExpect(jsonPath("$.data.durationMs", notNullValue()));
    }

    @Test
    @DisplayName("诊断失败时仍应携带 X-Trace-Id")
    void shouldKeepTraceIdOnFailure() throws Exception {
        DiagnosticResultVO failedResult = DiagnosticResultVO.builder()
            .traceId("diag-test-002")
            .packagePath("diagnostics/latest")
            .summaryPath("")
            .status(DiagnosticResultVO.Status.FAILED)
            .durationMs(100L)
            .missingItems(List.of("生成异常"))
            .generatedAt(LocalDateTime.now())
            .build();
        when(diagnosticService.generate()).thenReturn(failedResult);

        mockMvc.perform(post("/api/diagnostics/run"))
            .andExpect(status().isInternalServerError())
            .andExpect(header().exists(TraceIdFilter.HEADER_TRACE_ID))
            .andExpect(jsonPath("$.code", is(500)));
    }
}
