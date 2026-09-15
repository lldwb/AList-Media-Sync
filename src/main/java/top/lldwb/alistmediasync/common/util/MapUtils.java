package top.lldwb.alistmediasync.common.util;

import java.util.Map;

/**
 * Map 取值工具类
 * <p>
 * 提供 {@code Map<String, Object>} 的通用读取方法，用于外部系统（Webhook 等）
 * 传入的原始参数 Map 的字段提取。
 * </p>
 *
 * @author AList-Media-Sync
 */
public final class MapUtils {

    private MapUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 读取 Map 中指定 key 的字符串值
     *
     * @param data 源 Map
     * @param key  键
     * @return 值的 {@code toString()}；值为 null 时返回 null
     */
    public static String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }
}
