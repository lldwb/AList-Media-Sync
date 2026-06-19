# 实现计划：Web 管理前端界面

**分支**：`004-web-management-frontend` | **日期**：2026-06-19 | **修订**：2026-06-19（技术栈迁移至 React 19 + TypeScript + Vite + Tailwind CSS） | **规格**：[spec.md](./spec.md)

**输入**：来自 `specs/004-web-management-frontend/spec.md` 的功能规格

## 摘要

为 AList-Media-Sync 全部后端功能（001 + 002）构建 Web 管理前端界面。核心交付物包括：React 19 + TypeScript + Vite 单页应用（SPA），覆盖认证登录、存储引擎管理、同步任务管理（含执行历史）、转码任务管理、Webhook 规则配置、Webhook 事件查看、系统概览仪表板共 7 个功能页面。样式采用 Tailwind CSS 原子化方案。新增 1 个后端聚合统计端点（`GET /api/dashboard/stats`）。前端经 Vite 构建后作为静态资源输出到 Spring Boot 静态资源目录，与后端同端口提供。

## 技术上下文

**语言/版本**：TypeScript（ES2020+ target），React 19；后端 Java 21 + Spring Boot 4.1.0

**主要依赖**：
- **前端**：React 19 + ReactDOM 19、React Router v7（Hash 模式）、Tailwind CSS 4.x（Vite 插件）
- **构建工具**：Vite 6.x（Rollup 打包）、TypeScript 5.x（编译）
- **后端新增**：无新增依赖（DashboardService 使用现有 Repository 聚合查询）

**存储**：前端无持久化存储需求；sessionStorage 用于凭据和会话状态；后端 H2（与 001/002 共享）

**测试**：前端 TypeScript 编译时类型检查 + 手工验收测试（通过 quickstart.md 场景验证）；后端新增 `DashboardController` 的 `@WebMvcTest`

**目标平台**：桌面浏览器（Chrome 118+、Firefox 121+、Edge 120+），最低分辨率 1024×768，平板 768px 基本可用

**项目类型**：单体 Web 服务（后端 + Vite 构建输出的静态前端）

**性能目标**：页面首次加载（含 20 条记录列表）< 3 秒，页面切换 < 2 秒，表单提交反馈 < 5 秒。构建产物目标 < 100KB gzip。

**约束**：Vite 构建步骤（开发 HMR < 500ms）、Hash 路由（无需服务端 fallback 配置）、前端分页（不改造后端分页）、不修改现有 API 契约（仪表板为纯新增）

**规模/范围**：~25 个 `.tsx`/`.ts` 文件（~3000 行 TypeScript）+ ~100 行 Tailwind CSS 指令 + 1 个后端 Controller + 1 个 Service + 1 个 DTO + 1 个测试

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

| 章程原则 | 适用性 | 状态 |
|---------|--------|------|
| I. 分层架构（不可协商） | **适用（后端新增）** — DashboardController → DashboardService → Repository 严格遵循三层 | ✅ 通过 |
| II. 数据完整性优先 | **不适用** — 仪表板统计为纯查询，无写操作 | ✅ 通过 |
| III. RESTful API 契约优先 | **适用** — 新增 `GET /api/dashboard/stats` 使用 `ApiResult<T>` 封装，不修改现有端点 | ✅ 通过 |
| IV. 中文优先 | **适用** — 所有前端 UI 文本、TypeScript 注释、Tailwind 注释使用简体中文 | ✅ 通过 |
| V. 测试不可省略 | **适用（后端新增）** — DashboardService 单元测试、DashboardController API 测试 | ✅ 通过 |
| VI. 简洁至上（YAGNI） | **适用** — React + Vite + Tailwind 是当前最精简的组件化方案；cron 自解析、前端分页、无 WebSocket；Tailwind 消除手写 CSS；Vite 构建全自动 | ✅ 通过 |

**门禁结果**：全部通过，无违规项。

