# 实现计划：Docker 容器化部署支持

**分支**：`003-docker-deploy` | **日期**：2026-06-19 | **规格**：[spec.md](./spec.md)

**输入**：来自 `/specs/003-docker-deploy/spec.md` 的功能规格

## 摘要

为 AList-Media-Sync（Spring Boot 4.1.0 + Java 21 + Maven Wrapper）项目添加 Docker 容器化部署支持。核心交付物包括：多阶段构建的 Dockerfile（构建阶段使用 Maven 镜像编译，运行阶段使用 JRE 镜像）、docker-compose.yml 编排文件、.dockerignore 构建优化文件。同时需要为项目添加 Spring Boot Actuator 依赖以支持健康检查端点，并配置优雅关闭机制。

## 技术上下文

**语言/版本**：Java 21（LTS，支持虚拟线程）

**主要依赖**：Spring Boot 4.1.0（Spring Framework 7.x）、Spring Data JPA、H2、Lombok、Spring WebMVC、Spring Boot Actuator（新增）

**存储**：H2 内嵌数据库（文件模式，默认路径 `./data`，通过 `DATA_DIR` 环境变量可配置）

**测试**：Spring Boot Test（`@WebMvcTest`、`@DataJpaTest`）

**目标平台**：x86-64 Linux 服务器（Docker 容器），兼容 Docker Desktop（macOS/Windows）

**项目类型**：单体 Web 服务（单容器部署）

**性能目标**：镜像大小 < 250MB，容器冷启动 < 30 秒，优雅关闭 < 15 秒

**约束**：非 root 用户运行，日志输出到 stdout，环境变量配置优先于配置文件

**规模/范围**：单容器部署，3 个新增文件（Dockerfile、docker-compose.yml、.dockerignore）+ 1 个修改文件（pom.xml 添加 actuator 依赖）

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

| 章程原则 | 适用性 | 状态 |
|---------|--------|------|
| I. 分层架构（不可协商） | 不适用 — 本功能为基础设施/部署配置，不涉及业务代码 | ✅ 通过 |
| II. 数据完整性优先 | 间接相关 — 确保数据卷挂载和 H2 文件路径正确配置 | ✅ 通过 |
| III. RESTful API 契约优先 | 不适用 — 本功能不引入新 API 端点 | ✅ 通过 |
| IV. 中文优先 | 适用 — Dockerfile 注释、docker-compose.yml 注释、README 文档使用中文 | ✅ 通过 |
| V. 测试不可省略 | 间接相关 — 需确保 Docker 化后现有测试可通过（在容器内执行） | ✅ 通过 |
| VI. 简洁至上（YAGNI） | 适用 — 仅添加 actuator 一个必需依赖（健康检查），不引入额外编排工具 | ✅ 通过 |

**门禁结果**：全部通过，无违规项，无需记录复杂性追踪。

## 项目结构

### 文档（本功能）

```text
specs/003-docker-deploy/
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── research.md          # 阶段 0 输出
├── data-model.md        # 阶段 1 输出
├── quickstart.md        # 阶段 1 输出
├── contracts/           # 阶段 1 输出
└── tasks.md             # 阶段 2 输出（/speckit-tasks 命令创建）
```

### 源代码（仓库根目录）

```text
.
├── Dockerfile                    # 新增：多阶段构建定义
├── docker-compose.yml            # 新增：Compose 编排配置
├── .dockerignore                 # 新增：构建上下文排除规则
├── pom.xml                       # 修改：添加 actuator 依赖
├── src/
│   ├── main/
│   │   ├── java/...
│   │   └── resources/
│   │       └── application.yaml  # 修改：添加优雅关闭和健康检查配置
│   └── test/...
└── README.md                     # 修改：添加 Docker 部署文档
```

**结构决策**：Docker 相关文件放置在项目根目录（标准惯例）。这是单体项目，无需多层目录结构。遵循"选项 1：单项目"布局。

## 复杂性追踪

> 无违规项，无需记录。
