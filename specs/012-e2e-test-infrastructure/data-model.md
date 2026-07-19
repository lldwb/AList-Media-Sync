# 数据模型：端到端测试基础设施

**功能**：`012-e2e-test-infrastructure` | **阶段**：1（设计） | **日期**：2026-07-19

**说明**：本功能为测试基础设施，**不引入新业务实体**（不修改 `src/main/java` 下的 Entity 类）。本数据模型聚焦测试侧的"实体"：测试环境实例、测试 fixtures、链路断言点、测试 profile 配置。业务实体（SyncTask、TaskExecution、WebhookEvent、TranscodeTask 等）的状态转换作为 E2E 断言对象，此处仅引用验证点，不重复定义。

---

## 1. 测试环境实例实体

E2E 运行时由三个真实实例组成，通过 `E2ELifecycleManager` 管理生命周期。

### 1.1 AListInstance

| 字段 | 类型 | 说明 |
|------|------|------|
| binaryPath | Path | AList 可执行文件路径（`scripts/e2e/bin/alist/alist.exe`） |
| port | int | HTTP 端口，默认 5344 |
| dataDir | Path | AList 数据目录（独立于生产，如 `scripts/e2e/data/alist/`） |
| adminPassword | String | 初始管理员密码（E2E 固定值，非生产密码） |
| storageMount | String | 测试存储挂载路径（如 `/e2e-test`） |
| pid | int | 运行中进程 PID（写入 `alist.pid` 文件） |
| readyCheck | URL | 就绪探活地址（`http://localhost:5344/ping`，期望返回 `pong`） |

**验证规则**：启动后 `/ping` 必须在 30 秒内返回 `pong`；存储挂载必须可读写。

### 1.2 DanmujiInstance

| 字段 | 类型 | 说明 |
|------|------|------|
| binaryPath | Path | 录播姬可执行文件路径（`scripts/e2e/bin/danmuji/BililiveRecorder.exe`） |
| webuiPort | int | WebUI 端口，默认 2356 |
| workDir | Path | 录播姬工作目录（录制文件输出于此，如 `scripts/e2e/data/danmuji/`） |
| webhookUrl | URL | webhook 回调地址（`http://localhost:{server.port}/api/webhooks/recorder`） |
| configPath | Path | 配置文件路径（`scripts/e2e/e2e-config/danmuji.config.toml`） |
| pid | int | 运行中进程 PID |
| readyCheck | URL | 就绪探活地址（WebUI 首页 HTTP 200） |

**验证规则**：启动后 WebUI 必须在 30 秒内响应；webhookUrl 必须可达系统端点。

### 1.3 SystemInstance

| 字段 | 类型 | 说明 |
|------|------|------|
| port | int | 系统端口（`application-e2e.yaml` 配置，默认 8080） |
| configProfile | String | 配置 profile（`e2e`） |
| dataDir | Path | 系统数据目录（`./data-e2e/`，独立于生产 `./data/`） |
| alistTargetEngine | String | E2E 预置的 AList 存储引擎配置（指向 AListInstance） |
| readyCheck | URL | 就绪探活（Actuator `/actuator/health`，期望 `{"status":"UP"}`） |

**验证规则**：启动后 Actuator health 必须在 60 秒内返回 UP；存储引擎连接测试必须通过。

---

## 2. 测试 fixtures 实体

fixtures 存放在 `src/test/resources/fixtures/`，作为测试输入的固定数据。

### 2.1 WebhookEventFixture（录播姬 webhook 事件样本）

| 字段 | 类型 | 说明 | 来源契约 |
|------|------|------|---------|
| EventType | String | 事件类型（`FileClosed`/`SessionStarted`） | `md/danmuji/webhook.md` |
| EventId | String (UUID) | 事件唯一标识，固定值便于幂等测试 | v2 协议 |
| EventTimestamp | String (ISO 8601) | 事件时间戳，含时区 | v2 协议 |
| EventData | Object | 事件数据，含 RoomId/ShortId/Name/Title/RelativePath/FileSize/Duration 等 | v2 协议 |