## 项目结构

### 文档（本功能）

```text
specs/004-web-management-frontend/
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── spec.md              # 功能规格（/speckit-specify 命令输出）
├── research.md          # 阶段 0 输出（技术选型决策）
├── data-model.md        # 阶段 1 输出（前端数据模型）
├── quickstart.md        # 阶段 1 输出（验证场景）
├── contracts/           # 阶段 1 输出（API 契约）
│   └── api-contracts.md
└── tasks.md             # 阶段 2 输出（/speckit-tasks 命令创建）
```

### 源代码（仓库根目录）

```text
src/main/frontend/                    # 前端项目根目录
├── index.html                        # Vite 入口 HTML
├── package.json                      # 依赖与脚本
├── tsconfig.json                     # TypeScript 配置（strict: true）
├── vite.config.ts                    # Vite 构建配置
├── tailwind.config.ts                # Tailwind 配置
├── postcss.config.js                 # PostCSS 配置
├── public/                           # 直接复制的静态资源
└── src/
    ├── main.tsx                      # React 应用入口（createRoot → RouterProvider）
    ├── App.tsx                       # 根组件：AuthProvider + RouterProvider
    ├── index.css                     # Tailwind 指令（@tailwind base/components/utilities）
    ├── api/
    │   └── client.ts                 # fetch 封装（Basic Auth、401 拦截、ApiResult 解析、超时 15s）
    ├── auth/
    │   ├── AuthContext.tsx            # 认证上下文（Provider + useAuth hook）
    │   └── useSessionTimeout.ts      # 会话超时 hook（监听交互事件，30 分钟倒计时）
    ├── router/
    │   └── index.tsx                 # createHashRouter 路由表（7 条路由 + 认证守卫）
    ├── hooks/
    │   ├── usePolling.ts             # 通用轮询 hook（setInterval 5s）
    │   └── usePagination.ts          # 前端分页 hook（每页 20 条）
    ├── utils/
    │   ├── cron.ts                   # cron 表达式解析器（~120 行 TS）
    │   ├── format.ts                 # 日期时间/文件大小/状态文本格式化
    │   └── validate.ts               # 表单校验（必填、URL 格式、cron 格式、路径格式）
    ├── types/
    │   └── api.ts                    # 后端 DTO/VO 类型定义（StorageEngineVO、SyncTaskVO 等）
    ├── components/
    │   ├── layout/
    │   │   ├── AppLayout.tsx          # 主布局（侧边栏 240px + 内容区）
    │   │   └── Sidebar.tsx            # 侧边栏导航（当前路由高亮）
    │   ├── ui/
    │   │   ├── DataTable.tsx          # 通用数据表格（分页控件、列定义、空状态）
    │   │   ├── ConfirmDialog.tsx      # 删除确认对话框（显示对象名称）
    │   │   ├── LoadingSpinner.tsx     # 加载动画（按钮内联 + 全页 overlay）
    │   │   ├── ErrorBanner.tsx        # 错误/网络中断提示横幅
    │   │   ├── EmptyState.tsx         # 空数据状态（图标 + 文字 + 操作按钮）
    │   │   └── StatusBadge.tsx        # 状态标签（颜色映射：运行中/成功/失败/中断）
    │   └── forms/
    │       ├── EngineForm.tsx         # 存储引擎表单（名称/URL/用户名/密码、URL 校验）
    │       ├── SyncTaskForm.tsx       # 同步任务表单（条件字段切换、cron 实时预览）
    │       ├── TranscodeTaskForm.tsx  # 转码任务表单（引擎选择/路径/格式/码率）
    │       └── WebhookRuleForm.tsx    # Webhook 规则表单（事件类型/房间号/动作条件联动）
    └── pages/
        ├── LoginPage.tsx             # 登录页（独立布局，无侧边栏）
        ├── DashboardPage.tsx         # 仪表板（4 张统计卡片 + 点击跳转）
        ├── EngineListPage.tsx        # 存储引擎列表（表格 + 测试连接 + CRUD）
        ├── SyncTaskListPage.tsx      # 同步任务列表（表格 + 进度轮询 + 操作按钮）
        ├── SyncTaskDetailPage.tsx    # 同步任务详情（Tab：基本信息 / 执行历史展开）
        ├── TranscodeTaskListPage.tsx # 转码任务列表（表格 + 进度 + 重试/清理按钮）
        ├── WebhookRuleListPage.tsx   # Webhook 规则列表（表格 + 启用/禁用开关）
        └── WebhookEventListPage.tsx  # Webhook 事件列表（表格 + 空状态引导）

src/main/java/top/lldwb/alistmediasync/
├── controller/
│   └── DashboardController.java     # 新增：仪表板统计 API
├── service/
│   └── DashboardService.java        # 新增：聚合统计查询服务
├── dto/
│   └── DashboardStatsVO.java        # 新增：仪表板统计响应 DTO
└── config/
    └── WebMvcConfig.java            # 修改：确保 /app/** 正确映射 Classpath 静态资源

src/test/java/top/lldwb/alistmediasync/
└── controller/
    └── DashboardControllerTest.java  # 新增：仪表板 API 测试
```

