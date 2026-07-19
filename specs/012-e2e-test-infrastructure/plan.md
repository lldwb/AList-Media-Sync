# 实现计划：端到端测试基础设施

**分支**：`012-e2e-test-infrastructure` | **日期**：2026-07-19 | **规格**：[spec.md](./spec.md)

**输入**：来自 `/specs/012-e2e-test-infrastructure/spec.md` 的功能规格

## 摘要

补全项目测试金字塔的集成测试层与端到端测试层，填补章程原则 V 要求下"所有 Repository 与外部 API 客户端 MUST 有集成测试"的空白，并建立基于真实 AList + 录播姬二进制实例的 E2E 验证能力。

核心交付：
- **集成测试层**：6 个 Repository 的 `@DataJpaTest` 集成测试（验证乐观锁、事务、幂等去重等数据完整性约束）+ AList 客户端的 WireMock 契约测试（验证 9 个公共方法符合 AList REST 契约）
- **端到端测试层**：3 条核心业务链路（webhook 触发同步、手动同步、转码）+ traceId 全链路验证，通过真实二进制实例驱动
- **环境准备脚本**：一键下载 AList 与录播姬二进制到项目 `scripts/e2e/bin/`，幂等可重复
- **Maven profile 隔离**：`-Pe2e` 触发 E2E、`-Pintegration` 触发集成测试，不污染常规 `mvn test`

技术方法：复用项目已有的 H2 嵌入式数据库（集成测试）+ 引入 WireMock（AList 契约模拟）+ maven-failsafe-plugin（慢速测试生命周期管理）；E2E 通过 `@SpringBootTest` 全量上下文 + 真实外部二进制实例实现。

## 技术上下文

**语言/版本**：Java 21（LTS，虚拟线程已启用 `spring.threads.virtual.enabled=true`）

**主要依赖**：
- 已有：Spring Boot 4.1.0（Spring Framework 7.x）、Spring Data JPA、RestClient（HTTP 客户端）、JAVE2（转码）、SLF4J+Logback、Lombok、Jakarta Validation、H2、Spring Security Crypto（BCrypt）
- 测试已有：`spring-boot-starter-data-jpa-test`、`spring-boot-starter-validation-test`、`spring-boot-starter-webmvc-test`（含 JUnit 5 + Mockito + MockMvc 传递依赖）
- **本功能新增**：`wiremock-standalone`（AList REST 契约模拟）、`maven-failsafe-plugin`（集成/E2E 测试驱动）
- **明确不引入**：Testcontainers（YAGNI，H2 嵌入式 + 二进制本地启动已满足需求，避免 Docker 强依赖）

**存储**：H2 嵌入式数据库。集成测试用内存模式（`application-test.yaml` 已配置 `DB_CLOSE_DELAY=-1;MODE=MySQL;ddl-auto=create-drop`）；E2E 用文件模式（贴近生产 `application.yaml` 配置）

**测试**：
- 单元测试（已有 37 个）：JUnit 5 + Mockito + MockMvc，`@ExtendWith(MockitoExtension.class)` / `@WebMvcTest`
- 集成测试（新增）：`@DataJpaTest`（Repository 层，真实 H2）+ WireMock（AList 客户端契约，端口动态分配）
- 端到端测试（新增）：`@SpringBootTest(webEnvironment=RANDOM_PORT)` + 真实 AList/录播姬二进制 + TestRestTemplate
- 测试驱动：`maven-surefire-plugin`（单元测试，已有）+ `maven-failsafe-plugin`（集成/E2E，`*IT.java` 命名约定，新增）

**目标平台**：Windows 开发环境优先（二进制本地启动，`scripts/e2e/*.ps1`）；Linux 通过已有 `specs/003-docker-deploy` Docker 方案覆盖（`scripts/e2e/*.sh` 备选）

**项目类型**：web-service（Spring Boot 后端 + React 前端同端口；本功能聚焦后端测试基础设施）

**性能目标**：
- 集成测试套件（6 Repository + AList 契约）< 60 秒完成
- E2E 单链路（含录播姬录制-关闭-触发）< 5 分钟完成
- E2E 环境准备（下载+启动）< 10 分钟（SC-001）

**约束**：
- E2E 必须幂等可重复运行（SC-004，连续 3 次通过），每次运行前清理数据库+文件系统+临时文件
- E2E 通过独立 Maven profile 触发，不污染常规 `mvn test`（FR-005）
- 测试 fixtures 体积 < 10MB（小尺寸媒体样本，纳入版本控制）
- E2E 不依赖真实 B 站直播流自然结束，通过手动关闭直播触发"录制完成"事件（FR-011）
- 端口冲突可检测与自动避让（AList 5344、录播姬 2356、系统 8080 占用时降级报告）

