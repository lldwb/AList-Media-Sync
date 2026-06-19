# 阶段 0 研究：Web 管理前端界面

**功能**：004-web-management-frontend | **日期**：2026-06-19

## 研究项清单

| # | 研究主题 | 来源 | 影响范围 |
|---|---------|------|---------|
| R1 | 前端技术选型：纯 HTML/JS 还是轻量框架（Vue.js CDN）？ | A2 假设 | 代码组织、开发效率、可维护性 |
| R2 | 前端路由方案：URL Hash 还是 History API？ | A5 假设 | URL 结构、页面刷新行为、部署配置 |
| R3 | cron 表达式预览：前端库还是后端计算？ | FR-016 | 功能实现路径、依赖引入 |
| R4 | 仪表板统计 API：复用现有端点还是新增？ | A7 假设 | 后端改动范围 |
| R5 | 前端分页策略：前端分页还是后端分页改造？ | FR-006、现有 API 分析 | 分页实现方案 |
| R6 | 前端静态资源目录结构设计 | FR-034 | 文件组织 |

---

## R1：前端技术选型

**决策**：采用 Vue.js 3 CDN 引入（无构建工具）+ 原生 CSS

**理由**：

- 本功能包含 7 个页面、多个表单和列表、状态管理复杂，纯 HTML/JS 管理 36 个功能需求会导致代码组织混乱
- Vue.js CDN（`vue.global.prod.js`）约 40KB gzip，无需构建工具，直接通过 `<script>` 标签引入
- 单文件组件定义（SFC）不适合无构建工具场景，但可以通过 Vue 3 的 `options API` 或 `Composition API`（`setup()`）在单个 JS 文件中组织逻辑
- 依据章程 VI（YAGNI）——不引入 npm/Webpack/Vite 构建链，保持轻量

**考虑的替代方案**：

| 方案 | 规模 | 优点 | 缺点 |
|------|------|------|------|
| 纯 HTML + vanilla JS | ~15-20 个 HTML 文件，大量重复代码 | 零依赖 | 代码重复严重，状态管理困难，DOM 操作繁琐 |
| **Vue.js 3 CDN（选中）** | ~5 个 HTML 模板 + 3-4 个 JS 模块 | 组件化、响应式数据绑定、路由 | 依赖 CDN（可选降级为本地文件） |
| React CDN + JSX | 需要 Babel 转译 | 生态丰富 | 仍需构建工具处理 JSX |
| Alpine.js | 极轻量（15KB） | 比 Vue 更轻 | 缺少路由支持，不适合多页面 SPA |
| HTMX | 约 14KB | 超轻量，无需 JS 编写 | 服务器端渲染为主，本项目后端无模板引擎 |

**最终结论**：Vue.js 3 CDN 是最佳平衡——足够轻量（无构建步骤），足够强大（响应式 + 组件 + 路由），适合 7 个页面的管理后台。

---

## R2：前端路由方案

**决策**：URL Hash 路由（使用 Vue Router 的 `createWebHashHistory`）

**理由**：

- Hash 路由（`#/sync-tasks`）无需服务端配置支持 HTML5 History fallback
- Spring Boot 静态资源服务不处理 SPA fallback，Hash 路由天然可用
- 页面刷新后状态通过 URL Hash 自然恢复
- Vue Router CDN 版本原生支持 `createWebHashHistory()`

**考虑的替代方案**：

| 方案 | 优点 | 缺点 |
|------|------|------|
| **Hash 路由（选中）** | 无需服务端配置，刷新自然恢复 | URL 带有 `#` 符号 |
| History API 路由 | 美观的 URL | 需要服务端配置 fallback（所有路径返回 index.html），Spring Boot 静态资源不支持 |
| 无路由（多 HTML 文件） | 最简单 | 页面切换需要全页重载，用户体验差 |

**最终结论**：Hash 路由是静态资源部署的最简单可靠方案。

---

## R3：cron 表达式预览

**决策**：前端纯 JavaScript 解析 cron 表达式，不依赖外部库

**理由**：

- cron 表达式解析逻辑简单明确（5 字段解析 + 扩展到下次执行时间）
- 引入 cron-parser 库（~10KB）虽小但非必要——本项目只需要解析到"下次执行时间"和"人类可读描述"
- 自行实现约 100 行 JS 代码即可覆盖标准 5 字段 cron（分钟、小时、日、月、星期）

**实现要点**：

1. 用户输入时实时校验格式（5 个字段，每字段支持 `*`、数字、范围、步进、列表）
2. 计算"下一次执行时间"：从当前时间开始，找到下一个匹配 cron 的时间点
3. 生成人类可读描述：如"每 6 小时执行一次"

**考虑的替代方案**：

