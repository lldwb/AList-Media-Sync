# frontend/api/ — HTTP 请求层

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和前端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

封装前端 HTTP 请求，基于 `fetch` API + Basic Auth + 401 自动拦截跳转。

## 作用

- `client.ts`：提供 `api.get/post/put/del<T>()` 泛型方法，统一处理认证头、超时（15s）、401 拦截、错误解析

## 模块关联

- 被所有 **pages/** 和 **components/forms/** 使用
- 依赖 **types/api.ts** 中的 `ApiResult<T>` 类型
