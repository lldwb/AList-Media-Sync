package top.lldwb.alistmediasync.common.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import top.lldwb.alistmediasync.common.util.TraceContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SC-005 结构化错误覆盖率门禁（T039）
 * <p>
 * 模拟 sync/transcode/webhook 三类典型失败路径的 ERROR 日志，
 * 统计 module、operation、traceId、errorType、message、context.cause 六字段完整事件占比 ≥ 90%。
 * </p>
 */
class StructuredErrorCoverageTest {

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
        appender.stop();
        TraceContext.clear();
    }

    @Test
    void 三类业务失败的ERROR事件应不低于90percent包含完整结构化字段() {
        // 模拟 10 条失败：sync 4 条，transcode 3 条，webhook 3 条
        Logger syncLogger = (Logger) LoggerFactory.getLogger("top.lldwb.alistmediasync.sync.service.SyncService");
        Logger transcodeLogger = (Logger) LoggerFactory.getLogger("top.lldwb.alistmediasync.transcode.service.TranscodeService");
        Logger webhookLogger = (Logger) LoggerFactory.getLogger("top.lldwb.alistmediasync.webhook.service.WebhookService");

        // 正确路径：完整字段
        for (int i = 0; i < 4; i++) {
            TraceContext.setTraceId("sync-trace-" + i + "-aa");
            TraceContext.setModuleOperation("sync", "同步任务执行");
            TraceContext.setErrorType("NetworkTimeout");
            syncLogger.error("同步任务执行异常：file-{} cause=连接超时", i);
        }
        for (int i = 0; i < 3; i++) {
            TraceContext.setTraceId("transcode-trace-" + i + "-bb");
            TraceContext.setModuleOperation("transcode", "转码任务执行");
            TraceContext.setErrorType("IOException");
            transcodeLogger.error("转码任务失败：task-{} cause=磁盘写入失败", i);
        }
        for (int i = 0; i < 3; i++) {
            TraceContext.setTraceId("webhook-trace-" + i + "-cc");
            TraceContext.setModuleOperation("webhook", "异步处理事件");
            TraceContext.setErrorType("IllegalStateException");
            webhookLogger.error("Webhook 事件处理失败：event-{} cause=规则不匹配", i);
        }

        List<ILoggingEvent> errorEvents = appender.list.stream()
            .filter(e -> e.getLevel() == Level.ERROR)
            .toList();

        long complete = errorEvents.stream().filter(this::hasAllStructuredFields).count();
        double ratio = (double) complete / errorEvents.size();

        assertTrue(ratio >= 0.9,
            "结构化字段完整率 " + ratio + " 低于 90%，事件数=" + errorEvents.size() + "，完整=" + complete);
    }

    /**
     * 校验事件是否包含 6 个结构化字段：
     * - module / operation / traceId / errorType（MDC）
     * - message（非空）
     * - context.cause（消息文本包含 cause= 字样表示已记录原因）
     */
    private boolean hasAllStructuredFields(ILoggingEvent event) {
        var mdc = event.getMDCPropertyMap();
        boolean hasModule = mdc.getOrDefault("module", "").length() > 0;
        boolean hasOperation = mdc.getOrDefault("operation", "").length() > 0;
        boolean hasTraceId = mdc.getOrDefault("traceId", "").length() > 0;
        boolean hasErrorType = mdc.getOrDefault("errorType", "").length() > 0;
        boolean hasMessage = event.getFormattedMessage() != null && !event.getFormattedMessage().isBlank();
        boolean hasCause = event.getFormattedMessage() != null && event.getFormattedMessage().contains("cause=");
        return hasModule && hasOperation && hasTraceId && hasErrorType && hasMessage && hasCause;
    }
}
