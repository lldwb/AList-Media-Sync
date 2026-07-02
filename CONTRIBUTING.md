# 贡献指南

感谢参与 AList-Media-Sync！本篇是参与开发前需要了解的工作流约定。项目质量原则以 [`.specify/memory/constitution.md`](./.specify/memory/constitution.md)（章程）为准，本文件仅做引用与流程串联，不重复章程内容。

## 开发环境

请先按 [docs/02-开发环境搭建.md](./docs/02-开发环境搭建.md) 完成本地环境配置：JDK 21、Maven Wrapper、前端 Node 22+，以及 `ALIST_BASE_URL` / `ALIST_TOKEN` 等必填环境变量。

## 分支与提交规范

- 从 `main` 切出特性分支开发，命名建议 `feat/<short-desc>` 或 `fix/<short-desc>`；
- 提交信息遵循 **Conventional Commits** 中文格式（对应章程原则 IV：中文优先）：

```
<type>: <简述>

[可选正文：说明动机与关键变更]
```

常用 `type`：

| type | 用途 | 示例 |
|------|------|------|
| `feat` | 新增功能 | `feat: 添加媒体文件扫描功能` |
| `fix` | 修复缺陷 | `fix: 修复转码并发信号量泄漏问题` |
| `refactor` | 重构（无行为变化） | `refactor: 抽取同步服务目录扫描公共逻辑` |
| `docs` | 文档更新 | `docs: 补充开发环境搭建排障章节` |
| `test` | 测试补充 | `test: 补充存储引擎策略单元测试` |
| `chore` | 构建/工具变更 | `chore: 升级 Vite 至 6.0` |

> 提交信息、代码注释、日志消息均使用简体中文；对外 API 字段名与错误码保持英文。

## Spec Kit 工作流

新功能或较大改动通过 Spec Kit 工作流推进，确保设计有据可查、任务可追踪：

```
/speckit-specify  →  /speckit-plan  →  /speckit-tasks  →  /speckit-implement
   （写规格）        （出设计）         （拆任务）          （执行实现）
```

- 规格与设计产物落在 `specs/<NNN-feature-name>/` 目录；
- 实现阶段按 `tasks.md` 顺序执行，完成后更新 `spec.md` 状态字段（章程原则 VIII）；
- 实现完成后 MUST 同步更新 `README.md`（章程原则 IX）。

完整工作流说明与 AI 协作指令见 [AGENTS.md](./AGENTS.md)。

## 代码审查门禁

提交 Pull Request 前，请对照 [AGENTS.md 的"章程合规检查清单"](./AGENTS.md#章程合规检查清单)逐项自检。清单覆盖分层架构、乐观锁与事务、统一 `ApiResult<T>` 封装、中文规范、测试同步、依赖克制、日志规范、Spec 状态同步、README 更新等 10 项门禁。

## 日志与测试规范

- **日志**：所有重要操作按 DEBUG / INFO / WARN / ERROR 四级输出；API 调用与本地文件操作 MUST 记录输入输出；任务入口 MUST 通过 `TraceContext.runWith(...)` 注入 traceId / module / operation MDC 字段；ERROR 日志 MUST 同时写入 `error.log`；日志与诊断包不得出现密码 / Token / 密钥等敏感原始值。详见 [章程原则 VII](./.specify/memory/constitution.md)。
- **测试**：每次修改 Java 类后 MUST 同步修改或新增对应单元测试；`./mvnw test` 须全部通过。详见 [章程原则 V](./.specify/memory/constitution.md)。

## 其他约定

- 严格遵守分层架构：Controller 不写业务逻辑，Service 承载核心逻辑，Repository 仅做数据持久化，禁止跨层调用（章程原则 I）；
- 新增第三方依赖前评估必要性，遵循 YAGNI（章程原则 VI）；
- 修改某模块前先阅读该模块目录下的 `AGENTS.md`，模块索引见 [AGENTS.md 的"模块 AGENTS.md 索引"](./AGENTS.md#模块-agentsmd-索引)。
