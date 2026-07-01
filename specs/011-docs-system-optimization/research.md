# 研究：文档体系优化

**分支**：`011-docs-system-optimization` | **日期**：2026-07-02 | **关联**：[plan.md](./plan.md)

本文档记录 SpringDoc OpenAPI v3.0.3 集成方案的技术研究结论，解决 spec.md 中的技术选型问题。

## 研究任务 1：SpringDoc v3.0.3 的 Maven 依赖坐标与版本兼容性

### 决策

采用 `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3`。

### 理由

- v3.0.3（2026-04-11 发布）release notes 明确写明 "Upgrade Spring Boot to version 4.0.5"，即 v3.x 系列已官方支持 Spring Boot 4.x。
- 项目使用 Spring Boot 4.1.0，与 v3.0.3 构建基线 4.0.5 同属 4.x 系列（Spring Framework 7.x 同一大版本），预期无破坏性不兼容。
- v3.0.0（2025-11-20）即已升级到 Spring Boot 4.0.0，v3.x 针对 Boot 4.x 重新编译，历史 v2.x 与 Boot 3.4.x 的 `NoSuchMethodError` 问题不再影响。
- 底层依赖：swagger-core-jakarta 2.2.47、swagger-ui 5.32.2。

### 考虑的替代方案

- **SpringDoc v2.x**：官方 README 仅声明支持 Spring Boot 3.x，未覆盖 4.x，存在不确定风险，已排除。
- **SpringFox 3.0.0**：最后版本 2020 年发布，已停止维护，不支持 Boot 3.x/4.x，且基于过时的 OpenAPI 2 规范，已排除。
- **swagger-core 手动集成**：SpringDoc 的底层依赖，需自行编写大量配置，相当于造轮子，违反 YAGNI，已排除。
- **Spring Boot 4 内置 OpenAPI 生成**：经查证，Boot 4.1 仅内置 API versioning，无 OpenAPI 文档生成能力，不存在此选项。
- **纯 Javadoc 解析脚本**：无运行时 UI、无 OpenAPI 标准输出、需自研解析器，维护成本高，已排除。

**来源**：
- https://github.com/springdoc/springdoc-openapi/releases/tag/v3.0.3
- https://github.com/springdoc/springdoc-openapi/releases/tag/v3.0.0
- https://springdoc.org/

## 研究任务 2：SpringDoc v3.0.3 的集成方式

### 决策

添加依赖后无需 `@Configuration` 类即可自动启用（Spring Boot auto-configuration）。但为集中声明 API 元信息与安全方案，新增一个 `OpenApiConfig.java` 配置类，用 `@OpenAPIDefinition` + `@SecurityScheme` 注解。

### 理由

- SpringDoc 官方文档："No additional configuration is needed"，自动配置满足基本需求。
- `@OpenAPIDefinition` 与 `@SecurityScheme` 官方推荐声明在 Spring managed bean 上，以提升文档生成性能。
- 集中配置类避免注解散落在主启动类（污染 `@SpringBootApplication` 职责，违反 SRP）。

### application.yaml 配置项（新增 springdoc.* 段）

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true                      # 生产环境可通过环境变量设为 false
  swagger-ui:
    path: /swagger-ui.html
    enabled: true                      # 生产环境可通过环境变量设为 false
  packages-to-scan: top.lldwb.alistmediasync
  show-actuator: false
```

### 与 AuthInterceptor 共存

项目使用 HTTP Basic Auth（无 Spring Security），无需 `SecurityFilterChain`。需在 `AuthInterceptor` 中对 `/v3/api-docs**`、`/swagger-ui**` 路径做环境差异化处理：
- 开发环境：放行（便于预览）
- 生产环境：保持认证保护，或通过 `springdoc.api-docs.enabled=false` / `springdoc.swagger-ui.enabled=false` 禁用

**来源**：https://springdoc.org/ 、https://github.com/springdoc/springdoc-openapi

## 研究任务 3：注解使用与包路径

### 决策

所有注解来自 `io.swagger.v3.oas.annotations.*` 包（swagger-core-jakarta）。

### 注解清单与用法

| 注解 | 包路径 | 作用层 | 用法 |
|------|--------|--------|------|
| `@Tag` | `io.swagger.v3.oas.annotations.tags.Tag` | Controller 类 | `@Tag(name="同步任务", description="同步任务的创建、查询与触发")` |
| `@Operation` | `io.swagger.v3.oas.annotations.Operation` | Controller 方法 | `@Operation(summary="创建同步任务", operationId="createSyncTask")` |
| `@Parameter` | `io.swagger.v3.oas.annotations.Parameter` | 方法参数 | `@Parameter(description="任务ID", required=true)` |
| `@Schema` | `io.swagger.v3.oas.annotations.media.Schema` | DTO 类/字段 | `@Schema(description="同步任务创建 DTO")` / `@Schema(description="任务ID", example="1")` |
| `@ApiResponse` | `io.swagger.v3.oas.annotations.responses.ApiResponse` | 方法 | `@ApiResponse(responseCode="200", description="操作成功")` |
| `@OpenAPIDefinition` | `io.swagger.v3.oas.annotations.OpenAPIDefinition` | 全局（配置类） | 定义标题、版本、描述 |
| `@SecurityScheme` | `io.swagger.v3.oas.annotations.security.SecurityScheme` | 全局（配置类） | `@SecurityScheme(type=SecuritySchemeType.HTTP, scheme="basic")` |

### 中文优先原则（章程原则 IV）

- 注解 `name`/`summary`/`description` 使用简体中文（如"同步任务"、"创建同步任务"）
- `operationId` 使用英文（如 `createSyncTask`），保持与 API 字段命名英文惯例一致
- `@Schema(example=...)` 示例值使用实际数据格式

**来源**：https://springdoc.org/

## 研究任务 4：生成命令与输出位置

### 决策

采用"运行时端点 + 构建期静态导出 + Markdown 转换"三段式流水线。

### 运行时端点（应用启动后自动可用）

| 端点 | 输出格式 | 用途 |
|------|---------|------|
| `/v3/api-docs` | OpenAPI JSON | 机器可读 |
| `/v3/api-docs.yaml` | OpenAPI YAML | 机器可读，作为静态导出源 |
| `/swagger-ui.html`（或 `/swagger-ui/index.html`） | Swagger UI HTML | 开发者交互预览 |

### 构建期静态导出

使用 `springdoc-openapi-maven-plugin:1.5`，在 `integration-test` 阶段配合 `spring-boot-maven-plugin` 的 start/stop goal 启动应用并抓取 OpenAPI 文件：

```xml
<plugin>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-maven-plugin</artifactId>
    <version>1.5</version>
    <executions>
        <execution>
            <id>integration-test</id>
            <goals><goal>generate</goal></goals>
        </execution>
    </executions>
