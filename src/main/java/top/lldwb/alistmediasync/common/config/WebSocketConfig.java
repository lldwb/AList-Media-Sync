package top.lldwb.alistmediasync.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import top.lldwb.alistmediasync.common.interceptor.WebSocketAuthInterceptor;
import top.lldwb.alistmediasync.common.service.WsSessionManager;

/**
 * WebSocket 配置类
 * <p>
 * 注册原始 WebSocket 端点 /ws/events，启用握手认证拦截器，
 * 通过 WebSocketSessionManager 维护连接数上限控制。
 * 不使用 STOMP（遵循 YAGNI 原则），仅使用 Spring 原始 WebSocket 支持。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WsSessionManager wsSessionManager;
    private final WebSocketAuthInterceptor authInterceptor;
    private final AppProperties appProperties;

    public WebSocketConfig(
        WsSessionManager wsSessionManager,
        WebSocketAuthInterceptor authInterceptor,
        AppProperties appProperties) {
        this.wsSessionManager = wsSessionManager;
        this.authInterceptor = authInterceptor;
        this.appProperties = appProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        int maxConnections = appProperties.getWebsocket().getMaxConnections();
        log.info("注册 WebSocket 端点 /ws/events，最大连接数：{}", maxConnections);

        registry.addHandler(wsSessionManager, "/ws/events")
            .addInterceptors(authInterceptor)
            .setAllowedOrigins("*");
    }
}
