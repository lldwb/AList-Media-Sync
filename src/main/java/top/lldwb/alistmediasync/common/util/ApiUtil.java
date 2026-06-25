package top.lldwb.alistmediasync.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * AList REST API 请求工具类
 * <p>
 * 严格按 {@code md/alist/} 下的官方对接文档封装 HTTP 调用。
 * 所有方法都会：
 * <ol>
 *   <li>设置 {@code Authorization} 头（无 token 时跳过，用于 /ping 等公共接口）</li>
 *   <li>校验响应中的 {@code code} 字段——AList 始终返回 HTTP 200，业务错误通过
 *       {@code code != 200} 表达，并在 {@code message} 中携带原因，所以必须解析后再判断</li>
 *   <li>DEBUG 记录入参/出参，ERROR 记录失败上下文，token 永不入日志</li>
 * </ol>
 * </p>
 *
 * <h3>对应 AList 端点</h3>
 * <ul>
 *   <li>{@link #get} — GET 公共/带鉴权端点，如 {@code /ping}、{@code /api/me}</li>
 *   <li>{@link #post} — POST JSON 端点，如 {@code /api/fs/list}、{@code /api/fs/get}</li>
 *   <li>{@link #postVoid} — POST JSON 端点但忽略 data，如 {@code /api/fs/mkdir}、{@code /api/fs/copy}、{@code /api/fs/remove}</li>
 *   <li>{@link #putStream} — PUT 流式上传，对接 {@code PUT /api/fs/put}（请求体为 octet-stream）</li>
 * </ul>
 *
 * @author AList-Media-Sync
 */
@Slf4j
public final class ApiUtil {

    /** AList 业务成功状态码 */
    public static final int CODE_SUCCESS = 200;

    private ApiUtil() {
        // 工具类，禁止实例化
    }

    /**
     * GET 请求，校验 AList 业务码后返回 data 全量 Map（含 code/message/data 三个字段）
     *
     * @param restClient 已配置的 RestClient 实例
     * @param baseUrl    AList 服务器基础 URL（无尾部 /）
     * @param token      AList token；可为 null/空（用于 /ping 等无鉴权端点）
     * @param uri        API 路径
     * @return 解析后的响应 Map
     * @throws RuntimeException 网络异常、HTTP 非 2xx、或业务 code != 200 时抛出
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> get(RestClient restClient, String baseUrl, String token, String uri) {
        String fullUrl = baseUrl + uri;
        log.debug("AList API 请求：GET {}", fullUrl);
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(fullUrl);
            if (token != null && !token.isEmpty()) {
                spec = spec.header("Authorization", token);
            }
            Map<String, Object> result = spec.retrieve().body(Map.class);
            log.debug("AList API 响应：GET {} — result={}", fullUrl, result);
            verifyBusinessCode(fullUrl, result);
            return result;
        } catch (RuntimeException e) {
            log.error("AList API 调用失败：GET {} — {}", fullUrl, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AList API 调用失败：GET {} — {}", fullUrl, e.getMessage(), e);
            throw new RuntimeException("AList API GET 请求失败：" + fullUrl, e);
        }
    }

    /**
     * POST JSON 请求，校验 AList 业务码后返回响应 Map
     *
     * @param restClient 已配置的 RestClient 实例
     * @param baseUrl    AList 服务器基础 URL
     * @param token      AList token
     * @param uri        API 路径（如 {@code /api/fs/list}）
     * @param body       请求体（JSON Map）
     * @return 解析后的响应 Map
     * @throws RuntimeException 网络异常、HTTP 非 2xx、或业务 code != 200 时抛出
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
            log.debug("AList API 响应：POST {} — result={}", fullUrl, result);
            verifyBusinessCode(fullUrl, result);
            return result;
        } catch (RuntimeException e) {
            log.error("AList API 调用失败：POST {} — body={}, {}", fullUrl, summarizeBody(body), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AList API 调用失败：POST {} — body={}, {}", fullUrl, summarizeBody(body), e.getMessage(), e);
            throw new RuntimeException("AList API POST 请求失败：" + fullUrl, e);
        }
    }

    /**
     * POST JSON 请求，校验 AList 业务码后丢弃 data
     * <p>
     * 用于 {@code data: null} 的端点（如 /api/fs/mkdir、/api/fs/remove、/api/fs/copy、/api/fs/move）。
     * </p>
     *
     * @param restClient 已配置的 RestClient 实例
     * @param baseUrl    AList 服务器基础 URL
     * @param token      AList token
     * @param uri        API 路径
     * @param body       请求体
     * @throws RuntimeException 业务 code != 200 时抛出
     */
    public static void postVoid(RestClient restClient, String baseUrl, String token, String uri,
                                Map<String, Object> body) {
        post(restClient, baseUrl, token, uri, body);
    }

    /**
     * 流式 PUT 上传文件，对接 {@code PUT /api/fs/put}（octet-stream）
     * <p>
     * 严格按 {@code md/alist/fs/流式上传文件.md} 实现：请求体直接是文件字节流，
     * 通过 {@code File-Path}（URL-encoded）、{@code As-Task}、{@code Content-Length}
     * header 携带元数据。相比 multipart 形式，无需在内存中组装表单边界，对大文件更友好。
     * </p>
     *
     * @param restClient   已配置的 RestClient 实例
     * @param baseUrl      AList 服务器基础 URL
     * @param token        AList token
     * @param remotePath   目标文件绝对路径（原始字符串，方法内部做 URL 编码）
     * @param inputStream  文件内容输入流，调用方负责事先备好（关闭由 RestClient 接管）
     * @param fileSize     文件大小（字节），用于 Content-Length；&lt;0 表示未知
     * @param asTask       true 时 AList 异步任务化处理，false 时同步等待
     * @throws RuntimeException 业务 code != 200 或网络异常时抛出
     */
    public static void putStream(RestClient restClient, String baseUrl, String token,
                                  String remotePath, InputStream inputStream, long fileSize, boolean asTask) {
        String fullUrl = baseUrl + "/api/fs/put";
        String encodedPath = encodePath(remotePath);
        log.debug("AList API 请求：PUT {} (流式上传) — remotePath={}, size={}bytes, asTask={}",
            fullUrl, remotePath, fileSize, asTask);
        try {
            // InputStreamResource 携带 contentLength，RestClient 会自动写入 Content-Length 头
            InputStreamResource resource = new InputStreamResource(inputStream) {
                @Override
                public long contentLength() {
                    return fileSize >= 0 ? fileSize : -1;
                }
            };
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restClient.put()
                .uri(fullUrl)
                .header("Authorization", token)
                .header("File-Path", encodedPath)
                .header("As-Task", String.valueOf(asTask))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource)
                .retrieve()
                .body(Map.class);
            log.debug("AList API 响应：PUT {} — result={}", fullUrl, result);
            verifyBusinessCode(fullUrl, result);
        } catch (RuntimeException e) {
            log.error("AList API 调用失败：PUT {} — remotePath={}, {}", fullUrl, remotePath, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AList API 调用失败：PUT {} — remotePath={}, {}", fullUrl, remotePath, e.getMessage(), e);
            throw new RuntimeException("AList API 文件上传请求失败：" + fullUrl, e);
        }
    }

    /**
     * PUT multipart/form-data 上传文件，对接 {@code PUT /api/fs/form}
     * <p>
     * 备用通道：当目标 AList 实例的流式 PUT 不可用时回退使用。当前主路径优先 {@link #putStream}。
     * </p>
     *
     * @param restClient   已配置的 RestClient 实例
     * @param baseUrl      AList 服务器基础 URL
     * @param token        AList token
     * @param parts        multipart 表单数据（必须包含名为 {@code file} 的二进制部分）
     * @param extraHeaders 额外请求头（{@code File-Path}、{@code As-Task} 等）
     * @throws RuntimeException 业务 code != 200 或网络异常时抛出
     */
    public static void putForm(RestClient restClient, String baseUrl, String token,
                                MultiValueMap<String, Object> parts, Map<String, String> extraHeaders) {
        String fullUrl = baseUrl + "/api/fs/form";
        log.debug("AList API 请求：PUT {} (multipart 上传) — headers={}", fullUrl, extraHeaders);
        try {
            var builder = restClient.put()
                .uri(fullUrl)
                .header("Authorization", token)
                .contentType(MediaType.MULTIPART_FORM_DATA);
            if (extraHeaders != null) {
                extraHeaders.forEach(builder::header);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = builder.body(parts).retrieve().body(Map.class);
            log.debug("AList API 响应：PUT {} — result={}", fullUrl, result);
            verifyBusinessCode(fullUrl, result);
        } catch (RuntimeException e) {
            log.error("AList API 调用失败：PUT {} — {}", fullUrl, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AList API 调用失败：PUT {} — {}", fullUrl, e.getMessage(), e);
            throw new RuntimeException("AList API 文件上传请求失败：" + fullUrl, e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验 AList 响应的业务 code 字段
     * <p>
     * AList 永远返回 HTTP 200，业务错误通过 {@code code} 表达：
     * <ul>
     *   <li>200 — 成功</li>
     *   <li>401 — token 失效</li>
     *   <li>403 — 无权限</li>
     *   <li>404 — 路径不存在</li>
     *   <li>500 — 服务端内部错误</li>
     * </ul>
     * 任何非 200 业务码都被视为失败，抛出包含 {@code code} 与 {@code message} 的异常。
     * </p>
     */
    private static void verifyBusinessCode(String fullUrl, Map<String, Object> result) {
        if (result == null) {
            throw new RuntimeException("AList API 响应为空：" + fullUrl);
        }
        Object codeObj = result.get("code");
        if (!(codeObj instanceof Number num)) {
            // 部分公共端点（如 /ping）返回纯字符串 "pong"，没有 code 字段，调用方需自行处理
            return;
        }
        int code = num.intValue();
        if (code != CODE_SUCCESS) {
            String message = String.valueOf(result.get("message"));
            throw new RuntimeException("AList API 业务失败：" + fullUrl + " — code=" + code + ", message=" + message);
        }
    }

    /**
     * 将 AList 路径中每段进行 URL 编码，用于 File-Path 请求头
     * <p>
     * 中文、全角符号、空格等必须编码，否则 AList 服务端会解析失败或路径错位。
     * 编码遵循 RFC 3986，{@code java.net.URLEncoder} 默认会把空格编码为 {@code +}，
     * 因此手动替换为 {@code %20}。
     * </p>
     */
    private static String encodePath(String path) {
        if (path == null || path.isEmpty()) return path;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String segment : path.split("/", -1)) {
            if (!first) sb.append('/');
            first = false;
            if (!segment.isEmpty()) {
                sb.append(java.net.URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }
        return sb.toString();
    }

    /**
     * 请求体摘要（避免日志中输出过大或敏感的请求体）
     */
    private static String summarizeBody(Map<String, Object> body) {
        if (body == null) return "null";
        if (body.containsKey("path")) {
            StringBuilder sb = new StringBuilder("{path=").append(body.get("path"));
            if (body.containsKey("page")) sb.append(", page=").append(body.get("page"));
            if (body.containsKey("per_page")) sb.append(", per_page=").append(body.get("per_page"));
            if (body.containsKey("refresh")) sb.append(", refresh=").append(body.get("refresh"));
            return sb.append("}").toString();
        }
        if (body.containsKey("names")) {
            return "{names=" + body.get("names") + ", dir=" + body.get("dir")
                + (body.containsKey("src_dir") ? ", src_dir=" + body.get("src_dir") : "")
                + (body.containsKey("dst_dir") ? ", dst_dir=" + body.get("dst_dir") : "")
                + "}";
        }
        return body.toString();
    }
}
