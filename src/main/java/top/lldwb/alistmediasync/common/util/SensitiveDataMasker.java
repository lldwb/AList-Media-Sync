package top.lldwb.alistmediasync.common.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具
 * <p>
 * 提供字段名脱敏、值模式脱敏、HTTP 头脱敏和 URL 查询参数脱敏。
 * 与 spec FR-008/FR-009、SC-004 对齐：原始密码、Token、Cookie、Authorization、密钥、凭据不得出现在输出中；
 * 字段名和非敏感上下文应当保留以便排查。
 * </p>
 *
 * @author AList-Media-Sync
 */
public final class SensitiveDataMasker {

    /** 统一脱敏占位 */
    public static final String REDACTED = "***REDACTED***";
    /** 空值标记（与脱敏占位区分） */
    public static final String EMPTY = "EMPTY";

    /** 字段名敏感关键字（小写） */
    private static final String[] SENSITIVE_KEY_PARTS = {
        "password", "passwd", "pwd",
        "token", "secret", "credential",
        "authorization", "auth",
        "cookie", "session",
        "apikey", "api-key", "api_key",
        "privatekey", "private-key", "private_key",
        "accesskey", "access-key", "access_key",
        "salt", "signature",
        "cryptokey", "crypto-key", "crypto_key"
    };

    /** Authorization 头形式（Bearer/Basic xxx） */
    private static final Pattern AUTH_HEADER_VALUE = Pattern.compile("(?i)^(Bearer|Basic|Digest)\\s+.+$");

    /** JSON 风格的敏感键值对： "password" : "xxx" */
    private static final Pattern JSON_SENSITIVE_KV = Pattern.compile(
        "(?i)(\"(?:" + String.join("|", SENSITIVE_KEY_PARTS) + ")[^\"]*\"\\s*:\\s*\")([^\"]*)(\")"
    );

    /** URL 查询串中的敏感参数 */
    private static final Pattern URL_QUERY_PARAM = Pattern.compile(
        "(?i)([?&](?:" + String.join("|", SENSITIVE_KEY_PARTS) + ")[^=]*=)([^&#]*)"
    );

    /** 长疑似 Token（连续 20 位以上非空白字符且非纯数字非纯字母） */
    private static final Pattern LIKELY_TOKEN = Pattern.compile("(?i)\\b(?=.*[A-Za-z])(?=.*\\d)[A-Za-z0-9._+/=\\-]{32,}\\b");

    private SensitiveDataMasker() {
        // 工具类禁止实例化
    }

    /**
     * 判断字段名是否敏感
     *
     * @param fieldName 字段名（可能为 null）
     * @return 命中敏感关键字返回 true
     */
    public static boolean isSensitiveKey(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        for (String part : SENSITIVE_KEY_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据字段名脱敏值；空值标记为 {@link #EMPTY}，敏感字段脱敏，其他字段保留原值。
     *
     * @param fieldName 字段名
     * @param value     字段值
     * @return 脱敏后的展示值
     */
    public static String maskByKey(String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            return EMPTY;
        }
        if (isSensitiveKey(fieldName)) {
            return REDACTED;
        }
        // 即使字段名不敏感，但值疑似 Bearer/Basic Token 也脱敏
        if (AUTH_HEADER_VALUE.matcher(value.trim()).matches()) {
            return REDACTED;
        }
        return value;
    }

    /**
     * 脱敏一段任意文本（JSON、日志、配置片段），覆盖：
     * <ul>
     *   <li>JSON/YAML 风格敏感键值对</li>
     *   <li>URL 查询串敏感参数</li>
     *   <li>明显的 Authorization 头值</li>
     *   <li>疑似长 Token（含字母+数字的 32+ 字符串）</li>
     * </ul>
     *
     * @param text 任意文本
     * @return 脱敏后的文本
     */
    public static String maskText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        result = JSON_SENSITIVE_KV.matcher(result).replaceAll("$1" + REDACTED + "$3");
        result = URL_QUERY_PARAM.matcher(result).replaceAll("$1" + REDACTED);
        result = maskAuthorizationLine(result);
        result = LIKELY_TOKEN.matcher(result).replaceAll(REDACTED);
        return result;
    }

    /**
     * 脱敏 URL 中的查询参数（保留路径与非敏感参数）
     *
     * @param url 原始 URL
     * @return 脱敏后的 URL
     */
    public static String maskUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        try {
            URI uri = new URI(url);
            String query = uri.getRawQuery();
            if (query == null) {
                return url;
            }
            String masked = URL_QUERY_PARAM.matcher("?" + query).replaceAll("$1" + REDACTED);
            // 去掉前面的 '?'
            if (masked.startsWith("?")) {
                masked = masked.substring(1);
            }
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), masked, uri.getFragment()).toString();
        } catch (URISyntaxException e) {
            // 解析失败回退到通用文本脱敏
            return URL_QUERY_PARAM.matcher(url).replaceAll("$1" + REDACTED);
        }
    }

    /**
     * 对 Map 中的敏感字段做脱敏（保留键、替换值），返回新 Map（保留键顺序）
     *
     * @param source 原始键值对
     * @return 脱敏后的键值对
     */
    public static Map<String, String> maskMap(Map<String, String> source) {
        Map<String, String> masked = new LinkedHashMap<>();
        if (source == null) {
            return masked;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            masked.put(entry.getKey(), maskByKey(entry.getKey(), entry.getValue()));
        }
        return masked;
    }

    /**
     * 单独脱敏 HTTP Authorization / Cookie / Set-Cookie 等头部行
     */
    private static String maskAuthorizationLine(String text) {
        Pattern headerLine = Pattern.compile(
            "(?im)^(Authorization|Cookie|Set-Cookie|X-Auth-Token|X-API-Key|Proxy-Authorization)\\s*:\\s*.+$"
        );
        Matcher m = headerLine.matcher(text);
        return m.replaceAll(matchResult -> matchResult.group(1) + ": " + Matcher.quoteReplacement(REDACTED));
    }
}
