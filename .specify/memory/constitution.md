<!--
============================================================
同步影响报告
============================================================
版本变更：1.5.0 → 1.6.0（MINOR — 新增章程更新与 AGENTS.md 同步规则）
修改原则：
  - VII. 日志规范 — 无变化，仅重新编号
  - VIII. 规格状态必须同步更新 — 无变化，仅重新编号
  - IX. 实现后文档同步 — 无变化，仅重新编号
新增部分：
  - X. 章程更新与 AGENTS.md 同步（不可协商）
    - /speckit-constitution 执行后 MUST 自动同步更新 AGENTS.md 文件
    - AGENTS.md 中的 AI 工作指令章节与 constitution 原则冲突时，以 constitution 为准
    - 根级 AGENTS.md 和模块级 AGENTS.md MUST 保持与 constitution 一致的版本引用
AGENTS.md 合规修复（2026-06-21）：
  - 根级 AGENTS.md：新增第 7-10 条工作指令（分层架构、测试同步、日志规范、中文优先），新增章程合规检查清单
  - 根级 AGENTS.md：技术栈中"Vue 3"修正为"React 19"（4 处）
移除部分：无
模板同步状态：
  ✅ plan-template.md — 章程检查为占位符，无需修改
  ✅ spec-template.md — 状态字段默认为"草案"，无需修改
  ✅ tasks-template.md — 任务分类通用，无需修改
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

### VII. 日志规范（不可协商）

所有重要操作 MUST 实现规范的日志输出，按以下四级分级管理：

**日志级别定义：**

- **`DEBUG`**：开发调试阶段的详细信息
  - 变量值、中间状态、流程分支详情
  - 临时文件创建/删除、文件重命名等底层操作
  - 过滤/跳过逻辑的触发记录（如 "跳过已存在文件"）
  - **生产环境默认关闭**，通过 `logging.level` 按需开启
  - 示例：`log.debug("文件已重命名：{} -> {}", oldName, newName);`

- **`INFO`**：正常业务流程的关键节点
  - 任务启动/完成、服务初始化/销毁
  - 用户操作记录（如创建任务、修改配置）
  - 重要状态变更（如任务状态转换、同步阶段切换）
  - 外部系统交互记录（如 API 调用、文件上传/下载）
  - **INFO 级别日志应能还原核心业务流程的全貌**
  - 示例：`log.info("同步任务开始执行：{} (模式: {}, {} -> {})", name, mode, source, target);`

- **`WARN`**：异常但可恢复的情况
  - 参数校验失败（使用默认值降级处理）
  - 配置项缺失或无效（系统能继续运行）
  - 外部服务暂时不可用（自动重试/降级）
  - 资源不满足但非致命（如磁盘空间检查失败但跳过检查）
  - 重复事件/请求（如 EventId 去重命中）
  - **WARN 级别日志 MUST 包含降级措施说明**
  - 示例：`log.warn("临时文件后缀未配置，使用默认值：{}", ".tmp");`

- **`ERROR`**：需要人工介入的错误
  - 业务操作失败（如文件同步失败、转码失败）
  - 未捕获的异常（含完整堆栈信息）
  - 数据一致性问题（如乐观锁冲突）
  - 关键资源不可用（如加密密钥无效）
  - **ERROR 级别 MUST 包含足够的上下文信息以支持问题定位**
  - **带异常的日志 MUST 同时输出消息和异常对象**：`log.error("xxx", exception)`
  - 示例：`log.error("同步任务执行异常：{} — {}", taskName, e.getMessage(), e);`

**日志消息格式规范：**

- 所有日志消息 MUST 使用简体中文（遵循原则 IV）
- 参数占位符 MUST 使用 SLF4J `{}` 语法（禁止字符串拼接）
- 消息 MUST 包含操作主体（如任务名称、文件路径、用户标识）
- 消息 SHOULD 包含关键业务度量（如文件数量、执行时长、成功率）
- 分隔符统一使用中文标点（全角 `：` `—`），列表项使用逗号

**日志配置要求：**

- MUST 使用 SLF4J + Logback（Spring Boot 默认，不可替换为其他日志框架）
- MUST 通过 `application.yaml` 中的 `logging.level` 按包名控制日志级别
- 生产环境根日志级别 MUST 为 `WARN`，业务包 `top.lldwb.alistmediasync` 级别 MUST 为 `INFO`
- 敏感数据（密码、Token、加密密钥）MUST NOT 出现在日志中
- MUST NOT 使用 `System.out.print` 或 `System.err.print` 替代日志输出

