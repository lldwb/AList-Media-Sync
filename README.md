# AList-Media-Sync

基于 Spring Boot 4.1.0 + Java 21 的 AList 网盘媒体同步与转码服务，对接 B 站录播姬 Webhook 自动触发同步与转码，配套 React 19 + TypeScript Web 管理前端。

技术栈：Spring Boot 4.1.0 / Java 21（虚拟线程）/ Spring Data JPA + H2 / JAVE2 + FFmpeg / React 19 + Vite 6 / Docker

## 快速开始

### Docker（推荐）

```bash
# 1. 编辑 .env 填入 AList 连接信息
#    ALIST_BASE_URL=https://your-alist.example.com
#    ALIST_TOKEN=your-token

# 2. 启动
docker compose up -d

# 3. 验证
curl http://localhost:8080/actuator/health   # {"status":"UP"}

# 4. 访问管理界面
#    http://localhost:8080/app/   账号 admin / admin123
```

### 一体化启动包

```bash
# 1. 下载对应平台启动包并解压
#    Windows: alist-media-sync-{version}-windows-x64.zip
#    Linux:   alist-media-sync-{version}-linux-x64.tar.gz
tar -xzf alist-media-sync-{version}-linux-x64.tar.gz && cd alist-media-sync-{version}

# 2. 编辑 config/application.yaml 填入 alist.base-url 与 alist.token

# 3. 启动
./start.sh          # Windows 双击 start.bat

# 4. 访问 http://localhost:8080/app/   账号 admin / admin123
```

一体化启动包内置 JRE，无需预装 Java / Maven / Node.js。

### 源码构建

```bash
# 1. 准备 JDK 21 与环境变量
export ALIST_BASE_URL=https://your-alist.example.com
export ALIST_TOKEN=your-token

# 2. 前端构建（首次或前端变更时）
cd src/main/frontend && npm ci && npm run build && cd ../../..

# 3. 启动后端
./mvnw spring-boot:run

# 4. 访问 http://localhost:8080/app/   账号 admin / admin123
```

完整开发环境搭建见 [docs/02-开发环境搭建.md](./docs/02-开发环境搭建.md)。

## 文档导航

| 文档 | 说明 |
|------|------|
| [docs/01-项目概述.md](./docs/01-项目概述.md) | 项目背景、核心功能、技术栈、非目标边界 |
| [docs/02-开发环境搭建.md](./docs/02-开发环境搭建.md) | JDK 21 / Maven Wrapper / 前端构建 / 必填配置 / 启动排障 |
| [docs/03-架构设计.md](./docs/03-架构设计.md) | 分层结构、核心类职责、MCP 接入层、模块边界 |
| [docs/04-配置说明.md](./docs/04-配置说明.md) | 配置 SSOT：`app.*` 全部配置项（含 `app.mcp.*`）与 Relaxed Binding 映射 |
| [docs/05-API接口文档.md](./docs/05-API接口文档.md) | API SSOT：`/api/**` REST 接口 + MCP 服务器接入（36 工具） |
| [docs/06-运维部署.md](./docs/06-运维部署.md) | Docker 部署、MCP 启用指引、健康检查、备份恢复 |
| [docs/architecture/](./docs/architecture/) | 架构设计文档 |
| [docs/operations/](./docs/operations/) | 运维与诊断手册（环境变量清单 SSOT、故障排查） |
| [CHANGELOG.md](./CHANGELOG.md) | 变更日志 |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | 贡献指南 |
| [AGENTS.md](./AGENTS.md) | AI 工作指令、模块索引、章程合规检查清单 |

## 核心 FAQ

- **默认账号**：`admin` / `admin123`，可通过 `APP_AUTH_PASSWORD` 环境变量修改；
- **数据存储位置**：默认 `./data` 目录（H2 文件数据库），可通过 `DATA_DIR` 修改；
- **单实例限制**：H2 文件模式不支持并发写入，同一 `DATA_DIR` 仅可运行一个实例；
- **密码格式**：仅支持明文配置，每次启动用随机盐值 BCrypt 加密到内存，`{bcrypt}` 预加密格式已废弃；
- **端口冲突**：修改 `SERVER_PORT` 环境变量或 `server.port` 配置项。

## 许可证

本项目暂未指定开源许可证，使用前请联系作者确认授权。
