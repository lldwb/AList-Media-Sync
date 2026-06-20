# 任务：存储引擎重构与体验优化

**输入**：来自 `/specs/006-storage-engine-refactor/` 的设计文档

**前提条件**：plan.md（必需）、spec.md（用户故事必需）、research.md、data-model.md、contracts/

**测试**：章程原则 V 要求测试不可省略，以下任务包含测试任务。

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（例如，US1、US2、US3）
- 在描述中包含确切的文件路径

## 路径约定

- **后端**：`src/main/java/top/lldwb/alistmediasync/`
- **前端**：`src/main/frontend/src/`
- **测试**：`src/test/java/top/lldwb/alistmediasync/`

---

## 阶段 1：设置（共享基础设施）

**目的**：实体变更、枚举扩展、DTO 更新等所有用户故事共享的基础结构

- [ ] T001 在 src/main/java/top/lldwb/alistmediasync/entity/StorageEngine.java 中移除 username 字段，新增 engineType（EngineType 枚举：ALIST/LOCAL）和 localPath 字段，baseUrl 和 encryptedToken 改为 nullable
- [ ] T002 [P] 在 src/main/java/top/lldwb/alistmediasync/entity/StorageEngine.java 中新增 EngineType 枚举（ALIST、LOCAL）和 EngineStatus 枚举（ONLINE、OFFLINE、ERROR）
- [ ] T003 [P] 在 src/main/java/top/lldwb/alistmediasync/entity/TranscodeTask.java 中扩展 TranscodeStatus 为 8 状态模型（PENDING/DOWNLOADING/DOWNLOAD_FAILED/TRANSCODING/TRANSCODE_FAILED/UPLOADING/UPLOAD_FAILED/COMPLETED），移除 SCANNING，bitrate 改为 nullable，新增 tempSourcePath 字段
- [ ] T004 [P] 在 src/main/java/top/lldwb/alistmediasync/entity/WebhookRule.java 中新增 recordingEngine（ManyToOne 关联 StorageEngine）和 recordingPath 字段，targetPath 重命名为 targetFilePath
- [ ] T005 [P] 在 src/main/java/top/lldwb/alistmediasync/dto/storage/ 中更新 StorageEngineCreateDTO、StorageEngineUpdateDTO、StorageEngineVO，移除 username，新增 engineType 和 localPath 字段
- [ ] T006 [P] 在 src/main/java/top/lldwb/alistmediasync/dto/transcode/ 中更新 TranscodeTaskVO，bitrate 改为可选，新增 canRetry 字段，status 类型适配 8 状态枚举
- [ ] T007 [P] 在 src/main/java/top/lldwb/alistmediasync/dto/webhook/ 中更新 WebhookRuleCreateDTO 和 WebhookRuleVO，新增 recordingEngineId/recordingEngineName/recordingPath，targetPath 重命名为 targetFilePath
- [ ] T008 [P] 在 src/main/java/top/lldwb/alistmediasync/dto/sync/ 中新增 DirectoryEntryVO record（name、path、hasChildren）和 FileEntry record（name、path、isDirectory、size、modifiedTime）
- [ ] T009 [P] 在 src/main/java/top/lldwb/alistmediasync/config/AppProperties.java 中新增 transcode.default-bitrate 配置项（默认 128 kbps）和 server-address 配置项
- [ ] T010 在 src/main/java/top/lldwb/alistmediasync/repository/ 中确保 StorageEngineRepository、TranscodeTaskRepository、WebhookRuleRepository 支持新增字段的查询方法

---

## 阶段 2：基础（阻塞性前置条件）

**目的**：策略模式核心接口与实现，所有用户故事依赖的存储引擎抽象层

**⚠️ 关键**：在此阶段完成之前，不能开始任何用户故事的工作

