package top.lldwb.alistmediasync.integration.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule;
import top.lldwb.alistmediasync.webhook.repository.WebhookRuleRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Webhook 规则 Repository 集成测试
 * <p>
 * 验证持久化、findByTriggerEventTypeAndEnabledTrue、规则匹配查询。
 * 使用 {@link DataJpaTest} + H2 内存数据库。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("WebhookRuleRepository 集成测试")
class WebhookRuleRepositoryIT {

    @Autowired
    private WebhookRuleRepository repository;

    @Autowired
    private StorageEngineRepository engineRepository;

    private StorageEngine targetEngine;

    @BeforeEach
    void setUp() {
        targetEngine = new StorageEngine();
        targetEngine.setName("目标引擎");
        targetEngine.setEngineType(StorageEngine.EngineType.ALIST);
        targetEngine.setBaseUrl("https://target.example.com");
        targetEngine.setEncryptedToken("target-token");
        targetEngine.setStatus(StorageEngine.EngineStatus.ONLINE);
        engineRepository.save(targetEngine);

        // 创建测试规则
        repository.save(createRule("规则1-FILE_CLOSED-无过滤",
            WebhookRule.WebhookEventType.FILE_CLOSED, null,
            WebhookRule.RuleAction.BOTH, true));
        repository.save(createRule("规则2-FILE_CLOSED-房间123",
            WebhookRule.WebhookEventType.FILE_CLOSED, 123L,
            WebhookRule.RuleAction.SYNC_ONLY, true));
        repository.save(createRule("规则3-SESSION_STARTED-无过滤",
            WebhookRule.WebhookEventType.SESSION_STARTED, null,
            WebhookRule.RuleAction.TRANSCODE_ONLY, true));
        repository.save(createRule("规则4-FILE_CLOSED-禁用",
            WebhookRule.WebhookEventType.FILE_CLOSED, null,
            WebhookRule.RuleAction.BOTH, false));
        repository.save(createRule("规则5-FILE_CLOSED-房间456",
            WebhookRule.WebhookEventType.FILE_CLOSED, 456L,
            WebhookRule.RuleAction.SYNC_ONLY, true));
    }

    @Test
    @DisplayName("持久化并验证默认值")
    void shouldPersistWithDefaults() {
        WebhookRule rule = createRule("默认值测试",
            WebhookRule.WebhookEventType.FILE_OPENED, null,
            WebhookRule.RuleAction.BOTH, true);
        WebhookRule saved = repository.save(rule);

        assertNotNull(saved.getId());
        assertEquals(WebhookRule.RuleAction.BOTH, saved.getAction());
        assertTrue(saved.getEnabled());
        assertNotNull(saved.getCreatedAt());
        assertEquals(0L, saved.getVersion());
    }

    @Test
    @DisplayName("findByEnabledTrue - 查询已启用的规则")
    void shouldFindEnabledRules() {
        List<WebhookRule> result = repository.findByEnabledTrue();

        assertEquals(4, result.size());
        assertTrue(result.stream().allMatch(WebhookRule::getEnabled));
    }

    @Test
    @DisplayName("findByTriggerEventTypeAndEnabledTrue - 按事件类型查询")
    void shouldFindByEventTypeAndEnabled() {
        List<WebhookRule> result = repository.findByTriggerEventTypeAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED);

        // 3 个启用的 FILE_CLOSED 规则（规则1, 2, 5）
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(r ->
            r.getTriggerEventType() == WebhookRule.WebhookEventType.FILE_CLOSED
                && r.getEnabled()));
    }

    @Test
    @DisplayName("findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue - 按事件类型和房间号查询")
    void shouldFindByEventTypeAndRoomIdAndEnabled() {
        List<WebhookRule> result = repository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, 123L);

        assertEquals(1, result.size());
        assertEquals("规则2-FILE_CLOSED-房间123", result.get(0).getName());
        assertEquals(123L, result.get(0).getRoomIdFilter());
    }

    @Test
    @DisplayName("findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue - 房间号不匹配返回空")
    void shouldReturnEmptyWhenRoomIdNotMatch() {
        List<WebhookRule> result = repository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, 999L);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue - null 房间号过滤匹配全局规则")
    void shouldFindGlobalRulesWithNullRoomId() {
        List<WebhookRule> result = repository.findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED, null);

        // roomIdFilter 为 null 的规则（规则1）
        assertEquals(1, result.size());
        assertNull(result.get(0).getRoomIdFilter());
    }

    @Test
    @DisplayName("禁用的规则不被查询")
    void shouldNotReturnDisabledRules() {
        List<WebhookRule> result = repository.findByTriggerEventTypeAndEnabledTrue(
            WebhookRule.WebhookEventType.FILE_CLOSED);

        // 规则4 被禁用，不应出现
        assertFalse(result.stream().anyMatch(r -> "规则4-FILE_CLOSED-禁用".equals(r.getName())));
    }

    @Test
    @DisplayName("RuleAction 枚举持久化")
    void shouldPersistRuleActionEnum() {
        for (WebhookRule.RuleAction action : WebhookRule.RuleAction.values()) {
            WebhookRule rule = createRule("action-" + action,
                WebhookRule.WebhookEventType.FILE_RENAMED, null, action, true);
            WebhookRule saved = repository.save(rule);
            assertEquals(action, saved.getAction());
        }
    }

    @Test
    @DisplayName("WebhookEventType 枚举持久化")
    void shouldPersistWebhookEventTypeEnum() {
        for (WebhookRule.WebhookEventType type : WebhookRule.WebhookEventType.values()) {
            WebhookRule rule = createRule("type-" + type,
                type, null, WebhookRule.RuleAction.BOTH, true);
            WebhookRule saved = repository.save(rule);
            assertEquals(type, saved.getTriggerEventType());
        }
    }

    @Test
    @DisplayName("启用/禁用切换")
    void shouldToggleEnabled() {
        WebhookRule rule = repository.findByEnabledTrue().get(0);
        rule.setEnabled(false);
        repository.save(rule);

        List<WebhookRule> enabled = repository.findByEnabledTrue();
        assertFalse(enabled.stream().anyMatch(r -> r.getId().equals(rule.getId())));
    }

    private WebhookRule createRule(String name, WebhookRule.WebhookEventType eventType,
                                   Long roomIdFilter, WebhookRule.RuleAction action,
                                   boolean enabled) {
        WebhookRule rule = new WebhookRule();
        rule.setName(name);
        rule.setTriggerEventType(eventType);
        rule.setRoomIdFilter(roomIdFilter);
        rule.setAction(action);
        rule.setTargetEngine(targetEngine);
        rule.setTargetFilePath("/target/path");
        rule.setEnabled(enabled);
        return rule;
    }
}