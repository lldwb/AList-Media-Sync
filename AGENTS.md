# AGENTS.md — AList-Media-Sync

## AI 工作指令

以下规则约束 AI 在此项目中的行为，优先级高于任何默认行为。**规则与 `.specify/memory/constitution.md`（章程版本 1.8.0）的不可协商原则对齐，冲突时以章程为准**。

> **文件权重体系**：constitution.md（宪法）> AGENTS.md 根级（法律·全局）> 前端/后端 AGENTS.md（行政法规）> 模块 AGENTS.md（地方性法规）。本文件为全局级法律层，覆盖项目的日常修改和 AI 行为约束，前端/后端及各模块 AGENTS.md 在此基础上逐级细化。

1. **直接执行，跳过冗余分析** — 收到任务后直接开始实现，不要先输出"我先了解代码库结构"或"让我分析需求"之类的开场白。AGENTS.md 已包含足够的架构上下文，直接动手。
2. **先读模块 AGENTS.md** — 修改某个模块前，先读取该模块目录下的 `AGENTS.md` 了解功能、作用和关联。模块索引见下方"模块 AGENTS.md 索引"章节。
3. **禁止凭空编写规格文档** — 除非用户明确要求编写 spec/plan/tasks，否则不要创建或修改 `specs/` 目录下的任何文件。功能设计通过代码和注释表达。（对应章程原则 VI: YAGNI）
4. **先读后改，精准定位** — 修改前先阅读目标文件，理解现有实现后再改。不要猜测代码内容。
5. **最小化输出** — 完成任务后简要说明做了什么，不要长篇总结。代码本身就是最好的文档。
6. **遵循现有模式** — 新增代码保持与同模块现有代码一致的风格、命名和注释密度。不要引入新的架构模式除非任务明确要求。
7. **严格遵守分层架构** — Controller 不写业务逻辑，Service 承载核心逻辑，Repository 仅数据持久化。禁止跨层调用。（章程原则 I）
8. **代码变更必须同步测试** — 每次修改 Java 类文件后，MUST 同步修改或新增对应的单元测试。（章程原则 V）
9. **日志规范不可省略** — 所有重要操作 MUST 按 DEBUG/INFO/WARN/ERROR 四级输出日志，API 调用和本地文件操作 MUST 记录输入输出。（章程原则 VII）
10. **中文优先** — 所有文档、注释、日志消息、提交信息 MUST 使用简体中文。对外 API 字段名和错误信息使用英文。（章程原则 IV）

---

## 章程合规检查清单

以下为 constitution.md 中定义的质量门禁，代码审查时必须逐项验证：

| # | 检查项 | 章程原则 |
|---|--------|---------|
| 1 | 分层架构合规（Controller/Service/Repository 职责清晰） | I |
| 2 | 实体有 `@Version` 乐观锁，写操作有 `@Transactional` | II |
| 3 | 统一 `ApiResult<T>` 封装，DTO 不暴露 Entity | III |
| 4 | 注释、日志、提交信息使用简体中文 | IV |
| 5 | Java 类变更同步更新单元测试 | V |
| 6 | 未引入不必要的第三方依赖 | VI |
| 7 | 日志输出符合四级分级，API 调用和文件操作有日志 | VII |
| 8 | spec.md 状态字段已同步更新 | VIII |
| 9 | `/speckit-implement` 后 README.md 已更新 | IX |
| 10 | `/speckit-constitution` 后 AGENTS.md 已同步 | X |

---

## 项目简介

AList-Media-Sync 是一个基于 **Spring Boot 4.1.0 + Java 21** 的 AList 网盘媒体同步服务，提供文件同步、媒体转码、Webhook 事件处理等核心能力，配套 React 19 + TypeScript Web 管理前端。支持一体化启动包和 Docker 两种部署方式。

### 核心功能

- **存储引擎**：策略模式支持 AList 远程存储和本地文件系统，统一文件操作接口
- **文件同步**：NEW_ONLY / FULL / MOVE 三种同步模式，支持 CRON / INTERVAL 调度
- **媒体转码**：下载→转码→上传三步流程，8 状态模型，FFmpeg 转码引擎（JAVE2），并发信号量控制
- **Webhook**：接收录播姬 Webhook v2 事件，规则匹配后自动触发同步/转码
- **管理前端**：React 19 + TypeScript SPA，认证登录、引擎管理、同步任务、转码监控、Webhook 规则、仪表板

