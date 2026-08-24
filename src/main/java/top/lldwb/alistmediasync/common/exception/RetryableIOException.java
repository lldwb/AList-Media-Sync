package top.lldwb.alistmediasync.common.exception;

import java.io.IOException;

/**
 * 可重试的 IO 异常
 * <p>
 * 标识因瞬时网络/IO 故障导致的操作失败（如源文件下载、转码产物上传），
 * 实现 {@link RetryableException} 标记接口后，{@code RetryService.isRetryable} 返回 true，
 * 转码流程会对该类异常执行指数退避自动重试；业务逻辑错误（如格式不支持）不实现该接口，不重试。
 * </p>
 *
 * @author AList-Media-Sync
 */
public class RetryableIOException extends IOException implements RetryableException {

    public RetryableIOException(String message) {
        super(message);
    }

    public RetryableIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
