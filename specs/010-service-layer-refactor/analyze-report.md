# 跨制品一致性分析（/speckit-analyze）

**关联**：[spec.md](./spec.md) | [plan.md](./plan.md) | [tasks.md](./tasks.md) | [checklists/review-checklist.md](./checklists/review-checklist.md)

**生成日期**：2026-06-28

**性质**：非破坏性扫描，不修改任何文件，仅指出制品间一致性问题、覆盖缺口、质量风险。

---

## 1. 用户故事 ↔ 功能需求 ↔ 任务 ↔ 检查清单 对齐矩阵

| US | spec FR | tasks 编号 | checklist 编号 | 状态 |
|----|---------|-----------|----------------|------|
| US1 | FR-001 PathUtils | T101、T110~T113 | P1-001、P1-003、P1-004 | ✅ |
| US1 | FR-002 TraceContext.runWith | T102、T120~T124 | P1-005、P1-007 | ✅ |
| US1 | FR-003 删除重复路径方法 | T110~T113 | P1-003、P1-004 | ✅ |
| US1 | FR-004 共享 JsonMapper | T130 | P1-008 | ✅ |
| US1 | FR-011 格式修复 | T140、T141、T142 | P1-009、P1-010、P1-012 | ✅ |
| US1 | FR-014 工具类单测 ≥95% | T103、T104 | P1-002、P1-006 | ✅ |
| US2 | FR-005 拆分 executeSyncTaskInternal ≤50 行 | T220~T226 | P2-002、P2-003 | ✅ |
| US2 | FR-006 SyncContext | T210 | P2-004 | ✅ |
| US2 | FR-013 行为对等测试 | T201~T202、T230~T232 | P2-006~P2-008 | ✅ |
| US3 | FR-007 拆分 doProcess ≤40 行 | T320~T322 | P3-002、P3-003 | ✅ |
| US3 | FR-008 失败状态映射表 | T320、T323 | P3-007、P3-008 | ✅ |
| US3 | FR-009 ParallelTaskCollector | T310、T311、T330、T331 | P3-004~P3-006 | ✅ |
| US3 | FR-010 buildEphemeralSyncTask | T340、T341 | P3-009 | ✅ |
| 跨故事 | FR-012 零行为变更 | T242（手工 C1/C2/C3） | P2-008、P3-010 | ✅ |
| 跨故事 | FR-015 不引入依赖 + 三 PR 拆分 | T150~T153 / T240~T243 / T350~T353 | CR-003 | ✅ |

**结论**：FR ↔ tasks ↔ checklist 三向对齐齐全，无遗漏。

---

## 2. 成功标准（SC）覆盖

| SC | 描述 | 验证位置 | 状态 |
|----|------|----------|------|
| SC-001 | SyncService ≤420 行 | T241 + P2-001 | ✅ |
| SC-002 | TranscodeService ≤560 行 | T351 + P3-001 | ✅ |
| SC-003 | TranscodeFileProcessor ≤400 行；doProcess ≤40 行 | T351 + P3-002、P3-003 | ✅ |
| SC-004 | executeSyncTaskInternal ≤50 行 | T241 + P2-002 | ✅ |
| SC-005 | 路径方法重复从 ≥7 处降至 1 处 | T110~T113 + P1-003、P1-004 | ✅ |
| SC-006 | mvnw test 通过用例数 ≥ 基线 | T000、T151、T240、T350 + CR-006 | ✅ |
| SC-007 | 工具类单测 ≥95% | T103、T104、T311 + P1-002、P1-006、P3-005 | ✅ |
| SC-008 | 章程 10 项检查全部通过 | CR-005 | ✅ |
| SC-009 | 日志关键字段保留 | LG-001~LG-006 | ✅ |
| SC-010 | JsonMapper 实例化次数下降到常量级 | T130 + PF-001 | ✅ |

**结论**：10 项 SC 全部有明确验证路径。

---

## 3. 一致性问题与改进建议

### 3.1 ✅ 已自洽的关键决策

- 路径工具语义基准（SyncService.concatPath）在 spec / clarifications / research / contracts 四处一致
- "三 PR 拆分"在 spec FR-015 / plan PR 路线图 / tasks 阶段划分 / checklist P1/P2/P3 分段四处一致
- 不引入新依赖在 spec FR-015 / plan 技术上下文 / research R-7 / checklist CR-003 四处一致

### 3.2 ⚠️ 轻微歧义（建议在实施时澄清）

**A-1**：`PathUtils.join(dir, "")` 的预期行为在 contracts/PathUtils.md 中标注为"待测试确认或抛异常"。

