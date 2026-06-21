# frontend/hooks/ — React Hooks

## 功能

通用 React Hooks。

## 内容

- `usePolling.ts`：轮询 hook，定时调用 fetcher，满足 stopCondition 时停止
- `usePagination.ts`：分页状态管理

## 模块关联

- `usePolling` 被 `TranscodeTaskListPage` 和 `SyncTaskDetailPage` 用于实时状态更新
- **待优化**：轮询方式应替换为 WebSocket/SSE 推送，减少不必要的 HTTP 请求