### 技术栈

| 层 | 技术 |
|---|------|
| 后端框架 | Spring Boot 4.1.0（Java 21 虚拟线程） |
| 持久层 | Spring Data JPA + Hibernate + H2（文件模式） |
| 转码引擎 | JAVE2 (ws.schild) 封装 FFmpeg |
| 加密 | AES-256-GCM（Token 加密）+ BCrypt（密码哈希） |
| 前端 | React 19 + TypeScript + Vite + React Router |
| HTTP 客户端 | Spring RestClient（AList API 调用） |
| 构建工具 | Maven Wrapper（`./mvnw`） |
| 容器化 | Docker 多阶段构建 + Docker Compose |

---

## 项目根目录结构

```
.
├── pom.xml                    # Maven 依赖配置（Spring Boot 4.1.0, Java 21）
├── mvnw / mvnw.cmd           # Maven Wrapper（无需预装 Maven）
├── Dockerfile                 # 多阶段构建（frontend-build → builder → runtime）
├── docker-compose.yml         # Docker Compose 编排（含健康检查、环境变量）
├── .dockerignore              # Docker 构建上下文排除规则
├── .env                       # Docker Compose 环境变量模板
├── .gitignore                 # Git 忽略规则（含 .env）
├── .gitattributes             # Git 行尾规范化
├── assembly/                  # Maven Assembly 插件配置
│   └── bootstrap.xml          # 一体化启动包打包描述符
├── scripts/                   # 构建与启动脚本
│   ├── start.bat              # Windows 一体化启动脚本（含预检查）
│   ├── start.sh               # Linux 一体化启动脚本（含预检查）
│   ├── download-jre.sh        # JRE 下载脚本
│   └── verify-build.sh        # 构建验证脚本
├── specs/                     # 功能规格文档（001-006）
│   ├── 001-alist-media-sync/  # 核心业务（同步、转码、Webhook）
│   ├── 002-transcode-temp-suffix-config/  # 转码增强（临时后缀、磁盘检查）
│   ├── 003-docker-deploy/     # Docker 部署
│   ├── 004-web-management-frontend/  # Web 管理前端
│   ├── 005-standalone-bootstrap/     # 一体化启动包
│   └── 006-storage-engine-refactor/  # 存储引擎重构
└── src/
    ├── main/java/top/lldwb/alistmediasync/   # 后端源码
    ├── main/resources/                       # 配置文件
    ├── main/frontend/                        # 前端源码（React 19 + Vite）
    └── test/java/top/lldwb/alistmediasync/   # 测试代码（镜像 main 结构）
```

---

## 后端包结构 (`src/main/java/top/lldwb/alistmediasync/`)

### 入口类

- **`AListMediaSyncApplication.java`** — `@SpringBootApplication` + `@EnableScheduling`，Spring Boot 入口

### `common/` — 通用模块

