# AList-Media-Sync

基于 Spring Boot 4.1.0 + Java 21 的 AList 网盘媒体同步服务。

## 快速开始（一体化启动包）

一体化启动包是自包含的独立部署方案，内置 JRE 和所有依赖，解压后零配置即可运行。**无需安装 Java、Maven 或 Node.js。**

### 前置条件

- 操作系统：Windows 10/11（x86-64）或 Linux（x86-64, kernel 5.x+）
- 磁盘空间：至少 400MB 可用空间
- 网络端口：默认端口 8080（可配置）

### 安装与启动

```bash
# 1. 下载对应平台的启动包
#    Windows: alist-media-sync-{version}-windows-x64.zip
#    Linux:   alist-media-sync-{version}-linux-x64.tar.gz

# 2. 解压启动包
#    Windows：直接右键解压
#    Linux：
tar -xzf alist-media-sync-{version}-linux-x64.tar.gz
cd alist-media-sync-{version}

# 3. 配置 AList 连接信息
#    编辑 config/application.yaml，修改以下必填项：
#      alist.base-url: https://your-alist.example.com
#      alist.token: your-api-token

# 4. 启动服务
#    Windows：双击 start.bat 或在命令提示符中运行
#    Linux：
./start.sh
#    或
sh start.sh

# 5. 访问管理界面
#    打开浏览器访问 http://localhost:8080/app/
#    默认用户名：admin  密码：admin123
```

### 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | `8080` | 服务监听端口 |
| `app.data-dir` | `./data` | 数据目录（H2 数据库、PID 文件） |
| `app.auth.username` | `admin` | 管理后台用户名 |
| `app.auth.password` | `admin123` | 登录密码（仅支持明文，每次启动随机盐值加密到内存） |
| `app.transcode.temp-suffix` | `.tmp` | 转码临时文件后缀（可自定义，如 `.lldwb`） |
| `app.transcode.temp-dir` | `系统临时目录/alist-media-sync/transcode` | 转码临时文件存储目录（位于系统临时目录下的子目录） |
| `app.transcode.max-concurrent-transcode` | `32` | 最大并发转码任务数 |
| `alist.base-url` | — | AList 服务器地址（必填） |
| `alist.token` | — | AList API 认证令牌（必填） |

### 常见问题

**Q：启动脚本报"未检测到 Java 运行环境"**
A：启动包已内置 JRE。此错误表示 runtime/ 目录在解压过程中丢失，请重新下载启动包。

**Q：启动后提示"端口已被占用"**
A：修改 `config/application.yaml` 中的 `server.port` 配置项，或终止占用端口的进程后重试。

**Q：修改了密码配置但登录仍然使用旧密码**
A：重启服务后新密码生效。密码始终为明文，每次启动自动加密到内存。`{bcrypt}` 预加密格式已废弃，升级后需将密码改为明文。

**Q：可以同时运行多个实例吗？**
A：可以，但每个实例需要独立的数据目录（通过 `DATA_DIR` 环境变量指定）。启动脚本检测到同一数据目录已有实例运行时会给出提示。

**Q：数据文件存储在哪里？**
A：默认存储在启动包目录下的 `data/` 子目录。可通过环境变量 `DATA_DIR` 修改。

---

## Docker 部署

### 前置条件

- Docker Engine 20.10+（或 Docker Desktop 4.x）
- Docker Compose 2.0+（`docker compose` 子命令）

### 方式一：Docker Compose（推荐）

```bash
# 1. 配置环境变量（.env 文件已包含默认值，按需修改）
# 编辑 .env 文件，填入你的 AList 连接信息

# 2. 启动服务
docker compose up -d

# 3. 验证服务状态
curl http://localhost:8080/actuator/health
# 预期返回：{"status":"UP"}

# 4. 查看日志
docker compose logs -f
```

### 方式二：docker build + docker run

