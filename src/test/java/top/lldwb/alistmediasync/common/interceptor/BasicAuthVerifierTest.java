package top.lldwb.alistmediasync.common.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import top.lldwb.alistmediasync.common.config.AppProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic 认证校验器单元测试
 * <p>
 * {@link BasicAuthVerifier} 是 HTTP（{@link AuthInterceptor}）与 WebSocket 握手
 * （{@link WebSocketAuthInterceptor}）共用的凭据校验流程，本测试直接覆盖校验器本身，
 * 逐条验证六个 {@link BasicAuthVerifier.Status} 取值与两类
 * {@link IllegalArgumentException} 抛出路径：
 * </p>
 * <ul>
 *   <li>正向：合法 Basic 凭据（用户名匹配 + BCrypt 校验通过）</li>
 *   <li>异常：请求头缺失 / 非 Basic 前缀 / 非法 Base64 / 缺冒号 / 用户名不匹配 /
 *       存储密码非 {bcrypt} 前缀 / 密码错误 / BCrypt 哈希形态非法</li>
 *   <li>边界：请求头为 null、空 Base64 载荷、空用户名、密码自身含冒号</li>
 * </ul>
 * <p>
 * 校验器只返回状态，拒绝响应的写法在调用方（拦截器）中，因此本测试断言
 * {@link BasicAuthVerifier.Result} 的状态与用户名回传值，不断言响应行为。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("Basic 认证校验器测试")
class BasicAuthVerifierTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String VALID_PASSWORD = "admin123";
    private static final String VALID_BCRYPT_HASH = ENCODER.encode(VALID_PASSWORD);

    private AppProperties appProperties;
    private BasicAuthVerifier verifier;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getAuth().setUsername("admin");
        appProperties.getAuth().setPassword("{bcrypt}" + VALID_BCRYPT_HASH);
        verifier = new BasicAuthVerifier(appProperties);
    }

    // ================================================================
    // 正向场景
    // ================================================================

    @Test
    @DisplayName("合法 Basic 凭据应校验通过并回传用户名")
    void shouldReturnSuccessWithValidCredentials() {
        BasicAuthVerifier.Result result = verifier.verify(basicHeader("admin", VALID_PASSWORD));

        assertEquals(BasicAuthVerifier.Status.SUCCESS, result.status());
        assertEquals("admin", result.username(), "校验通过时应回传解析出的用户名");
    }

    // ================================================================
    // 请求头缺失 / 形态不符
    // ================================================================

    @Test
    @DisplayName("Authorization 请求头为 null 应返回 MISSING_CREDENTIALS 且用户名为 null")
    void shouldReturnMissingCredentialsWhenHeaderIsNull() {
        BasicAuthVerifier.Result result = verifier.verify(null);

        assertEquals(BasicAuthVerifier.Status.MISSING_CREDENTIALS, result.status());
        assertNull(result.username(), "凭据未解析出用户名时应回传 null");
    }

    @Test
    @DisplayName("非 Basic 前缀（Bearer）应返回 MISSING_CREDENTIALS")
    void shouldReturnMissingCredentialsWhenSchemeIsBearer() {
        BasicAuthVerifier.Result result = verifier.verify("Bearer token123");

        assertEquals(BasicAuthVerifier.Status.MISSING_CREDENTIALS, result.status());
        assertNull(result.username());
    }

    @Test
    @DisplayName("小写 basic 前缀当前实现判定为 MISSING_CREDENTIALS（大小写敏感，如实记录）")
    void shouldReturnMissingCredentialsWhenSchemeIsLowerCase() {
        // 如实记录现状：实现使用 startsWith("Basic ") 精确匹配，
        // 而 RFC 7235 规定 auth-scheme 大小写不敏感——小写前缀会被当作「无凭据」拒绝。
        String lowerCaseScheme = "basic " + base64("admin:" + VALID_PASSWORD);

        BasicAuthVerifier.Result result = verifier.verify(lowerCaseScheme);

        assertEquals(BasicAuthVerifier.Status.MISSING_CREDENTIALS, result.status());
    }

    @Test
    @DisplayName("Basic 后为非法 Base64 应原样抛出 IllegalArgumentException")
    void shouldThrowWhenBase64IsInvalid() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> verifier.verify("Basic not-valid-base64!!!"));

        assertTrue(e.getMessage().toLowerCase().contains("base64"),
            "异常信息应指出 Base64 非法，实际：" + e.getMessage());
    }

    // ================================================================
    // 凭据格式无效
    // ================================================================

    @Test
    @DisplayName("Base64 解码后无冒号应返回 MALFORMED_CREDENTIALS")
    void shouldReturnMalformedCredentialsWhenNoColon() {
        BasicAuthVerifier.Result result = verifier.verify("Basic " + base64("onlyUsername"));

        assertEquals(BasicAuthVerifier.Status.MALFORMED_CREDENTIALS, result.status());
        assertNull(result.username());
    }

    @Test
    @DisplayName("Basic 后为空载荷应返回 MALFORMED_CREDENTIALS")
    void shouldReturnMalformedCredentialsWhenPayloadIsEmpty() {
        BasicAuthVerifier.Result result = verifier.verify("Basic ");

        assertEquals(BasicAuthVerifier.Status.MALFORMED_CREDENTIALS, result.status());
    }

    // ================================================================
    // 用户名校验
    // ================================================================

    @Test
    @DisplayName("用户名不匹配应返回 USERNAME_MISMATCH 并回传实际用户名")
    void shouldReturnUsernameMismatchWhenUsernameDiffers() {
        BasicAuthVerifier.Result result = verifier.verify(basicHeader("wrongUser", VALID_PASSWORD));

        assertEquals(BasicAuthVerifier.Status.USERNAME_MISMATCH, result.status());
        assertEquals("wrongUser", result.username(), "应回传实际收到的用户名供调用方记录日志");
    }

    @Test
    @DisplayName("空用户名应返回 USERNAME_MISMATCH")
    void shouldReturnUsernameMismatchWhenUsernameIsEmpty() {
        BasicAuthVerifier.Result result = verifier.verify(basicHeader("", VALID_PASSWORD));

        assertEquals(BasicAuthVerifier.Status.USERNAME_MISMATCH, result.status());
        assertEquals("", result.username());
    }

    // ================================================================
    // 密码校验
    // ================================================================

    @Test
    @DisplayName("存储密码未以 {bcrypt} 前缀开头应返回 PASSWORD_NOT_BCRYPT（防御性检查）")
    void shouldReturnPasswordNotBcryptWhenStoredPasswordHasNoPrefix() {
        // 模拟 PasswordEncryptionPostProcessor 未生效：密码仍是明文
        appProperties.getAuth().setPassword(VALID_PASSWORD);
        verifier = new BasicAuthVerifier(appProperties);

        BasicAuthVerifier.Result result = verifier.verify(basicHeader("admin", VALID_PASSWORD));

        assertEquals(BasicAuthVerifier.Status.PASSWORD_NOT_BCRYPT, result.status());
        assertEquals("admin", result.username());
    }

    @Test
    @DisplayName("密码错误应返回 PASSWORD_MISMATCH")
    void shouldReturnPasswordMismatchWhenPasswordIsWrong() {
        BasicAuthVerifier.Result result = verifier.verify(basicHeader("admin", "wrongPassword"));

        assertEquals(BasicAuthVerifier.Status.PASSWORD_MISMATCH, result.status());
        assertEquals("admin", result.username());
    }

    @Test
    @DisplayName("{bcrypt} 前缀后哈希形态非法应原样抛出 IllegalArgumentException")
    void shouldThrowWhenBcryptHashIsMalformed() {
        // 如实记录现状：BCrypt.checkpw 对非法盐值抛 IllegalArgumentException，
        // 校验器不吞异常，由调用方（拦截器）按既有文案记录并拒绝。
        appProperties.getAuth().setPassword("{bcrypt}not-a-valid-hash");
        verifier = new BasicAuthVerifier(appProperties);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> verifier.verify(basicHeader("admin", VALID_PASSWORD)));

        assertTrue(e.getMessage().toLowerCase().contains("salt"),
            "异常信息应指出盐值非法，实际：" + e.getMessage());
    }

    // ================================================================
    // 边界：密码自身包含冒号
    // ================================================================

    @Test
    @DisplayName("密码自身包含冒号时应按首个冒号切分并校验通过")
    void shouldSplitOnlyOnFirstColonSoPasswordMayContainColon() {
        String passwordWithColon = "pw:with:colon";
        appProperties.getAuth().setPassword("{bcrypt}" + ENCODER.encode(passwordWithColon));
        verifier = new BasicAuthVerifier(appProperties);

        BasicAuthVerifier.Result result = verifier.verify(basicHeader("admin", passwordWithColon));

        assertEquals(BasicAuthVerifier.Status.SUCCESS, result.status(),
            "split(\":\", 2) 只按首个冒号切分，密码中的冒号应保留在密码段");
        assertEquals("admin", result.username());
    }

    // ================================================================
    // 测试辅助
    // ================================================================

    private static String basicHeader(String username, String password) {
        return "Basic " + base64(username + ":" + password);
    }

    private static String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
