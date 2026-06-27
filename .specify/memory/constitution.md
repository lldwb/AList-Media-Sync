<!--
============================================================
同步影响报告
============================================================
版本变更：1.8.0 → 1.9.0（MINOR — 在原则 VII 中固化轻量诊断系统对日志的新要求）
修改原则：
  - VII. 日志规范 — 新增不可协商子条款：
    * traceId 全链路追踪与 MDC 结构化字段（module/operation/errorType）
    * 错误日志强制双写：app.log（含全部级别）+ error.log（仅 ERROR 级）
    * 任务边界 MUST 调用 TraceContext.runWith 或等价机制清理 MDC
    * HTTP 响应 MUST 返回 X-Trace-Id，便于将一次请求与日志串联
    * 敏感数据脱敏：日志与诊断包 MUST NOT 出现原始密码 / Token / 密钥 / 认证头 / Cookie
    * 诊断包入口：MUST 同时提供脚本（diagnose.sh / diagnose.bat）、API（POST /api/diagnostics/run）、Web 三种入口
新增部分：无
移除部分：无
模板同步状态：
  ✅ plan-template.md — 章程检查为占位符，无需修改
  ✅ spec-template.md — 无需修改
  ✅ tasks-template.md — 无需修改
  ✅ AGENTS.md（根级） — 版本引用同步至 1.9.0；AI 工作指令第 9 条扩展为 traceId/error.log/X-Trace-Id/脱敏
  ✅ src/main/java/.../AGENTS.md — 合规要点扩展 traceId 与脱敏
  ⚠ 前端 AGENTS.md — 待人工确认是否需要补充 X-Trace-Id 响应头读取约束
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
- 所有 `/api/**` 响应 MUST 包含 `X-Trace-Id` 响应头（与原则 VII 的 traceId 串联，见下文）

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

所有重要操作 MUST 实现规范的日志输出，按以下四级分级管理，并满足轻量诊断系统对结构化、可追溯、可分享的强制要求。

#### 7.1 日志级别定义

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
  - **ERROR 级别日志 MUST 同时写入 `${LOG_PATH}/app.log` 和 `${LOG_PATH}/error.log`**（由 Logback Appender 强制分流，不可在业务代码层面绕过）
  - 示例：`log.error("同步任务执行异常：{} — {}", taskName, e.getMessage(), e);`

#### 7.2 日志消息格式规范

- 所有日志消息 MUST 使用简体中文（遵循原则 IV）
- 参数占位符 MUST 使用 SLF4J `{}` 语法（禁止字符串拼接）
- 消息 MUST 包含操作主体（如任务名称、文件路径、用户标识）
- 消息 SHOULD 包含关键业务度量（如文件数量、执行时长、成功率）
- 分隔符统一使用中文标点（全角 `：` `—`），列表项使用逗号

#### 7.3 traceId 与结构化 MDC 字段（不可协商）

为支撑轻量诊断系统（specs/009-lightweight-diagnostics），所有任务执行 MUST 携带 traceId 并以 MDC 方式注入结构化上下文：

- **生成时机**：每次用户触发、定时触发或事件触发的任务执行 MUST 在入口处生成或继承唯一 traceId（格式：长度 8–128，仅含字母数字、`-`、`_`、`.`，禁止空白/控制字符）
- **传播机制**：traceId MUST 通过 SLF4J MDC 在线程上下文中传播；跨线程提交（`@Async`、`CompletableFuture`、自定义线程池）MUST 显式透传，禁止依赖 InheritableThreadLocal 默认行为
- **结构化字段**：每条业务日志的 MDC MUST 至少包含 `traceId`，并 SHOULD 包含 `module`（如 `sync`、`transcode`、`webhook`、`storage`）与 `operation`（如 `同步任务执行`、`转码任务创建`）；ERROR 级别日志 MUST 额外写入 `errorType`（异常类别简短标识）
- **任务边界**：任务结束（成功或失败）MUST 调用 `TraceContext.clear()` 或使用 `TraceContext.runWith(...)` 包裹执行体，防止 MDC 字段污染线程池中的后续任务
- **覆盖率要求**：100% 的新任务执行记录 MUST 包含非空且唯一的 traceId（对应 SC-003）；至少 90% 的错误事件 MUST 同时包含 `module`、`operation`、`traceId`、`errorType`、错误消息和可定位原因（对应 SC-005）
- **日志 Pattern**：Logback 的 `console` 与 `file` Pattern MUST 输出 `[traceId=%X{traceId:-}] [module=%X{module:-}] [operation=%X{operation:-}] [errorType=%X{errorType:-}]`，不得删除或简化字段