**结构决策**：前端采用独立项目目录 `src/main/frontend/`，与后端 Java 代码物理分离。Vite 开发服务器通过 proxy 转发 API 请求到 `localhost:8080`，构建产物输出到 `src/main/resources/static/app/`。React 组件按职责分层：`components/layout/`（布局）、`components/ui/`（可复用 UI 组件）、`components/forms/`（业务表单）、`pages/`（页面级组件）。认证通过 React Context（`AuthContext`）全局共享状态。后端新增三层严格遵循章程分层。

## 技术设计决策

### 1. React 19 + React Router v7 Hash SPA 架构

**决策**：单页应用 + Hash 路由 + 独立登录页模式。

**理由**：
- React 19 的并发特性（`useTransition`、`useDeferredValue`）可用于列表过滤等场景提升交互流畅度
- React Router v7 的 `createHashRouter` 提供声明式路由、`loader`/`action` 模式、`useNavigate` 等完备 API
- Hash 路由无需服务端 fallback，与 Spring Boot 静态资源服务天然兼容
- 登录页仍为独立路由（`/login`），在 `AppLayout` 之外渲染，未认证时不加载侧边栏和管理组件

**认证流程**：
1. 用户访问 `/app/#/` → Router 的 `loader` 检查 `sessionStorage` 凭据 → 无凭据 → `redirect('/login')`
2. 登录页提交凭据 → `client.ts` 发送 `GET /api/dashboard/stats` 验证 → 成功 → 存储到 `sessionStorage` + 更新 `AuthContext` → `navigate('/')`
3. 30 分钟无交互 → `useSessionTimeout` 清除凭据 → `AuthContext.setAuth(null)` → `navigate('/login')`
4. 任何 API 返回 401 → `client.ts` 全局拦截 → 清除凭据 → `navigate('/login')`，保留 `redirectPath`

### 2. React Router Hash 路由表

```typescript
// src/main/frontend/src/router/index.tsx
const router = createHashRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: <ProtectedRoute><AppLayout /></ProtectedRoute>,
    children: [
      { index: true,          element: <DashboardPage /> },
      { path: 'engines',       element: <EngineListPage /> },
      { path: 'sync-tasks',    element: <SyncTaskListPage /> },
      { path: 'sync-tasks/:id',element: <SyncTaskDetailPage /> },
      { path: 'transcode-tasks',element: <TranscodeTaskListPage /> },
      { path: 'webhook-rules', element: <WebhookRuleListPage /> },
      { path: 'webhook-events',element: <WebhookEventListPage /> },
    ],
  },
]);
```

