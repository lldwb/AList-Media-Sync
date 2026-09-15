# ops/ — 跨模块运维聚合模块

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和后端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

聚合横跨多个业务模块的运维能力：仪表盘统计、过期记录与临时文件清理、诊断包生成，以及对应的 HTTP / MCP 入口。是依赖链的**顶层**模块，**不被任何模块依赖**。

## 作用

- **DashboardService**：仪表板聚合统计，经 `TaskExecutionRepository` / `TranscodeTaskRepository` / `StorageEngineRepository` / `WebhookRuleRepository` 计算活跃同步任务数、待处理转码任务数、24 小时文件处理量与成功率等指标（`@Transactional(readOnly = true)`）
- **CleanupService**：三类清理——每天凌晨 3 点按 `app.retention-days` 删除过期 `TaskExecution` 与 `WebhookEvent`；每 4 小时清理超过 24 小时的孤立转码临时文件；`ApplicationReadyEvent` 启动时清理残留临时文件（按 `app.transcode.temp-suffix` 与 `src-` / `out-` 前缀匹配）
- **DiagnosticService**：只读生成诊断包（日志摘要 + 配置摘要 + 系统信息 + 数据库状态），输出至 `diagnostics/latest`（临时目录构建后原子替换）；不触发同步/转码/Webhook 等业务副作用
- **入口层**：`DashboardController`（`GET /api/dashboard/stats`）、`DiagnosticController`（`POST /api/diagnostics/run`，受 `AuthInterceptor` 保护）、`SystemMcpTools`（`system_dashboard_stats` / `system_run_diagnostics` 两个 MCP 工具，`app.mcp.enabled=true` 时装配）
- **依赖倒置实现方**：`CleanupService` 实现 `common/service/TempFileCleanupTrigger`，为 `transcode/` 的两个入口提供「手动清理临时文件」能力

## 模块关联

- 依赖 **transcode/**：`DashboardService` 经 `TranscodeTaskRepository` 统计转码任务状态分布
- 依赖 **webhook/**：`CleanupService` 经 `WebhookEventRepository` 删除过期事件；`DashboardService` 经 `WebhookRuleRepository` 统计规则总数
- 依赖 **storage/**：`DashboardService` 经 `StorageEngineRepository` 统计存储引擎总数
- 依赖 **execution/**：`TaskExecution` 实体与 `TaskExecutionRepository` 是仪表盘统计与过期清理的数据来源
- 依赖 **common/**：`AppProperties`、`ApiResult`、`DashboardStatsVO`、`DiagnosticResultVO`、`JsonUtils`、`SensitiveDataMasker`、`McpToolResult`、`TempFileCleanupTrigger`（本模块为实现方）
- **不被任何模块依赖**（顶层聚合）；`transcode/` 通过 `common` 的 `TempFileCleanupTrigger` 接口间接调用本模块，不直接 import `ops`
- 本模块的 Service 与入口层原位于 `common/`（`common/service/`、`common/controller/`、`common/mcp/`），因聚合多个业务模块而迁出，避免 `common` 反向依赖业务模块
