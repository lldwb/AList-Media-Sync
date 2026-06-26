package top.lldwb.alistmediasync.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.lldwb.alistmediasync.common.dto.ApiResult;
import top.lldwb.alistmediasync.common.util.DiskSpaceChecker;
import top.lldwb.alistmediasync.common.util.TraceContext;

import java.util.NoSuchElementException;

/**
 * 全局异常处理
 * <p>
 * 将 Service 层抛出的业务异常统一转换为 ApiResult 格式的 HTTP 响应。
 * </p>
 * <p>
 * 所有异常分支都会在 MDC 中写入 {@code errorType}，便于日志按错误分类筛选，
 * 同时 {@code X-Trace-Id} 响应头由 {@link TraceIdFilter} 写入，
 * 这里仅负责日志结构化与响应体生成。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 参数校验失败 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult<Void>> handleIllegalArgument(IllegalArgumentException e) {
        TraceContext.setErrorType("IllegalArgumentException");
        log.warn("参数校验失败：{}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(ApiResult.error(400, e.getMessage()));
    }

    /** 资源不存在 */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResult<Void>> handleNotFound(NoSuchElementException e) {
        TraceContext.setErrorType("NoSuchElementException");
        log.warn("资源不存在：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResult.error(404, e.getMessage()));
    }

    /** 任务正在执行中 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResult<Void>> handleConflict(IllegalStateException e) {
        TraceContext.setErrorType("IllegalStateException");
        log.warn("业务冲突：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResult.error(409, e.getMessage()));
    }

    /** 磁盘空间不足 */
    @ExceptionHandler(DiskSpaceChecker.InsufficientDiskSpaceException.class)
    public ResponseEntity<ApiResult<Void>> handleInsufficientDiskSpace(
        DiskSpaceChecker.InsufficientDiskSpaceException e) {
        TraceContext.setErrorType("InsufficientDiskSpaceException");
        log.error("磁盘空间不足：{}", e.getMessage(), e);
        return ResponseEntity.status(507)
            .body(ApiResult.error(507, e.getMessage()));
    }

    /** 未知异常（兜底） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleGeneral(Exception e) {
        TraceContext.setErrorType(e.getClass().getSimpleName());
        log.error("未处理的异常：{}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResult.error(500, "服务器内部错误：" + e.getMessage()));
    }
}
