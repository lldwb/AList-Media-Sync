# frontend/hooks/ — React Hooks

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和前端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

通用 React Hooks。

## 内容

- `usePolling.ts`：轮询 hook，定时调用 fetcher，满足 stopCondition 时停止
- `usePagination.ts`：分页状态管理

## 模块关联

- `usePolling` 被 `TranscodeTaskListPage` 和 `SyncTaskDetailPage` 用于实时状态更新
- **待优化**：轮询方式应替换为 WebSocket/SSE 推送，减少不必要的 HTTP 请求
