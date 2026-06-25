# HTTP Trace 契约

## 目标

所有后端 HTTP 请求都必须产生可追踪的 traceId，并通过响应头返回给调用方，方便用户把问题反馈给 AI 或开发者。

## 请求头

| Header | 必填 | 说明 |
|--------|------|------|
| `X-Trace-Id` | 否 | 调用方提供的追踪标识；合法时后端沿用，不合法或缺失时后端生成新值 |

## 响应头

| Header | 必填 | 说明 |
|--------|------|------|
| `X-Trace-Id` | 是 | 本次请求实际使用的 traceId；成功、业务失败、认证失败和异常响应都必须返回 |

## 处理规则

1. 请求未携带 `X-Trace-Id` 时，后端生成新的 traceId。
2. 请求携带合法 `X-Trace-Id` 时，后端沿用该值。
3. 请求携带非法 `X-Trace-Id` 时，后端忽略原值并生成新 traceId。
4. traceId 必须写入日志上下文，贯穿本次请求的 Controller、Service、Repository 和异常处理日志。
5. 请求处理结束后必须清理线程上下文，避免污染后续请求。
6. 响应体结构不因 traceId 改变，仍保持现有 `ApiResult<T>` 契约。

## 合法 traceId 建议

- 长度：8–128 个字符。
- 字符：英文字母、数字、短横线、下划线、点号。
- 不允许包含空白、换行、控制字符或明显敏感内容。

## 示例

### 请求

```http
GET /api/dashboard/stats HTTP/1.1
Host: localhost:8080
Authorization: Basic ***REDACTED***
X-Trace-Id: user-report-20260626-001
```

### 响应

```http
HTTP/1.1 200 OK
X-Trace-Id: user-report-20260626-001
Content-Type: application/json
```

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 验收检查

- 任意 `/api/**` 响应包含 `X-Trace-Id`。
- 异常响应包含 `X-Trace-Id`。
- 前端请求失败时可读取并展示或记录 `X-Trace-Id`。
- 日志中可通过同一 traceId 找到该请求关键流程。
