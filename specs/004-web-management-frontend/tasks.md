# 任务：Web 管理前端界面

**输入**：来自 `specs/004-web-management-frontend/` 的设计文档

**前提条件**：plan.md（必需）、spec.md（用户故事必需）、research.md、data-model.md、contracts/

**测试**：前端手工验收（通过 quickstart.md）；后端 DashboardController 新增 `@WebMvcTest`

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（例如，US1、US2、US3）
- 在描述中包含确切的文件路径

---

## 阶段 1：设置（共享基础设施）

**目的**：项目初始化和目录结构创建，下载第三方库，配置后端路由映射

- [ ] T001 在 `src/main/resources/static/app/` 下创建目录结构（`css/`、`js/pages/`、`vendor/`）
- [ ] T002 [P] 下载 Vue 3 生产版本到 `src/main/resources/static/app/vendor/vue.global.prod.js`（从 `https://unpkg.com/vue@3/dist/vue.global.prod.js`）
- [ ] T003 [P] 下载 Vue Router 4 生产版本到 `src/main/resources/static/app/vendor/vue-router.global.prod.js`（从 `https://unpkg.com/vue-router@4/dist/vue-router.global.prod.js`）
- [ ] T004 修改 `src/main/java/top/lldwb/alistmediasync/config/WebMvcConfig.java`，添加 `/app/**` 路径的静态资源映射和根路径重定向规则

**检查点**：目录结构就绪，后端可正确提供静态资源

---

## 阶段 2：基础（阻塞性前置条件 — 包含 US1 登录认证）

**目的**：在任何管理页面可以开发之前必须完成的核心基础设施，同时覆盖 US1 的登录认证功能

**⚠️ 关键**：在此阶段完成之前，不能开始任何管理页面（US2-US6）的工作

**US1 目标**：用户可通过登录页面输入凭据认证，成功后进入管理主页，会话超时自动重新登录

**US1 独立测试**：浏览器访问 `/app/` → 重定向到登录页 → 输入正确凭据 → 进入仪表板占位页 → 30 分钟无交互 → 跳回登录页

### 用户故事 1 的实现

- [ ] T005 在 `src/main/resources/static/app/js/api.js` 中实现 HTTP 请求封装（fetch 封装：Basic Auth 自动注入、401 拦截跳转、`ApiResult<T>` 统一解析、15 秒超时、加载状态管理）
- [ ] T006 [P] 在 `src/main/resources/static/app/js/auth.js` 中实现认证管理模块（登录/登出/凭据 sessionStorage 存储/30 分钟交互监听超时/401 响应处理/redirectPath 恢复）
- [ ] T007 [P] 在 `src/main/resources/static/app/js/utils.js` 中实现工具函数（cron 表达式解析与下次执行时间计算、日期时间格式化、URL 格式校验、文件大小格式化、分页计算器）
- [ ] T008 [US1] 在 `src/main/resources/static/app/login.html` 中创建独立登录页（纯 HTML + 内联 JS：登录表单、凭据预校验、Basic Auth 验证请求、成功跳转 `/app/`、错误提示、凭据 sessionStorage 存储）
- [ ] T009 [US1] 在 `src/main/resources/static/app/css/app.css` 中编写全局样式（CSS 变量主题色、布局系统、侧边栏样式、表格样式、表单样式、按钮样式、通知/提示样式、加载动画、空状态样式、确认对话框样式）
- [ ] T010 在 `src/main/resources/static/app/js/router.js` 中配置 Vue Router Hash 路由表（7 条路由 + 全局导航守卫：无凭据时重定向登录页）
- [ ] T011 在 `src/main/resources/static/app/js/app.js` 中初始化 Vue 3 应用（创建应用实例、注册 Router、挂载全局组件、错误处理钩子）
- [ ] T012 在 `src/main/resources/static/app/index.html` 中创建 SPA 入口页面（CDN 加载 Vue + Vue Router 降级到本地 vendor、侧边栏导航结构、`<router-view>` 挂载点、用户信息显示区域）

**检查点**：用户可登录认证，导航框架可用，所有管理页面路由就绪但显示占位内容

