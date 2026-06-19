# 研究文档：Docker 容器化部署支持

**功能**：Docker 容器化部署 | **日期**：2026-06-19

## 1. Java 21 轻量级基础镜像选择

**决策**：使用 `eclipse-temurin:21-jre-alpine` 作为运行阶段基础镜像。

**理由**：
- Eclipse Temurin 是 Adoptium 项目提供的官方 OpenJDK 发行版，社区认可度最高
- `alpine` 变体基于 Alpine Linux，镜像大小约 70MB（JRE），远小于 Ubuntu 变体（约 160MB）
- Java 21 是项目章程规定的 LTS 版本，Temurin 已提供稳定的 21 版本
- Alpine 使用 `musl libc`，Spring Boot 4.0+ 已良好兼容

**考虑的替代方案**：
- `bellsoft/liberica-openjdk-alpine:21-jre`：基于 Liberica JDK，与 Spring Boot 内置的 Paketo Buildpacks 集成更好，但社区体积略大于 Temurin
- `amazoncorretto:21-alpine`：AWS 官方维护，但 Alpine 变体起步较晚，成熟度略低于 Temurin
- `ibm-semeru-runtimes:open-21-jre`：IBM 维护，附带 OpenJ9 虚拟机（更小内存占用），但社区文档和示例较少
- `eclipse-temurin:21-jre-jammy`（Ubuntu）：兼容性最佳，但镜像大小约 160MB，不符合 <250MB 整体镜像目标

## 2. 构建阶段基础镜像

**决策**：使用 `eclipse-temurin:21-jdk-alpine` + Maven Wrapper 进行构建。

**理由**：
- 项目已配置 Maven Wrapper（`mvnw` + `.mvn/wrapper`），无需在基础镜像中安装 Maven
- JDK 版本与运行时统一（21），避免编译目标与运行时差异
- Alpine 变体保持构建与运行阶段基础环境一致

**考虑的替代方案**：
- `maven:3.9-eclipse-temurin-21-alpine`（官方 Maven 镜像）：预装 Maven，但镜像更大（约 300MB），且 Maven Wrapper 已绑定版本号，使用预装 Maven 可能版本不匹配
- Docker BuildKit 缓存挂载：在 `RUN --mount=type=cache` 中缓存 `~/.m2` 仓库，加速重复构建。已决定在 Dockerfile 中采用此方式

## 3. Spring Boot Actuator 健康检查配置

**决策**：添加 `spring-boot-starter-actuator` 依赖，使用默认的 `/actuator/health` 端点作为 Docker HEALTHCHECK。

**理由**：
- Actuator 是 Spring Boot 生态组件，与现有技术栈完全匹配
- 默认健康检查端点返回 `{"status":"UP"}`，Docker 可基于退出码判断
- Spring Boot 4.x 内置 K8s 风格的健康组（liveness/readiness），无需额外配置即可支持 Docker Compose 的 `healthcheck`

**配置方案**：
```yaml
# application.yaml
spring:
  application:
    name: AList-Media-Sync
server:
  shutdown: graceful  # 优雅关闭已默认开启

# Docker HEALTHCHECK 使用
# HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
#   CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
```

**考虑的替代方案**：
- 自定义 `/health` 端点：需要额外编写 Controller，违反 YAGNI 原则
- Spring Boot 4.x 内置健康端点不使用 actuator：不可行，健康检查需要 Actuator 模块

## 4. 优雅关闭机制

**决策**：使用 Spring Boot 4.x 内置的优雅关闭（`server.shutdown: graceful`），配合 Docker 的 `stop_grace_period`。

**理由**：
- Spring Boot 4.x 默认启用优雅关闭，无需额外配置
- 默认优雅关闭超时为 30 秒，可通过 `spring.lifecycle.timeout-per-shutdown-phase` 调整
- Docker Compose 的 `stop_grace_period` 应设为略大于应用优雅关闭超时（建议 35s）

**配置方案**：
```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 12s  # 略小于 Docker Compose 的 stop_grace_period (35s)
```

## 5. H2 数据库文件持久化

**决策**：将 H2 数据库文件存储在 `/app/data` 目录，通过 `DATA_DIR` 环境变量可配置，Docker 卷挂载该目录实现持久化。

**理由**：
- 规格要求数据库文件存储在可挂载的数据目录
- `/app` 为工作目录，`/app/data` 作为数据子目录符合惯例
- 环境变量命名遵循 Spring Boot Relaxed Binding 约定

## 6. 非 root 用户运行

**决策**：Dockerfile 中创建 `appuser` 用户（UID 1000），所有运行时进程以该用户身份运行。

**理由**：
- Alpine 基础镜像默认使用 root 用户，需显式创建非 root 用户
- UID 1000 是 Docker 容器中常用的非特权用户 ID
- 满足安全最佳实践（CIS Docker Benchmark 4.1）

**实现要点**：
```dockerfile
RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -D appuser
USER appuser
```

## 7. 环境变量配置方案

**决策**：通过 Spring Boot 外部化配置 + Docker 环境变量实现运行时配置覆盖。

**理由**：
- Spring Boot 支持通过环境变量覆盖 `application.yaml` 配置（Relaxed Binding）
- 无需引入额外的配置管理工具（如 Spring Cloud Config）
- 符合十二要素应用的「配置」原则

**环境变量映射**：

| 环境变量 | Spring 属性 | 默认值 | 说明 |
|---------|-------------|--------|------|
| `SERVER_PORT` | `server.port` | 8080 | HTTP 服务端口 |
| `DATA_DIR` | `spring.datasource.url`（间接） | `./data` | H2 数据库文件目录 |
| `LOGGING_LEVEL` | `logging.level.root` | INFO | 日志级别 |
| `ALIST_BASE_URL` | `alist.base-url`（自定义配置） | — | AList 服务器地址 |

## 8. Docker Compose 健康检查配置

**决策**：在 `docker-compose.yml` 中配置健康检查，而非在 Dockerfile 中硬编码 HEALTHCHECK 指令。

**理由**：
- 保持 Dockerfile 简洁，不在 Dockerfile 中依赖 `wget` 或 `curl` 工具（Dockerfile 中安装 wget 是为 docker-compose.yml 的 healthcheck 提供依赖，而非为 Dockerfile 自身的 HEALTHCHECK 指令）
- Docker Compose 的健康检查语法更易维护
- 规格未强制 Dockerfile 级别的健康检查，Docker Compose 方案满足需求

## 9. .dockerignore 规则

**决策**：排除版本控制目录、构建产物、IDE 配置、不必要的项目文件。

**排除项**：
- `.git/`、`.idea/`、`.vscode/`、`.specify/` — 版本控制与 IDE 配置
- `target/`、`build/` — 本地构建产物
- `*.iml`、`*.iws`、`*.ipr` — IDE 项目文件
- `HELP.md`、`*.md`（除 README.md） — 文档（Docker 构建不需要）
- `.dockerignore`、`docker-compose.yml` — 自身引用文件
