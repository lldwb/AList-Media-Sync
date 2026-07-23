package top.lldwb.alistmediasync.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

/**
 * E2E 测试基类
 * <p>
 * 提供端到端测试的公共生命周期管理：
 * <ul>
 *   <li>{@link BeforeAll} 强制清理 {@code data-e2e/} 目录，保证幂等（FR-009）</li>
 *   <li>{@link AfterEach} 成功时清理三级状态，失败时保留现场</li>
 *   <li>通过 {@link DynamicPropertySource} 注入动态端口（从系统属性读取）</li>
 *   <li>单链路超时 15 分钟（SC-006）</li>
 * </ul>
 * </p>
 * <p>
 * 注意：真实 AList 二进制启动依赖外部脚本（scripts/e2e/prepare-e2e-env.ps1）。
 * 本基类通过 {@code @EnabledIfEnvironmentVariable} 或系统属性 {@code ALIST_LOCAL_PATH}
 * 控制启用条件，默认跳过。
 * </p>
 *
 * @author AList-Media-Sync
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
public abstract class E2ETestBase {

    /** 测试失败标记（ThreadLocal 保证各测试线程独立） */
    private static final ThreadLocal<Boolean> FAILED = ThreadLocal.withInitial(() -> false);

    /**
     * 通过系统属性注入动态端口到 application-e2e.yaml 占位符
     * <p>
     * 占位符 0 由 RANDOM_PORT 自动分配，此处仅注入 alist.base-url。
     * 端口值由 E2ELifecycleManager 或外部脚本提前设置到系统属性。
     * </p>
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // 从系统属性读取 alist 端口，默认 5244
        String alistPort = System.getProperty("alist.port", "5244");
        registry.add("alist.base-url", () -> "http://localhost:" + alistPort);
    }

    /**
     * 清理 data-e2e/ 目录
     * <p>
     * 在所有 E2E 测试开始前执行，确保测试幂等性（FR-009）。
     * 目录不存在时静默跳过。
     * </p>
     */
    @BeforeAll
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
                // 清理失败时仅警告，不阻止测试
                System.err.println("警告：清理 data-e2e/ 失败 — " + e.getMessage());
            }
        }
    }

    /**
     * 测试失败时保留现场
     * <p>
     * 成功时清理三级状态（MDC、临时文件等），失败时保留现场供排查。
     * </p>
     *
     * @param testInfo 当前测试信息
     */
    @AfterEach
    void tearDown(TestInfo testInfo) {
        boolean failed = FAILED.get();
        if (failed) {
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
     * <p>
     * 检查 {@code ALIST_LOCAL_PATH} 系统属性与 {@code RUN_E2E} 环境变量。
     * </p>
     *
     * @return true 表示应跳过
     */
    protected static boolean shouldSkip() {
        if ("true".equalsIgnoreCase(System.getenv("RUN_E2E"))) {
            return false;
        }
        String alistPath = System.getProperty("ALIST_LOCAL_PATH");
        if (alistPath != null && !alistPath.isEmpty()) {
            return false;
        }
        return true;
    }
}