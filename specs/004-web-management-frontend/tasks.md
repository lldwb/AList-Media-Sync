# 任务：Web 管理前端界面

**输入**：来自 `specs/004-web-management-frontend/` 的设计文档

**前提条件**：plan.md（必需）、spec.md（用户故事必需）、research.md、data-model.md、contracts/

**技术栈**：React 19 + TypeScript + Vite + Tailwind CSS

**测试**：前端 `tsc --noEmit` 类型检查 + 手工验收测试（通过 quickstart.md 场景验证）；后端新增 `DashboardController` 的 `@WebMvcTest`

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（例如，US1、US2、US3）
- 在描述中包含确切的文件路径

---

## 阶段 1：设置（前端项目初始化 + 后端基础）

**目的**：初始化前端 Vite + React + TypeScript + Tailwind CSS 项目，配置构建管道，下载依赖库，配置后端路由映射

- [X] T001 在 `src/main/frontend/` 下通过 `npm create vite@latest . -- --template react-ts` 初始化 Vite + React + TypeScript 项目，配置 `package.json` 添加 React 19、React Router v7、Tailwind CSS 依赖
- [X] T002 在 `src/main/frontend/vite.config.ts` 中配置 Vite（开发端口 5173、`/api` 代理到 `localhost:8080`、构建输出目录 `../resources/static/app`）
- [X] T003 [P] 在 `src/main/frontend/tailwind.config.ts` 中配置 Tailwind CSS（项目色板 `primary`/`success`/`danger`/`warning`、content 路径）（注：Tailwind CSS v4 通过 Vite 插件配置，无需独立 tailwind.config.ts）
- [X] T004 [P] 在 `src/main/frontend/postcss.config.js` 中配置 PostCSS（注：Tailwind CSS v4 Vite 插件内置 PostCSS，已删除该文件）
- [X] T005 [P] 在 `src/main/frontend/tsconfig.json` 中配置 TypeScript（`strict: true`、`jsx: "react-jsx"`、路径别名 `@/` → `src/`）
- [X] T006 在 `src/main/frontend/src/index.css` 中添加 Tailwind 指令（`@import "tailwindcss"`）和极少量全局样式
- [X] T007 修改 `src/main/java/top/lldwb/alistmediasync/config/WebMvcConfig.java`，添加 `/app/**` 路径的 Classpath 静态资源映射规则
- [X] T008 [P] 更新根目录 `Dockerfile`，添加前端构建阶段（`node:22-alpine` → `npm ci` → `npm run build` → 复制到 `src/main/resources/static/app/`）

**检查点**：`npm run dev` 可启动 HMR 开发服务器，`npm run build` 可输出到 `static/app/`

---

## 阶段 2：基础（核心基础设施 + 认证 US1）

**目的**：建立可复用的核心模块（HTTP 客户端、认证上下文、路由、布局、通用 UI 组件），同时覆盖 US1 登录认证

**⚠️ 关键**：在此阶段完成之前，不能开始任何管理页面（US2-US6）的工作

**US1 目标**：用户可通过独立登录页输入凭据认证，成功后进入管理主页，会话超时自动重新登录

**US1 独立测试**：`npm run dev` → 访问 `localhost:5173` → 重定向到 `/#/login` → 输入正确凭据 → 进入仪表板占位页 → 30 分钟无交互 → 跳回登录页

### 用户故事 1 的实现

