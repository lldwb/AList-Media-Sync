# 接口契约：Docker 容器化部署支持

**功能**：Docker 容器化部署 | **日期**：2026-06-19

## 概述

本功能的"接口"分为三类：Docker 构建契约（Dockerfile）、Docker 运行时契约（环境变量与健康检查）、Compose 编排契约（docker-compose.yml）。这些契约定义了系统的外部可操作边界。

---

## 1. Dockerfile 构建契约

### 1.1 构建命令

```bash
# 标准构建
docker build -t alist-media-sync .

# 无缓存构建（拉取最新基础镜像）
docker build --no-cache -t alist-media-sync .
```

### 1.2 构建阶段约定

| 阶段 | 基础镜像 | 产出 |
|------|---------|------|
| `builder` | `eclipse-temurin:21-jdk-alpine` | `target/*.jar`（Spring Boot 可执行 JAR） |
| `runtime` | `eclipse-temurin:21-jre-alpine` | 最终镜像（仅包含 JAR + JRE） |

### 1.3 构建参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `MAVEN_OPTS` | `-Xmx1024m -XX:MaxMetaspaceSize=256m` | Maven 构建 JVM 参数 |

### 1.4 运行时约定

| 属性 | 值 |
|------|-----|
| 工作目录 | `/app` |
| 运行用户 | `appuser`（UID 1000，非 root） |
| 暴露端口 | `8080/tcp` |
| 入口点 | `java $JAVA_OPTS -jar /app/app.jar` |
| 信号处理 | JVM 响应 SIGTERM 触发优雅关闭 |

---

## 2. 运行时契约

### 2.1 容器启动命令

```bash
# 最简启动
docker run -d -p 8080:8080 alist-media-sync

# 完整启动（带卷挂载和环境变量）
docker run -d \
  --name alist-media-sync \
  -p 8080:8080 \
  -v alist-media-sync-data:/app/data \
  -e ALIST_BASE_URL=https://alist.example.com \
  -e ALIST_TOKEN=<token> \
  -e SERVER_PORT=8080 \
  -e LOGGING_LEVEL=INFO \
  alist-media-sync
```

### 2.2 环境变量（完整列表）

| 环境变量 | 类型 | 默认值 | 必填 | 说明 |
|---------|------|--------|------|------|
| `SERVER_PORT` | `int` (1-65535) | `8080` | 否 | HTTP 监听端口 |
| `DATA_DIR` | `path` | `/app/data` | 否 | 数据库文件存储目录 |
| `LOGGING_LEVEL` | `enum` | `INFO` | 否 | 日志级别：TRACE、DEBUG、INFO、WARN、ERROR、OFF |
| `ALIST_BASE_URL` | `url` | — | 是 | AList 服务器地址（如 `https://alist.example.com`） |
| `ALIST_TOKEN` | `string` | — | 是 | AList API 认证令牌 |
| `JAVA_OPTS` | `string` | `-Xms128m -Xmx256m` | 否 | JVM 运行参数 |

### 2.3 健康检查端点

```
GET /actuator/health

响应 200：
{
    "status": "UP"
}

响应 503（服务不可用）：
{
    "status": "DOWN"
}
```

Docker Compose 中配置健康检查，使用 `wget` 或 `curl` 探测上述端点。

---

## 3. Docker Compose 编排契约

### 3.1 命令集

```bash
# 启动服务
docker compose up -d

# 查看日志
docker compose logs -f

# 停止服务
docker compose down

# 重建并重启
docker compose build --no-cache && docker compose up -d

# 查看运行状态
docker compose ps
```

### 3.2 服务定义

```yaml
services:
  alist-media-sync:
    build: .
    container_name: alist-media-sync
    ports:
      - "${PORT:-8080}:${SERVER_PORT:-8080}"
    environment:
      - SERVER_PORT=${SERVER_PORT:-8080}
      - DATA_DIR=/app/data
      - LOGGING_LEVEL=${LOGGING_LEVEL:-INFO}
      - ALIST_BASE_URL=${ALIST_BASE_URL}
      - ALIST_TOKEN=${ALIST_TOKEN}
      - JAVA_OPTS=${JAVA_OPTS:--Xms128m -Xmx256m}
    volumes:
      - alist-media-sync-data:/app/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:${SERVER_PORT:-8080}/actuator/health"]
      interval: 30s
      timeout: 5s
      start_period: 30s
      retries: 3
    stop_grace_period: 35s

volumes:
  alist-media-sync-data:
    name: alist-media-sync-data
```

### 3.3 .env 文件（环境变量模板）

```bash
# AList-Media-Sync Docker Compose 环境变量
PORT=8080
SERVER_PORT=8080
LOGGING_LEVEL=INFO
ALIST_BASE_URL=https://alist.example.com
ALIST_TOKEN=<your-token-here>
JAVA_OPTS=-Xms128m -Xmx256m
```
