package top.lldwb.alistmediasync.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.config.AppProperties;
import top.lldwb.alistmediasync.dto.ApiResult;
import top.lldwb.alistmediasync.entity.WebhookEvent;
import top.lldwb.alistmediasync.service.WebhookService;

import java.util.Map;

/**
 * 录播姬 Webhook 接收端点
 * <p>
 * 接收录播姬 Webhook v2 协议的 HTTP POST 请求。
 * 此端点不要求认证（在 AuthInterceptor 中已排除）。
 * 接收到事件后立即返回 HTTP 200，异步处理事件。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final AppProperties appProperties;

    /**
     * 接收录播姬 Webhook 事件
     * 响应时间 < 1 秒（仅入库 + 返回）
     */
    @PostMapping("/recorder")
    public ApiResult<String> receiveRecorder(@RequestBody Map<String, Object> body) {
        log.debug("收到录播姬 Webhook 请求：{}", body);

        String eventType = getString(body, "EventType");
        String eventId = getString(body, "EventId");
        String timestamp = getString(body, "Timestamp");

        @SuppressWarnings("unchecked")
        Map<String, Object> eventData = (Map<String, Object>) body.getOrDefault("EventData", Map.of());

        WebhookEvent event = webhookService.receiveWebhookEvent(eventType, eventId, timestamp, eventData);

        // 立即响应，异步处理
        webhookService.processWebhookEvent(event);

        return ApiResult.success("accepted", event.getEventId());
    }

    /**
     * 获取录播姬 Webhook V2 的完整 URL
     * <p>
     * 优先使用 app.server-address 配置，未配置时使用当前请求的 origin。
     * </p>
     */
    @GetMapping("/address")
    public ApiResult<String> getWebhookAddress(HttpServletRequest request) {
        String serverAddress = appProperties.getServerAddress();
        if (serverAddress == null || serverAddress.isBlank()) {
            // 使用请求 origin
            String scheme = request.getScheme();
            String host = request.getServerName();
            int port = request.getServerPort();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
                serverAddress = scheme + "://" + host;
            } else {
                serverAddress = scheme + "://" + host + ":" + port;
            }
        }
        // 去掉末尾斜杠
        serverAddress = serverAddress.replaceAll("/$", "");
        return ApiResult.success(serverAddress + "/api/webhooks/recorder");
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
