package top.lldwb.alistmediasync.webhook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.lldwb.alistmediasync.common.enums.TargetFormat;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.execution.TaskExecution;
import top.lldwb.alistmediasync.execution.TaskExecutionRepository;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.service.SyncService;
import top.lldwb.alistmediasync.sync.service.SyncTaskManageService;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.service.TranscodeService;
import top.lldwb.alistmediasync.webhook.dto.WebhookEventVO;
import top.lldwb.alistmediasync.webhook.entity.WebhookEvent;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule;
import top.lldwb.alistmediasync.webhook.repository.WebhookEventRepository;
import top.lldwb.alistmediasync.webhook.repository.WebhookRuleRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Webhook 事件处理服务单元测试
 * <p>
 * 覆盖 receiveWebhookEvent()、processWebhookEvent()（含 SYNC_ONLY / TRANSCODE_ONLY / BOTH
 * 三条规则动作分支）、listEvents() 三个方法。
 * </p>
 * <p>
 * 不使用类级 LENIENT：严格桩校验可暴露"Mock 声明了但未被被测路径使用"的问题，
 * 每个用例只声明自身真正依赖的桩（见 {@link #stubEventSave()}、{@link #stubTaskExecutionSave()}）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook 事件处理服务测试")
class WebhookServiceTest {

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

    private Map<String, Object> eventData;

    private StorageEngine recordingEngine;
    private StorageEngine targetEngine;

    @BeforeEach
    void setUp() {
        eventData = Map.of(
            "SessionId", "test-session-001",
            "RoomId", 12345,
            "RelativePath", "/recordings/room123",
            "FileName", "recording-2026-06-20.flv",
            "FileSize", 1024000L,
            "Duration", 3600.0
        );

        recordingEngine = new StorageEngine();
        recordingEngine.setId(10L);
        recordingEngine.setName("录播引擎");

        targetEngine = new StorageEngine();
        targetEngine.setId(20L);
        targetEngine.setName("目标引擎");
    }

    // ================================================================
    // 测试辅助
    // ================================================================

    /** 模拟 JPA 主键回填：save 返回入参并补全 ID */
    private void stubEventSave() {
        when(eventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> {
            WebhookEvent e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(1L);
            }
            return e;
        });
    }

    /** 模拟 TaskExecution 主键回填 */
    private void stubTaskExecutionSave() {
        when(taskExecutionRepository.save(any(TaskExecution.class))).thenAnswer(inv -> {
            TaskExecution e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(500L);
            }
            return e;
        });
    }

    /** 构造待处理的 FILE_CLOSED 事件 */
    private WebhookEvent fileClosedEvent(Long roomId) {
        WebhookEvent event = new WebhookEvent();
        event.setId(1L);
        event.setEventId("evt-001");
        event.setEventType(WebhookEvent.WebhookEventType.FILE_CLOSED);
        event.setStatus(WebhookEvent.EventStatus.PENDING);
        event.setRoomId(roomId);
        event.setRelativePath("/recordings/room123");
        event.setFileName("recording-2026-06-20.flv");
        return event;
    }

    /** 构造指定动作的规则 */
    private WebhookRule rule(Long id, String name, WebhookRule.RuleAction action,
                             StorageEngine recordingEngine, StorageEngine targetEngine) {
        WebhookRule rule = new WebhookRule();
        rule.setId(id);
        rule.setName(name);
        rule.setAction(action);
        rule.setTriggerEventType(WebhookRule.WebhookEventType.FILE_CLOSED);
        rule.setRecordingEngine(recordingEngine);
        rule.setRecordingPath("/录播源目录");
        rule.setTargetEngine(targetEngine);
        rule.setTargetFilePath("/媒体目标目录");
        rule.setEnabled(true);
        return rule;
    }

    // ================================================================
    // receiveWebhookEvent 方法测试
    // ================================================================

    @Test
    @DisplayName("接收事件 — 正常保存新事件并逐一解析 EventData 字段")
    void shouldReceiveNewWebhookEvent() throws Exception {
        when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.empty());
        stubEventSave();
        when(objectMapper.writeValueAsString(eventData)).thenReturn("{\"mock\":\"data\"}");

        WebhookEvent result = service.receiveWebhookEvent(
            "FileClosed", "evt-001", "1718841600000", eventData);

        assertEquals("evt-001", result.getEventId());
        assertEquals(WebhookEvent.WebhookEventType.FILE_CLOSED, result.getEventType());
        assertEquals(WebhookEvent.EventStatus.PENDING, result.getStatus());
        assertEquals("test-session-001", result.getSessionId());
        assertEquals(12345L, result.getRoomId());
        assertEquals("/recordings/room123", result.getRelativePath());
        assertEquals("recording-2026-06-20.flv", result.getFileName());
        assertEquals(1024000L, result.getFileSize());
        // Duration 在 EventData 中为 Double（3600.0），getLong 应取整数部分而非丢失该字段
        assertEquals(3600L, result.getDuration());
        assertEquals("{\"mock\":\"data\"}", result.getRawData());
        // 毫秒时间戳应被真实解析（而非落到"当前时间"兜底分支）
        assertEquals(
            Instant.ofEpochMilli(1718841600000L).atZone(ZoneId.systemDefault()).toLocalDateTime(),
            result.getEventTimestamp());
        assertTrue(result.getEventTimestamp().isBefore(LocalDateTime.now().minusDays(1)),
            "录播姬提供的时间戳应被解析，而不是兜底为当前时间");
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

        assertSame(existing, result, "重复事件应直接返回既有记录");
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

        assertSame(existing, result);
        // 已标记为 DUPLICATE 的不再 save
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("接收事件 — 并发重发唯一索引冲突时重查返回既有事件")
    void shouldRecoverWhenUniqueIndexConflict() {
        WebhookEvent existing = new WebhookEvent();
        existing.setId(1L);
        existing.setEventId("evt-race");
        existing.setStatus(WebhookEvent.EventStatus.PENDING);
        // 去重检查未发现 → save 触发唯一索引冲突 → 重查返回既有事件
        when(eventRepository.findByEventId("evt-race"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));
        when(eventRepository.save(any(WebhookEvent.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("unique constraint"));

        WebhookEvent result = service.receiveWebhookEvent(
            "FileClosed", "evt-race", "1718841600000", eventData);

        assertSame(existing, result, "唯一索引冲突时应返回既有事件而非抛异常");
    }

    @Test
    @DisplayName("接收事件 — EventId 为 null 时跳过去重")
    void shouldSkipDedupWhenEventIdIsNull() throws Exception {
        stubEventSave();
        when(objectMapper.writeValueAsString(eventData)).thenReturn("{\"mock\":\"data\"}");

        WebhookEvent result = service.receiveWebhookEvent(
            "FileClosed", null, "1718841600000", eventData);

        assertNull(result.getEventId());
        assertEquals(WebhookEvent.EventStatus.PENDING, result.getStatus());
        verify(eventRepository, never()).findByEventId(any());
        verify(eventRepository).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("接收事件 — 未知事件类型映射为 OTHER")
    void shouldMapUnknownEventTypeToOther() throws Exception {
        when(eventRepository.findByEventId("evt-unknown")).thenReturn(Optional.empty());
        stubEventSave();
        when(objectMapper.writeValueAsString(eventData)).thenReturn("{\"mock\":\"data\"}");

        WebhookEvent result = service.receiveWebhookEvent(
            "UnknownType", "evt-unknown", "1718841600000", eventData);

        assertEquals(WebhookEvent.WebhookEventType.OTHER, result.getEventType());
    }

    @Test
    @DisplayName("接收事件 — UPPER_SNAKE_CASE 事件类型同样可解析")
    void shouldParseUpperSnakeCaseEventType() throws Exception {
        when(eventRepository.findByEventId("evt-snake")).thenReturn(Optional.empty());
        stubEventSave();
        when(objectMapper.writeValueAsString(eventData)).thenReturn("{\"mock\":\"data\"}");

        WebhookEvent result = service.receiveWebhookEvent(
            "SESSION_ENDED", "evt-snake", "1718841600000", eventData);

        assertEquals(WebhookEvent.WebhookEventType.SESSION_ENDED, result.getEventType());
    }

    @Test
    @DisplayName("接收事件 — 时间戳解析失败时使用当前时间")
    void shouldUseCurrentTimeWhenTimestampInvalid() throws Exception {
        when(eventRepository.findByEventId("evt-time")).thenReturn(Optional.empty());
        stubEventSave();
        when(objectMapper.writeValueAsString(eventData)).thenReturn("{\"mock\":\"data\"}");

        LocalDateTime beforeCall = LocalDateTime.now().minusSeconds(5);
        WebhookEvent result = service.receiveWebhookEvent(
            "FileClosed", "evt-time", "not-a-number", eventData);

        // 兜底分支必须落到"当前时间"附近（合法的毫秒时间戳已被上一个用例证明会走解析分支）
        LocalDateTime ts = result.getEventTimestamp();
        assertTrue(ts.isAfter(beforeCall), "非法时间戳应兜底为当前时间，实际=" + ts);
        assertTrue(ts.isBefore(LocalDateTime.now().plusSeconds(5)), "兜底时间不应偏离当前时间，实际=" + ts);
    }

    // ================================================================
    // processWebhookEvent 方法测试
    // ================================================================

    @Test
    @DisplayName("处理事件 — DUPLICATE 状态应跳过")
    void shouldSkipDuplicateEventProcessing() {
        WebhookEvent event = fileClosedEvent(12345L);
        event.setStatus(WebhookEvent.EventStatus.DUPLICATE);

        service.processWebhookEvent(event);

        assertEquals(WebhookEvent.EventStatus.DUPLICATE, event.getStatus());
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(ruleRepository, syncService, syncTaskManageService, transcodeService);
    }

    @Test
    @DisplayName("处理事件 — 非 FILE_CLOSED/SESSION_ENDED 事件标记为完成")
    void shouldCompleteNonProcessableEvents() {
        WebhookEvent event = fileClosedEvent(12345L);
        event.setEventType(WebhookEvent.WebhookEventType.SESSION_STARTED);
        stubEventSave();

        service.processWebhookEvent(event);

        assertEquals(WebhookEvent.EventStatus.COMPLETED, event.getStatus());
        verify(eventRepository, atLeastOnce()).save(event);
        verifyNoInteractions(ruleRepository);
    }

    @Test
    @DisplayName("处理事件 — 无匹配规则时标记为完成")
    void shouldCompleteWhenNoMatchingRules() {
        WebhookEvent event = fileClosedEvent(99999L);
        stubEventSave();

        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            any(), eq(99999L))).thenReturn(List.of());
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            any(), isNull())).thenReturn(List.of());

        service.processWebhookEvent(event);

        assertEquals(WebhookEvent.EventStatus.COMPLETED, event.getStatus());
        verifyNoInteractions(syncService, syncTaskManageService, transcodeService);
    }

    @Test
    @DisplayName("处理事件 — 异常时标记为 FAILED")
    void shouldMarkFailedOnException() {
        WebhookEvent event = fileClosedEvent(null);
        stubEventSave();

        when(ruleRepository.findByTriggerEventTypeAndEnabledTrue(any()))
            .thenThrow(new RuntimeException("数据库异常"));

        service.processWebhookEvent(event);

        assertEquals(WebhookEvent.EventStatus.FAILED, event.getStatus());
    }

    @Test
    @DisplayName("处理事件 — 无房间号事件走全局规则查询分支")
    void shouldQueryGlobalRulesWhenRoomIdIsNull() {
        WebhookEvent event = fileClosedEvent(null);
        event.setId(9L);
        stubEventSave();
        stubTaskExecutionSave();

        WebhookRule globalRule = rule(3L, "全局规则", WebhookRule.RuleAction.SYNC_ONLY,
            recordingEngine, targetEngine);
        when(ruleRepository.findByTriggerEventTypeAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED)).thenReturn(List.of(globalRule));

        SyncTask tempTask = new SyncTask();
        tempTask.setId(88L);
        when(syncTaskManageService.createWebhookTempTask(anyString(), any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(tempTask);

        service.processWebhookEvent(event);

        assertEquals(WebhookEvent.EventStatus.COMPLETED, event.getStatus());
        verify(ruleRepository, never()).findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(any(), any());
        verify(syncTaskManageService).createWebhookTempTask(
            eq("Webhook-全局规则"), same(recordingEngine), same(targetEngine),
            eq("/录播源目录"), eq("/媒体目标目录"), eq(false), isNull());
    }

    // ================================================================
    // executeRuleAction — SYNC_ONLY / BOTH / TRANSCODE_ONLY 三条动作分支
    // ================================================================

    @Test
    @DisplayName("规则动作 SYNC_ONLY — 临时同步任务参数正确且事务提交后才触发执行")
    void shouldExecuteSyncOnlyRuleAction() {
        WebhookEvent event = fileClosedEvent(12345L);
        stubEventSave();
        stubTaskExecutionSave();

        WebhookRule syncOnlyRule = rule(7L, "录播规则A", WebhookRule.RuleAction.SYNC_ONLY,
            recordingEngine, targetEngine);
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, 12345L)).thenReturn(List.of(syncOnlyRule));
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, null)).thenReturn(List.of());

        SyncTask tempTask = new SyncTask();
        tempTask.setId(88L);
        when(syncTaskManageService.createWebhookTempTask(anyString(), any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(tempTask);

        // 模拟 Spring 事务同步上下文，否则 registerSynchronization 会抛 IllegalStateException
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.processWebhookEvent(event);

            // 临时任务的每个构造参数逐一核对（SYNC_ONLY：不启用转码、目标格式保持默认）
            verify(syncTaskManageService).createWebhookTempTask(
                eq("Webhook-录播规则A"),
                same(recordingEngine),
                same(targetEngine),
                eq("/录播源目录"),
                eq("/媒体目标目录"),
                eq(false),
                isNull());

            // 注册了事务同步回调，且回调内容为"提交后触发同步"
            List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size(), "应注册且仅注册一个 afterCommit 回调");
            verify(syncService, never()).executeSyncTask(any());
            synchronizations.get(0).afterCommit();
            verify(syncService).executeSyncTask(tempTask);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // 规则执行记录：WEBHOOK / SUCCESS / 关联事件
        ArgumentCaptor<TaskExecution> executionCaptor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository, times(2)).save(executionCaptor.capture());
        TaskExecution finalExecution = executionCaptor.getValue();
        assertEquals(TaskExecution.TaskType.WEBHOOK, finalExecution.getTaskType());
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, finalExecution.getStatus());
        assertEquals(event.getId(), finalExecution.getWebhookEventId());
        assertNotNull(finalExecution.getEndTime());
        assertNull(finalExecution.getFailureDetails(), "成功路径不应写入失败明细");

        // 事件最终状态与 WebSocket 推送
        assertEquals(WebhookEvent.EventStatus.COMPLETED, event.getStatus());
        assertBroadcastStatuses("PROCESSING", "COMPLETED");
    }

    @Test
    @DisplayName("规则动作 BOTH — 临时同步任务启用转码且目标格式为 MP3")
    void shouldExecuteBothRuleAction() {
        WebhookEvent event = fileClosedEvent(12345L);
        stubEventSave();
        stubTaskExecutionSave();

        WebhookRule bothRule = rule(8L, "录播规则B", WebhookRule.RuleAction.BOTH,
            recordingEngine, targetEngine);
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, 12345L)).thenReturn(List.of(bothRule));
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, null)).thenReturn(List.of());

        SyncTask tempTask = new SyncTask();
        tempTask.setId(89L);
        when(syncTaskManageService.createWebhookTempTask(anyString(), any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(tempTask);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.processWebhookEvent(event);

            // BOTH：启用转码 + 目标格式 MP3，其余参数与 SYNC_ONLY 一致
            verify(syncTaskManageService).createWebhookTempTask(
                eq("Webhook-录播规则B"),
                same(recordingEngine),
                same(targetEngine),
                eq("/录播源目录"),
                eq("/媒体目标目录"),
                eq(true),
                eq(TargetFormat.MP3));

            List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.get(0).afterCommit();
            verify(syncService).executeSyncTask(tempTask);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertEquals(WebhookEvent.EventStatus.COMPLETED, event.getStatus());
        ArgumentCaptor<TaskExecution> executionCaptor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository, times(2)).save(executionCaptor.capture());
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, executionCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("规则动作 SYNC_ONLY — 未配置录播引擎时回退用目标引擎作源引擎")
    void shouldFallbackToTargetEngineWhenRecordingEngineMissing() {
        WebhookEvent event = fileClosedEvent(12345L);
        stubEventSave();
        stubTaskExecutionSave();

        WebhookRule ruleWithoutRecordingEngine = rule(9L, "无录播引擎规则",
            WebhookRule.RuleAction.SYNC_ONLY, null, targetEngine);
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, 12345L)).thenReturn(List.of(ruleWithoutRecordingEngine));
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, null)).thenReturn(List.of());

        when(syncTaskManageService.createWebhookTempTask(anyString(), any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(new SyncTask());

        service.processWebhookEvent(event);

        verify(syncTaskManageService).createWebhookTempTask(
            anyString(), same(targetEngine), same(targetEngine), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("规则动作 TRANSCODE_ONLY — 创建并异步执行转码任务，不注册同步任务")
    void shouldExecuteTranscodeOnlyRuleAction() {
        WebhookEvent event = fileClosedEvent(12345L);
        stubEventSave();
        stubTaskExecutionSave();

        WebhookRule transcodeRule = rule(11L, "录播规则C", WebhookRule.RuleAction.TRANSCODE_ONLY,
            recordingEngine, targetEngine);
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, 12345L)).thenReturn(List.of(transcodeRule));
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, null)).thenReturn(List.of());

        TranscodeTask transcodeTask = new TranscodeTask();
        transcodeTask.setId(66L);
        when(transcodeService.createTask(anyLong(), anyLong(), anyString(), anyString(),
            any(TargetFormat.class), any(), anyBoolean())).thenReturn(transcodeTask);

        service.processWebhookEvent(event);

        // 纯转码：源即目标，目标路径 = 录播路径 + 文件名，格式 MP3，使用系统默认码率
        verify(transcodeService).createTask(
            eq(10L), eq(10L), eq("/recordings/room123"),
            eq("/录播源目录/recording-2026-06-20.flv"),
            eq(TargetFormat.MP3), isNull(), eq(false));
        verify(transcodeService).executeAsync(transcodeTask);
        // 不应创建同步任务、不应注册 afterCommit
        verify(syncTaskManageService, never()).createWebhookTempTask(
            any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(syncService, never()).executeSyncTask(any());

        // 执行记录应关联转码任务且成功
        ArgumentCaptor<TaskExecution> executionCaptor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository, times(2)).save(executionCaptor.capture());
        TaskExecution finalExecution = executionCaptor.getValue();
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, finalExecution.getStatus());
        assertEquals(66L, finalExecution.getTranscodeTaskId());
        assertEquals(WebhookEvent.EventStatus.COMPLETED, event.getStatus());
    }

    @Test
    @DisplayName("规则动作 TRANSCODE_ONLY — 缺少 RelativePath 时跳过转码但仍标记执行成功")
    void shouldSkipTranscodeWhenRelativePathMissing() {
        WebhookEvent event = fileClosedEvent(12345L);
        event.setRelativePath(null);
        stubEventSave();
        stubTaskExecutionSave();

        WebhookRule transcodeRule = rule(12L, "录播规则D", WebhookRule.RuleAction.TRANSCODE_ONLY,
            recordingEngine, targetEngine);
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, 12345L)).thenReturn(List.of(transcodeRule));
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, null)).thenReturn(List.of());

        service.processWebhookEvent(event);

        verifyNoInteractions(transcodeService);
        verifyNoInteractions(syncService);

        ArgumentCaptor<TaskExecution> executionCaptor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository, times(2)).save(executionCaptor.capture());
        assertEquals(TaskExecution.ExecutionStatus.SUCCESS, executionCaptor.getValue().getStatus());
        assertNull(executionCaptor.getValue().getTranscodeTaskId());
    }

    @Test
    @DisplayName("规则动作执行异常 — 执行记录标记 FAILED 且事件仍标记完成")
    void shouldMarkExecutionFailedWhenRuleActionThrows() {
        WebhookEvent event = fileClosedEvent(12345L);
        stubEventSave();
        stubTaskExecutionSave();

        WebhookRule syncOnlyRule = rule(13L, "录播规则E", WebhookRule.RuleAction.SYNC_ONLY,
            recordingEngine, targetEngine);
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, 12345L)).thenReturn(List.of(syncOnlyRule));
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, null)).thenReturn(List.of());

        when(syncTaskManageService.createWebhookTempTask(anyString(), any(), any(), any(), any(), anyBoolean(), any()))
            .thenThrow(new IllegalStateException("源引擎不存在"));

        service.processWebhookEvent(event);

        ArgumentCaptor<TaskExecution> executionCaptor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository, times(2)).save(executionCaptor.capture());
        TaskExecution finalExecution = executionCaptor.getValue();
        assertEquals(TaskExecution.ExecutionStatus.FAILED, finalExecution.getStatus());
        assertEquals("源引擎不存在", finalExecution.getFailureDetails());
        assertEquals(WebhookEvent.EventStatus.COMPLETED, event.getStatus());
        verify(syncService, never()).executeSyncTask(any());
    }

    /** 断言 WEBHOOK_EVENT 推送的状态序列 */
    private void assertBroadcastStatuses(String... expectedStatuses) {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(wsSessionManager, times(expectedStatuses.length))
            .broadcast(eq("WEBHOOK_EVENT"), payloadCaptor.capture());

        List<Object> payloads = payloadCaptor.getAllValues();
        for (int i = 0; i < expectedStatuses.length; i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) payloads.get(i);
            assertEquals(1L, payload.get("eventId"));
            assertEquals("FILE_CLOSED", payload.get("eventType"));
            assertEquals(expectedStatuses[i], payload.get("status"));
        }
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

        when(eventRepository.findAll(any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(event1, event2)));

        List<WebhookEventVO> result = service.listEvents(1, 20);

        assertEquals(2, result.size());
        assertEquals("evt-001", result.get(0).getEventId());
        assertEquals("evt-002", result.get(1).getEventId());
    }

    @Test
    @DisplayName("分页查询 — 无数据时返回空列表")
    void shouldReturnEmptyListWhenNoEvents() {
        when(eventRepository.findAll(any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));

        List<WebhookEventVO> result = service.listEvents(1, 20);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("分页查询 — 页码小于 1 时修正为 0")
    void shouldClampPageNumberToZero() {
        when(eventRepository.findAll(any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));

        service.listEvents(0, 20);
        service.listEvents(-1, 20);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(eventRepository, times(2)).findAll(captor.capture());

        PageRequest firstRequest = captor.getAllValues().get(0);
        PageRequest secondRequest = captor.getAllValues().get(1);
        assertEquals(0, firstRequest.getPageNumber(), "page=0 应被修正为 0 基页码 0");
        assertEquals(0, secondRequest.getPageNumber(), "page=-1 应被修正为 0 基页码 0");
        assertEquals(20, firstRequest.getPageSize());
        assertEquals(20, secondRequest.getPageSize());
        // 排序契约：createdAt 倒序
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), firstRequest.getSort());
    }

    @Test
    @DisplayName("分页查询 — 1 基页码换算为 Spring Data 的 0 基页码")
    void shouldConvertOneBasedPageNumberToZeroBased() {
        when(eventRepository.findAll(any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));

        service.listEvents(3, 15);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(eventRepository).findAll(captor.capture());
        assertEquals(2, captor.getValue().getPageNumber(), "page=3 应换算为 0 基页码 2");
        assertEquals(15, captor.getValue().getPageSize());
    }

    // ================================================================
    // pushWebhookEvent 推送内容
    // ================================================================

    @Test
    @DisplayName("WebSocket 推送 — 未匹配到规则时推送 PROCESSING 与 COMPLETED 两条消息")
    void shouldPushProcessingAndCompletedForUnmatchedEvent() {
        WebhookEvent event = fileClosedEvent(99999L);
        stubEventSave();
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(any(), eq(99999L)))
            .thenReturn(List.of());
        when(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(any(), isNull()))
            .thenReturn(List.of());

        service.processWebhookEvent(event);

        assertBroadcastStatuses("PROCESSING", "COMPLETED");
    }
}