`ProtectedRoute` 组件检查 `AuthContext.isAuthenticated`，未认证时重定向到 `/login` 并记录当前路径。

### 3. 导航布局（Tailwind CSS 实现）

采用左侧固定侧边栏（`w-60` / 240px）+ 右侧内容区（`flex-1`）的经典管理后台布局：

```
┌─────────────┬──────────────────────────────────┐
│  Logo/标题   │                                  │
│  系统概览    │         <Outlet />               │
│  存储引擎    │         页面内容区域              │
│  同步任务    │    (max-h-screen overflow-auto)   │
│  转码任务    │                                  │
│  Webhook规则 │                                  │
│  Webhook事件 │                                  │
│             │                                  │
│  [用户信息]  │                                  │
└─────────────┴──────────────────────────────────┘
```

- 侧边栏固定在左侧 `h-screen sticky top-0`
- 当前路由在侧边栏中高亮（React Router `useLocation` + Tailwind `bg-primary-50 text-primary-700`）
- 平板设备（`md:` 断点 768px）侧边栏折叠为顶部导航

### 4. HTTP 请求封装（client.ts）

```typescript
// src/main/frontend/src/api/client.ts
const API_BASE = '/api';

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };
  const creds = sessionStorage.getItem('auth_credentials');
  if (creds) headers['Authorization'] = `Basic ${creds}`;

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 15000);

  try {
    const res = await fetch(`${API_BASE}${path}`, {
      method, headers,
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });

    if (res.status === 401) {
      sessionStorage.removeItem('auth_credentials');
      window.location.hash = '#/login';
      throw new Error('未认证');
    }

    const result: ApiResult<T> = await res.json();
    if (result.code !== 200) {
      throw new Error(result.message || '请求失败');
    }

    return result.data as T;
  } finally {
    clearTimeout(timeoutId);
  }
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
};
```

### 5. 认证上下文（AuthContext.tsx）

```typescript
// src/main/frontend/src/auth/AuthContext.tsx
interface AuthState {
  username: string;
  credentials: string; // Base64(username:password)
}

const AuthContext = createContext<{
  auth: AuthState | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  redirectPath: string | null;
  setRedirectPath: (path: string | null) => void;
}>(null!);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthState | null>(() => {
    const saved = sessionStorage.getItem('auth_credentials');
    const username = sessionStorage.getItem('auth_username');
    return saved && username ? { username, credentials: saved } : null;
  });

  const login = async (username: string, password: string) => {
    const credentials = btoa(`${username}:${password}`);
    // 通过一次真实 API 调用验证凭据
    await api.get('/dashboard/stats');
    sessionStorage.setItem('auth_credentials', credentials);
    sessionStorage.setItem('auth_username', username);
    setAuth({ username, credentials });
  };

  const logout = () => {
    sessionStorage.removeItem('auth_credentials');
    sessionStorage.removeItem('auth_username');
    setAuth(null);
  };

  // ... 提供 redirectPath 状态
}
```

### 6. Tailwind CSS 设计系统

通过 `tailwind.config.ts` 扩展项目主题：

```typescript
// tailwind.config.ts
export default {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#eff6ff',   // 浅蓝背景
          500: '#3b82f6',  // 主色
          700: '#1d4ed8',  // 深色文字
        },
        success: '#16a34a',
        danger: '#dc2626',
        warning: '#f59e0b',
      },
      fontSize: {
        'table': '0.875rem',  // 表格内容 14px
      },
    },
  },
};
```

- 颜色语义化：`primary`（主色蓝）、`success`（成功绿）、`danger`（危险红）、`warning`（警告黄）
- 所有间距使用 Tailwind 内置尺度（`p-4`、`m-2`、`gap-3`），保持视觉一致性
- 响应式断点：`md:`（768px）、`lg:`（1024px），移动端不优化但保持基本可用

### 7. cron 表达式 TypeScript 解析器（utils/cron.ts）

