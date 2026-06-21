# 实现计划：转码与同步模块优化及实时通信改造

**分支**：`008-transcode-sync-optimization` | **日期**：2026-06-21 | **规格**：[spec.md](./spec.md)

**输入**：来自 `/specs/008-transcode-sync-optimization/spec.md` 的功能规格

## 摘要

本功能对转码模块、同步模块和公共基础设施进行三项核心优化：

1. **转码模块 UX 优化**：源目录转码选项文案修正（"原"→"源"）、勾选后隐藏无关表单字段、输出路径计算修正、列表仅显示文件、批量操作（清理失败/成功任务、重试所有失败文件）
2. **同步模块同引擎复制优化**：同存储引擎间同步直接调用引擎的 copy 方法（AList `/api/fs/copy`、本地 `Files.copy`），避免低效的下载→上传流程
3. **WebSocket 实时推送替代轮询**：引入 Spring WebSocket 支持，所有列表页面从 5 秒 HTTP 轮询迁移至 WebSocket 增量推送，新增自动重试机制（指数退避，RetryableException 标记接口）

**技术方法**：WebSocket 使用原始 WebSocket（非 STOMP，遵循 YAGNI），消息格式为 JSON 增量变更；自动重试通过 `ScheduledExecutorService` 调度不占用工作线程；同引擎复制通过 `StorageEngineStrategy.copyFile()` 策略方法实现。

## 技术上下文

**语言/版本**：Java 21（虚拟线程）+ TypeScript 5.x（strict 模式）

**主要依赖**：Spring Boot 4.1.0、Spring Data JPA、H2、JAVE2（FFmpeg）、React 19、Vite 6.x、Tailwind CSS 4.x、React Router v7；**新增**：`spring-boot-starter-websocket`

**存储**：H2 文件数据库（单实例，WebSocket 会话管理在进程内）

**测试**：JUnit 5 + Mockito + Spring MockMvc（后端）；Vitest（前端）

**目标平台**：Windows 开发环境 + Linux Docker 生产环境

**项目类型**：Web 应用（Spring Boot 后端 + React SPA 前端）

**性能目标**：
- 同引擎同步性能提升 ≥ 50%（SC-006）
- 列表页面 30 秒内 HTTP 请求减少 ≥ 80%（SC-007）
- WebSocket 断线 5 秒内自动重连（SC-008）
- 瞬时故障自动重试 3 次内恢复成功率 ≥ 80%（SC-009）

**约束**：
- 单实例部署（WebSocket 进程内管理，无需 Redis Pub/Sub）
- WebSocket 并发连接上限可配置（默认 50）
- 自动重试最大次数可配置（默认 3），指数退避（1s→2s→4s→...→60s）
- 遵循章程所有不可协商原则（分层架构、数据完整性、API 契约、中文优先、测试覆盖、YAGNI、日志规范）

**规模/范围**（粗略估计，实际以 tasks.md 为准）：
- 后端：新增 ~10 个类/接口，修改 ~15 个现有类
- 前端：新增 ~2 个文件，修改 ~8 个现有文件，删除 1 个文件
- 新增 1 个 Maven 依赖（spring-boot-starter-websocket）
- 总计 78 个任务（T001~T078），含 12 个测试任务

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

### 初始检查（阶段 0 前）

| # | 原则 | 检查项 | 状态 |
|---|------|--------|------|
| I | 分层架构 | WebSocket 相关类放入 `common/`（通用基础设施），批量操作在 `transcode/controller/` 和 `transcode/repository/`，copyFile 在 `storage/service/engine/` | ✅ 通过 |
| II | 数据完整性 | TranscodeTask 新增 `retryCount` 字段需 `@Column`；批量删除需 `@Transactional`；WebSocket 消息不涉及持久化 | ✅ 通过 |
| III | API 契约 | 新增 REST 端点使用 `ApiResult<T>` 封装，DTO 不暴露 Entity | ✅ 通过 |
| IV | 中文优先 | 所有注释、日志、提交信息使用简体中文 | ✅ 通过 |
| V | 测试覆盖 | 所有新增/修改的 Java 类需同步更新单元测试 | ⚠️ 需在实现阶段执行 |
| VI | YAGNI | WebSocket 使用原始 WebSocket（非 STOMP），不引入消息中间件；`spring-boot-starter-websocket` 是必要依赖 | ✅ 通过 |
| VII | 日志规范 | WebSocket 连接/断开、批量操作、自动重试、copyFile 均需按四级日志规范输出 | ✅ 通过 |
| VIII | 规格状态 | 计划完成后更新 spec.md 状态为"已计划" | ⏳ 待执行 |
| IX | 文档同步 | 实现完成后更新 README.md | ⏳ 待实现阶段 |
| X | 章程同步 | 不涉及章程修订 | ✅ 不适用 |

### 设计后重新检查（阶段 1 后）

