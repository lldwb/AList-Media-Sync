# 功能规格：密码加密优化与代码目录重组

**功能分支**：`007-password-encryption-and-code-organization`

**创建日期**：2026-06-20

**状态**：已排期

**输入**：用户描述："1、不提供设置加密后的密码（配置文件只设置明文），每次启动的盐值都随机生成的；2、创建转码任务时，添加原目录转码选项；3、代码按照不同功能目录管理，不要都放在 src/main/java/top/lldwb/alistmediasync 下面，参考 src/main/java/top/lldwb/alistmediasync/xxx1 src/main/java/top/lldwb/alistmediasync/xxx2 这种格式"

## 澄清

### 会话 2026-06-20

- Q: "不提供设置加密后的密码"的具体含义？ → A: 配置文件中的 `app.auth.password` 只接受明文密码，不再支持 `{bcrypt}` 前缀的预加密格式。每次启动时系统自动使用随机盐值生成 BCrypt 哈希并保存在内存中，配置文件中始终保持用户填入的明文。这意味着 `PasswordEncryptionPostProcessor` 的 `{bcrypt}` 前缀检测逻辑需要简化——始终将配置值视为明文并加密，不再需要判断"是否已加密"。

- Q: "原目录转码"的含义？ → A: 创建转码任务时，如果用户未指定目标路径，则转码输出文件放置在与源文件相同的目录下（即"原目录转码"）。这是一种便捷选项，避免用户每次都要手动填写目标路径。如果用户指定了目标路径，则按指定路径输出。

- Q: 代码目录重组的粒度？ → A: 按功能模块拆分，每个模块有独立的子包。例如 `top.lldwb.alistmediasync.storage`（存储引擎）、`top.lldwb.alistmediasync.sync`（同步）、`top.lldwb.alistmediasync.transcode`（转码）、`top.lldwb.alistmediasync.webhook`（Webhook）、`top.lldwb.alistmediasync.common`（通用组件）。每个子包内部保持现有的分层结构（entity/repository/dto/service/controller）。
- Q: 移除 `{bcrypt}` 前缀检测后，每次启动都会执行加密。日志输出策略：每次启动都输出加密日志，还是仅在密码变更时输出？ → A: 不输出日志。加密过程完全静默，不输出"检测到明文密码，已自动加密为 BCrypt 格式"日志。仅在密码为空或未设置时输出 WARN 日志。
- Q: 原目录转码选项的实现方式：后端 API 是否应将 `targetFilePath` 从必填改为可选（`sameDirectoryTranscode=true` 时后端自动计算），还是保持 `targetFilePath` 必填、由前端在勾选时自动填充源目录路径？ → A: `targetFilePath` 改为可选。当 `sameDirectoryTranscode=true` 时后端自动计算目标路径（使用源文件所在目录），无需前端填充；当 `sameDirectoryTranscode=false` 时 `targetFilePath` 仍为必填。
- Q: `common` 子包内部是否也需要保持分层结构（如 `common/config/`、`common/dto/`、`common/util/`），还是将所有通用类直接放在 `common` 包下（扁平结构）？ → A: 按技术层分层。`common` 包内部同样保持 `config/`、`dto/`、`interceptor/`、`util/`、`client/`、`service/`、`controller/` 分层结构。
- Q: `src/test` 下的测试文件目录结构是否也需要按功能模块重组（与 `src/main` 镜像对应），还是仅更新 import 语句？ → A: 同步重组测试目录。测试文件的包路径必须与源文件的新包路径保持一致镜像。

## 用户场景与测试 *（强制）*

### 用户故事 1 — 配置文件仅支持明文密码（优先级：P1）

作为一名运维人员，我希望在配置文件中只需填写明文密码（如 `password: "admin123"`），系统每次启动时自动使用随机盐值生成 BCrypt 哈希并保存在内存中，无需我手动生成或粘贴哈希值。配置文件不再接受 `{bcrypt}` 预加密格式，保持配置的极简性和一致性。

**为什么是此优先级**：这是对现有密码加密机制的简化优化。当前实现支持明文和 `{bcrypt}` 两种格式，增加了代码复杂度和用户困惑（用户不确定应该填哪种）。统一为仅明文输入，每次启动随机盐值加密，既简化了代码也提升了安全性（每次启动盐值不同，即使同一密码每次生成的哈希也不同）。