#### 7.4 错误日志双写（不可协商）

- MUST 配置独立的 `ERROR_FILE` Appender，将 ERROR 级别事件写入 `${LOG_PATH}/error.log`
- 同一条 ERROR 日志 MUST 同时出现在 `app.log`（全级别）和 `error.log`（仅 ERROR），确保全量审计与快速错误定位两条路径互不阻塞
- error.log 的 Pattern MUST 与 app.log 保持一致（含 traceId / module / operation / errorType）
- 滚动策略 MUST 按日切分并保留最近 N 天（默认 7 天），避免单文件无限增长

#### 7.5 X-Trace-Id 响应头（不可协商）

- 所有 `/api/**` HTTP 响应 MUST 通过过滤器统一注入 `X-Trace-Id` 响应头，值为当前 MDC 中的 traceId
- 当请求未携带 traceId 时，过滤器 MUST 生成新的合法 traceId 并写入 MDC 与响应头
- 响应头 MUST 在异常路径（4xx/5xx）下同样存在，不允许仅在成功路径下注入

#### 7.6 敏感数据脱敏（不可协商）

- 日志消息与诊断包内容 MUST NOT 出现原始密码、Token、API Key、加密密钥、`Authorization` 头、`Cookie` 头、Webhook Secret 等任意凭据值
- 调用外部 API 时，请求/响应日志 MUST 对认证头与请求体中的敏感字段做脱敏（保留字段名，值替换为 `***` 或长度提示）
- 配置摘要日志（如启动信息）MUST 标注敏感字段为 "已配置/未配置"，不得打印实际值
- 出现疑似凭据含义的配置键（包含 `password`、`token`、`secret`、`key`、`auth`、`cookie` 等关键字，大小写无关）MUST 默认按敏感处理

#### 7.7 日志配置要求

- MUST 使用 SLF4J + Logback（Spring Boot 默认，不可替换为其他日志框架）
- MUST 通过 `application.yaml` 中的 `logging.level` 按包名控制日志级别
- 生产环境根日志级别 MUST 为 `WARN`，业务包 `top.lldwb.alistmediasync` 级别 MUST 为 `INFO`
- 日志输出目录 MUST 通过 `LOG_PATH` 环境变量配置（默认 `./logs`），同时生成 `app.log` 与 `error.log`
- MUST NOT 使用 `System.out.print` 或 `System.err.print` 替代日志输出

#### 7.8 日志输出格式模板

```java
// INFO — 业务关键节点
log.info("同步任务开始执行：{} (模式: {}, 源: {} -> 目标: {})", taskName, mode, source, target);
log.info("扫描完成，发现 {} 个待处理文件", fileCount);
log.info("同步任务执行完毕：{} — 成功 {} / 失败 {}", taskName, success, failed);

// WARN — 可恢复异常
log.warn("配置项缺失：{}，使用默认值：{}", configKey, defaultValue);
log.warn("磁盘空间检查失败：{}，跳过检查", e.getMessage());

// ERROR — 需要人工介入（同时写入 app.log 与 error.log，含 errorType MDC）
TraceContext.setErrorType(e.getClass().getSimpleName());
log.error("文件同步失败：{}，原因：{}", fileName, e.getMessage(), e);

// DEBUG — 调试详情
log.debug("临时文件已创建：{}", tempFilePath);
log.debug("跳过已存在文件：{}", fileName);

// 任务入口（traceId + MDC 结构化字段）
TraceContext.runWith(null, "sync", "同步任务执行", () -> {
    log.info("同步任务开始执行：{}", taskName);
    // ... 业务逻辑 ...
});
```