```typescript
// src/main/frontend/src/utils/cron.ts  (~120 行)
interface CronResult {
  valid: boolean;
  description?: string;     // "每 6 小时执行一次"
  nextExecution?: Date;     // 下次执行时间
  error?: string;           // 校验失败时的错误信息
}

function parseCron(expr: string): CronResult {
  // 1. 分割并校验 5 个字段
  // 2. 每字段解析：数字、*、范围(1-5)、步进(*/5)、列表(1,3,5)
  // 3. 计算下次执行时间
  // 4. 生成人类可读描述（中文）
}
```

### 8. 进度轮询 hook（usePolling.ts）

```typescript
// src/main/frontend/src/hooks/usePolling.ts
function usePolling<T>(
  fetcher: () => Promise<T>,
  interval: number = 5000,
  stopCondition: (data: T) => boolean,
) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let timer: number;
    const poll = async () => {
      try {
        const result = await fetcher();
        setData(result);
        if (stopCondition(result)) return; // 完成时停止
        timer = window.setTimeout(poll, interval);
      } catch (e) {
        setError(e as Error);
        timer = window.setTimeout(poll, interval); // 错误后也继续重试
      }
    };
    poll();
    return () => clearTimeout(timer);
  }, []);

  return { data, error };
}
```

同步任务进度页和转码任务页共用此 hook，分别传入不同的 `fetcher` 和 `stopCondition`。

### 9. 表单条件校验

同步任务表单中的"执行计划类型"驱动条件字段的显示和校验：

| scheduleType | 必填字段 | 隐藏字段 |
|-------------|---------|---------|
| INTERVAL | `intervalSeconds` | `cronExpression` |
| CRON | `cronExpression` | `intervalSeconds` |
| MANUAL | 无 | `intervalSeconds`, `cronExpression` |

通过 React 条件渲染（`{scheduleType === 'CRON' && <CronInput />}`）切换显示。提交前通过 `validate.ts` 中的 `validateSyncTaskForm(values)` 统一校验。

### 10. 仪表板统计端点（后端新增，不变）

```java
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ApiResult<DashboardStatsVO> getStats() {
        return ApiResult.success(dashboardService.getStats());
    }
}
```

`DashboardService.getStats()` 通过 Repository 执行聚合查询。

## 数据模型概要

详见 [data-model.md](./data-model.md)。迁移后核心变更：

- **前端模型**：从 JavaScript 运行时对象转为 TypeScript 接口定义（`src/main/frontend/src/types/api.ts`）
- **AuthState**：React Context 管理，sessionStorage 持久化
- **组件状态**：各页面组件使用 `useState`/`useReducer` 管理列表、表单、分页状态
- **不引入新数据库实体**：仪表板统计为纯查询聚合

## 复杂性追踪

| 偏离项 | 说明 |
|--------|------|
| 技术栈变更（Vue CDN → React + Vite） | 增加 Node.js 和构建步骤作为开发依赖，但带来类型安全（TypeScript）、更好的代码组织（组件化）和更小的生产构建（Tailwind JIT）。此偏离已经 spec.md A2 假设的预留弹性覆盖——"如评估后发现轻量框架能显著提升开发效率和代码可维护性，可在 plan 阶段讨论"。章程 VI（YAGNI）合规：Vite + Tailwind 的自动化程度使其复杂度低于手写 500 行 CSS + 零构建调试。 |

## 与其他规格的关系

| 规格 | 关系 | 说明 |
|------|------|------|
| 001-alist-media-sync | **UI 层覆盖** | 004 为 001 的全部管理 API 提供 Web 前端界面 |
| 002-transcode-temp-suffix-config | **UI 层覆盖** | 004 为 002 的重试上传、残留文件清理提供 Web 操作入口 |
| 003-docker-deploy | **基础设施依赖（需更新）** | 004 的 Dockerfile 需添加前端构建阶段（Node.js → Vite build），003 原 Dockerfile 仅包含 Java 构建 |
