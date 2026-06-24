# AGENTS.md — 录播姬对接文档（md/danmuji/）

## 定位

本目录是 **B站录播姬（BililiveRecorder）Webhook 协议的完整参考**，源自 [rec.danmuji.org/reference/webhook/](https://rec.danmuji.org/reference/webhook/) 官方文档。仅含一份 [webhook.md](./webhook.md)，覆盖 Webhook v1 / v2 两套协议。

后端消费模块：**`webhook/`**（`WebhookController` 接收事件，`WebhookService` 异步去重 + 规则匹配 + 触发同步/转码）。

> 父级规范：[../AGENTS.md](../AGENTS.md)。本文件为录播姬对接的地方性法规，覆盖事件类型、字段、幂等性、对接约束。

---

## 工作指令

1. **改 `WebhookController`/`WebhookService` 前必读 [webhook.md](./webhook.md)** — 录播姬不保证事件发送/接收顺序，时序假设（如"FileClosed 一定在 SessionEnded 之前"）会导致 Bug。
2. **统一走 Webhook v2** — 项目仅支持 v2 协议（`EventType` + `EventId` + `EventData` 三段式），v1 在新版本中可能被移除，禁止新增 v1 处理逻辑。
3. **接口必须无认证可达** — `AuthInterceptor` 已放行 `/api/webhooks/**`，新增 webhook 路径时务必同步放行（见 `common/config/WebMvcConfig` 或 `common/interceptor/AuthInterceptor`）。
4. **响应必须 2xx 快速返回** — 重业务逻辑用 `@Async` 异步处理，Controller 内只完成"持久化 `WebhookEvent` + 入队"，避免录播姬重试。
5. **幂等去重必须基于 `EventId`** — `WebhookEvent.eventId` 列上有唯一索引，重复事件直接命中索引落库失败即跳过，禁止改用业务字段（RoomId+时间戳）替代。

---

## Webhook v2 事件矩阵

| EventType | 触发时机 | 后端典型动作 | EventData 关键字段 |
|-----------|---------|-------------|-------------------|
| `SessionStarted` | 开始一次录制 | 记录会话开始 | `SessionId`, `RoomId`, `Title`, `AreaNameParent/Child` |
| `FileOpening` | 录制文件创建 | （通常忽略）| `RelativePath`, `FileOpenTime`, `SessionId` |
| `FileClosed` | 单个 flv/m4s 文件写入完毕 | **触发同步 + 转码**（项目核心入口） | `RelativePath`, `FileSize`, `Duration`, `FileOpenTime`, `FileCloseTime` |
| `SessionEnded` | 一次录制结束 | 标记会话结束 | `SessionId`（不含 `RelativePath`） |
| `StreamStarted` | 直播开始（2.0.0+） | 可选触发 | 无 `SessionId`（未必正在录制） |
| `StreamEnded` | 直播结束（2.0.0+） | 可选触发 | 无 `SessionId` |

### 公共字段（所有事件 `EventData` 都有）

| 字段 | 类型 | 说明 |
|------|------|------|
| `RoomId` | int | 房间号（长号） |
| `ShortId` | int | 短号，无则为 0 |
| `Name` | string | 主播名 |
| `Title` | string | 直播间标题 |
| `AreaNameParent` | string | 父分区 |
| `AreaNameChild` | string | 子分区 |
| `Recording` | bool | 当前是否在录制（2.0.0+） |
| `Streaming` | bool | 当前是否直播中（2.0.0+） |
| `DanmakuConnected` | bool | 弹幕服务器是否已连接（2.0.0+） |

### 顶层字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `EventType` | string | 事件类型枚举 |
| `EventTimestamp` | string | ISO 8601 含时区，**不保证单调递增** |
| `EventId` | string | UUID，**幂等去重唯一依据** |
| `EventData` | object | 上表字段的并集 |

---

## 关键约束（来自官方文档）

### 1. 不保证发送顺序

> `SessionEnded` 的 `EventTimestamp` 可能晚于 `FileClosed` 仅几毫秒，但因异步执行可能先发出。

**实现影响**：禁止用"收到 SessionEnded 表示文件已全部 close"的假设；处理 `SessionEnded` 时不要遍历 `FileClosed` 列表做收尾。

### 2. 不保证接收顺序

> 下一个文件的 `FileOpening` 可能在上一个的 `FileClosed` 之前到达。

**实现影响**：`WebhookEvent` 按时间戳排序展示，不要按到达顺序构建状态机。

### 3. 失败重试最多 3 次

> 非 2xx 响应或网络错误，录播姬重试上限 3 次，重试时 payload 完全一致（含 `EventId`）。

**实现影响**：
- Controller 必须快速返回，2xx 之前不阻塞重业务
- `WebhookEvent.eventId` 列必须唯一索引，重复事件入库失败即丢弃

### 4. Webhook v1 仅文件结束触发

> v1 仅在 `FileClosed` 时触发，字段结构与 v2 完全不同（`EventRandomId` 而非 `EventId`，`StartRecordTime`/`EndRecordTime` 而非 `FileOpenTime`/`FileCloseTime`）。

**实现影响**：项目固定按 v2 协议处理，录播姬侧需在"设置 - Webhook"中配置 v2 URL。

---

## 当前实现引用

```java
// WebhookController.java
// 协议参考: md/danmuji/webhook.md (Webhook v2)
// 放行规则: AuthInterceptor 排除 /api/webhooks/**
@PostMapping("/api/webhooks/recorder")
public ResponseEntity<Void> receive(@RequestBody DanmujiWebhookEvent event);

// WebhookService.java
// 去重: 基于 EventData.EventId 唯一索引
// 异步: @Async 触发规则匹配 → 决策 SYNC_ONLY / TRANSCODE_ONLY / BOTH
```

### 请求样例

录播姬向项目发起的实际请求：

```http
POST /api/webhooks/recorder HTTP/1.1
Host: alist-media-sync.example.com
Content-Type: application/json
User-Agent: BililiveRecorder/2.x

{
  "EventType": "FileClosed",
  "EventTimestamp": "2026-06-25T20:00:00.5071815+08:00",
  "EventId": "98f85267-e08c-4f15-ad9a-1fc463d42b0b",
  "EventData": {
    "RelativePath": "23058-3号直播间/录制-23058-20260625-200000.flv",
    "FileSize": 816412,
    "Duration": 4.992,
    "FileOpenTime": "2026-06-25T19:55:08.5246401+08:00",
    "FileCloseTime": "2026-06-25T20:00:00.4961101+08:00",
    "SessionId": "7c7f3672-70ce-405a-aa12-886702ced6e5",
    "RoomId": 23058,
    "ShortId": 3,
    "Name": "3号直播间",
    "Title": "哔哩哔哩音悦台",
    "AreaNameParent": "生活",
    "AreaNameChild": "影音馆",
    "Recording": true,
    "Streaming": true,
    "DanmakuConnected": true
  }
}
```

项目期望响应：

```http
HTTP/1.1 200 OK
Content-Length: 0
```

---

## 测试与调试

官方推荐两种 webhook 抓包方式（见 webhook.md）：

1. [httpdump.mjs](https://github.com/BililiveRecorder/website/blob/main/scripts/httpdump.mjs) — Node.js 本地抓包脚本
2. [webhook.site](https://webhook.site) — 在线临时端点

本地调试时可在录播姬中临时把 webhook URL 指向以上工具，确认字段结构与官方一致后再切回本项目。
