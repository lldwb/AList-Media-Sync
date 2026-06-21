# 研究文档：转码与同步模块优化及实时通信改造

**日期**：2026-06-21 | **关联**：[plan.md](./plan.md)

## 1. WebSocket 技术选型

### 决策：Spring 原始 WebSocket（非 STOMP）

**理由**：
- STOMP 协议提供发布/订阅、目标路由等高级特性，但本项目仅需简单的服务端→客户端推送和客户端→服务端轻量消息
- 原始 WebSocket 减少协议层开销，消息格式完全由应用控制（JSON 增量变更）
- 遵循 YAGNI 原则：当前需求不需要 STOMP 的目标路由、确认机制、事务支持
- 与 spec A5 对齐：Spring WebSocket 通过 `spring-boot-starter-websocket` 自动配置

**考虑的替代方案**：
- **STOMP + SockJS**：功能过剩，增加前端 SockJS 依赖，消息格式受 STOMP 帧限制
- **SSE（Server-Sent Events）**：仅支持单向推送，无法满足前端主动发送消息（如心跳/订阅）的需求
- **轮询保持**：已明确要替代的方案，不符合需求

### 决策：连接数上限 50，可配置

**理由**：
- 单实例部署，前端管理页面通常 1-5 个用户同时使用
- 50 个连接上限留有充足余量（含多标签页场景）
- 可配置化允许运维根据实际负载调整

**考虑的替代方案**：
- 无上限：可能导致资源耗尽（每个 WebSocket 连接占用一个线程/内存）
- 硬编码固定值：失去运维灵活性

---

## 2. WebSocket 认证机制

### 决策：HTTP Upgrade 握手阶段通过 Authorization 请求头传递 Basic Auth 凭据

**理由**：
- 与 REST API 认证方式完全一致（Basic Auth），前端无需维护两套认证逻辑
- 浏览器 WebSocket API 允许在构造函数中传入子协议，但 Authorization 头通过 HTTP Upgrade 请求传递最为简洁
- Spring `HandshakeInterceptor` 可在 `beforeHandshake` 中读取 `ServletRequest` 的 `Authorization` 头进行认证
- 认证失败时拒绝 WebSocket 升级请求（返回 HTTP 401），前端不退化为轮询

**考虑的替代方案**：
- **URL 查询参数传递 Token**：Token 暴露在 URL 中，存在日志泄露风险（URL 通常被服务器日志记录）
- **首条消息认证**：需要额外的状态机管理"已认证/未认证"连接，增加复杂度
- **Cookie/Session**：需要引入 Spring Session，违反 YAGNI

---

## 3. WebSocket 消息格式

### 决策：JSON 增量变更模式

**格式**：
```json
{
  "type": "TRANSCODE_PROGRESS",
  "payload": {
    "taskId": 1,
    "progressPercent": 45,
    "status": "TRANSCODING"
  },
  "timestamp": "2026-06-21T10:30:00"
}
```

**理由**：
- 增量变更模式（仅推送变更字段）最小化消息体积，减少带宽消耗
- 前端维护本地状态，收到增量后合并（如 `{ ...prev, ...payload }` 浅合并），无需重新拉取全量列表
- JSON 格式前端原生支持，无需额外解析器

**消息类型**：
- `SYNC_PROGRESS` — 同步任务状态/进度变更
- `TRANSCODE_PROGRESS` — 转码任务状态/进度变更
- `TASK_EVENT` — 任务创建/删除/完成
- `WEBHOOK_EVENT` — Webhook 事件接收/处理状态变更
- `DASHBOARD_UPDATE` — 仪表板统计数据变更（防抖 2 秒合并）

**考虑的替代方案**：
- **全量列表推送**：消息体过大，高频率下浪费带宽，前端需重新渲染整个列表
- **二进制格式（Protobuf/MessagePack）**：增加编解码依赖，浏览器端支持不如 JSON 原生
- **仅推送事件类型不携带数据**：前端仍需额外 HTTP 请求获取详情，失去 WebSocket 优势

---

## 4. WebSocket 前端重连策略

### 决策：指数退避重连，最大间隔 30 秒

**理由**：
- 与服务端断开后自动重连，无需用户手动刷新页面
- 指数退避避免重连风暴（当服务端重启时所有客户端同时重连）
- 最大 30 秒间隔保证在服务端恢复后 30 秒内重连
- 认证失败（401）时不重连，直接跳转登录页

**退避序列**：1s → 2s → 4s → 8s → 16s → 30s → 30s → ...

**考虑的替代方案**：
- **固定间隔重连**：在服务端重启时可能造成重连风暴
- **不退化为 HTTP 轮询**：spec 明确要求不退化为轮询
- **无限重试**：认证失败时不应重试，否则用户永远看不到重新登录提示

---

## 5. 自动重试机制设计

### 决策：RetryableException 标记接口 + ScheduledExecutorService 调度

**理由**：
- `RetryableException` 标记接口是区分瞬时故障和业务逻辑错误的最简方案
- 网络超时（`SocketTimeoutException`）、HTTP 5xx、连接拒绝等包装为 `RetryableException` 子类
- 文件不存在（404）、格式不支持、权限不足等不实现此接口，直接标记为最终失败
- 使用 `ScheduledExecutorService` 调度重试，不占用线程池工作线程
- 重试从失败步骤开始（不回到 PENDING），遵循 006 的重试逻辑