---

## 阶段 3：用户故事 2 — 存储引擎管理（优先级：P1）

**目标**：用户可在 Web 界面中查看、添加、编辑、删除、测试连接 AList 存储引擎

**独立测试**：登录后进入"存储引擎"页面 → 点击"添加引擎" → 填写表单 → 保存 → 引擎出现在列表中 → 点击"测试连接" → 显示连接结果 → 编辑引擎信息 → 删除未被引用的引擎 → 尝试删除被引用引擎时看到冲突提示

### 用户故事 2 的实现

- [ ] T013 [US2] 在 `src/main/resources/static/app/js/pages/engines.js` 中实现存储引擎列表页组件（获取所有引擎、表格渲染、分页、空状态提示）
- [ ] T014 [US2] 在 `src/main/resources/static/app/js/pages/engines.js` 中添加创建/编辑引擎表单（模态框或内联表单：名称、API 地址、用户名、密码/Token 输入、前端校验（必填 + URL 格式）、保存后刷新列表）
- [ ] T015 [US2] 在 `src/main/resources/static/app/js/pages/engines.js` 中添加引擎删除功能（确认对话框显示引擎名称、删除后刷新列表、被引用时显示冲突提示并列出引用任务名）
- [ ] T016 [US2] 在 `src/main/resources/static/app/js/pages/engines.js` 中添加连接测试功能（测试按钮、加载状态、成功/失败颜色反馈）

**检查点**：存储引擎 CRUD 全流程可在 Web 界面中完成

---

## 阶段 4：用户故事 3 — 同步任务全生命周期管理（优先级：P1）

**目标**：用户可创建/编辑/启用/禁用/手动触发同步任务，查看实时进度和执行历史

**独立测试**：登录后进入"同步任务"页面 → 创建任务（选择源/目标引擎、填写路径、选择同步模式和执行计划）→ 保存 → 手动触发 → 观察实时进度变化 → 查看执行历史 → 展开失败记录查看详情 → 禁用任务调度

### 用户故事 3 的实现

- [ ] T017 [US3] 在 `src/main/resources/static/app/js/pages/sync-tasks.js` 中实现同步任务列表页组件（获取所有任务、表格展示：名称/源引擎/目标引擎/同步模式/执行计划/启用状态/最后执行时间/最近执行状态、分页、空状态提示）
- [ ] T018 [US3] 在 `src/main/resources/static/app/js/pages/sync-tasks.js` 中添加创建/编辑同步任务表单（任务名称、源引擎下拉、目标引擎下拉、源路径、目标路径、同步模式下拉、执行计划类型单选（间隔/cron/手动）、条件字段切换（间隔秒数/cron 表达式）、文件排除规则多行文本、冲突策略下拉、"同步后转 MP3"开关、前端校验（必填/路径格式/cron 格式））
- [ ] T019 [US3] 在 `src/main/resources/static/app/js/pages/sync-tasks.js` 中添加 cron 表达式实时预览（用户输入时调用 utils.js 的 parseCron()，显示人类可读描述和下次执行时间）
- [ ] T020 [US3] 在 `src/main/resources/static/app/js/pages/sync-tasks.js` 中添加任务操作按钮（手动触发执行（检查无运行中实例）、启用/禁用调度开关、删除确认对话框）
- [ ] T021 [US3] 在 `src/main/resources/static/app/js/pages/sync-tasks.js` 中添加实时进度显示（5 秒轮询 `GET /api/sync-tasks/{id}`、显示已完成/总文件数、当前文件、速度、预估剩余时间、完成后停止轮询）
- [ ] T022 [US3] 在 `src/main/resources/static/app/js/pages/sync-task-detail.js` 中实现同步任务详情页组件（基本信息展示、Tab 切换：基本信息 / 执行历史）
- [ ] T023 [US3] 在 `src/main/resources/static/app/js/pages/sync-task-detail.js` 中添加执行历史 Tab（分页表格：开始时间/结束时间/状态/总文件数/成功数/失败数、失败条目可展开显示失败文件列表（文件名、失败原因））

