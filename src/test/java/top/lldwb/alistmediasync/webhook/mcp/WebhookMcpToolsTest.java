package top.lldwb.alistmediasync.webhook.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.ai.mcp.annotation.McpTool;
import top.lldwb.alistmediasync.common.mcp.McpToolResult;
import top.lldwb.alistmediasync.webhook.dto.WebhookRuleCreateDTO;
import top.lldwb.alistmediasync.webhook.entity.WebhookRule;
import top.lldwb.alistmediasync.webhook.service.WebhookRuleService;
import top.lldwb.alistmediasync.webhook.service.WebhookService;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Webhook MCP 工具单元测试
 * <p>
 * 覆盖 8 个工具的参数映射、枚举转换、分页默认值、错误处理（FR-015）、
 * 无事件注入工具边界（FR-006）与 traceId/module/operation 注入（FR-010）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("Webhook MCP 工具测试")
class WebhookMcpToolsTest {

    private WebhookRuleService ruleService;
    private WebhookService webhookService;
    private WebhookMcpTools tools;

    @BeforeEach
    void setUp() {
        ruleService = mock(WebhookRuleService.class);
        webhookService = mock(WebhookService.class);
        tools = new WebhookMcpTools(ruleService, webhookService, new McpToolResult(new JsonMapper()));
    }

    private String textOf(McpSchema.CallToolResult r) {
        return ((McpSchema.TextContent) r.content().get(0)).text();
    }

    @Test
    @DisplayName("webhook_rule_list 成功返回规则列表")
    void shouldListRules() {
        when(ruleService.listAll()).thenReturn(List.of());
        McpSchema.CallToolResult r = tools.webhookRuleList();
        assertFalse(r.isError());
        verify(ruleService).listAll();
    }

