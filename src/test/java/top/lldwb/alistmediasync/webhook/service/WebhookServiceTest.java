package top.lldwb.alistmediasync.webhook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import top.lldwb.alistmediasync.webhook.dto.webhook.WebhookEventVO;
import top.lldwb.alistmediasync.webhook.entity.WebhookEvent;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule;
import top.lldwb.alistmediasync.sync.service.SyncService;
import top.lldwb.alistmediasync.transcode.service.TranscodeService;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.webhook.repository.WebhookEventRepository;
import top.lldwb.alistmediasync.webhook.repository.WebhookRuleRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Webhook 事件处理服务单元测试
 * <p>
 * 覆盖 receiveWebhookEvent()、processWebhookEvent()、listEvents() 三个方法。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Webhook 事件处理服务测试")
class WebhookServiceTest {

    @Mock
    private WebhookEventRepository eventRepository;

    @Mock
    private WebhookRuleRepository ruleRepository;

    @Mock
    private SyncService syncService;

    @Mock
    private TranscodeService transcodeService;

    @Mock
    private StorageEngineRepository storageEngineRepository;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private JsonMapper objectMapper;

    @Mock
    private WsSessionManager wsSessionManager;

    @InjectMocks
    private WebhookService service;

    private Map<String, Object> eventData;

    @BeforeEach
    void setUp() throws Exception {
        eventData = Map.of(
            "SessionId", "test-session-001",
            "RoomId", 12345,
            "RelativePath", "/recordings/room123",
            "FileName", "recording-2026-06-20.flv",
            "FileSize", 1024000L,
            "Duration", 3600.0
        );

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"mock\":\"data\"}");
        when(eventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> {
            WebhookEvent e = inv.getArgument(0);
            if (e.getId() == null) e.setId(1L);
            return e;
        });
        when(taskExecutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ================================================================
    // receiveWebhookEvent 方法测试
    // ================================================================

    @Test
    @DisplayName("接收事件 — 正常保存新事件")
    void shouldReceiveNewWebhookEvent() {
        when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.empty());

        WebhookEvent result = service.receiveWebhookEvent(
            "FileClosed", "evt-001", "1718841600000", eventData);

        assertNotNull(result);
        assertEquals("evt-001", result.getEventId());
        assertEquals(WebhookEvent.WebhookEventType.FILE_CLOSED, result.getEventType());
        assertEquals(WebhookEvent.EventStatus.PENDING, result.getStatus());
        assertEquals("test-session-001", result.getSessionId());
        assertEquals(12345L, result.getRoomId());
        assertEquals("/recordings/room123", result.getRelativePath());
        assertEquals("recording-2026-06-20.flv", result.getFileName());
        assertEquals(1024000L, result.getFileSize());
        verify(eventRepository).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("接收事件 — EventId 重复时标记为 DUPLICATE")
    void shouldMarkDuplicateEvent() {
        WebhookEvent existing = new WebhookEvent();
        existing.setId(1L);
        existing.setEventId("evt-dup");
        existing.setStatus(WebhookEvent.EventStatus.COMPLETED);
        when(eventRepository.findByEventId("evt-dup")).thenReturn(Optional.of(existing));

        WebhookEvent result = service.receiveWebhookEvent(
            "FileClosed", "evt-dup", "1718841600000", eventData);

        assertEquals(WebhookEvent.EventStatus.DUPLICATE, existing.getStatus());
        verify(eventRepository).save(existing);
    }

    @Test
    @DisplayName("接收事件 — 重复事件再次接收不重复标记")
    void shouldNotDoubleMarkDuplicate() {
        WebhookEvent existing = new WebhookEvent();
        existing.setId(1L);
        existing.setEventId("evt-dup");
        existing.setStatus(WebhookEvent.EventStatus.DUPLICATE); // 已标记
        when(eventRepository.findByEventId("evt-dup")).thenReturn(Optional.of(existing));

        WebhookEvent result = service.receiveWebhookEvent(
            "FileClosed", "evt-dup", "1718841600000", eventData);

        // 已标记为 DUPLICATE 的不再 save
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("接收事件 — EventId 为 null 时跳过去重")
    void shouldSkipDedupWhenEventIdIsNull() {
        when(eventRepository.findByEventId(null)).thenReturn(Optional.empty());

        WebhookEvent result = service.receiveWebhookEvent(
            "FileClosed", null, "1718841600000", eventData);

        assertNotNull(result);
        verify(eventRepository).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("接收事件 — 未知事件类型映射为 OTHER")
    void shouldMapUnknownEventTypeToOther() {
        when(eventRepository.findByEventId("evt-unknown")).thenReturn(Optional.empty());

        WebhookEvent result = service.receiveWebhookEvent(
            "UnknownType", "evt-unknown", "1718841600000", eventData);

        assertEquals(WebhookEvent.WebhookEventType.OTHER, result.getEventType());
    }

    @Test
    @DisplayName("接收事件 — 时间戳解析失败时使用当前时间")
    void shouldUseCurrentTimeWhenTimestampInvalid() {
        when(eventRepository.findByEventId("evt-time")).thenReturn(Optional.empty());

        WebhookEvent result = service.receiveWebhookEvent(
            "FileClosed", "evt-time", "not-a-number", eventData);

        assertNotNull(result.getEventTimestamp());
    }

    // ================================================================
    // processWebhookEvent 方法测试
    // ================================================================

    @Test
    @DisplayName("处理事件 — DUPLICATE 状态应跳过")
    void shouldSkipDuplicateEventProcessing() {
        WebhookEvent event = new WebhookEvent();
        event.setId(1L);
        event.setEventId("evt-001");
        event.setStatus(WebhookEvent.EventStatus.DUPLICATE);

        service.processWebhookEvent(event);

        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("处理事件 — 非 FILE_CLOSED/SESSION_ENDED 事件标记为完成")
    void shouldCompleteNonProcessableEvents() {
        WebhookEvent event = new WebhookEvent();
        event.setId(1L);
        event.setEventId("evt-001");
        event.setEventType(WebhookEvent.WebhookEventType.SESSION_STARTED);
        event.setStatus(WebhookEvent.EventStatus.PENDING);

        service.processWebhookEvent(event);

        assertEquals(WebhookEvent.EventStatus.COMPLETED, event.getStatus());
        verify(eventRepository, atLeastOnce()).save(event);
    }

    @Test
    @DisplayName("处理事件 — 无匹配规则时标记为完成")
    void shouldCompleteWhenNoMatchingRules() {
        WebhookEvent event = new WebhookEvent();
        event.setId(1L);
        event.setEventId("evt-001");
        event.setEventType(WebhookEvent.WebhookEventType.FILE_CLOSED);
        event.setStatus(WebhookEvent.EventStatus.PENDING);
        event.setRoomId(99999L);

        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            any(), eq(99999L))).thenReturn(List.of());
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            any(), isNull())).thenReturn(List.of());

        service.processWebhookEvent(event);

        assertEquals(WebhookEvent.EventStatus.COMPLETED, event.getStatus());
    }

