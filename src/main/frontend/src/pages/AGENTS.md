# frontend/pages/ — 页面组件

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和前端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

应用页面，每个页面对应一个路由。

## 页面列表

| 页面 | 路由 | 说明 |
|------|------|------|
| `LoginPage.tsx` | `/login` | 登录页 |
| `DashboardPage.tsx` | `/` | 仪表板首页（WebSocket 实时刷新统计） |
| `EngineListPage.tsx` | `/engines` | 存储引擎管理 |
| `SyncTaskListPage.tsx` | `/sync-tasks` | 同步任务列表（创建/编辑/触发） |
| `SyncTaskDetailPage.tsx` | `/sync-tasks/:id` | 同步任务详情+进度（WebSocket 推送） |
| `TranscodeTaskListPage.tsx` | `/transcode-tasks` | 转码任务列表（创建/重试/批量清理/重试所有失败） |
| `WebhookRuleListPage.tsx` | `/webhook-rules` | Webhook 规则管理 |
| `WebhookEventListPage.tsx` | `/webhook-events` | Webhook 事件查询（WebSocket 实时刷新） |

## 模块关联

- 使用 **api/client.ts** 进行数据请求
- 使用 **components/forms/** 中的表单组件
- 使用 **components/ui/** 中的 UI 组件
- 使用 **hooks/useWebSocket** 订阅实时事件，**hooks/usePagination** 进行分页