**规模/范围**：
- 6 个 Repository 集成测试（含 `@Query`、乐观锁、幂等去重等自定义方法）
- 1 个 AList 客户端契约测试类（覆盖 AListStorageStrategy 的 9 个公共方法）
- 3 条 E2E 链路 + 1 个 traceId 链路验证
- 1 套环境准备脚本（Windows PowerShell 优先 + Linux Bash 备选）

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

| # | 原则 | 门禁评估 | 状态 |
|---|------|---------|------|
| I | 分层架构 | 测试目录镜像 `src/main/java` 分层结构（integration/repository、integration/client、e2e、support），测试代码不跨业务层调用；E2E 仅通过 HTTP 端点与公共 Service 入口驱动，不直调 Repository | ✅ 通过 |
| II | 数据完整性 | 集成测试显式验证 `@Version` 乐观锁、`@Transactional` 回滚、`EventId` 幂等去重（WebhookEventRepository.findByEventId）、`markAllRunningAsInterrupted` 等数据一致性约束；E2E 验证任务记录与执行历史持久化 | ✅ 通过 |
| III | RESTful API 契约 | E2E 通过真实 HTTP 调用验证 `/api/webhooks/recorder`、`/api/diagnostics/run`、`/api/sync-tasks/**` 端点契约；WireMock 桩严格按 `md/alist/` 契约构造，感知契约漂移 | ✅ 通过 |
| IV | 中文优先 | 所有测试注释、fixtures 描述、quickstart、脚本输出使用简体中文；测试方法名用英文（遵循 Java 惯例与现有测试风格） | ✅ 通过 |
| V | 测试不可省略 | 本功能直接服务于原则 V：补全 Repository 集成测试（>60%）与外部 API 客户端集成测试，新增 E2E 覆盖核心链路。覆盖率目标对齐章程要求 | ✅ 通过 |
| VI | 简洁至上（YAGNI） | **2 处违规需证明合理性**（见复杂性追踪）：① 引入 `wiremock-standalone`；② 引入 `maven-failsafe-plugin`。明确拒绝 Testcontainers（H2+二进制已够）与手写 stub 服务（易漂移） | ⚠️ 违规已证明（见下表） |
| VII | 日志规范 | E2E 验证 traceId 全链路传播（`TraceContext.runWith` MDC 注入）、`error.log` 分流、`X-Trace-Id` 响应头覆盖 `/api/**`；E2E 复用 `scripts/diagnose.{sh,bat}` 收集诊断包验证链路可追溯 | ✅ 通过 |
| VIII | 规格状态同步 | plan 完成后将 spec.md 状态从"草案"更新为"已计划" | ✅ 通过（本计划收尾执行） |
| IX | 实现后文档同步 | 实现后更新 `docs/02-开发环境搭建.md`（E2E 环境准备）、`docs/06-运维部署.md`（测试 profile 用法）、`CHANGELOG.md` 新增版本条目 | ✅ 通过（实现阶段执行） |
| X | 章程更新与 AGENTS.md 同步 | 本功能不涉及章程修订，N/A | ✅ N/A |
| XI | 文档体系结构 | E2E 环境准备文档同步到 `docs/02`，配置项（`app.e2e.*` 若有）同步到 `docs/04` SSOT，spec 冻结于 `specs/012` | ✅ 通过 |

**门禁结论**：所有原则通过或违规已证明合理性，可进入阶段 0 研究。

## 项目结构

### 文档（本功能）

```text
specs/012-e2e-test-infrastructure/
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── research.md          # 阶段 0 输出（技术选型与研究）
├── data-model.md        # 阶段 1 输出（测试 fixtures 结构与链路断言点）
├── quickstart.md        # 阶段 1 输出（E2E 验证运行指南）
├── contracts/           # 阶段 1 输出（测试对外契约）
│   ├── alist-api-stubs.md      # AList WireMock 桩契约（端点+响应结构）
│   ├── e2e-maven-profile.md    # E2E/integration Maven profile 命令契约
│   └── env-prep-script.md      # 环境准备脚本接口契约
└── tasks.md             # 阶段 2 输出（/speckit-tasks 命令 - 非 /speckit-plan 创建）
```

### 源代码（仓库根目录）