- [X] T009 在 `src/main/frontend/src/types/api.ts` 中定义所有后端 API 响应的 TypeScript 接口（`ApiResult<T>`、`StorageEngineVO`、`SyncTaskVO`、`TaskExecution`、`TranscodeTaskVO`、`WebhookRuleVO`、`WebhookEventVO`、`DashboardStatsVO`、各 DTO 类型、枚举类型）
- [X] T010 在 `src/main/frontend/src/api/client.ts` 中实现 HTTP 请求封装（`fetch` 封装：`api.get<T>()` / `api.post<T>()` / `api.put<T>()` / `api.del<T>()` 泛型方法、Basic Auth 自动注入、401 拦截清除凭据并跳转 `/#/login`、`ApiResult<T>` 统一解析、15 秒 AbortController 超时）
- [X] T011 [P] 在 `src/main/frontend/src/auth/AuthContext.tsx` 中实现认证上下文（`AuthProvider` 组件 + `useAuth` hook：`login()` 验证凭据并存储到 `sessionStorage`、`logout()` 清除、`redirectPath` 状态管理）
- [X] T012 [P] 在 `src/main/frontend/src/auth/useSessionTimeout.ts` 中实现会话超时 hook（监听 `mousedown`/`keydown`/`scroll`/`touchstart` 事件，30 分钟倒计时，超时后调用 `logout()` 并跳转登录页）
- [X] T013 [P] 在 `src/main/frontend/src/utils/cron.ts` 中实现 cron 表达式 TypeScript 解析器（`parseCron(expr): CronResult`：5 字段格式校验、下次执行时间计算、中文人类可读描述生成，~120 行）
- [X] T014 [P] 在 `src/main/frontend/src/utils/format.ts` 中实现格式化工具（日期时间 ISO 8601 → 本地显示、文件大小字节 → 可读格式、任务状态枚举 → 中文文本、百分比格式化）
- [X] T015 [P] 在 `src/main/frontend/src/utils/validate.ts` 中实现表单校验工具（`validateRequired`、`validateUrl`、`validateCron`、`validatePath`、`validatePositiveInt`，返回 `{valid: boolean, error?: string}`）
- [X] T016 在 `src/main/frontend/src/router/index.tsx` 中配置 React Router Hash 路由表（`createHashRouter`：`/login` 独立路由 + `/` 受保护布局路由，含 7 个子路由，`ProtectedRoute` 认证守卫组件）
- [X] T017 在 `src/main/frontend/src/components/layout/AppLayout.tsx` 中实现主布局组件（左侧 `Sidebar` + 右侧 `<Outlet />`，使用 Tailwind 弹性布局 `flex h-screen`）
- [X] T018 [P] 在 `src/main/frontend/src/components/layout/Sidebar.tsx` 中实现侧边栏导航组件（菜单项列表：系统概览/存储引擎/同步任务/转码任务/Webhook规则/Webhook事件，`useLocation` 当前路由高亮，底部用户信息显示）
- [X] T019 [P] 在 `src/main/frontend/src/components/ui/LoadingSpinner.tsx` 中实现加载动画组件（SVG 旋转圆环，支持 `size` 属性：`sm`/`md`/`lg`）
- [X] T020 [P] 在 `src/main/frontend/src/components/ui/ConfirmDialog.tsx` 中实现删除确认对话框组件（Modal overlay + 标题"确认删除" + 显示对象名称 + 取消/确认按钮，通过 `onConfirm`/`onCancel` props 控制）
- [X] T021 [P] 在 `src/main/frontend/src/components/ui/EmptyState.tsx` 中实现空数据状态组件（图标 + 提示文字 + 可选操作按钮，通过 `title`/`description`/`actionLabel`/`onAction` props 定制）
- [X] T022 [P] 在 `src/main/frontend/src/components/ui/StatusBadge.tsx` 中实现状态标签组件（根据 `status` 属性渲染不同颜色：绿=成功/运行中（动画）、红=失败、黄=部分成功/等待中、灰=中断/禁用）
- [X] T023 [P] 在 `src/main/frontend/src/components/ui/ErrorBanner.tsx` 中实现错误横幅组件（红色横幅 + 错误消息 + 关闭按钮，`onRetry` 属性可选）
- [X] T024 在 `src/main/frontend/src/pages/LoginPage.tsx` 中实现登录页（独立布局无侧边栏：用户名/密码输入、前端校验必填、提交时调用 `useAuth().login()`、错误提示保留用户名不清空、成功跳转到 `redirectPath` 或 `/`）
- [X] T025 在 `src/main/frontend/src/main.tsx` 中创建 React 应用入口（`createRoot(document.getElementById('root')!)` → 渲染 `<AuthProvider><RouterProvider router={router} /></AuthProvider>`）
- [X] T026 更新 `src/main/frontend/index.html`（确保 `<div id="root">` 挂载点存在，引入 `src/main.tsx`）

