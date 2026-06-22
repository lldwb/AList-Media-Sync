package top.lldwb.alistmediasync.common.service;

import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.dto.WsMessage;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket 会话管理与消息广播
 * <p>
 * 负责 WebSocket 连接的建立、关闭、消息推送和连接数控制。
 * 同时作为 TextWebSocketHandler 处理原始 WebSocket 消息。
 * </p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>连接数上限控制：超过 app.websocket.max-connections 时拒绝新连接</li>
 *   <li>增量消息广播：向所有已连接会话推送 WsMessage</li>
 *   <li>DASHBOARD_UPDATE 防抖：2 秒内多次变更合并为一次推送</li>
 * </ul>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
public class WsSessionManager extends TextWebSocketHandler {

    /** 活跃的 WebSocket 会话集合（线程安全） */
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** 当前连接数计数器 */
    private final AtomicInteger connectionCount = new AtomicInteger(0);

    /** 防抖调度器 */
    private final ScheduledExecutorService debounceScheduler = Executors.newSingleThreadScheduledExecutor();

    /** Dashboard 防抖推送的调度 Future，null 表示无待推送任务 */
    private volatile ScheduledFuture<?> dashboardDebounceFuture;

    /** 防抖时间窗口（毫秒） */
    private static final long DASHBOARD_DEBOUNCE_MS = 2000;

    private final JsonMapper jsonMapper;
    private final AppProperties appProperties;

    public WsSessionManager(JsonMapper jsonMapper, AppProperties appProperties) {
        this.jsonMapper = jsonMapper;
        this.appProperties = appProperties;
    }

    // ==================== 连接生命周期 ====================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        int maxConnections = appProperties.getWebsocket().getMaxConnections();

        if (connectionCount.get() >= maxConnections) {
            log.warn("WebSocket 连接数已达上限 {}，拒绝新连接：{}", maxConnections, session.getId());
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException e) {
                log.error("关闭超限 WebSocket 连接时出错：{}", e.getMessage());
            }
            return;
        }

        connectionCount.incrementAndGet();
        sessions.put(session.getId(), session);
        log.info("WebSocket 连接已建立：{}，当前连接数：{}/{}",
            session.getId(), connectionCount.get(), maxConnections);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        connectionCount.decrementAndGet();
        log.info("WebSocket 连接已关闭：{}，状态：{}，当前连接数：{}",
            session.getId(), status, connectionCount.get());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 传输错误：{} — {}", session.getId(), exception.getMessage());
        sessions.remove(session.getId());
        connectionCount.decrementAndGet();
    }

    // ==================== 消息广播 ====================

    /**
     * 向所有已连接会话广播消息
     *
     * @param type    消息类型（使用 MessageType 枚举值）
     * @param payload 增量数据载荷
     */
    public void broadcast(String type, Object payload) {
        WsMessage message = new WsMessage(type, payload, Instant.now().toString());
        String json;
        try {
            json = jsonMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("序列化 WebSocket 消息失败：{}", e.getMessage());
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        int sent = 0;
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                    sent++;
                } catch (IOException e) {
                    log.warn("向会话 {} 发送消息失败：{}", session.getId(), e.getMessage());
                }
            }
        }
        log.debug("广播 {} 消息至 {}/{} 个会话", type, sent, sessions.size());
    }

    /**
     * 推送仪表板更新（带 2 秒防抖）
     * <p>
     * 任务状态变更后延迟 2 秒推送，2 秒内的多次变更合并为一次 Dashboard 更新，
     * 保证数据新鲜度同时避免冗余推送和过度数据库查询。
     * </p>
     *
     * @param payload 仪表板统计数据载荷
     */
    public void broadcastDashboardUpdate(Object payload) {
        // 取消上一次待执行的防抖任务
        ScheduledFuture<?> previous = dashboardDebounceFuture;
        if (previous != null && !previous.isDone()) {
            previous.cancel(false);
        }

        // 重新调度防抖推送
        dashboardDebounceFuture = debounceScheduler.schedule(
            () -> broadcast("DASHBOARD_UPDATE", payload),
            DASHBOARD_DEBOUNCE_MS,
            TimeUnit.MILLISECONDS
        );
    }

    /** 获取当前连接数 */
    public int getConnectionCount() {
        return connectionCount.get();
    }

    /** 获取最大连接数 */
    public int getMaxConnections() {
        return appProperties.getWebsocket().getMaxConnections();
    }
}