**检查点**：同步任务全生命周期（创建→触发→监控→历史查看）可在 Web 界面中完成

---

## 阶段 5：用户故事 4 — 转码任务管理与监控（优先级：P2）

**目标**：用户可创建独立转码任务、查看进度、重试上传失败文件、清理残留临时文件

**独立测试**：登录后进入"转码任务"页面 → 创建转码任务（选择引擎、路径、格式、码率）→ 观察进度 → 对上传失败任务点击"重试上传"→ 点击"清理残留文件"→ 查看清理结果

### 用户故事 4 的实现

- [ ] T024 [US4] 在 `src/main/resources/static/app/js/pages/transcode-tasks.js` 中实现转码任务列表页组件（获取所有任务、表格展示：ID/源路径/目标路径/目标格式/进度百分比/状态/创建时间、进度条渲染、空状态提示）
- [ ] T025 [US4] 在 `src/main/resources/static/app/js/pages/transcode-tasks.js` 中添加创建转码任务表单（源引擎下拉、目标引擎下拉、源文件路径、目标路径、目标格式下拉（MP3/MP4/FLV）、码率输入（默认 128）、前端校验（必填/码率为正整数））
- [ ] T026 [US4] 在 `src/main/resources/static/app/js/pages/transcode-tasks.js` 中添加实时进度显示（5 秒轮询 `GET /api/transcode-tasks`、显示进度百分比、状态文字（等待中/转码中/上传中）、已耗时、预估剩余时间、进度条动画）
- [ ] T027 [US4] 在 `src/main/resources/static/app/js/pages/transcode-tasks.js` 中添加操作按钮（上传失败时显示"重试上传"并处理结果反馈、"清理残留临时文件"按钮并显示清理数量）

**检查点**：转码任务全流程可在 Web 界面中管理

---

## 阶段 6：用户故事 5 — Webhook 规则配置与事件查看（优先级：P2）

**目标**：用户可创建/编辑/启用/禁用 Webhook 处理规则，查看接收到的 Webhook 事件记录

**独立测试**：登录后进入"Webhook 规则"页面 → 创建规则 → 保存 → 启用/禁用 → 进入"Webhook 事件"页面 → 查看事件列表或空状态引导

### 用户故事 5 的实现

- [ ] T028 [US5] 在 `src/main/resources/static/app/js/pages/webhook-rules.js` 中实现 Webhook 规则列表页组件（获取所有规则、表格展示：规则名称/触发事件类型/房间号过滤/后续动作/启用状态、分页、空状态提示）
- [ ] T029 [US5] 在 `src/main/resources/static/app/js/pages/webhook-rules.js` 中添加创建/编辑规则表单（规则名称、触发事件类型下拉（FileClosed/SessionEnded/全部）、房间号过滤可选输入、后续动作多选（同步至 AList/转 MP3）、目标引擎条件显示（动作含同步时）、目标路径条件显示、前端条件校验）
- [ ] T030 [US5] 在 `src/main/resources/static/app/js/pages/webhook-rules.js` 中添加启用/禁用开关和删除确认操作
- [ ] T031 [US5] 在 `src/main/resources/static/app/js/pages/webhook-events.js` 中实现 Webhook 事件列表页组件（获取所有事件 `GET /api/webhooks/events`、时间倒序表格展示：事件类型/EventId/发生时间/关联规则/处理状态、分页）
- [ ] T032 [US5] 在 `src/main/resources/static/app/js/pages/webhook-events.js` 中添加空状态提示和录播姬 Webhook 配置引导（URL 格式说明、配置步骤）

**检查点**：Webhook 规则和事件查看可在 Web 界面中完成

---

## 阶段 7：用户故事 6 — 系统概览仪表板（优先级：P3）

**目标**：用户登录后首先看到系统概览，汇总关键运行指标，卡片可点击跳转

**独立测试**：登录后进入仪表板 → 查看统计卡片数据 → 点击卡片跳转到对应模块 → 无活跃任务时显示"系统当前空闲"

### 用户故事 6 的实现

