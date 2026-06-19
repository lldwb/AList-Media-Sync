# 数据模型：Docker 容器化部署支持

**功能**：Docker 容器化部署 | **日期**：2026-06-19

## 概述

本功能不引入新的数据库实体。所有"实体"均为 Docker 编排配置中的抽象概念。本文档描述这些配置实体的结构与关系。

## 配置实体

### 1. Docker 镜像（Image）

镜像分为两个构建阶段，最终产出运行时镜像。

| 字段 | 类型 | 说明 |
|------|------|------|
| 基础镜像（构建阶段） | `eclipse-temurin:21-jdk-alpine` | 提供 JDK 21 + Maven Wrapper 编译环境 |
| 基础镜像（运行阶段） | `eclipse-temurin:21-jre-alpine` | 仅包含 JRE 21，最小化镜像体积 |
| 工作目录 | `/app` | 应用文件与数据的根目录 |
| 运行用户 | `appuser:appgroup`（UID 1000） | 非 root 用户 |
| 暴露端口 | `8080` | HTTP 服务端口（可通过环境变量覆盖） |
| 入口点 | `java -jar /app/app.jar` | Spring Boot 可执行 JAR |

**体积约束**：最终镜像 < 250MB（规格 SC-002）。

**状态转换**：
```
源码 + Dockerfile → docker build → 镜像（已构建）
镜像 + 卷挂载  → docker run    → 容器（运行中）
容器           → docker stop   → 容器（已停止）
容器           → docker rm     → 容器（已删除，数据卷保留）
```

### 2. Docker 数据卷（Volume）

| 字段 | 类型 | 说明 |
|------|------|------|
| 名称 | `alist-media-sync-data` | Docker Compose 命名卷 |
| 挂载点（容器内） | `/app/data` | H2 数据库文件存储路径 |
| 生命周期 | 独立于容器 | 容器删除后卷保留 |
| 配置方式 | `DATA_DIR` 环境变量 | 可自定义数据目录路径 |

### 3. 环境变量配置（Environment Variables）

Spring Boot 外部化配置 + Docker 环境变量的映射关系。

| 环境变量 | 对应 Spring 属性 | 默认值 | 必填 | 说明 |
|---------|------------------|--------|------|------|
| `SERVER_PORT` | `server.port` | `8080` | 否 | HTTP 服务端口 |
| `DATA_DIR` | `app.data-dir` | `/app/data` | 否 | 数据库文件存储目录 |
| `LOGGING_LEVEL` | `logging.level.root` | `INFO` | 否 | 根日志级别 |
| `ALIST_BASE_URL` | `alist.base-url` | — | 是 | AList 服务器连接地址 |
| `ALIST_TOKEN` | `alist.token` | — | 是 | AList API 认证令牌 |
| `JAVA_OPTS` | — | `-Xms128m -Xmx256m` | 否 | JVM 运行参数 |

**规则**：
- 所有环境变量遵循 Spring Boot Relaxed Binding 约定
- `SERVER_PORT` 映射到 `server.port`，`DATA_DIR` 映射到 `app.data-dir`（自定义配置前缀）
- 环境变量值类型：`SERVER_PORT` 为整数，`LOGGING_LEVEL` 为枚举字符串

### 4. Docker Compose 编排服务

| 字段 | 类型 | 说明 |
|------|------|------|
| 服务名称 | `alist-media-sync` | Compose 项目中的服务标识 |
| 构建上下文 | `.`（项目根目录） | Dockerfile 所在目录 |
| 端口映射 | `"${PORT:-8080}:${SERVER_PORT:-8080}"` | 主机端口和容器内端口均可配置 |
| 环境变量 | 见上表 | 从 `.env` 文件或命令行注入 |
| 卷挂载 | `alist-media-sync-data:/app/data` | 数据持久化 |
| 重启策略 | `unless-stopped` | 异常退出自动重启，手动停止不复启 |
| 健康检查 | HTTP GET `/actuator/health` | 30s 间隔，5s 超时，3 次重试 |
| 停止宽限期 | `35s` | 略大于应用优雅关闭超时（12s） |
| 网络 | 默认 bridge | 单容器场景无需自定义网络 |

### 实体关系

```
Dockerfile ──构建──▶ Docker 镜像 ──实例化──▶ Docker 容器
                         │                      │
                         │                      ├── 挂载 ──▶ 数据卷（H2 DB 文件）
                         │                      │
                         │                      └── 读取 ──▶ 环境变量
                         │
docker-compose.yml ─编排──▶ Compose 服务（单容器）
                         │
                         ├── 定义 ──▶ 端口映射
                         ├── 定义 ──▶ 卷挂载
                         ├── 定义 ──▶ 健康检查
                         └── 定义 ──▶ 重启策略
```
