package top.lldwb.alistmediasync.integration.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import top.lldwb.alistmediasync.webhook.entity.WebhookEvent;
import top.lldwb.alistmediasync.webhook.repository.WebhookEventRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Webhook 事件 Repository 集成测试
 * <p>
 * 验证 findByEventId 幂等去重、EventId 唯一索引、DUPLICATE 状态转换。
 * 使用 {@link DataJpaTest} + H2 内存数据库。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("WebhookEventRepository 集成测试")
class WebhookEventRepositoryIT {

    @Autowired
    private WebhookEventRepository repository;

    @BeforeEach
    void setUp() {
        WebhookEvent event = new WebhookEvent();
        event.setEventId("evt-001");
        event.setEventType(WebhookEvent.WebhookEventType.FILE_CLOSED);
        event.setEventTimestamp(LocalDateTime.now());
        event.setStatus(WebhookEvent.EventStatus.PENDING);
        event.setSessionId("session-001");
        event.setRoomId(12345L);
        event.setFileName("test.flv");
        event.setFileSize(1024L);
        repository.save(event);
    }

    @Test
    @DisplayName("findByEventId - 按 EventId 查询已存在事件")
    void shouldFindByEventId() {
        Optional<WebhookEvent> result = repository.findByEventId("evt-001");

        assertTrue(result.isPresent());
        assertEquals("evt-001", result.get().getEventId());
        assertEquals(WebhookEvent.WebhookEventType.FILE_CLOSED, result.get().getEventType());
        assertEquals(WebhookEvent.EventStatus.PENDING, result.get().getStatus());
    }

    @Test
    @DisplayName("findByEventId - 不存在的 EventId 返回空")
    void shouldReturnEmptyForNonExistentEventId() {
        Optional<WebhookEvent> result = repository.findByEventId("non-existent");

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("EventId 唯一索引防止重复插入")
    void shouldPreventDuplicateEventId() {
        WebhookEvent duplicate = new WebhookEvent();
        duplicate.setEventId("evt-001"); // 相同 EventId
        duplicate.setEventType(WebhookEvent.WebhookEventType.SESSION_STARTED);
        duplicate.setEventTimestamp(LocalDateTime.now());
        duplicate.setStatus(WebhookEvent.EventStatus.PENDING);

        assertThrows(Exception.class, () -> repository.saveAndFlush(duplicate),
            "唯一索引约束应阻止重复 EventId");
    }

    @Test
    @DisplayName("EventId 不同的事件可以重复插入")
    void shouldAllowDifferentEventIds() {
        WebhookEvent event2 = new WebhookEvent();
        event2.setEventId("evt-002");
        event2.setEventType(WebhookEvent.WebhookEventType.FILE_OPENED);
        event2.setEventTimestamp(LocalDateTime.now());
        event2.setStatus(WebhookEvent.EventStatus.PENDING);

        assertDoesNotThrow(() -> repository.saveAndFlush(event2));
    }

    @Test
    @DisplayName("DUPLICATE 状态持久化与查询")
    void shouldPersistAndQueryDuplicateStatus() {
        // 模拟去重逻辑：标记已存在事件为 DUPLICATE
        WebhookEvent existing = repository.findByEventId("evt-001").orElseThrow();
        existing.setStatus(WebhookEvent.EventStatus.DUPLICATE);
        repository.save(existing);

        WebhookEvent updated = repository.findByEventId("evt-001").orElseThrow();
        assertEquals(WebhookEvent.EventStatus.DUPLICATE, updated.getStatus());
    }

    @Test
    @DisplayName("状态转换：PENDING -> PROCESSING -> COMPLETED")
    void shouldTransitionStatus() {
        WebhookEvent event = repository.findByEventId("evt-001").orElseThrow();

        event.setStatus(WebhookEvent.EventStatus.PROCESSING);
        repository.save(event);
        assertEquals(WebhookEvent.EventStatus.PROCESSING,
            repository.findByEventId("evt-001").orElseThrow().getStatus());

        event.setStatus(WebhookEvent.EventStatus.COMPLETED);
        repository.save(event);
        assertEquals(WebhookEvent.EventStatus.COMPLETED,
            repository.findByEventId("evt-001").orElseThrow().getStatus());
    }

    @Test
    @DisplayName("枚举持久化：所有 EventType 和 EventStatus")
    void shouldPersistAllEnumValues() {
        for (WebhookEvent.WebhookEventType type : WebhookEvent.WebhookEventType.values()) {
            for (WebhookEvent.EventStatus status : WebhookEvent.EventStatus.values()) {
                WebhookEvent event = new WebhookEvent();
                event.setEventId("evt-enum-" + type + "-" + status);
                event.setEventType(type);
                event.setEventTimestamp(LocalDateTime.now());
                event.setStatus(status);

                WebhookEvent saved = repository.save(event);
                assertEquals(type, saved.getEventType());
                assertEquals(status, saved.getStatus());
            }
        }
    }

    @Test
    @DisplayName("持久化验证自动填充 createdAt/updatedAt/version")
    void shouldAutoFillTimestamps() {
        WebhookEvent saved = repository.findByEventId("evt-001").orElseThrow();

        assertNotNull(saved.getCreatedAt(), "createdAt 应自动填充");
        assertNotNull(saved.getUpdatedAt(), "updatedAt 应自动填充");
        assertEquals(0L, saved.getVersion(), "初始 version 应为 0");
    }

    @Test
    @DisplayName("deleteByCreatedAtBefore - 删除过期事件")
    void shouldDeleteExpiredEvents() {
        int deleted = repository.deleteByCreatedAtBefore(LocalDateTime.now().plusMinutes(1));
        assertTrue(deleted > 0);
    }
}