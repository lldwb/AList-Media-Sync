package top.lldwb.alistmediasync.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
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
 * 自动将配置中的明文密码使用 BCrypt 加密到内存。
 * </p>
 * <p>
 * 核心原则：
 * <ul>
 *   <li>配置文件中仅支持明文密码，所有配置值均视为明文</li>
 *   <li>每次启动使用随机盐值进行 BCrypt 加密</li>
 *   <li>加密值仅保存在内存中的 Spring Environment，绝不回写到 YAML 文件</li>
 *   <li>加密过程完全静默，仅在密码为空时输出 WARN 日志</li>
 * </ul>
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

    /** BCrypt 密码编码器（默认构造，每次随机盐值） */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /** 内存属性源名称 */
    private static final String PROPERTY_SOURCE_NAME = "passwordEncryptionMemory";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 1. 读取 app.auth.password 属性
        String password = environment.getProperty(PASSWORD_KEY);

        // 2. 空值检测
        if (password == null || password.isBlank()) {
            log.warn("认证密码未设置，管理后台将无法登录。请在 application.yaml 中配置 app.auth.password。");
            return;
        }

        // 3. BCrypt 加密（所有值均视为明文，包括含 {bcrypt} 前缀的旧格式）
        String encrypted = encryptIfPlain(password);
        if (encrypted == null) {
            return;
        }

        // 4. 更新 Environment（仅内存，不回写文件）
        setPasswordInEnvironment(environment, encrypted);
    }

    /**
     * 对明文密码执行 BCrypt 加密。
     * <p>
     * 所有配置值均视为明文密码，不再识别 {bcrypt} 前缀。
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

        String hash = ENCODER.encode(plainPassword);
        return BCRYPT_PREFIX + hash;
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