| 子包 | 关键类 | 职责 |
|------|--------|------|
| `config/` | `AppProperties` | `@ConfigurationProperties(prefix="app")`，绑定认证、转码、线程池配置 |
| | `AsyncConfig` | 配置 `transcodeExecutor` 线程池（核8/最大32/队64） |
| | `WebMvcConfig` | 拦截器注册、CORS、SPA 静态资源映射 `/app/**` |
| | `WebSocketConfig` | 注册 `/ws/events` 端点，绑定 `WebSocketAuthInterceptor` |
| | `RestClientConfig` | 手动注册 `RestClient.Builder` Bean（Spring Boot 4.x 不会自动注册） |
| | `PasswordEncryptionPostProcessor` | `EnvironmentPostProcessor`，启动时将明文密码 BCrypt 加密到内存 |
| | `CryptoKeyEnvironmentPostProcessor` | `EnvironmentPostProcessor`，将 `ALIST_CRYPTO_KEY` 环境变量桥接到 JVM 系统属性 |
| | `GlobalExceptionHandler` | `@RestControllerAdvice` 全局异常处理 |
| `controller/` | `DashboardController` | 仪表板统计查询 API（`/api/dashboard/stats`） |
| `interceptor/` | `AuthInterceptor` | HTTP Basic 认证拦截器（排除 `/api/webhooks/**`, `/actuator/health`） |
| | `WebSocketAuthInterceptor` | WebSocket 握手认证拦截器（Basic Auth 校验） |
| `entity/` | `CryptoConverter` | JPA `AttributeConverter`，AES-256-GCM 加密/解密数据库字段 |
| `enums/` | `MessageType` | WebSocket 消息类型枚举（SYNC_PROGRESS / TRANSCODE_STATUS 等） |
| `exception/` | `RetryableException` | 可重试异常标识，配合 `RetryService` 使用 |
| `dto/` | `ApiResult<T>` | 统一 API 响应体（code, message, data） |
| | `DashboardStatsVO` | 仪表板统计 VO |
| | `WsMessage` | WebSocket 消息载体（type + payload） |
| `service/` | `DashboardService` | 仪表板统计数据查询 |
| | `CleanupService` | 定时清理过期记录 + 启动清理残留临时文件 |
| | `RetryService` | 通用重试封装（指数退避，配合 `RetryableException`） |
| | `WsSessionManager` | WebSocket 会话注册表，提供广播能力 |
| `util/` | `ApiUtil` | 通用 API 辅助工具 |
| | `DiskSpaceChecker` | 转码前磁盘空间检查（预估 1.5 倍安全阈值） |
| | `MagicBytesDetector` | 文件魔数检测（FLV/MP4/M4V） |
| | `TempFileManager` | 临时文件创建/重命名/删除，UUID 并发安全命名 |
| | `TempSuffixValidator` | 临时文件后缀校验 |
| | `ServerAddressLogger` | 启动时打印服务访问地址 |

### `storage/` — 存储引擎模块（策略模式）

这是系统的核心抽象层，通过策略模式实现多存储后端支持。

```
storage/
├── controller/
│   └── StorageEngineController    # 存储引擎 CRUD + 连接测试 API
├── dto/storage/
│   ├── StorageEngineCreateDTO     # 创建引擎请求体
│   ├── StorageEngineUpdateDTO     # 更新引擎请求体
│   └── StorageEngineVO            # 引擎视图（已脱敏）
├── entity/
│   └── StorageEngine              # JPA 实体（EngineType: ALIST/LOCAL, EngineStatus: ONLINE/OFFLINE/ERROR）
├── repository/
│   └── StorageEngineRepository    # Spring Data JPA Repository
└── service/
    ├── StorageEngineService       # 引擎管理 + 策略分发（按 engineType 选择策略）
    └── engine/                    # ⭐ 策略接口与实现
        ├── StorageEngineStrategy  # 策略接口（type(), listFiles, getFileInfo, downloadFile, uploadFile, ...）
        ├── AListStorageStrategy   # AList 远程策略（HTTP 调用 AList REST API）
        └── LocalStorageStrategy   # 本地路径策略（java.nio.file 操作）
```

**策略分发机制：** `StorageEngineService` 通过构造器注入 `List<StorageEngineStrategy>`，按 `type()` 方法构建 `Map<String, StorageEngineStrategy>`，`resolve(engine)` 方法按 `engine.getEngineType().name()` 分发。

**AListStorageStrategy 关键实现细节：**
- 使用 Spring `RestClient` 调用 AList API（`/api/fs/list`, `/api/fs/get`, `/api/fs/put`, `/api/fs/mkdir`, `/api/fs/remove`）
- Token 通过 `engine.getEncryptedToken()` 获取（CryptoConverter 自动解密）
- `listDirectories()` 通过 `fetchAllEntries()` 分页获取所有条目后过滤目录
- 分页参数：PAGE_SIZE=50，循环直到条目数 < 50 或返回空
- 上传使用 `MultipartFormData` + `File-Path` header + `As-Task: true`

