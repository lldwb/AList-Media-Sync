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
| `app.auth.password` | `{bcrypt}...` | 登录密码（支持明文和 BCrypt 格式，明文自动加密到内存） |
| `alist.base-url` | — | AList 服务器地址（必填） |
| `alist.token` | — | AList API 认证令牌（必填） |

### 常见问题

**Q：启动脚本报"未检测到 Java 运行环境"**
A：启动包已内置 JRE。此错误表示 runtime/ 目录在解压过程中丢失，请重新下载启动包。

**Q：启动后提示"端口已被占用"**
A：修改 `config/application.yaml` 中的 `server.port` 配置项，或终止占用端口的进程后重试。

**Q：修改了密码配置但登录仍然使用旧密码**
A：重启服务后新密码生效。密码为明文时每次启动自动加密到内存；为 `{bcrypt}` 格式时直接使用。

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
# 1. 配置环境变量
cp .env.example .env   # 如已有 .env 则跳过
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

## 项目结构

```text
.
├── Dockerfile                 # 多阶段构建定义
├── docker-compose.yml         # Compose 编排配置
├── .dockerignore              # 构建上下文排除规则
├── .env                       # 环境变量模板
├── pom.xml                    # Maven 依赖配置
├── mvnw / mvnw.cmd           # Maven Wrapper
└── src/                       # 源代码
    ├── main/java/             # Java 源码
    └── main/resources/        # 配置文件
        └── application.yaml   # 应用配置
```