- [ ] T033 [US6] 在 `src/main/java/top/lldwb/alistmediasync/service/DashboardService.java` 中实现聚合统计查询服务（使用现有 Repository 查询：活跃同步任务数、等待转码任务数、今日处理文件总数、24h 成功率、引擎总数、规则总数）
- [ ] T034 [US6] 在 `src/main/java/top/lldwb/alistmediasync/controller/DashboardController.java` 中实现仪表板统计 API（`GET /api/dashboard/stats`，返回 `ApiResult<DashboardStatsVO>`）
- [ ] T035 [US6] 在 `src/main/java/top/lldwb/alistmediasync/dto/DashboardStatsVO.java` 中创建仪表板统计响应 DTO
- [ ] T036 [US6] 在 `src/main/java/top/lldwb/alistmediasync/service/DashboardService.java` 中添加 `totalEngines` 和 `totalWebhookRules` 统计字段
- [ ] T037 [US6] 在 `src/main/resources/static/app/js/pages/dashboard.js` 中实现仪表板页面组件（调用 `GET /api/dashboard/stats`、渲染 4 张统计卡片：活跃同步任务/等待转码任务/今日处理文件/24h 成功率、卡片点击跳转、无活跃任务时显示"系统当前空闲"）

**检查点**：仪表板可展示系统运行概览，所有卡片可点击跳转

---

## 阶段 8：后端 Webhook 事件查询端点（用户故事 5 依赖）

**目的**：US5 的 Webhook 事件页面需要后端提供事件查询端点（现有后端可能未提供独立的分页查询接口）

- [ ] T038 在 `src/main/java/top/lldwb/alistmediasync/controller/WebhookEventController.java` 中新增 Webhook 事件分页查询端点（`GET /api/webhooks/events?page=1&size=20`，时间倒序）

> **注**：如现有 `WebhookRuleController` 或 `WebhookService` 已包含事件查询方法，则跳过此任务。

---

## 阶段 9：润色与跨领域关注点

**目的**：影响多个用户故事的改进和最终验证

- [ ] T039 [P] 在 `src/main/resources/static/app/css/app.css` 中添加响应式断点样式（平板 768px+ 侧边栏折叠为顶部导航）
- [ ] T040 [P] 在 `src/test/java/top/lldwb/alistmediasync/controller/DashboardControllerTest.java` 中编写仪表板 API 测试（`@WebMvcTest`，Mock DashboardService，验证响应格式）
- [ ] T041 在所有页面组件中添加网络中断提示（fetch 失败时显示"连接失败，正在重试"横幅，连接恢复后自动重试）
- [ ] T042 在所有页面组件中完善空数据状态（覆盖所有可能的空列表场景，确保有引导性操作按钮）
- [ ] T043 [P] 在 `src/main/resources/static/app/js/pages/engines.js` 中添加凭据字段脱敏显示（密码/Token 显示为 `***`）
- [ ] T044 全局前端错误处理审查（确保所有 catch 块有用户友好的错误提示，避免 `console.log` 裸错误）
- [ ] T045 按 quickstart.md 执行全部验证场景手动测试（7 个场景 + 边界情况），记录发现的缺陷并修复

**检查点**：所有功能页面就绪且通过 quickstart.md 验证

---

## 依赖与执行顺序

### 阶段依赖

- **设置（阶段 1）**：无依赖 — 可立即开始
- **基础 + US1（阶段 2）**：依赖阶段 1 完成 — 阻塞所有管理页面（US2-US6）
- **US2 存储引擎（阶段 3）**：依赖阶段 2 完成 — 可独立进行
- **US3 同步任务（阶段 4）**：依赖阶段 2 完成 — 需要 US2 的引擎数据创建任务但不阻塞实现
- **US4 转码任务（阶段 5）**：依赖阶段 2 完成 — 需要 US2 的引擎数据但不阻塞实现
- **US5 Webhook（阶段 6）**：依赖阶段 2 + 阶段 8 完成 — 需要后端事件查询端点
- **US6 仪表板（阶段 7）**：依赖阶段 2 + 后端 T033-T036 完成
- **后端事件端点（阶段 8）**：依赖阶段 1 完成 — 可并行于阶段 3-7
- **润色（阶段 9）**：依赖所有期望的用户故事完成