**检查点**：用户可登录认证，导航框架可用，所有管理页面路由就绪但显示占位内容

---

## 阶段 3：用户故事 2 — 存储引擎管理（优先级：P1）

**目标**：用户可在 Web 界面中查看、添加、编辑、删除、测试连接 AList 存储引擎

**独立测试**：登录后进入"存储引擎"页面 → 点击"添加引擎" → 填写表单 → 保存 → 引擎出现在表格中 → 点击"测试连接" → 显示连接结果 → 编辑引擎信息 → 删除未被引用的引擎 → 尝试删除被引用引擎时看到冲突提示

### 用户故事 2 的实现

- [X] T027 [US2] 在 `src/main/frontend/src/components/ui/DataTable.tsx` 中实现通用数据表格组件（泛型 `<T>`，props：`columns: ColumnDef<T>[]`、`items: T[]`、`pageSize: number`、`emptyState: ReactNode`。内置前端分页控件：页码导航、每页条数、总页数）
- [X] T028 [P] [US2] 在 `src/main/frontend/src/hooks/usePagination.ts` 中实现前端分页 hook（接收全量 `items[]`，返回 `currentItems`、`currentPage`、`totalPages`、`goToPage`，默认每页 20 条）
- [X] T029 [US2] 在 `src/main/frontend/src/components/forms/EngineForm.tsx` 中实现存储引擎表单组件（名称/API地址/用户名/密码输入、URL 格式和必填前端校验、`onSubmit` 回调、支持 `initialValues` 用于编辑模式、`loading` 状态）
- [X] T030 [US2] 在 `src/main/frontend/src/pages/EngineListPage.tsx` 中实现存储引擎列表页（调用 `api.get<StorageEngineVO[]>('/storage-engines')` 获取数据、DataTable 渲染、`EmptyState`（无引擎时）、"添加引擎"按钮 → 显示 EngineForm、点击行"编辑" → 显示 EngineForm（编辑模式）、"测试连接"按钮 → 调用 `api.post('/storage-engines/{id}/test')` → 绿色/红色反馈、删除按钮 → ConfirmDialog → `api.del()` → 刷新列表）

**检查点**：存储引擎 CRUD 全流程可在 React 界面中完成

---

## 阶段 4：用户故事 3 — 同步任务全生命周期管理（优先级：P1）

**目标**：用户可创建/编辑/启用/禁用/手动触发同步任务，查看实时进度和执行历史

**独立测试**：登录后进入"同步任务"页面 → 创建任务（选择源/目标引擎、填写路径、选择同步模式和执行计划）→ 保存 → 手动触发 → 观察实时进度变化 → 查看执行历史 → 展开失败记录查看详情 → 禁用任务调度

### 用户故事 3 的实现

- [X] T031 [US3] 在 `src/main/frontend/src/components/forms/SyncTaskForm.tsx` 中实现同步任务表单组件
- [X] T032 [US3] 在 `src/main/frontend/src/pages/SyncTaskListPage.tsx` 中实现同步任务列表页
- [X] T033 [US3] 在 `src/main/frontend/src/hooks/usePolling.ts` 中实现通用轮询 hook
- [X] T034 [US3] 在 `src/main/frontend/src/pages/SyncTaskListPage.tsx` 中添加实时进度（usePolling 轮询）
- [X] T035 [US3] 在 `src/main/frontend/src/pages/SyncTaskDetailPage.tsx` 中实现同步任务详情页
- [X] T036 [US3] 在 `src/main/frontend/src/pages/SyncTaskDetailPage.tsx` 中添加执行历史 Tab

