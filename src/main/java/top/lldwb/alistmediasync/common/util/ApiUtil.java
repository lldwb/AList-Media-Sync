package top.lldwb.alistmediasync.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * AList API 请求工具类
 * <p>
 * 提供静态方法封装 AList REST API 的 HTTP 请求发送、完整日志输出和异常处理。
 * 所有方法均遵循 constitution 原则 VII：DEBUG 级别记录请求入参和响应出参（含完整返回值），
 * ERROR 级别记录失败详情（含完整上下文）。
 * </p>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>提供 5 个公共静态方法，覆盖 AList API 的 6 种调用模式</li>
 *   <li>Token 仅用于设置 Authorization 请求头，不出现在日志中</li>
 *   <li>异常统一包装为 RuntimeException 向上抛出，由业务层决定处理方式</li>
 *   <li>遵循 KISS 原则：纯静态工具类，不创建接口抽象，不引入自定义异常类</li>
 *   <li>每个方法要求传入 {@link RestClient} 实例，由调用方通过依赖注入获取</li>
 * </ul>
 *
 * @author AList-Media-Sync
 */
@Slf4j
public final class ApiUtil {

    private ApiUtil() {
        // 工具类，禁止实例化
    }

    /**
     * GET 请求，返回 JSON Map 响应体
     * <p>
     * 用于 AList API 的 GET 端点（如 GET /api/me 连接测试）。
     * </p>
     *
     * @param restClient 已配置的 RestClient 实例
     * @param baseUrl    AList 服务器基础 URL
     * @param token      AList API 认证令牌
     * @param uri        API 路径（如 /api/me）
     * @return 响应体解析为 Map，键为字符串，值为任意类型
     * @throws RuntimeException 请求失败时抛出（含 ERROR 日志）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> get(RestClient restClient, String baseUrl, String token, String uri) {
        String fullUrl = baseUrl + uri;
        log.debug("AList API 请求：GET {} — 无请求体", fullUrl);
        try {
            Map<String, Object> result = restClient.get()
                .uri(fullUrl)
                .header("Authorization", token)
                .retrieve()
                .body(Map.class);
            log.debug("AList API 响应：GET {} — 状态码=200, result={}", fullUrl, result);
            return result;
        } catch (Exception e) {
            log.error("AList API 调用失败：GET {} — 原因：{}", fullUrl, e.getMessage(), e);
            throw new RuntimeException("AList API GET 请求失败：" + fullUrl, e);
        }
    }

    /**
     * POST JSON 请求，返回 JSON Map 响应体
     * <p>
     * 用于 AList API 的 POST 端点（如 POST /api/fs/list、POST /api/fs/get 获取元数据）。
     * </p>
     *
     * @param restClient 已配置的 RestClient 实例
     * @param baseUrl    AList 服务器基础 URL
     * @param token      AList API 认证令牌
     * @param uri        API 路径（如 /api/fs/list）
     * @param body       请求体（JSON Map）
     * @return 响应体解析为 Map
     * @throws RuntimeException 请求失败时抛出（含 ERROR 日志）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> post(RestClient restClient, String baseUrl, String token, String uri,
                                           Map<String, Object> body) {
        String fullUrl = baseUrl + uri;
        log.debug("AList API 请求：POST {} — body={}", fullUrl, summarizeBody(body));
        try {
            Map<String, Object> result = restClient.post()
                .uri(fullUrl)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
            log.debug("AList API 响应：POST {} — 状态码=200, result={}", fullUrl, result);
            return result;
        } catch (Exception e) {
            log.error("AList API 调用失败：POST {} — body={}, 原因：{}", fullUrl, summarizeBody(body),
                e.getMessage(), e);
            throw new RuntimeException("AList API POST 请求失败：" + fullUrl, e);
        }
    }

    /**
     * POST JSON 请求，返回二进制字节数组
     * <p>
     * 用于 AList API 的文件下载端点（POST /api/fs/get + Accept: octet-stream）。
     * </p>
     *
     * @param restClient 已配置的 RestClient 实例
     * @param baseUrl    AList 服务器基础 URL
     * @param token      AList API 认证令牌
     * @param uri        API 路径（如 /api/fs/get）
     * @param body       请求体（JSON Map，含 path 等信息）
     * @return 响应体字节数组，可能为 null
     * @throws RuntimeException 请求失败时抛出（含 ERROR 日志）
     */
    public static byte[] postForBytes(RestClient restClient, String baseUrl, String token, String uri,
                                      Map<String, Object> body) {
        String fullUrl = baseUrl + uri;
        log.debug("AList API 请求：POST {} (下载) — path={}", fullUrl, body.get("path"));
        try {
            byte[] result = restClient.post()
                .uri(fullUrl)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .body(byte[].class);
            log.debug("AList API 响应：POST {} (下载) — 状态码=200, 文件大小={}bytes, 字节数组前16位(hex)={}",
                fullUrl,
                result != null ? result.length : 0,
                result != null ? toHexPrefix(result, 16) : "null");
            return result;
        } catch (Exception e) {
            log.error("AList API 调用失败：POST {} (下载) — path={}, 原因：{}", fullUrl,
                body.get("path"), e.getMessage(), e);
            throw new RuntimeException("AList API 文件下载请求失败：" + fullUrl, e);
        }
    }

