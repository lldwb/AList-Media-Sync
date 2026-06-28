package top.lldwb.alistmediasync.common.util;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 追踪上下文工具
 * <p>
 * 负责 traceId 的生成、合法性校验以及 SLF4J MDC 上下文的读写与清理。
 * 同时承载模块、操作、错误类型等结构化日志字段（参见 spec FR-002/FR-003/SC-005）。
 * </p>
 * <p>
 * traceId 格式约束（与 contracts/http-trace-contract.md 对齐）：
 * <ul>
 *   <li>长度：8 - 128 字符</li>
 *   <li>字符集：英文字母、数字、短横线、下划线、点号</li>
 *   <li>禁止包含空白、换行、控制字符</li>
 * </ul>
 * </p>
 *
 * @author AList-Media-Sync
 */
public final class TraceContext {

    /** MDC 中存储 traceId 的键 */
    public static final String MDC_TRACE_ID = "traceId";
    /** MDC 中存储模块名称的键 */
    public static final String MDC_MODULE = "module";
    /** MDC 中存储操作名称的键 */
    public static final String MDC_OPERATION = "operation";
    /** MDC 中存储错误类型的键 */
    public static final String MDC_ERROR_TYPE = "errorType";

    /** traceId 合法字符集：字母、数字、短横线、下划线、点号 */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]+$");
    /** traceId 最小长度 */
    private static final int MIN_LENGTH = 8;
    /** traceId 最大长度 */
    private static final int MAX_LENGTH = 128;

    /** 用于生成随机后缀 */
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 时间前缀格式：yyyyMMddHHmmss */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 随机后缀字符集（去除易混淆字符） */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private TraceContext() {
        // 工具类禁止实例化
    }

    /**
     * 生成新的 traceId
     * <p>格式：{时间前缀14位}{随机后缀8位}，总长度 22 个字符，全部为字母与数字，
     * 不含连字符，便于在终端或 IDE 中双击整段复制。</p>
     *
     * @return 合法 traceId
     */
    public static String generate() {
        String time = LocalDateTime.now().format(TIME_FMT);
        StringBuilder suffix = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            suffix.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return time + suffix;
    }

    /**
     * 校验 traceId 是否合法
     *
     * @param traceId 待校验的 traceId
     * @return 合法返回 true，否则 false
     */
    public static boolean isValid(String traceId) {
        if (traceId == null) {
            return false;
        }
        int len = traceId.length();
        if (len < MIN_LENGTH || len > MAX_LENGTH) {
            return false;
        }
        return TRACE_ID_PATTERN.matcher(traceId).matches();
    }

    /**
     * 解析候选 traceId：合法时返回原值，非法时返回新生成的值
     *
     * @param candidate 候选 traceId（可为 null）
     * @return 合法 traceId
     */
    public static String resolveOrGenerate(String candidate) {
        return isValid(candidate) ? candidate : generate();
    }

    /**
     * 将 traceId 写入 MDC
     *
     * @param traceId traceId
     */
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(MDC_TRACE_ID, traceId);
        }
    }

    /**
     * 获取当前 MDC 中的 traceId
     *
     * @return 当前 traceId（可能为 null）
     */
    public static String getTraceId() {
        return MDC.get(MDC_TRACE_ID);
    }

    /**
     * 设置模块和操作名称（用于结构化日志的 module 与 operation 字段）
     *
     * @param module    模块名称（如 sync、transcode、webhook）
     * @param operation 操作名称（如 同步任务执行、转码任务创建）
     */
    public static void setModuleOperation(String module, String operation) {
        if (module != null) {
            MDC.put(MDC_MODULE, module);
        }
        if (operation != null) {
            MDC.put(MDC_OPERATION, operation);
        }
    }

    /**
     * 设置错误类型（失败日志结构化字段）
     *
     * @param errorType 错误类别（如 NetworkTimeout、IllegalArgumentException）
     */
    public static void setErrorType(String errorType) {
        if (errorType != null) {
            MDC.put(MDC_ERROR_TYPE, errorType);
        }
    }

    /**
     * 清理当前线程的 traceId、module、operation、errorType MDC 字段
     */
    public static void clear() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_MODULE);
        MDC.remove(MDC_OPERATION);
        MDC.remove(MDC_ERROR_TYPE);
    }

    /**
     * 在已设置 traceId 的上下文中执行 Runnable，结束后自动清理
     *
     * @param traceId   traceId
     * @param module    模块
     * @param operation 操作
     * @param action    执行体
     */
    public static void runWith(String traceId, String module, String operation, Runnable action) {
        String resolved = resolveOrGenerate(traceId);
        try {
            setTraceId(resolved);
            setModuleOperation(module, operation);
            action.run();
        } finally {
            clear();
        }
    }

    /**
     * 在自动管理 traceId 的上下文中执行任务。
     * <p>
     * 语义：
     * <ol>
     *   <li>若当前 MDC 中已有 traceId，则继承之，并在结束时<strong>不清理</strong>（由外层拥有者负责）</li>
     *   <li>否则生成新 traceId 并标记为"本次拥有"，结束时调用 {@link #clear()} 清理 MDC</li>
     *   <li>设置 module 与 operation</li>
     *   <li>执行 task.run()</li>
     *   <li>finally 块：仅在"本次拥有"时清理</li>
     * </ol>
     * </p>
     * <p>
     * 异常处理：task 抛出的异常原样向外抛出，不吞异常，但仍执行 finally 清理。
     * </p>
     *
     * @param module    业务模块名（如 "sync" / "transcode" / "webhook"）
     * @param operation 当前操作描述（如 "同步任务执行"）
     * @param task      要执行的任务
     */
    public static void runWith(String module, String operation, Runnable task) {
        String existing = getTraceId();
        boolean ownsTrace = (existing == null);
        if (ownsTrace) {
            setTraceId(generate());
        }
        setModuleOperation(module, operation);
        try {
            task.run();
        } finally {
            if (ownsTrace) {
                clear();
            }
        }
    }
}