```text
src/
├── main/
│   ├── java/top/lldwb/alistmediasync/   # 业务代码（已有，本功能不改动）
│   └── resources/
│       └── application.yaml             # 已有生产配置
└── test/
    ├── java/top/lldwb/alistmediasync/
    │   ├── common/ sync/ transcode/ webhook/ storage/  # 已有单元测试（37 个，保持不动）
    │   ├── integration/                 # 【新增】集成测试层
    │   │   ├── repository/              #   6 个 Repository @DataJpaTest
    │   │   │   ├── StorageEngineRepositoryIT.java
    │   │   │   ├── SyncTaskRepositoryIT.java
    │   │   │   ├── TaskExecutionRepositoryIT.java
    │   │   │   ├── WebhookEventRepositoryIT.java
    │   │   │   ├── WebhookRuleRepositoryIT.java
    │   │   │   └── TranscodeTaskRepositoryIT.java
    │   │   └── client/                  #   AList 客户端 WireMock 契约测试
    │   │       └── AListStorageStrategyIT.java
    │   ├── e2e/                         # 【新增】端到端测试层
    │   │   ├── E2ETestBase.java         #   基类：启动外部依赖、清理状态
    │   │   ├── WebhookSyncE2ETest.java  #   链路1：webhook 触发同步
    │   │   ├── ManualSyncE2ETest.java   #   链路2：手动同步任务
    │   │   ├── TranscodeE2ETest.java    #   链路3：转码任务
    │   │   └── TraceIdChainE2ETest.java #   traceId 全链路验证
    │   └── support/                     # 【新增】测试支持工具
    │       ├── E2ELifecycleManager.java #   外部依赖生命周期管理
    │       ├── AListTestClient.java     #   AList 真实操作封装
    │       └── DanmujiEventTrigger.java #   录播姬录制-关闭-触发封装
    └── resources/
        ├── application-test.yaml        # 已有（集成测试用，H2 内存）
        ├── application-e2e.yaml         # 【新增】E2E 专用配置（H2 文件、端口、路径）
        ├── fixtures/                    # 【新增】测试 fixtures
        │   ├── webhook/                 #   录播姬 webhook 事件样本
        │   │   ├── fileclosed-event.json
        │   │   └── sessionstarted-event.json
        │   ├── media/                   #   小尺寸测试媒体（<5MB）
        │   │   └── sample.mp4
        │   └── alist/                   #   AList 期望响应样本
        │       └── list-response.json
        └── wiremock/                    # 【新增】AList 桩映射
            └── alist-mappings/

scripts/
└── e2e/                                 # 【新增】E2E 环境准备脚本
    ├── prepare-e2e-env.ps1              #   Windows：一键下载 alist+录播姬
    ├── prepare-e2e-env.sh               #   Linux 备选
    ├── start-alist.ps1                  #   启动 AList 实例
    ├── start-danmuji.ps1                #   启动录播姬实例
    ├── stop-e2e-env.ps1                 #   停止并清理外部依赖
    ├── e2e-config/                      #   外部依赖配置模板
    │   ├── alist.config.json
    │   └── danmuji.config.toml
    └── bin/                             #   下载的二进制存放（.gitignore 忽略）
        ├── alist/                       #   AList 可执行文件
        └── danmuji/                     #   录播姬可执行文件
```

**结构决策**：
- 测试目录分层镜像 `src/main/java` 业务结构，`integration/` 与 `e2e/` 物理隔离，对应 Maven profile 隔离
- 集成测试用 `*IT.java` 后缀（failsafe 约定），E2E 用 `*E2ETest.java` 明示语义
- 外部二进制存放于 `scripts/e2e/bin/`（`.gitignore` 忽略，不纳入版本控制），配置模板 `e2e-config/` 纳入版本控制
- 复用 `scripts/diagnose.{sh,bat}`（009-lightweight-diagnostics 产出）验证 traceId，不重复造轮子
- 环境准备脚本风格对齐 `specs/005-standalone-bootstrap` 的 `download-jre.sh` 模式（幂等下载、支持本地路径跳过）

## 复杂性追踪

> **仅在章程检查有必须证明合理性的违规时填充**

| 违规 | 为什么需要 | 被拒绝的更简单替代方案及原因 |
|------|-----------|------------------------|
| 引入 `wiremock-standalone` 依赖（违反原则 VI YAGNI） | AList 客户端集成测试需要模拟 AList REST 契约（9 个端点，含 `/ping` text/plain、`/api/fs/put` 二进制流、统一 `{code,message,data}` 响应结构）。WireMock 是 Spring 生态标准契约模拟工具，支持端口动态分配、请求匹配、响应模板、录制回放，能检测契约漂移 | ① 手写 `@RestController` stub 服务：需重复实现 9 个端点，易与真实契约漂移，维护成本高；② Testcontainers + 真实 AList 容器：偏离用户决策 Q1:A（二进制本地启动），引入 Docker 强依赖，与 E2E 层重复；③ 直接调用真实 AList（无桩）：集成测试变 E2E，失去"快速定位契约层问题"的中间层价值 |
| 引入 `maven-failsafe-plugin`（违反原则 VI YAGNI） | 集成测试与 E2E 测试耗时显著高于单元测试（集成 <60s，E2E 单链路 <5min），需独立生命周期管理。failsafe 通过 `*IT.java` 命名约定与 `integration-test`/`verify` 阶段绑定，支持与 surefire 分离执行 | ① 全部用 surefire 驱动：E2E 会拖慢常规 `mvn test`，违反 FR-005（不污染常规测试）；② 手写 shell 脚本驱动测试：绕过 Maven 生命周期，依赖管理与报告生成缺失，CI 集成困难；③ 拆分为独立 Maven 模块：过度设计，项目单模块结构足以承载测试分层 |
