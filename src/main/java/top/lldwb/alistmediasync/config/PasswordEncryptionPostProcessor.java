package top.lldwb.alistmediasync.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Collections;
import java.util.Map;

/**
 * 密码自动加密后处理器
 * <p>
 * 实现 {@link EnvironmentPostProcessor} 接口，在 Spring Boot 环境准备阶段
 * 自动检测配置中的明文密码并使用 BCrypt 加密到内存。
 * </p>
 * <p>
 * 核心原则：
 * <ul>
 *   <li>加密值仅保存在内存中的 Spring Environment，绝不回写到 YAML 文件</li>
 *   <li>每次启动均重新执行检测与加密流程</li>
 *   <li>配置文件始终保持用户写入的原始值（明文或已加密格式均可）</li>
 *   <li>支持 YAML 文件和环境变量两种密码来源</li>
 * </ul>
 * </p>
 * <p>
 * 参见 specs/005-standalone-bootstrap/contracts/password-encryption.md
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
public class PasswordEncryptionPostProcessor implements EnvironmentPostProcessor {

    /** 密码配置键 */
    private static final String PASSWORD_KEY = "app.auth.password";

    /** BCrypt 前缀标识 */
    private static final String BCRYPT_PREFIX = "{bcrypt}";

    /** BCrypt 密码编码器（10 轮哈希，与 Spring Security 默认一致） */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    /** 内存属性源名称 */
    private static final String PROPERTY_SOURCE_NAME = "passwordEncryptionMemory";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 1. 读取 app.auth.password 属性
        String password = environment.getProperty(PASSWORD_KEY);

        // 2. 判断值类型
        if (password == null || password.isBlank()) {
            log.warn("认证密码未设置，管理后台将无法登录。请在 config/application.yaml 中配置 app.auth.password。");
            return;
        }

        if (password.startsWith(BCRYPT_PREFIX)) {
            String hash = password.substring(BCRYPT_PREFIX.length());
            if (BCRYPT_HASH_PATTERN.matcher(hash).matches()) {
                // 已加密且哈希格式有效 — 静默跳过
                return;
            }
            // 哈希格式无效，继续走加密流程
            log.warn("检测到无效的 BCrypt 哈希格式，将重新加密: {}", password);
        }

        // 3. 明文密码 — BCrypt 加密
        String encrypted = encryptIfPlain(password);
        if (encrypted == null) {
            return;
        }

        // 4. 更新 Environment（仅内存，不回写文件）
        setPasswordInEnvironment(environment, encrypted);

        log.info("检测到明文密码，已自动加密为 BCrypt 格式。");
    }

    /** 有效 BCrypt 哈希的正则（$2a/$2b/$2y$ + 2位cost + $ + 53位Base64字符） */
    private static final java.util.regex.Pattern BCRYPT_HASH_PATTERN =
            java.util.regex.Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    /**
     * 对明文密码执行 BCrypt 加密。
     * <p>
     * 包级可见（非 private），便于单元测试。
     * </p>
     *
     * @param plainPassword 明文密码
     * @return "{bcrypt}" + BCrypt 哈希值，若输入为空则返回 null
     */
    String encryptIfPlain(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            return null;
        }

        // 如果已经是有效的 {bcrypt} 格式，直接返回；无效哈希则重新加密
        if (plainPassword.startsWith(BCRYPT_PREFIX)) {
            String hash = plainPassword.substring(BCRYPT_PREFIX.length());
            if (BCRYPT_HASH_PATTERN.matcher(hash).matches()) {
                return plainPassword;
            }
            log.warn("检测到无效的 BCrypt 哈希格式，将重新加密: {}", plainPassword);
        }

        String hash = ENCODER.encode(plainPassword);
        return BCRYPT_PREFIX + hash;
    }

    /**
     * 判断密码是否为明文（非 BCrypt 格式）。
     *
     * @param password 密码值
     * @return true 表示明文，false 表示已加密或空值
     */
    boolean isPlainPassword(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        if (!password.startsWith(BCRYPT_PREFIX)) {
            return true;
        }
        // 带有 {bcrypt} 前缀但哈希格式无效，视为明文
        String hash = password.substring(BCRYPT_PREFIX.length());
        return !BCRYPT_HASH_PATTERN.matcher(hash).matches();
    }

    /**
     * 将加密后的密码设置到内存中的 Spring Environment。
     * <p>
     * 使用 MapPropertySource 以最高优先级注入，确保后续
     * {@code @ConfigurationProperties} 绑定时读取到加密后的值。
     * 绝不回写到 YAML 文件。
     * </p>
     *
     * @param environment Spring 环境对象
     * @param encrypted   加密后的密码（含 {bcrypt} 前缀）
     */
    private void setPasswordInEnvironment(ConfigurableEnvironment environment, String encrypted) {
        MutablePropertySources propertySources = environment.getPropertySources();
        Map<String, Object> map = Collections.singletonMap(PASSWORD_KEY, encrypted);

        // 移除旧的同名属性源（如果存在），确保每次启动使用最新加密值
        if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
            propertySources.remove(PROPERTY_SOURCE_NAME);
        }

        // 以最高优先级添加（排在所有其他属性源之前）
        propertySources.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
    }
}
