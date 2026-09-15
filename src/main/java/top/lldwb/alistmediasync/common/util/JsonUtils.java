package top.lldwb.alistmediasync.common.util;

import tools.jackson.databind.json.JsonMapper;

/**
 * JSON 序列化工具类
 * <p>
 * 提供跨模块统一的「对象转 JSON 字符串」静态方法，用于消除各业务模块中重复的私有
 * {@code toJson} 实现（原先分散在 {@code SyncService}、{@code TranscodeService} 等处）。
 * </p>
 * <p>
 * 语义基准：以 {@code TranscodeService.toJson} 的异常处理策略为准——序列化成功时返回
 * Jackson 输出的 JSON 字符串；序列化失败时<b>不抛异常</b>，降级返回 {@code value.toString()}
 * 兜底，且<b>不记录日志</b>。该降级策略用于保证执行记录（如 {@code failureDetails}）在
 * 极端情况下仍可写入，不因序列化失败中断主流程。
 * </p>
 * <p>
 * 注意：本项目 JSON 库为 <b>Jackson 3</b>（{@code tools.jackson.*}），
 * 调用方传入的必须是 Jackson 3 的 {@link JsonMapper}，而非 Jackson 2 的 {@code ObjectMapper}。
 * </p>
 *
 * @author AList-Media-Sync
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /**
     * 将对象序列化为 JSON 字符串。
     * <p>
     * 序列化失败时不抛出异常，直接返回 {@code value.toString()} 作为兜底值，行为与
     * 被替换的既有私有实现完全一致。
     * </p>
     *
     * @param mapper 调用方持有的 Jackson 3 {@link JsonMapper} 实例，用于复用其序列化配置
     * @param value  待序列化的对象，可为 {@code null}（此时序列化结果为字符串 {@code "null"}）
     * @return 序列化后的 JSON 字符串；序列化失败时返回 {@code value.toString()}
     */
    public static String toJson(JsonMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}