| # | 原则 | 检查项 | 状态 |
|---|------|--------|------|
| I | 分层架构 | WebSocket 配置在 common/config，会话管理在 common/service，消息在 common/dto；copyFile 在 storage/service/engine；批量操作在 transcode | ✅ 通过 |
| II | 数据完整性 | TranscodeTask.retryCount 默认 0，`@Column(nullable=false)`；批量删除使用 `@Transactional` + `@Modifying` | ✅ 通过 |
| III | API 契约 | 新增端点均定义 DTO，响应使用 `ApiResult<T>` | ✅ 通过 |
| V | 测试覆盖 | 已规划测试文件（见 quickstart.md） | ✅ 通过 |
| VI | YAGNI | 未引入额外依赖，仅 `spring-boot-starter-websocket`（Spring Boot 内置） | ✅ 通过 |

## 项目结构

### 文档（本功能）

```text
specs/008-transcode-sync-optimization/
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── research.md          # 阶段 0 输出
├── data-model.md        # 阶段 1 输出
├── quickstart.md        # 阶段 1 输出
├── contracts/           # 阶段 1 输出
│   ├── websocket-messages.md    # WebSocket 消息契约
│   └── rest-api.md              # 新增 REST API 契约
└── tasks.md             # 阶段 2 输出（/speckit-tasks 命令 — 非 /speckit-plan 创建）
```

### 源代码（仓库根目录）

```text
# 后端（src/main/java/top/lldwb/alistmediasync/）
common/
├── config/
│   └── WebSocketConfig.java              # 新增：WebSocket 端点注册 + 握手拦截器
├── dto/
│   └── WsMessage.java                    # 新增：WebSocket 消息 DTO
├── enums/
│   └── MessageType.java                  # 新增：消息类型枚举
├── service/
│   └── WsSessionManager.java             # 新增：WebSocket 会话管理 + 消息广播
├── interceptor/
│   └── WebSocketAuthInterceptor.java     # 新增：WebSocket 握手认证拦截器
└── exception/
    └── RetryableException.java           # 新增：可重试异常标记接口

storage/
└── service/
    └── engine/
        ├── StorageEngineStrategy.java    # 修改：新增 copyFile() 默认方法
        ├── AListStorageStrategy.java     # 修改：实现 copyFile()（调用 /api/fs/copy）
        └── LocalStorageStrategy.java     # 修改：实现 copyFile()（Files.copy）

sync/
├── entity/
│   └── SyncTask.java                     # 修改：新增 transcodeTargetFormat、transcodeBitrate 字段
├── service/
│   └── SyncService.java                  # 修改：同引擎检测 + copyFile 调用 + 自动重试
└── dto/sync/
    └── SyncTaskCreateDTO.java            # 修改：新增转码配置字段

transcode/
├── controller/
│   └── TranscodeTaskController.java      # 修改：新增批量操作端点
├── dto/transcode/
│   ├── TranscodeTaskCreateDTO.java       # 修改：sameDirectoryTranscode→sourceDirectoryTranscode，targetEngineId 条件校验
│   └── TranscodeTaskVO.java              # 修改：新增 retryCount 字段
├── entity/
│   └── TranscodeTask.java                # 修改：新增 retryCount 字段
├── repository/
│   └── TranscodeTaskRepository.java      # 修改：新增按状态批量操作方法
└── service/
    ├── TranscodeService.java             # 修改：sourceDirectoryTranscode 路径计算修正 + 自动重试
    └── TranscodeFileProcessor.java       # 修改：按步骤精确设置失败状态 + 自动重试逻辑

# 前端（src/main/frontend/src/）
hooks/
├── useWebSocket.ts                       # 新增：WebSocket 连接管理 Hook
└── usePolling.ts                         # 删除：不再需要轮询

api/
└── client.ts                             # 修改：新增批量操作 API 方法 + WebSocket 消息类型

types/
└── index.ts                              # 修改：新增 WsMessage、MessageType 等类型定义

pages/
├── TranscodeTaskListPage.tsx             # 修改：新增批量操作按钮 + 迁移至 WebSocket
├── TranscodeTaskForm.tsx                 # 修改：文案修正 + 条件隐藏字段
├── SyncTaskListPage.tsx                  # 修改：迁移至 WebSocket
├── SyncTaskDetailPage.tsx                # 修改：迁移至 WebSocket
├── WebhookEventListPage.tsx              # 修改：迁移至 WebSocket
└── DashboardPage.tsx                     # 修改：迁移至 WebSocket（保留 REST 初始加载）
```

**结构决策**：遵循 007 定义的分包原则——WebSocket 相关类放入 `common/`（通用基础设施），批量操作在 `transcode/`，copyFile 在 `storage/service/engine/`，前端 useWebSocket Hook 在原有 hooks 目录。

## 复杂性追踪

> **仅在章程检查有必须证明合理性的违规时填充**

| 违规 | 为什么需要 | 被拒绝的更简单替代方案及原因 |
|------|-----------|------------------------|
| 新增 `spring-boot-starter-websocket` 依赖 | WebSocket 是替代 HTTP 轮询的必要基础设施，Spring Boot 内置 starter 无需额外第三方库 | SSE（Server-Sent Events）—仅支持单向推送，无法满足前端主动发送消息的需求；STOMP—功能过剩，原始 WebSocket 已满足需求（YAGNI） |
| 新增 `RetryableException` 标记接口 | 区分瞬时故障和业务逻辑错误是自动重试的核心前提，标记接口是最简方案 | 异常分类枚举—增加维护成本且与 Java 异常体系重复；白名单/黑名单配置—运行时类型检查效率低且易遗漏 |
