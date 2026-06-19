# 阶段 0 研究：Web 管理前端界面

**功能**：004-web-management-frontend | **日期**：2026-06-19 | **修订**：2026-06-19（技术栈迁移至 React + TypeScript + Vite + Tailwind CSS）

## 迁移说明

**原始决策**：Vue.js 3 CDN（零构建步骤）+ 原生 CSS（~500 行手写）
**新决策**：React 19 + TypeScript + Vite + Tailwind CSS

**迁移理由**：

- TypeScript 提供静态类型检查，显著降低运行时错误率，适合管理后台的表单密集和数据列表场景（36 个 FR 中大量涉及表单校验、数据映射）
- Vite 构建工具提供极快的 HMR（热模块替换），开发体验优于零构建步骤方案，且生产构建产物经过 tree-shaking、代码分割后体积可控
- Tailwind CSS 替代手写 CSS——实用工具优先的原子化 CSS 消除样式命名冲突，删除死代码时安全，且配合 Vite 的 JIT 模式仅生成使用到的样式（生产 CSS < 10KB gzip）
- React 19 的 `use()` hook、Server Components 等新特性可用于未来迭代；本次 v1 采用客户端渲染（CSR）+ React Router Hash 路由的传统 SPA 模式

---

## 研究项清单

| # | 研究主题 | 来源 | 影响范围 |
|---|---------|------|---------|
| R1 | 前端技术选型：React 19 + TypeScript + Vite + Tailwind CSS | A2 假设 | 代码组织、开发效率、可维护性 |
| R2 | 前端路由方案：React Router Hash 路由 | A5 假设 | URL 结构、页面刷新行为、部署配置 |
| R3 | cron 表达式预览：前端 TypeScript 自实现 | FR-016 | 功能实现路径、依赖引入 |
| R4 | 仪表板统计 API：复用现有端点还是新增？ | A7 假设 | 后端改动范围 |
| R5 | 前端分页策略：前端分页还是后端分页改造？ | FR-006、现有 API 分析 | 分页实现方案 |
| R6 | 前端项目结构与构建流程设计 | FR-034 | 文件组织、构建集成 |

---

## R1：前端技术选型

**决策**：React 19 + TypeScript（ES2020+）+ Vite + Tailwind CSS

**理由**：

- React 19 是当前最成熟的前端组件化框架，生态丰富；TypeScript 为数据密集型管理后台提供编译时安全保障
- Vite 提供极快的开发服务器启动（< 500ms）和 HMR，构建产物经 Rollup 打包 + tree-shaking 后体积可控
- Tailwind CSS 的原子化样式方法完全消除手写 CSS 的维护负担：无命名冲突、无死代码、生产构建仅包含使用到的类
- 最终构建产物（含代码分割）控制在 ~150KB gzip 以内，满足 SC-002 的 3 秒首次加载目标
- 章程 VI（YAGNI）考虑：相比 Vue CDN 方案，React + Vite 增加了构建步骤，但带来了类型安全、更好的组件组织和可维护性。对于 36 个 FR、7 个页面的管理后台，这是合理的复杂度投资

**考虑的替代方案**：

| 方案 | 规模 | 优点 | 缺点 |
|------|------|------|------|
| Vue.js 3 CDN（原方案） | ~5 个 HTML + ~10 个 JS 模块 | 零构建，部署简单 | 无类型检查、手写 CSS 维护成本高、代码组织松散 |
| **React 19 + Vite + TypeScript + Tailwind（选中）** | ~15 个 `.tsx` 组件 + ~5 个工具模块 | 类型安全、组件化、HMR、原子化 CSS | 需要 Node.js + 构建步骤 |
| Next.js (App Router) | 全栈框架 | SSR/SSG 能力 | 过度复杂——本项目仅需 CSR SPA，无 SEO 需求 |

**最终结论**：React 19 + TypeScript + Vite + Tailwind CSS 在类型安全、开发体验和最终产物体积之间取得了最佳平衡。

---