**检查点**：同步任务全生命周期可在 React 界面中完成

---

## 阶段 5：用户故事 4 — 转码任务管理与监控（优先级：P2）

**目标**：用户可创建独立转码任务、查看进度、重试上传失败文件、清理残留临时文件

**独立测试**：登录后进入"转码任务"页面 → 创建转码任务（选择引擎、路径、格式、码率）→ 观察进度 → 对上传失败任务点击"重试上传"→ 点击"清理残留文件"→ 查看清理结果

### 用户故事 4 的实现

- [X] T037 [US4] 在 `src/main/frontend/src/components/forms/TranscodeTaskForm.tsx` 中实现转码任务表单组件
- [X] T038 [US4] 在 `src/main/frontend/src/pages/TranscodeTaskListPage.tsx` 中实现转码任务列表页

**检查点**：转码任务全流程可在 React 界面中管理

---

## 阶段 6：用户故事 5 — Webhook 规则配置与事件查看（优先级：P2）

**目标**：用户可创建/编辑/启用/禁用 Webhook 处理规则，查看接收到的 Webhook 事件记录

**独立测试**：登录后进入"Webhook 规则"页面 → 创建规则 → 保存 → 启用/禁用 → 进入"Webhook 事件"页面 → 查看事件列表或空状态引导

### 用户故事 5 的实现

- [X] T039 [US5] 在 `src/main/frontend/src/components/forms/WebhookRuleForm.tsx` 中实现 Webhook 规则表单组件
- [X] T040 [US5] 在 `src/main/frontend/src/pages/WebhookRuleListPage.tsx` 中实现 Webhook 规则列表页
- [X] T041 [US5] 在 `src/main/frontend/src/pages/WebhookEventListPage.tsx` 中实现 Webhook 事件列表页

**检查点**：Webhook 规则和事件查看可在 React 界面中完成

---

## 阶段 7：用户故事 6 — 系统概览仪表板（优先级：P3）

**目标**：用户登录后首先看到系统概览，汇总关键运行指标，卡片可点击跳转

**独立测试**：登录后进入仪表板 → 查看统计卡片数据 → 点击卡片跳转到对应模块 → 无活跃任务时显示"系统当前空闲"

### 用户故事 6 的实现

- [X] T042 [US6] 在 `src/main/java/top/lldwb/alistmediasync/dto/DashboardStatsVO.java` 中创建仪表板统计响应 DTO
- [X] T043 [US6] 在 `src/main/java/top/lldwb/alistmediasync/service/DashboardService.java` 中实现聚合统计查询服务
- [X] T044 [US6] 在 `src/main/java/top/lldwb/alistmediasync/controller/DashboardController.java` 中实现仪表板统计 API
- [X] T045 [US6] 在 `src/main/frontend/src/pages/DashboardPage.tsx` 中实现仪表板页面

**检查点**：仪表板可展示系统运行概览，所有卡片可点击跳转

---

## 阶段 8：后端 Webhook 事件查询端点（用户故事 5 依赖）

**目的**：US5 的 Webhook 事件页面需要后端提供事件分页查询端点

- [X] T046 在 `src/main/java/top/lldwb/alistmediasync/controller/WebhookEventController.java` 中新增 Webhook 事件分页查询端点

---

## 阶段 9：润色与跨领域关注点

**目的**：影响多个用户故事的改进和最终验证

