package top.lldwb.alistmediasync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.client.AListClient;
import top.lldwb.alistmediasync.entity.*;
import top.lldwb.alistmediasync.repository.*;

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
    private final ObjectMapper objectMapper;

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
        } catch (JsonProcessingException e) {
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

        try {
            log.info("正在处理 Webhook 事件：EventId={}, Type={}", event.getEventId(), event.getEventType());

            // 仅处理 FILE_CLOSED 和 SESSION_ENDED 事件
            if (event.getEventType() != WebhookEvent.WebhookEventType.FILE_CLOSED
                && event.getEventType() != WebhookEvent.WebhookEventType.SESSION_ENDED) {
                event.setStatus(WebhookEvent.EventStatus.COMPLETED);
                eventRepository.save(event);
                log.debug("非处理事件类型，跳过：{}", event.getEventType());
                return;
            }

            // 查询匹配的规则
            List<WebhookRule> rules;
            if (event.getRoomId() != null) {
                rules = ruleRepository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
                    convertEventType(event.getEventType()), event.getRoomId());
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
                return;
            }

            // 对每个匹配的规则执行关联动作
            for (WebhookRule rule : rules) {
                executeRuleAction(rule, event);
            }

            event.setStatus(WebhookEvent.EventStatus.COMPLETED);
            eventRepository.save(event);
            log.info("Webhook 事件处理完成：EventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("Webhook 事件处理失败：EventId={} — {}", event.getEventId(), e.getMessage(), e);
            event.setStatus(WebhookEvent.EventStatus.FAILED);
            eventRepository.save(event);
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
            switch (rule.getAction()) {
                case SYNC_ONLY -> {
                    // 构建临时 SyncTask 执行同步
                    SyncTask tempTask = new SyncTask();
                    tempTask.setName("Webhook-" + rule.getName());
                    tempTask.setSourceEngine(rule.getTargetEngine());
                    tempTask.setTargetEngine(rule.getTargetEngine());
                    tempTask.setSourcePath(event.getRelativePath());
                    tempTask.setTargetPath(rule.getTargetPath());
                    tempTask.setSyncMode(SyncTask.SyncMode.NEW_ONLY);
                    tempTask.setTranscodeEnabled(false);
                    syncService.executeSyncTask(tempTask);
                }
                case TRANSCODE_ONLY -> {
                    // 构建转码任务
                    if (event.getRelativePath() != null) {
                        TranscodeTask task = transcodeService.createTask(
                            null,
                            rule.getTargetEngine().getId(),
                            event.getRelativePath(),
                            rule.getTargetPath() + "/" + event.getFileName(),
                            TranscodeTask.TargetFormat.MP3,
                            128000
                        );
                        transcodeService.executeAsync(task);
                        execution.setTranscodeTask(task);
                    }
                }
                case BOTH -> {
                    // 先同步再转码
                    SyncTask tempTask = new SyncTask();
                    tempTask.setName("Webhook-" + rule.getName());
                    tempTask.setSourceEngine(rule.getTargetEngine());
                    tempTask.setTargetEngine(rule.getTargetEngine());
                    tempTask.setSourcePath(event.getRelativePath());
                    tempTask.setTargetPath(rule.getTargetPath());
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
            return WebhookEvent.WebhookEventType.valueOf(type.toUpperCase());
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
}