**独立测试**：在 `application.yaml` 中设置 `app.auth.password: "test123"`（明文），启动系统，验证过程静默无日志输出，且用 `test123` 能正常登录管理界面。关闭应用后再次启动，验证用 `test123` 仍能正常登录（验证每次启动独立加密）。尝试在配置文件中设置 `{bcrypt}$2a$10$...` 格式的密码，验证系统将其视为明文密码（即把 `{bcrypt}...` 字符串本身当作密码进行 BCrypt 加密），而非识别为已加密格式。

**验收场景**：

1. **假设** 配置文件中 `app.auth.password` 设置为明文 `"admin123"`，**当** 应用启动，**则** 系统使用随机盐值生成 BCrypt 哈希并加载到内存中的 Spring Environment，整个过程静默无日志输出。配置文件中的值保持不变（仍为明文），用户可使用 `admin123` 正常登录管理界面。
2. **假设** 配置文件中 `app.auth.password` 设置为 `"{bcrypt}$2a$10$xxxxx"`（旧版预加密格式），**当** 应用启动，**则** 系统不再识别 `{bcrypt}` 前缀，将整个字符串作为明文密码进行 BCrypt 加密，用户无法使用原密码登录（因为系统将 `{bcrypt}...` 字符串本身当作密码原文进行了加密）。
3. **假设** 用户修改了明文密码为新值 `"newpassword456"`，**当** 启动应用，**则** 系统使用新的随机盐值加密新密码到内存，用户可使用新密码登录。
4. **假设** 配置文件中 `app.auth.password` 为空或未设置，**当** 应用启动，**则** 系统输出中文警告："认证密码未设置，管理后台将无法登录。请在 application.yaml 中配置 app.auth.password。"，但服务仍正常启动。

---

### 用户故事 2 — 原目录转码选项（优先级：P2）

作为一名系统管理员，我希望在创建转码任务时可以选择"原目录转码"选项——即转码输出文件自动放置在与源文件相同的目录下，无需我手动填写目标路径。这样在批量处理同一目录下的视频文件时，可以大幅减少重复的目标路径配置工作。

**为什么是此优先级**：这是一个便捷性优化，不影响核心转码功能。对于大多数使用场景（同一目录下批量转码），原目录转码是最常见的需求。当前用户必须手动填写目标路径，增加了操作步骤和出错概率。

**独立测试**：创建一个转码任务，选择源文件路径，勾选"原目录转码"选项，不填写目标路径，执行转码后验证输出文件是否出现在源文件所在目录。

**验收场景**：

1. **假设** 用户创建转码任务时选择了源文件路径 `/videos/test.flv`，勾选了"原目录转码"选项且未填写目标路径，**当** 转码任务执行完成，**则** 后端自动将目标路径设为 `/videos/`（源文件所在目录），转码输出文件（如 `test.mp3`）位于 `/videos/` 目录下。
2. **假设** 用户创建转码任务时选择了源文件路径 `/videos/test.flv`，未勾选"原目录转码"选项并指定了目标路径 `/output/`，**当** 转码任务执行完成，**则** 转码输出文件位于 `/output/` 目录下（按用户指定路径）。
3. **假设** 用户创建转码任务时勾选了"原目录转码"选项，**当** 查看创建表单，**则** 目标路径输入框变为禁用状态（灰色），自动显示为源文件所在目录路径。后端 API 的 `targetFilePath` 字段可为空。
4. **假设** 用户创建转码任务时未勾选"原目录转码"选项，**当** 查看创建表单，**则** 目标路径输入框为可编辑状态，用户必须手动输入目标路径。后端 API 的 `targetFilePath` 字段为必填。

---

### 用户故事 3 — 代码按功能模块目录重组（优先级：P2）

作为一名开发者，我希望项目的 Java 源代码按功能模块组织（如 `storage`、`sync`、`transcode`、`webhook`、`common`），而不是所有类都平铺在同一个包 `top.lldwb.alistmediasync` 下，这样我可以快速定位到特定功能的代码，降低模块间的耦合度，提升代码的可维护性。

**为什么是此优先级**：代码目录重组是架构优化，不影响业务功能。当前 60+ 个 Java 文件全部放在同一个包下，查找和维护困难。按功能模块拆分后，每个模块职责清晰，符合 SOLID 原则中的单一职责原则（SRP）和接口隔离原则（ISP）。

**独立测试**：重组后执行 `./mvnw clean test`，验证所有测试通过。验证各模块的包结构符合设计规范，每个功能模块内部保持分层结构。

**验收场景**：