</plugin>
```

- 执行：`mvn verify -Pgen-api-doc`（通过 profile 隔离，避免污染常规构建）
- 输出：`target/openapi.json` 与 `target/openapi.yaml`

### OpenAPI → Markdown 转换

| 方案 | 优劣 | 建议 |
|------|------|------|
| `openapi-to-md`（npm/pip CLI，零依赖） | 成熟、即装即用 | **优先**，若输出格式可接受则采用 |
| `openapi-markdown`（Python pip） | Redoc 风格 Markdown | 备选 |
| 自研 Node 脚本 | 完全可控，但需维护 | 仅当前两者输出不符合 docs/05 风格要求时采用 |

转换命令封装为 `scripts/gen-api-doc.sh` 与 `scripts/gen-api-doc.bat`：
1. `mvn verify -Pgen-api-doc` 生成 `target/openapi.yaml`
2. 调用转换工具生成 `docs/05-API接口文档.md`
3. 校验生成结果非空

### 派生产物约定

- `docs/05-API接口文档.md` 为派生产物，**不手工编辑**（文件头标注"本文档由 scripts/gen-api-doc 自动生成，源真值在 Controller/DTO 注解中"）
- 源真值在 `src/main/java/.../controller/*.java` 与 `dto/*.java` 的注解中
- SC-004 验证：重新生成后 diff 为空即表示文档与实现一致

**来源**：https://springdoc.org/plugins.html 、https://pypi.org/project/openapi-to-md/

## 研究任务 5：与 Spring Boot 4.1.0 的兼容性验证策略

### 决策

在 plan 阶段以静态查证确认兼容性，在实现阶段以集成测试验证。

### 理由

- v3.0.3 基于 Spring Boot 4.0.5，项目用 4.1.0，属 4.x 系列内小版本差异。
- Spring Framework 7.x 在 Web MVC 层 API 稳定，SpringDoc 基于运行时反射，受小版本变动影响概率低。
- 已知历史问题（v2.x 与 Boot 3.4.x 的 `ControllerAdviceBean` `NoSuchMethodError`）已在 v3.x 重新编译时解决。

### 验证步骤（实现阶段执行）

1. `mvn dependency:tree` 确认无 Spring Framework 版本冲突
2. `mvn spring-boot:run` 启动应用，访问 `/v3/api-docs` 确认返回非空 OpenAPI JSON
3. 访问 `/swagger-ui.html` 确认 UI 正常渲染
4. 若出现方法签名不匹配，检查 SpringDoc 依赖的 Spring Framework 7.x API 变动，必要时升级到 SpringDoc 后续小版本

**来源**：https://github.com/springdoc/springdoc-openapi/releases/tag/v3.0.3

## 研究任务 6：文档目录结构与 SSOT 最佳实践

### 决策

采用"根级入口 + docs/ 主题文档 + specs/ 冻结档案"三层结构，配置项与环境变量各设单一权威来源。

### 理由

- **三层分离**避免 AGENTS.md 同时承担"AI 指令"与"架构总览"的混合职责（当前痛点）。
- **SSOT 原则**：`AppProperties.java`（代码层 SSOT）→ `application.yaml`（运行时默认值）→ `docs/04-配置说明.md`（人类可读 SSOT），三者通过约定保持一致，避免配置散落 4 处的当前问题。
- **冻结 vs 同步**：specs/ 作为"功能设计档案"冻结，docs/ 作为"系统当前状态"同步，二者通过链接互引，不互相覆盖。

### 配置项抽取方式

`docs/04-配置说明.md` 的映射表可通过解析 `AppProperties.java` 的 `@ConfigurationProperties` 字段与 `application.yaml` 的占位符自动抽取，减少手写误差。建议在实现阶段编写一次性脚本或直接手工对照（字段数 17 个，手工成本可接受）。

**来源**：项目章程原则 I/VI/IX、当前文档痛点评估（见对话上下文）

## 结论汇总

所有技术选型已确定，无残留"需要澄清"项。关键决策：

1. **注解库**：SpringDoc OpenAPI v3.0.3（官方支持 Spring Boot 4.x）
2. **集成方式**：自动配置 + `OpenApiConfig.java` 集中声明元信息
3. **注解包**：`io.swagger.v3.oas.annotations.*`
4. **生成流水线**：运行时端点 + `springdoc-openapi-maven-plugin:1.5` 静态导出 + `openapi-to-md` 转 Markdown
5. **派生产物**：docs/05 不手工编辑，源真值在注解中
6. **兼容性**：4.0.5 → 4.1.0 属系列内升级，预期兼容，实现阶段集成测试验证
