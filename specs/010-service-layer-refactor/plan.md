# 实现计划：Service 层重复代码与超长方法重构

**分支**：`010-service-layer-refactor` | **日期**：2026-06-28 | **规格**：[spec.md](./spec.md)

**输入**：来自 `/specs/010-service-layer-refactor/spec.md` 的功能规格

## 摘要

通过抽取通用工具类（`PathUtils` / `TraceContext.runWith` / `ParallelTaskCollector`）与共享 `JsonMapper`，
并对 `SyncService.executeSyncTaskInternal`（224 行）、`TranscodeFileProcessor.doProcess`（128 行）
两个核心超长方法进行函数级拆分，零行为变更地消除 Service 层中至少 200 行重复代码与 4-5 层嵌套。
分 3 个 PR 提交，每个 PR 通过 `./mvnw test` 全量回归 + 章程合规清单作为门禁。

## 技术上下文

**语言/版本**：Java 21（虚拟线程开启），无需升级

**主要依赖**：Spring Boot 4.1.0、Spring Data JPA、Hibernate、tools.jackson、JAVE2 3.5.0 — 全部保持现状

**新增依赖**：**无**（章程原则 VI）

**存储**：H2 文件模式（重构不涉及）

**测试**：JUnit 5 + Mockito（已有），新增 JaCoCo 覆盖率校验（如未引入则手动通过 `./mvnw test` 验证用例数 ≥ 基线）

**目标平台**：JDK 21+（本地、Docker、一体化启动包三种部署均不受影响）

**项目类型**：Web 服务（Spring Boot）

**性能目标**：

- 重构后 `JsonMapper` 单例复用，`toJson` 单次调用耗时下降至 ≤ 当前的 50%（消除 builder.build() 开销）；
- 其他路径无性能预期变化（拆分方法不引入额外对象分配）。

**约束**：

- 零行为变更（FR-012），不修改任何对外契约；
- 所有改动文件必须同步更新测试（章程原则 V）；
- 中文注释、日志、提交信息（章程原则 IV）；
- 日志四级分级（章程原则 VII），且关键字段保留。

**规模/范围**：

- 修改主源代码 ~7 个文件、新增 3 个工具类文件（`PathUtils`、`ParallelTaskCollector`、可选 `JsonUtil`）；
- 新增/更新测试 ~6 个文件；
- 净行数预计 **-180**（删除 ~280 行重复 + 拆分 / 新增 ~100 行）。

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

| # | 章程原则 | 状态 | 备注 |
|---|---------|------|------|
| I | 分层架构合规 | ✅ | 工具类放 `common/util/`，不跨 Controller→Service→Repository |
| II | `@Version` 乐观锁 + `@Transactional` | ✅ | 不改 Entity，不改事务边界；TranscodeFileProcessor 已存在的 reloadTask 模式保留 |
| III | 统一 `ApiResult<T>` + DTO 脱敏 | ✅ | 不涉及 |
| IV | 中文优先 | ✅ | 全部新代码注释/日志/提交信息使用简体中文 |
| V | Java 类变更同步单测 | ✅（强约束） | FR-013、FR-014 已明确 |
| VI | 不引入不必要依赖 | ✅ | FR-015 |
| VII | 日志四级 + API/文件操作必有日志 | ✅ | SC-009 锁死"不削减原日志" |
| VIII | spec.md 状态字段同步 | ✅ | 本 PR 完成时由 `/speckit-implement` 更新 |
| IX | README.md 更新 | ⚠️ | 仅在变更影响开发者使用方式时更新；本次重构是纯内部重构，README 无需变更，但需在 PR 中显式标注"无 README 影响" |
| X | AGENTS.md 同步 | ⚠️ | 若新增 `common/util/PathUtils` 与 `ParallelTaskCollector`，需在 `common/AGENTS.md` 索引中追加一行 |

**结论**：通过门禁。X 项需在 PR 1 中同步更新 `common/AGENTS.md`。

## 项目结构

### 文档（本功能）

```text
specs/010-service-layer-refactor/
├── plan.md                  # 本文件
├── spec.md                  # 功能规格
├── clarifications.md        # 澄清问答
├── research.md              # 阶段 0：行为对等基线、技术抉择
├── data-model.md            # 阶段 1：重构产物的"实体"清单
├── quickstart.md            # 阶段 1：本地验证步骤
├── contracts/               # 阶段 1：工具类公共 API 契约
│   ├── PathUtils.md
│   ├── ParallelTaskCollector.md
│   └── TraceContextRunWith.md
├── checklists/              # /speckit-checklist 输出
│   └── review-checklist.md
└── tasks.md                 # /speckit-tasks 输出
```