1. **假设** 代码目录重组完成，**当** 开发者查看 `src/main/java/top/lldwb/alistmediasync/` 目录，**则** 看到以下子包：`common/`（通用组件）、`storage/`（存储引擎）、`sync/`（同步任务）、`transcode/`（转码）、`webhook/`（Webhook），以及根包下的 `AListMediaSyncApplication.java` 启动类。
2. **假设** 代码目录重组完成，**当** 开发者查看 `storage/` 子包，**则** 内部包含 `entity/`、`repository/`、`dto/`、`service/`、`controller/` 等分层子包（按需，仅包含该模块相关的类）。
3. **假设** 代码目录重组完成，**当** 执行 `./mvnw clean test`，**则** 所有现有测试通过，无编译错误。
4. **假设** 代码目录重组完成，**当** 开发者查看 `common/` 子包，**则** 内部同样保持分层结构：`config/`（全局配置）、`dto/ApiResult.java`（统一响应）、`interceptor/`（认证拦截器）、`util/`（工具类）、`client/`（AList 客户端）、`service/`（DashboardService、CleanupService）、`controller/`（DashboardController）。
5. **假设** 代码目录重组完成，**当** 开发者查看 `src/test/java/top/lldwb/alistmediasync/` 目录，**则** 测试目录结构与 `src/main` 镜像对应，同样按功能模块拆分为 `common/`、`storage/`、`sync/`、`transcode/`、`webhook/` 子包。

### 边界情况

- 当用户使用旧版配置文件（含 `{bcrypt}` 前缀密码）升级到新版本时，会发生什么？系统将 `{bcrypt}...` 字符串视为明文密码进行加密，用户将无法用原密码登录。需在发布说明中明确告知用户：升级后需将密码改为明文。
- 当用户同时勾选"原目录转码"并填写了目标路径时，以哪个为准？系统应以"原目录转码"选项为优先——勾选后忽略用户填写的目标路径，使用源文件所在目录作为目标路径。后端实现：`sameDirectoryTranscode=true` 时忽略 `targetFilePath` 字段值，自动计算目标路径。
- 当源文件位于存储引擎根目录（如 `/`）时，原目录转码的目标路径是什么？目标路径为根目录 `/`。
- 代码目录重组后，Spring Boot 的组件扫描是否会受影响？`@SpringBootApplication` 默认扫描启动类所在包及其子包，由于所有子包仍在 `top.lldwb.alistmediasync` 下，组件扫描不受影响。
- 代码目录重组后，JPA 实体扫描是否会受影响？需要确保 `@EntityScan` 或 `@Entity` 注解的实体类仍在扫描路径内。由于所有实体类仍在 `top.lldwb.alistmediasync` 的子包下，Spring Boot 自动配置的实体扫描不受影响。
- 代码目录重组后，现有的 import 语句如何处理？所有类的包路径变更后，需要更新所有引用这些类的 import 语句。这包括 Java 源文件中的 import 以及测试文件中的 import。

## 需求 *（强制）*

### 功能需求

#### 密码加密简化

- **FR-001**：`application.yaml` 中的 `app.auth.password` 配置项必须仅支持明文值。系统不再识别 `{bcrypt}` 前缀，所有配置值均视为明文密码。
- **FR-002**：系统必须在每次启动时使用随机盐值（通过 `BCryptPasswordEncoder` 默认构造函数，每次生成不同的盐值）对明文密码进行 BCrypt 加密。加密后的密码仅保存在内存中的 Spring Environment 内，绝不回写到 YAML 文件。
- **FR-003**：系统必须在密码加密过程中保持静默，不输出加密成功日志。仅在密码为空或未设置时输出中文 WARN 日志："认证密码未设置，管理后台将无法登录。请在 application.yaml 中配置 app.auth.password。"
- **FR-004**：`PasswordEncryptionPostProcessor` 必须移除对 `{bcrypt}` 前缀的检测逻辑和 BCrypt 哈希格式验证逻辑，简化为始终执行加密。
- **FR-005**：`AuthInterceptor` 必须移除对非 `{bcrypt}` 前缀密码的明文比较回退逻辑——密码在到达拦截器之前已被 `PasswordEncryptionPostProcessor` 加密为 `{bcrypt}` 格式，拦截器只需处理 `{bcrypt}` 格式的密码验证。

#### 原目录转码选项