#### 7.9 外部 API 调用与本地目录操作日志规范（不可协商）

所有外部 API 调用（如 AList API）和本地目录操作（如文件系统扫描、读写）MUST 记录输入参数和输出结果：

**外部 API 调用日志要求：**

- 请求前 MUST 使用 `log.debug` 记录请求参数（API 端点、路径、请求体关键字段、分页参数等），敏感字段按 7.6 节脱敏
- 响应后 MUST 使用 `log.debug` 记录响应结果（状态码、返回条目数、文件大小、分页信息等）
- 请求失败 MUST 使用 `log.error` 记录异常（含请求参数和错误响应体，敏感字段脱敏）
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

- RestClient MUST 配置请求/响应拦截器，统一记录 HTTP 请求方法、URI、状态码和耗时；MUST 注入并透传 MDC traceId
- 拦截器日志级别为 `DEBUG`，生产环境可按需开启；敏感请求头 MUST 在记录前脱敏
- 示例：
  ```java
  .requestInterceptor((request, body, execution) -> {
      log.debug("HTTP 请求：{} {} — headers={}", request.getMethod(), request.getURI(),
                sanitize(request.getHeaders()));
      return execution.execute(request, body);
  })
  ```

#### 7.10 诊断包入口（不可协商）

为确保任意部署形态下的可观测性，系统 MUST 同时提供以下三种诊断生成入口（参见 FR-005、SC-001）：

- **脚本入口**：项目根目录 MUST 提供 `diagnose.sh`（Linux/macOS）与 `diagnose.bat`（Windows），用于本地开发与一体化启动包
- **API 入口**：后端 MUST 暴露 `POST /api/diagnostics/run` 端点（受认证保护），用于 Docker 部署与远程触发
- **Web 入口**：Web 管理前端 MUST 提供诊断生成按钮，复用 API 入口
- 任一入口在 30 秒内 MUST 输出诊断包路径与 `diagnostics/latest/summary.md` 摘要文件位置
- 诊断生成过程 MUST 为只读操作，MUST NOT 触发同步、转码、Webhook 处理等业务副作用

**理由（合并 7.1–7.10）**：

规范的日志是生产环境问题定位和运维监控的基础。没有统一的日志级别标准，关键错误可能被淹没在海量 DEBUG 日志中，或者 INFO 日志过于稀疏导致无法追溯业务流程。四级分级的明确边界消除了开发者对 "该用 WARN 还是 ERROR" 的犹豫，统一的格式则使日志可被结构化解析和聚合分析。

API 调用和文件系统操作是媒体同步系统的核心交互路径。没有输入输出日志，问题定位如同盲人摸象——无法判断问题出在请求构造阶段、网络传输阶段还是响应处理阶段。