**LocalStorageStrategy 关键实现细节：**
- 通过 `resolvePath()` 将相对路径解析为本地绝对路径（`localPath + relativePath`）
- `listFiles()` 返回排序后的列表（目录在前、名称升序）
- `uploadFile()` 自动创建父目录，8KB 缓冲区流式写入
- `deleteFile()` 目录递归删除，文件直接删除
- `listDirectories()` 包含 `hasChildren` 判断（子目录探测）

### `sync/` — 同步任务模块

```
sync/
├── controller/
│   └── SyncTaskController         # 同步任务 CRUD + 手动触发 + 进度查询
├── dto/sync/
│   ├── SyncTaskCreateDTO / UpdateDTO / VO
│   ├── TaskExecutionVO            # 执行记录视图
│   ├── SyncProgressVO             # 同步进度视图
│   ├── FileEntry                  # 文件条目 record（name, path, isDirectory, size, modifiedTime）
│   └── DirectoryEntryVO           # 目录条目 VO（name, path, hasChildren）
├── entity/
│   ├── SyncTask                   # 同步任务实体（SyncMode, ScheduleType, ConflictStrategy, 排除规则）
│   └── TaskExecution              # 任务执行记录（TaskType: SYNC/TRANSCODE/WEBHOOK, ExecutionStatus）
├── repository/
│   ├── SyncTaskRepository         # 含 findByEnabledTrue, findBySyncMode 等
│   └── TaskExecutionRepository    # 含 markAllRunningAsInterrupted() 批量更新
└── service/
    ├── SyncService                # 核心同步引擎（扫描→比对→执行三阶段，大文件/小文件分流）
    ├── SyncTaskManageService      # 同步任务 CRUD 管理
    └── ScheduleService            # 定时调度管理（CRON / INTERVAL，@PostConstruct 恢复调度）
```

**SyncService 同步三阶段：**
1. **扫描阶段**：`scanDirectory()` 递归遍历源目录，应用 Glob 排除规则
2. **比对阶段**：构建目标文件集合，按 SyncMode 计算差异集
3. **执行阶段**：流式下载→上传，>100MB 使用临时文件中转，≤100MB 直接流式传输

### `transcode/` — 转码模块

```
transcode/
├── controller/
│   └── TranscodeTaskController    # 转码任务 CRUD + 手动触发 + 重试
├── dto/transcode/
│   ├── TranscodeTaskCreateDTO     # 创建 DTO（含 sourceDirectoryTranscode 字段）
│   └── TranscodeTaskVO            # 转码任务视图
├── entity/
│   └── TranscodeTask              # 转码任务实体（TranscodeStatus 8状态, TargetFormat MP3/MP4/FLV）
├── repository/
│   └── TranscodeTaskRepository
└── service/
    ├── TranscodeService           # 转码编排层（三步流程 + 状态机 + 并行处理）
    ├── TranscodeFileProcessor     # 单文件处理器（下载→FFmpeg→上传 + Semaphore 并发控制）
    ├── TranscodeCandidate         # 转码候选文件 record
    └── TranscodeResult            # 转码结果 record
```

**8 状态模型：**
```
PENDING(0) → DOWNLOADING(1) → TRANSCODING(3) → UPLOADING(5) → COMPLETED(7)
               ↓(失败)            ↓(失败)          ↓(失败)
          DOWNLOAD_FAILED(2)  TRANSCODE_FAILED(4)  UPLOAD_FAILED(6)
               ↓(重试)            ↓(重试)          ↓(重试)
          DOWNLOADING(1)      TRANSCODING(3)      UPLOADING(5)
```
- 使用 `Semaphore` 控制并发数（由 `app.transcode.max-concurrent-transcode` 配置，默认 32）
- `TranscodeService.createTask()` 支持 `sourceDirectoryTranscode` 选项（输出至源文件所在目录）
- `retry()` 方法从失败步骤继续（保留已完成步骤的临时文件）

### `webhook/` — Webhook 模块

