package top.lldwb.alistmediasync.common.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lldwb.alistmediasync.common.util.TraceContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * MCP 工具结果封装
 * <p>
 * 为 MCP 工具方法提供统一的结果构建：在 {@code TraceContext} 上下文中执行工具操作（FR-010），
 * 成功返回序列化 JSON（isError=false），失败返回统一错误结构（FR-015，{@code {code,message,data}}，isError=true），
 * 敏感字段由复用 VO 层保证脱敏（FR-011）。
 * </p>
 * <p>
 * 工具方法仅需声明业务调用，由本类统一处理 traceId 注入、异常捕获与错误码映射，避免各工具类重复样板。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
public class McpToolResult {

    private final JsonMapper objectMapper;

    public McpToolResult(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 在 TraceContext 上下文中执行工具操作并封装为 MCP 工具结果
     *
     * @param operation 工具名（写入 MDC operation 字段，FR-010）
     * @param action    业务调用（复用现有 Service 层，FR-009）
     * @return 统一封装的 CallToolResult
     */
    public McpSchema.CallToolResult run(String operation, Supplier<?> action) {
        Object[] result = { null };
        Throwable[] error = { null };
        TraceContext.runWith("mcp", operation, () -> {
            try {
                result[0] = action.get();
                // SC-003：每次工具调用 MUST 产生含 traceId/module/operation 的日志记录
                log.info("MCP 工具调用成功：{}", operation);
            } catch (Throwable t) {
                TraceContext.setErrorType(t.getClass().getSimpleName());
                log.error("MCP 工具调用失败：{} — {}", operation, t.getMessage(), t);
                error[0] = t;
            }
        });
        return error[0] != null ? error(error[0]) : ok(result[0]);
    }

    /**
     * 构建成功结果（isError=false），返回对象序列化为 JSON 文本
     */
    public McpSchema.CallToolResult ok(Object result) {
        String json;
        try {
            json = result == null ? "{}" : objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("MCP 工具结果序列化失败", e);
            return error(500, "结果序列化失败");
        }
        return McpSchema.CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent(json)))
            .isError(false)
            .build();
    }

    /**
     * 根据异常类型映射业务错误码并构建错误结果（FR-015）
     */
    public McpSchema.CallToolResult error(Throwable t) {
        int code = resolveCode(t);
        return error(code, t.getMessage() != null ? t.getMessage() : "未知错误");
    }

    /**
     * 构建指定业务码与中文消息的错误结果（FR-015）
     */
    public McpSchema.CallToolResult error(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            json = "{\"code\":500,\"message\":\"错误信息序列化失败\",\"data\":null}";
        }
        return McpSchema.CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent(json)))
            .isError(true)
            .build();
    }

    /**
     * 业务异常 → 错误码映射（与 mcp-tools-contract.md 第 4 节对齐）
     */
    private int resolveCode(Throwable t) {
        if (t instanceof NoSuchElementException) {
            return 404;
        }
        if (t instanceof IllegalArgumentException) {
            return 400;
        }
        if (t instanceof IllegalStateException) {
            return 409;
        }
        return 500;
    }
}
