package top.lldwb.alistmediasync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.dto.webhook.WebhookRuleCreateDTO;
import top.lldwb.alistmediasync.dto.webhook.WebhookRuleVO;
import top.lldwb.alistmediasync.entity.StorageEngine;
import top.lldwb.alistmediasync.entity.WebhookRule;
import top.lldwb.alistmediasync.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.repository.WebhookRuleRepository;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Webhook 规则管理服务
 * <p>
 * 负责规则的 CRUD 和启用/禁用操作。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookRuleService {

    private final WebhookRuleRepository repository;
    private final StorageEngineRepository storageEngineRepository;

    @Transactional
    public WebhookRuleVO create(WebhookRuleCreateDTO dto) {
        if (dto.getTargetEngineId() == null || dto.getTargetEngineId() < 1) {
            throw new IllegalArgumentException("targetEngineId 必须为正整数");
        }
        StorageEngine engine = storageEngineRepository.findById(dto.getTargetEngineId())
            .orElseThrow(() -> new NoSuchElementException(
                "目标存储引擎不存在：id=" + dto.getTargetEngineId()));
        WebhookRule entity = new WebhookRule();
        entity.setName(dto.getName());
        entity.setTriggerEventType(dto.getTriggerEventType());
        entity.setRoomIdFilter(dto.getRoomIdFilter());
        entity.setAction(dto.getAction());
        entity.setTargetEngine(engine);
        entity.setTargetPath(dto.getTargetPath());
        entity.setEnabled(true);
        entity = repository.save(entity);
        log.info("Webhook 规则已创建：{} (触发: {})", entity.getName(), entity.getTriggerEventType());
        return WebhookRuleVO.from(entity);
    }

    @Transactional
    public WebhookRuleVO update(Long id, WebhookRuleCreateDTO dto) {
        WebhookRule entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Webhook 规则不存在：id=" + id));

        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getTriggerEventType() != null) entity.setTriggerEventType(dto.getTriggerEventType());
        if (dto.getRoomIdFilter() != null) entity.setRoomIdFilter(dto.getRoomIdFilter());
        if (dto.getAction() != null) entity.setAction(dto.getAction());
        if (dto.getTargetEngineId() != null) entity.setTargetEngine(storageEngineRepository.getReferenceById(dto.getTargetEngineId()));
        if (dto.getTargetPath() != null) entity.setTargetPath(dto.getTargetPath());

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
}
