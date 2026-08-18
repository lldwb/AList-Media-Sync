package top.lldwb.alistmediasync.webhook.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import top.lldwb.alistmediasync.common.mcp.McpToolResult;
import top.lldwb.alistmediasync.webhook.dto.webhook.WebhookRuleCreateDTO;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule;
import top.lldwb.alistmediasync.webhook.service.WebhookRuleService;
import top.lldwb.alistmediasync.webhook.service.WebhookService;

import java.util.Map;

/**
 * Webhook 模块 MCP 工具（FR-006）
 * <p>
 * 提供 Webhook 规则的查询/创建/更新/删除/启用禁用与事件分页查询工具。
 * 工具层仅做参数适配与结果封装，业务逻辑复用 {@link WebhookRuleService} 与 {@link WebhookService}（FR-009）。
 * </p>
 * <p>
 * 范围边界：MUST NOT 提供事件注入/模拟发送工具，Webhook 事件仅由外部系统真实触发（FR-006）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Component
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class WebhookMcpTools {

    private final WebhookRuleService ruleService;
    private final WebhookService webhookService;
    private final McpToolResult result;

    public WebhookMcpTools(WebhookRuleService ruleService, WebhookService webhookService, McpToolResult result) {
        this.ruleService = ruleService;
        this.webhookService = webhookService;
        this.result = result;
    }

    @McpTool(name = "webhook_rule_list", description = "列出所有 Webhook 规则")
    public McpSchema.CallToolResult webhookRuleList() {
        return result.run("webhook_rule_list", ruleService::listAll);
    }

    @McpTool(name = "webhook_rule_get", description = "按 ID 查询 Webhook 规则详情")
    public McpSchema.CallToolResult webhookRuleGet(
            @McpToolParam(description = "Webhook 规则 ID") Long id) {
        return result.run("webhook_rule_get", () -> ruleService.getById(id));
    }

    @McpTool(name = "webhook_rule_create", description = "创建 Webhook 规则")
    public McpSchema.CallToolResult webhookRuleCreate(
            @McpToolParam(description = "规则名称") String name,
            @McpToolParam(description = "触发事件类型：FILE_OPENED / FILE_CLOSED / FILE_RENAMED / SESSION_STARTED / SESSION_ENDED / SPACE_FULL / OTHER") String triggerEventType,
            @McpToolParam(description = "房间号过滤（可选）", required = false) Long roomIdFilter,
            @McpToolParam(description = "触发后的操作：SYNC_ONLY / TRANSCODE_ONLY / BOTH（默认 BOTH）", required = false) String action,
            @McpToolParam(description = "录播存储引擎 ID（源端，TRANSCODE_ONLY/BOTH 时必填）", required = false) Long recordingEngineId,
            @McpToolParam(description = "录播文件路径（源端路径）", required = false) String recordingPath,
            @McpToolParam(description = "目标存储引擎 ID（SYNC_ONLY/BOTH 时必填）", required = false) Long targetEngineId,
            @McpToolParam(description = "目标文件路径（SYNC_ONLY/BOTH 时必填）", required = false) String targetFilePath) {
        return result.run("webhook_rule_create", () -> {
            WebhookRuleCreateDTO dto = new WebhookRuleCreateDTO();
            dto.setName(name);
            dto.setTriggerEventType(WebhookRule.WebhookEventType.valueOf(triggerEventType));
            dto.setRoomIdFilter(roomIdFilter);
            if (action != null) dto.setAction(WebhookRule.RuleAction.valueOf(action));
            dto.setRecordingEngineId(recordingEngineId);
            dto.setRecordingPath(recordingPath);
            dto.setTargetEngineId(targetEngineId);
            dto.setTargetFilePath(targetFilePath);
            return ruleService.create(dto);
        });
    }

    @McpTool(name = "webhook_rule_update", description = "更新 Webhook 规则（仅更新提供的字段）")
    public McpSchema.CallToolResult webhookRuleUpdate(
            @McpToolParam(description = "Webhook 规则 ID") Long id,
            @McpToolParam(description = "规则名称", required = false) String name,
            @McpToolParam(description = "触发事件类型", required = false) String triggerEventType,
            @McpToolParam(description = "房间号过滤", required = false) Long roomIdFilter,
            @McpToolParam(description = "触发后的操作：SYNC_ONLY / TRANSCODE_ONLY / BOTH", required = false) String action,
            @McpToolParam(description = "录播存储引擎 ID（源端）", required = false) Long recordingEngineId,
            @McpToolParam(description = "录播文件路径（源端路径）", required = false) String recordingPath,
            @McpToolParam(description = "目标存储引擎 ID", required = false) Long targetEngineId,
            @McpToolParam(description = "目标文件路径", required = false) String targetFilePath) {
        return result.run("webhook_rule_update", () -> {
            WebhookRuleCreateDTO dto = new WebhookRuleCreateDTO();
            dto.setName(name);
            if (triggerEventType != null) dto.setTriggerEventType(WebhookRule.WebhookEventType.valueOf(triggerEventType));
            dto.setRoomIdFilter(roomIdFilter);
            if (action != null) dto.setAction(WebhookRule.RuleAction.valueOf(action));
            dto.setRecordingEngineId(recordingEngineId);
            dto.setRecordingPath(recordingPath);
            dto.setTargetEngineId(targetEngineId);
            dto.setTargetFilePath(targetFilePath);
            return ruleService.update(id, dto);
        });
    }

    @McpTool(name = "webhook_rule_delete", description = "删除 Webhook 规则")
    public McpSchema.CallToolResult webhookRuleDelete(
            @McpToolParam(description = "Webhook 规则 ID") Long id) {
        return result.run("webhook_rule_delete", () -> {
            ruleService.delete(id);
            return Map.of("deleted", true);
        });
    }

    @McpTool(name = "webhook_rule_enable", description = "启用 Webhook 规则")
    public McpSchema.CallToolResult webhookRuleEnable(
            @McpToolParam(description = "Webhook 规则 ID") Long id) {
        return result.run("webhook_rule_enable", () -> ruleService.enable(id));
    }

    @McpTool(name = "webhook_rule_disable", description = "禁用 Webhook 规则")
    public McpSchema.CallToolResult webhookRuleDisable(
            @McpToolParam(description = "Webhook 规则 ID") Long id) {
        return result.run("webhook_rule_disable", () -> ruleService.disable(id));
    }

    @McpTool(name = "webhook_event_list", description = "分页查询已接收的 Webhook 事件记录")
    public McpSchema.CallToolResult webhookEventList(
            @McpToolParam(description = "页码（默认 1）", required = false) Integer page,
            @McpToolParam(description = "每页数量（默认 20）", required = false) Integer size) {
        return result.run("webhook_event_list",
            () -> webhookService.listEvents(page == null ? 1 : page, size == null ? 20 : size));
    }
}