- **FR-006**：创建转码任务时，必须提供"原目录转码"选项（布尔值，默认 `false`）。
- **FR-007**：当"原目录转码"选项启用时，系统必须自动将目标路径设置为源文件所在目录路径。前端目标路径输入框变为禁用状态（灰色），并自动填充源文件所在目录路径；目标路径的红色必填标记 `*` 隐藏。当选项取消勾选时，目标路径输入框恢复可编辑状态，必填标记恢复显示。
- **FR-008**：当"原目录转码"选项启用时，后端必须自动使用源文件所在目录作为目标路径，无需前端填充 `targetFilePath`。当"原目录转码"选项未启用时，`targetFilePath` 为必填字段。`targetFilePath` 字段从必填改为可选，`sameDirectoryTranscode=true` 时可为空，后端自动计算。
- **FR-009**：`TranscodeTaskCreateDTO` 必须新增 `sameDirectoryTranscode`（原目录转码）布尔字段。
- **FR-020**：前端 `api.ts` 中的 `TranscodeTaskCreateDTO` 接口必须新增 `sameDirectoryTranscode?: boolean` 可选字段，与后端 DTO 字段对齐。
- **FR-021**：前端 `TranscodeTaskForm.tsx` 转码任务创建表单必须新增"原目录转码"复选框，默认不勾选。勾选后：目标路径输入框禁用（灰色）、目标路径必填标记 `*` 隐藏、自动将源文件所在目录路径填充到目标路径输入框、清除目标路径的校验错误。取消勾选后：恢复目标路径输入框可编辑、恢复必填标记、恢复目标路径校验。
- **FR-022**：前端表单提交时，`sameDirectoryTranscode=true` 时 `targetFilePath` 传空字符串（后端自动计算），`sameDirectoryTranscode=false` 时 `targetFilePath` 传用户输入值。

#### 代码目录重组

- **FR-010**：Java 源代码必须按功能模块拆分为以下子包（均在 `top.lldwb.alistmediasync` 下）。`common` 子包内部同样保持技术层分层结构（`config/`、`dto/`、`interceptor/`、`util/`、`client/`、`service/`、`controller/`）：

| 子包 | 包含内容 | 现有类（示例） |
|------|---------|---------------|
| `common/` | 全局配置、通用 DTO、拦截器、工具类、客户端 | `config/AppProperties.java`、`config/AsyncConfig.java`、`config/WebMvcConfig.java`、`config/RestClientConfig.java`、`config/GlobalExceptionHandler.java`、`config/PasswordEncryptionPostProcessor.java`、`dto/ApiResult.java`、`interceptor/AuthInterceptor.java`、`util/*.java`、`client/AListClient.java` |
| `storage/` | 存储引擎模块 | `entity/StorageEngine.java`、`repository/StorageEngineRepository.java`、`dto/storage/*.java`、`service/StorageEngineService.java`、`service/engine/*.java`、`controller/StorageEngineController.java` |
| `sync/` | 同步任务模块 | `entity/SyncTask.java`、`entity/TaskExecution.java`、`repository/SyncTaskRepository.java`、`repository/TaskExecutionRepository.java`、`dto/sync/*.java`、`service/SyncService.java`、`service/SyncTaskManageService.java`、`service/ScheduleService.java`、`controller/SyncTaskController.java` |
| `transcode/` | 转码模块 | `entity/TranscodeTask.java`、`repository/TranscodeTaskRepository.java`、`dto/transcode/*.java`、`service/TranscodeService.java`、`service/TranscodeFileProcessor.java`、`service/TranscodeCandidate.java`、`service/TranscodeResult.java`、`controller/TranscodeTaskController.java` |
| `webhook/` | Webhook 模块 | `entity/WebhookRule.java`、`entity/WebhookEvent.java`、`repository/WebhookRuleRepository.java`、`repository/WebhookEventRepository.java`、`dto/webhook/*.java`、`service/WebhookService.java`、`service/WebhookRuleService.java`、`controller/WebhookController.java`、`controller/WebhookRuleController.java`、`controller/WebhookEventController.java` |

- **FR-011**：每个功能子包内部必须保持现有的分层结构（`entity/`、`repository/`、`dto/`、`service/`、`controller/`），仅包含该模块相关的类。
- **FR-012**：`AListMediaSyncApplication.java` 启动类必须保留在根包 `top.lldwb.alistmediasync` 下。
- **FR-013**：`DashboardService.java` 和 `DashboardController.java` 以及 `DashboardStatsVO.java` 应归入 `common/` 子包（仪表板聚合了多个模块的数据，属于通用功能）。
- **FR-014**：`CleanupService.java` 应归入 `common/` 子包（清理服务跨模块操作，属于通用功能）。
- **FR-015**：`ServerAddressLogger.java` 应归入 `common/` 子包（启动地址输出属于通用功能）。
- **FR-016**：`CryptoConverter.java` 应归入 `common/` 子包（加密转换器属于通用功能）。
- **FR-017**：所有 Java 源文件中的 `package` 声明和相互引用的 `import` 语句必须更新为新的包路径。
- **FR-018**：`src/test` 下的测试文件目录结构必须与 `src/main` 镜像对应，同步重组为功能模块子包。所有测试文件中的 `package` 声明和 `import` 语句必须同步更新为新的包路径。
- **FR-019**：`META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor` 文件中的全限定类名必须更新（如果 `PasswordEncryptionPostProcessor` 的包路径变更）。