- 建议在 T103 编写时与团队确认；
- 推荐：返回 `dir`（即认为空 name 是 no-op），避免出现 `/a/` 形式产物。

**A-2**：`TraceContext.runWith` 场景 1（上游已有 traceId）对 `module/operation` 退出后是否恢复原值，contracts/TraceContextRunWith.md 标注"需在测试中固定"。

- 建议在 T104 编写时优先选择"覆盖后不还原"语义（与现有 5 处样板代码一致），并在测试中显式断言。

**A-3**：`SyncContext` 中 `failedFiles` 是可变共享集合，data-model.md 已显式说明只在单任务执行线程内有效。

- 建议在 T210 实施时增加 javadoc 警告，避免被误用。

### 3.3 ⚠️ 测试覆盖缺口

**G-1**：spec.md SC-006 要求"用例数 ≥ 基线"，但**未规定通过率**。

- 风险：若基线本身就有 skip/error，重构后引入新 skip 仍可"满足"标准；
- 建议：在 T000 基线建立时同时记录"Failures: Y, Errors: Z"，PR 门禁要求 Y + Z 不增加。已隐含在 quickstart.md "通过标准"中，建议显式写入 checklist CR-006。

**G-2**：spec.md SC-007 要求"工具类单测行覆盖率 ≥95%"，但项目当前 pom.xml 是否已配置 JaCoCo 未知。

- 风险：若未配置 JaCoCo，则 95% 无法机械验证；
- 建议：在 T103 前先确认 JaCoCo 状态；若未配置，可降级为"穷举契约表格行 + 至少 30 个用例"代偿（research.md R-1 已隐含此降级）。

### 3.4 ⚠️ 章程合规风险

**C-1**：spec.md FR-013 要求"每个被改动的 Service 必须同步补齐行为对等回归测试"，但**未规定补齐到何种粒度**。

- 现状：`SyncServiceTest` 已 450 行 / 24 用例，覆盖较好；`TranscodeServiceTest` 295 行 / 17 用例，覆盖中等；
- 建议：在 T201、T301 实施时显式列出新增用例编号写入 PR 描述，便于审查。

**C-2**：plan.md 章程检查 IX 项 README.md 标注"无影响"。

- 风险：若实际重构产生了影响（如 PathUtils 被前端 API 间接观察到），需重新评估；
- 缓解：checklist DP-004 已锁死"本次重构是内部重构"。

### 3.5 ⚠️ 任务顺序风险

**T-1**：PR 2 / PR 3 都依赖 PR 1 合入，tasks.md 依赖图已正确标注。

**T-2**：PR 2 T201（测试护栏）必须在 T220~T226（拆分）之前完成，tasks.md 阶段划分已隐含；建议在 PR 实施时**严格作为本地 git 提交顺序**遵守。

**T-3**：PR 3 T310（ParallelTaskCollector）与 T320~T322（FileProcessor 拆分）顺序未硬性约束。

- 建议：T310 先合入工具类与单测，再做 T320 拆分，便于 review 分块审视。

---

## 4. 风险登记

| 风险 | 级别 | 缓解 |
|------|------|------|
| 拆分后 `executeSyncTaskInternal` 行为微小偏移导致同步结果不一致 | 高 | T201/T202 测试护栏 + T242 手工 C1/C2/C3 |
| `JsonMapper` 单例复用引发并发问题 | 低 | research R-3 已确认线程安全；运行时由 `./mvnw test` 全量验证 |
| `TraceContext.runWith` 在异常路径下 MDC 泄漏 | 中 | T104 用例 3 明确测试 finally 执行 |
| `ParallelTaskCollector` 超时语义与现状不一致导致任务"假死" | 中 | T311 用例 3（超时归为失败）显式覆盖 |
| 重构期间主分支接入 hotfix 导致冲突 | 中 | quickstart.md 已要求每个 PR 内 rebase 并重跑基线 |

---

## 5. 一致性结论

**总体**：✅ 通过

- 14 项功能需求 ↔ 54 项任务 ↔ 42 项检查清单两两映射完整；
- 10 项成功标准全部有验证路径；
- 4 处轻微歧义、2 处覆盖缺口、3 处顺序风险已识别并标注缓解措施；
- 章程 10 项原则在三 PR 路线图中均有对应门禁。

**建议下一步**：

1. 执行 T000 建立基线后即可启动 PR 1；
2. 在 T103/T104 编写测试时同步消化 A-1/A-2/A-3 歧义并写入用例；
3. PR 1 合入后再并行启动 PR 2 与 PR 3 的测试护栏阶段（T201、T301），互不阻塞。
