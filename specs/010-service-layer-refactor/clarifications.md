# 澄清问答记录

**关联规格**：[spec.md](./spec.md)

**会话日期**：2026-06-28

下表为 `/speckit-clarify` 流程中针对 spec.md 中初始空白的高针对性问答，结果已写回 spec.md 的"澄清"小节。

| # | 问题 | 答案（用户决策） | 已落地位置 |
|---|------|----------------|-----------|
| 1 | 路径工具方法的语义基准应以哪个现行实现为准？ | 以 `SyncService.concatPath`（处理 `name` 前导斜杠）为基准 | FR-001 / FR-003 |
| 2 | 是否允许引入第三方依赖（Guava / Apache Commons IO）以提供路径工具？ | 不允许，仅使用 JDK 21 内置 API（章程原则 VI） | FR-015 |
| 3 | 拆分 `executeSyncTaskInternal` 是否需要新建子服务类（如 `SyncPipelineService`）？ | 不新建类，仅拆为同类私有方法 + 内部 `SyncContext` record | FR-005、FR-006 |
| 4 | 是否需要等待"Service 层测试覆盖率缺口"问题先行解决再启动重构？ | 不等待，但每个被改动的 Service 必须同步补齐"行为对等回归测试" | FR-013 |
| 5 | 一个 PR 完成 vs 多个 PR？每个 PR 的边界？ | 拆为 3 个 PR：① 工具类抽取与卫生清理 ② `SyncService` 拆分 ③ 转码与 Webhook 收尾 | FR-015、plan.md 路线图 |

## 补充说明

### 关于"行为对等"的定义

- 同一输入（任务参数、引擎配置、源目录文件集合）下，重构前后产生的 `TaskExecution` 关键字段（status / totalFiles / successFiles / failedFiles / failureDetails）必须一致；
- WebSocket 推送的 message type 与 payload 字段集合必须一致；
- 日志关键字段（任务名、路径、错误信息）必须保留；级别可微调（如把同一行 DEBUG 提到 INFO 不属于变更，反之亦然不允许）；
- 数据库写入字段集与默认值不得改变。

### 关于"风险护栏"

- 阶段 0 必须先建立 `./mvnw test` 通过基线（输出文件名与时间戳记录在 `quickstart-results.md`）；
- 每个 PR 合入主分支前，必须重新运行测试基线并对比；
- 任何无法用现有测试覆盖的分支，必须在该 PR 内补齐测试用例。

### 未确认事项（None）

无遗留未确认问题。所有 spec 中的 [需要澄清] 标记已清空。