- [ ] T011 在 src/main/java/top/lldwb/alistmediasync/service/engine/StorageEngineStrategy.java 中定义策略接口（type、listFiles、getFileInfo、downloadFile、uploadFile、createDirectory、deleteFile、listDirectories、testConnection）
- [ ] T012 在 src/main/java/top/lldwb/alistmediasync/service/engine/AListStorageStrategy.java 中实现 AList 策略，内部使用 RestClient 调用 AList API，将现有 AListClient 逻辑迁移至此
- [ ] T013 在 src/main/java/top/lldwb/alistmediasync/service/engine/LocalStorageStrategy.java 中实现本地路径策略，使用 java.nio.file.Files/Path 操作本地文件系统
- [ ] T014 在 src/main/java/top/lldwb/alistmediasync/service/StorageEngineService.java 中重构为策略分发模式，构造器注入 List<StorageEngineStrategy> 构建 Map，新增 resolve(StorageEngine) 方法按 engineType 选择策略
- [ ] T015 在 src/main/java/top/lldwb/alistmediasync/controller/StorageEngineController.java 中新增 GET /api/storage-engines/{id}/directories 端点，调用 StorageEngineService 策略分发获取目录列表
- [ ] T016 [P] 在 src/test/java/top/lldwb/alistmediasync/service/engine/ 中编写 AListStorageStrategy 单元测试（Mock RestClient）
- [ ] T017 [P] 在 src/test/java/top/lldwb/alistmediasync/service/engine/ 中编写 LocalStorageStrategy 单元测试（Mock/临时目录）
- [ ] T018 [P] 在 src/test/java/top/lldwb/alistmediasync/service/ 中编写 StorageEngineService 策略分发单元测试

**检查点**：基础就绪 — 策略模式可工作，目录浏览端点可用，现在可以并行开始用户故事实现

---

## 阶段 3：用户故事 1 — 存储引擎策略模式与本地路径支持（优先级：P1）🎯 MVP

**目标**：支持 AList 和本地路径两种存储引擎类型，策略模式实现动态切换，移除用户名字段

**独立测试**：创建不同类型存储引擎（AList/本地路径），分别执行连接测试，验证两种引擎均可独立工作

### 用户故事 1 的测试

- [ ] T019 [P] [US1] 在 src/test/java/top/lldwb/alistmediasync/controller/ 中编写 StorageEngineController @WebMvcTest 测试（创建 AList 引擎、创建本地路径引擎、编辑时类型禁用校验）
- [ ] T020 [P] [US1] 在 src/test/java/top/lldwb/alistmediasync/service/ 中编写 StorageEngineService CRUD 单元测试（engineType 校验、连接测试分发、类型不可更改校验）

### 用户故事 1 的实现

- [ ] T021 [US1] 在 src/main/java/top/lldwb/alistmediasync/service/StorageEngineService.java 中实现创建存储引擎逻辑：按 engineType 校验必填字段（ALIST 需 baseUrl+token，LOCAL 需 localPath），调用策略 testConnection 验证连接
- [ ] T022 [US1] 在 src/main/java/top/lldwb/alistmediasync/service/StorageEngineService.java 中实现更新存储引擎逻辑：engineType 不可更改校验，其他字段可选更新
- [ ] T023 [US1] 在 src/main/java/top/lldwb/alistmediasync/controller/StorageEngineController.java 中更新 CRUD 端点适配新 DTO（移除 username，新增 engineType/localPath），更新 POST /{id}/test 端点按 engineType 分发测试逻辑
- [ ] T024 [US1] 在 src/main/java/top/lldwb/alistmediasync/service/SyncService.java 中适配策略模式，将直接调用 AListClient 改为通过 StorageEngineService.resolve() 获取策略执行同步操作
- [ ] T025 [US1] 在 src/main/java/top/lldwb/alistmediasync/service/SyncTaskManageService.java 中适配策略模式，同步任务创建和执行使用 StorageEngineService 策略分发
- [ ] T026 [US1] 在 src/main/java/top/lldwb/alistmediasync/client/AListClient.java 中重构为 AListStorageStrategy 的内部依赖或标记为 @Deprecated（策略模式已接管其职责）
- [ ] T027 [US1] 在 src/main/frontend/src/pages/ 中更新存储引擎管理页面：创建表单新增 engineType 选择器（ALIST/LOCAL），按类型动态显示字段（ALIST 显示地址+Token，LOCAL 显示路径），移除用户名字段，编辑模式下类型字段禁用
- [ ] T028 [US1] 在 src/main/frontend/src/types/ 中更新 StorageEngine 相关 TypeScript 类型定义，新增 engineType、localPath，移除 username
- [ ] T029 [US1] 在 src/main/frontend/src/api/ 中更新存储引擎 API 客户端，适配新 DTO 结构

**检查点**：此时，用户故事 1 应完全功能可用 — 可创建 AList 和本地路径引擎，连接测试通过，类型不可更改

---

## 阶段 4：用户故事 2 — 转码流程三步优化与任务过滤（优先级：P1）

