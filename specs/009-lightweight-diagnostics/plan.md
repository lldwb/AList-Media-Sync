# 实现计划：轻量诊断系统

**分支**：`009-lightweight-diagnostics` | **日期**：2026-06-26 | **规格**：[spec.md](./spec.md)

**输入**：来自 `/specs/009-lightweight-diagnostics/spec.md` 的功能规格

## 摘要

本功能为 AList-Media-Sync 增加轻量诊断系统，解决排障时 AI 或维护者无法直接看到关键日志、无法快速关联一次任务执行的问题。核心交付包括：

1. **统一 traceId 链路**：所有后端 HTTP 请求、任务执行、同步、转码、Webhook 处理和关键日志都关联 traceId。
2. **请求级 traceId 可见性**：后端所有 HTTP 响应统一返回 `X-Trace-Id` 响应头；如请求已携带 `X-Trace-Id`，在合法时沿用，否则生成新值。
3. **结构化日志与错误日志分流**：通过日志上下文记录模块、操作、traceId 和错误类别，ERROR 级日志单独写入 `logs/error.log`。
4. **一键诊断包**：本地开发、Docker、一体化启动包均提供诊断生成入口，输出 `diagnostics/latest/summary.md` 及配套证据文件。
5. **敏感信息脱敏**：诊断包中的密码、Token、Cookie、Authorization、密钥和疑似凭据统一脱敏。

**技术方法**：复用 Spring Boot 内置 Filter、SLF4J MDC、Logback、现有分层架构与脚本体系；不引入新第三方依赖。诊断能力作为 `common` 基础设施实现，命令入口通过脚本或管理端点触发，保持只读、无业务副作用。

**日志上下文传递**：模块（`module`）和操作（`operation`）字段通过 SLF4J MDC 传递，键名分别为 `mdc.module` 和 `mdc.operation`。业务服务在任务入口通过 `TraceContext.setModuleOperation(String module, String operation)` 设置，Logback pattern 通过 `%X{module}` 和 `%X{operation}` 输出。失败时通过 `TraceContext.setErrorType(String errorType)` 设置错误类别，pattern 通过 `%X{errorType}` 输出。

## 技术上下文

**语言/版本**：Java 21 + TypeScript 5.x（strict 模式）+ Shell/Bat 脚本

**主要依赖**：Spring Boot 4.1.0、Spring MVC、SLF4J + Logback、Spring Data JPA、H2、React 19、Vite 6.x；不新增第三方依赖

**存储**：文件系统日志与诊断目录；H2 数据库只作为只读信息来源，不新增诊断业务表

**测试**：JUnit 5 + Mockito + Spring MockMvc（后端）；脚本执行 smoke test；前端如读取 traceId 响应头则使用现有前端测试方式

**目标平台**：Windows 本地开发、一体化启动包、Linux Docker 单实例部署

**项目类型**：Web 应用（Spring Boot 后端 + React SPA 前端）+ 运维脚本

**性能目标**：
- 诊断包在正常部署环境中 30 秒内生成
- 任意同步、转码或 Webhook 失败可在 2 分钟内通过摘要定位 traceId 和关键错误日志
- 100% 新后端 HTTP 响应包含 `X-Trace-Id`（参见 spec.md FR-013）
- 100% 新任务执行上下文包含非空唯一 traceId

**约束**：
- 不引入日志聚合、链路追踪平台或消息中间件，保持轻量化
- 诊断生成必须只读，不触发同步、转码或 Webhook 业务副作用
- `diagnostics/latest` 表示最新诊断结果，不在本阶段提供长期归档管理
- 所有敏感信息默认保守脱敏
- 所有新增/修改 Java 类必须同步测试

**规模/范围**：
- 后端新增 common 诊断基础设施与请求 trace 过滤器
- 修改日志配置、脚本入口和必要的任务执行入口
- 新增或更新后端测试、脚本验证与 README 后续实现阶段更新

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

### 初始检查（阶段 0 前）

