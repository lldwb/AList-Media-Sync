package top.lldwb.alistmediasync.common.controller;

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