    @Test
    @DisplayName("处理事件 — 异常时标记为 FAILED")
    void shouldMarkFailedOnException() {
        WebhookEvent event = new WebhookEvent();
        event.setId(1L);
        event.setEventId("evt-001");
        event.setEventType(WebhookEvent.WebhookEventType.FILE_CLOSED);
        event.setStatus(WebhookEvent.EventStatus.PENDING);

        when(ruleRepository.findByTriggerEventTypeAndEnabledTrue(
            any())).thenThrow(new RuntimeException("数据库异常"));

        service.processWebhookEvent(event);

        assertEquals(WebhookEvent.EventStatus.FAILED, event.getStatus());
    }

    // ================================================================
    // listEvents 方法测试
    // ================================================================

    @Test
    @DisplayName("分页查询 — 正常返回 VO 列表")
    void shouldListEventsWithPagination() {
        WebhookEvent event1 = new WebhookEvent();
        event1.setId(1L);
        event1.setEventId("evt-001");
        event1.setEventType(WebhookEvent.WebhookEventType.FILE_CLOSED);
        event1.setStatus(WebhookEvent.EventStatus.COMPLETED);

        WebhookEvent event2 = new WebhookEvent();
        event2.setId(2L);
        event2.setEventId("evt-002");
        event2.setEventType(WebhookEvent.WebhookEventType.SESSION_ENDED);
        event2.setStatus(WebhookEvent.EventStatus.PENDING);

        when(eventRepository.findAll(any(org.springframework.data.domain.PageRequest.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(event1, event2)));

        List<WebhookEventVO> result = service.listEvents(1, 20);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("evt-001", result.get(0).getEventId());
        assertEquals("evt-002", result.get(1).getEventId());
    }

    @Test
    @DisplayName("分页查询 — 无数据时返回空列表")
    void shouldReturnEmptyListWhenNoEvents() {
        when(eventRepository.findAll(any(org.springframework.data.domain.PageRequest.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        List<WebhookEventVO> result = service.listEvents(1, 20);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("分页查询 — 页码小于 1 时修正为 0")
    void shouldClampPageNumberToZero() {
        when(eventRepository.findAll(any(org.springframework.data.domain.PageRequest.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        assertDoesNotThrow(() -> service.listEvents(0, 20));
        assertDoesNotThrow(() -> service.listEvents(-1, 20));
    }
}
