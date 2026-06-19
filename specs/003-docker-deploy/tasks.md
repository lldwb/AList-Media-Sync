# 任务：Docker 容器化部署支持

**输入**：来自 `/specs/003-docker-deploy/` 的设计文档

**前提条件**：plan.md（必需）、spec.md（用户故事必需）、research.md、data-model.md、contracts/docker-contracts.md、quickstart.md

**测试**：本功能规格未明确请求 TDD 测试任务。任务聚焦于实现与验证。

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（例如，US1、US2、US3）
- 在描述中包含确切的文件路径

## 路径约定

- **项目根目录**：`C:\Users\Administrator\Documents\GitHub\AList-Media-Sync\`
- Docker 配置文件位于项目根目录（标准惯例）
- Spring Boot 配置文件位于 `src/main/resources/`

---

## 阶段 1：设置（共享基础设施）

**目的**：添加 Docker 部署所需的基础依赖

- [ ] T001 在 `pom.xml` 中添加 `spring-boot-starter-actuator` 依赖（位于现有 `<dependencies>` 块内，与其它 Spring Boot starter 并列），为健康检查端点提供支持

---

## 阶段 2：基础（阻塞性前置条件）

**目的**：配置应用核心运行时行为——优雅关闭与健康检查。所有用户故事依赖此阶段完成。

**⚠️ 关键**：在此阶段完成之前，不能开始任何用户故事的工作

- [ ] T002 在 `src/main/resources/application.yaml` 中配置优雅关闭：添加 `server.shutdown: graceful` 和 `spring.lifecycle.timeout-per-shutdown-phase: 12s`（略小于 Docker Compose 的 `stop_grace_period: 35s`）
- [ ] T003 在 `src/main/resources/application.yaml` 中配置 Actuator 健康检查端点：暴露 `/actuator/health`，启用 `management.endpoint.health.show-details: always`，并配置 `management.endpoints.web.exposure.include: health`

**检查点**：基础就绪 — 应用具备健康检查能力与优雅关闭机制，可以开始 Docker 化工作

---

## 阶段 3：用户故事 1 — 开发者通过 Docker 一键构建并运行应用（优先级：P1）🎯 MVP

**目标**：提供多阶段构建的 Dockerfile 和构建优化文件（.dockerignore），使开发者可以在未安装 Java/Maven 的机器上通过 `docker build` + `docker run` 部署应用

**独立测试**：在仅安装 Docker 的机器上执行 `docker build -t alist-media-sync .` 构建镜像，然后执行 `docker run -d -p 8080:8080 alist-media-sync` 启动容器，访问 `http://localhost:8080/actuator/health` 返回 `{"status":"UP"}` 来验证

### 用户故事 1 的实现

- [ ] T004 [US1] 在项目根目录 `Dockerfile` 中实现多阶段构建：阶段 1（builder）使用 `eclipse-temurin:21-jdk-alpine` + Maven Wrapper 编译打包应用（含 BuildKit `--mount=type=cache` 缓存 Maven 仓库加速构建）；阶段 2（runtime）使用 `eclipse-temurin:21-jre-alpine`，仅复制 JAR 文件，创建非 root 用户 `appuser`（UID 1000），设置工作目录 `/app`，暴露端口 `8080/tcp`，入口点 `java $JAVA_OPTS -jar /app/app.jar`，添加中文注释说明各阶段用途
- [ ] T005 [P] [US1] 在项目根目录 `.dockerignore` 中排除构建上下文无关文件：`.git/`、`.idea/`、`.vscode/`、`.specify/`、`target/`、`build/`、`*.iml`、`*.iws`、`*.ipr`、`HELP.md`、`*.md`（除 `README.md` 外）、`.dockerignore`、`docker-compose.yml`、`.env`、`specs/`、`.claude/`

**检查点**：此时，用户故事 1 应完全功能可用——开发者可通过 `docker build` + `docker run` 部署并访问健康端点

---

## 阶段 4：用户故事 2 — 使用 Docker Compose 编排部署（优先级：P2）