## R2：前端路由方案

**决策**：React Router v7 Hash 路由（`createHashRouter`）

**理由**：

- Hash 路由（`#/sync-tasks`）无需服务端配置支持 HTML5 History fallback
- Spring Boot 静态资源服务不处理 SPA fallback，Hash 路由天然可用
- React Router v7 原生支持 `createHashRouter()`，API 与 BrowserRouter 一致
- 页面刷新后状态通过 URL Hash 自然恢复

**考虑的替代方案**：

| 方案 | 优点 | 缺点 |
|------|------|------|
| **React Router Hash 路由（选中）** | 无需服务端配置，刷新自然恢复 | URL 带有 `#` 符号 |
| React Router BrowserRouter | 美观的 URL | 需要服务端配置 fallback，Spring Boot 静态资源不支持 |
| TanStack Router | 类型安全路由 | 额外依赖，生态较小 |

**最终结论**：React Router v7 Hash 路由是最可靠的选择。

---

## R3：cron 表达式预览

**决策**：前端 TypeScript 自实现 cron 解析器，不依赖外部库

**理由**：

- cron 表达式解析逻辑简单明确（5 字段解析 + 扩展到下次执行时间）
- TypeScript 类型系统可以在编译时捕获字段解析错误
- 自行实现约 120 行 TS 代码即可覆盖标准 5 字段 cron
- 保留零额外依赖原则

**实现要点**：

1. 用户输入时实时校验格式（5 个字段，每字段支持 `*`、数字、范围、步进、列表）
2. 计算"下一次执行时间"：从当前时间开始逐步递增，匹配所有字段
3. 生成人类可读描述：如"每 6 小时执行一次"

**考虑的替代方案**：同原始研究——自实现仍是最佳。

---

## R4：仪表板统计 API

**决策**：新增一个聚合统计端点 `GET /api/dashboard/stats`（不变）

**API 设计**保持不变，包括 `activeSyncTasks`、`pendingTranscodeTasks`、`todayProcessedFiles`、`last24hSuccessRate`、`totalEngines`、`totalWebhookRules` 字段。

---

## R5：前端分页策略

**决策**：前端获取全量数据后在前端分页，不改造后端 API（不变）

理由同原始研究——本系统数据量有限，前端分页符合规模特征。

---

## R6：前端项目结构与构建流程

**决策**：独立前端项目目录 `src/main/frontend/`，Vite 构建输出到 `src/main/resources/static/app/`

**理由**：

- 前端代码与后端 `src/main/java/` 物理分离，关注点清晰
- Vite 开发服务器通过 `vite.config.ts` 中的 proxy 配置代理 API 请求到后端 `localhost:8080`
- 构建产物（`vite build`）输出到 Spring Boot 静态资源目录，Docker 构建时无需额外步骤

**目录结构**：