```
webhook/
├── controller/
│   ├── WebhookController          # 接收录播姬 Webhook v2 事件（无认证路径）
│   ├── WebhookRuleController      # Webhook 规则 CRUD
│   └── WebhookEventController     # Webhook 事件查询
├── dto/webhook/
│   ├── WebhookRuleCreateDTO / VO
│   └── WebhookEventVO
├── entity/
│   ├── WebhookRule                # 规则实体（action: SYNC_ONLY/TRANSCODE_ONLY/BOTH）
│   └── WebhookEvent               # 事件实体（WebhookEventType, EventStatus）
├── repository/
│   ├── WebhookRuleRepository
│   └── WebhookEventRepository
└── service/
    ├── WebhookService             # 事件接收+异步处理（EventId 去重 → 规则匹配 → 执行动作）
    └── WebhookRuleService         # 规则 CRUD 管理
```

---

## 配置文件

| 文件 | 用途 |
|------|------|
| `src/main/resources/application.yaml` | 主配置（服务端口、数据源、JPA、日志、AList、转码、线程池） |
| `src/main/resources/application.template.yaml` | 启动包配置模板 |
| `src/test/resources/application-test.yaml` | 测试环境配置 |
| `src/main/frontend/package.json` | 前端依赖（React 19, React Router, Vite） |

### 关键配置项（`app.*` 命名空间）

| 配置键 | 默认值 | 环境变量覆盖 |
|--------|--------|-------------|
| `app.data-dir` | `./data` | `DATA_DIR` |
| `app.retention-days` | `30` | — |
| `app.auth.username` | `admin` | — |
| `app.auth.password` | `admin123` | `APP_AUTH_PASSWORD` |
| `app.transcode.temp-suffix` | `.tmp` | `TRANSCODE_TEMP_SUFFIX` |
| `app.transcode.temp-dir` | `${java.io.tmpdir}/alist-media-sync/transcode` | `TRANSCODE_TEMP_DIR` |
| `app.transcode.max-concurrent-transcode` | `32` | `TRANSCODE_MAX_CONCURRENT` |
| `app.transcode.default-bitrate` | `128000` | `TRANSCODE_DEFAULT_BITRATE` |
| `app.pool.core-size` | `8` | — |
| `app.pool.max-size` | `32` | — |

### 关键环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `ALIST_BASE_URL` | 是 | AList 服务器地址 |
| `ALIST_TOKEN` | 是 | AList API 认证令牌 |
| `ALIST_CRYPTO_KEY` | 否 | AES-256 密钥（Base64编码32字节），未设置则随机生成（重启后已加密数据不可解密） |
| `SERVER_PORT` | 否 | 应用监听端口，默认 8080 |
| `LOGGING_LEVEL` | 否 | 日志级别，默认 INFO |
| `JAVA_OPTS` | 否 | JVM 参数，默认 `-Xms128m -Xmx256m` |

---

## 构建与运行

### 本地开发

```bash
# 启动后端（使用虚拟线程，H2 文件数据库）
./mvnw spring-boot:run

# 运行全部测试
./mvnw test

# 前端开发（需要 Node.js）
cd src/main/frontend
npm install
npm run dev

# 构建前端
npm run build
```

### Docker 部署

```bash
# Docker Compose（推荐）
docker compose up -d

# 手动构建
docker build -t alist-media-sync .
docker run -d -p 8080:8080 -e ALIST_BASE_URL=... -e ALIST_TOKEN=... -v alist-data:/app/data alist-media-sync
```

### 一体化启动包

```bash
# 打包（Maven Assembly）
./mvnw package -DskipTests
# 产物在 target/alist-media-sync-{version}-*.zip / *.tar.gz

# 解压后启动
# Windows: 双击 start.bat 或命令行运行
# Linux: ./start.sh
# 访问: http://localhost:8080/app/
# 默认登录: admin / admin123
```

---

## 架构设计原则

1. **策略模式**：存储引擎通过 `StorageEngineStrategy` 接口抽象，新增后端只需实现接口 + `@Component` 注解
2. **YAGNI**：认证使用自定义拦截器（80行），不引入 Spring Security
3. **密码安全**：仅支持明文配置，启动时 BCrypt 加密到内存，绝不回写文件
4. **Token 加密**：`CryptoConverter` 使用 AES-256-GCM 自动加密/解密数据库中的 Token
5. **并发控制**：转码使用 `Semaphore` 限流，线程池核8/最大32，拒绝策略 `CallerRunsPolicy`
6. **优雅关闭**：应用 12s 关闭超时 + Docker Compose 35s 停止宽限期
7. **健康检查**：通过 `/actuator/health` 端点暴露（Docker Compose 使用 wget 探测）

