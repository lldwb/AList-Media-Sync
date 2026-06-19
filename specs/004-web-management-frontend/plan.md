# 实现计划：Web 管理前端界面

**分支**：`004-web-management-frontend` | **日期**：2026-06-19 | **规格**：[spec.md](./spec.md)

**输入**：来自 `specs/004-web-management-frontend/spec.md` 的功能规格

## 摘要

为 AList-Media-Sync 全部后端功能（001 + 002）构建 Web 管理前端界面。核心交付物包括：Vue.js 3 CDN 单页应用（SPA），覆盖认证登录、存储引擎管理、同步任务管理（含执行历史）、转码任务管理、Webhook 规则配置、Webhook 事件查看、系统概览仪表板共 7 个功能页面。新增 1 个后端聚合统计端点（`GET /api/dashboard/stats`）。前端作为静态资源提供，与后端同端口，无需额外 Web 服务器。

## 技术上下文

**语言/版本**：JavaScript（ES2020+），无 TypeScript（零构建步骤）；后端 Java 21 + Spring Boot 4.1.0

**主要依赖**：
- **前端**：Vue.js 3 CDN（`vue.global.prod.js` ~130KB）、Vue Router 4 CDN（~30KB）
- **后端新增**：无新增依赖（DashboardService 使用现有 Repository 聚合查询）

**存储**：前端无持久化存储需求；sessionStorage 用于凭据和会话状态；后端 H2（与 001/002 共享）

**测试**：前端手工验收测试（通过 quickstart.md 场景验证）；后端新增 `DashboardController` 的 `@WebMvcTest`

**目标平台**：桌面浏览器（Chrome 118+、Firefox 121+、Edge 120+），最低分辨率 1024×768，平板 768px 基本可用

**项目类型**：单体 Web 服务（后端 + 内嵌静态前端）

**性能目标**：页面首次加载（含 20 条记录列表）< 3 秒，页面切换 < 2 秒，表单提交反馈 < 5 秒

**约束**：零构建步骤（无 npm/webpack/vite）、Hash 路由（无需服务端 fallback 配置）、前端分页（不改造后端分页）、不修改现有 API 契约（仪表板为纯新增）

**规模/范围**：~5 个 HTML 文件 + ~10 个 JS 模块（~2500 行 JS + ~500 行 CSS）+ 1 个后端 Controller + 1 个 Service + 1 个测试

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

| 章程原则 | 适用性 | 状态 |
|---------|--------|------|
| I. 分层架构（不可协商） | **适用（后端新增）** — DashboardController → DashboardService → Repository 严格遵循三层 | ✅ 通过 |
| II. 数据完整性优先 | **不适用** — 仪表板统计为纯查询，无写操作 | ✅ 通过 |
| III. RESTful API 契约优先 | **适用** — 新增 `GET /api/dashboard/stats` 使用 `ApiResult<T>` 封装，不修改现有端点 | ✅ 通过 |
| IV. 中文优先 | **适用** — 所有前端 UI 文本、JS 注释、CSS 注释使用简体中文 | ✅ 通过 |
| V. 测试不可省略 | **适用（后端新增）** — DashboardService 单元测试、DashboardController API 测试 | ✅ 通过 |
| VI. 简洁至上（YAGNI） | **适用** — Vue.js CDN 引入（零构建工具）、cron 自解析（不引库）、前端分页（不改造后端）、无 WebSocket | ✅ 通过 |

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
src/main/resources/static/app/
├── index.html                     # SPA 入口（Vue 挂载点 + 导航框架）
├── login.html                     # 独立登录页（非 SPA，安全隔离）
├── css/
│   └── app.css                    # 全局样式（~500 行）
├── js/
│   ├── app.js                     # Vue 应用初始化、路由定义、全局组件注册
│   ├── api.js                     # fetch 封装（Basic Auth 注入、错误拦截、超时处理）
│   ├── auth.js                    # 认证管理（登录/登出/会话超时/401 拦截）
│   ├── router.js                  # Vue Router 配置（Hash 模式，路由表）
│   ├── utils.js                   # 工具函数（cron 解析、日期格式化、URL 校验、文件大小格式化）
│   └── pages/                     # 页面组件（Vue Options API）
│       ├── login.js               # 登录页逻辑
│       ├── dashboard.js           # 仪表板（统计卡片 + 跳转）
│       ├── engines.js             # 存储引擎管理（列表 + CRUD + 连接测试）
│       ├── sync-tasks.js          # 同步任务管理（列表 + 创建/编辑 + 进度监控）
│       ├── sync-task-detail.js    # 同步任务详情（基本信息 + 执行历史展开）
│       ├── transcode-tasks.js     # 转码任务管理（列表 + 创建 + 重试 + 清理）
│       ├── webhook-rules.js       # Webhook 规则管理（列表 + 创建/编辑 + 启用/禁用）
│       └── webhook-events.js      # Webhook 事件查看（列表 + 空状态引导）
└── vendor/                        # 本地备用（离线降级）
    ├── vue.global.prod.js         # Vue 3.5.x 生产版本
    └── vue-router.global.prod.js  # Vue Router 4.x 生产版本