| 方案 | 优点 | 缺点 |
|------|------|------|
| **自实现（选中）** | 零依赖，体积小 | 需要自写解析逻辑（~100 行） |
| cronstrue 库 (~20KB) | 支持 30+ 种语言的人类可读描述 | 增加依赖，只有描述功能无下次执行时间计算 |
| cron-parser 库 (~10KB) | 精确的下次执行时间计算 | 仅支持计算，无人类可读描述 |
| 后端计算 API | 逻辑集中，前端简单 | 需要新增 API 端点，违反 A10（不新增 API） |

**最终结论**：自实现 ~100 行 JS，覆盖格式校验 + 人类可读描述 + 下次执行时间预览。

---

## R4：仪表板统计 API

**决策**：新增一个聚合统计端点 `GET /api/dashboard/stats`

**理由**：

- 仪表板需要的数据（活跃任务数、今日处理文件总数、24h 成功率）分散在多个现有 API 端点中
- 前端逐个调用 + 聚合会导致 4-5 次 HTTP 请求（Dashboard 页面加载慢）
- 单个聚合端点一次性返回所有统计，遵循 RESTful 资源设计
- 此端点是**纯读取聚合查询**，不修改任何现有 API 契约

**API 设计（概要）**：

```json
GET /api/dashboard/stats
响应：
{
  "code": 200,
  "message": "success",
  "data": {
    "activeSyncTasks": 3,
    "pendingTranscodeTasks": 5,
    "todayProcessedFiles": 1280,
    "last24hSuccessRate": 96.5,
    "totalEngines": 8,
    "totalWebhookRules": 4
  }
}
```

**后端实现范围**：在 Controller 层新增一个 `DashboardController`（`@RestController`），Service 层新增 `DashboardService`，通过 Repository 的聚合查询方法获取统计数据。

**考虑的替代方案**：

| 方案 | 优点 | 缺点 |
|------|------|------|
| **新增聚合端点（选中）** | 单请求获取全部数据，前端简单 | 需要新增后端代码 |
| 前端调用多个现有 API 聚合 | 无需后端改动 | 4-5 次请求，页面加载慢，逻辑分散 |
| 复用现有列表端点 + 前端过滤 | 零后端改动 | 列表端点返回全量数据，前端计算量大 |

---

## R5：前端分页策略

**决策**：前端获取全量数据后在前端分页，不改造后端 API

**理由**：

- 分析现有 Controller 代码：所有列表端点返回 `List<T>`（全量），不支持 `Pageable` 参数
- 本系统是管理工具，单个用户使用，数据量有限（引擎通常 < 20、任务 < 100、事件记录 < 1000）
- 全量数据总大小通常在 100KB 以内，前端一次性获取 + 缓存 + 分页展示的体验优于多次 Ajax 请求
- 遵循章程 YAGNI 和 A10（不修改现有 API 契约）
- 对于真正的"大量数据"场景（如 TaskExecution 超过 1000 条），前端数据量仍可控（每条约 200 字节，1000 条 ≈ 200KB）

**考虑的替代方案**：

| 方案 | 优点 | 缺点 |
|------|------|------|
| **前端分页（选中）** | 零后端改动，快速翻页 | 首次加载需传输全量数据 |
| 后端分页（Spring Page） | 大数据集友好 | 需要改造所有 Controller 和 Repository |
| 混合：仅执行历史使用后端分页 | 针对数据量最大的场景优化 | 不一致的分页策略，复杂度高 |

**最终结论**：前端分页符合本项目的规模和数据量特征。如果未来数据量增长需要后端分页，可以渐进式改造。

---

## R6：静态资源目录结构

**决策**：采用 `/static/app/` 子目录隔离前端文件

**理由**：

- 将前端文件放在 `/static/app/` 下，与 Spring Boot 默认资源分离
- 根路径 `/` 重定向到 `/app/index.html`
- 结构清晰：JS/CSS/页面模板各自独立文件

**目录结构**：

```text
src/main/resources/static/app/
├── index.html              # 主 SPA 入口
├── css/
│   └── app.css             # 全局样式（~500 行）
├── js/
│   ├── app.js              # Vue 应用初始化 + 路由定义 + 全局组件
│   ├── api.js              # HTTP 请求封装（fetch + Basic Auth）
│   ├── auth.js             # 认证与会话管理模块
│   ├── utils.js            # 工具函数（cron 解析、格式化等）
│   └── pages/              # 每个页面的 Vue 组件定义（options API）
│       ├── dashboard.js
│       ├── engines.js
│       ├── sync-tasks.js
│       ├── transcode-tasks.js
│       ├── webhook-rules.js
│       ├── webhook-events.js
│       └── login.js
└── vendor/                 # 第三方库（本地备份，降级使用）
    ├── vue.global.prod.js  # Vue 3 生产版（~130KB）
    └── vue-router.global.prod.js  # Vue Router 4（~30KB）
```

**最终结论**：此结构清晰分离关注点，JS 文件按页面划分，便于维护。CDN 引入为主，`vendor/` 目录作为离线降级。
