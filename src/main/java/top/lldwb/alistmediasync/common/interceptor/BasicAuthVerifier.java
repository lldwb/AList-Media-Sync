package top.lldwb.alistmediasync.common.interceptor;

import org.springframework.security.crypto.bcrypt.BCrypt;
import top.lldwb.alistmediasync.common.config.AppProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Basic 认证校验器
 * <p>
 * 承载 HTTP（{@link AuthInterceptor}）与 WebSocket 握手（{@link WebSocketAuthInterceptor}）
 * 共用的 Basic 凭据校验流程：Base64 解码 → {@code username:password} 拆分 → 用户名比对 →
 * {@code {bcrypt}} 前缀防御检查 → BCrypt 哈希比对。校验顺序与边界处理与被替换的两份内联实现一致：
 * 请求头为 null / 不以 {@code Basic } 开头、Base64 解码失败、凭据缺少冒号、
 * 用户名不匹配、存储密码非 {@code {bcrypt}} 形态、BCrypt 校验失败。
 * </p>
 * <p>
 * 只承载「校验」：拒绝响应的写法（HTTP 401 JSON 响应、WebSocket 拒绝升级）与日志文案
 * 仍保留在各自的拦截器中。凭据无法 Base64 解码（或 BCrypt 哈希形态非法，与既有实现一致）
 * 时按原样抛出 {@link IllegalArgumentException}，由调用方按既有文案记录并拒绝。
 * </p>
 * <p>
 * 由拦截器在构造时实例化（不注册为 Spring Bean），以保持两个拦截器的构造签名不变。
 * </p>
 *
 * @author AList-Media-Sync
 */
public class BasicAuthVerifier {

    /** 校验结果状态 */
    public enum Status {

        /** 校验通过 */
        SUCCESS,

        /** 请求头缺失或不是 Basic 认证（为 null / 不以 {@code Basic } 开头） */
        MISSING_CREDENTIALS,

        /** 凭据格式无效（解码后缺少冒号分隔） */
        MALFORMED_CREDENTIALS,

        /** 用户名不匹配 */
        USERNAME_MISMATCH,

        /** 存储的密码不是 {bcrypt} 形态（PasswordEncryptionPostProcessor 未生效） */
        PASSWORD_NOT_BCRYPT,

        /** BCrypt 密码不匹配 */
        PASSWORD_MISMATCH
    }

    /**
     * 校验结果
     *
     * @param status   校验结果状态
     * @param username 解析出的用户名（凭据尚未解析出用户名时为 null），供调用方日志使用
     */
    public record Result(Status status, String username) {
    }

    private final AppProperties appProperties;

    public BasicAuthVerifier(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * 校验 Authorization 请求头中的 Basic 凭据
     *
     * @param authHeader Authorization 请求头原始值（可为 null）
     * @return 校验结果，{@link Status#SUCCESS} 表示校验通过
     * @throws IllegalArgumentException 凭据 Base64 解码失败，或存储的 BCrypt 哈希形态非法
     */
    public Result verify(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return new Result(Status.MISSING_CREDENTIALS, null);
        }

        String base64Credentials = authHeader.substring(6);
        byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
        String credentials = new String(decodedBytes, StandardCharsets.UTF_8);
        String[] parts = credentials.split(":", 2);
        if (parts.length != 2) {
            return new Result(Status.MALFORMED_CREDENTIALS, null);
        }

        String username = parts[0];
        String password = parts[1];

        // 验证用户名
        if (!appProperties.getAuth().getUsername().equals(username)) {
            return new Result(Status.USERNAME_MISMATCH, username);
        }

        // 验证 BCrypt 密码（密码始终以 {bcrypt} 前缀存储）
        String storedPassword = appProperties.getAuth().getPassword();
        if (!storedPassword.startsWith("{bcrypt}")) {
            // 防御性检查：密码到达时不是 {bcrypt} 格式（PasswordEncryptionPostProcessor 未生效）
            return new Result(Status.PASSWORD_NOT_BCRYPT, username);
        }

        // BCrypt 哈希比较
        String bcryptHash = storedPassword.substring(8);
        if (!BCrypt.checkpw(password, bcryptHash)) {
            return new Result(Status.PASSWORD_MISMATCH, username);
        }

        return new Result(Status.SUCCESS, username);
    }
}