进一步地，traceId、结构化 MDC 字段、error.log 分流和 X-Trace-Id 响应头共同构成"一次任务可追溯到一条链路"的能力，消除了在并发场景下"日志混杂、找不到上下文"的核心痛点；敏感数据脱敏则保证诊断包可安全分享给 AI 或外部协作者；三种诊断入口确保本地、Docker、一体化启动包均可一键产出可交付的诊断证据，让"用户报告问题—维护者复现问题"的反馈环从小时级压缩到分钟级。

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
- **日志**：SLF4J + Logback（Spring Boot 默认，不可替换），MUST 通过 MDC 承载 traceId / module / operation / errorType 结构化字段，并提供独立的 `error.log` Appender

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
- **诊断**：MUST 同时提供 `diagnose.sh` / `diagnose.bat` 脚本入口、`POST /api/diagnostics/run` API 入口与 Web 入口，覆盖本地、Docker、一体化启动包三种部署形态

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
- 代码审查 MUST 验证 traceId / MDC 字段的注入与清理，error.log 分流是否生效，X-Trace-Id 响应头是否覆盖所有 `/api/**` 路径（原则 VII §7.3–§7.5）
- 代码审查 MUST 检查日志与诊断包是否完成敏感数据脱敏（原则 VII §7.6）
- 所有 API 变更 MUST 同步更新对应的 API 文档
- 实现阶段完成 MUST 包含 README.md 更新（原则 IX）
- 章程更新完成 MUST 包含 AGENTS.md 文件同步（原则 X）
- 每个 Spec Kit 工作流阶段完成后 MUST 验证 spec.md 状态字段已同步更新（原则 VIII）

## 治理

本章程是 AList-Media-Sync 项目的最高开发准则，其效力优先于所有其他实践文档。

### AI 代理上下文文件体系

项目使用四层上下文文件指导 AI 编码代理，权重从高到低如下：

```
constitution.md  >  AGENTS.md（根级）  >  前端/后端 AGENTS.md  >  各模块 AGENTS.md
   宪法              法律                   行政法规                地方性法规
```

| 层级 | 文件 | 作用范围 | 适用场景 |
|------|------|---------|---------|
| 1（最高） | `.specify/memory/constitution.md` | 全局 | 功能修改、架构变更、新增原则；下级无法解释时 |
| 2 | `AGENTS.md`（根级） | 全局 | 日常修改、代码审查、AI 行为约束；下级无法解释时 |
| 3 | 前端/后端 `AGENTS.md` | 前端或后端整体 | 涉及前端/后端修改；下级无法解释时 |
| 4 | 各模块 `AGENTS.md` | 模块 | 对应模块修改、其他模块修改涉及本模块时；下级无法解释时 |

**修改权限：**

| 层级 | 修改条件 |
|------|---------|
| constitution.md | 仅通过 `/speckit-constitution` 修订流程修改 |
| AGENTS.md（根级） | 可在不违反 constitution.md 的前提下日常优化；违反则终止修改并提示 |
| 前端/后端 AGENTS.md | 涉及前端/后端修改后，如需更新 AGENTS.md 则进行修改 |
| 各模块 AGENTS.md | 对应模块修改或他模块修改涉及本模块后，如需更新 AGENTS.md 则进行修改 |

**冲突解决规则：**

- 当各层规则冲突时，上层文件优先级高于下层
- AGENTS.md（根级）中的指令 MUST 对齐 constitution.md 的不可协商原则，不得与之冲突
- 前端/后端 AGENTS.md 中的说明 MUST 对齐 AGENTS.md（根级）的 AI 工作指令，可在此基础上细化端级特定约束
- 模块 AGENTS.md 中的说明 MUST 对齐上级 AGENTS.md，可在此基础上细化模块特定约束
- 模块 AGENTS.md 之间的关联描述 MUST 与对应模块 AGENTS.md 的自述一致

**修订流程**：

- 章程修订 MUST 通过 `/speckit-constitution` 命令进行，确保依赖模板同步更新
- 任何团队成员可以提议修订，MUST 附带修订理由与影响分析
- 破坏性变更（MAJOR 版本递增）MUST 附带迁移计划
- 版本遵循语义化版本规范（MAJOR.MINOR.PATCH）

**合规审查**：

- 每个用户故事的实现完成后 MUST 对照本章程进行合规检查
- 违反不可协商原则（标记为「不可协商」的条目）的代码 MUST 被拒绝
- 技术约束的例外 MUST 在 plan.md 的「复杂性追踪」表格中记录并获得批准

**版本**：1.9.0 | **批准日期**：2026-06-19 | **最近修订**：2026-06-27