**目标**：提供 `docker-compose.yml` 声明式配置文件，使运维人员可通过 `docker compose up -d` 一键启动服务，无需记忆复杂的 `docker run` 参数

**独立测试**：在安装 Docker Compose 的机器上执行 `docker compose up -d`，观察服务是否在 30 秒内启动并通过 `http://localhost:8080/actuator/health` 可访问来验证

### 用户故事 2 的实现

- [ ] T006 [US2] 在项目根目录 `docker-compose.yml` 中定义服务编排：服务名 `alist-media-sync`，构建上下文 `.`，端口映射 `"${PORT:-8080}:8080"`，挂载命名卷 `alist-media-sync-data:/app/data`，配置健康检查（HTTP GET `/actuator/health`，30s 间隔，5s 超时，30s 启动等待，3 次重试），重启策略 `unless-stopped`，停止宽限期 `35s`，注入环境变量（`SERVER_PORT`、`DATA_DIR`、`LOGGING_LEVEL`、`ALIST_BASE_URL`、`ALIST_TOKEN`、`JAVA_OPTS`），定义顶层 `volumes` 命名卷 `alist-media-sync-data`，添加中文注释说明数据备份方法
- [ ] T007 [P] [US2] 在项目根目录 `.env` 中创建环境变量模板文件：包含 `PORT=8080`、`LOGGING_LEVEL=INFO`、`ALIST_BASE_URL=https://alist.example.com`、`ALIST_TOKEN=<your-token-here>`、`JAVA_OPTS=-Xms128m -Xmx256m`，添加中文注释说明每个变量的用途

**检查点**：此时，运维人员可通过 `docker compose up -d` 一键启动服务，可通过 `docker compose logs -f` 查看日志

---

## 阶段 5：用户故事 3 — 通过环境变量灵活配置应用（优先级：P3）

**目标**：确保所有关键运行时参数（服务端口、数据库路径、日志级别、AList 连接地址）可通过环境变量覆盖，无需重新构建镜像，满足十二要素应用原则

**独立测试**：通过 `docker run -e SERVER_PORT=9090` 等环境变量启动容器，验证应用行为随环境变量变化来测试

### 用户故事 3 的实现

- [ ] T008 [US3] 在 `src/main/resources/application.yaml` 中添加自定义配置属性 `app.data-dir`（默认值 `/app/data`），配置 `spring.datasource.url` 使用文件模式 H2 路径（引用 `${app.data-dir}` 属性），添加 `alist.base-url` 和 `alist.token` 占位配置（默认值为空，由环境变量注入）
- [ ] T009 [P] [US3] 在 `src/main/resources/application.yaml` 中配置日志级别默认值：`logging.level.root: ${LOGGING_LEVEL:INFO}`，利用 Spring Boot Relaxed Binding 自动将 `LOGGING_LEVEL` 环境变量映射到日志配置
- [ ] T010 [US3] 验证环境变量覆盖行为：确认 `SERVER_PORT` → `server.port`、`DATA_DIR` → `app.data-dir`、`LOGGING_LEVEL` → `logging.level.root`、`ALIST_BASE_URL` → `alist.base-url` 的 Spring Boot Relaxed Binding 映射正常工作

**检查点**：此时所有用户故事应各自独立功能可用——运维人员可通过环境变量灵活配置应用，无需修改配置文件或重新构建镜像

---

## 阶段 6：润色与跨领域关注点

**目的**：文档编写与端到端验证

- [ ] T011 [P] 在项目根目录 `README.md` 中编写 Docker 部署文档（如文件不存在则创建）：包含前置条件（Docker 20.10+ / Docker Compose 2.0+）、快速开始（`docker build` + `docker run` 或 `docker compose up -d`）、环境变量说明表格、数据卷备份方法说明、多容器实例限制说明（H2 文件锁），所有文档内容使用简体中文
- [ ] T012 依照 `specs/003-docker-deploy/quickstart.md` 中的六个验证场景（镜像构建、容器启动与健康检查、数据持久化、Docker Compose 编排、环境变量配置、非 root 用户运行）执行端到端验证，确认所有验收场景通过