**目标**：转码采用"下载→转码→上传"三步模式，8 状态模型提升可恢复性，任务列表仅显示文件条目

**独立测试**：创建转码任务观察三步流程执行和状态变化，验证失败重试从正确步骤继续，列表无目录条目

### 用户故事 2 的测试

- [ ] T030 [P] [US2] 在 src/test/java/top/lldwb/alistmediasync/service/ 中编写 TranscodeService 状态转换单元测试（合法转换通过、非法转换抛异常、重试逻辑验证）
- [ ] T031 [P] [US2] 在 src/test/java/top/lldwb/alistmediasync/service/ 中编写 TranscodeFileProcessor 三步流程单元测试（下载→转码→上传、各步骤失败场景、临时文件生命周期、下载失败时部分文件清理）

### 用户故事 2 的实现

- [ ] T032 [US2] 在 src/main/java/top/lldwb/alistmediasync/service/TranscodeService.java 中实现 8 状态转换表（Set<Map.Entry<TranscodeStatus, TranscodeStatus>>），新增状态转换校验方法 validateTransition，非法转换抛 IllegalStateException
- [ ] T033 [US2] 在 src/main/java/top/lldwb/alistmediasync/service/TranscodeFileProcessor.java 中将 doProcess 拆分为三个独立步骤方法：downloadStep（下载源文件到临时目录）、transcodeStep（从临时源文件转码）、uploadStep（上传转码输出到目标引擎）
- [ ] T034 [US2] 在 src/main/java/top/lldwb/alistmediasync/service/TranscodeService.java 中实现三步流程编排：PENDING→DOWNLOADING→TRANSCODING→UPLOADING→COMPLETED，每步失败进入对应失败状态，成功后清理临时文件
- [ ] T035 [US2] 在 src/main/java/top/lldwb/alistmediasync/service/TranscodeService.java 中实现重试逻辑：DOWNLOAD_FAILED→DOWNLOADING（删除部分下载文件）、TRANSCODE_FAILED→TRANSCODING（保留源临时文件跳过下载）、UPLOAD_FAILED→UPLOADING（保留源+输出文件跳过前两步）
- [ ] T036 [US2] 在 src/main/java/top/lldwb/alistmediasync/service/TranscodeFileProcessor.java 中优化 JAVE2 编解码参数：AudioAttributes/VideoAttributes 的 codec 设为 null，让 FFmpeg 自动选择编解码器（避免硬编码编解码器导致的兼容性问题）；bitrate 为 null 时使用 AppProperties 配置的默认码率（128 kbps）
- [ ] T037 [US2] 在 src/main/java/top/lldwb/alistmediasync/service/CleanupService.java 中扩展孤立临时文件清理：清理超过 24 小时的孤立临时文件（源文件和转码输出文件）
- [ ] T038 [US2] 在 src/main/java/top/lldwb/alistmediasync/controller/TranscodeTaskController.java 中更新 POST /{id}/retry 端点支持从任意失败状态重试，GET / 列表查询过滤目录条目仅返回文件类型，响应新增 canRetry 字段
- [ ] T039 [US2] 在 src/main/java/top/lldwb/alistmediasync/service/TranscodeService.java 中实现转码候选列表过滤：扫描结果仅返回文件类型条目，过滤目录项
- [ ] T040 [US2] 在 src/main/frontend/src/pages/ 中更新转码任务页面：状态显示适配 8 状态模型（新增下载中/下载失败/转码失败/上传失败状态标签），重试按钮仅对失败状态显示，移除目录条目展示
- [ ] T041 [US2] 在 src/main/frontend/src/types/ 中更新 TranscodeTask 相关 TypeScript 类型定义，适配 8 状态枚举和 canRetry 字段

**检查点**：此时，用户故事 1 和 2 应都能独立工作 — 转码三步流程可执行，失败可重试，列表无目录

---

## 阶段 5：用户故事 3 — Webhook 规则路径显示优化与地址复制（优先级：P2）

**目标**：Webhook 规则路径显示为"存储引擎→路径"组合形式，新增录播存储引擎选择，一键复制 Webhook 地址

**独立测试**：创建/编辑 Webhook 规则观察路径显示格式，点击复制按钮验证剪贴板内容

### 用户故事 3 的测试

