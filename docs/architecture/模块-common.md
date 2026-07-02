# common 模块 — 通用基础设施

> 本文档为 US4 架构设计细化文档之一，描述 common 模块的职责边界、核心类、关键流程与扩展点。总览见 [03-架构设计.md](../03-架构设计.md)。

## 职责边界

common 模块是整个系统的共享基础设施层，被所有业务模块（storage / sync / transcode / webhook）依赖。其职责涵盖：

- **配置绑定**：通过 `AppProperties` 统一绑定 `app.*` 配置命名空间，作为配置层唯一入口
- **认证与安全**：HTTP Basic 认证拦截器、WebSocket 握手认证、AES-256 字段加密、BCrypt 密码加密
- **工具类**：磁盘空间检查、文件魔数检测、临时文件管理、路径拼接、敏感数据脱敏、traceId 上下文管理
- **异常处理**：全局异常处理器统一转换为 `ApiResult<T>`
- **诊断与仪表盘**：轻量诊断系统、仪表板统计、定时清理服务
- **WebSocket**：会话管理与广播能力
- **API 文档**：OpenAPI / Swagger 自动生成

common 模块本身不承载任何业务逻辑，仅提供技术基础设施和跨模块共享抽象。遵循章程原则 I（分层架构），common 模块内的 Controller 仅负责 HTTP 请求处理，Service 承载统计/诊断逻辑，Repository 由各业务模块自行维护。

## 核心类

### config 子包 — 配置层

#### AppProperties

`@ConfigurationProperties(prefix = "app")`，绑定所有 `app.*` 配置项，是配置层的唯一入口。包含以下子配置组：

- `auth`：认证配置（username / password）
- `transcode`：转码配置（tempSuffix / tempDir / maxConcurrentTranscode / defaultBitrate）
- `pool`：线程池配置（coreSize / maxSize）
- `dataDir`：数据目录路径
- `retentionDays`：记录保留天数

#### AsyncConfig

配置 `transcodeExecutor` 线程池 Bean：核心 8 线程、最大 32 线程、队列容量 64，拒绝策略 `CallerRunsPolicy`。转码任务通过 `@Async("transcodeExecutor")` 提交到此线程池。

#### WebMvcConfig

实现 `WebMvcConfigurer`，注册以下组件：
- `AuthInterceptor` 拦截器注册（排除 `/api/webhooks/**`、`/actuator/health`）
- CORS 配置
- SPA 静态资源映射：将 `/app/**` 映射到前端构建产物

#### WebSocketConfig

继承 `TextWebSocketHandler` 或通过 `@EnableWebSocket` 注册 `/ws/events` 端点，绑定 `WebSocketAuthInterceptor` 进行握手认证，连接后注册到 `WsSessionManager`。

#### RestClientConfig

手动注册 `RestClient.Builder` Bean。Spring Boot 4.x 不会自动注册此 Bean，必须手动声明。配置请求/响应拦截器，统一记录 HTTP 请求方法、URI、状态码和耗时，并注入 MDC traceId 透传。

#### TraceIdFilter

`OncePerRequestFilter` 实现，作为全链路追踪的入口：
- 从请求头读取或生成 traceId，写入 MDC
- 注入 `X-Trace-Id` 响应头（覆盖所有 `/api/**` 路径，含异常路径）
- 请求结束后清理 MDC

#### GlobalExceptionHandler

`@RestControllerAdvice` 全局异常处理器，将各类异常统一转换为 `ApiResult<T>` 响应：
- 业务异常 → 对应错误码
- 校验异常 → 400
- 未捕获异常 → 500

#### CryptoKeyEnvironmentPostProcessor

`EnvironmentPostProcessor` 实现，在 Spring 上下文刷新前将 `ALIST_CRYPTO_KEY` 环境变量桥接到 JVM 系统属性，使 `CryptoConverter` 能通过 `System.getProperty` 读取密钥。若未配置则随机生成（重启后已加密数据不可解密）。

#### PasswordEncryptionPostProcessor

`EnvironmentPostProcessor` 实现，启动时将 `app.auth.password` 明文密码通过 BCrypt 加密后存入内存环境，绝不回写配置文件。遵循 YAGNI 原则，仅使用 Spring Security Crypto 的 BCrypt，不引入完整 Spring Security 框架。

#### OpenApiConfig

`@Configuration` + `@OpenAPIDefinition` + `@SecurityScheme`，配置 OpenAPI 文档元信息与 Basic 认证安全方案，供 `/swagger-ui.html` 端点使用。

### interceptor 子包 — 认证拦截

#### AuthInterceptor

HTTP Basic 认证拦截器（约 80 行），实现 `HandlerInterceptor`：
- 从 `Authorization` 头解析 Basic 凭据
- 与 `AppProperties.auth` 中的用户名和已 BCrypt 加密的密码比对
- 认证失败返回 401
- 排除路径：`/api/webhooks/**`、`/actuator/health`

#### WebSocketAuthInterceptor

WebSocket 握手阶段认证拦截器，在 `beforeHandshake` 中校验 Basic Auth 凭据，认证失败拒绝握手。

### entity 子包

#### CryptoConverter

