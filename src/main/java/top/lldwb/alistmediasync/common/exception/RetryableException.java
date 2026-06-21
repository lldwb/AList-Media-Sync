package top.lldwb.alistmediasync.common.exception;

/**
 * 可重试异常标记接口
 * <p>
 * 实现此接口的异常被视为瞬时故障（如网络超时、API 临时不可用），
 * 自动重试逻辑通过 {@code instanceof RetryableException} 判断是否触发重试。
 * 未实现此接口的异常（如文件不存在 404、格式不支持、权限不足）
 * 直接标记为最终失败，不进行自动重试。
 * </p>
 *
 * <h3>实现此接口的异常</h3>
 * <ul>
 *   <li>网络超时（SocketTimeoutException 包装）</li>
 *   <li>HTTP 5xx 服务端错误</li>
 *   <li>连接拒绝（ConnectException 包装）</li>
 *   <li>AList API 临时不可用（503 Service Unavailable）</li>
 * </ul>
 *
 * <h3>不实现此接口的异常</h3>
 * <ul>
 *   <li>文件不存在（404）</li>
 *   <li>格式不支持</li>
 *   <li>权限不足（403）</li>
 *   <li>磁盘空间不足</li>
 * </ul>
 *
 * @author AList-Media-Sync
 */
public interface RetryableException {
}