```text
src/main/frontend/                    # 前端项目根目录
├── index.html                        # Vite 入口 HTML
├── package.json                      # 依赖与脚本
├── tsconfig.json                     # TypeScript 配置
├── vite.config.ts                    # Vite 构建配置（含代理、输出路径）
├── tailwind.config.ts                # Tailwind 配置（含项目色板、字体）
├── postcss.config.js                 # PostCSS 配置（Tailwind 插件）
├── public/                           # 直接复制的静态资源
└── src/
    ├── main.tsx                      # React 应用入口（createRoot）
    ├── App.tsx                       # 根组件：RouterProvider + 认证守卫
    ├── index.css                     # Tailwind 指令 + 少量全局样式
    ├── api/
    │   └── client.ts                 # fetch 封装（Basic Auth 注入、401 拦截、ApiResult 解析）
    ├── auth/
    │   ├── AuthContext.tsx            # 认证上下文（Provider + useAuth hook）
    │   └── useSessionTimeout.ts      # 会话超时 hook（交互监听 + 30 分钟倒计时）
    ├── router/
    │   └── index.tsx                 # React Router Hash 路由配置（7 条路由）
    ├── hooks/
    │   ├── usePolling.ts             # 通用轮询 hook（5 秒间隔）
    │   └── usePagination.ts          # 前端分页 hook（每页 20 条）
    ├── utils/
    │   ├── cron.ts                   # cron 表达式解析器（~120 行）
    │   ├── format.ts                 # 日期/文件大小格式化
    │   └── validate.ts               # 表单校验工具（URL、必填、cron 格式）
    ├── types/
    │   └── api.ts                    # 后端 API 响应类型定义（StorageEngineVO、SyncTaskVO 等）
    ├── components/
    │   ├── layout/
    │   │   ├── AppLayout.tsx          # 主布局（侧边栏 + 内容区）
    │   │   └── Sidebar.tsx            # 侧边栏导航组件
    │   ├── ui/
    │   │   ├── DataTable.tsx          # 通用数据表格（分页、排序、空状态）
    │   │   ├── ConfirmDialog.tsx      # 删除确认对话框
    │   │   ├── LoadingSpinner.tsx     # 加载动画
    │   │   ├── ErrorBanner.tsx        # 错误/网络中断提示横幅
    │   │   ├── EmptyState.tsx         # 空数据状态组件
    │   │   └── StatusBadge.tsx        # 状态标签（运行中/成功/失败等）
    │   └── forms/
    │       ├── EngineForm.tsx         # 存储引擎创建/编辑表单
    │       ├── SyncTaskForm.tsx       # 同步任务创建/编辑表单（含 cron 预览）
    │       ├── TranscodeTaskForm.tsx  # 转码任务创建表单
    │       └── WebhookRuleForm.tsx    # Webhook 规则创建/编辑表单
    └── pages/
        ├── LoginPage.tsx             # 登录页（独立路由，不使用 AppLayout）
        ├── DashboardPage.tsx         # 仪表板（统计卡片）
        ├── EngineListPage.tsx        # 存储引擎列表
        ├── SyncTaskListPage.tsx      # 同步任务列表
        ├── SyncTaskDetailPage.tsx    # 同步任务详情（基本信息 + 执行历史 Tab）
        ├── TranscodeTaskListPage.tsx # 转码任务列表
        ├── WebhookRuleListPage.tsx   # Webhook 规则列表
        └── WebhookEventListPage.tsx  # Webhook 事件列表
```

**构建流程**：

```bash
# 开发模式（前端 HMR + API 代理到后端）
cd src/main/frontend
npm run dev       # Vite 开发服务器 :5173 → 代理 /api → localhost:8080

# 生产构建
npm run build     # 输出到 src/main/resources/static/app/

# 后端构建包含前端产物
mvn package       # Spring Boot 打包时自动包含 static/app/ 下的文件
```

**Docker 集成**：在 `Dockerfile` 的构建阶段中添加前端构建步骤：

```dockerfile
# 前端构建阶段
FROM node:22-alpine AS frontend-build
WORKDIR /app/frontend
COPY src/main/frontend/package*.json ./
RUN npm ci
COPY src/main/frontend/ .
RUN npm run build

# 后端构建阶段
FROM maven:3.9-eclipse-temurin-21 AS backend-build
...
COPY --from=frontend-build /app/frontend/dist /app/src/main/resources/static/app
...
```

**构建产物大小预估**：

| 分块 | 大小（gzip） | 说明 |
|------|-------------|------|
| React 19 + ReactDOM | ~45KB | 核心运行时 |
| React Router v7 | ~12KB | Hash 路由 |
| Tailwind CSS（JIT 生产） | ~6KB | 仅使用到的原子类 |
| 应用代码（~15 组件） | ~30KB | TypeScript 编译后的 JS |
| **总计** | **~93KB** | 远低于原 Vue CDN 方案的 ~160KB（Vue + Router 未压缩） |

**最终结论**：独立前端项目 + Vite 构建 → 输出到 Spring Boot 静态资源目录的模式清晰分离前后端关注点，开发体验优秀，构建产物体积可控。
