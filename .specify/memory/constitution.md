<!--
============================================================
同步影响报告
============================================================
版本变更：1.1.0 → 1.2.0（MINOR — 原则强化与工作流扩展）
修改原则：
  - V. 测试不可省略 — 新增"Java 类修改后必须同步测试"强制规则，提高覆盖率要求
新增部分：
  - 开发工作流 > 实现后文档同步（README.md 自动更新规则）
移除部分：无
模板同步状态：
  ✅ plan-template.md — 章程检查为占位符，无需修改
  ✅ spec-template.md — 需求结构通用，无需修改
  ✅ tasks-template.md — 任务分类通用，无需修改
  ✅ CLAUDE.md — SPECKIT 区块引用 plan.md 间接包含工作流，无需修改
  ✅ README.md — 作为文档同步规则的目标文件，无需模板级修改
延期 TODO：无
============================================================
-->
# AList-Media-Sync 项目章程

## 核心原则

### I. 分层架构（不可协商）

所有业务代码 MUST 遵循 Spring Boot 经典三层架构：

- **Controller 层**：仅负责 HTTP 请求处理、参数校验、响应封装，MUST NOT 包含业务逻辑
- **Service 层**：承载核心业务逻辑与事务管理，MUST 保持对 Controller 和 Repository 的双向解耦（依赖注入）
- **Repository 层**：仅负责数据持久化，MUST 使用 Spring Data JPA 接口定义，禁止自定义 SQL（除非经批准的性能优化）

**理由**：清晰的分层边界保障代码可维护性，使单元测试与 Mock 隔离成为可能。任何跨层调用（Controller 直调 Repository）都 MUST 在代码审查中被拒绝。

### II. 数据完整性优先

媒体同步的核心是数据一致性。以下规则不可协商：

- 所有实体 MUST 定义 `@Version` 字段用于乐观锁控制
- 所有写操作 MUST 在事务上下文中执行（`@Transactional`）
- 外部 API 调用（AList API 等）MUST 具备重试机制与幂等性保证
- 同步操作的中间状态 MUST 持久化，不得仅存于内存

**理由**：媒体文件同步涉及多系统数据交换，网络中断、API 限流等异常场景不可避免。数据完整性是用户体验与系统可信度的基石。

### III. RESTful API 契约优先

所有对外接口 MUST 遵循以下规范：

- URL 使用 RESTful 风格（资源名词复数形式，层级关系通过路径表达）
- 请求/响应使用 JSON 格式，MUST 定义明确的 DTO（禁止 Entity 直接暴露）
- 错误响应 MUST 使用统一的 `ApiResult<T>` 封装结构，包含 code、message、data 字段
- 所有 API 变更 MUST 保持向后兼容至少一个版本，破坏性变更需提前标记 `@Deprecated`

**理由**：API 是系统的对外契约。统一的响应格式与版本管理降低了前后端协作成本，也便于第三方集成。

### IV. 中文优先

项目内所有文档、注释、提交信息 MUST 使用简体中文：

- 代码注释 MUST 使用简体中文（Javadoc 使用 `@param`、`@return` 等标准标记，描述部分使用中文）
- 所有 Spec Kit 制品（spec.md、plan.md、tasks.md）MUST 使用简体中文
- Git 提交信息 MUST 使用中文，遵循 Conventional Commits 格式（`feat: 添加媒体文件扫描功能`）
- 对外 API 的字段命名与错误信息使用英文（遵循 Spring 惯例）

**理由**：项目维护团队以中文为母语，中文优先降低理解门槛，加速问题定位与代码审查。

### V. 测试不可省略

测试策略 MUST 遵循以下层级：

- **单元测试**：所有 Service 层公共方法 MUST 有对应的单元测试（Mock 依赖）
- **集成测试**：所有 Repository 方法和外部 API 客户端 MUST 有集成测试（使用 `@DataJpaTest` 或 WireMock）
- **API 测试**：所有 Controller 端点 MUST 有 `@WebMvcTest` 覆盖（含正常与异常场景）
- 测试覆盖率目标：Service 层 > 80%，Repository 层 > 60%，Controller 层 > 70%
- **测试同步规则（不可协商）**：每次修改 Java 类文件后，MUST 同步修改或新增对应的单元测试文件，确保测试覆盖所有新增和变更的业务逻辑。未同步更新测试的代码变更 MUST NOT 通过代码审查
- **覆盖率追求**：单元测试 MUST 尽可能实现高覆盖率，以最大化回归保障能力

**理由**：媒体同步系统的逻辑复杂且涉及外部依赖，自动化测试是保证回归质量、支持快速迭代的唯一可靠手段。测试与代码的同步更新是维持测试有效性的关键，滞后的测试等同于没有测试。

### VI. 简洁至上（YAGNI）

所有设计决策 MUST 遵循以下规则：

