# 实现计划：文档体系优化

**分支**：`011-docs-system-optimization` | **日期**：2026-07-02 | **规格**：[spec.md](./spec.md)

**输入**：来自 `/specs/011-docs-system-optimization/spec.md` 的功能规格

## 摘要

本功能为项目建立"根级入口 + docs/ 主题文档 + specs/ 冻结档案"三层文档体系。主要交付物：CHANGELOG.md、docs/ 下 6 个编号主题文档、docs/architecture/ 五大模块文档、docs/operations/ 运维文档，以及对 AGENTS.md 与 README.md 的精简。核心技术创新是 docs/05-API接口文档.md 采用 SpringDoc OpenAPI v3.0.3 通过代码注解自动生成（运行时 Swagger UI + 构建期静态导出 OpenAPI YAML → Markdown）。

## 技术上下文

**语言/版本**：Java 21（LTS，虚拟线程可用）

**主要依赖**：
- Spring Boot 4.1.0（Spring Framework 7.x）
- Spring Data JPA + H2（嵌入式）
- Spring Security Crypto（仅 BCrypt，无完整 Security 框架）
- JAVE2 3.5.0 + FFmpeg（转码）
- Lombok、Jakarta Validation、Jackson
- **新增**：SpringDoc OpenAPI v3.0.3（`springdoc-openapi-starter-webmvc-ui`，基于 Spring Boot 4.0.5，官方支持 4.x）
- **新增（可选）**：`springdoc-openapi-maven-plugin` 1.5（构建期静态导出 OpenAPI 文件）
- **新增（可选）**：`openapi-to-md` 或自研 Node 脚本（OpenAPI YAML → Markdown 转换）

**存储**：不适用（纯文档功能，不引入数据存储；SpringDoc 元数据存于代码注解）

**测试**：
- 文档构建验证：`mvn verify` 触发 SpringDoc 静态导出，校验 `target/openapi.json` 生成成功
- 注解覆盖率校验：自研脚本扫描所有 Controller/DTO，断言无 `@Tag`/`@Operation`/`@Schema` 缺失
- 文档链路验证：校验 docs/ 间相对链接与 specs/ 引用链接无死链

**目标平台**：Linux 生产（Docker）/ Windows 开发 / 一体化启动包（离线）

**项目类型**：web-service（Spring Boot 单体 + React 前端静态资源）

**性能目标**：不适用（文档功能无运行时性能指标；SpringDoc 仅在访问 `/v3/api-docs` 与 `/swagger-ui` 时反射扫描，不影响业务路径）

**约束**：
- 新增依赖须遵循章程原则 VI（YAGNI），在「复杂性追踪」记录
- SpringDoc 端点（`/v3/api-docs**`、`/swagger-ui**`）MUST 受 AuthInterceptor 保护或在生产环境禁用，避免未认证访问暴露 API 结构
- 文档生成命令 MUST 可在 CI 与本地重复执行，确保 docs/05 为派生产物、源真值在注解中

**规模/范围**：
- 新增文档约 15 份（6 主题 + 6 模块/交叉 + 2 运维 + CHANGELOG）
- 精简文档 2 份（AGENTS.md、README.md）
- 代码注解覆盖 7 个 Controller + 约 20 个 DTO
- 冻结不动：specs/001-010、md/ 全部文件

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

| 原则 | 合规状态 | 说明 |
|------|---------|------|
| I. 分层架构 | ✅ 通过 | 本功能不修改业务分层；SpringDoc 注解加在 Controller/DTO 层，不引入跨层调用 |
| II. 数据完整性 | ✅ 不适用 | 纯文档功能，无实体写操作 |
| III. RESTful API 契约 | ✅ 通过 | SpringDoc 生成的文档反向强化 API 契约可见性；`ApiResult<T>` 统一响应结构将通过 `@Schema` 注解体现 |
| IV. 中文优先 | ✅ 通过 | 所有新增文档使用简体中文；注解 `description` 使用中文；API 字段名与错误码保持英文 |
| V. 测试不可省略 | ⚠️ 需注意 | 本功能无 Service/Repository 代码变更，单元测试不适用；但须补充"文档生成可重复性"验证（见技术上下文·测试）与 Java 类变更同步测试检查（注解不改变行为，无需新增测试） |
| VI. 简洁至上（YAGNI） | ⚠️ 需证明 | 新增 SpringDoc 依赖 + 可选 Maven 插件 + 可选 MD 转换工具，须在「复杂性追踪」记录合理性 |
| VII. 日志规范 | ✅ 不适用 | 文档功能不涉及业务日志；SpringDoc 自身日志不纳入项目日志规范 |
| VIII. 规格状态同步 | ✅ 通过 | 规格状态已按流转更新（草案 → 已澄清 → 已计划） |
| IX. 实现后文档同步 | ✅ 通过 | 本功能本身就是文档同步机制；README.md 精简后仍作为实现收尾同步点 |
| X. 章程与 AGENTS.md 同步 | ✅ 通过 | AGENTS.md 精简属于本功能范围；精简后须保持与章程一致 |