- [ ] T047 [P] 使用 Tailwind 响应式断点（`md:`/`lg:`）优化 `Sidebar.tsx` 和 `AppLayout.tsx`，768px 以下折叠侧边栏为顶部汉堡菜单
- [ ] T048 [P] 在 `src/main/frontend/src/components/ui/ErrorBanner.tsx` 中添加网络中断检测（`navigator.onLine` + `fetch` 失败时显示"连接失败，正在重试"横幅，`window.addEventListener('online')` 恢复后自动重试）
- [X] T049 [P] 在 `src/test/java/top/lldwb/alistmediasync/controller/DashboardControllerTest.java` 中编写仪表板 API 测试（`@WebMvcTest`，Mock `DashboardService`，验证 `ApiResult` 响应结构和 HTTP 状态码）
- [X] T050 在所有页面组件中完善空数据状态（确保 `EngineListPage`、`SyncTaskListPage`、`TranscodeTaskListPage`、`WebhookRuleListPage`、`WebhookEventListPage` 无数据时使用 `EmptyState` 组件，提供引导性操作按钮）
- [X] T051 在 `EngineListPage.tsx` 中确保凭据字段脱敏显示（密码/Token 列显示为 `***`）。注：后端 StorageEngineVO 不返回 Token/密码字段，前端不展示凭据信息。
- [X] T052 全局错误处理审查（确保所有 `try/catch` 有用户友好错误提示，ErrorBanner 或 toast 通知显示错误消息）
- [X] T053 运行 `npm run typecheck`（`tsc --noEmit`）确保零 TypeScript 类型错误
- [X] T054 运行 `npm run build` 验证生产构建产物成功，大小在目标范围内（108.3 KB gzip，< 150KB 目标）
- [ ] T055 执行 `quickstart.md` 中全部 7 个验证场景 + 边界情况手动测试，记录并修复发现的缺陷

**检查点**：所有功能页面就绪且通过 quickstart.md 验证

---

## 依赖与执行顺序

### 阶段依赖

- **设置（阶段 1）**：无依赖 — 可立即开始
- **基础 + US1（阶段 2）**：依赖阶段 1 完成（需要 `package.json`、`vite.config.ts`、`tsconfig.json`）— 阻塞所有管理页面（US2-US6）
- **US2 存储引擎（阶段 3）**：依赖阶段 2 完成 — 需要 `DataTable`、`usePagination`、`api/client.ts`、`types/api.ts`、`AppLayout`
- **US3 同步任务（阶段 4）**：依赖阶段 2 + 阶段 3（需要 `DataTable`、`usePagination`、`usePolling`）— 需要 US2 的引擎数据但不阻塞代码编写
- **US4 转码任务（阶段 5）**：依赖阶段 2 + 阶段 3（需要 `DataTable`、`usePagination`、`usePolling`）
- **US5 Webhook（阶段 6）**：依赖阶段 2 + 阶段 3 + 阶段 8 后端端点
- **US6 仪表板（阶段 7）**：依赖阶段 2 + 阶段 7 后端（T042-T044）
- **后端事件端点（阶段 8）**：依赖阶段 1 — 可并行于前端阶段 3-7
- **润色（阶段 9）**：依赖所有期望的用户故事完成

### 用户故事依赖

- **US1（P1）**：阶段 2 实现 — 所有其他故事的前置条件
- **US2（P1）**：不依赖其他故事，但需要提供引擎数据给 US3/US4 的表单下拉选择
- **US3（P1）**：表单中需要引擎下拉数据（US2 完成提供列表 API），但不阻塞代码编写（可先写 UI 后联调）
- **US4（P2）**：需要引擎下拉数据，不阻塞代码编写
- **US5（P2）**：需要阶段 8 后端事件查询端点 + 引擎下拉数据
- **US6（P3）**：需要阶段 7 后端 DashboardController 完成

### 每个用户故事内部

- TypeScript 类型定义先于组件
- 通用 UI 组件先于页面组件
- hooks 先于页面集成
- 表单组件先于列表页的 CRUD 操作
- 核心功能先于进度更新和轮询

### 并行机会

- 阶段 1 内：T003、T004、T005 可并行（Tailwind 配置、PostCSS 配置、TypeScript 配置互不依赖）
- 阶段 2 内：T011、T012、T013、T014、T015 可并行（5 个独立模块，不同文件）；T016-T023 可并行（组件彼此独立）
- 阶段 3-7（前端） + 阶段 7/8（后端）：前后端可并行
  - 前端 US2 → US3 → US4 → US5（依次或并行）
  - 后端（T042-T044 仪表板、T046 WebhookEvents）可与前端 US2-US5 并行
