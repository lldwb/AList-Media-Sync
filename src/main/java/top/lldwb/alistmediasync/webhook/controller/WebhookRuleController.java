package top.lldwb.alistmediasync.webhook.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.common.dto.ApiResult;
import top.lldwb.alistmediasync.webhook.dto.webhook.WebhookRuleCreateDTO;
import top.lldwb.alistmediasync.webhook.dto.webhook.WebhookRuleVO;
import top.lldwb.alistmediasync.webhook.service.WebhookRuleService;

import java.util.List;

/**
 * Webhook 规则管理 API
 *
 * @author AList-Media-Sync
 */
@RestController
@RequestMapping("/api/webhook-rules")
@RequiredArgsConstructor
public class WebhookRuleController {

    private final WebhookRuleService service;

    @PostMapping
    public ApiResult<WebhookRuleVO> create(@Valid @RequestBody WebhookRuleCreateDTO dto) {
        return ApiResult.success(service.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResult<WebhookRuleVO> update(@PathVariable Long id, @RequestBody WebhookRuleCreateDTO dto) {
        return ApiResult.success(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.success();
    }

    @GetMapping
    public ApiResult<List<WebhookRuleVO>> listAll() {
        return ApiResult.success(service.listAll());
    }

    @GetMapping("/{id}")
    public ApiResult<WebhookRuleVO> getById(@PathVariable Long id) {
        return ApiResult.success(service.getById(id));
    }

    @PostMapping("/{id}/enable")
    public ApiResult<WebhookRuleVO> enable(@PathVariable Long id) {
        return ApiResult.success(service.enable(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResult<WebhookRuleVO> disable(@PathVariable Long id) {
        return ApiResult.success(service.disable(id));
    }
}
