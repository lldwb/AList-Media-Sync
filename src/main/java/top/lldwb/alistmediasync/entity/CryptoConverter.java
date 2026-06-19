package top.lldwb.alistmediasync.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
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
 *
 * @author AList-Media-Sync
 */
@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 位 IV
    private static final int GCM_TAG_LENGTH = 128; // 128 位认证标签

    private final SecretKey secretKey;

    public CryptoConverter() {
        this.secretKey = loadOrGenerateKey();
    }

    private SecretKey loadOrGenerateKey() {
        try {
            String keyStr = System.getProperty("alist.crypto.key");
            if (keyStr != null && !keyStr.isEmpty()) {
                byte[] keyBytes = Base64.getDecoder().decode(keyStr);
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
            }
            // 默认生成随机密钥（重启后之前加密的数据将无法解密！生产环境必须设置 alist.crypto.key）
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
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
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
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密字段失败", e);
        }
    }
}
