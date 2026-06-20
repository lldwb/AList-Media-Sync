package top.lldwb.alistmediasync.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.dto.ApiResult;
import top.lldwb.alistmediasync.dto.webhook.WebhookEventVO;
import top.lldwb.alistmediasync.service.WebhookService;

import java.util.List;

/**
 * Webhook 事件查询 API
 * <p>
 * 提供 Webhook 接收事件的只读分页查询功能，供前端事件列表页使用。
 * 不提供修改操作（Webhook 事件由系统自动记录和管理）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookEventController {

    private final WebhookService webhookService;

    /**
     * 分页查询 Webhook 事件列表（按创建时间倒序）
     *
     * @param page 页码（从 1 开始），默认 1
     * @param size 每页条数，默认 20
     * @return 事件分页结果
     */
    @GetMapping("/events")
    public ApiResult<List<WebhookEventVO>> listEvents(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResult.success(webhookService.listEvents(page, size));
    }
}