    /**
     * POST JSON 请求，无返回值（Bodiless）
     * <p>
     * 用于 AList API 的无响应体端点（如 POST /api/fs/mkdir、POST /api/fs/remove）。
     * </p>
     *
     * @param restClient 已配置的 RestClient 实例
     * @param baseUrl    AList 服务器基础 URL
     * @param token      AList API 认证令牌
     * @param uri        API 路径（如 /api/fs/mkdir）
     * @param body       请求体（JSON Map）
     * @throws RuntimeException 请求失败时抛出（含 ERROR 日志）
     */
    public static void postVoid(RestClient restClient, String baseUrl, String token, String uri,
                                Map<String, Object> body) {
        String fullUrl = baseUrl + uri;
        log.debug("AList API 请求：POST {} (void) — body={}", fullUrl, summarizeBody(body));
        try {
            restClient.post()
                .uri(fullUrl)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            log.debug("AList API 响应：POST {} (void) — 状态码=200, 无响应体", fullUrl);
        } catch (Exception e) {
            log.error("AList API 调用失败：POST {} (void) — body={}, 原因：{}", fullUrl,
                summarizeBody(body), e.getMessage(), e);
            throw new RuntimeException("AList API POST 请求失败：" + fullUrl, e);
        }
    }

    /**
     * PUT multipart/form-data 请求，无返回值
     * <p>
     * 用于 AList API 的文件上传端点（PUT /api/fs/put）。
     * </p>
     *
     * @param restClient   已配置的 RestClient 实例
     * @param baseUrl      AList 服务器基础 URL
     * @param token        AList API 认证令牌
     * @param uri          API 路径（如 /api/fs/put）
     * @param parts        multipart 表单数据
     * @param extraHeaders 额外的请求头（如 File-Path、As-Task）
     * @throws RuntimeException 请求失败时抛出（含 ERROR 日志）
     */
    public static void putMultipart(RestClient restClient, String baseUrl, String token, String uri,
                                    MultiValueMap<String, Object> parts,
                                    Map<String, String> extraHeaders) {
        String fullUrl = baseUrl + uri;
        log.debug("AList API 请求：PUT {} (上传) — headers={}", fullUrl, extraHeaders);
        try {
            var requestBuilder = restClient.put()
                .uri(fullUrl)
                .header("Authorization", token)
                .contentType(MediaType.MULTIPART_FORM_DATA);
            // 添加额外请求头
            if (extraHeaders != null) {
                extraHeaders.forEach(requestBuilder::header);
            }
            requestBuilder.body(parts)
                .retrieve()
                .toBodilessEntity();
            log.debug("AList API 响应：PUT {} (上传) — 状态码=200, 无响应体", fullUrl);
        } catch (Exception e) {
            log.error("AList API 调用失败：PUT {} (上传) — headers={}, 原因：{}", fullUrl,
                extraHeaders, e.getMessage(), e);
            throw new RuntimeException("AList API 文件上传请求失败：" + fullUrl, e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 请求体摘要：避免日志中输出完整的请求体内容
     * <p>
     * 对于敏感或过大的请求体字段进行摘要处理，仅保留关键业务字段。
     * </p>
     */
    private static String summarizeBody(Map<String, Object> body) {
        if (body == null) {
            return "null";
        }
        // 对于 list/get 请求，仅记录 path/page/perPage/password 等关键字段
        if (body.containsKey("path")) {
            StringBuilder sb = new StringBuilder("{path=").append(body.get("path"));
            if (body.containsKey("page")) {
                sb.append(", page=").append(body.get("page"));
            }
            if (body.containsKey("per_page")) {
                sb.append(", per_page=").append(body.get("per_page"));
            }
            sb.append("}");
            return sb.toString();
        }
        // 对于 delete 请求，记录 names 和 dir
        if (body.containsKey("names")) {
            return "{names=" + body.get("names") + ", dir=" + body.get("dir") + "}";
        }
        return body.toString();
    }

    /**
     * 将字节数组前 N 位转换为十六进制字符串（用于日志，避免输出完整二进制内容）
     *
     * @param bytes 字节数组
     * @param maxLen 最大显示的字节数
     * @return 十六进制字符串
     */
    private static String toHexPrefix(byte[] bytes, int maxLen) {
        if (bytes == null) {
            return "null";
        }
        int len = Math.min(bytes.length, maxLen);
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xFF));
        }
        if (bytes.length > maxLen) {
            sb.append("...");
        }
        return sb.toString();
    }
}
