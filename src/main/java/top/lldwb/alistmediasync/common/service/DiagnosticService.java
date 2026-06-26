package top.lldwb.alistmediasync.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.dto.DiagnosticResultVO;
import top.lldwb.alistmediasync.common.util.SensitiveDataMasker;
import top.lldwb.alistmediasync.common.util.TraceContext;
import top.lldwb.alistmediasync.sync.entity.TaskExecution;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 诊断包生成服务
 * <p>
 * 一次性收集运行状态、日志摘录、配置摘要和环境信息，输出到 {@code diagnostics/latest}。
 * 与 contracts/diagnostics-*-contract.md 对齐。
 * </p>
 * <p>
 * 核心约束（FR-012）：诊断生成只读，不触发同步、转码、Webhook 等业务副作用，
 * 不调用 SyncService / TranscodeService / WebhookService 的写入路径，
 * 不开启 JPA 写事务（{@code readOnly = true}）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
public class DiagnosticService {

    /** 诊断模块名称（用于 MDC 结构化字段） */
    public static final String MODULE = "diagnostics";

    /** summary.md 时间格式 */
    private static final DateTimeFormatter SUMMARY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 单个日志文件最大复制行数（防止超大文件拖慢诊断） */
    private static final int DEFAULT_MAX_LINES = 2000;

    private final AppProperties appProperties;
    private final ConfigurableEnvironment environment;
    private final Optional<TaskExecutionRepository> taskExecutionRepository;
    private final String appVersion;
    private final String logPath;

    public DiagnosticService(AppProperties appProperties,
                             ConfigurableEnvironment environment,
                             Optional<TaskExecutionRepository> taskExecutionRepository,
                             @Value("${app.version:unknown}") String appVersion,
                             @Value("${logging.file.path:./logs}") String logPath) {
        this.appProperties = appProperties;
        this.environment = environment;
        this.taskExecutionRepository = taskExecutionRepository;
        this.appVersion = appVersion;
        this.logPath = logPath;
    }

    /**
     * 生成诊断包（默认输出至 diagnostics/latest）
     *
     * @return 诊断生成结果
     */
    public DiagnosticResultVO generate() {
        return generate(Paths.get("diagnostics"), DEFAULT_MAX_LINES);
    }