## 项目结构

### 文档（本功能）

```text
specs/011-docs-system-optimization/
├── spec.md              # 功能规格（已完成）
├── plan.md              # 本文件（实现计划）
├── research.md          # 阶段 0 输出（SpringDoc 集成研究）
├── data-model.md        # 阶段 1 输出（文档元数据模型）
├── quickstart.md        # 阶段 1 输出（验证指南）
├── contracts/           # 阶段 1 输出（文档引用契约 + API 文档生成契约）
│   ├── doc-reference-contract.md
│   └── api-doc-generation-contract.md
└── tasks.md             # 阶段 2 输出（/speckit-tasks 命令创建）
```

### 源代码（仓库根目录）

```text
# 选项 2：Web 应用（后端为主，前端为静态资源）— 文档体系增量
.
├── README.md                    # [精简] 仅快速开始 + 导航（约 80 行）
├── CHANGELOG.md                 # [新增] Keep a Changelog 格式
├── AGENTS.md                    # [精简] 仅 AI 协作指令 + 章程引用（约 100 行）
├── CLAUDE.md                    # [保持] 一行重定向
│
├── docs/                        # [新增] 人类开发者文档主目录
│   ├── 01-项目概述.md
│   ├── 02-开发环境搭建.md
│   ├── 03-架构设计.md
│   ├── 04-配置说明.md            # SSOT：app.* 配置项与环境变量映射
│   ├── 05-API接口文档.md         # 派生产物：由 SpringDoc 注解生成
│   ├── 06-运维部署.md
│   ├── architecture/
│   │   ├── 模块-common.md
│   │   ├── 模块-storage.md
│   │   ├── 模块-sync.md
│   │   ├── 模块-transcode.md
│   │   ├── 模块-webhook.md
│   │   └── 交叉关注点.md
│   └── operations/
│       ├── 环境变量清单.md        # SSOT：全量环境变量
│       └── 故障排查与日志.md
│
├── src/main/java/top/lldwb/alistmediasync/
│   ├── common/config/
│   │   └── OpenApiConfig.java    # [新增] @OpenAPIDefinition + @SecurityScheme（HTTP Basic）
│   ├── common/interceptor/
│   │   └── AuthInterceptor.java  # [修改] 放行 /v3/api-docs** 与 /swagger-ui**（或生产禁用）
│   └── ...                       # [修改] 7 个 Controller + 约 20 个 DTO 添加 @Tag/@Operation/@Schema
│
├── src/main/resources/
│   └── application.yaml          # [修改] 新增 springdoc.* 配置段
│
├── pom.xml                       # [修改] 新增 springdoc-openapi-starter-webmvc-ui:3.0.3 + 可选插件
├── scripts/
│   └── gen-api-doc.sh/.bat       # [新增] OpenAPI YAML → Markdown 生成脚本
│
├── md/                           # [保持冻结] 外部对接系统 API
└── specs/001..010/               # [保持冻结] 历史功能设计档案
```

**结构决策**：采用选项 2（Web 应用）的增量变体。本功能不新建 src/ 子目录，文档产物集中在 `docs/`，代码改动是对现有 Controller/DTO 的注解增强 + 一个新配置类 `OpenApiConfig.java`。`docs/` 与 `specs/` 形成两层分离：docs/ 描述"系统当前状态"（同步态），specs/ 为"历史设计档案"（冻结态），通过相对链接互引。

