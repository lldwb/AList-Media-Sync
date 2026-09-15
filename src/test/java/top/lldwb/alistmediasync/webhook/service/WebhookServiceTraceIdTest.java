package top.lldwb.alistmediasync.webhook.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.common.util.TraceContext;
import top.lldwb.alistmediasync.execution.TaskExecution;
import top.lldwb.alistmediasync.execution.TaskExecutionRepository;
import top.lldwb.alistmediasync.sync.service.SyncService;
import top.lldwb.alistmediasync.sync.service.SyncTaskManageService;
import top.lldwb.alistmediasync.transcode.service.TranscodeService;
import top.lldwb.alistmediasync.webhook.entity.WebhookEvent;
import top.lldwb.alistmediasync.webhook.repository.WebhookEventRepository;
import top.lldwb.alistmediasync.webhook.repository.WebhookRuleRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Webhook 任务 traceId 贯穿测试（T036）
 * <p>
 * 直接驱动真实的 {@link WebhookService}，在<b>依赖被调用的执行路径上</b>抓取 MDC，
 * 断言接收/异步处理入口的 module、operation、traceId 继承与清理语义。
 * 仅断言 {@code TraceContext} 自身的读写，测的是工具类而非服务，无法证明服务做了这些设置。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook 任务 traceId 贯穿测试")
class WebhookServiceTraceIdTest {

    @Mock
    private WebhookEventRepository eventRepository;

    @Mock
    private WebhookRuleRepository ruleRepository;

    @Mock
    private SyncService syncService;

    @Mock
    private SyncTaskManageService syncTaskManageService;

    @Mock
    private TranscodeService transcodeService;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private JsonMapper objectMapper;

    @Mock
    private WsSessionManager wsSessionManager;

    @InjectMocks
    private WebhookService service;

    @AfterEach
    void cleanup() {
        TraceContext.clear();
    }

    // ================================================================
    // 测试辅助
    // ================================================================

    private void stubEventSave() {
        when(eventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> {
            WebhookEvent e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1L);
            }
            return e;
        });
    }

    private void stubRawDataSerialization() {
        JsonMapper realMapper = new JsonMapper();
        try {
            when(objectMapper.writeValueAsString(any()))
                .thenAnswer(inv -> realMapper.writeValueAsString(inv.getArgument(0)));
        } catch (Exception e) {
            throw new IllegalStateException("桩声明失败", e);
        }
    }

    private WebhookEvent event(WebhookEvent.WebhookEventType type, Long roomId) {
        WebhookEvent event = new WebhookEvent();
        event.setId(1L);
        event.setEventId("evt-001");
        event.setEventType(type);
        event.setStatus(WebhookEvent.EventStatus.PENDING);
        event.setRoomId(roomId);
        return event;
    }

    // ================================================================
    // 用例
    // ================================================================

    @Test
    @DisplayName("接收 Webhook 事件应设置 module 与 operation，并保留上游 traceId")
    void shouldSetModuleAndOperationOnReceive() {
        TraceContext.setTraceId("upstream-webhook-trace-001");
        when(eventRepository.findByEventId("evt-trace-001")).thenReturn(Optional.empty());
        stubEventSave();
        stubRawDataSerialization();

        service.receiveWebhookEvent("FileClosed", "evt-trace-001", "1718841600000",
            Map.of("RoomId", 12345));

        assertEquals("webhook", MDC.get(TraceContext.MDC_MODULE),
            "接收入口应把 module 写为 webhook");
        assertEquals("接收事件：FileClosed", MDC.get(TraceContext.MDC_OPERATION),
            "接收入口应把 operation 写为「接收事件：<事件类型>」");
        assertEquals("upstream-webhook-trace-001", MDC.get(TraceContext.MDC_TRACE_ID),
            "接收入口不应改写上游 traceId");
    }

    @Test
    @DisplayName("异步处理 Webhook 应设置 module/operation 并继承上游 traceId")
    void shouldInheritUpstreamTraceIdOnAsyncProcessing() {
        TraceContext.setTraceId("upstream-webhook-trace-001");

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        AtomicReference<String> capturedModule = new AtomicReference<>();
        AtomicReference<String> capturedOperation = new AtomicReference<>();
        when(eventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> {
            WebhookEvent e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1L);
            }
            capturedTraceId.set(MDC.get(TraceContext.MDC_TRACE_ID));
            capturedModule.set(MDC.get(TraceContext.MDC_MODULE));
            capturedOperation.set(MDC.get(TraceContext.MDC_OPERATION));
            return e;
        });

        service.processWebhookEvent(event(WebhookEvent.WebhookEventType.SESSION_STARTED, 12345L));

        assertEquals("upstream-webhook-trace-001", capturedTraceId.get(),
            "异步处理路径上应继承上游 traceId");
        assertEquals("webhook", capturedModule.get(), "异步处理路径上 module 应为 webhook");
        assertEquals("异步处理事件：evt-001", capturedOperation.get(),
            "异步处理路径上 operation 应为「异步处理事件：<EventId>」");
        // 上游 traceId 由外层拥有，服务不应清理
        assertEquals("upstream-webhook-trace-001", MDC.get(TraceContext.MDC_TRACE_ID));
    }

    @Test
    @DisplayName("异步处理 Webhook 无上游 traceId 时应生成合法 traceId 并在结束后清理")
    void shouldGenerateAndClearTraceIdWhenNoUpstream() {
        TraceContext.clear();

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        when(eventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> {
            WebhookEvent e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1L);
            }
            capturedTraceId.set(MDC.get(TraceContext.MDC_TRACE_ID));
            return e;
        });

        service.processWebhookEvent(event(WebhookEvent.WebhookEventType.SESSION_STARTED, 12345L));

        assertNotNull(capturedTraceId.get(), "异步处理路径上必须存在 traceId");
        assertTrue(TraceContext.isValid(capturedTraceId.get()),
            "自动生成的 traceId 应满足格式契约，实际=" + capturedTraceId.get());
        assertNull(MDC.get(TraceContext.MDC_TRACE_ID), "处理结束后应清理 traceId");
        assertNull(MDC.get(TraceContext.MDC_MODULE), "处理结束后应清理 module");
        assertNull(MDC.get(TraceContext.MDC_OPERATION), "处理结束后应清理 operation");
    }

    @Test
    @DisplayName("Webhook 处理失败应设置 errorType 结构化字段")
    void shouldSetErrorTypeOnFailure() {
        TraceContext.clear();
        // 无房间号 → 走全局规则查询分支，此处抛出异常
        when(ruleRepository.findByTriggerEventTypeAndEnabledTrue(any()))
            .thenThrow(new RuntimeException("数据库异常"));

        AtomicReference<String> capturedErrorType = new AtomicReference<>();
        AtomicReference<String> capturedOperation = new AtomicReference<>();
        when(eventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> {
            WebhookEvent e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1L);
            }
            if (e.getStatus() == WebhookEvent.EventStatus.FAILED) {
                capturedErrorType.set(MDC.get(TraceContext.MDC_ERROR_TYPE));
                capturedOperation.set(MDC.get(TraceContext.MDC_OPERATION));
            }
            return e;
        });

        service.processWebhookEvent(event(WebhookEvent.WebhookEventType.FILE_CLOSED, null));

        assertEquals("RuntimeException", capturedErrorType.get(),
            "失败路径应将异常类名写入 errorType 结构化字段");
        assertEquals("异步处理事件：evt-001", capturedOperation.get(),
            "失败路径的 operation 仍应为异步处理入口语义");
    }
}
