package top.lldwb.alistmediasync.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PasswordEncryptionPostProcessor 单元测试
 * <p>
 * 覆盖：明文加密到内存、已加密跳过、空值警告、环境变量覆盖场景。
 * 参见 specs/005-standalone-bootstrap/contracts/password-encryption.md
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
    @DisplayName("已加密的密码（{bcrypt} 前缀）应跳过加密")
    void shouldSkipAlreadyEncryptedPassword() {
        String alreadyEncrypted = "{bcrypt}$2a$10$AtWuyDKJC0K5FXjtF/vhZ.HEk2ZoLWMpyzlsOc3ac4wxY7D5Y2Yke";
        String result = processor.encryptIfPlain(alreadyEncrypted);

        assertEquals(alreadyEncrypted, result, "已加密密码应原样返回");
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
    @DisplayName("密码值仅包含 {bcrypt} 前缀但无有效哈希应被重新加密")
    void shouldReEncryptInvalidBcryptPrefix() {
        String invalidBcrypt = "{bcrypt}invalid_hash";
        String result = processor.encryptIfPlain(invalidBcrypt);

        assertNotNull(result);
        assertTrue(result.startsWith("{bcrypt}"));
        assertNotEquals(invalidBcrypt, result, "无效的 BCrypt 哈希应被重新加密");
    }

    @Test
    @DisplayName("isPlainPassword 应正确判断明文和已加密密码")
    void shouldCorrectlyDetectPlainPassword() {
        assertTrue(processor.isPlainPassword("admin123"));
        assertTrue(processor.isPlainPassword("my_secret"));
        assertFalse(processor.isPlainPassword("{bcrypt}$2a$10$..."));
        assertFalse(processor.isPlainPassword(null));
        assertFalse(processor.isPlainPassword(""));
        assertFalse(processor.isPlainPassword("  "));
    }

    @Test
    @DisplayName("环境变量来源的明文密码同样应被加密（仅加密到内存）")
    void shouldEncryptEnvVarPlainPassword() {
        // 模拟环境变量来源的明文密码
        String envPlainPassword = "envSecret123";
        String encrypted = processor.encryptIfPlain(envPlainPassword);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("{bcrypt}"));
        assertTrue(BCrypt.checkpw(envPlainPassword, encrypted.substring(8)));
        // 加密值绝不写回 — 此测试验证加密逻辑本身，不涉及文件写入
    }
}