```bash
# 1. 构建镜像
docker build -t alist-media-sync .

# 2. 启动容器
docker run -d \
  --name alist-media-sync \
  -p 8080:8080 \
  -e ALIST_BASE_URL=https://your-alist.example.com \
  -e ALIST_TOKEN=your-token \
  -v alist-media-sync-data:/app/data \
  alist-media-sync

# 3. 验证
curl http://localhost:8080/actuator/health
```

## 环境变量说明

| 环境变量 | 默认值 | 必填 | 说明 |
|---------|--------|------|------|
| `PORT` | `8080` | 否 | Docker Compose 主机端口映射 |
| `SERVER_PORT` | `8080` | 否 | 应用 HTTP 监听端口（直接 docker run 时使用） |
| `DATA_DIR` | `/app/data` | 否 | H2 数据库文件存储目录（容器内路径） |
| `LOGGING_LEVEL` | `INFO` | 否 | 日志级别：TRACE、DEBUG、INFO、WARN、ERROR、OFF |
| `ALIST_BASE_URL` | — | **是** | AList 服务器连接地址（如 `https://alist.example.com`） |
| `ALIST_TOKEN` | — | **是** | AList API 认证令牌（从 AList 管理后台获取） |
| `JAVA_OPTS` | `-Xms128m -Xmx256m` | 否 | JVM 运行参数 |
| `ALIST_CRYPTO_KEY` | — | 否 | AES-256 加密密钥（Base64 编码的 32 字节明文，用于数据库敏感字段加密/解密） |
| `APP_AUTH_PASSWORD` | `admin123` | 否 | 管理后台登录密码（覆盖配置文件中的 `app.auth.password`） |

## 数据备份与恢复

数据卷 `alist-media-sync-data` 存储 H2 数据库文件。容器删除后数据卷保留。

### 备份数据

```bash
docker run --rm \
  -v alist-media-sync-data:/data \
  -v $(pwd):/backup \
  alpine tar czf /backup/alist-data-backup.tar.gz -C /data .
```

### 恢复数据

```bash
docker run --rm \
  -v alist-media-sync-data:/data \
  -v $(pwd):/backup \
  alpine tar xzf /backup/alist-data-backup.tar.gz -C /data
```

## 注意事项

- **单实例限制**：应用使用 H2 内嵌数据库（文件模式），同一数据卷只能运行一个容器实例。如需多实例部署，请改用 PostgreSQL 等外部数据库。
- **非 root 运行**：容器以 `appuser`（UID 1000）用户运行，符合安全最佳实践。
- **优雅关闭**：收到 `docker stop` 信号后，应用会在 12 秒内完成优雅关闭（Docker Compose 停止宽限期为 35 秒）。
- **内存占用**：默认 JVM 堆内存为 128MB–256MB，可通过 `JAVA_OPTS` 环境变量调整。

## 本地开发

```bash
# 使用 Maven Wrapper（无需预装 Maven）
./mvnw spring-boot:run

# 运行测试
./mvnw test
```

## 诊断系统

应用内置轻量诊断系统，便于在出现同步/转码/Webhook 失败时快速将"问题摘要"交给 AI 或开发者排查。详细规格参见 [`specs/009-lightweight-diagnostics/`](./specs/009-lightweight-diagnostics/)。

### 关键能力

- **统一 traceId**：每次任务执行或 HTTP 请求都生成 traceId，关键日志、错误日志和响应头均携带；
- **响应头 `X-Trace-Id`**：所有 `/api/**` 响应（成功、业务失败、认证失败、异常）都返回 `X-Trace-Id`，请求方可直接复制使用；如请求已携带合法值则沿用；
- **结构化日志**：`logs/app.log` 和 `logs/error.log` 默认包含 traceId、module、operation、errorType 字段，ERROR 级日志同时写入 `logs/error.log`；
- **一键诊断包**：脚本或后端端点生成 `diagnostics/latest/summary.md` 等关键证据；
- **自动脱敏**：诊断包内所有密码、Token、Cookie、Authorization、密钥统一替换为 `***REDACTED***`，可放心交给 AI 或开发者。

### 触发方式

