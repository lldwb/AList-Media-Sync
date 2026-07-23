# 契约：E2E / 集成测试 Maven Profile

**功能**：`012-e2e-test-infrastructure` | **用途**：定义测试执行的 Maven profile 命令契约

本文档定义开发者与 CI 触发不同层级测试的 Maven profile 契约，实现 FR-005（端到端测试不污染常规测试）。

## Profile 清单

### 默认 profile（无 `-P` 参数）

**命令**：`mvn test` 或 `./mvnw test`

**行为**：
- 触发 `maven-surefire-plugin` 执行单元测试
- 测试范围：`src/test/java/**/*Test.java`，**排除** `*IT.java` 与 `*E2ETest.java`
- 不启动任何外部依赖
- 预期完成时间：< 60 秒（37 个现有单元测试）

**适用场景**：日常开发快速反馈、CI 提交前检查

### integration profile

**命令**：`mvn verify -Pintegration` 或 `./mvnw verify -Pintegration`

**行为**：
- 触发 `maven-failsafe-plugin` 执行集成测试
- 测试范围：`src/test/java/**/*IT.java`（Repository `@DataJpaTest` + AList 客户端 WireMock 契约测试）
- 外部依赖：无（H2 内存数据库 + WireMock 动态端口，全部进程内）
- 配置：使用 `application-test.yaml`（已有，H2 内存模式）
- 预期完成时间：< 120 秒

**适用场景**：提交前完整检查、PR 审查、CI 集成阶段

### e2e profile

**命令**：`mvn verify -Pe2e` 或 `./mvnw verify -Pe2e`

**行为**：
- 触发 `maven-failsafe-plugin` 执行端到端测试
- 测试范围：`src/test/java/**/*E2ETest.java`（3 条核心链路 + traceId 链路验证）
- 事件驱动：`WebhookEventReplayer` 重放 `fixtures/webhook/` 样本注入系统（FR-011），不依赖真实直播源
- 外部依赖：自动启动 AList 实例（录播姬可选，默认不启动，`-Ddanmuji.enabled=true` 启用可选验证）；端口动态分配（FR-014，由 `E2ELifecycleManager` 管理）
- 配置：使用 `application-e2e.yaml`（H2 文件模式，独立数据目录 `./data-e2e/`）
- 前置条件：AList 二进制已下载（`scripts/e2e/bin/alist/` 存在；不存在时自动触发 `prepare-e2e-env` 脚本）
- 预期完成时间：单链路 < 5 分钟，全套 < 15 分钟

**适用场景**：发布前验证、重大变更后回归、手动全链路验证、CI nightly 定时运行（FR-013）

**失败保留**：测试失败时保留现场（数据库 + 文件系统）供诊断，下次运行前强制清理（FR-009）；无直播源 skip 机制（重放方案下事件来源稳定）。

## profile 组合规则

| 命令 | 单元测试 | 集成测试 | E2E 测试 | 外部依赖 |
|------|---------|---------|---------|---------|
| `mvn test` | ✅ | ❌ | ❌ | 无 |
| `mvn verify -Pintegration` | ✅ | ✅ | ❌ | 无 |
| `mvn verify -Pe2e` | ✅ | ✅ | ✅ | AList（录播姬可选） |
| `mvn verify -Pe2e -DskipUnitTests` | ❌ | ✅ | ✅ | AList（录播姬可选） |

**说明**：`mvn verify` 隐含执行 `test` 阶段（单元测试），除非显式 `-DskipTests` 或 `-DskipUnitTests`。

## 配置位置

- profile 定义：`pom.xml` 的 `<profiles>` 段
- surefire 配置：`pom.xml` 的 `maven-surefire-plugin`（排除 `*IT.java`、`*E2ETest.java`）
- failsafe 配置：`pom.xml` 的 `maven-failsafe-plugin`（`<includes>` 按 profile 区分 `*IT.java` 与 `*E2ETest.java`）
- 测试配置文件：`src/test/resources/application-test.yaml`（集成）、`application-e2e.yaml`（E2E）

## 命名约定

| 后缀 | 层级 | 驱动插件 | 示例 |
|------|------|---------|------|
| `*Test.java` | 单元测试 | surefire | `SyncServiceTest.java` |
| `*IT.java` | 集成测试 | failsafe | `SyncTaskRepositoryIT.java` |
| `*E2ETest.java` | 端到端测试 | failsafe | `WebhookSyncE2ETest.java` |

## CI 集成建议

- **PR 提交检查**：`mvn verify -Pintegration`（快速 + 集成，无外部依赖，不触发 E2E）
- **nightly 定时运行**：`mvn verify -Pe2e`（全链路，CI 定时任务触发，FR-013；PR 流水线不触发 E2E）
- **主分支发布**：手动 `mvn verify -Pe2e` 验证后发布
- **E2E 失败处理**：检查重放 fixtures 契约一致性（R5）、AList 二进制可用性、动态端口分配日志；失败现场保留在 `./data-e2e/` 供诊断（FR-009）

## 引用

- 技术决策：[research.md R2](../research.md#r2-maven-failsafe-plugin-配置与-maven-profile-隔离方案)
- 章程依据：原则 V（测试不可省略）、FR-005
- 测试目录：`src/test/java/top/lldwb/alistmediasync/{integration,e2e}/`