**验证规则**：
- `fileclosed-event.json` 必须包含 `RelativePath`、`FileSize`、`Duration`、`FileOpenTime`、`FileCloseTime`、`SessionId` 字段
- `EventId` 用固定 UUID（如 `e2e-test-fileclosed-0001`），便于重复发送验证幂等去重
- 结构必须与 `md/danmuji/webhook.md` v2 协议严格一致

**用途**：
- 集成测试：直接作为 `WebhookService.receiveWebhookEvent` 输入验证去重逻辑
- E2E 降级：当真实录播姬不可用时（R5 skip 机制），可作为契约基准对比真实事件格式

### 2.2 MediaFixture（测试媒体文件）

| 字段 | 类型 | 说明 |
|------|------|------|
| fileName | String | 文件名（如 `sample.mp4`） |
| format | String | 格式（mp4，转码目标格式测试用） |
| sizeBytes | long | 体积（<5MB） |
| durationSeconds | int | 时长（<30 秒，短时长降低转码耗时） |
| bitrate | int | 码率（低码率，降低体积） |

**验证规则**：体积 <5MB；可被 JAVE2 识别与转码；转码后体积合理（不为 0，不超原体积 2 倍）。

### 2.3 AListResponseFixture（AList 期望响应样本）

| 字段 | 类型 | 说明 | 来源契约 |
|------|------|------|---------|
| code | int | 200（AList 统一成功码） | `md/alist/AGENTS.md` |
| message | String | "success" | 同上 |
| data.content | Array | 文件列表（name/path/is_dir/size/modified） | 同上 |
| data.total | int | 总条目数 | 同上 |

**验证规则**：结构必须与 `md/alist/` 契约一致；用于 WireMock 桩响应与断言基准。

---

## 3. 链路断言点实体

E2E 链路中可观察的验证点，`*E2ETest.java` 通过这些断言点验证链路正确性。

### 3.1 链路断言点清单

| 断言点 | 观察方式 | 验证内容 | 对应需求 |
|--------|---------|---------|---------|
| AP1 webhook 接收 | HTTP 响应 `ApiResult<String>` 含 eventId | 系统成功接收录播姬 webhook，返回 2xx | FR-001 |
| AP2 事件持久化 | 查询 `webhook_event` 表 | WebhookEvent 入库，状态非 DUPLICATE | FR-007 |
| AP3 幂等去重 | 重复发送同一 EventId 后查询 | 第二次标记 DUPLICATE，不产生重复任务 | FR-007、原则 II |
| AP4 任务创建 | 查询 `sync_task`/`transcode_task` 表 | 规则匹配后创建对应任务 | FR-001 |
| AP5 任务执行 | 查询 `task_execution` 表 | 执行记录持久化，状态 SUCCESS | FR-007 |
| AP6 文件落盘 | AList API `/api/fs/list` 查询 | 目标路径出现同步文件 | FR-001 |
| AP7 转码完成 | 查询 `transcode_task` 表 + AList 查询 | 转码任务 SUCCESS，转码后文件存在 | FR-001 |
| AP8 traceId 一致 | 响应头 `X-Trace-Id` + 诊断包 | 全链路日志携带同一 traceId | FR-006、原则 VII |
| AP9 error.log 分流 | 读取 `logs/error.log` | ERROR 事件同时出现在 app.log 与 error.log | 原则 VII §7.4 |

### 3.2 业务实体状态转换（E2E 断言对象）

E2E 验证以下业务实体的状态转换（实体本身已在业务代码定义，此处仅列断言期望）：

**WebhookEvent 状态转换**：
```
RECEIVED -> PROCESSING -> PROCESSED
                     \-> DUPLICATE（重复 EventId）
                     \-> FAILED（处理异常）
```

