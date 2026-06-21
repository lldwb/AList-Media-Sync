# frontend/pages/ — 页面组件

## 功能

应用页面，每个页面对应一个路由。

## 页面列表

| 页面 | 路由 | 说明 |
|------|------|------|
| `LoginPage.tsx` | `/login` | 登录页 |
| `DashboardPage.tsx` | `/` | 仪表板首页 |
| `EngineListPage.tsx` | `/engines` | 存储引擎管理 |
| `SyncTaskListPage.tsx` | `/sync-tasks` | 同步任务列表（创建/编辑/触发） |
| `SyncTaskDetailPage.tsx` | `/sync-tasks/:id` | 同步任务详情+进度（使用轮询） |
| `TranscodeTaskListPage.tsx` | `/transcode-tasks` | 转码任务列表（创建/重试/清理） |
| `WebhookRuleListPage.tsx` | `/webhook-rules` | Webhook 规则管理 |
| `WebhookEventListPage.tsx` | `/webhook-events` | Webhook 事件查询 |

## 模块关联

- 使用 **api/client.ts** 进行数据请求
- 使用 **components/forms/** 中的表单组件
- 使用 **components/ui/** 中的 UI 组件
- 使用 **hooks/** 中的轮询和分页 hook
- **TranscodeTaskListPage** 待添加：清理失败/成功任务、重试所有失败文件的批量操作按钮
