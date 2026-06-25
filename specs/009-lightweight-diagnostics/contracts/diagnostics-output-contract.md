# 诊断包输出契约

## 目录结构

```text
diagnostics/latest/
├── summary.md
├── logs/
│   ├── error.log
│   └── app.log
├── config/
│   └── config.redacted.json
├── environment.txt
└── last-run.json
```

## summary.md 必填内容

```markdown
# 诊断摘要

## 基本信息
- 生成时间：<时间>
- 部署形态：<本地开发|Docker|一体化启动包>
- 应用版本：<版本或不可获取>
- Trace ID：<诊断自身 traceId>

## 最近一次失败
- Trace ID：<最近失败 traceId 或未发现>
- 模块：<模块或未知>
- 操作：<操作或未知>
- 错误类型：<错误类型或未知>
- 错误信息：<脱敏后的错误信息>

## 关键证据
- 错误日志：logs/error.log
- 应用日志：logs/app.log
- 配置摘要：config/config.redacted.json
- 环境摘要：environment.txt

## 缺失信息
- <没有缺失时写“无”>

## 建议下一步
- <基于证据的排查建议>
```

## config.redacted.json 规则

- 字段名必须保留。
- 敏感值必须替换为 `***REDACTED***`。
- 空值必须显示为空或标记为 `EMPTY`，不能显示为 `***REDACTED***`。
- 缺失关键配置必须在 `missingKeys` 中列出。

## last-run.json 建议字段

```json
{
  "traceId": "sync-20260626-001",
  "taskType": "SYNC",
  "taskName": "示例任务",
  "startedAt": "2026-06-26T10:00:00+08:00",
  "endedAt": "2026-06-26T10:00:12+08:00",
  "status": "FAILED",
  "errorType": "NetworkTimeout",
  "message": "脱敏后的错误信息"
}
```

## 安全要求

诊断包中不得出现以下原始值：

- 密码
- Token
- Cookie
- Authorization header
- 加密密钥
- 数据库连接凭据
- URL 中的敏感查询参数

## 验收检查

- `summary.md` 必定存在。
- `summary.md` 中能定位最近失败 traceId 或明确说明未发现失败。
- `config.redacted.json` 不包含原始敏感值。
- 部分信息不可用时，`summary.md` 明确列出缺失信息。
