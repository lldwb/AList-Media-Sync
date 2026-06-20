package top.lldwb.alistmediasync.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Base64;

/**
 * 加密密钥环境后处理器
 * <p>
 * 实现 {@link EnvironmentPostProcessor} 接口，在 Spring Boot 环境准备阶段
 * 将环境变量 {@code ALIST_CRYPTO_KEY} 桥接到 JVM 系统属性 {@code alist.crypto.key}，
 * 供 {@link top.lldwb.alistmediasync.common.entity.CryptoConverter} 使用。
 * </p>
 * <p>
 * 核心原则：
 * <ul>
 *   <li>密钥从环境变量读取，不写入配置文件</li>
 *   <li>空值时输出 WARN 日志，回退到 CryptoConverter 的随机密钥逻辑</li>
 *   <li>密钥格式错误时 fail-fast，阻止应用启动</li>
 *   <li>合法密钥设置到 System.setProperty，CryptoConverter 透明读取</li>
 * </ul>
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
public class CryptoKeyEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 环境变量键名 */
    private static final String ENV_KEY = "ALIST_CRYPTO_KEY";

    /** JVM 系统属性键名（与 CryptoConverter 读取的键一致） */
    private static final String SYSTEM_PROPERTY_KEY = "alist.crypto.key";

    /** AES-256 密钥要求的字节长度 */
    private static final int AES_KEY_BYTES = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 1. 读取环境变量
        String key = environment.getProperty(ENV_KEY);

        // 2. 空值检测：未设置或为空时回退到 CryptoConverter 随机密钥
        if (key == null || key.isBlank()) {
            log.warn("========================================");
            log.warn("ALIST_CRYPTO_KEY 未设置！");
            log.warn("CryptoConverter 将生成随机 AES-256 密钥，");
            log.warn("应用重启后所有已加密数据将无法解密。");
            log.warn("请设置环境变量 ALIST_CRYPTO_KEY，");
            log.warn("生成方法：openssl rand -base64 32");
            log.warn("========================================");
            return;
        }

        // 3. 去除前后空白
        String trimmedKey = key.trim();

        // 4. 校验 Base64 格式
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(trimmedKey);
        } catch (IllegalArgumentException e) {
            log.error("========================================");
            log.error("ALIST_CRYPTO_KEY 不是有效的 Base64 字符串！");
            log.error("请使用 openssl rand -base64 32 生成合法密钥");
            log.error("========================================");
            throw new IllegalArgumentException(
                "ALIST_CRYPTO_KEY 必须是 Base64 编码的 32 字节密钥，当前值无法解码", e);
        }

        // 5. 校验密钥长度（AES-256 要求 32 字节）
        if (keyBytes.length != AES_KEY_BYTES) {
            log.error("========================================");
            log.error("ALIST_CRYPTO_KEY 长度无效（{} 字节），AES-256 需要 32 字节密钥", keyBytes.length);
            log.error("请使用 openssl rand -base64 32 生成合法密钥");
            log.error("========================================");
            throw new IllegalArgumentException(
                "ALIST_CRYPTO_KEY 解码后为 " + keyBytes.length + " 字节，AES-256 需要 32 字节密钥");
        }

        // 6. 设置 JVM 系统属性（CryptoConverter 通过 System.getProperty 读取）
        System.setProperty(SYSTEM_PROPERTY_KEY, trimmedKey);
        log.info("已从 ALIST_CRYPTO_KEY 环境变量加载 AES-256 密钥（{} 字节）", keyBytes.length);
    }
}
