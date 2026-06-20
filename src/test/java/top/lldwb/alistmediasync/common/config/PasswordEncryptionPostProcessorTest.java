package top.lldwb.alistmediasync.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PasswordEncryptionPostProcessor 单元测试
 * <p>
 * 覆盖：明文加密到内存、空值警告、环境变量覆盖场景。
 * 简化后所有配置值均视为明文，不再识别 {bcrypt} 前缀。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("密码加密后处理器测试")
class PasswordEncryptionPostProcessorTest {

    private PasswordEncryptionPostProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PasswordEncryptionPostProcessor();
    }

    @Test
    @DisplayName("明文密码应被加密为 BCrypt 格式")
    void shouldEncryptPlainPassword() {
        String plainPassword = "test123";
        String encrypted = processor.encryptIfPlain(plainPassword);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("{bcrypt}"), "加密后的密码应以 {bcrypt} 前缀开头");
        String bcryptHash = encrypted.substring(8);
        assertTrue(BCrypt.checkpw(plainPassword, bcryptHash), "加密后的密码应能通过 BCrypt 验证");
    }

    @Test
    @DisplayName("空值应返回 null")
    void shouldReturnNullForEmptyValue() {
        assertNull(processor.encryptIfPlain(null));
        assertNull(processor.encryptIfPlain(""));
        assertNull(processor.encryptIfPlain("  ")); // 空白字符串视为空
    }

    @Test
    @DisplayName("不同明文密码应生成不同 BCrypt 哈希")
    void shouldGenerateDifferentHashesForDifferentPasswords() {
        String encrypted1 = processor.encryptIfPlain("password1");
        String encrypted2 = processor.encryptIfPlain("password2");

        assertNotEquals(encrypted1, encrypted2, "不同密码应生成不同的哈希值");
    }

    @Test
    @DisplayName("相同明文每次加密结果应不同（盐值随机）")
    void shouldGenerateDifferentHashesWithRandomSalt() {
        String encrypted1 = processor.encryptIfPlain("samePassword");
        String encrypted2 = processor.encryptIfPlain("samePassword");

        assertNotEquals(encrypted1, encrypted2, "相同密码每次加密应生成不同的哈希（盐值随机）");

        // 但两次加密结果都应能验证原始密码
        assertTrue(BCrypt.checkpw("samePassword", encrypted1.substring(8)));
        assertTrue(BCrypt.checkpw("samePassword", encrypted2.substring(8)));
    }

    @Test
    @DisplayName("中文密码应能正常加密")
    void shouldEncryptChinesePassword() {
        String chinesePassword = "你好世界123";
        String encrypted = processor.encryptIfPlain(chinesePassword);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("{bcrypt}"));
        assertTrue(BCrypt.checkpw(chinesePassword, encrypted.substring(8)));
    }

    @Test
    @DisplayName("特殊字符密码应能正常加密")
    void shouldEncryptSpecialCharPassword() {
        String specialPassword = "P@$$w0rd!#%^&*()_+-=[]{}|;':\",./<>?";
        String encrypted = processor.encryptIfPlain(specialPassword);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("{bcrypt}"));
        assertTrue(BCrypt.checkpw(specialPassword, encrypted.substring(8)));
    }

    @Test
    @DisplayName("{bcrypt} 前缀的旧格式密码应被当作明文重新加密")
    void shouldTreatBcryptPrefixAsPlainPassword() {
        String oldFormatPassword = "{bcrypt}$2a$10$AtWuyDKJC0K5FXjtF/vhZ.HEk2ZoLWMpyzlsOc3ac4wxY7D5Y2Yke";
        String result = processor.encryptIfPlain(oldFormatPassword);

        assertNotNull(result);
        assertTrue(result.startsWith("{bcrypt}"));
        // 旧格式密码被当作明文，加密后与原文不同
        assertNotEquals(oldFormatPassword, result, "旧版 {bcrypt} 格式密码应被当作明文重新加密");
    }

    @Test
    @DisplayName("每次启动（调用 encryptIfPlain）应生成不同的哈希值")
    void shouldGenerateDifferentHashOnEachStartup() {
        // 模拟两次启动
        String firstStartup = processor.encryptIfPlain("admin123");
        String secondStartup = processor.encryptIfPlain("admin123");

        assertNotEquals(firstStartup, secondStartup,
            "每次启动应生成不同的哈希值（随机盐值）");
    }

    @Test
    @DisplayName("环境变量来源的明文密码同样应被加密（仅加密到内存）")
    void shouldEncryptEnvVarPlainPassword() {
        String envPlainPassword = "envSecret123";
        String encrypted = processor.encryptIfPlain(envPlainPassword);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("{bcrypt}"));
        assertTrue(BCrypt.checkpw(envPlainPassword, encrypted.substring(8)));
    }
}