JPA `AttributeConverter<String, String>`，使用 AES-256-GCM 对数据库字段自动加密/解密：
- `convertToDatabaseColumn`：明文 → 密文
- `convertToEntityAttribute`：密文 → 明文
- 密钥来源：`System.getProperty("alist.crypto.key")`（由 `CryptoKeyEnvironmentPostProcessor` 桥接）
- 被 `StorageEngine` 实体的 Token 字段使用

### util 子包 — 工具类

| 类 | 职责 |
|---|------|
| `TraceContext` | traceId / MDC 上下文管理，提供 `runWith(traceId, module, operation, runnable)` 便捷入口和 `clear()` 清理方法 |
| `PathUtils` | 跨模块路径拼接与拆分工具，处理路径分隔符统一 |
| `SensitiveDataMasker` | 敏感数据脱敏工具，将密码/Token/密钥等替换为 `***`，用于日志和诊断包 |
| `MagicBytesDetector` | 文件魔数检测，识别 FLV/MP4/M4V 等格式 |
| `DiskSpaceChecker` | 转码前磁盘空间检查，预估 1.5 倍安全阈值 |
| `TempFileManager` | 临时文件创建/重命名/删除，UUID 并发安全命名 |
| `TempSuffixValidator` | 临时文件后缀校验，防止未完成转码的文件被误识别 |
| `ApiUtil` | 通用 API 辅助工具 |
| `ServerAddressLogger` | 启动时打印服务访问地址 |

### service 子包

| 类 | 职责 |
|---|------|
| `DashboardService` | 仪表板统计数据查询，聚合各模块统计信息 |
| `DiagnosticService` | 轻量诊断系统核心，生成诊断包（日志摘要 + 配置 + 系统信息），对应 specs/009 |
| `RetryService` | 通用重试封装，指数退避策略，配合 `RetryableException` 使用 |
| `WsSessionManager` | WebSocket 会话注册表，提供广播能力，被 sync/transcode 模块用于实时进度推送 |
| `CleanupService` | 定时清理过期记录 + 启动时清理残留临时文件 |

### controller 子包

| 类 | 职责 |
|---|------|
| `DashboardController` | 仪表板统计查询 API（`/api/dashboard/stats`） |
| `DiagnosticController` | 诊断系统 API（`POST /api/diagnostics/run`），受认证保护 |

### dto 子包

| 类 | 职责 |
|---|------|
| `ApiResult<T>` | 统一 API 响应体（code / message / data），所有 Controller 端点 MUST 使用此封装 |
| `DashboardStatsVO` | 仪表板统计 VO |
| `WsMessage` | WebSocket 消息载体（type + payload） |
| `DiagnosticResultVO` | 诊断结果 VO |
| `DiagnosticSummaryVO` | 诊断摘要 VO |

### enum 子包

| 类 | 职责 |
|---|------|
| `MessageType` | WebSocket 消息类型枚举（SYNC_PROGRESS / TRANSCODE_STATUS 等） |

## 关键流程

### 请求认证流程

1. `TraceIdFilter` 在请求入口生成/继承 traceId，写入 MDC，设置 `X-Trace-Id` 响应头
2. 请求到达 `AuthInterceptor`（排除 webhook / health 路径）
3. 解析 `Authorization` 头，与 `AppProperties.auth` 中的凭据比对（密码已 BCrypt 加密）
4. 认证通过 → 继续；认证失败 → 401
5. Controller 处理完毕，`TraceIdFilter` 清理 MDC

### 启动加密初始化流程

1. `CryptoKeyEnvironmentPostProcessor` 在上下文刷新前执行：读取 `ALIST_CRYPTO_KEY` 环境变量，桥接到 JVM 系统属性
2. `PasswordEncryptionPostProcessor` 在上下文刷新前执行：读取明文密码，BCrypt 加密后替换环境中的值
3. Spring 容器初始化，`CryptoConverter` 可通过 `System.getProperty` 获取密钥
4. 配置文件中的明文密码不被修改，仅内存中被替换

### 诊断包生成流程

1. 用户通过脚本（diagnose.sh）/ API（`POST /api/diagnostics/run`）/ Web 按钮触发
2. `DiagnosticService` 收集：日志摘要、配置信息（脱敏）、系统信息、数据库状态
3. 生成诊断包到 `diagnostics/{timestamp}/` 目录
4. 输出 `summary.md` 摘要文件
5. 30 秒内返回诊断包路径与摘要位置

## 扩展点

- **新增配置项**：在 `AppProperties` 中添加字段，`application.yaml` 中配置对应 `app.*` 键
- **新增工具类**：在 `util` 子包下创建，保持无状态、可独立测试
- **新增 WebSocket 消息类型**：在 `MessageType` 枚举中添加值，对应 Service 层通过 `WsSessionManager` 广播

## 关联 spec

- `specs/001-alist-media-sync/` — 核心业务（配置、认证、工具基础）
- `specs/007-password-encryption-and-code-organization/` — 密码加密与代码组织
- `specs/009-lightweight-diagnostics/` — 轻量诊断系统
- `.specify/memory/constitution.md` §I（分层架构）、§VII（日志规范）、§VI（YAGNI — 不引入完整 Spring Security）
