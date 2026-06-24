# frontend/hooks/ — React Hooks

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和前端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

通用 React Hooks。

## 内容

- `useWebSocket.ts`：WebSocket 连接管理 hook，连接 `/ws/events` 端点，提供消息分发回调、指数退避重连（1s → 30s）和连接状态（CONNECTING / OPEN / CLOSED / AUTH_FAILED）
- `usePagination.ts`：分页状态管理

## 模块关联

- `useWebSocket` 被 `DashboardPage`、`SyncTaskDetailPage`、`TranscodeTaskListPage`、`WebhookEventListPage`、`SyncTaskListPage` 用于接收实时状态/事件推送（替代旧的 HTTP 轮询）
- 依赖 **api/client.ts** 暴露的 `AUTH_CREDENTIALS_KEY` 取出 Basic 凭证