- 阶段 9 内：T047、T048、T049 可并行（不同文件）

---

## 并行示例：阶段 2 核心模块

```bash
# 5 个独立模块并行开发（不同文件，无依赖）：
T011："在 AuthContext.tsx 中实现认证上下文"
T012："在 useSessionTimeout.ts 中实现会话超时 hook"
T013："在 cron.ts 中实现 cron 表达式解析器"
T014："在 format.ts 中实现格式化工具"
T015："在 validate.ts 中实现表单校验工具"

# 7 个 UI 组件并行开发（不同文件，互不依赖）：
T017："在主布局 AppLayout.tsx"
T018："在侧边栏 Sidebar.tsx"
T019："在加载动画 LoadingSpinner.tsx"
T020："在确认对话框 ConfirmDialog.tsx"
T021："在空状态 EmptyState.tsx"
T022："在状态标签 StatusBadge.tsx"
T023："在错误横幅 ErrorBanner.tsx"
```

## 并行示例：前端页面组件 + 后端服务

```bash
# 前后端完全并行（不同技术栈，无依赖）：
# 开发者 A — 前端：
T029："在 EngineForm.tsx 中实现存储引擎表单"
T030："在 EngineListPage.tsx 中实现存储引擎列表页"

# 开发者 B — 后端：
T042："创建 DashboardStatsVO.java DTO"
T043："实现 DashboardService.java 聚合统计查询"
T044："实现 DashboardController.java 仪表板 API"
T046："新增 WebhookEventController.java 事件查询端点"（如需）
```

---

## 实现策略

### MVP 优先（US1 + US2）

1. 完成阶段 1：设置（Vite 项目 + 后端路由映射 + Dockerfile 更新）
2. 完成阶段 2：基础 + US1（核心模块 + 登录页 + 导航框架）
3. 完成阶段 3：US2 存储引擎管理
4. **停止并验证**：用户可登录（React 界面）→ 管理存储引擎（CRUD + 连接测试）
5. 部署/演示 — 已可用！

### 增量交付

1. 设置 + 基础 + US1 → 登录认证可用
2. 添加 US2 存储引擎 → 独立测试 → 演示
3. 添加 US3 同步任务 → 独立测试 → 演示（核心功能就绪！）
4. 添加 US4 转码任务 → 独立测试 → 演示
5. 添加 US5 Webhook → 独立测试 → 演示
6. 添加 US6 仪表板 → 独立测试 → 演示
7. 润色 → 类型检查 + 生产构建 + quickstart 验证

### 推荐 MVP 范围

**阶段 1 + 阶段 2（US1）+ 阶段 3（US2）** = 用户可通过 React 界面登录并管理存储引擎。这是最小可用的交付，共 30 个任务。

### 开发工作流

```bash
# 日常开发
cd src/main/frontend
npm run dev          # 启动 HMR 开发服务器（:5173）
# 后端在另一个终端：mvn spring-boot:run （:8080）

# 提交前检查
npm run typecheck    # tsc --noEmit — 零类型错误
npm run build        # 验证生产构建成功

# 全量构建（含后端）
cd <项目根目录>
mvn package          # 如 Dockerfile 已集成前端构建
```

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应能独立完成和测试
- 前端构建输出目录 `src/main/resources/static/app/` 通过 Vite 自动管理，不要手动编辑
- 开发期间使用 Vite 代理避免跨域，生产构建同源部署
- 所有 TypeScript 类型定义集中在 `src/types/api.ts`
- 每个任务或逻辑组后提交 Git
- 在任何检查点停止以独立验证故事
- 避免：模糊的任务、同文件冲突、破坏独立性的跨故事依赖
