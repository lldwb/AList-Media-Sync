package top.lldwb.alistmediasync.webhook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule.WebhookEventType;

import java.util.List;

/**
 * Webhook 规则 Repository
 *
 * @author AList-Media-Sync
 */
@Repository
public interface WebhookRuleRepository extends JpaRepository<WebhookRule, Long> {

    /** 查询所有已启用的规则 */
    List<WebhookRule> findByEnabledTrue();

    /** 按事件类型和启用状态查询 */
    List<WebhookRule> findByTriggerEventTypeAndEnabledTrue(WebhookEventType eventType);

    /** 按事件类型、房间号和启用状态查询 */
    List<WebhookRule> findByTriggerEventTypeAndRoomIdFilterAndEnabledTrue(WebhookEventType eventType, Long roomIdFilter);
}
