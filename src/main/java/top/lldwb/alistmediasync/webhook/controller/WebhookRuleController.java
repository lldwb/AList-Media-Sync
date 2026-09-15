package top.lldwb.alistmediasync.webhook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.lldwb.alistmediasync.common.dto.ApiResult;
import top.lldwb.alistmediasync.webhook.dto.WebhookRuleCreateDTO;
import top.lldwb.alistmediasync.webhook.dto.WebhookRuleVO;
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
@Tag(name = "Webhook 规则", description = "Webhook 规则的 CRUD 和启用/禁用")
public class WebhookRuleController {

    private final WebhookRuleService service;

    @PostMapping
    @Operation(summary = "创建 Webhook 规则", operationId = "create", description = "根据传入的规则配置创建一个新的 Webhook 规则")
    @ApiResponse(responseCode = "200", description = "创建成功，返回新建规则详情")
    public ApiResult<WebhookRuleVO> create(@Valid @RequestBody WebhookRuleCreateDTO dto) {
        return ApiResult.success(service.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 Webhook 规则", operationId = "update", description = "根据 ID 更新指定 Webhook 规则的配置信息")
    @ApiResponse(responseCode = "200", description = "更新成功，返回更新后的规则详情")
    public ApiResult<WebhookRuleVO> update(@PathVariable Long id, @RequestBody WebhookRuleCreateDTO dto) {
        return ApiResult.success(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 Webhook 规则", operationId = "delete", description = "根据 ID 删除指定的 Webhook 规则")
    @ApiResponse(responseCode = "200", description = "删除成功")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.success();
    }

    @GetMapping
    @Operation(summary = "查询所有 Webhook 规则", operationId = "listAll", description = "返回系统中所有 Webhook 规则列表")
    @ApiResponse(responseCode = "200", description = "查询成功，返回规则列表")
    public ApiResult<List<WebhookRuleVO>> listAll() {
        return ApiResult.success(service.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个 Webhook 规则", operationId = "getById", description = "根据 ID 返回指定 Webhook 规则的详细信息")
    @ApiResponse(responseCode = "200", description = "查询成功，返回规则详情")
    public ApiResult<WebhookRuleVO> getById(@PathVariable Long id) {
        return ApiResult.success(service.getById(id));
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用 Webhook 规则", operationId = "enable", description = "根据 ID 启用指定的 Webhook 规则")
    @ApiResponse(responseCode = "200", description = "启用成功，返回更新后的规则详情")
    public ApiResult<WebhookRuleVO> enable(@PathVariable Long id) {
        return ApiResult.success(service.enable(id));
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用 Webhook 规则", operationId = "disable", description = "根据 ID 禁用指定的 Webhook 规则")
    @ApiResponse(responseCode = "200", description = "禁用成功，返回更新后的规则详情")
    public ApiResult<WebhookRuleVO> disable(@PathVariable Long id) {
        return ApiResult.success(service.disable(id));
    }
}
