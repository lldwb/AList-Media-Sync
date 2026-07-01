# 数据模型：文档体系优化

**分支**：`011-docs-system-optimization` | **日期**：2026-07-02 | **关联**：[plan.md](./plan.md)

本功能为文档体系优化，不涉及业务实体与持久化存储。本数据模型描述"文档元数据"——即文档本身作为制品的结构化属性，用于规范文档命名、职责与引用关系。

## 文档制品实体

### 文档（Document）

| 字段 | 类型 | 说明 |
|------|------|------|
| `path` | 路径 | 文档在仓库中的相对路径（如 `docs/04-配置说明.md`） |
| `tier` | 枚举 | 所属层级：`root`（根级）/ `docs`（主题）/ `architecture`（模块）/ `operations`（运维） |
| `responsibility` | 字符串 | 单一职责描述（如"配置项 SSOT"） |
| `is_ssot` | 布尔 | 是否为某类信息的单一权威来源 |
| `is_derived` | 布尔 | 是否为派生产物（如 docs/05 由注解生成） |
| `source_of_truth` | 路径 | 若 is_derived=true，指向源真值位置（如 Controller 注解） |
| `frozen` | 布尔 | 是否冻结不可修改（specs/ 与 md/ 为 true） |

### 文档清单（本功能产出）

| path | tier | responsibility | is_ssot | is_derived | source_of_truth | frozen |
|------|------|----------------|---------|------------|-----------------|--------|
| `README.md` | root | 项目入口 + 导航 | false | false | — | false |
| `CHANGELOG.md` | root | 版本变更日志 | true（变更历史） | false | — | false |
| `AGENTS.md` | root | AI 协作指令 | false | false | — | false |
| `CONTRIBUTING.md` | root | 人类贡献者参与入口 | false | false | — | false |
| `docs/01-项目概述.md` | docs | 项目背景与核心功能 | false | false | — | false |
| `docs/02-开发环境搭建.md` | docs | 环境搭建指南 | false | false | — | false |
| `docs/03-架构设计.md` | docs | 分层架构与核心类职责 | false | false | — | false |
| `docs/04-配置说明.md` | docs | app.* 配置项与环境变量映射 | true（配置说明） | false | AppProperties.java | false |
| `docs/05-API接口文档.md` | docs | API 接口清单 | false | true | Controller/DTO 注解 | false |
| `docs/06-运维部署.md` | docs | 部署与运维 | false | false | — | false |
| `docs/architecture/模块-*.md` | architecture | 各模块职责边界与核心类 | false | false | — | false |
| `docs/architecture/交叉关注点.md` | architecture | 认证/加密/虚拟线程/诊断 | false | false | — | false |
| `docs/operations/环境变量清单.md` | operations | 全量环境变量 | true（环境变量） | false | application.yaml/.env | false |
| `docs/operations/故障排查与日志.md` | operations | 排障指南 | false | false | — | false |
| `specs/001..010/*` | — | 历史功能设计档案 | false | false | — | **true** |
| `md/**` | — | 外部对接系统 API | false | false | — | **true** |

## SSOT（单一权威来源）映射关系

为避免信息散落，每类信息明确一个权威来源，其他位置仅引用：

| 信息类别 | 代码层 SSOT | 运行时 SSOT | 人类文档 SSOT |
|---------|------------|------------|--------------|
| `app.*` 配置项定义 | `AppProperties.java` | `application.yaml` | `docs/04-配置说明.md` |
| 非 `app.*` 环境变量 | `Dockerfile` / `.env` | 环境变量注入 | `docs/operations/环境变量清单.md` |
| API 端点契约 | Controller 方法签名 | `/v3/api-docs` 运行时反射 | `docs/05-API接口文档.md`（派生） |
| API 响应结构 | `ApiResult<T>` / DTO | 运行时序列化 | `docs/05-API接口文档.md`（派生） |
| 版本变更历史 | git log | — | `CHANGELOG.md` |
| 模块职责边界 | 源码包结构 | — | `docs/architecture/模块-*.md` |

## 状态转换：文档生命周期

```text
[新建] → [草拟] → [评审] → [发布] → [同步更新（每次相关代码变更）]
                                            ↓
                                      [过时] → [归档至 specs/ 或删除]
```

- **派生文档**（docs/05）：生命周期由生成命令驱动，不经历"草拟/评审"，每次生成即"发布"
- **冻结文档**（specs/、md/）：不进入"同步更新"，保持发布时状态

## 验证规则

- **VR-001**：所有 `is_ssot=true` 的文档，其内容 MUST 与代码层 SSOT 一致（docs/04 ↔ AppProperties.java，docs/operations/环境变量清单 ↔ application.yaml/.env）
- **VR-002**：所有 `is_derived=true` 的文档，重新生成后 diff MUST 为空（SC-004）
- **VR-003**：所有 `frozen=true` 的文档，本次变更后文件数与内容哈希 MUST 不变（SC-007）
- **VR-004**：docs/ 间相对链接与指向 specs/ 的链接 MUST 无死链
