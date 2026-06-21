package top.lldwb.alistmediasync.webhook.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.webhook.dto.webhook.WebhookEventVO;
import top.lldwb.alistmediasync.webhook.entity.WebhookEvent;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule;
import top.lldwb.alistmediasync.webhook.repository.WebhookEventRepository;
import top.lldwb.alistmediasync.webhook.repository.WebhookRuleRepository;
import top.lldwb.alistmediasync.sync.service.SyncService;
import top.lldwb.alistmediasync.transcode.service.TranscodeService;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.entity.TaskExecution;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.common.service.WsSessionManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Webhook 事件处理服务
 * <p>
 * 处理录播姬 Webhook v2 协议事件：
 * <ul>
 *   <li>接收到事件后立即保存入库（EventId 去重）并返回 HTTP 202</li>
 *   <li>异步处理：匹配规则、执行同步/转码动作</li>
 *   <li>仅处理 FILE_CLOSED 和 SESSION_ENDED 事件</li>
 * </ul>
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEventRepository eventRepository;
    private final WebhookRuleRepository ruleRepository;
    private final SyncService syncService;
    private final TranscodeService transcodeService;
    private final StorageEngineRepository storageEngineRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final JsonMapper objectMapper;
    private final WsSessionManager wsSessionManager;

    /**
     * 接收 Webhook 事件（同步执行，立即返回）
     *
     * @return 保存后的事件实体
     */
    @Transactional
    public WebhookEvent receiveWebhookEvent(String eventType, String eventId, String timestamp,
                                             Map<String, Object> eventData) {
        // 1. EventId 去重
        if (eventId != null && !eventId.isEmpty()) {
            var existing = eventRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                WebhookEvent event = existing.get();
                if (event.getStatus() != WebhookEvent.EventStatus.DUPLICATE) {
                    event.setStatus(WebhookEvent.EventStatus.DUPLICATE);
                    eventRepository.save(event);
                }
                log.debug("Webhook 事件去重：EventId={}", eventId);
                return event;
            }
        }

        // 2. 解析事件
        WebhookEvent event = new WebhookEvent();
        event.setEventId(eventId);
        event.setEventType(parseEventType(eventType));
        event.setEventTimestamp(parseTimestamp(timestamp));
        event.setSessionId(getString(eventData, "SessionId"));
        event.setRoomId(getLong(eventData, "RoomId"));
        event.setRelativePath(getString(eventData, "RelativePath"));
        event.setFileName(getString(eventData, "FileName"));
        event.setFileSize(getLong(eventData, "FileSize"));
        event.setDuration(getLong(eventData, "Duration"));

        // 3. 保存原始数据
        try {
            event.setRawData(objectMapper.writeValueAsString(eventData));
        } catch (JacksonException e) {
            event.setRawData(eventData.toString());
        }

        event.setStatus(WebhookEvent.EventStatus.PENDING);
        event = eventRepository.save(event);

        log.info("Webhook 事件已接收：Type={}, EventId={}", eventType, eventId);
        return event;
    }

    /**
     * 异步处理 Webhook 事件
     */
    @Async
    @Transactional
    public void processWebhookEvent(WebhookEvent event) {
        if (event.getStatus() == WebhookEvent.EventStatus.DUPLICATE) {
            return; // 重复事件不处理
        }

        event.setStatus(WebhookEvent.EventStatus.PROCESSING);
        event = eventRepository.save(event);

        // WebSocket 推送 WEBHOOK_EVENT 消息
        pushWebhookEvent(event);

        try {
            log.info("正在处理 Webhook 事件：EventId={}, Type={}", event.getEventId(), event.getEventType());

            // 仅处理 FILE_CLOSED 和 SESSION_ENDED 事件
            if (event.getEventType() != WebhookEvent.WebhookEventType.FILE_CLOSED
                && event.getEventType() != WebhookEvent.WebhookEventType.SESSION_ENDED) {
                event.setStatus(WebhookEvent.EventStatus.COMPLETED);
                eventRepository.save(event);
                pushWebhookEvent(event);
                log.debug("非处理事件类型，跳过：{}", event.getEventType());
                return;
            }

            // 查询匹配的规则
            List<WebhookRule> rules;
            if (event.getRoomId() != null) {
                rules = new java.util.ArrayList<>(ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
                    convertEventType(event.getEventType()), event.getRoomId()));
                // 同时匹配 roomIdFilter 为空的规则
                var globalRules = ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
                    convertEventType(event.getEventType()), null);
                rules.addAll(globalRules);
            } else {
                rules = ruleRepository.findByTriggerEventTypeAndEnabledTrue(
                    convertEventType(event.getEventType()));
            }

            if (rules.isEmpty()) {
                log.debug("未找到匹配的 Webhook 规则：EventType={}, RoomId={}",
                    event.getEventType(), event.getRoomId());
                event.setStatus(WebhookEvent.EventStatus.COMPLETED);
                eventRepository.save(event);
                pushWebhookEvent(event);
                return;
            }

            // 对每个匹配的规则执行关联动作
            for (WebhookRule rule : rules) {
                executeRuleAction(rule, event);
            }

            event.setStatus(WebhookEvent.EventStatus.COMPLETED);
            eventRepository.save(event);
            pushWebhookEvent(event);
            log.info("Webhook 事件处理完成：EventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("Webhook 事件处理失败：EventId={} — {}", event.getEventId(), e.getMessage(), e);
            event.setStatus(WebhookEvent.EventStatus.FAILED);
            eventRepository.save(event);
            pushWebhookEvent(event);
        }
    }

    private void executeRuleAction(WebhookRule rule, WebhookEvent event) {
        log.info("执行 Webhook 规则：{} (操作: {})", rule.getName(), rule.getAction());

        // 构建 TaskExecution 记录
        TaskExecution execution = new TaskExecution();
        execution.setWebhookEvent(event);
        execution.setTaskType(TaskExecution.TaskType.WEBHOOK);
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(TaskExecution.ExecutionStatus.RUNNING);
        execution = taskExecutionRepository.save(execution);

        try {
            // 源引擎：优先使用 recordingEngine，否则使用 targetEngine
            StorageEngine sourceEngine = rule.getRecordingEngine() != null
                ? rule.getRecordingEngine() : rule.getTargetEngine();

            switch (rule.getAction()) {
                case SYNC_ONLY -> {
                    SyncTask tempTask = new SyncTask();
                    tempTask.setName("Webhook-" + rule.getName());
                    tempTask.setSourceEngine(sourceEngine);
                    tempTask.setTargetEngine(rule.getTargetEngine());
                    tempTask.setSourcePath(event.getRelativePath());
                    tempTask.setTargetPath(rule.getTargetFilePath());
                    tempTask.setSyncMode(SyncTask.SyncMode.NEW_ONLY);
                    tempTask.setTranscodeEnabled(false);
                    syncService.executeSyncTask(tempTask);
                }
                case TRANSCODE_ONLY -> {
                    if (event.getRelativePath() != null && rule.getRecordingEngine() != null) {
                        TranscodeTask task = transcodeService.createTask(
                            sourceEngine.getId(),
                            sourceEngine.getId(), // 纯转码不跨引擎，源即目标
                            event.getRelativePath(),
                            rule.getRecordingPath() + "/" + event.getFileName(),
                            TranscodeTask.TargetFormat.MP3,
                            null, // 使用系统默认码率
                            false // Webhook 触发转码不使用原目录转码
                        );
                        transcodeService.executeAsync(task);
                        execution.setTranscodeTask(task);
                    }
                }
                case BOTH -> {
                    SyncTask tempTask = new SyncTask();
                    tempTask.setName("Webhook-" + rule.getName());
                    tempTask.setSourceEngine(sourceEngine);
                    tempTask.setTargetEngine(rule.getTargetEngine());
                    tempTask.setSourcePath(event.getRelativePath());
                    tempTask.setTargetPath(rule.getTargetFilePath());
                    tempTask.setSyncMode(SyncTask.SyncMode.NEW_ONLY);
                    tempTask.setTranscodeEnabled(true);
                    tempTask.setTargetFormat(SyncTask.TargetFormat.MP3);
                    syncService.executeSyncTask(tempTask);
                }
            }

            execution.setStatus(TaskExecution.ExecutionStatus.SUCCESS);
        } catch (Exception e) {
            log.error("规则执行失败：{} — {}", rule.getName(), e.getMessage(), e);
            execution.setStatus(TaskExecution.ExecutionStatus.FAILED);
            execution.setFailureDetails(e.getMessage());
        } finally {
            execution.setEndTime(LocalDateTime.now());
            taskExecutionRepository.save(execution);
        }
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private WebhookEvent.WebhookEventType parseEventType(String type) {
        if (type == null) return WebhookEvent.WebhookEventType.OTHER;
        try {
            // 支持 PascalCase（如 FileClosed）和 UPPER_SNAKE_CASE（如 FILE_CLOSED）
            String normalized = type.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
            return WebhookEvent.WebhookEventType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return WebhookEvent.WebhookEventType.OTHER;
        }
    }

    private WebhookRule.WebhookEventType convertEventType(WebhookEvent.WebhookEventType type) {
        try {
            return WebhookRule.WebhookEventType.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            return WebhookRule.WebhookEventType.OTHER;
        }
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null) return LocalDateTime.now();
        try {
            long epochMs = Long.parseLong(timestamp);
            return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (NumberFormatException e) {
            return LocalDateTime.now();
        }
    }

    private String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    private Long getLong(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val instanceof Number num) return num.longValue();
        if (val != null) {
            try { return Long.parseLong(val.toString()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    // ================================================================
    // 查询方法（供 Controller 调用）
    // ================================================================

    /**
     * 分页查询 Webhook 事件列表（按创建时间倒序）
     *
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     * @return 事件视图对象列表
     */
    public List<WebhookEventVO> listEvents(int page, int size) {
        int pageIndex = Math.max(0, page - 1);
        PageRequest pageRequest = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return eventRepository.findAll(pageRequest)
            .stream()
            .map(WebhookEventVO::from)
            .toList();
    }

    /**
     * 通过 WebSocket 推送 Webhook 事件状态变更
     */
    private void pushWebhookEvent(WebhookEvent event) {
        wsSessionManager.broadcast("WEBHOOK_EVENT", Map.of(
            "eventId", event.getId(),
            "eventType", event.getEventType().name(),
            "status", event.getStatus().name()
        ));
    }
}
