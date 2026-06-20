package top.lldwb.alistmediasync.webhook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.webhook.entity.WebhookEvent;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Webhook 事件 Repository
 *
 * @author AList-Media-Sync
 */
@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    /** 按 EventId 查询（去重检测） */
    Optional<WebhookEvent> findByEventId(String eventId);

    /** 删除过期记录 */
    @Modifying
    @Transactional
    @Query("DELETE FROM WebhookEvent e WHERE e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