## 复杂性追踪

> **章程检查有需证明合理性的违规（原则 VI YAGNI）：新增依赖须记录**

| 违规 | 为什么需要 | 被拒绝的更简单替代方案及原因 |
|------|-----------|------------------------|
| 新增 `springdoc-openapi-starter-webmvc-ui:3.0.3` | API 文档需与代码注解自动同步，避免手写漂移（SC-004 要求偏差为 0）。SpringDoc v3.0.3 是唯一活跃且官方支持 Spring Boot 4.x 的方案（research.md 已论证 SpringFox 已停维、Boot 4 无内置 OpenAPI 生成） | **(a) 纯手写 docs/05**：违反 SC-004，文档与实现必然漂移，长期维护成本高于引入依赖。**(b) 自研 Javadoc 解析脚本**：需自研解析器、无 Swagger UI 交互预览、无 OpenAPI 标准输出，违反 YAGNI（造轮子）。**(c) 半自动注解 + 人工校对**：仍需引入注解库依赖，且无法消除人工校对成本，自动化收益打折 |
| 新增 `OpenApiConfig.java` 配置类 | 集中声明 `@OpenAPIDefinition`（标题/版本/描述）与 `@SecurityScheme`（HTTP Basic），SpringDoc 官方推荐在 Spring bean 上声明以提升生成性能 | 直接在主启动类上堆注解：会污染主类职责，违反 SRP；且主类已有 `@SpringBootApplication`，注解耦合度高 |
| 可选：`springdoc-openapi-maven-plugin:1.5` | 实现构建期静态导出 OpenAPI JSON/YAML，支撑 docs/05 的 Markdown 生成与 CI 可重复性 | 仅依赖运行时端点手动下载：无法纳入 CI，违反"生成命令可重复执行"约束；开发人员需手动启动应用并抓取，易遗漏 |
| 可选：`openapi-to-md` 或自研 Node 脚本 | 将 OpenAPI YAML 转换为 docs/05 的 Markdown，使文档可读且可纳入版本控制 | 手写 Markdown 对齐 OpenAPI：等同于纯手写方案，违反 SC-004。自研脚本比引入 `openapi-to-md` 更符合 YAGNI，但需评估脚本复杂度（见 research.md） |
| AuthInterceptor 放行 SpringDoc 端点 | 开发环境需访问 Swagger UI 预览；生产环境应通过 `springdoc.api-docs.enabled=false` 或保持认证保护 | 全局放行：暴露 API 结构给未认证用户，安全风险。维持现有认证保护：开发预览不便，但可通过配置环境差异化处理 |

## 实现阶段

> **本计划仅覆盖设计阶段（阶段 0 研究、阶段 1 契约）。任务分解由 `/speckit-tasks` 生成。**

### 阶段 0：research.md（已完成）

- SpringDoc v3.0.3 依赖坐标、集成方式、注解包路径、生成端点、静态导出插件、OpenAPI→MD 转换工具、与 Spring Boot 4.1.0 兼容性论证

### 阶段 1：设计制品（已完成）

- `data-model.md`：文档元数据模型（无业务实体）
- `contracts/doc-reference-contract.md`：文档间引用与 SSOT 契约
- `contracts/api-doc-generation-contract.md`：API 文档生成流水线契约
- `quickstart.md`：文档体系验证指南

### 阶段 2：任务分解（待 /speckit-tasks 执行）

预期任务分组：
1. **依赖与配置**：pom.xml 新增 SpringDoc、application.yaml 新增 springdoc.* 段、OpenApiConfig.java
2. **注解增强**：7 个 Controller + 约 20 个 DTO 添加注解、AuthInterceptor 放行调整
3. **生成流水线**：gen-api-doc 脚本、CI 集成、docs/05 派生产物生成
4. **根级文档**：CHANGELOG.md、README.md 精简、AGENTS.md 精简
5. **docs 主题文档**：01-06 六份
6. **docs/architecture**：五大模块 + 交叉关注点六份
7. **docs/operations**：环境变量清单 + 故障排查两份
8. **验证**：注解覆盖率校验、文档死链检查、冻结文件完整性校验