---

## 依赖与执行顺序

### 阶段依赖

- **设置（阶段 1）**：无依赖 — 可立即开始
- **基础（阶段 2）**：依赖设置完成 — 阻塞所有用户故事
- **用户故事 1（阶段 3）**：依赖基础阶段完成
- **用户故事 2（阶段 4）**：依赖基础阶段完成 — 可与 US1 并行但建议在 US1 后执行（docker-compose.yml 引用 Dockerfile）
- **用户故事 3（阶段 5）**：依赖基础阶段完成 — 可与 US1/US2 并行，但建议在 US1 后执行（需要可运行的容器验证环境变量）
- **润色（阶段 6）**：依赖所有用户故事完成

### 用户故事依赖

- **用户故事 1（P1）**：可在基础（阶段 2）后开始 — 不依赖其他故事
- **用户故事 2（P2）**：可在基础（阶段 2）后开始 — 逻辑上引用 US1 的 Dockerfile，但文件可独立编写
- **用户故事 3（P3）**：可在基础（阶段 2）后开始 — 配置独立于 Docker 文件，但验证依赖 US1 的镜像

### 每个用户故事内部

- Docker 文件先于验证任务
- 核心配置先于验证
- 故事完成后再进入下一个优先级

### 并行机会

- 阶段 2 中的 T002 和 T003 是同一文件（application.yaml），不可并行
- T004（Dockerfile）和 T005（.dockerignore）可并行（不同文件）
- T006（docker-compose.yml）和 T007（.env）可并行（不同文件）
- T008 和 T009 是同一文件（application.yaml），不可并行
- T011（README.md）与 T012（验证）可并行（不同活动）

---

## 并行示例：用户故事 1

```bash
# 一起启动用户故事 1 的所有独立文件任务：
任务："在项目根目录 Dockerfile 中实现多阶段构建"
任务："在项目根目录 .dockerignore 中排除构建上下文无关文件"
```

## 并行示例：用户故事 2

```bash
# 一起启动用户故事 2 的所有独立文件任务：
任务："在项目根目录 docker-compose.yml 中定义服务编排"
任务："在项目根目录 .env 中创建环境变量模板文件"
```

---

## 实现策略

### MVP 优先（仅用户故事 1）

1. 完成阶段 1：设置（添加 Actuator 依赖）
2. 完成阶段 2：基础（优雅关闭 + 健康检查配置）
3. 完成阶段 3：用户故事 1（Dockerfile + .dockerignore）
4. **停止并验证**：执行 `docker build` + `docker run`，确认健康端点可用
5. 如果就绪则部署/演示

### 增量交付

1. 完成设置 + 基础 → 基础就绪（应用具备健康检查与优雅关闭）
2. 添加用户故事 1 → 独立测试 → Docker 镜像可构建并运行（MVP！）
3. 添加用户故事 2 → 独立测试 → Docker Compose 一键部署
4. 添加用户故事 3 → 独立测试 → 环境变量灵活配置
5. 完成润色 → 文档完备 + 全场景验证通过

### 并行团队策略

多个开发人员时：

1. 团队一起完成设置 + 基础（均为 application.yaml 同一文件，需顺序执行）
2. 基础完成后：
   - 开发人员 A：用户故事 1（Dockerfile + .dockerignore）
   - 开发人员 B：用户故事 2（docker-compose.yml + .env）— 可在 US1 进行中开始
   - 开发人员 C：用户故事 3（application.yaml 环境变量配置）
3. 各故事独立完成后合并验证

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应能独立完成和测试
- 每个任务或逻辑组后提交
- 在任何检查点停止以独立验证故事
- 避免：模糊的任务、同文件冲突、破坏独立性的跨故事依赖
- 所有注释和文档使用简体中文（章程 IV. 中文优先）
- Dockerfile 注释、docker-compose.yml 注释、README.md 内容均使用简体中文
