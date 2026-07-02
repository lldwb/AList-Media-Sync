package top.lldwb.alistmediasync.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.lldwb.alistmediasync.common.dto.ApiResult;
import top.lldwb.alistmediasync.common.dto.DiagnosticResultVO;
import top.lldwb.alistmediasync.common.service.DiagnosticService;

/**
 * 诊断包生成 Controller
 * <p>
 * 受 {@code AuthInterceptor} 认证保护（路径以 {@code /api/} 开头且不在排除列表）。
 * 仅触发 {@link DiagnosticService}，不承载业务逻辑（章程原则 I）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@RestController
@RequestMapping("/api/diagnostics")
@Tag(name = "诊断", description = "系统诊断包生成")
public class DiagnosticController {

    private final DiagnosticService diagnosticService;

    public DiagnosticController(DiagnosticService diagnosticService) {
        this.diagnosticService = diagnosticService;
    }

    /**
     * 一键生成诊断包
     *
     * @return 诊断生成结果（包含包路径和摘要路径）；失败时 HTTP 500，部分成功时 HTTP 200
     */
    @PostMapping("/run")
    @Operation(summary = "一键生成诊断包", operationId = "run", description = "触发诊断服务生成包含系统状态、日志等信息的诊断包，返回包路径和摘要路径")
    @ApiResponse(responseCode = "200", description = "诊断包生成成功或部分成功，返回诊断结果")
    @ApiResponse(responseCode = "500", description = "诊断包生成失败")
    public ResponseEntity<ApiResult<DiagnosticResultVO>> run() {
        log.info("收到诊断生成请求");
        DiagnosticResultVO result = diagnosticService.generate();
        if (result.getStatus() == DiagnosticResultVO.Status.FAILED) {
            return ResponseEntity.status(500)
                .body(ApiResult.error(500, "诊断包生成失败", result));
        }
        return ResponseEntity.ok(ApiResult.success("诊断包已生成", result));
    }
}