### 用户故事依赖

- **US1（P1）**：阶段 2 实现 — 所有其他故事的前置条件
- **US2（P1）**：可在阶段 2 后开始 — 不依赖其他故事（引擎独立管理）
- **US3（P1）**：可在阶段 2 后开始 — 表单中需要引擎下拉数据（US2 完成提供）但不阻塞代码编写
- **US4（P2）**：可在阶段 2 后开始 — 表单中需要引擎下拉数据但不阻塞代码编写
- **US5（P2）**：可在阶段 2 后开始 — 需要阶段 8 的后端事件查询端点
- **US6（P3）**：可在阶段 2 后开始 — 需要阶段 7 的后端 DashboardController 完成

### 每个用户故事内部

- 页面列表组件先于表单组件
- 表单组件先于操作按钮（删除/启用等）
- 核心功能先于进度更新（对于 US3、US4）

### 并行机会

- 阶段 1 内：T002、T003 可并行（独立下载文件）
- 阶段 2 内：T005、T006、T007 可并行（三个独立 JS 模块，不同文件）
- 阶段 3-7（前后端）：阶段 2 完成后可并行
  - 前端开发：US2 → US3 → US4 → US5（可依次或不同开发者并行）
  - 后端开发：阶段 7（US6 后端 T033-T036）+ 阶段 8（T038）可与前端阶段 3-6 并行
- US2/US3/US4/US5 的前端页面组件均标记为独立文件，不同故事间无代码冲突
- 阶段 9 内：T039、T040、T043 可并行（不同文件）

---

## 并行示例：阶段 2 基础模块

```bash
# 三个独立 JS 模块并行开发（不同文件，无依赖）：
T005："在 src/main/resources/static/app/js/api.js 中实现 HTTP 请求封装"
T006："在 src/main/resources/static/app/js/auth.js 中实现认证管理模块"
T007："在 src/main/resources/static/app/js/utils.js 中实现工具函数"

# 完成后，T008-T012 依次进行（有依赖顺序）
```

## 并行示例：后端服务（阶段 7 + 阶段 8）

```bash
# 与前端页面组件（阶段 3-6）并行进行
T033："在 DashboardService.java 中实现聚合统计查询服务"
T038："在 WebhookEventController.java 中新增 Webhook 事件分页查询端点"
```

---

## 实现策略

### MVP 优先（US1 + US2）

1. 完成阶段 1：设置（目录结构 + 依赖下载 + WebMvcConfig）
2. 完成阶段 2：基础 + US1（api.js、auth.js、router.js、app.js、login.html、index.html、app.css）
3. 完成阶段 3：US2 存储引擎管理
4. **停止并验证**：用户可登录 → 管理存储引擎（CRUD + 连接测试）
5. 部署/演示 — 已可用！

### 增量交付

1. 设置 + 基础 + US1 → 登录认证可用
2. 添加 US2 存储引擎 → 独立测试 → 演示
3. 添加 US3 同步任务 → 独立测试 → 演示（核心功能就绪！）
4. 添加 US4 转码任务 → 独立测试 → 演示
5. 添加 US5 Webhook → 独立测试 → 演示
6. 添加 US6 仪表板 → 独立测试 → 演示
7. 润色 → 全面验证

### 推荐 MVP 范围

**阶段 1 + 阶段 2 + 阶段 3（US2）** = 用户可以登录并管理存储引擎。这是所有后续功能的基础，也是最小的可用交付。

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应能独立完成和测试
- 前端无构建步骤：所有 JS 直接编辑 `src/main/resources/static/app/js/` 下的文件
- 后端需要重启 `mvn spring-boot:run` 才能加载新的静态资源（开发期间可利用 Spring DevTools 自动重启）
- 每个任务或逻辑组后提交
- 在任何检查点停止以独立验证故事
- 避免：模糊的任务、同文件冲突、破坏独立性的跨故事依赖
