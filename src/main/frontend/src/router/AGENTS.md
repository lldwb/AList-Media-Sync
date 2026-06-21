# frontend/router/ — 路由配置

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和前端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

React Router Hash 路由表，含认证守卫。

## 作用

- `index.tsx`：定义所有路由（hash 模式），`ProtectedRoute` 组件检查认证状态，未登录重定向到 `/login`

## 路由表

| 路由 | 组件 | 认证 |
|------|------|------|
| `/login` | LoginPage | 否 |
| `/` | DashboardPage | 是 |
| `/engines` | EngineListPage | 是 |
| `/sync-tasks` | SyncTaskListPage | 是 |
| `/sync-tasks/:id` | SyncTaskDetailPage | 是 |
| `/transcode-tasks` | TranscodeTaskListPage | 是 |
| `/webhook-rules` | WebhookRuleListPage | 是 |
| `/webhook-events` | WebhookEventListPage | 是 |
