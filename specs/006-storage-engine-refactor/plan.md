# 实现计划：存储引擎重构与体验优化

**分支**：`006-storage-engine-refactor` | **日期**：2026-06-20 | **规格**：[spec.md](./spec.md)

**输入**：来自 `specs/006-storage-engine-refactor/spec.md` 的功能规格

## 摘要

本次重构的核心是将存储引擎从 AList 专属实现抽象为策略模式，支持 AList 远程存储和本地文件系统两种引擎类型。同时优化转码流程为三步模式（下载→转码→上传），采用 7 状态模型提升可恢复性。配套改进包括：Webhook 规则路径显示优化、树状目录浏览组件、Cron 图形化配置、文案统一替换。

## 技术上下文

**语言/版本**：Java 21（LTS，虚拟线程已启用）

**主要依赖**：Spring Boot 4.1.0（Spring Framework 7.x）、Spring Data JPA、JAVE2 3.5.0、Lombok、Jakarta Validation

**存储**：H2 嵌入式文件数据库（`jdbc:h2:file:./data/alist_media_sync`），Spring Data JPA + Hibernate

**测试**：JUnit 5（Spring Boot 内置）、@WebMvcTest、@DataJpaTest、Mockito

**目标平台**：Linux 服务器（Docker 部署）、Windows 开发环境

**项目类型**：Web 应用（Spring Boot 后端 + React 前端）

**性能目标**：转码并发度 32（Semaphore 控制）、Webhook 异步处理（虚拟线程）、目录浏览实时加载

**约束**：单实例部署、H2 嵌入式数据库（无分布式）、BCrypt 认证（无完整 Spring Security）、前端纯 Tailwind CSS 手写组件（无第三方 UI 库）

**规模/范围**：6 个核心实体、7 个 Controller、约 15 个 Service、前端约 10 个页面

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

| 章程原则 | 合规状态 | 说明 |
|---------|---------|------|
| I. 分层架构 | ✅ 通过 | 策略模式在 Service 层实现（`service/engine/` 包），Controller 仅调 Service，Repository 使用 JPA 接口 |
| II. 数据完整性 | ✅ 通过 | StorageEngine/TranscodeTask/WebhookRule 均有 @Version 乐观锁；转码 7 状态中间状态持久化到数据库；@Transactional 管理写操作 |
| III. RESTful API | ✅ 通过 | 新增目录浏览端点 `GET /api/storage-engines/{id}/directories` 遵循 RESTful 风格；所有变更使用 DTO 封装；ApiResult 统一响应 |
| IV. 中文优先 | ✅ 通过 | 所有代码注释、文档、提交信息使用简体中文 |
| V. 测试不可省略 | ✅ 通过 | 设计中已规划：策略模式接口和实现类需同步编写单元测试（Mock 依赖）；转码状态转换表需测试覆盖；Controller 需 @WebMvcTest |
| VI. 简洁至上 | ✅ 通过 | 策略模式引入接口抽象（已有第二个实现：本地路径引擎，满足"有第二个具体实现时引入"原则）；不引入新第三方依赖；JAVE2 codec=null 复用 FFmpeg 内置能力 |
| VII. 文档同步 | ✅ 通过 | 实现完成后更新 README.md |

## 项目结构

### 文档（本功能）

```text
specs/006-storage-engine-refactor/
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── research.md          # 阶段 0 输出（/speckit-plan 命令）
├── data-model.md        # 阶段 1 输出（/speckit-plan 命令）
├── quickstart.md        # 阶段 1 输出（/speckit-plan 命令）
├── contracts/           # 阶段 1 输出（/speckit-plan 命令）
└── tasks.md             # 阶段 2 输出（/speckit-tasks 命令 — 非 /speckit-plan 创建）
```

### 源代码（仓库根目录）

