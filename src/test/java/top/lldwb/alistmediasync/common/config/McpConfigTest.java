package top.lldwb.alistmediasync.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 装配配置单元测试
 * <p>
 * 覆盖 {@code @ConditionalOnProperty} 三分支（FR-012）：
 * enabled=false 不装配；enabled=true + 令牌空 → 拒绝启用；enabled=true + 令牌非空 → 正常装配。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("MCP 装配配置测试")
class McpConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(McpConfig.class);

    @Test
    @DisplayName("app.mcp.enabled=false 时不应装配 McpConfig")
    void shouldNotAssembleWhenDisabled() {
        contextRunner
            .withBean(AppProperties.class, AppProperties::new)
            .withPropertyValues("app.mcp.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(McpConfig.class));
    }

    @Test
    @DisplayName("app.mcp.enabled=true 且令牌为空时应拒绝启用（启动失败）")
    void shouldFailWhenEnabledWithoutToken() {
        contextRunner
            .withBean(AppProperties.class, AppProperties::new)
            .withPropertyValues("app.mcp.enabled=true")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class);
            });
    }

    @Test
    @DisplayName("app.mcp.enabled=true 且令牌非空时应正常装配")
    void shouldAssembleWhenEnabledWithToken() {
        contextRunner
            .withBean(AppProperties.class, () -> {
                AppProperties props = new AppProperties();
                props.getMcp().setToken("secret-mcp-token");
                return props;
            })
            .withPropertyValues("app.mcp.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(McpConfig.class);
            });
    }

    @Test
    @DisplayName("app.mcp.enabled=true 且令牌为空白时应拒绝启用")
    void shouldFailWhenEnabledWithBlankToken() {
        contextRunner
            .withBean(AppProperties.class, () -> {
                AppProperties props = new AppProperties();
                props.getMcp().setToken("   ");
                return props;
            })
            .withPropertyValues("app.mcp.enabled=true")
            .run(context -> assertThat(context).hasFailed());
    }
}