    /**
     * 生成诊断包
     * <p>
     * 流程：
     * <ol>
     *   <li>创建临时工作目录</li>
     *   <li>生成 logs / config / environment / last-run / summary</li>
     *   <li>原子刷新 {@code latest} 软链接（直接替换目录）</li>
     * </ol>
     * </p>
     *
     * @param outputRoot 诊断目录根（默认 diagnostics）
     * @param maxLines   每个日志摘录最多保留行数
     * @return 诊断生成结果
     */
    @Transactional(readOnly = true)
    public DiagnosticResultVO generate(Path outputRoot, int maxLines) {
        long start = System.currentTimeMillis();
        String traceId = TraceContext.getTraceId();
        if (traceId == null) {
            traceId = TraceContext.generate();
            TraceContext.setTraceId(traceId);
        }
        TraceContext.setModuleOperation(MODULE, "生成诊断包");
        log.info("开始生成诊断包：outputRoot={}, maxLines={}", outputRoot, maxLines);

        List<String> missing = new ArrayList<>();
        DiagnosticResultVO.Status status = DiagnosticResultVO.Status.COMPLETED;
        Path latestDir = outputRoot.resolve("latest");
        Path tempDir = null;
        Optional<TaskExecution> latestFailure = Optional.empty();

        try {
            Files.createDirectories(outputRoot);
            tempDir = Files.createTempDirectory(outputRoot, "tmp-");

            // 1) 复制日志摘录
            try {
                copyLogEvidence(tempDir, maxLines, missing);
            } catch (Exception e) {
                missing.add("日志摘录失败：" + e.getMessage());
                log.warn("复制日志摘录失败", e);
            }

            // 2) 写入脱敏配置摘要
            try {
                writeRedactedConfig(tempDir, missing);
            } catch (Exception e) {
                missing.add("配置摘要失败：" + e.getMessage());
                log.warn("写入配置摘要失败", e);
            }

            // 3) 写入环境摘要
            try {
                writeEnvironment(tempDir);
            } catch (Exception e) {
                missing.add("环境摘要失败：" + e.getMessage());
                log.warn("写入环境摘要失败", e);
            }

            // 4) 写入最近任务上下文
            try {
                latestFailure = writeLastRun(tempDir, missing);
            } catch (Exception e) {
                missing.add("最近任务记录失败：" + e.getMessage());
                log.warn("写入 last-run.json 失败", e);
            }

            // 5) 生成 summary.md（必须最后写，确保汇总缺失信息）
            String env = detectEnvironment();
            Path summaryPath = tempDir.resolve("summary.md");
            writeSummary(summaryPath, traceId, env, latestFailure.orElse(null), missing);

            // 6) 原子替换 latest
            replaceLatest(latestDir, tempDir);

            if (!missing.isEmpty()) {
                status = DiagnosticResultVO.Status.PARTIAL;
            }

            long duration = System.currentTimeMillis() - start;
            log.info("诊断包生成完成：status={}, duration={}ms, latest={}", status, duration, latestDir);

            return DiagnosticResultVO.builder()
                .traceId(traceId)
                .packagePath(latestDir.toString())
                .summaryPath(latestDir.resolve("summary.md").toString())
                .status(status)
                .durationMs(duration)
                .missingItems(missing)
                .generatedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            TraceContext.setErrorType(e.getClass().getSimpleName());
            log.error("诊断包生成失败：{}", e.getMessage(), e);
            // 清理失败的临时目录
            if (tempDir != null) {
                deleteQuietly(tempDir);
            }
            long duration = System.currentTimeMillis() - start;
            return DiagnosticResultVO.builder()
                .traceId(traceId)
                .packagePath(latestDir.toString())
                .summaryPath("")
                .status(DiagnosticResultVO.Status.FAILED)
                .durationMs(duration)
                .missingItems(List.of("诊断生成异常：" + e.getMessage()))
                .generatedAt(LocalDateTime.now())
                .build();
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 复制日志摘录（app.log、error.log）
     */
    private void copyLogEvidence(Path tempDir, int maxLines, List<String> missing) throws IOException {
        Path logsTarget = tempDir.resolve("logs");
        Files.createDirectories(logsTarget);
        Path logSource = Paths.get(logPath);

        // 收集已知敏感值，作为额外替换字典
        List<String> knownSecrets = collectKnownSecretValues();

        Path errorLog = logSource.resolve("error.log");
        if (Files.exists(errorLog)) {
            copyTail(errorLog, logsTarget.resolve("error.log"), maxLines, knownSecrets);
        } else {
            missing.add("logs/error.log 不存在或不可读");
        }

        Path appLog = logSource.resolve("app.log");
        if (Files.exists(appLog)) {
            copyTail(appLog, logsTarget.resolve("app.log"), maxLines, knownSecrets);
        } else {
            missing.add("logs/app.log 不存在或不可读");
        }
    }

    /**
     * 收集 environment 中所有敏感字段的非空值，作为额外脱敏字典。
     * 这样即使日志中以非常规格式（如裸字符串）出现配置值，也能保证脱敏。
     */
    private List<String> collectKnownSecretValues() {
        List<String> secrets = new ArrayList<>();
        String[] prefixes = {"app.", "alist.", "spring.datasource.", "server.", "logging."};
        for (PropertySource<?> ps : environment.getPropertySources()) {
            if (ps instanceof EnumerablePropertySource<?> eps) {
                for (String name : eps.getPropertyNames()) {
                    for (String prefix : prefixes) {
                        if (name.startsWith(prefix) && SensitiveDataMasker.isSensitiveKey(name)) {
                            String value = environment.getProperty(name);
                            if (value != null && value.length() >= 4 && !secrets.contains(value)) {
                                secrets.add(value);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return secrets;
    }

    /**
     * 复制文件最后 N 行并对内容做文本级脱敏 + 已知敏感值替换
     */
    private void copyTail(Path source, Path target, int maxLines, List<String> knownSecrets) throws IOException {
        try (Stream<String> lines = Files.lines(source)) {
            List<String> all = lines.toList();
            int from = Math.max(0, all.size() - maxLines);
            List<String> tail = all.subList(from, all.size());
            String content = String.join(System.lineSeparator(), tail);
            // 1) 文本级脱敏（JSON 键值对、Authorization 头、URL 参数、疑似 Token）
            String safe = SensitiveDataMasker.maskText(content);
            // 2) 已知敏感配置值的硬替换（防止裸字符串泄露）
            for (String secret : knownSecrets) {
                safe = safe.replace(secret, SensitiveDataMasker.REDACTED);
            }
            Files.writeString(target, safe);
        }
    }

    /**
     * 写入脱敏配置摘要：config/config.redacted.json
     * <p>
     * 区分：redactedKeys（已脱敏的敏感字段）、emptyKeys（空值字段）、missingKeys（关键但缺失字段）。
     * </p>
     */
    private void writeRedactedConfig(Path tempDir, List<String> missing) throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);

        Map<String, String> entries = new LinkedHashMap<>();
        List<String> redactedKeys = new ArrayList<>();
        List<String> emptyKeys = new ArrayList<>();

        // 仅收集 app.* / alist.* / spring.* / server.* / logging.* 命名空间，避免泄露 JVM 内部属性
        String[] prefixes = {"app.", "alist.", "spring.datasource.", "server.", "logging."};
        for (PropertySource<?> ps : environment.getPropertySources()) {
            if (ps instanceof EnumerablePropertySource<?> eps) {
                for (String name : eps.getPropertyNames()) {
                    for (String prefix : prefixes) {
                        if (name.startsWith(prefix)) {
                            String raw = environment.getProperty(name);
                            if (raw == null || raw.isEmpty()) {
                                if (!entries.containsKey(name)) {
                                    entries.put(name, SensitiveDataMasker.EMPTY);
                                    emptyKeys.add(name);
                                }
                            } else if (SensitiveDataMasker.isSensitiveKey(name)) {
                                if (!entries.containsKey(name)) {
                                    entries.put(name, SensitiveDataMasker.REDACTED);
                                    redactedKeys.add(name);
                                }
                            } else {
                                if (!entries.containsKey(name)) {
                                    entries.put(name, SensitiveDataMasker.maskByKey(name, raw));
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }

        // 关键配置缺失项
        List<String> missingKeys = new ArrayList<>();
        String alistBaseUrl = environment.getProperty("alist.base-url");
        if (alistBaseUrl == null || alistBaseUrl.isEmpty()) {
            missingKeys.add("alist.base-url");
        }
        String alistToken = environment.getProperty("alist.token");
        if (alistToken == null || alistToken.isEmpty()) {
            missingKeys.add("alist.token");
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("entries", entries);
        root.put("redactedKeys", redactedKeys);
        root.put("emptyKeys", emptyKeys);
        root.put("missingKeys", missingKeys);

        Path configFile = configDir.resolve("config.redacted.json");
        Files.writeString(configFile, toJson(root));

        if (!missingKeys.isEmpty()) {
            missing.add("关键配置缺失：" + String.join(", ", missingKeys));
        }
    }

    /**
     * 写入 environment.txt
     */
    private void writeEnvironment(Path tempDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("应用版本: ").append(appVersion).append(System.lineSeparator());
        sb.append("Java 版本: ").append(System.getProperty("java.version")).append(System.lineSeparator());
        sb.append("Java 厂商: ").append(System.getProperty("java.vendor")).append(System.lineSeparator());
        sb.append("操作系统: ").append(System.getProperty("os.name")).append(' ')
            .append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch"))
            .append(System.lineSeparator());
        sb.append("用户目录: ").append(System.getProperty("user.dir")).append(System.lineSeparator());
        sb.append("时区: ").append(java.util.TimeZone.getDefault().getID()).append(System.lineSeparator());
        sb.append("CPU 核心数: ").append(Runtime.getRuntime().availableProcessors()).append(System.lineSeparator());

        Runtime r = Runtime.getRuntime();
        sb.append("JVM 总内存: ").append(r.totalMemory() / 1024 / 1024).append(" MB").append(System.lineSeparator());
        sb.append("JVM 空闲内存: ").append(r.freeMemory() / 1024 / 1024).append(" MB").append(System.lineSeparator());
        sb.append("JVM 最大内存: ").append(r.maxMemory() / 1024 / 1024).append(" MB").append(System.lineSeparator());
        sb.append("数据目录: ").append(appProperties.getDataDir()).append(System.lineSeparator());
        sb.append("日志目录: ").append(logPath).append(System.lineSeparator());

        Files.writeString(tempDir.resolve("environment.txt"), sb.toString());
    }

    /**
     * 写入 last-run.json（最近一次失败或运行记录）
     */
    private Optional<TaskExecution> writeLastRun(Path tempDir, List<String> missing) throws IOException {
        if (taskExecutionRepository.isEmpty()) {
            missing.add("TaskExecutionRepository 不可用，无法收集最近任务");
            return Optional.empty();
        }

        try {
            List<TaskExecution> all = taskExecutionRepository.get().findAll();
            // 优先取最近失败，否则取最近一次
            Optional<TaskExecution> latestFailure = all.stream()
                .filter(e -> e.getStatus() == TaskExecution.ExecutionStatus.FAILED
                          || e.getStatus() == TaskExecution.ExecutionStatus.PARTIAL_SUCCESS)
                .max(Comparator.comparing(TaskExecution::getCreatedAt));

            Optional<TaskExecution> chosen = latestFailure.isPresent() ? latestFailure
                : all.stream().max(Comparator.comparing(TaskExecution::getCreatedAt));

            Map<String, Object> root = new LinkedHashMap<>();
            if (chosen.isEmpty()) {
                root.put("status", "NO_RECORDS");
                root.put("message", "未发现任务执行记录");
                missing.add("无最近任务执行记录");
            } else {
                TaskExecution e = chosen.get();
                root.put("traceId", TraceContext.getTraceId());
                root.put("executionId", e.getId());
                root.put("taskType", e.getTaskType() != null ? e.getTaskType().name() : "UNKNOWN");
                root.put("status", e.getStatus() != null ? e.getStatus().name() : "UNKNOWN");
                root.put("startTime", e.getStartTime() != null ? e.getStartTime().toString() : null);
                root.put("endTime", e.getEndTime() != null ? e.getEndTime().toString() : null);
                root.put("totalFiles", e.getTotalFiles());
                root.put("successFiles", e.getSuccessFiles());
                root.put("failedFiles", e.getFailedFiles());
                root.put("failureDetails", SensitiveDataMasker.maskText(e.getFailureDetails()));
            }
            Files.writeString(tempDir.resolve("last-run.json"), toJson(root));
            return chosen;
        } catch (Exception e) {
            missing.add("查询任务记录失败：" + e.getMessage());
            log.warn("查询 TaskExecution 失败", e);
            return Optional.empty();
        }
    }

    /**
     * 生成 summary.md（diagnostics-output-contract.md 中的必填结构）
     */
    private void writeSummary(Path summaryPath,
                              String traceId,
                              String environment,
                              TaskExecution latestFailure,
                              List<String> missing) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# 诊断摘要").append(System.lineSeparator()).append(System.lineSeparator());

        sb.append("## 基本信息").append(System.lineSeparator());
        sb.append("- 生成时间：").append(LocalDateTime.now().format(SUMMARY_FMT)).append(System.lineSeparator());
        sb.append("- 部署形态：").append(environment).append(System.lineSeparator());
        sb.append("- 应用版本：").append(appVersion == null || appVersion.isEmpty() ? "不可获取" : appVersion).append(System.lineSeparator());
        sb.append("- Trace ID：").append(traceId).append(System.lineSeparator()).append(System.lineSeparator());

        sb.append("## 最近一次失败").append(System.lineSeparator());
        if (latestFailure == null) {
            sb.append("- Trace ID：未发现").append(System.lineSeparator());
            sb.append("- 模块：未知").append(System.lineSeparator());
            sb.append("- 操作：未知").append(System.lineSeparator());
            sb.append("- 错误类型：未知").append(System.lineSeparator());
            sb.append("- 错误信息：未发现近期失败任务").append(System.lineSeparator()).append(System.lineSeparator());
        } else {
            sb.append("- Trace ID：").append(latestFailure.getId()).append(System.lineSeparator());
            sb.append("- 模块：").append(latestFailure.getTaskType() != null ? latestFailure.getTaskType().name() : "未知").append(System.lineSeparator());
            sb.append("- 操作：").append("任务执行").append(System.lineSeparator());
            sb.append("- 错误类型：").append(latestFailure.getStatus() != null ? latestFailure.getStatus().name() : "未知").append(System.lineSeparator());
            String detail = latestFailure.getFailureDetails();
            sb.append("- 错误信息：")
                .append(detail == null || detail.isEmpty() ? "无失败详情" : SensitiveDataMasker.maskText(detail))
                .append(System.lineSeparator()).append(System.lineSeparator());
        }

        sb.append("## 关键证据").append(System.lineSeparator());
        sb.append("- 错误日志：logs/error.log").append(System.lineSeparator());
        sb.append("- 应用日志：logs/app.log").append(System.lineSeparator());
        sb.append("- 配置摘要：config/config.redacted.json").append(System.lineSeparator());
        sb.append("- 环境摘要：environment.txt").append(System.lineSeparator());
        sb.append("- 最近任务：last-run.json").append(System.lineSeparator()).append(System.lineSeparator());

        sb.append("## 缺失信息").append(System.lineSeparator());
        if (missing.isEmpty()) {
            sb.append("- 无").append(System.lineSeparator()).append(System.lineSeparator());
        } else {
            for (String item : missing) {
                sb.append("- ").append(item).append(System.lineSeparator());
            }
            sb.append(System.lineSeparator());
        }

        sb.append("## 建议下一步").append(System.lineSeparator());
        if (latestFailure != null) {
            sb.append("- 通过 Trace ID 在 logs/app.log 与 logs/error.log 中搜索完整执行链路")
                .append(System.lineSeparator());
            sb.append("- 检查 config/config.redacted.json 中相关模块配置是否完整")
                .append(System.lineSeparator());
        } else {
            sb.append("- 检查 logs/app.log 确认服务运行状态").append(System.lineSeparator());
            sb.append("- 通过响应头 X-Trace-Id 串联具体请求与日志").append(System.lineSeparator());
        }

        Files.writeString(summaryPath, sb.toString());
    }

    /**
     * 检测部署形态
     */
    private String detectEnvironment() {
        if (Files.exists(Paths.get("/.dockerenv"))) {
            return "Docker";
        }
        if (Files.exists(Paths.get("runtime/bin/java")) || Files.exists(Paths.get("runtime/bin/java.exe"))) {
            return "一体化启动包";
        }
        return "本地开发";
    }

    /**
     * 原子替换 latest 目录：移走旧目录后再 move 临时目录
     */
    private void replaceLatest(Path latestDir, Path tempDir) throws IOException {
        if (Files.exists(latestDir)) {
            Path backup = latestDir.resolveSibling("latest-old-" + System.currentTimeMillis());
            try {
                Files.move(latestDir, backup, StandardCopyOption.REPLACE_EXISTING);
                deleteQuietly(backup);
            } catch (IOException e) {
                // 回退方案：直接递归删除
                deleteQuietly(latestDir);
            }
        }
        Files.move(tempDir, latestDir, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 递归静默删除目录
     */
    private void deleteQuietly(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ex) {
                    log.debug("删除路径失败：{}", p, ex);
                }
            });
        } catch (IOException e) {
            log.debug("递归删除失败：{}", path, e);
        }
    }

    /**
     * 最小化 JSON 序列化（避免引入额外依赖；对当前键值结构足够）
     */
    private String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, value, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendJson(StringBuilder sb, Object value, int depth) {
        String indent = "  ".repeat(depth);
        String innerIndent = "  ".repeat(depth + 1);
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{').append(System.lineSeparator());
            int i = 0;
            int n = map.size();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append(innerIndent).append('"').append(escape(String.valueOf(entry.getKey()))).append("\": ");
                appendJson(sb, entry.getValue(), depth + 1);
                if (++i < n) {
                    sb.append(',');
                }
                sb.append(System.lineSeparator());
            }
            sb.append(indent).append('}');
        } else if (value instanceof Iterable<?> iter) {
            List<Object> list = new ArrayList<>();
            iter.forEach(list::add);
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[').append(System.lineSeparator());
            for (int i = 0; i < list.size(); i++) {
                sb.append(innerIndent);
                appendJson(sb, list.get(i), depth + 1);
                if (i < list.size() - 1) {
                    sb.append(',');
                }
                sb.append(System.lineSeparator());
            }
            sb.append(indent).append(']');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 包装因 IO 异常导致的失败，便于上层捕获
     */
    private static class DiagnosticIOException extends RuntimeException {
        DiagnosticIOException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 抛出 IO 失败的包装异常
     */
    private void throwUnchecked(String message, IOException e) {
        throw new UncheckedIOException(message, e);
    }
}
