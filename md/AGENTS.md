# AGENTS.md — 对接系统文档（md/）

## 定位

本目录是 **AList-Media-Sync 对接外部系统的 API 参考资料库**，集中存放所有第三方系统的接口规范、Webhook 协议、Schema 定义。开发对接代码时（如 `storage/service/engine/AListStorageStrategy`、`webhook/service/WebhookService`），先在本目录查阅原始接口契约，再开始编码。

> **文件权重体系**：constitution.md（宪法）> 根 AGENTS.md（法律·全局）> 前端/后端/md AGENTS.md（行政法规）> 模块 AGENTS.md（地方性法规）。本文件为对接系统层的行政法规，下辖各对接系统的地方性法规。

---

## 工作指令

1. **先查文档再写代码** — 修改对接相关代码（`storage/`、`webhook/`）前，MUST 先读本目录下对应系统的 AGENTS.md 和接口 md 文件，确认请求路径、参数、响应结构，禁止凭记忆编码。
2. **以本目录为权威** — 当代码实现与 md 文档存在差异时，以本目录文档为外部系统契约的事实来源；如发现 md 与外部真实接口不一致，更新 md 而非屈就错误实现。
3. **禁止直接修改 md 内容** — md 文件由外部 OpenAPI 规范同步生成（见 `alist/_download.sh` 与 `alist/_llms_index.txt`），手动编辑会被下次同步覆盖。如需更新，先修改源 OpenAPI 或重新执行下载脚本。
4. **新增对接系统的规范** — 每接入一个新外部系统，MUST 在 `md/` 下新建子目录，附 `AGENTS.md`（说明该系统职责、认证方式、关键接口、对应后端模块），并在本文件"对接系统索引"章节登记。
5. **使用中文注释** — 在引用本目录文档的代码注释中，MUST 标注源文档相对路径（如 `// 见 md/alist/fs/列出文件目录.md`），便于回溯。

---

## 对接系统索引

| 子目录 | 外部系统 | 对接形式 | 后端消费模块 | AGENTS.md |
|--------|---------|---------|-------------|----------|
| `alist/` | [AList](https://alist.nn.ci/) 网盘 | 主动调用 REST API | `storage/service/engine/AListStorageStrategy` | [alist/AGENTS.md](./alist/AGENTS.md) |
| `danmuji/` | [B站录播姬](https://rec.danmuji.org/) | 被动接收 Webhook v2 | `webhook/service/WebhookService` | [danmuji/AGENTS.md](./danmuji/AGENTS.md) |

---

## 对接模式速查

### 主动调用型（AList）

- 后端持有 `engine.encryptedToken`（`CryptoConverter` 自动 AES-256-GCM 解密）
- 通过 `RestClient`（`common/config/RestClientConfig`）发起 HTTPS 请求
- 统一响应封装：`{ code, message, data }`，`code != 200` 视为业务错误
- 失败由 `RetryService` 配合 `RetryableException` 做指数退避重试

### 被动接收型（录播姬）

- 暴露 `/api/webhooks/**` 路径，`AuthInterceptor` 已放行
- 必须在 2 次重试窗口内返回 2xx，否则录播姬会发起最多 3 次重试
- 使用 `EventId` 字段做幂等去重（持久化到 `WebhookEvent` 表）
- 异步处理：Controller 立即返回，Service 用 `@Async` 跑规则匹配 + 动作触发

---

## 文档来源与同步

| 系统 | 文档源 | 同步方式 |
|------|--------|---------|
| AList | Apifox 项目 6849786（[在线索引](https://alist-public.apifox.cn/)） | `alist/_download.sh` 解析 `_llms_index.txt` 批量 curl 下载 |
| 录播姬 | [rec.danmuji.org/reference/webhook/](https://rec.danmuji.org/reference/webhook/) | 手动复制官方 Reference 页面 |

如需更新 AList 文档：
```bash
cd md/alist
bash _download.sh
```

---

## 引用规则示例

在后端代码中引用对接文档：

```java
// AListStorageStrategy.java
// 对应接口契约：md/alist/fs/列出文件目录.md
public List<FileEntry> listFiles(StorageEngine engine, String path) { ... }
```

```java
// WebhookService.java
// 事件结构定义：md/danmuji/webhook.md (Webhook v2 - FileClosed)
public void handleFileClosed(WebhookEvent event) { ... }
```