---

## 模块 AGENTS.md 索引

每个模块目录下有一份 `AGENTS.md`，描述该模块的功能、作用和模块间关联。修改某模块时先读其 AGENTS.md。前后端各有一份入口 `AGENTS.md` 作为行政法规层。

### 后端

| 层级 | AGENTS.md 路径 | 一句话说明 |
|------|---------------|-----------|
| 行政法规 | `src/main/java/…/AGENTS.md` | 后端入口（技术栈、模块索引、合规要点） |
| 地方性法规 | `src/main/java/…/common/AGENTS.md` | 共享基础设施（配置、认证、加密、工具） |
| 地方性法规 | `src/main/java/…/storage/AGENTS.md` | 策略模式存储引擎（AList 远程 + 本地） |
| 地方性法规 | `src/main/java/…/sync/AGENTS.md` | 文件同步引擎（三模式+三阶段） |
| 地方性法规 | `src/main/java/…/transcode/AGENTS.md` | 媒体转码引擎（三步流程+8状态） |
| 地方性法规 | `src/main/java/…/webhook/AGENTS.md` | Webhook 事件接收+规则匹配 |

### 前端

| 层级 | AGENTS.md 路径 | 一句话说明 |
|------|---------------|-----------|
| 行政法规 | `src/main/frontend/AGENTS.md` | 前端入口（技术栈、模块索引、路由表） |
| 地方性法规 | `src/main/frontend/src/api/AGENTS.md` | HTTP 请求封装（fetch + Basic Auth） |
| 地方性法规 | `src/main/frontend/src/types/AGENTS.md` | TypeScript 类型定义 |
| 地方性法规 | `src/main/frontend/src/auth/AGENTS.md` | 认证状态管理（Context + 超时） |
| 地方性法规 | `src/main/frontend/src/components/AGENTS.md` | 可复用 UI 组件（布局/表单/基础） |
| 地方性法规 | `src/main/frontend/src/hooks/AGENTS.md` | React Hooks（轮询/分页） |
| 地方性法规 | `src/main/frontend/src/pages/AGENTS.md` | 页面组件（7个路由页面） |
| 地方性法规 | `src/main/frontend/src/router/AGENTS.md` | Hash 路由表 + 认证守卫 |
| 地方性法规 | `src/main/frontend/src/utils/AGENTS.md` | 工具函数（格式化/校验/Cron） |

### 对接系统文档

修改对接相关代码（`storage/service/engine/AListStorageStrategy`、`webhook/service/WebhookService`）前，MUST 先读 `md/` 下对应外部系统的 AGENTS.md 与接口 md，以外部契约为事实来源。

| 层级 | AGENTS.md 路径 | 一句话说明 |
|------|---------------|-----------|
| 行政法规 | `md/AGENTS.md` | 对接系统总入口（对接模式、系统索引、文档同步） |
| 地方性法规 | `md/alist/AGENTS.md` | AList REST API 参考（auth/fs/public/admin/Schemas，源自 Apifox） |
| 地方性法规 | `md/danmuji/AGENTS.md` | 录播姬 Webhook v2 协议参考（事件矩阵、幂等约束） |

---

## 引用规格文档

如需了解详细设计决策，请参阅：

- 核心业务功能：`specs/001-alist-media-sync/plan.md`
- 转码增强功能：`specs/002-transcode-temp-suffix-config/plan.md`
- 容器化部署：`specs/003-docker-deploy/plan.md`
- Web 管理前端：`specs/004-web-management-frontend/plan.md`
- 一体化启动包：`specs/005-standalone-bootstrap/plan.md`
- 存储引擎重构：`specs/006-storage-engine-refactor/plan.md`
- 密码加密与代码组织：`specs/007-password-encryption-and-code-organization/plan.md`
<!-- SPECKIT START -->
- 轻量诊断系统：`specs/009-lightweight-diagnostics/plan.md`
<!-- SPECKIT END -->