```text
src/main/java/top/lldwb/alistmediasync/
├── client/
│   └── AListClient.java                     # AList REST API 客户端（重构为策略实现之一）
├── config/
│   ├── AppProperties.java                   # 应用配置属性
│   ├── AsyncConfig.java                     # 异步线程池
│   ├── GlobalExceptionHandler.java          # 全局异常处理
│   ├── PasswordEncryptionPostProcessor.java # 密码加密
│   ├── RestClientConfig.java                # RestClient 配置
│   └── WebMvcConfig.java                    # CORS / 拦截器
├── controller/
│   ├── DashboardController.java
│   ├── StorageEngineController.java         # 存储引擎 CRUD + 目录浏览接口
│   ├── SyncTaskController.java
│   ├── TranscodeTaskController.java         # 转码任务 CRUD + 重试
│   ├── WebhookController.java               # 录播姬 Webhook 入口
│   ├── WebhookEventController.java
│   └── WebhookRuleController.java           # Webhook 规则 CRUD
├── dto/
│   ├── ApiResult.java                       # 统一 API 响应
│   ├── storage/                             # 存储引擎 DTO
│   ├── sync/                                # 同步任务 DTO
│   ├── transcode/                           # 转码任务 DTO
│   └── webhook/                             # Webhook DTO
├── entity/
│   ├── CryptoConverter.java                 # AES-256-GCM 加密转换器
│   ├── StorageEngine.java                   # 存储引擎实体（新增 type 字段）
│   ├── SyncTask.java
│   ├── TaskExecution.java
│   ├── TranscodeTask.java                   # 转码任务实体（新增 8 状态）
│   ├── WebhookEvent.java
│   └── WebhookRule.java                     # Webhook 规则实体（新增录播存储引擎关联）
├── interceptor/
│   └── AuthInterceptor.java
├── repository/
│   └── [JPA 接口]
├── service/
│   ├── engine/                              # ★ 新增：存储引擎策略模式
│   │   ├── StorageEngineStrategy.java       # 策略接口
│   │   ├── AListStorageStrategy.java        # AList 策略实现
│   │   └── LocalStorageStrategy.java        # 本地路径策略实现
│   ├── CleanupService.java                  # 临时文件清理（扩展孤立文件清理）
│   ├── DashboardService.java
│   ├── ScheduleService.java
│   ├── StorageEngineService.java            # 存储引擎 CRUD + 策略分发
│   ├── SyncService.java                     # 同步执行引擎（适配策略模式）
│   ├── SyncTaskManageService.java
│   ├── TranscodeFileProcessor.java          # 单文件转码（适配三步流程）
│   ├── TranscodeService.java                # 转码编排（三步流程状态机）
│   ├── WebhookRuleService.java
│   └── WebhookService.java
└── util/
    ├── DiskSpaceChecker.java
    ├── MagicBytesDetector.java
    ├── ServerAddressLogger.java
    ├── TempFileManager.java
    └── TempSuffixValidator.java

src/main/frontend/src/
├── api/                                     # HTTP API 客户端
├── auth/                                    # 认证上下文
├── components/
│   ├── forms/                               # 表单组件
│   ├── layout/                              # 布局组件
│   └── ui/                                  # 通用 UI 组件
│       ├── DirectoryTreeSelector.tsx         # ★ 新增：树状目录浏览组件
│       └── CronBuilder.tsx                   # ★ 新增：Cron 图形化配置组件
├── hooks/
├── pages/
├── router/
├── types/
└── utils/

src/test/java/top/lldwb/alistmediasync/
├── service/
│   ├── engine/                              # ★ 策略模式单元测试
│   └── TranscodeServiceTest.java            # 转码状态机测试
├── controller/
└── repository/
```

**结构决策**：采用现有项目结构，在 `service/engine/` 包下新增策略模式相关类。前端在 `components/ui/` 下新增通用组件。保持后端三层架构不变。

## 复杂性追踪

> **仅在章程检查有必须证明合理性的违规时填充**

| 违规 | 为什么需要 | 被拒绝的更简单替代方案及原因 |
|------|-----------|------------------------|
| 无 | — | — |
