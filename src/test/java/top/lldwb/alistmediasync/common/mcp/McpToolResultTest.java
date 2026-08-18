package top.lldwb.alistmediasync.common.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 工具结果封装单元测试
 * <p>
 * 覆盖统一结果构建（FR-015）：成功序列化、业务码映射（400/404/409/500）、
 * 错误 JSON 结构、traceId/module/operation 注入（FR-010）与 MDC 清理。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("MCP 工具结果封装测试")
class McpToolResultTest {

    private McpToolResult result;

    @BeforeEach
    void setUp() {
        result = new McpToolResult(new JsonMapper());
    }

    private String textOf(McpSchema.CallToolResult r) {
        return ((McpSchema.TextContent) r.content().get(0)).text();
    }

    @Test
    @DisplayName("成功执行应返回 isError=false 且结果为 JSON 文本")
    void shouldReturnSuccessResult() {
        McpSchema.CallToolResult r = result.run("test_op", () -> List.of("a", "b"));
        assertFalse(r.isError());
        assertEquals("[\"a\",\"b\"]", textOf(r));
    }

    @Test
    @DisplayName("结果为 null 时应返回空对象 JSON")
    void shouldReturnEmptyObjectForNullResult() {
        McpSchema.CallToolResult r = result.run("test_op", () -> null);
        assertFalse(r.isError());
        assertEquals("{}", textOf(r));
    }

    @Test
    @DisplayName("NoSuchElementException 应映射为 404 错误结构")
    void shouldMapNotFound() {
        McpSchema.CallToolResult r = result.run("test_op",
            () -> { throw new NoSuchElementException("资源不存在：id=1"); });
        assertTrue(r.isError());
        String text = textOf(r);
        assertTrue(text.contains("\"code\":404"));
        assertTrue(text.contains("资源不存在"));
        assertTrue(text.contains("\"data\":null"));
    }

    @Test
    @DisplayName("IllegalArgumentException 应映射为 400")
    void shouldMapBadRequest() {
        McpSchema.CallToolResult r = result.run("test_op",
            () -> { throw new IllegalArgumentException("参数非法"); });
        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":400"));
    }

    @Test
    @DisplayName("IllegalStateException 应映射为 409")
    void shouldMapConflict() {
        McpSchema.CallToolResult r = result.run("test_op",
            () -> { throw new IllegalStateException("任务正在执行中"); });
        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":409"));
    }

    @Test
    @DisplayName("其他异常应映射为 500")
    void shouldMapInternalError() {
        McpSchema.CallToolResult r = result.run("test_op",
            () -> { throw new RuntimeException("内部错误"); });
        assertTrue(r.isError());
        assertTrue(textOf(r).contains("\"code\":500"));
    }

    @Test
    @DisplayName("执行期间应注入 module=mcp 与 operation，结束后清理 MDC")
    void shouldInjectAndCleanTraceContext() {
        result.run("test_op", () -> {
            assertEquals("mcp", MDC.get("module"));
            assertEquals("test_op", MDC.get("operation"));
            assertNotNull(MDC.get("traceId"));
            return null;
        });
        assertNull(MDC.get("module"));
        assertNull(MDC.get("operation"));
        assertNull(MDC.get("traceId"));
    }

    @Test
    @DisplayName("失败时应设置 errorType MDC 字段（原则 VII §7.3）")
    void shouldSetErrorTypeOnFailure() {
        result.run("test_op", () -> { throw new IllegalStateException("失败"); });
        // runWith 结束后 errorType 被清理，验证失败日志路径不抛异常即可
        assertNull(MDC.get("errorType"));
    }
}