**日志输出格式模板：**

```java
// INFO — 业务关键节点
log.info("同步任务开始执行：{} (模式: {}, 源: {} -> 目标: {})", taskName, mode, source, target);
log.info("扫描完成，发现 {} 个待处理文件", fileCount);
log.info("同步任务执行完毕：{} — 成功 {} / 失败 {}", taskName, success, failed);

// WARN — 可恢复异常
log.warn("配置项缺失：{}，使用默认值：{}", configKey, defaultValue);
log.warn("磁盘空间检查失败：{}，跳过检查", e.getMessage());

// ERROR — 需要人工介入
log.error("文件同步失败：{}，原因：{}", fileName, e.getMessage());
log.error("转码任务执行异常：{} — {}", taskName, e.getMessage(), e);

// DEBUG — 调试详情
log.debug("临时文件已创建：{}", tempFilePath);
log.debug("跳过已存在文件：{}", fileName);
```

**外部 API 调用与本地目录操作日志规范（不可协商）：**

所有外部 API 调用（如 AList API）和本地目录操作（如文件系统扫描、读写）MUST 记录输入参数和输出结果：

**外部 API 调用日志要求：**

- 请求前 MUST 使用 `log.debug` 记录请求参数（API 端点、路径、请求体关键字段、分页参数等）
- 响应后 MUST 使用 `log.debug` 记录响应结果（状态码、返回条目数、文件大小、分页信息等）
- 请求失败 MUST 使用 `log.error` 记录异常（含请求参数和错误响应体）
- 示例：
  ```java
  // 请求前
  log.debug("调用 AList API：POST /api/fs/list — path={}, page={}, perPage={}", path, page, perPage);
  // 响应后
  log.debug("AList API 响应：POST /api/fs/list — 返回 {} 条记录，共 {} 页", total, pages);
  // 请求失败
  log.error("AList API 调用失败：POST /api/fs/list — path={}, 原因：{}", path, e.getMessage(), e);
  ```

**本地目录操作日志要求：**

- 目录列表/扫描操作 MUST 使用 `log.info`（首次进入目录）或 `log.debug`（子目录递归）记录输入路径和输出文件数
- 文件读写操作 MUST 使用 `log.debug` 记录源路径、目标路径和文件大小
- 文件/目录删除操作 MUST 使用 `log.debug` 记录目标路径
- 示例：
  ```java
  // 目录扫描
  log.info("开始扫描目录：{}", basePath);
  log.debug("目录扫描完成：{} — 发现 {} 个文件", dirPath, fileCount);

  // 文件操作
  log.debug("开始下载：{} -> {} (大小: {})", remotePath, localPath, fileSize);
  log.debug("下载完成：{} -> {} ({} bytes, 耗时: {}ms)", remotePath, localPath, size, elapsed);

  // 文件删除
  log.debug("删除文件：{}", filePath);
  ```

**HTTP 客户端配置要求：**

- RestClient MUST 配置请求/响应拦截器，统一记录 HTTP 请求方法、URI、状态码和耗时
- 拦截器日志级别为 `DEBUG`，生产环境可按需开启
- 示例：
  ```java
  .requestInterceptor((request, body) ->
      log.debug("HTTP 请求：{} {} — headers={}", request.getMethod(), request.getURI(), request.getHeaders()))
  ```

**理由**：API 调用和文件系统操作是媒体同步系统的核心交互路径。没有输入输出日志，问题定位如同盲人摸象——无法判断问题出在请求构造阶段、网络传输阶段还是响应处理阶段。本地文件操作的日志缺失同样导致"文件去哪了"、"为什么没扫到"等常见问题无从排查。规范这两类操作的日志输出，使每一次外部交互都可追溯、可审计。

**理由**：规范的日志是生产环境问题定位和运维监控的基础。没有统一的日志级别标准，关键错误可能被淹没在海量 DEBUG 日志中，或者 INFO 日志过于稀疏导致无法追溯业务流程。四级分级的明确边界消除了开发者对 "该用 WARN 还是 ERROR" 的犹豫，统一的格式则使日志可被结构化解析和聚合分析。

