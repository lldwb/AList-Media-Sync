# 前端 AGENTS.md

> **文件权重**：三级文件（行政法规级），低于 `AGENTS.md`（根级·法律）和 `constitution.md`（宪法），高于各模块 AGENTS.md（地方性法规）。适用于涉及前端整体的修改，或下级模块 AGENTS.md 无法解释时。

## 功能

前端基于 React 19 + TypeScript + Vite，提供存储引擎管理、同步任务、转码监控、Webhook 规则、仪表板等管理界面。

## 技术栈

| 层 | 技术 |
|---|------|
| 框架 | React 19 + ReactDOM 19 |
| 语言 | TypeScript 5.x（strict 模式） |
| 构建工具 | Vite 6.x |
| 样式方案 | Tailwind CSS 4.x |
| 路由 | React Router v7（Hash 模式） |
| HTTP 客户端 | fetch + Basic Auth（`api/client.ts`） |

## 模块结构

```
src/main/frontend/src/
├── api/           # HTTP 请求封装（fetch + Basic Auth + 401 拦截）
├── types/         # TypeScript 类型定义（与后端 DTO/VO 对应）
├── auth/          # 认证状态管理（Context + 会话超时）
├── components/    # 可复用 UI 组件（布局/表单/基础）
├── hooks/         # React Hooks（WebSocket / 分页）
├── pages/         # 页面组件（8 个路由页面）
├── router/        # Hash 路由表 + 认证守卫
└── utils/         # 工具函数（格式化/校验/Cron）
```

## 模块 AGENTS.md 索引

| 模块 | AGENTS.md 路径 | 一句话说明 |
|------|---------------|-----------|
| api | `src/api/AGENTS.md` | HTTP 请求封装（fetch + Basic Auth） |
| types | `src/types/AGENTS.md` | TypeScript 类型定义 |
| auth | `src/auth/AGENTS.md` | 认证状态管理（Context + 超时） |
| components | `src/components/AGENTS.md` | 可复用 UI 组件（布局/表单/基础） |
| hooks | `src/hooks/AGENTS.md` | React Hooks（WebSocket / 分页） |
| pages | `src/pages/AGENTS.md` | 页面组件（8 个路由页面） |
| router | `src/router/AGENTS.md` | Hash 路由表 + 认证守卫 |
| utils | `src/utils/AGENTS.md` | 工具函数（格式化/校验/Cron） |

## 路由表

| 路由 | 页面 | 认证 |
|------|------|------|
| `/login` | LoginPage | 否 |
| `/` | DashboardPage | 是 |
| `/engines` | EngineListPage | 是 |
| `/sync-tasks` | SyncTaskListPage | 是 |
| `/sync-tasks/:id` | SyncTaskDetailPage | 是 |
| `/transcode-tasks` | TranscodeTaskListPage | 是 |
| `/webhook-rules` | WebhookRuleListPage | 是 |
| `/webhook-events` | WebhookEventListPage | 是 |

## 实时通信

- 通过 `hooks/useWebSocket` 连接后端 `/ws/events` 端点，推送同步进度、转码状态、Webhook 事件、仪表板统计
- 重连策略：指数退避（初始 1s，封顶 30s），认证失败置为 `AUTH_FAILED` 不再重连
