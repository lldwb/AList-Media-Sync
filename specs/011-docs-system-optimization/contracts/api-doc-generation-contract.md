# 契约：API 文档生成流水线

**分支**：`011-docs-system-optimization` | **日期**：2026-07-02 | **关联**：[plan.md](./plan.md)

本契约定义 `docs/05-API接口文档.md` 的自动生成流水线，确保文档与代码注解保持同步（SC-004）。

## 1. 流水线总览

```text
[Controller/DTO 注解] → [Spring Boot 启动] → [/v3/api-docs.yaml] → [静态导出] → [target/openapi.yaml] → [MD 转换] → [docs/05-API接口文档.md]
```

### 阶段划分

| 阶段 | 触发命令 | 输入 | 输出 | 责任工具 |
|------|---------|------|------|---------|
| 1. 编译注解 | `mvn compile` | Controller/DTO 源码 | class 文件（含注解元数据） | maven-compiler-plugin |
| 2. 启动应用 | `mvn spring-boot:start` | class 文件 + SpringDoc 自动配置 | 运行中的 Spring Boot 实例 | spring-boot-maven-plugin |
| 3. 抓取 OpenAPI | `springdoc-openapi-maven-plugin:generate` | 运行时 `/v3/api-docs.yaml` | `target/openapi.yaml` + `target/openapi.json` | springdoc-openapi-maven-plugin 1.5 |
| 4. 停止应用 | `mvn spring-boot:stop` | — | — | spring-boot-maven-plugin |
| 5. 转 Markdown | `scripts/gen-api-doc.sh/.bat` | `target/openapi.yaml` | `docs/05-API接口文档.md` | openapi-to-md 或自研脚本 |

## 2. 源真值约定（输入契约）

### 2.1 Controller 注解要求

每个 Controller 类 MUST 声明：

```java
@Tag(name = "同步任务", description = "同步任务的创建、查询与触发")
@RestController
@RequestMapping("/api/sync-tasks")
public class SyncTaskController { ... }
```

每个 Controller 方法 SHOULD 声明：

```java
@Operation(summary = "创建同步任务", operationId = "createSyncTask", description = "根据请求体创建一个新的同步任务")
@ApiResponse(responseCode = "200", description = "创建成功，返回任务详情")
@PostMapping
public ApiResult<SyncTaskVO> create(@RequestBody @Valid SyncTaskCreateDTO dto) { ... }
```

### 2.2 DTO 注解要求

每个 DTO 类与字段 SHOULD 声明：

```java
@Schema(description = "同步任务创建请求")
public class SyncTaskCreateDTO {
    @Schema(description = "任务名称", example = "每日同步", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String name;
}
```

### 2.3 全局元信息（OpenApiConfig.java）

```java
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
public class OpenApiConfig { }
```

## 3. 生成命令契约（输出契约）

### 3.1 命令封装

`scripts/gen-api-doc.sh`（Linux/macOS）与 `scripts/gen-api-doc.bat`（Windows）封装阶段 1-5，对外暴露单一命令：

```bash
./scripts/gen-api-doc.sh
# 或 Windows
scripts\gen-api-doc.bat
```

### 3.2 命令行为契约

- **C-001**：命令 MUST 是幂等的——连续执行两次，`docs/05-API接口文档.md` 中**生成区**（见 §3.4 分界标记）的内容（除"最后生成时间"外）diff 为空。手工引导区不在 diff 校验范围内。
- **C-002**：命令 MUST 在无网络环境下可执行（除首次安装 `openapi-to-md` 外），依赖均通过 Maven/npm 本地缓存
- **C-003**：命令执行失败时 MUST 以非零退出码终止，并输出明确错误信息到 stderr
- **C-004**：命令 MUST 在 `docs/05-API接口文档.md` 生成区文件头写入派生产物声明与生成时间
- **C-005**：命令 MUST 校验 `target/openapi.yaml` 非空后才执行转换，否则报错
- **C-006**：命令 MUST 仅重写生成区（`<!-- GENERATED START -->` 与 `<!-- GENERATED END -->` 之间）内容，MUST NOT 触碰手工引导区，以保护 T021 手工维护的认证/统一响应/WebSocket/错误码章节

### 3.3 Maven Profile 隔离

为避免静态导出污染常规构建，MUST 通过 `gen-api-doc` profile 隔离：

```bash
mvn verify -Pgen-api-doc        # 仅触发阶段 1-4
./scripts/gen-api-doc.sh        # 在上一步基础上执行阶段 5
```