### VIII. 规格状态必须同步更新（不可协商）

所有 spec.md 文件 MUST 在每个 Spec Kit 工作流阶段完成后立即更新其 `**状态**` 字段，状态流转规则如下：

- **草案** → `/speckit-specify` 完成后的初始状态
- **已澄清** → `/speckit-clarify` 完成后 MUST 立即更新为此状态
- **已计划** → `/speckit-plan` 完成后 MUST 立即更新为此状态
- **已排期** → `/speckit-tasks` 完成后 MUST 立即更新为此状态
- **实现中** → `/speckit-implement` 开始执行后 MUST 立即更新为此状态
- **已完成** → `/speckit-implement` 全部任务完成后 MUST 立即更新为此状态

任何阶段完成后未更新状态的 spec.md MUST NOT 进入下一阶段。状态字段是项目进度追踪的唯一真实来源，过时的状态等同于错误信息。

**理由**：spec.md 的状态字段是团队了解功能进度的核心信号。状态滞后会导致重复工作（如对已澄清的规格再次澄清）或遗漏步骤（如跳过澄清直接计划）。将状态更新绑定到阶段完成时刻，是确保进度信息始终可信的唯一可靠方式。

### IX. 实现后文档同步（不可协商）

每次执行完 `/speckit-implement` 后，MUST 自动更新项目根目录的 `README.md` 文件，确保文档与代码实现保持一致：

- 新增功能、配置项、API 端点、环境变量 MUST 同步反映到 README.md 对应章节
- 废弃或移除的功能 MUST 从 README.md 中移除或标注为已废弃
- README.md 的更新 MUST 作为实现阶段的收尾步骤，不得推迟到后续迭代

**理由**：过时的文档比没有文档更具危害性——它会误导使用者做出错误的配置决策。将文档同步绑定到实现流程，是确保文档始终可信的唯一可靠方式。

### X. 章程更新与 AGENTS.md 同步（不可协商）

每次执行 `/speckit-constitution` 更新章程后，MUST 自动同步更新以下 AGENTS.md 文件：

- **根级 AGENTS.md**（项目根目录）：MUST 在章程更新后同步检查 AI 工作指令章节，确保指令与章程原则一致
- **模块级 AGENTS.md**（各模块目录）：MUST 在章程涉及特定模块的约束变更时同步更新对应模块的 AGENTS.md

同步规则：

- AGENTS.md 中的 AI 工作指令与章程原则冲突时，MUST 以章程为准
- 章程新增或修改的原则如影响模块行为，MUST 在对应模块 AGENTS.md 中反映
- 章程版本号变更 MUST 同步反映到 AGENTS.md 中（如通过版本引用或注释）
- 同步检查 MUST 作为 `/speckit-constitution` 命令的收尾步骤，不得推迟

**理由**：AGENTS.md 是 AI 编码代理的运行时指令文件，章程是项目的最高开发准则。如果 AGENTS.md 中的行为指令与章程原则不一致，AI 代理将按错误指令工作，导致代码不合规。将章程更新与 AGENTS.md 同步绑定，是确保 AI 代理始终按最新章程工作的唯一可靠方式。

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
- **日志**：SLF4J + Logback（Spring Boot 默认，不可替换）

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
5. **文档同步**：实现完成后 MUST 更新 `README.md`，确保文档与代码一致（原则 IX）
6. **审查**：每个用户故事完成后进行代码审查，对照章程进行合规检查

**质量门禁**：

- 代码审查 MUST 验证章程合规性（分层架构、测试覆盖、中文注释、日志规范）
- 代码审查 MUST 验证 Java 类变更是否同步更新了对应单元测试（原则 V）
- 代码审查 MUST 检查日志级别使用是否符合原则 VII 的分级标准
- 所有 API 变更 MUST 同步更新对应的 API 文档
- 实现阶段完成 MUST 包含 README.md 更新（原则 IX）
- 章程更新完成 MUST 包含 AGENTS.md 文件同步（原则 X）
- 每个 Spec Kit 工作流阶段完成后 MUST 验证 spec.md 状态字段已同步更新（原则 VIII）

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

**版本**：1.6.0 | **批准日期**：2026-06-19 | **最近修订**：2026-06-21
