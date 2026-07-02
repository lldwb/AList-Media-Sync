package top.lldwb.alistmediasync.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档全局配置
 * <p>
 * 集中声明 {@link OpenAPIDefinition}（API 元信息）与 {@link SecurityScheme}（HTTP Basic 认证方案），
 * 供 SpringDoc OpenAPI v3.0.3 在运行时反射生成 OpenAPI JSON/YAML 与 Swagger UI。
 * </p>
 * <p>
 * SpringDoc 官方推荐在 Spring managed bean 上声明上述注解以提升文档生成性能，
 * 避免注解散落在主启动类（污染 {@code @SpringBootApplication} 职责，遵循单一职责原则）。
 * </p>
 * <p>
 * 端点契约详见 {@code docs/05-API接口文档.md}（生成区由 {@code scripts/gen-api-doc} 自动产出）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "AList-Media-Sync API",
        version = "${app.version}",
        description = "AList 媒体同步与转码系统接口文档"
    )
)
@SecurityScheme(
    type = SecuritySchemeType.HTTP,
    scheme = "basic",
    name = "basicAuth"
)
public class OpenApiConfig {
}
