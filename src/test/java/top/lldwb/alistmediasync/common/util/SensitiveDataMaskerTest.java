package top.lldwb.alistmediasync.common.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SensitiveDataMasker} 单元测试（T050）
 * <p>
 * 覆盖：
 * <ul>
 *   <li>字段名脱敏（password/token/secret/key/authorization/cookie/credential）</li>
 *   <li>值模式脱敏（Bearer/Basic Token、长疑似 Token）</li>
 *   <li>空值识别（区分 EMPTY 与 REDACTED）</li>
 *   <li>URL 查询参数脱敏</li>
 * </ul>
 * </p>
 */
class SensitiveDataMaskerTest {

    @Test
    void isSensitiveKey_命中各类敏感关键字() {
        assertTrue(SensitiveDataMasker.isSensitiveKey("password"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("app.auth.password"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("apiToken"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("alist.token"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("client_secret"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("private_key"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("Authorization"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("cookie"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("aws_credential"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("crypto-key"));
    }

    @Test
    void isSensitiveKey_非敏感字段不应误报() {
        assertFalse(SensitiveDataMasker.isSensitiveKey("server.port"));
        assertFalse(SensitiveDataMasker.isSensitiveKey("alist.base-url"));
        assertFalse(SensitiveDataMasker.isSensitiveKey("app.data-dir"));
    }

    @Test
    void maskByKey_敏感字段非空值脱敏为REDACTED() {
        assertEquals(SensitiveDataMasker.REDACTED,
            SensitiveDataMasker.maskByKey("password", "plaintext123"));
        assertEquals(SensitiveDataMasker.REDACTED,
            SensitiveDataMasker.maskByKey("alist.token", "abc-token-xyz"));
    }

    @Test
    void maskByKey_空值标记为EMPTY而非REDACTED() {
        assertEquals(SensitiveDataMasker.EMPTY,
            SensitiveDataMasker.maskByKey("password", ""));
        assertEquals(SensitiveDataMasker.EMPTY,
            SensitiveDataMasker.maskByKey("password", null));
        assertEquals(SensitiveDataMasker.EMPTY,
            SensitiveDataMasker.maskByKey("anyField", ""));
    }

    @Test
    void maskByKey_非敏感字段保留原值() {
        assertEquals("https://alist.example.com",
            SensitiveDataMasker.maskByKey("alist.base-url", "https://alist.example.com"));
        assertEquals("8080", SensitiveDataMasker.maskByKey("server.port", "8080"));
    }

    @Test
    void maskByKey_即使字段名安全但值像Bearer令牌也应脱敏() {
        assertEquals(SensitiveDataMasker.REDACTED,
            SensitiveDataMasker.maskByKey("X-Header-Foo", "Bearer abcdef1234567890"));
        assertEquals(SensitiveDataMasker.REDACTED,
            SensitiveDataMasker.maskByKey("X-Header-Foo", "Basic dXNlcjpwYXNzd29yZA=="));
    }

    @Test
    void maskText_应脱敏JSON风格的敏感键值对() {
        String input = "{\"password\":\"plaintext123\",\"name\":\"alice\"}";
        String out = SensitiveDataMasker.maskText(input);
        assertFalse(out.contains("plaintext123"));
        assertTrue(out.contains(SensitiveDataMasker.REDACTED));
        assertTrue(out.contains("alice"), "非敏感字段应保留");
    }

    @Test
    void maskText_应脱敏Authorization头() {
        String input = "Authorization: Bearer abc-token-xyz-12345\nUser-Agent: test";
        String out = SensitiveDataMasker.maskText(input);
        assertFalse(out.contains("abc-token-xyz-12345"));
        assertTrue(out.contains("Authorization: " + SensitiveDataMasker.REDACTED));
        assertTrue(out.contains("User-Agent: test"));
    }

    @Test
    void maskText_应脱敏Cookie头() {
        String input = "Cookie: session=abc123; user=alice";
        String out = SensitiveDataMasker.maskText(input);
        assertFalse(out.contains("abc123"));
        assertTrue(out.contains("Cookie: " + SensitiveDataMasker.REDACTED));
    }

    @Test
    void maskText_应脱敏URL查询串中的敏感参数() {
        String input = "GET /api?token=abc-secret-1234&page=1 HTTP/1.1";
        String out = SensitiveDataMasker.maskText(input);
        assertFalse(out.contains("abc-secret-1234"));
        assertTrue(out.contains(SensitiveDataMasker.REDACTED));
        assertTrue(out.contains("page=1"));
    }

    @Test
    void maskText_应识别疑似长Token() {
        String input = "Found tokenXYZ123abcXYZ4567890abcdefghij in payload";
        String out = SensitiveDataMasker.maskText(input);
        assertFalse(out.contains("tokenXYZ123abcXYZ4567890abcdefghij"));
    }

    @Test
    void maskUrl_应只对敏感查询参数脱敏() {
        String url = "https://alist.example.com/api/get?token=secret-abc-xyz-123&path=/movies";
        String masked = SensitiveDataMasker.maskUrl(url);
        assertFalse(masked.contains("secret-abc-xyz-123"));
        assertTrue(masked.contains("token=" + SensitiveDataMasker.REDACTED));
        assertTrue(masked.contains("path=/movies"));
        assertTrue(masked.startsWith("https://alist.example.com/"));
    }

    @Test
    void maskMap_保留键顺序并脱敏值() {
        Map<String, String> src = new LinkedHashMap<>();
        src.put("alist.base-url", "https://example.com");
        src.put("alist.token", "secret-token");
        src.put("server.port", "8080");
        src.put("app.auth.password", "plain123");

        Map<String, String> masked = SensitiveDataMasker.maskMap(src);
        assertEquals("https://example.com", masked.get("alist.base-url"));
        assertEquals(SensitiveDataMasker.REDACTED, masked.get("alist.token"));
        assertEquals("8080", masked.get("server.port"));
        assertEquals(SensitiveDataMasker.REDACTED, masked.get("app.auth.password"));
    }
}
