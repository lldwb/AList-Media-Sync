package top.lldwb.alistmediasync.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Map;

/**
 * E2E 测试基类
 * <p>
 * 提供端到端测试的公共生命周期管理：
 * <ul>
 *   <li>{@link BeforeAll} 强制清理 {@code data-e2e/} 目录并预置测试数据（AList 登录、源文件上传、StorageEngine、WebhookRule），保证幂等（FR-009）</li>
 *   <li>{@link AfterEach} 成功时清理、失败时保留现场（FR-009）</li>
 *   <li>通过 {@link DynamicPropertySource} 注入动态端口（从系统属性读取）</li>
 *   <li>单链路超时 15 分钟（SC-006）</li>
 *   <li>提供 Basic 认证 helper 与轮询等待 helper，供子类加强断言</li>
 * </ul>
 * </p>
 * <p>
 * 采用 {@link TestInstance.Lifecycle#PER_CLASS} 使 {@link BeforeAll} 可访问
 * {@link TestRestTemplate} 与 {@link LocalServerPort}，便于在套件起始预置数据。
 * </p>
 * <p>
 * 注意：真实 AList 二进制启动依赖外部脚本（scripts/e2e/start-alist.ps1）。
 * AList 不可达时跳过数据预置，测试断言将自然失败以暴露环境问题。
 * </p>
 *
 * @author AList-Media-Sync
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Timeout(value = 15, unit = java.util.concurrent.TimeUnit.MINUTES)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class E2ETestBase {

    /** 测试失败标记（ThreadLocal 保证各测试线程独立） */
    private static final ThreadLocal<Boolean> FAILED = ThreadLocal.withInitial(() -> false);

    @Autowired
    protected TestRestTemplate testRestTemplate;

    @LocalServerPort
    protected int serverPort;

    /** AList 端口（由 -Dalist.port 注入，E2ELifecycleManager 或外部脚本设置） */
    protected int alistPort;
    /** AList 认证 token（预置时登录获取） */
    protected String alistToken;
    /** 预置的源存储引擎 ID */
    protected Long sourceEngineId;
    /** 预置的目标存储引擎 ID */
    protected Long targetEngineId;
    /** 预置的 Webhook 规则 ID */
    protected Long webhookRuleId;

    /**
     * 通过系统属性注入动态端口到 application-e2e.yaml 占位符
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String alistPort = System.getProperty("alist.port", "5244");
        registry.add("alist.base-url", () -> "http://localhost:" + alistPort);
    }

    /**
     * 套件起始：清理上次现场 + 预置 AList 与系统数据
     * <p>
     * AList 不可达时跳过预置（测试断言自然失败定位环境问题），不抛异常中止套件。
     * </p>
     */
    @BeforeAll
    void setupE2EData() {
        alistPort = Integer.parseInt(System.getProperty("alist.port", "5244"));
        cleanDataE2e();

        if (!isAListReachable()) {
            System.err.println("警告：AList 不可达（端口 " + alistPort + "），跳过数据预置。测试断言将失败以暴露环境问题。");
            return;
        }
        prepareAListData();
        prepareSystemData();
    }

    /**
     * AList 探活：GET /ping 期望返回 pong
     */
    private boolean isAListReachable() {
        try {
            RestClient client = RestClient.builder().baseUrl("http://localhost:" + alistPort).build();
            String resp = client.get().uri("/ping").retrieve().body(String.class);
            return resp != null && resp.contains("pong");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 预置 AList 数据：登录获取 token + 上传 sample.mp4 到源路径
     * <p>
     * 依赖 start-alist.ps1 已通过 {@code alist admin set admin} 设置管理员密码为 admin。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private void prepareAListData() {
        RestClient alistClient = RestClient.builder().baseUrl("http://localhost:" + alistPort).build();
        try {
            // 登录获取 token
            Map<String, Object> loginResp = alistClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "admin", "password", "admin"))
                .retrieve()
                .body(Map.class);
            if (loginResp != null && loginResp.get("data") instanceof Map<?, ?> data) {
                Object token = data.get("token");
                alistToken = token != null ? token.toString() : null;
            }

            // 上传 sample.mp4 到 AList 源路径（PUT /api/fs/put + File-Path header）
            Path sample = Path.of("src/test/resources/fixtures/media/sample.mp4");
            byte[] bytes = Files.readAllBytes(sample);
            alistClient.put()
                .uri("/api/fs/put")
                .header("File-Path", "/e2e-test/sample.mp4")
                .header("Authorization", alistToken)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            System.err.println("警告：预置 AList 数据失败 - " + e.getMessage());
        }
    }

    /**
     * 预置系统数据：创建源/目标 StorageEngine + Webhook 规则（FILE_CLOSED -> BOTH）
     * <p>
     * 源与目标引擎指向同一 AList 实例，同步从 /e2e-test 读取，写到 /e2e-test-sync。
     * </p>
     */
    private void prepareSystemData() {
        String alistBaseUrl = "http://localhost:" + alistPort;
        sourceEngineId = createStorageEngine("E2E源引擎", alistBaseUrl, alistToken);
        targetEngineId = createStorageEngine("E2E目标引擎", alistBaseUrl, alistToken);

        if (sourceEngineId == null || targetEngineId == null) {
            System.err.println("警告：StorageEngine 创建失败，后续断言将失败定位问题。");
            return;
        }

        Map<String, Object> rule = Map.of(
            "name", "E2E-FileClosed-Rule",
            "triggerEventType", "FILE_CLOSED",
            "action", "BOTH",
            "recordingEngineId", sourceEngineId,
            "recordingPath", "/e2e-test",
            "targetEngineId", targetEngineId,
            "targetFilePath", "/e2e-test-sync"
        );
        HttpEntity<Map<String, Object>> ruleReq = new HttpEntity<>(rule, basicAuth());
        ResponseEntity<Map> ruleResp = testRestTemplate.postForEntity("/api/webhook-rules", ruleReq, Map.class);
        webhookRuleId = extractId(ruleResp.getBody());
    }

    /**
     * 创建 AList 类型 StorageEngine，返回新建 id
     */
    @SuppressWarnings("unchecked")
    private Long createStorageEngine(String name, String baseUrl, String token) {
        Map<String, Object> engine = Map.of(
            "name", name,
            "engineType", "ALIST",
            "baseUrl", baseUrl,
            "token", token != null ? token : ""
        );
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(engine, basicAuth());
        ResponseEntity<Map> resp = testRestTemplate.postForEntity("/api/storage-engines", req, Map.class);
        return extractId(resp.getBody());
    }

    /**
     * 从 ApiResult 响应提取 data.id
     */
    @SuppressWarnings("unchecked")
    protected Long extractId(Map<String, Object> body) {
        if (body == null) return null;
        Object data = body.get("data");
        if (data instanceof Map<?, ?> m && m.get("id") instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    /**
     * 检查 AList list 响应中是否含文件（data.content 非空）
     */
    @SuppressWarnings("unchecked")
    protected static boolean hasFileInList(Map<String, Object> response) {
        if (response == null) return false;
        Object code = response.get("code");
        if (!(code instanceof Number n) || n.intValue() != 200) return false;
        Object data = response.get("data");
        if (data instanceof Map<?, ?> d) {
            Object content = d.get("content");
            return content instanceof java.util.List<?> list && !list.isEmpty();
        }
        return false;
    }

    /**
     * Basic 认证请求头（admin/admin123，对齐 application-e2e.yaml 明文密码）
     */
    protected HttpHeaders basicAuth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("admin", "admin123");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * 轮询等待条件满足，超时返回 false
     * <p>
     * 替代 {@code Thread.sleep} 硬编码等待，提升异步链路断言稳定性。
     * </p>
     *
     * @param condition 待满足条件（可抛异常）
     * @param timeout   总超时
     * @param interval  轮询间隔
     * @return true 表示条件在超时前满足
     */
    protected boolean await(ThrowingBooleanSupplier condition, Duration timeout, Duration interval) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.getAsBoolean()) return true;
            } catch (Exception ignored) {
                // 条件未就绪，继续轮询
            }
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** 可抛异常的 BooleanSupplier */
    @FunctionalInterface
    protected interface ThrowingBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    /**
     * 清理 data-e2e/ 目录（幂等保证 FR-009）
     */
    static void cleanDataE2e() {
        Path dataE2e = Path.of("data-e2e");
        if (Files.exists(dataE2e)) {
            try {
                Files.walkFileTree(dataE2e, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        if (!dir.equals(dataE2e)) {
                            Files.delete(dir);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                System.err.println("警告：清理 data-e2e/ 失败 - " + e.getMessage());
            }
        }
    }

    /**
     * 测试失败时保留现场（FR-009）
     */
    @AfterEach
    void tearDown(TestInfo testInfo) {
        if (FAILED.get()) {
            System.err.println("测试失败，保留现场：" + testInfo.getDisplayName());
        }
        FAILED.remove();
    }

    /**
     * 标记当前测试失败，触发 {@link AfterEach} 保留现场
     */
    protected void markFailed() {
        FAILED.set(true);
    }

    /**
     * 判断是否应跳过 E2E 测试（AList 二进制不可用时）
     */
    protected static boolean shouldSkip() {
        if ("true".equalsIgnoreCase(System.getenv("RUN_E2E"))) {
            return false;
        }
        String alistPath = System.getProperty("ALIST_LOCAL_PATH");
        return alistPath == null || alistPath.isEmpty();
    }
}
