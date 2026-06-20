package top.lldwb.alistmediasync.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密转换器
 * <p>
 * 用于 JPA 实体字段的透明加密/解密。
 * 加密后的数据以 Base64 编码存储。
 * 密钥存储在 JVM 系统属性 alist.crypto.key 中（默认使用随机密钥）。
 * </p>
 * <p>
 * <b>生产环境必须通过 JVM 参数设置密钥：</b>
 * {@code -Dalist.crypto.key=<Base64 编码的 256 位密钥>}
 * 可使用以下命令生成密钥：
 * {@code openssl rand -base64 32}
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 位 IV
    private static final int GCM_TAG_LENGTH = 128; // 128 位认证标签

    /** 静态密钥，确保同一 JVM 进程中所有 Converter 实例使用同一密钥 */
    private static final SecretKey SECRET_KEY = loadOrGenerateKey();

    private static SecretKey loadOrGenerateKey() {
        String keyStr = System.getProperty("alist.crypto.key");
        if (keyStr != null && !keyStr.isEmpty()) {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(keyStr);
                if (keyBytes.length != 32) {
                    log.error("alist.crypto.key 长度无效（{} 字节），AES-256 需要 32 字节密钥", keyBytes.length);
                    throw new RuntimeException("alist.crypto.key 必须是 Base64 编码的 32 字节密钥");
                }
                log.info("已从 alist.crypto.key 系统属性加载 AES-256 密钥");
                return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
            } catch (IllegalArgumentException e) {
                log.error("alist.crypto.key 不是有效的 Base64 字符串", e);
                throw new RuntimeException("alist.crypto.key 必须是 Base64 编码的 32 字节密钥", e);
            }
        }

        // 默认生成随机密钥
        // 安全提示：重启后之前加密的数据将无法解密，生产环境必须设置 alist.crypto.key
        log.info("未设置 alist.crypto.key 系统属性，已生成随机 AES-256 密钥。"
            + "生产环境请通过 JVM 参数设置：-Dalist.crypto.key=<Base64 编码的 32 字节密钥>"
            + "（生成命令：openssl rand -base64 32）。"
            + "应用重启后所有加密数据将无法解密。");
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("无法初始化加密密钥", e);
        }
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, spec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            // 将 IV 和密文拼接后 Base64 编码
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("加密字段失败", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            // 最小长度校验：IV (12) + GCM 认证标签 (16) = 28 字节
            if (combined.length < GCM_IV_LENGTH + 16) {
                throw new IllegalArgumentException(
                    "密文长度异常（" + combined.length + " 字节），"
                    + "可能为非加密数据或密文已损坏");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, spec);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException e) {
            log.error("解密失败：密钥不匹配或密文已损坏 — 请检查 alist.crypto.key 是否已设置且与加密时一致", e);
            throw new RuntimeException("解密字段失败：密钥不匹配或密文被篡改", e);
        } catch (Exception e) {
            throw new RuntimeException("解密字段失败", e);
        }
    }
}
