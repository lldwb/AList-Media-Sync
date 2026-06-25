# 数据模型：轻量诊断系统

## TraceContext（追踪上下文）

**用途**：表示当前 HTTP 请求或后台任务执行的可观测性关联信息。

**字段**：

| 字段 | 类型 | 必填 | 规则 |
|------|------|------|------|
| traceId | 字符串 | 是 | 全局唯一；建议由时间前缀 + 随机后缀组成；仅允许安全字符 |
| source | 枚举 | 是 | `HTTP_REQUEST`、`SYNC_TASK`、`TRANSCODE_TASK`、`WEBHOOK_EVENT`、`DIAGNOSTIC_COMMAND` |
| operation | 字符串 | 否 | 当前操作名称，如“同步任务执行”“生成诊断包” |
| startedAt | 时间 | 是 | 创建时间 |

**关系**：一个 TraceContext 可关联多个日志事件和一个或多个任务阶段。

**生命周期**：创建 → 写入上下文 → 贯穿日志/响应/诊断 → 执行结束后从线程上下文清理。

## LogEvent（日志事件）

**用途**：描述系统运行过程中的结构化日志记录。

**字段**：

| 字段 | 类型 | 必填 | 规则 |
|------|------|------|------|
| time | 时间 | 是 | 日志发生时间 |
| level | 枚举 | 是 | `DEBUG`、`INFO`、`WARN`、`ERROR` |
| module | 字符串 | 是 | 模块名称，如 common、sync、transcode、webhook、storage |
| operation | 字符串 | 是 | 当前业务或系统操作 |
| traceId | 字符串 | 是 | 来自 TraceContext |
| message | 字符串 | 是 | 中文日志消息 |
| errorType | 字符串 | 错误时必填 | 异常类别或失败分类 |
| context | 键值集合 | 否 | 非敏感上下文信息 |

**验证规则**：
- ERROR 日志必须包含 traceId、errorType、message 和足够定位失败的上下文。
- context 中不得包含未脱敏的敏感值。

## DiagnosticPackage（诊断包）

**用途**：一次诊断生成操作的输出集合。

**字段**：

| 字段 | 类型 | 必填 | 规则 |
|------|------|------|------|
| packageId | 字符串 | 是 | 本次诊断生成标识 |
| generatedAt | 时间 | 是 | 生成时间 |
| targetPath | 路径 | 是 | `diagnostics/latest` 或临时目录 |
| summaryPath | 路径 | 是 | `diagnostics/latest/summary.md` |
| traceId | 字符串 | 是 | 诊断生成操作自身 traceId |
| relatedTraceId | 字符串 | 否 | 最近失败任务的 traceId |
| status | 枚举 | 是 | `COMPLETED`、`PARTIAL`、`FAILED` |
| missingItems | 列表 | 否 | 不可读取或缺失的信息 |

**关系**：一个 DiagnosticPackage 包含一个 DiagnosticSummary、多个 EvidenceFile 和一个 RedactedConfigSnapshot。

**状态转换**：
`CREATING` → `COMPLETED`；`CREATING` → `PARTIAL`；`CREATING` → `FAILED`。

## DiagnosticSummary（诊断摘要）

**用途**：给用户、AI 或开发者优先阅读的 Markdown 摘要。

**字段**：

| 字段 | 类型 | 必填 | 规则 |
|------|------|------|------|
| appVersion | 字符串 | 否 | 应用版本，缺失时说明不可获取 |
| commit | 字符串 | 否 | 构建或 Git 信息，缺失时说明不可获取 |
| environment | 字符串 | 是 | 本地开发、Docker 或一体化启动包 |
| latestFailure | 对象 | 否 | 最近失败概览 |
| recommendedFiles | 列表 | 是 | 建议优先阅读的证据文件 |
| suspectedCause | 字符串 | 否 | 基于日志证据的疑似原因 |
| missingItems | 列表 | 否 | 诊断生成时不可读取的信息 |

## RedactedConfigSnapshot（脱敏配置摘要）

**用途**：安全展示配置状态，避免泄露凭据。

**字段**：

| 字段 | 类型 | 必填 | 规则 |
|------|------|------|------|
| source | 字符串 | 是 | 配置来源，如 application.yaml、环境变量 |
| entries | 键值集合 | 是 | 字段名保留，敏感值替换为脱敏占位 |
| redactedKeys | 列表 | 是 | 被脱敏的字段名列表 |
| emptyKeys | 列表 | 否 | 存在但为空的字段名列表 |
| missingKeys | 列表 | 否 | 关键但未配置的字段名列表 |

**验证规则**：
- 原始密码、Token、Cookie、Authorization、密钥不得出现在输出中。
- 空值必须标记为空，不得伪装为已脱敏。

## EvidenceFile（证据文件）

**用途**：诊断包内可供排查的具体文件或摘录。

**字段**：

| 字段 | 类型 | 必填 | 规则 |
|------|------|------|------|
| name | 字符串 | 是 | 文件名 |
| type | 枚举 | 是 | `SUMMARY`、`ERROR_LOG`、`APP_LOG`、`CONFIG`、`ENVIRONMENT`、`LAST_RUN` |
| path | 路径 | 是 | 诊断包内相对路径 |
| truncated | 布尔 | 是 | 是否为截取内容 |
| relatedTraceId | 字符串 | 否 | 关联 traceId |