**指数退避公式**：`min(1000 * 2^(attempt-1), 60000)` 毫秒
| 次数 | 间隔 |
|------|------|
| 第 1 次 | 1s |
| 第 2 次 | 2s |
| 第 3 次 | 4s |
| 第 4 次 | 8s（如 max-auto-retries 配置为 >3） |
| ... | ... |
| 最大值 | 60s |

**考虑的替代方案**：
- **Spring Retry**：功能齐全但引入额外依赖，且 `@Retryable` 注解在虚拟线程上行为未充分验证
- **Resilience4j**：功能过剩，项目不需要断路器、限流、舱壁等模式
- **手写 while 循环**：阻塞工作线程，影响并发处理能力

### 决策：TranscodeTask 实体存储 retryCount

**理由**：
- TranscodeTask 是重试的实际执行单元（每个文件一条记录）
- `retryCount` 整型字段，默认 0，每次自动重试递增
- TaskExecution 的 failureDetails JSON 同步记录 `retryCount` 和 `maxRetries` 用于同步任务执行详情展示
- 手动重试不计入自动重试次数，始终执行

**考虑的替代方案**：
- **仅存 TaskExecution.failureDetails JSON**：JSON 字段无法用 JPA 查询，无法在前端转码列表直接展示
- **TaskExecution 新增 retryCount 列**：TaskExecution 是任务级别，TranscodeTask 是文件级别，粒度不匹配

---

## 6. 同引擎复制实现

### 决策：StorageEngineStrategy 新增 copyFile() 方法，AList 批量调用 /api/fs/copy

**理由**：
- copyFile 是存储引擎的基本能力，放在策略接口中符合开闭原则（OCP）
- AList 的 `/api/fs/copy` 接口支持 `names` 数组批量复制，按源目录分组批量调用减少 API 请求次数
- SKIP 文件先过滤再批量请求，仅对需要复制/覆盖的文件发起请求
- 同引擎复制不经过本地磁盘，性能提升至少一个数量级

**AList `/api/fs/copy` 请求体**：
```json
{
  "src_dir": "/videos/source/",
  "dst_dir": "/videos/target/",
  "names": ["file1.mp3", "file2.mp4"]
}
```

**前置条件**：目标目录必须已存在（调用前通过 `createDirectory` 确保）

**考虑的替代方案**：
- **保持下载→上传流程**：同引擎场景下效率极低，浪费网络带宽和处理时间
- **按文件大小分流**：spec 明确不需要分流，AList 服务端 copy 是内部操作不经过客户端网络
- **在 SyncService 中直接调用 RestClient**：破坏分层架构（SyncService 不应直接操作 HTTP 客户端），违反原则 I

---

## 7. 临时文件清理策略

### 决策：统一为 006 的 24 小时定时清理策略

**理由**：
- 替代 002 的"启动时无条件清理"策略
- 上传成功后立即清理临时文件
- 失败状态下保留供重试
- 定时任务（`CleanupService`）清理超过 24 小时的孤立任务和临时文件
- 同步临时文件（`{temp-dir}/sync/`）由 SyncService 自行管理生命周期（完成/失败后立即清理）

**影响的 spec**：002（临时文件清理策略变更）

---

## 8. 新增配置项

| 配置键 | 默认值 | 环境变量 | 说明 |
|--------|--------|----------|------|
| `app.websocket.max-connections` | 50 | `APP_WEBSOCKET_MAX_CONNECTIONS` | WebSocket 最大并发连接数 |
| `app.retry.max-auto-retries` | 3 | `APP_RETRY_MAX_AUTO_RETRIES` | 最大自动重试次数 |
| `app.retry.initial-interval` | 1000 | — | 初始重试间隔（毫秒） |
| `app.retry.max-interval` | 60000 | — | 最大重试间隔（毫秒） |
| `app.storage.health-check-interval` | 300 | — | 存储引擎健康检查间隔（秒） |
| `sync.transcode-target-format` | MP3 | — | 同步后置转码默认目标格式 |
| `sync.transcode-bitrate` | 128000 | — | 同步后置转码默认码率 |

**理由**：所有配置项遵循现有 `app.*` 命名空间约定，通过 `@ConfigurationProperties` 绑定，支持环境变量覆盖。

---

## 9. 与 004/006/007 的实现依赖关系

### 决策：串行实现 — 006 → 007 → 008

**理由**：
- 006（存储引擎重构）：008 依赖 006 的 8 状态模型和策略模式接口（StorageEngineStrategy）
- 007（密码加密与代码组织）：008 的 DTO 字段重命名（sameDirectoryTranscode → sourceDirectoryTranscode）依赖 007 的实现状态
- 008 可在 006 完成后开始，007 的部分变更（如包路径调整）不影响 008 的核心逻辑

**具体依赖**：
- **强依赖 006**：8 状态模型（DOWNLOAD_FAILED/TRANSCODE_FAILED/UPLOAD_FAILED）、StorageEngineStrategy 接口、LocalStorageStrategy
- **弱依赖 007**：DTO 字段名——若 007 已完成则修改为 sourceDirectoryTranscode；若未完成则 008 直接使用新字段名
- **并行可能**：008 的前端部分（WebSocket 迁移、批量操作 UI）可与 007 的后端重组并行开发

**兼容性保障**：
- 008 实现时检查 006 代码状态：若失败状态未按步骤精确设置，008 在 TranscodeFileProcessor 中补充
- copyFile() 方法在接口中新增默认实现（抛 UnsupportedOperationException），AList 和 Local 策略各自覆盖