- [ ] T042 [P] [US3] 在 src/test/java/top/lldwb/alistmediasync/controller/ 中编写 WebhookRuleController @WebMvcTest 测试（recordingEngine 关联、targetFilePath 字段、Webhook 地址端点）
- [ ] T043 [P] [US3] 在 src/test/java/top/lldwb/alistmediasync/service/ 中编写 WebhookRuleService 单元测试（recordingEngine 校验、action 与 recordingEngine 必填关系）

### 用户故事 3 的实现

- [ ] T044 [US3] 在 src/main/java/top/lldwb/alistmediasync/service/WebhookRuleService.java 中更新创建/更新逻辑：action 为 TRANSCODE_ONLY 或 BOTH 时 recordingEngineId 和 recordingPath 必填校验，targetPath 重命名为 targetFilePath
- [ ] T045 [US3] 在 src/main/java/top/lldwb/alistmediasync/controller/WebhookRuleController.java 中更新 CRUD 端点适配新 DTO（recordingEngineId、recordingPath、targetFilePath）
- [ ] T046 [US3] 在 src/main/java/top/lldwb/alistmediasync/controller/WebhookController.java 中新增 GET /api/webhooks/address 端点，返回录播姬 Webhook V2 完整 URL（优先使用 app.server-address 配置，未配置时使用请求 origin）
- [ ] T047 [US3] 在 src/main/java/top/lldwb/alistmediasync/service/WebhookService.java 中适配 recordingEngine 关联，Webhook 触发转码时使用 recordingEngine 作为源引擎
- [ ] T048 [US3] 在 src/main/frontend/src/pages/ 中更新 Webhook 规则页面：源路径显示为"录播存储引擎（下拉选择器）→ 录播文件路径"组合，目标路径显示为"目标存储引擎 → 目标文件路径"组合，移除独立目标目录路径字段
- [ ] T049 [US3] 在 src/main/frontend/src/pages/ 中新增"复制 Webhook 地址"功能：只读输入框显示 URL + 内嵌复制按钮，navigator.clipboard.writeText() + execCommand('copy') 降级，按钮状态切换提示（2 秒恢复）
- [ ] T050 [US3] 在 src/main/frontend/src/types/ 中更新 WebhookRule 相关 TypeScript 类型定义，新增 recordingEngineId/recordingEngineName/recordingPath，targetPath 重命名为 targetFilePath

**检查点**：此时，用户故事 1、2、3 应都能独立工作 — Webhook 路径显示清晰，地址复制可用

---

## 阶段 6：用户故事 4 — 文件路径树状浏览组件（优先级：P2）

**目标**：树状目录浏览组件，实时加载子目录，选择路径自动回填输入框

**独立测试**：在存储引擎配置或同步任务配置中打开路径选择器，浏览目录结构并选择路径

### 用户故事 4 的测试

- [ ] T051a [P] [US4] 在 src/test/java/top/lldwb/alistmediasync/service/engine/ 中编写 LocalStorageStrategy 边界场景单元测试（目录不存在返回错误、权限不足返回明确错误信息）

### 用户故事 4 的实现

- [ ] T051 [P] [US4] 在 src/main/frontend/src/api/ 中新增目录浏览 API 客户端方法：getDirectories(engineId, path?) 调用 GET /api/storage-engines/{id}/directories
- [ ] T052 [P] [US4] 在 src/main/frontend/src/types/ 中新增 DirectoryEntryVO TypeScript 类型定义（name、path、hasChildren）
- [ ] T053 [US4] 在 src/main/frontend/src/components/ui/DirectoryTreeSelector.tsx 中实现树状目录浏览组件：useReducer + 扁平化 Map 管理树状态，点击目录节点实时加载子目录（仅目录不显示文件），选中路径自动回填，面包屑显示当前层级
- [ ] T054 [US4] 在 src/main/frontend/src/pages/ 中将 DirectoryTreeSelector 组件集成到存储引擎配置页面（localPath 字段旁新增浏览按钮）
- [ ] T055 [US4] 在 src/main/frontend/src/pages/ 中将 DirectoryTreeSelector 组件集成到同步任务配置页面（源路径和目标路径字段旁新增浏览按钮）
- [ ] T056 [US4] 在 src/main/frontend/src/pages/ 中将 DirectoryTreeSelector 组件集成到 Webhook 规则配置页面（recordingPath 和 targetFilePath 字段旁新增浏览按钮）

**检查点**：此时，用户故事 4 应完全功能可用 — 树状目录浏览可正常加载和选择路径

---

## 阶段 7：用户故事 5 — Cron 表达式图形化配置（优先级：P3）

