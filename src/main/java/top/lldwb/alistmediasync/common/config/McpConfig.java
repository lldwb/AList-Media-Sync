package top.lldwb.alistmediasync.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import top.lldwb.alistmediasync.common.util.TraceContext;

/**
 * MCP 服务器装配配置
 * <p>
 * 控制 MCP 服务器相关组件的装配（FR-012）：仅当 {@code app.mcp.enabled=true} 时本配置生效，
 * 并在启动时校验 {@code app.mcp.token} 非空——令牌缺失或为空时拒绝启用 MCP，防止接口裸奔。
 * </p>
 * <p>
 * MCP 端点（{@code /mcp}）、工具注解扫描等由 Spring AI 自动配置承担（见 application.yaml
 * {@code spring.ai.mcp.server.*}），本类仅负责总开关与令牌校验。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class McpConfig {

    private final AppProperties appProperties;

    public McpConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
        validateToken();
        TraceContext.runWith("mcp", "MCP 服务器启动", () -> {
            // 启动日志仅标注令牌"已配置"，不打印令牌值（原则 VII §7.6）
            log.info("MCP 服务器已启用：/mcp（访问令牌已配置）");
        });
    }

    /**
     * 启动令牌校验：enabled=true 且 token 为空/空白时拒绝启用（FR-012、R4）
     */
    private void validateToken() {
        String token = appProperties.getMcp().getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                "MCP 服务器已启用（app.mcp.enabled=true），但未配置访问令牌 app.mcp.token，请设置 MCP_TOKEN 环境变量后重启");
        }
    }
}