- 常规 `mvn verify` / `mvn package` MUST NOT 触发 SpringDoc 静态导出
- CI 可在文档更新检查任务中调用完整流水线

### 3.4 手工引导区与生成区分界标记

`docs/05-API接口文档.md` 采用"手工引导区 + 生成区"双区结构，以两个固定 HTML 注释锚点分界。生成脚本 MUST 仅重写生成区，手工引导区由 T021 手工维护、永不被脚本覆盖。

**文件结构模板**：

```markdown
<!-- 派生产物声明：本文件手工引导区由人工维护，生成区由 scripts/gen-api-doc 自动生成 -->
# API 接口文档

（手工引导章节：认证方式、统一响应格式、WebSocket 端点、错误码清单、Swagger UI 访问方式）
（此区域由 T021 维护，生成脚本 MUST NOT 触碰）

<!-- GENERATED START -->
> 本区块由 `scripts/gen-api-doc` 自动生成，请勿手工编辑。
> 源真值位于 `src/main/java/top/lldwb/alistmediasync/**/controller/*.java`
> 与 `**/dto/*.java` 的 OpenAPI 注解中。
> 最后生成时间：[由脚本填充]

（自动生成的端点清单，按 @Tag 分组）

<!-- GENERATED END -->
```

**规则**：

- **R-Gen-001**：生成脚本 MUST 通过定位 `<!-- GENERATED START -->` 与 `<!-- GENERATED END -->` 锚点，仅替换二者之间的内容；锚点本身 MUST 保留
- **R-Gen-002**：若文件中缺少锚点，脚本 MUST 报错并以非零退出码终止（防止首次生成时误覆盖手工引导区）
- **R-Gen-003**：首次创建 `docs/05-API接口文档.md` 时，手工引导区先由 T021 写入并包含锚点，再由 T011/T022 触发生成填充生成区
- **R-Gen-004**：SC-004 的"重新生成 diff 为空"约束仅适用于生成区；手工引导区的变更不纳入 SC-004 校验

## 4. 访问控制契约

### 4.1 运行时端点访问

- `/v3/api-docs**` 与 `/swagger-ui**` 的访问控制由 `AuthInterceptor` 与 `application.yaml` 共同决定
- 开发环境：`springdoc.api-docs.enabled=true` + `springdoc.swagger-ui.enabled=true`，AuthInterceptor 放行
- 生产环境：`springdoc.api-docs.enabled=false` + `springdoc.swagger-ui.enabled=false`（通过环境变量 `SPRINGDOC_API_DOCS_ENABLED` 等控制），或保持认证保护

### 4.2 静态导出访问

- 静态导出阶段（`springdoc-openapi-maven-plugin`）在本地启动应用实例，不受生产 AuthInterceptor 环境约束
- 导出过程 MUST NOT 修改生产环境配置

## 5. 一致性验证（SC-004）

### 验证流程

1. 执行 `./scripts/gen-api-doc.sh` 生成最新 `docs/05-API接口文档.md`
2. `git diff --exit-code docs/05-API接口文档.md`
3. 若 diff 仅出现在**生成区**（`<!-- GENERATED START -->` 与 `<!-- GENERATED END -->` 之间，且除生成时间戳外），表示文档与注解不一致：
   - 若 diff 来自注解变更未提交：提示提交注解变更后重新生成
   - 若 diff 来自生成区手工编辑：提示恢复，禁止手工编辑生成区
4. 手工引导区的 diff 不纳入 SC-004 校验（该区由人工维护）

### 注解覆盖率校验

- 自研脚本扫描 `src/main/java/**/controller/*.java` 与 `**/dto/*.java`
- 断言：每个 Controller 类有 `@Tag`，每个公开方法有 `@Operation`，每个 DTO 类有 `@Schema`
- 缺失时以非零退出码终止，输出缺失清单

## 6. 回退方案

若 `openapi-to-md` 输出不符合 docs/05 风格要求，或与 Spring Boot 4.1.0 集成出现兼容性问题：

1. **MD 转换回退**：改用自研 Node 脚本解析 `target/openapi.yaml`，按 docs/05 预定模板生成 Markdown
2. **兼容性回退**：若 SpringDoc v3.0.3 与 Boot 4.1.0 实测不兼容，降级为"注解标记 + 半自动生成"——注解保留，生成命令辅以人工校对，并在 docs/05 文件头标注"半自动生成"

回退决策 MUST 记录在 tasks.md 实现过程中，并更新本契约。