**目标**：图形化下拉选择器配置 Cron 表达式，预设快捷模式，中文描述和下次执行时间预览

**独立测试**：创建/编辑同步任务使用图形化选择器配置 Cron，验证生成表达式与预期一致

### 用户故事 5 的测试

- [ ] T057a [P] [US5] 在 src/test/java/ 或 src/main/frontend/src/ 中编写 cron.ts 工具函数单元测试（buildCronExpression 各字段组合、无效组合处理、预设快捷模式生成）

### 用户故事 5 的实现

- [ ] T057 [P] [US5] 在 src/main/frontend/src/utils/cron.ts 中新增 buildCronExpression(fields) 反向生成函数，将五字段选择值组合为 Cron 表达式
- [ ] T058 [US5] 在 src/main/frontend/src/components/ui/CronBuilder.tsx 中实现 Cron 图形化配置组件：五字段独立选择器（分钟/小时/日/月/周），每字段支持每(*)/指定值/范围/步进，预设快捷模式按钮组（每小时/每天凌晨/每天8点/每周一/每月1号/每6小时），与手动输入模式双向同步
- [ ] T059 [US5] 在 src/main/frontend/src/components/ui/CronBuilder.tsx 中实现预览功能：复用已有 parseCron/buildDescription/calcNextExecution 逻辑，实时显示中文描述和下次执行时间
- [ ] T060 [US5] 在 src/main/frontend/src/pages/ 中将 CronBuilder 组件集成到同步任务配置页面，替换原有纯文本 Cron 输入

**检查点**：此时，用户故事 5 应完全功能可用 — 图形化配置可正常工作，预览准确

---

## 阶段 8：用户故事 6 — 文案统一替换（优先级：P3）

**目标**：所有界面中"存储引擎"概念相关的"AList"文案统一替换为"存储引擎"，产品名称保持不变

**独立测试**：遍历所有前端页面确认不再出现以存储引擎概念出现的"AList"文案

### 用户故事 6 的实现

- [ ] T061 [US6] 在 src/main/frontend/src/ 中全局搜索并替换存储引擎概念相关的"AList"文案为"存储引擎"（排除产品名称"AList Media Sync"/"AList Sync"等品牌标识）
- [ ] T062 [US6] 在 src/main/frontend/src/pages/ 中检查并更新侧边栏导航、页面标题、表单标签、按钮文案、空状态提示等所有用户可见文案
- [ ] T063 [US6] 在 src/main/java/top/lldwb/alistmediasync/ 中检查后端 API 错误信息和日志中的"AList"引用，存储引擎概念相关的替换为"存储引擎"

**检查点**：此时，用户故事 6 应完全功能可用 — 所有存储引擎相关文案统一为"存储引擎"

---

## 阶段 9：润色与跨领域关注点

**目的**：影响多个用户故事的改进和最终验证

- [ ] T064 [P] 在 src/main/java/top/lldwb/alistmediasync/config/GlobalExceptionHandler.java 中确保新增异常类型（IllegalStateException 状态转换、IllegalArgumentException 引擎类型）有统一错误响应
- [ ] T065 [P] 在 src/main/java/top/lldwb/alistmediasync/service/ 中审查所有 Service 层 @Transactional 注解，确保写操作在事务上下文中执行
- [ ] T066 在 src/main/java/top/lldwb/alistmediasync/entity/ 中审查所有实体 @Version 乐观锁字段，确保新增字段和关联关系的数据完整性
- [ ] T067 代码清理：移除 AListClient 中已被策略模式替代的废弃方法，移除 TranscodeStatus 中已删除的 SCANNING/FAILED 枚举值的残留引用
- [ ] T068 [P] 在 src/test/java/top/lldwb/alistmediasync/ 中补充跨故事集成测试（同步任务使用本地路径引擎、Webhook 触发转码使用 recordingEngine）
- [ ] T069 运行 quickstart.md 中所有验证场景，确认端到端功能正常
- [ ] T070 更新项目根目录 README.md，同步新增功能、配置项、API 端点说明

---

## 依赖与执行顺序

### 阶段依赖

- **设置（阶段 1）**：无依赖 — 可立即开始
- **基础（阶段 2）**：依赖设置完成 — 阻塞所有用户故事
- **用户故事（阶段 3-8）**：全部依赖基础阶段完成
  - US1（P1）和 US2（P1）可并行开始
  - US3（P2）依赖 US1（需要 StorageEngine 策略模式和 recordingEngine 关联）
  - US4（P2）依赖 US1 + 阶段 2（需要目录浏览端点和策略模式）
  - US5（P3）和 US6（P3）可在基础完成后并行开始，但建议在 US1 后执行