### 关键实体

- **密码加密（概念实体）**：配置文件中的明文密码，每次启动时由 `PasswordEncryptionPostProcessor` 使用随机盐值 BCrypt 加密后注入内存中的 Spring Environment。配置文件始终保持明文，加密值仅存在于内存中。
- **转码任务（TranscodeTask）**：新增 `sameDirectoryTranscode`（原目录转码）布尔字段。当为 `true` 时，目标路径自动使用源文件所在目录。

## 成功标准 *（强制）*

### 可衡量的结果

- **SC-001**：用户在配置文件中只需填写明文密码，系统每次启动使用不同的随机盐值加密，同一密码连续两次启动生成的 BCrypt 哈希值不同（验证随机盐值生效）。加密过程静默执行，无日志输出。
- **SC-002**：`PasswordEncryptionPostProcessor` 的代码行数减少至少 30%（移除 `{bcrypt}` 前缀检测和哈希格式验证逻辑）。
- **SC-003**：用户创建转码任务时勾选"原目录转码"后，无需手动填写目标路径即可成功创建任务。
- **SC-004**：代码目录重组后，`./mvnw clean test` 所有测试通过，无编译错误。
- **SC-005**：代码目录重组后，每个功能子包的类文件数量不超过 15 个（保持模块粒度合理）。
- **SC-006**：代码目录重组后，开发者可以在 10 秒内定位到任意功能模块的核心类（通过包名直接导航）。

## 假设

- **A1**：用户理解升级到新版本后，旧版配置文件中的 `{bcrypt}` 预加密密码将失效，需要改为明文密码。
- **A2**：原目录转码选项仅适用于独立转码任务创建，不改变同步后置转码和 Webhook 触发转码的行为（这两种场景已有各自的目标路径配置机制）。
- **A3**：代码目录重组不涉及任何业务逻辑变更，仅移动文件位置和更新包声明/import 语句。
- **A4**：Spring Boot 的自动配置（`@SpringBootApplication`、`@EntityScan`、`@EnableJpaRepositories`）默认扫描启动类所在包及其子包，因此代码目录重组后组件扫描不受影响。
- **A5**：前端代码（React/TypeScript）需要同步更新——类型定义 `api.ts` 需新增 `sameDirectoryTranscode` 字段，转码任务表单 `TranscodeTaskForm.tsx` 需新增"原目录转码"复选框及配套交互逻辑。API 接口请求/响应格式（JSON 结构）不变。
- **A6**：`BCryptPasswordEncoder` 的默认构造函数每次生成不同的随机盐值，满足"每次启动盐值随机"的需求。

## 与其他规格的关系

### 对 005-standalone-bootstrap 的修改

- **FR-015（密码加密）**：005 中定义的密码加密支持明文和 `{bcrypt}` 两种格式。本规格将其简化为仅支持明文，移除 `{bcrypt}` 前缀检测逻辑。
- **password-encryption.md 契约**：005 的密码加密契约需要更新——移除 `{bcrypt}` 前缀检测步骤，简化为始终加密。

### 对 001-alist-media-sync 的修改

- **TranscodeTaskCreateDTO**：新增 `sameDirectoryTranscode` 字段（FR-009）。
- **TranscodeService.createTask()**：新增原目录转码逻辑——当 `sameDirectoryTranscode=true` 时，自动将 `targetFilePath` 设置为源文件所在目录。

### 对 004-web-management-frontend 的依赖

- 前端转码任务创建表单需要新增"原目录转码"复选框，勾选后禁用目标路径输入框并自动填充源文件目录路径。
- 前端类型定义 `api.ts` 的 `TranscodeTaskCreateDTO` 接口需新增 `sameDirectoryTranscode?: boolean` 字段。
- 前端表单校验逻辑需调整：原目录转码时目标路径为可选，未勾选时目标路径为必填。
- 前端提交时需将 `sameDirectoryTranscode` 字段透传给后端 API。

### 对 006-storage-engine-refactor 的关系

- 006 已完成存储引擎策略模式重构和代码结构优化。本规格的代码目录重组在 006 的基础上进一步按功能模块拆分。
- 006 中定义的转码三步流程和 8 状态模型不受本规格影响。