### 源代码（仓库根目录）

```text
src/main/java/top/lldwb/alistmediasync/
├── common/util/
│   ├── PathUtils.java                  # 新增（FR-001）
│   ├── ParallelTaskCollector.java      # 新增（FR-009）
│   ├── JsonUtil.java                   # 新增（FR-004，可选；亦可直接复用注入的 JsonMapper）
│   └── TraceContext.java               # 修改（新增 runWith，FR-002）
├── sync/service/
│   ├── SyncService.java                # 大改：拆分 executeSyncTaskInternal（FR-005、FR-006）
│   └── ScheduleService.java            # 小改：使用 TraceContext.runWith
├── transcode/service/
│   ├── TranscodeService.java           # 中改：删除重复工具方法 + 使用 ParallelTaskCollector
│   └── TranscodeFileProcessor.java     # 大改：拆分 doProcess + 失败状态映射表（FR-007、FR-008）
└── webhook/service/
    └── WebhookService.java             # 中改：提取 buildEphemeralSyncTask（FR-010）+ 格式修复（FR-011）

src/test/java/top/lldwb/alistmediasync/
├── common/util/
│   ├── PathUtilsTest.java                  # 新增
│   ├── ParallelTaskCollectorTest.java      # 新增
│   ├── TraceContextRunWithTest.java        # 新增（合并入 TraceContextTest 也可）
│   └── JsonUtilTest.java                   # 可选
├── sync/service/
│   └── SyncServiceTest.java                # 补齐：SyncMode × ConflictStrategy 矩阵
├── transcode/service/
│   ├── TranscodeFileProcessorTest.java     # 补齐：失败状态映射、步骤拆分
│   └── TranscodeServiceTest.java           # 补齐：并行 collector 集成
└── webhook/service/
    └── WebhookServiceTest.java             # 补齐：buildEphemeralSyncTask 单测
```

## 阶段 0：研究

详见 [research.md](./research.md)。主要决策：

1. **行为对等基线**：在重构前用 `./mvnw test -Dsurefire.printSummary=true > baseline-tests.log` 生成基线快照，存入 `quickstart-results.md`。
2. **JsonMapper 线程安全**：依据 Jackson 文档，`JsonMapper`（`ObjectMapper` 子类）在不修改 config 的前提下线程安全，可作为单例 Bean 注入。
3. **路径工具基准**：以 `SyncService.concatPath` 为准（去 `name` 前导斜杠）。
4. **状态机映射**：失败状态映射用 `Map.of`（不可变），保留 `validateTransition` 调用以不绕过状态机。
5. **超时常量**：`ParallelTaskCollector` 接受调用方传入超时（默认 10 分钟，与现状一致）。

## 阶段 1：设计

详见：

- [data-model.md](./data-model.md) — 重构产物的"实体"清单
- [contracts/PathUtils.md](./contracts/PathUtils.md)
- [contracts/ParallelTaskCollector.md](./contracts/ParallelTaskCollector.md)
- [contracts/TraceContextRunWith.md](./contracts/TraceContextRunWith.md)
- [quickstart.md](./quickstart.md) — 本地验证步骤

## 阶段 2：任务拆解

由 `/speckit-tasks` 命令生成，输出 [tasks.md](./tasks.md)。

## PR 路线图

| PR | 范围 | 风险 | 工作量 | 关键产物 |
|----|------|------|--------|----------|
| **PR 1** | `PathUtils` + `TraceContext.runWith` + 共享 JsonMapper + 格式修复（FR-001~004、FR-011） | 低 | ~1 天 | 工具类 + 单测 + 调用方替换 |
| **PR 2** | `SyncService.executeSyncTaskInternal` 拆分（FR-005、FR-006） | 中 | ~2 天 | 6 个新私有方法 + `SyncContext` record + 测试补齐 |
| **PR 3** | `TranscodeFileProcessor.doProcess` 拆分 + `ParallelTaskCollector` + Webhook 收尾（FR-007~010） | 中 | ~2 天 | 工具类 + 失败状态映射 + 测试补齐 |

每个 PR 的 Definition of Done：

1. `./mvnw test` 通过用例数 ≥ 基线；
2. 章程 10 项检查表已勾选；
3. 受影响 Service 的"行为对等回归测试"已补齐；
4. 提交信息使用简体中文，遵循 conventional commits（如 `refactor(sync): 拆分 executeSyncTaskInternal`）。