| 部署形态 | 命令 |
|---|---|
| 本地开发 / 一体化启动包（Linux） | `sh scripts/diagnose.sh` |
| 本地开发 / 一体化启动包（Windows） | `scripts\diagnose.bat` |
| Docker（应用运行中） | `curl -u admin:admin123 -X POST http://localhost:8080/api/diagnostics/run` |
| Docker（容器内脚本） | `docker exec -it alist-media-sync sh /app/scripts/diagnose.sh` |

脚本可选参数：

- `--output <dir>`：指定诊断包输出目录（默认 `./diagnostics/latest`）
- `--trace-id <id>`：指定优先收集的 traceId
- `--max-lines <n>`：每个日志摘录最多保留行数（默认 2000）

### 诊断包结构

```text
diagnostics/latest/
├── summary.md              # 阅读入口：基本信息 / 最近失败 / 关键证据 / 缺失信息 / 建议下一步
├── logs/
│   ├── error.log           # 错误日志摘录（自动脱敏）
│   └── app.log             # 应用日志摘录（自动脱敏）
├── config/
│   └── config.redacted.json  # 脱敏配置摘要（含 emptyKeys / missingKeys / redactedKeys）
├── environment.txt         # 运行环境（JVM、OS、磁盘、内存）
└── last-run.json           # 最近一次任务执行摘要
```

### 日志路径

| 文件 | 内容 |
|---|---|
| `logs/app.log` | 所有级别的应用日志（按天滚动，单文件 ≤50MB，保留 30 天） |
| `logs/error.log` | 仅 ERROR 级日志，便于诊断包快速采集（同样按天滚动） |

可通过环境变量 `LOG_PATH` 重定向日志目录（默认 `./logs`）。

## 项目结构

```text
.
├── Dockerfile                 # 多阶段构建定义
├── docker-compose.yml         # Compose 编排配置
├── .dockerignore              # 构建上下文排除规则
├── .env                       # 环境变量模板
├── pom.xml                    # Maven 依赖配置
├── mvnw / mvnw.cmd           # Maven Wrapper
├── scripts/                   # 构建与启动脚本
│   ├── start.bat              # Windows 一体化启动脚本
│   ├── start.sh               # Linux 一体化启动脚本
│   ├── download-jre.sh        # JRE 下载脚本
│   └── verify-build.sh        # 构建验证脚本
└── src/                       # 源代码
    ├── main/java/top/lldwb/alistmediasync/
    │   ├── AListMediaSyncApplication.java  # Spring Boot 入口（含 @EnableScheduling）
    │   ├── common/            # 通用模块（配置、实体、DTO、拦截器、工具类、通用服务）
    │   ├── storage/           # 存储引擎模块（策略模式：AList/本地路径，含目录浏览）
    │   ├── sync/              # 同步任务模块（NEW_ONLY/FULL/MOVE三模式、CRON/INTERVAL调度）
    │   ├── transcode/         # 转码模块（下载→转码→上传三步流程、8状态模型）
    │   └── webhook/           # Webhook 模块（录播姬事件接收、规则匹配）
    ├── main/resources/        # 配置文件
    │   ├── application.yaml   # 应用配置（含认证、转码、AList 连接）
    │   └── application.template.yaml  # 启动包配置模板
    ├── main/frontend/         # Vue 3 + TypeScript 前端源码（Vite 构建）
    └── test/                  # 测试代码（镜像 main 目录结构）
```

## 功能说明

### 密码配置

配置文件中的 `app.auth.password` 仅支持明文密码。每次应用启动时，系统使用随机盐值对明文密码进行 BCrypt 加密，加密结果仅保存在内存中，绝不可回写到配置文件。

- ✅ 配置文件填写明文密码（如 `password: "admin123"`）
- ❌ `{bcrypt}` 预加密格式已废弃，不再被识别

### 原目录转码

创建转码任务时，可勾选"原目录转码"选项。启用后转码输出文件自动放置在源文件所在目录，无需手动填写目标路径。详见 `TranscodeTaskCreateDTO.sameDirectoryTranscode` 字段。
