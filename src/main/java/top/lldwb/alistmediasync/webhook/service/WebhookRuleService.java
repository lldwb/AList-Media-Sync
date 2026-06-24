package top.lldwb.alistmediasync.webhook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.webhook.dto.webhook.WebhookRuleCreateDTO;
import top.lldwb.alistmediasync.webhook.dto.webhook.WebhookRuleVO;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule.RuleAction;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.webhook.repository.WebhookRuleRepository;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Webhook 规则管理服务
 * <p>
 * 负责规则的 CRUD 和启用/禁用操作。
 * 支持录播存储引擎关联和 recordingEngine 校验。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookRuleService {

    private final WebhookRuleRepository repository;
    private final StorageEngineRepository storageEngineRepository;

    @Transactional
    public WebhookRuleVO create(WebhookRuleCreateDTO dto) {
        // 校验：SYNC_ONLY / BOTH 时 targetEngineId 和 targetFilePath 必填
        validateTargetEngine(dto);
        // 校验：TRANSCODE_ONLY / BOTH 时 recordingEngineId 和 recordingPath 必填
        validateRecordingEngine(dto);

        StorageEngine targetEngine = null;
        if (dto.getTargetEngineId() != null && dto.getTargetEngineId() > 0) {
            targetEngine = storageEngineRepository.findById(dto.getTargetEngineId())
                .orElseThrow(() -> new NoSuchElementException(
                    "目标存储引擎不存在：id=" + dto.getTargetEngineId()));
        }

        WebhookRule entity = new WebhookRule();
        entity.setName(dto.getName());
        entity.setTriggerEventType(dto.getTriggerEventType());
        entity.setRoomIdFilter(dto.getRoomIdFilter());
        entity.setAction(dto.getAction());
        entity.setTargetEngine(targetEngine);
        entity.setTargetFilePath(dto.getTargetFilePath());

        // 录播存储引擎关联
        if (dto.getRecordingEngineId() != null && dto.getRecordingEngineId() > 0) {
            StorageEngine recordingEngine = storageEngineRepository.findById(dto.getRecordingEngineId())
                .orElseThrow(() -> new NoSuchElementException(
                    "录播存储引擎不存在：id=" + dto.getRecordingEngineId()));
            entity.setRecordingEngine(recordingEngine);
        }
        entity.setRecordingPath(dto.getRecordingPath());

        entity.setEnabled(true);
        entity = repository.save(entity);
        log.info("Webhook 规则已创建：{} (触发: {}, 操作: {})", entity.getName(),
            entity.getTriggerEventType(), entity.getAction());
        return WebhookRuleVO.from(entity);
    }

    @Transactional
    public WebhookRuleVO update(Long id, WebhookRuleCreateDTO dto) {
        WebhookRule entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Webhook 规则不存在：id=" + id));

        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getTriggerEventType() != null) entity.setTriggerEventType(dto.getTriggerEventType());
        if (dto.getRoomIdFilter() != null) entity.setRoomIdFilter(dto.getRoomIdFilter());
        if (dto.getAction() != null) {
            entity.setAction(dto.getAction());
            // 操作变更后重新校验必填字段
            validateRecordingEngineForEntity(dto, dto.getAction());
            validateTargetEngineForEntity(dto, dto.getAction());
        }
        if (dto.getTargetEngineId() != null) {
            entity.setTargetEngine(storageEngineRepository.getReferenceById(dto.getTargetEngineId()));
        }
        if (dto.getTargetFilePath() != null) entity.setTargetFilePath(dto.getTargetFilePath());
        if (dto.getRecordingEngineId() != null) {
            entity.setRecordingEngine(storageEngineRepository.getReferenceById(dto.getRecordingEngineId()));
        }
        if (dto.getRecordingPath() != null) entity.setRecordingPath(dto.getRecordingPath());

        entity = repository.save(entity);
        log.info("Webhook 规则已更新：{}", entity.getName());
        return WebhookRuleVO.from(entity);
    }

    @Transactional
    public void delete(Long id) {
        WebhookRule entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Webhook 规则不存在：id=" + id));
        repository.delete(entity);
        log.info("Webhook 规则已删除：{}", entity.getName());
    }

    public WebhookRuleVO getById(Long id) {
        return repository.findById(id)
            .map(WebhookRuleVO::from)
            .orElseThrow(() -> new NoSuchElementException("Webhook 规则不存在：id=" + id));
    }

    public List<WebhookRuleVO> listAll() {
        return repository.findAll().stream()
            .map(WebhookRuleVO::from)
            .toList();
    }

    @Transactional
    public WebhookRuleVO enable(Long id) {
        WebhookRule entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Webhook 规则不存在：id=" + id));
        entity.setEnabled(true);
        entity = repository.save(entity);
        log.info("Webhook 规则已启用：{}", entity.getName());
        return WebhookRuleVO.from(entity);
    }

    @Transactional
    public WebhookRuleVO disable(Long id) {
        WebhookRule entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Webhook 规则不存在：id=" + id));
        entity.setEnabled(false);
        entity = repository.save(entity);
        log.info("Webhook 规则已禁用：{}", entity.getName());
        return WebhookRuleVO.from(entity);
    }

    // ================================================================
    // 私有辅助方法
    // ================================================================

    /**
     * 校验 targetEngine 必填关系
     */
    private void validateTargetEngine(WebhookRuleCreateDTO dto) {
        RuleAction action = dto.getAction();
        if (action == RuleAction.SYNC_ONLY || action == RuleAction.BOTH) {
            if (dto.getTargetEngineId() == null || dto.getTargetEngineId() < 1) {
                throw new IllegalArgumentException(
                    "操作为" + action + "时，目标存储引擎（targetEngineId）为必填项");
            }
            if (dto.getTargetFilePath() == null || dto.getTargetFilePath().isBlank()) {
                throw new IllegalArgumentException(
                    "操作为" + action + "时，目标文件路径（targetFilePath）为必填项");
            }
        }
    }

    /**
     * 校验 recordingEngine 必填关系
     */
    private void validateRecordingEngine(WebhookRuleCreateDTO dto) {
        RuleAction action = dto.getAction();
        if (action == RuleAction.TRANSCODE_ONLY || action == RuleAction.BOTH) {
            if (dto.getRecordingEngineId() == null || dto.getRecordingEngineId() < 1) {
                throw new IllegalArgumentException(
                    "操作为" + action + "时，录播存储引擎（recordingEngineId）为必填项");
            }
            if (dto.getRecordingPath() == null || dto.getRecordingPath().isBlank()) {
                throw new IllegalArgumentException(
                    "操作为" + action + "时，录播文件路径（recordingPath）为必填项");
            }
        }
    }

    /** 更新时的 recordingEngine 校验 */
    private void validateRecordingEngineForEntity(WebhookRuleCreateDTO dto, RuleAction action) {
        if (action == RuleAction.TRANSCODE_ONLY || action == RuleAction.BOTH) {
            Long recordingEngineId = dto.getRecordingEngineId();
            if (recordingEngineId == null || recordingEngineId < 1) {
                throw new IllegalArgumentException(
                    "操作变更为" + action + "时，录播存储引擎（recordingEngineId）为必填项");
            }
        }
    }

    /** 更新时的 targetEngine 校验 */
    private void validateTargetEngineForEntity(WebhookRuleCreateDTO dto, RuleAction action) {
        if (action == RuleAction.SYNC_ONLY || action == RuleAction.BOTH) {
            if (dto.getTargetEngineId() == null || dto.getTargetEngineId() < 1) {
                throw new IllegalArgumentException(
                    "操作变更为" + action + "时，目标存储引擎（targetEngineId）为必填项");
            }
        }
    }
}