| # | 原则 | 检查项 | 状态 |
|---|------|--------|------|
| I | 分层架构 | trace 过滤器、日志上下文、诊断生成放入 `common` 基础设施；业务模块仅接收/传递 traceId，不跨层调用 | ✅ 通过 |
| II | 数据完整性 | 不新增写业务数据流程；诊断读取数据库信息时只读；日志与诊断文件写入不影响业务事务 | ✅ 通过 |
| III | RESTful API 契约 | `X-Trace-Id` 通过响应头暴露，避免破坏现有 `ApiResult<T>` 响应体；新增诊断端点如存在仍使用 `ApiResult<T>` | ✅ 通过 |
| IV | 中文优先 | 文档、日志、注释使用简体中文；HTTP 头字段保持英文标准命名 | ✅ 通过 |
| V | 测试覆盖 | 规划包含 Filter、脱敏、诊断生成、脚本入口和 Controller/API 测试，tasks.md 中已规划 16 个专项测试任务（T008–T009, T017–T022, T034–T040, T050–T053） | ✅ 通过 |
| VI | YAGNI | 不引入 OpenTelemetry、ELK、Zipkin 等平台；使用 MDC + Logback + 脚本满足当前需求 | ✅ 通过 |
| VII | 日志规范 | 统一日志上下文、ERROR 分流、敏感信息不得入日志或诊断包 | ✅ 通过 |
| VIII | 规格状态 | spec.md 状态已更新为”已排期” | ✅ 通过 |
| IX | 文档同步 | 实现完成后更新 README.md（已在 tasks.md T061 中规划） | ⏳ 实现阶段执行 |
| X | 章程同步 | 不涉及章程修订 | ✅ 不适用 |

### 设计后重新检查（阶段 1 后）

| # | 原则 | 检查项 | 状态 |
|---|------|--------|------|
| I | 分层架构 | `TraceIdFilter`、`TraceContext`、`DiagnosticService`、`SensitiveDataMasker`、诊断 DTO 位于 `common`；Controller 仅触发与返回结果 | ✅ 通过 |
| II | 数据完整性 | 诊断服务不修改业务实体；`diagnostics/latest` 原子刷新，失败时保留可读错误说明 | ✅ 通过 |
| III | RESTful API 契约 | 响应头 `X-Trace-Id` 为非破坏性契约；诊断端点和错误响应保持 `ApiResult<T>` | ✅ 通过 |
| V | 测试覆盖 | 已规划 MockMvc 验证响应头、MDC 清理、脱敏规则、诊断摘要生成和脚本入口 | ✅ 通过 |
| VI | YAGNI | 未新增第三方依赖，所有能力使用项目现有技术栈 | ✅ 通过 |
| VII | 日志规范 | Logback 增加 error appender 和 traceId pattern；诊断包默认脱敏 | ✅ 通过 |

## 项目结构

### 文档（本功能）

```text
specs/009-lightweight-diagnostics/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── http-trace-contract.md
│   ├── diagnostics-command-contract.md
│   └── diagnostics-output-contract.md
└── tasks.md             # /speckit-tasks 输出
```

### 源代码（仓库根目录）

```text
src/main/java/top/lldwb/alistmediasync/
├── common/
│   ├── config/
│   │   └── TraceIdFilter.java              # 新增：请求 traceId 生成、校验、响应头写入、MDC 生命周期
│   ├── controller/
│   │   └── DiagnosticController.java       # 新增：受认证保护的一键诊断触发/下载入口（如实现端点）
│   ├── dto/
│   │   ├── DiagnosticResultVO.java         # 新增：诊断生成结果视图
│   │   └── DiagnosticSummaryVO.java        # 新增：摘要数据视图（如需 API 返回）
│   ├── service/
│   │   └── DiagnosticService.java          # 新增：诊断包生成编排，只读收集日志/配置/运行状态
│   └── util/
│       ├── TraceContext.java               # 新增：生成/获取/校验 traceId
│       └── SensitiveDataMasker.java        # 新增：敏感字段和值脱敏

src/main/resources/
├── application.yaml                        # 修改：日志文件位置、pattern 增加 traceId
└── logback-spring.xml                      # 新增或修改：ERROR 单独写入 logs/error.log

scripts/
├── diagnose.sh                             # 新增：Linux/Docker 一键诊断入口
├── diagnose.bat                            # 新增：Windows 一键诊断入口
├── start.sh                                # 修改：提示诊断入口或传递诊断目录配置
└── start.bat                               # 修改：提示诊断入口或传递诊断目录配置

Dockerfile / docker-compose.yml             # 如需要：确保 logs/ 与 diagnostics/ 可访问或挂载

src/test/java/top/lldwb/alistmediasync/
└── common/
    ├── config/TraceIdFilterTest.java
    ├── service/DiagnosticServiceTest.java
    └── util/SensitiveDataMaskerTest.java
```

**结构决策**：诊断能力属于跨模块基础设施，统一放入 `common`；业务模块只在任务入口、状态变化和错误处理处传递 traceId 并输出日志，避免将诊断逻辑散落到同步、转码、Webhook 的业务服务中。

## 复杂性追踪

> **仅在章程检查有必须证明合理性的违规时填充**

无章程违规；不新增第三方依赖。