src/main/java/top/lldwb/alistmediasync/
├── controller/
│   └── DashboardController.java   # 新增：仪表板统计 API
├── service/
│   └── DashboardService.java      # 新增：聚合统计查询服务
└── config/
    └── WebMvcConfig.java          # 修改：添加 /app 路径映射

src/test/java/top/lldwb/alistmediasync/
└── controller/
    └── DashboardControllerTest.java  # 新增：仪表板 API 测试
```

**结构决策**：前端采用"SPA + 独立登录页"模式。登录页独立于 SPA（`login.html`），确保未认证状态下 Vue 应用不会加载。SPA 入口 `index.html` 挂载 Vue 3 应用，所有管理页面通过 Vue Router Hash 路由渲染为组件。JS 模块按职责分离（api/auth/router/utils/pages），CSS 单个文件覆盖全部样式。后端新增 `DashboardController` 和 `DashboardService` 遵循章程分层架构。

## 技术设计决策

### 1. SPA + 独立登录页架构

**决策**：登录页（`login.html`）独立于 SPA（`index.html`），采用两页面模式。

**理由**：
- 登录页不加载 Vue 应用（约 130KB），避免未认证用户下载不必要的 JS
- 认证状态检查简单：`login.html` 仅做 form submit → Basic Auth 验证 → 成功后 `window.location = '/app/'`
- SPA 内不再需要"登录组件"，路由守卫只需检查 sessionStorage 中是否有凭据

**流程**：
1. 用户访问 `/app/` → SPA 路由守卫检查凭据 → 无凭据 → `window.location = '/app/login.html'`
2. 用户在 `login.html` 输入凭据 → 发送 `GET /api/storage-engines`（或其他管理 API）验证 → 成功 → 存储凭据到 sessionStorage → 跳转 `/app/`
3. SPA 内 30 分钟无交互 → 清除凭据 → `window.location = '/app/login.html'`

### 2. Vue Router Hash 路由表

```javascript
const routes = [
  { path: '/',              component: Dashboard,      meta: { title: '系统概览' } },
  { path: '/engines',       component: StorageEngines,  meta: { title: '存储引擎' } },
  { path: '/sync-tasks',    component: SyncTasks,       meta: { title: '同步任务' } },
  { path: '/sync-tasks/:id',component: SyncTaskDetail,  meta: { title: '任务详情' } },
  { path: '/transcode-tasks',component: TranscodeTasks, meta: { title: '转码任务' } },
  { path: '/webhook-rules', component: WebhookRules,    meta: { title: 'Webhook 规则' } },
  { path: '/webhook-events',component: WebhookEvents,   meta: { title: 'Webhook 事件' } },
];
```

### 3. 导航布局

采用左侧固定侧边栏（240px）+ 右侧内容区的经典管理后台布局：

```
┌─────────────┬──────────────────────────────────┐
│  系统概览    │                                  │
│  存储引擎    │         <router-view>            │
│  同步任务    │         页面内容区域              │
│  转码任务    │                                  │
│  Webhook规则 │                                  │
│  Webhook事件 │                                  │
└─────────────┴──────────────────────────────────┘
```

### 4. HTTP 请求封装（api.js）

**关键设计**：
- 所有请求自动附加 `Authorization: Basic` 头
- 统一处理 401 响应 → 跳转登录页
- 统一解析 `ApiResult<T>` → 提取 `data` 字段或抛出错误
- 超时设置：默认 15 秒（转码/同步为长时间操作，仅 GET 状态不等待完成）
- 加载状态：请求前设置 `loading = true`，响应/错误后 `loading = false`

### 5. cron 表达式前端解析（utils.js）

```javascript
// ~100 行自实现
function parseCron(expr) {
  // 1. 格式校验：5 字段，每字段验证范围
  // 2. 计算下次执行时间：从 now 开始逐步递增，匹配所有字段
  // 3. 人类可读描述：映射常见模式
  //    - "*/30 * * * *"   → "每 30 分钟执行一次"
  //    - "0 */6 * * *"    → "每 6 小时执行一次"
  //    - "0 2 * * *"      → "每天凌晨 2 点执行"
  //    - "0 9 * * 1-5"    → "每周一至周五上午 9 点执行"
}
```

### 6. 进度轮询机制

同步任务执行进度通过定时轮询 `GET /api/sync-tasks/{id}` 获取。转码任务同理，轮询 `GET /api/transcode-tasks`。轮询间隔固定 5 秒，组件销毁前自动清除定时器。

### 7. 表单条件校验

同步任务表单中的"执行计划类型"驱动条件字段的显示和校验：

| scheduleType | 必填字段 | 隐藏字段 |
|-------------|---------|---------|
| INTERVAL | intervalSeconds | cronExpression |
| CRON | cronExpression | intervalSeconds |
| MANUAL | 无 | intervalSeconds, cronExpression |

前端通过 Vue `v-if`/`v-show` 切换显示，通过计算属性动态生成校验规则。

### 8. 仪表板统计端点（后端新增）

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

`DashboardService.getStats()` 通过 Repository 执行聚合查询：

- `activeSyncTasks`：`taskExecutionRepository.findByStatusAndTaskType(RUNNING, SYNC).size()`
- `pendingTranscodeTasks`：`transcodeTaskRepository.findByStatus(PENDING).size() + transcodeTaskRepository.findByStatus(TRANSCODING).size()`
- `todayProcessedFiles`：`taskExecutionRepository.findByCreatedAtBetween(todayStart, todayEnd)` 中已完成记录的 `successCount` 汇总
- `last24hSuccessRate`：同上，按 `successCount / (successCount + failureCount) * 100` 计算

## 数据模型概要

详见 [data-model.md](./data-model.md)。核心要点：

- **前端运行时模型**：AuthState（认证状态）、PageState（列表页通用状态）、各表单模型
- **前后端映射**：前端直接消费后端 DTO/VO 的 JSON 表示，无需本地模型转换
- **不引入新数据库实体**：仪表板统计为纯查询聚合，不持久化

## 复杂性追踪

> 无违规项，无需记录。

## 与其他规格的关系

| 规格 | 关系 | 说明 |
|------|------|------|
| 001-alist-media-sync | **UI 层覆盖** | 004 为 001 的全部管理 API 提供 Web 前端界面 |
| 002-transcode-temp-suffix-config | **UI 层覆盖** | 004 为 002 的重试上传、残留文件清理提供 Web 操作入口 |
| 003-docker-deploy | **基础设施依赖** | 004 的前端静态资源由 003 的 Docker 镜像一并提供，无需修改 Dockerfile |