- 仅在明确需要时才引入新技术/依赖（YAGNI — You Aren't Gonna Need It）
- 任何新增的第三方依赖 MUST 在 plan.md 的「复杂性追踪」中记录并证明合理性
- 优先使用 Spring Boot 内置能力（如 RestClient 而非第三方 HTTP 库，Bean Validation 而非手写校验逻辑）而非引入额外框架
- 抽象仅在有第二个具体实现时引入（单一实现 = 不需要接口抽象）

**理由**：简单系统更容易理解、测试和维护。过度设计是技术债务的主要来源。

### VII. 实现后文档同步（不可协商）

每次执行完 `/speckit-implement` 后，MUST 自动更新项目根目录的 `README.md` 文件，确保文档与代码实现保持一致：

- 新增功能、配置项、API 端点、环境变量 MUST 同步反映到 README.md 对应章节
- 废弃或移除的功能 MUST 从 README.md 中移除或标注为已废弃
- README.md 的更新 MUST 作为实现阶段的收尾步骤，不得推迟到后续迭代

**理由**：过时的文档比没有文档更具危害性——它会误导使用者做出错误的配置决策。将文档同步绑定到实现流程，是确保文档始终可信的唯一可靠方式。

## 技术约束

以下技术选型为项目级约束，变更需要章程修订：

### 后端

- **语言**：Java 21（LTS，虚拟线程可用）
- **框架**：Spring Boot 4.1.0（Spring Framework 7.x）
- **构建**：Maven，pom.xml MUST 保持依赖版本统一管理
- **数据库**：H2（嵌入式，用于单实例部署）
- **持久化**：Spring Data JPA + Hibernate
- **HTTP 客户端**：RestClient（Spring Framework 7.x 内置，替代 RestTemplate）
- **转码引擎**：JAVE2（Java 封装的 FFmpeg，含 win64/linux64 原生二进制）
- **认证**：Spring Security Crypto（仅使用 BCrypt 密码哈希，不引入完整 Security 框架以遵循 YAGNI）
- **监控**：Spring Boot Actuator
- **工具**：Lombok（减少样板代码），Jakarta Validation（声明式校验）

### 前端

- **框架**：React 19 + ReactDOM 19
- **语言**：TypeScript 5.x（strict 模式）
- **构建工具**：Vite 6.x（Rollup 打包，开发 HMR < 500ms）
- **样式方案**：Tailwind CSS 4.x（Vite 插件集成，原子化 CSS）
- **路由**：React Router v7（Hash 模式，无需服务端 fallback 配置）
- **构建产出**：Vite 构建后作为静态资源输出到 Spring Boot 静态资源目录，与后端同端口提供服务

### 基础设施

- **容器化**：Docker（多阶段构建） + Docker Compose（单机编排）
- **部署**：Docker 镜像通过环境变量注入配置，支持 Windows 开发环境与 Linux 生产环境

## 开发工作流

所有开发工作 MUST 遵循以下流程，使用 Spec Kit 工作流驱动：

1. **规格化**（`/speckit-specify`）：将需求转化为带用户故事的功能规格
2. **计划**（`/speckit-plan`）：基于技术约束与章程原则制定实现计划
3. **任务生成**（`/speckit-tasks`）：将计划分解为可操作、依赖排序的任务
4. **实现**（`/speckit-implement`）：按任务顺序逐个实现
5. **文档同步**：实现完成后 MUST 更新 `README.md`，确保文档与代码一致（原则 VII）
6. **审查**：每个用户故事完成后进行代码审查，对照章程进行合规检查

**质量门禁**：

- 代码审查 MUST 验证章程合规性（分层架构、测试覆盖、中文注释）
- 代码审查 MUST 验证 Java 类变更是否同步更新了对应单元测试（原则 V）
- 所有 API 变更 MUST 同步更新对应的 API 文档
- 实现阶段完成 MUST 包含 README.md 更新（原则 VII）

## 治理

本章程是 AList-Media-Sync 项目的最高开发准则，其效力优先于所有其他实践文档。

**修订流程**：

- 章程修订 MUST 通过 `/speckit-constitution` 命令进行，确保依赖模板同步更新
- 任何团队成员可以提议修订，MUST 附带修订理由与影响分析
- 破坏性变更（MAJOR 版本递增）MUST 附带迁移计划
- 版本遵循语义化版本规范（MAJOR.MINOR.PATCH）

**合规审查**：

- 每个用户故事的实现完成后 MUST 对照本章程进行合规检查
- 违反不可协商原则（标记为「不可协商」的条目）的代码 MUST 被拒绝
- 技术约束的例外 MUST 在 plan.md 的「复杂性追踪」表格中记录并获得批准

**版本**：1.2.0 | **批准日期**：2026-06-19 | **最近修订**：2026-06-20
