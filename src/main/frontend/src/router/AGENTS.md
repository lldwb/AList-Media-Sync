# frontend/router/ — 路由配置

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
