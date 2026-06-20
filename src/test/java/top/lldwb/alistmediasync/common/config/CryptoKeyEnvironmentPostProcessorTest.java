package top.lldwb.alistmediasync.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Base64;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CryptoKeyEnvironmentPostProcessor 单元测试
 * <p>
 * 覆盖：有效密钥加载、空值回退、无效 Base64、长度错误、空白处理。
 * 通过直接调用 {@code postProcessEnvironment()} 验证密钥桥接逻辑。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("加密密钥环境后处理器测试")
class CryptoKeyEnvironmentPostProcessorTest {

    private static final String SYSTEM_PROPERTY_KEY = "alist.crypto.key";

    private CryptoKeyEnvironmentPostProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CryptoKeyEnvironmentPostProcessor();
    }

    @AfterEach
    void tearDown() {
        // 清理系统属性，避免测试间污染
        System.clearProperty(SYSTEM_PROPERTY_KEY);
    }

    /**
     * 创建一个包含 ALIST_CRYPTO_KEY 属性的 ConfigurableEnvironment
     */
    private ConfigurableEnvironment createEnvironment(String cryptoKey) {
        ConfigurableEnvironment env = new StandardEnvironment();
        if (cryptoKey != null) {
            env.getPropertySources().addFirst(
                new MapPropertySource("test-crypto-key",
                    Collections.singletonMap("ALIST_CRYPTO_KEY", cryptoKey))
            );
        }
        return env;
    }

    @Test
    @DisplayName("有效密钥（Base64, 32 字节）应设置系统属性")
    void shouldSetSystemPropertyForValidKey() {
        // 生成合法的 AES-256 密钥
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) keyBytes[i] = (byte) i;
        String validKey = Base64.getEncoder().encodeToString(keyBytes);

        ConfigurableEnvironment env = createEnvironment(validKey);

        // 调用前系统属性应为 null
        assertNull(System.getProperty(SYSTEM_PROPERTY_KEY));

        processor.postProcessEnvironment(env, null);

        // 调用后系统属性应被设置
        assertEquals(validKey, System.getProperty(SYSTEM_PROPERTY_KEY));
    }

    @Test
    @DisplayName("空字符串应不设置系统属性且不抛异常")
    void shouldNotSetSystemPropertyForEmptyString() {
        ConfigurableEnvironment env = createEnvironment("");

        assertDoesNotThrow(() -> processor.postProcessEnvironment(env, null));
        assertNull(System.getProperty(SYSTEM_PROPERTY_KEY));
    }

    @Test
    @DisplayName("null 值应不设置系统属性且不抛异常")
    void shouldNotSetSystemPropertyForNull() {
        ConfigurableEnvironment env = createEnvironment(null);

        assertDoesNotThrow(() -> processor.postProcessEnvironment(env, null));
        assertNull(System.getProperty(SYSTEM_PROPERTY_KEY));
    }

    @Test
    @DisplayName("纯空白字符串应不设置系统属性且不抛异常")
    void shouldNotSetSystemPropertyForBlankString() {
        ConfigurableEnvironment env = createEnvironment("   ");

        assertDoesNotThrow(() -> processor.postProcessEnvironment(env, null));
        assertNull(System.getProperty(SYSTEM_PROPERTY_KEY));
    }

    @Test
    @DisplayName("无效 Base64 应抛出 IllegalArgumentException")
    void shouldThrowExceptionForInvalidBase64() {
        ConfigurableEnvironment env = createEnvironment("!!!not-valid-base64!!!");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> processor.postProcessEnvironment(env, null));
        assertTrue(ex.getMessage().contains("Base64"));
    }

    @Test
    @DisplayName("Base64 解码后长度不足 32 字节应抛 IllegalArgumentException")
    void shouldThrowExceptionForKeyTooShort() {
        // 16 字节密钥（仅 AES-128 长度）
        byte[] shortKey = new byte[16];
        for (int i = 0; i < 16; i++) shortKey[i] = (byte) i;
        String shortKeyBase64 = Base64.getEncoder().encodeToString(shortKey);

        ConfigurableEnvironment env = createEnvironment(shortKeyBase64);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> processor.postProcessEnvironment(env, null));
        assertTrue(ex.getMessage().contains("16 字节"));
    }

    @Test
    @DisplayName("Base64 解码后长度超标应抛 IllegalArgumentException")
    void shouldThrowExceptionForKeyTooLong() {
        // 64 字节密钥
        byte[] longKey = new byte[64];
        for (int i = 0; i < 64; i++) longKey[i] = (byte) i;
        String longKeyBase64 = Base64.getEncoder().encodeToString(longKey);

        ConfigurableEnvironment env = createEnvironment(longKeyBase64);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> processor.postProcessEnvironment(env, null));
        assertTrue(ex.getMessage().contains("64 字节"));
    }

    @Test
    @DisplayName("含前后空格的有效密钥应 trim 后正常处理")
    void shouldTrimWhitespaceAroundValidKey() {
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) keyBytes[i] = (byte) i;
        String validKey = Base64.getEncoder().encodeToString(keyBytes);
        String keyWithSpaces = "  " + validKey + "  ";

        ConfigurableEnvironment env = createEnvironment(keyWithSpaces);

        processor.postProcessEnvironment(env, null);

        // trim 后的值应被设置
        assertEquals(validKey, System.getProperty(SYSTEM_PROPERTY_KEY));
    }

    @Test
    @DisplayName("通过 openssl rand -base64 32 生成的密钥可正常加载")
    void shouldAcceptOpensslGeneratedKey() {
        // 模拟 openssl rand -base64 32 的输出格式（44 字符 Base64，解码为 32 字节）
        byte[] keyBytes = new byte[32];
        // 使用 SecureRandom 模拟真实密钥
        new java.security.SecureRandom().nextBytes(keyBytes);
        String opensslStyleKey = Base64.getEncoder().encodeToString(keyBytes);

        ConfigurableEnvironment env = createEnvironment(opensslStyleKey);

        processor.postProcessEnvironment(env, null);

        assertEquals(opensslStyleKey, System.getProperty(SYSTEM_PROPERTY_KEY));
        assertEquals(32, Base64.getDecoder().decode(opensslStyleKey).length);
    }
}