**TaskExecution 状态转换**：
```
PENDING -> RUNNING -> SUCCESS
                \-> FAILED
                \-> INTERRUPTED（markAllRunningAsInterrupted，启动恢复时）
```

**TranscodeTask 状态转换**（8 状态，引用 specs/002）：
```
CREATED -> QUEUED -> RUNNING -> SUCCESS
                       \-> FAILED -> RETRYING -> SUCCESS/FAILED
```

**验证规则**：E2E 断言终态为 SUCCESS（或 DUPLICATE 用于幂等测试）；中间状态可通过轮询 `task_execution` 表观察。

---

## 4. 测试 profile 配置实体

### 4.1 application-e2e.yaml 关键配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `server.port` | 8080（或避让端口） | E2E 系统端口 |
| `spring.datasource.url` | `jdbc:h2:file:./data-e2e/alistmediasync` | H2 文件模式，独立于生产 |
| `spring.jpa.hibernate.ddl-auto` | `create-drop` | 每次启动重建 schema |
| `app.data-dir` | `./data-e2e/app/` | 系统数据目录，独立 |
| `app.transcode.temp-dir` | `./data-e2e/transcode-tmp/` | 转码临时目录 |
| `app.auth.username` / `password` | E2E 固定值 | 测试认证（非生产密码） |
| `alist.base-url` | `http://localhost:5344` | 指向 E2E AList 实例 |
| `logging.level.top.lldwb.alistmediasync` | `DEBUG` | E2E 期间开启 DEBUG 便于链路观察 |

### 4.2 Maven profile 配置实体

| profile | 激活方式 | 触发测试 | 外部依赖 |
|---------|---------|---------|---------|
| 默认 | `mvn test` | surefire: `*Test.java`（排除 IT/E2E） | 无 |
| integration | `mvn verify -Pintegration` | failsafe: `*IT.java` | 无（H2 内存 + WireMock） |
| e2e | `mvn verify -Pe2e` | failsafe: `*E2ETest.java` | 启动 AList + 录播姬 二进制 |

**验证规则**：默认 profile 必须在 60 秒内完成；integration profile 必须在 120 秒内完成；e2e profile 单链路 <5 分钟。

---

## 5. 实体关系图

```
┌─────────────────┐     启动     ┌──────────────────┐
│ E2ELifecycleMgr │─────────────>│  AListInstance   │
│                 │              └──────────────────┘
│                 │     启动     ┌──────────────────┐
│                 │─────────────>│ DanmujiInstance  │
└─────────────────┘              └────────┬─────────┘
        │                                 │ webhook
        │ 管理                             v
        │                        ┌──────────────────┐
        │                        │ SystemInstance   │
        │                        │  (app:8080)      │
        │                        └────────┬─────────┘
        │                                 │ 驱动
        v                                 v
┌─────────────────┐              ┌──────────────────┐
│   fixtures/     │─────────────>│   链路断言点      │
│ webhook/media/  │   作为输入    │ AP1..AP9         │
│ alist/          │              └──────────────────┘
└─────────────────┘
```

---

## 6. 验证规则汇总

| 规则 | 适用实体 | 来源 |
|------|---------|------|
| EventId 唯一索引 | WebhookEvent | 原则 II、`md/danmuji/AGENTS.md` |
| 乐观锁 @Version | 所有业务 Entity | 原则 II |
| fixtures 与契约一致 | WebhookEventFixture、AListResponseFixture | `md/danmuji/`、`md/alist/` |
| 媒体体积 <5MB | MediaFixture | 假设 |
| E2E 数据目录独立 | SystemInstance.dataDir | R7 |
| 端口冲突可检测 | AListInstance.port、DanmujiInstance.webuiPort | R8 |
| traceId 全链路传播 | AP8 | 原则 VII §7.3 |
| error.log 双写 | AP9 | 原则 VII §7.4 |