    @Test
    @DisplayName("webhook_rule_get 不存在的 ID 应返回统一错误结构（404）")
    void shouldReturn404WhenRuleNotFound() {
        when(ruleService.getById(99L))
            .thenThrow(new NoSuchElementException("Webhook 规则不存在：id=99"));
        McpSchema.CallToolResult r = tools.webhookRuleGet(99L);
        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":404"));
    }

    @Test
    @DisplayName("webhook_rule_create 应将参数映射到 DTO 并转换枚举")
    void shouldMapCreateParams() {
        when(ruleService.create(any(WebhookRuleCreateDTO.class))).thenReturn(null);

        tools.webhookRuleCreate("自动同步录播", "FILE_CLOSED", 12345678L, "SYNC_ONLY",
            1L, "/recordings", 2L, "/media/recordings");

        ArgumentCaptor<WebhookRuleCreateDTO> captor = ArgumentCaptor.forClass(WebhookRuleCreateDTO.class);
        verify(ruleService).create(captor.capture());
        WebhookRuleCreateDTO dto = captor.getValue();
        assertEquals("自动同步录播", dto.getName());
        assertEquals(WebhookRule.WebhookEventType.FILE_CLOSED, dto.getTriggerEventType());
        assertEquals(12345678L, dto.getRoomIdFilter());
        assertEquals(WebhookRule.RuleAction.SYNC_ONLY, dto.getAction());
        assertEquals("/recordings", dto.getRecordingPath());
        assertEquals(2L, dto.getTargetEngineId());
    }

    @Test
    @DisplayName("webhook_rule_create 未指定 action 时应使用默认 BOTH")
    void shouldDefaultActionToBoth() {
        when(ruleService.create(any(WebhookRuleCreateDTO.class))).thenReturn(null);

        tools.webhookRuleCreate("规则", "FILE_OPENED", null, null, null, null, null, null);

        ArgumentCaptor<WebhookRuleCreateDTO> captor = ArgumentCaptor.forClass(WebhookRuleCreateDTO.class);
        verify(ruleService).create(captor.capture());
        assertEquals(WebhookRule.RuleAction.BOTH, captor.getValue().getAction());
    }

    @Test
    @DisplayName("webhook_rule_create 非法事件类型应返回统一错误结构（400）")
    void shouldReturn400ForInvalidEventType() {
        when(ruleService.create(any(WebhookRuleCreateDTO.class)))
            .thenThrow(new IllegalArgumentException("没有枚举常量 UNKNOWN_EVENT"));

        McpSchema.CallToolResult r = tools.webhookRuleCreate("规则", "UNKNOWN_EVENT",
            null, null, null, null, null, null);

        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":400"));
    }

    @Test
    @DisplayName("webhook_rule_update 应将字段映射到 DTO")
    void shouldMapUpdateParams() {
        when(ruleService.update(any(Long.class), any(WebhookRuleCreateDTO.class))).thenReturn(null);

        tools.webhookRuleUpdate(1L, "新规则名", "SESSION_ENDED", null, "BOTH",
            null, null, null, "/new/path");

        ArgumentCaptor<WebhookRuleCreateDTO> captor = ArgumentCaptor.forClass(WebhookRuleCreateDTO.class);
        verify(ruleService).update(eq(1L), captor.capture());
        WebhookRuleCreateDTO dto = captor.getValue();
        assertEquals("新规则名", dto.getName());
        assertEquals(WebhookRule.WebhookEventType.SESSION_ENDED, dto.getTriggerEventType());
        assertEquals(WebhookRule.RuleAction.BOTH, dto.getAction());
        assertEquals("/new/path", dto.getTargetFilePath());
    }

    @Test
    @DisplayName("webhook_rule_delete 返回 {deleted:true}")
    void shouldDeleteRule() {
        McpSchema.CallToolResult r = tools.webhookRuleDelete(1L);
        assertFalse(r.isError());
        assertTrue(textOf(r).contains("\"deleted\":true"));
        verify(ruleService).delete(1L);
    }

    @Test
    @DisplayName("webhook_rule_enable 与 disable 应切换规则状态")
    void shouldEnableAndDisable() {
        when(ruleService.enable(1L)).thenReturn(null);
        when(ruleService.disable(1L)).thenReturn(null);
        assertFalse(tools.webhookRuleEnable(1L).isError());
        assertFalse(tools.webhookRuleDisable(1L).isError());
        verify(ruleService).enable(1L);
        verify(ruleService).disable(1L);
    }

    @Test
    @DisplayName("webhook_event_list 应使用分页默认值 page=1 size=20")
    void shouldDefaultPagination() {
        when(webhookService.listEvents(anyInt(), anyInt())).thenReturn(List.of());
        McpSchema.CallToolResult r = tools.webhookEventList(null, null);
        assertFalse(r.isError());
        verify(webhookService).listEvents(1, 20);
    }

    @Test
    @DisplayName("webhook_event_list 应透传自定义分页参数")
    void shouldPassCustomPagination() {
        when(webhookService.listEvents(anyInt(), anyInt())).thenReturn(List.of());
        tools.webhookEventList(3, 50);
        verify(webhookService).listEvents(3, 50);
    }

    @Test
    @DisplayName("工具集 MUST NOT 提供事件注入/模拟发送工具（FR-006）")
    void shouldNotExposeEventInjectionTools() {
        List<String> toolNames = Arrays.stream(WebhookMcpTools.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(McpTool.class))
            .map(m -> m.getAnnotation(McpTool.class).name())
            .toList();

        assertTrue(toolNames.stream().noneMatch(name -> name.contains("inject")),
            "不应包含事件注入工具");
        assertTrue(toolNames.stream().noneMatch(name -> name.contains("simulate")),
            "不应包含事件模拟发送工具");
        assertTrue(toolNames.stream().noneMatch(name -> name.contains("send_event")),
            "不应包含事件发送工具");
    }

    @Test
    @DisplayName("工具调用应在 MDC 注入 module=mcp 与 operation=工具名（FR-010）")
    void shouldInjectTraceContext() {
        when(ruleService.listAll()).thenAnswer(invocation -> {
            assertEquals("mcp", MDC.get("module"));
            assertEquals("webhook_rule_list", MDC.get("operation"));
            assertNotNull(MDC.get("traceId"));
            return List.of();
        });

        tools.webhookRuleList();

        assertNull(MDC.get("module"));
        assertNull(MDC.get("operation"));
    }
}