- **润色（阶段 9）**：依赖所有期望的用户故事完成

### 用户故事依赖

- **US1（P1）**：可在基础（阶段 2）后开始 — 不依赖其他故事
- **US2（P1）**：可在基础（阶段 2）后开始 — 依赖策略模式（阶段 2），不依赖 US1 的前端部分
- **US3（P2）**：依赖 US1（需要 StorageEngine 实体变更和策略模式完成，recordingEngine 关联需要 engineType 字段）
- **US4（P2）**：依赖阶段 2（目录浏览端点）+ US1（前端存储引擎类型判断逻辑）
- **US5（P3）**：可在基础后开始 — 独立组件，不依赖其他故事
- **US6（P3）**：建议在 US1 后执行 — 需要确认哪些"AList"文案属于存储引擎概念

### 每个用户故事内部

- 测试必须在实现之前编写并失败
- 实体/模型先于 Service
- Service 先于 Controller
- 后端先于前端
- 核心实现先于集成

### 并行机会

- 阶段 1 中 T002-T009 均可并行（不同文件，无依赖）
- 阶段 2 中 T016-T018 可并行（不同测试文件）
- US1 和 US2 的后端部分可并行（不同 Service/Controller 文件）
- US4 的 T051-T052 可并行（API 客户端和类型定义）
- US5 的 T057 可与 T058 并行（工具函数和组件）
- US6 的 T061-T063 可并行（不同目录的文案替换）

---

## 并行示例：用户故事 1

```bash
# 一起启动用户故事 1 的所有测试：
任务："编写 StorageEngineController @WebMvcTest 测试"
任务："编写 StorageEngineService CRUD 单元测试"

# 一起启动用户故事 1 的前端任务（后端完成后）：
任务："更新存储引擎管理页面"
任务："更新 StorageEngine TypeScript 类型定义"
任务："更新存储引擎 API 客户端"
```

## 并行示例：用户故事 1 + 用户故事 2

```bash
# US1 后端和 US2 后端可并行：
任务 US1："实现 StorageEngineService 创建/更新逻辑"
任务 US2："实现 TranscodeService 8 状态转换表"
```

---

## 实现策略

### MVP 优先（仅用户故事 1 + 用户故事 2）

1. 完成阶段 1：设置
2. 完成阶段 2：基础（策略模式核心 — 关键，阻塞所有故事）
3. 完成阶段 3：用户故事 1（存储引擎策略模式与本地路径支持）
4. 完成阶段 4：用户故事 2（转码三步流程优化）
5. **停止并验证**：独立测试 US1 + US2
6. 如果就绪则部署/演示

### 增量交付

1. 完成设置 + 基础 → 基础就绪
2. 添加 US1 + US2 → 独立测试 → 部署/演示（MVP！核心架构变更 + 转码优化）
3. 添加 US3 → 独立测试 → 部署/演示（Webhook 路径优化）
4. 添加 US4 → 独立测试 → 部署/演示（树状目录浏览）
5. 添加 US5 + US6 → 独立测试 → 部署/演示（Cron 图形化 + 文案统一）
6. 润色 → 最终验证 → 正式发布

### 并行团队策略

多个开发人员时：

1. 团队一起完成设置 + 基础
2. 基础完成后：
   - 开发人员 A：US1（存储引擎策略模式）→ US3（Webhook 路径优化）
   - 开发人员 B：US2（转码三步流程）→ US4（树状目录浏览）
   - 开发人员 C：US5（Cron 图形化）+ US6（文案统一）
3. 各故事独立完成并集成

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应能独立完成和测试
- 实现前验证测试失败
- 每个任务或逻辑组后提交
- 在任何检查点停止以独立验证故事
- 避免：模糊的任务、同文件冲突、破坏独立性的跨故事依赖
- 数据迁移注意：现有 StorageEngine 记录 engineType 默认 ALIST，现有 TranscodeStatus.SCANNING 映射为 DOWNLOADING，现有 FAILED 映射为 TRANSCODE_FAILED（8状态模型：PENDING/DOWNLOADING/DOWNLOAD_FAILED/TRANSCODING/TRANSCODE_FAILED/UPLOADING/UPLOAD_FAILED/COMPLETED）
