# 任务：密码加密优化与代码目录重组

**输入**：来自 `/specs/007-password-encryption-and-code-organization/` 的设计文档

**前提条件**：plan.md（必需）、spec.md（用户故事必需）

**测试**：章程原则 V 要求测试不可省略，以下任务包含测试任务。

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（例如，US1、US2、US3）
- 在描述中包含确切的文件路径

## 路径约定

- **后端**：`src/main/java/top/lldwb/alistmediasync/`
- **测试**：`src/test/java/top/lldwb/alistmediasync/`
- **配置**：`src/main/resources/`

---

## 阶段 1：设置（共享基础设施）

**目的**：代码目录重组——所有用户故事共享的包结构变更。必须在任何功能变更之前完成，因为所有后续任务都依赖新的包路径。

**⚠️ 关键**：此阶段是阻塞性前置条件，完成后才能开始用户故事实现。

- [ ] T001 在 `src/main/java/top/lldwb/alistmediasync/` 下创建 5 个功能模块子包目录结构：`common/config/`、`common/dto/`、`common/entity/`、`common/interceptor/`、`common/util/`、`common/client/`、`common/service/`、`common/controller/`、`storage/entity/`、`storage/repository/`、`storage/dto/storage/`、`storage/service/engine/`、`storage/controller/`、`sync/entity/`、`sync/repository/`、`sync/dto/sync/`、`sync/service/`、`sync/controller/`、`transcode/entity/`、`transcode/repository/`、`transcode/dto/transcode/`、`transcode/service/`、`transcode/controller/`、`webhook/entity/`、`webhook/repository/`、`webhook/dto/webhook/`、`webhook/service/`、`webhook/controller/`
- [ ] T002 [P] 在 `src/test/java/top/lldwb/alistmediasync/` 下创建镜像测试目录结构：`common/config/`、`common/util/`、`common/service/`、`common/controller/`、`storage/service/engine/`、`sync/service/`、`transcode/service/`、`webhook/service/`

---

## 阶段 2：用户故事 3 — 代码按功能模块目录重组（优先级：P2）🎯 基础设施

**目标**：将 64 个 `src/main` Java 文件和 16 个 `src/test` Java 文件按 5 个功能模块（common/storage/sync/transcode/webhook）重新组织包结构，每个模块内部保持分层结构。

**独立测试**：重组后执行 `./mvnw clean test`，验证所有测试通过。验证各模块的包结构符合 FR-010 定义。

### 用户故事 3 的 common 模块迁移

- [ ] T003 [US3] 使用 `git mv` 将 `config/AppProperties.java`、`config/AsyncConfig.java`、`config/WebMvcConfig.java`、`config/RestClientConfig.java`、`config/GlobalExceptionHandler.java`、`config/PasswordEncryptionPostProcessor.java` 移动到 `common/config/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.common.config`
- [ ] T004 [P] [US3] 使用 `git mv` 将 `dto/ApiResult.java`、`dto/DashboardStatsVO.java` 移动到 `common/dto/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.common.dto`
- [ ] T005 [P] [US3] 使用 `git mv` 将 `entity/CryptoConverter.java` 移动到 `common/entity/`，更新 `package` 声明为 `top.lldwb.alistmediasync.common.entity`
- [ ] T006 [P] [US3] 使用 `git mv` 将 `interceptor/AuthInterceptor.java` 移动到 `common/interceptor/`，更新 `package` 声明为 `top.lldwb.alistmediasync.common.interceptor`
- [ ] T007 [P] [US3] 使用 `git mv` 将 `util/MagicBytesDetector.java`、`util/TempFileManager.java`、`util/DiskSpaceChecker.java`、`util/TempSuffixValidator.java`、`util/ServerAddressLogger.java` 移动到 `common/util/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.common.util`
- [ ] T008 [P] [US3] 使用 `git mv` 将 `client/AListClient.java` 移动到 `common/client/`，更新 `package` 声明为 `top.lldwb.alistmediasync.common.client`
- [ ] T009 [P] [US3] 使用 `git mv` 将 `service/DashboardService.java`、`service/CleanupService.java` 移动到 `common/service/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.common.service`
- [ ] T010 [P] [US3] 使用 `git mv` 将 `controller/DashboardController.java` 移动到 `common/controller/`，更新 `package` 声明为 `top.lldwb.alistmediasync.common.controller`

### 用户故事 3 的 storage 模块迁移

- [ ] T011 [US3] 使用 `git mv` 将 `entity/StorageEngine.java` 移动到 `storage/entity/`，更新 `package` 声明为 `top.lldwb.alistmediasync.storage.entity`
- [ ] T012 [P] [US3] 使用 `git mv` 将 `repository/StorageEngineRepository.java` 移动到 `storage/repository/`，更新 `package` 声明为 `top.lldwb.alistmediasync.storage.repository`
- [ ] T013 [P] [US3] 使用 `git mv` 将 `dto/storage/StorageEngineCreateDTO.java`、`dto/storage/StorageEngineUpdateDTO.java`、`dto/storage/StorageEngineVO.java` 移动到 `storage/dto/storage/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.storage.dto.storage`
- [ ] T014 [P] [US3] 使用 `git mv` 将 `service/StorageEngineService.java` 移动到 `storage/service/`，将 `service/engine/StorageEngineStrategy.java`、`service/engine/AListStorageStrategy.java`、`service/engine/LocalStorageStrategy.java` 移动到 `storage/service/engine/`，更新每个文件的 `package` 声明
- [ ] T015 [P] [US3] 使用 `git mv` 将 `controller/StorageEngineController.java` 移动到 `storage/controller/`，更新 `package` 声明为 `top.lldwb.alistmediasync.storage.controller`

### 用户故事 3 的 sync 模块迁移

- [ ] T016 [US3] 使用 `git mv` 将 `entity/SyncTask.java`、`entity/TaskExecution.java` 移动到 `sync/entity/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.sync.entity`
- [ ] T017 [P] [US3] 使用 `git mv` 将 `repository/SyncTaskRepository.java`、`repository/TaskExecutionRepository.java` 移动到 `sync/repository/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.sync.repository`
- [ ] T018 [P] [US3] 使用 `git mv` 将 `dto/sync/SyncTaskCreateDTO.java`、`dto/sync/SyncTaskUpdateDTO.java`、`dto/sync/SyncTaskVO.java`、`dto/sync/SyncProgressVO.java`、`dto/sync/TaskExecutionVO.java`、`dto/sync/DirectoryEntryVO.java`、`dto/sync/FileEntry.java` 移动到 `sync/dto/sync/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.sync.dto.sync`
- [ ] T019 [P] [US3] 使用 `git mv` 将 `service/SyncService.java`、`service/SyncTaskManageService.java`、`service/ScheduleService.java` 移动到 `sync/service/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.sync.service`
- [ ] T020 [P] [US3] 使用 `git mv` 将 `controller/SyncTaskController.java` 移动到 `sync/controller/`，更新 `package` 声明为 `top.lldwb.alistmediasync.sync.controller`

### 用户故事 3 的 transcode 模块迁移

- [ ] T021 [US3] 使用 `git mv` 将 `entity/TranscodeTask.java` 移动到 `transcode/entity/`，更新 `package` 声明为 `top.lldwb.alistmediasync.transcode.entity`
- [ ] T022 [P] [US3] 使用 `git mv` 将 `repository/TranscodeTaskRepository.java` 移动到 `transcode/repository/`，更新 `package` 声明为 `top.lldwb.alistmediasync.transcode.repository`
- [ ] T023 [P] [US3] 使用 `git mv` 将 `dto/transcode/TranscodeTaskCreateDTO.java`、`dto/transcode/TranscodeTaskVO.java` 移动到 `transcode/dto/transcode/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.transcode.dto.transcode`
- [ ] T024 [P] [US3] 使用 `git mv` 将 `service/TranscodeService.java`、`service/TranscodeFileProcessor.java`、`service/TranscodeCandidate.java`、`service/TranscodeResult.java` 移动到 `transcode/service/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.transcode.service`
- [ ] T025 [P] [US3] 使用 `git mv` 将 `controller/TranscodeTaskController.java` 移动到 `transcode/controller/`，更新 `package` 声明为 `top.lldwb.alistmediasync.transcode.controller`

### 用户故事 3 的 webhook 模块迁移

- [ ] T026 [US3] 使用 `git mv` 将 `entity/WebhookRule.java`、`entity/WebhookEvent.java` 移动到 `webhook/entity/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.webhook.entity`
- [ ] T027 [P] [US3] 使用 `git mv` 将 `repository/WebhookRuleRepository.java`、`repository/WebhookEventRepository.java` 移动到 `webhook/repository/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.webhook.repository`
- [ ] T028 [P] [US3] 使用 `git mv` 将 `dto/webhook/WebhookRuleCreateDTO.java`、`dto/webhook/WebhookRuleVO.java`、`dto/webhook/WebhookEventVO.java` 移动到 `webhook/dto/webhook/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.webhook.dto.webhook`
- [ ] T029 [P] [US3] 使用 `git mv` 将 `service/WebhookService.java`、`service/WebhookRuleService.java` 移动到 `webhook/service/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.webhook.service`
- [ ] T030 [P] [US3] 使用 `git mv` 将 `controller/WebhookController.java`、`controller/WebhookRuleController.java`、`controller/WebhookEventController.java` 移动到 `webhook/controller/`，更新每个文件的 `package` 声明为 `top.lldwb.alistmediasync.webhook.controller`

### 用户故事 3 的测试文件迁移

- [ ] T031 [US3] 使用 `git mv` 将 `src/test` 下的测试文件按 plan.md §3.2 定义的镜像结构移动到对应模块子包，更新每个测试文件的 `package` 声明：`config/PasswordEncryptionPostProcessorTest.java` → `common/config/`、`util/ServerAddressLoggerTest.java` → `common/util/`、`service/DashboardServiceTest.java` → `common/service/`、`service/CleanupServiceTest.java` → `common/service/`、`controller/DashboardControllerTest.java` → `common/controller/`、`service/StorageEngineServiceTest.java` → `storage/service/`、`service/engine/AListStorageStrategyTest.java` → `storage/service/engine/`、`service/engine/LocalStorageStrategyTest.java` → `storage/service/engine/`、`service/SyncServiceTest.java` → `sync/service/`、`service/SyncTaskManageServiceTest.java` → `sync/service/`、`service/ScheduleServiceTest.java` → `sync/service/`、`service/TranscodeServiceTest.java` → `transcode/service/`、`service/TranscodeFileProcessorTest.java` → `transcode/service/`、`service/WebhookServiceTest.java` → `webhook/service/`、`service/WebhookRuleServiceTest.java` → `webhook/service/`

### 用户故事 3 的 import 语句更新

- [ ] T032 [US3] 更新所有 `src/main` Java 文件中的 `import` 语句，将旧的包路径引用替换为新的模块包路径（跨模块引用规则见 plan.md §3.4）。重点检查：`sync` → `storage`、`sync` → `transcode`、`transcode` → `storage`、`webhook` → `storage`、`webhook` → `sync`、所有模块 → `common`
- [ ] T033 [US3] 更新所有 `src/test` Java 文件中的 `import` 语句，与 `src/main` 的包路径变更保持一致
- [ ] T034 [US3] 更新 `src/main/resources/META-INF/spring/org.springframework.boot.EnvironmentPostProcessor` 文件中的全限定类名：`top.lldwb.alistmediasync.config.PasswordEncryptionPostProcessor` → `top.lldwb.alistmediasync.common.config.PasswordEncryptionPostProcessor`
- [ ] T035 [US3] 验证编译：执行 `./mvnw clean compile` 确保无编译错误，执行 `./mvnw clean test` 确保所有 16 个测试通过

**检查点**：代码目录重组完成 — 所有文件位于正确的功能模块子包中，编译和测试全部通过。此时可以开始功能变更。

---

## 阶段 3：用户故事 1 — 配置文件仅支持明文密码（优先级：P1）🎯 MVP

**目标**：简化 `PasswordEncryptionPostProcessor`，移除 `{bcrypt}` 前缀检测逻辑，始终执行加密。所有配置值视为明文，静默加密，不输出日志。简化 `AuthInterceptor`，移除明文回退逻辑。

**独立测试**：在 `application.yaml` 中设置明文密码，启动系统验证静默加密且可正常登录。设置 `{bcrypt}` 旧格式密码，验证系统将其视为明文重新加密（旧密码失效）。空密码时验证 WARN 日志输出。

### 用户故事 1 的测试

- [ ] T036 [P] [US1] 在 `src/test/java/top/lldwb/alistmediasync/common/config/PasswordEncryptionPostProcessorTest.java` 中更新测试：移除 `shouldSkipAlreadyEncryptedPassword`、`shouldReEncryptInvalidBcryptPrefix`、`isPlainPassword` 相关测试方法；新增 `shouldTreatBcryptPrefixAsPlainPassword`（验证 `{bcrypt}$2a$10$...` 被当作明文重新加密）、`shouldGenerateDifferentHashOnEachStartup`（验证两次调用 `encryptIfPlain` 生成不同哈希值）、`shouldLogWarnWhenPasswordEmpty`（验证空密码输出 WARN）、`shouldNotLogInfoOnEncryption`（验证加密过程无 INFO 日志）
- [ ] T037 [P] [US1] 在 `src/test/java/top/lldwb/alistmediasync/common/interceptor/` 中新增 `AuthInterceptorTest.java` 单元测试：验证 `{bcrypt}` 格式密码的 BCrypt 验证通过、验证非 `{bcrypt}` 格式密码被拒绝（401 + ERROR 日志）

### 用户故事 1 的实现

- [ ] T038 [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/config/PasswordEncryptionPostProcessor.java` 中简化 `postProcessEnvironment()` 方法：移除 `{bcrypt}` 前缀检测分支（第 61-69 行）、移除 `BCRYPT_HASH_PATTERN` 常量、移除 `isPlainPassword()` 方法、移除 `encryptIfPlain()` 中的跳过逻辑（第 102-105 行）。简化后流程：空值检测 → WARN 日志 → BCrypt 加密 → 注入 Environment。移除 INFO 日志 `"检测到明文密码，已自动加密为 BCrypt 格式。"`
- [ ] T039 [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/config/PasswordEncryptionPostProcessor.java` 中更新 Javadoc：移除对 `{bcrypt}` 前缀支持的描述，更新为"所有配置值均视为明文，每次启动使用随机盐值加密"
- [ ] T040 [US1] 在 `src/main/java/top/lldwb/alistmediasync/common/interceptor/AuthInterceptor.java` 中移除明文回退比较分支（第 87-93 行，`if (!storedPassword.startsWith("{bcrypt}"))` 块），新增防御性检查：如果密码到达时不是 `{bcrypt}` 格式，记录 ERROR 日志并返回 401
- [ ] T041 [US1] 在 `specs/005-standalone-bootstrap/contracts/password-encryption.md` 中更新契约文档：移除 `{bcrypt}` 前缀检测步骤，更新日志输出说明（加密过程静默，仅空密码时输出 WARN）

**检查点**：密码加密简化完成 — 配置文件仅接受明文密码，每次启动随机盐值加密，静默执行，`{bcrypt}` 旧格式不再被识别

---

## 阶段 4：用户故事 2 — 原目录转码选项（优先级：P2）

**目标**：为转码任务创建接口添加 `sameDirectoryTranscode` 布尔选项。启用时 `targetFilePath` 可选，后端自动使用源文件所在目录作为目标路径。

**独立测试**：创建转码任务勾选"原目录转码"不填目标路径，验证输出文件在源文件目录。不勾选时不填目标路径应返回校验错误。

### 用户故事 2 的测试

- [ ] T042 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/transcode/service/TranscodeServiceTest.java` 中新增测试方法：`shouldAutoSetTargetPathWhenSameDirectoryTranscodeTrue`（验证 `sameDirectoryTranscode=true` 时自动使用源文件目录）、`shouldUseSpecifiedTargetPathWhenSameDirectoryTranscodeFalse`（验证 `sameDirectoryTranscode=false` 时使用指定路径）、`shouldIgnoreTargetPathWhenSameDirectoryTranscodeTrue`（验证 `sameDirectoryTranscode=true` 时忽略传入的 `targetFilePath`）、`shouldSetTargetPathToRootWhenSourceInRoot`（验证源文件在根目录时目标路径为 `/`）
- [ ] T043 [P] [US2] 在 `src/test/java/top/lldwb/alistmediasync/transcode/controller/` 中新增 `TranscodeTaskControllerTest.java` @WebMvcTest 测试：验证 `sameDirectoryTranscode=true` 且 `targetFilePath` 为空时创建成功（200）、验证 `sameDirectoryTranscode=false` 且 `targetFilePath` 为空时返回校验错误（400）

### 用户故事 2 的实现

- [ ] T044 [US2] 在 `src/main/java/top/lldwb/alistmediasync/transcode/dto/transcode/TranscodeTaskCreateDTO.java` 中新增 `sameDirectoryTranscode` 字段（`boolean`，默认 `false`），将 `targetFilePath` 的 `@NotBlank` 移除（改为可选字段，校验逻辑移至 Service 层）
- [ ] T045 [US2] 在 `src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeService.java` 的 `createTask()` 方法中新增原目录转码逻辑：当 `sameDirectoryTranscode=true` 时，调用已有的 `getDirPath(sourcePath)` 方法自动计算目标路径，忽略传入的 `targetPath` 参数；当 `sameDirectoryTranscode=false` 且 `targetPath` 为空时，抛出 `IllegalArgumentException`（"未启用原目录转码时，目标路径为必填"）
- [ ] T046 [US2] 在 `src/main/java/top/lldwb/alistmediasync/transcode/controller/TranscodeTaskController.java` 的 `create()` 方法中传递 `dto.getSameDirectoryTranscode()` 给 `transcodeService.createTask()`，更新 `createTask()` 方法签名新增 `sameDirectoryTranscode` 参数

**检查点**：原目录转码选项完成 — 勾选后无需手动填写目标路径，后端自动使用源文件目录

---

## 阶段 5：润色与跨领域关注点

**目的**：最终验证和清理

- [ ] T047 执行 `./mvnw clean test` 验证所有测试通过（含新增和修改的测试）
- [ ] T048 启动应用，使用明文密码登录管理界面验证密码加密简化功能正常
- [ ] T049 创建转码任务验证原目录转码选项功能正常（`sameDirectoryTranscode=true` 和 `false` 两种场景）
- [ ] T050 检查代码目录结构符合 FR-010 定义，每个功能子包的类文件数量不超过 15 个（SC-005），验证 PasswordEncryptionPostProcessor.java 代码行数较重构前减少 ≥30%（SC-002）
- [ ] T051 更新项目根目录 `README.md`：反映新的 5 模块包结构（common/storage/sync/transcode/webhook）、密码配置仅支持明文且每次启动随机盐值加密（`{bcrypt}` 预加密格式已废弃）、新增原目录转码选项（`sameDirectoryTranscode`）的使用说明（constitution 原则 IX）

---

## 依赖与执行顺序

### 阶段依赖

- **阶段 1（设置）**：无依赖 — 可立即开始（创建目录结构）
- **阶段 2（US3 代码目录重组）**：依赖阶段 1 — 阻塞所有功能变更
- **阶段 3（US1 密码加密简化）**：依赖阶段 2（需要新的包路径）
- **阶段 4（US2 原目录转码）**：依赖阶段 2（需要新的包路径），可与阶段 3 并行
- **阶段 5（润色）**：依赖阶段 3 和阶段 4 完成

### 用户故事依赖

- **US3（P2）**：必须在 US1 和 US2 之前完成（代码目录重组是所有功能变更的前置条件）
- **US1（P1）**：可在 US3 完成后开始 — 不依赖 US2
- **US2（P2）**：可在 US3 完成后开始 — 不依赖 US1

### 每个用户故事内部

- 测试必须在实现之前编写并失败（TDD 原则）
- DTO 先于 Service
- Service 先于 Controller
- 核心实现先于契约文档更新

### 并行机会

- 阶段 2 中 T004-T010（common 模块迁移）可并行执行
- 阶段 2 中 T012-T015（storage）、T017-T020（sync）、T022-T025（transcode）、T027-T030（webhook）各组内可并行
- 阶段 3 中 T036 和 T037 可并行（不同测试文件）
- 阶段 4 中 T042 和 T043 可并行（不同测试文件）
- 阶段 3（US1）和阶段 4（US2）可完全并行执行

---

## 并行示例：阶段 2 — 代码目录重组

```bash
# 5 个模块的迁移可并行启动（不同文件，无依赖）：
任务 T003-T010："common 模块迁移（8 个文件组）"
任务 T011-T015："storage 模块迁移（5 个文件组）"
任务 T016-T020："sync 模块迁移（5 个文件组）"
任务 T021-T025："transcode 模块迁移（5 个文件组）"
任务 T026-T030："webhook 模块迁移（5 个文件组）"
任务 T031："测试文件迁移（16 个文件）"

# 所有模块迁移完成后：
任务 T032-T033："import 语句更新（80 个文件）"
任务 T034："SPI 文件更新"
任务 T035："编译和测试验证"
```

## 并行示例：阶段 3 + 阶段 4

```bash
# US1 和 US2 可完全并行（不同模块，无交叉依赖）：
# 开发者 A — US1 密码加密简化：
任务 T036："更新 PasswordEncryptionPostProcessorTest"
任务 T037："新增 AuthInterceptorTest"
任务 T038："简化 PasswordEncryptionPostProcessor"
任务 T039："更新 Javadoc"
任务 T040："简化 AuthInterceptor"
任务 T041："更新密码加密契约文档"

# 开发者 B — US2 原目录转码：
任务 T042："新增 TranscodeService 原目录转码测试"
任务 T043："新增 TranscodeTaskController @WebMvcTest"
任务 T044："更新 TranscodeTaskCreateDTO"
任务 T045："更新 TranscodeService.createTask()"
任务 T046："更新 TranscodeTaskController.create()"
```

---

## 实现策略

### MVP 优先（US3 + US1）

1. 完成阶段 1：创建目录结构
2. 完成阶段 2：代码目录重组（US3）— 关键阻塞步骤
3. 完成阶段 3：密码加密简化（US1）— 核心安全变更
4. **停止并验证**：独立测试 US3 + US1
5. 如果就绪则部署/演示

### 增量交付

1. 完成阶段 1 + 阶段 2 → 代码目录重组完成，编译测试通过
2. 添加 US1 → 独立测试 → 部署/演示（密码加密简化）
3. 添加 US2 → 独立测试 → 部署/演示（原目录转码）
4. 润色 → 最终验证 → 正式发布

### 并行团队策略

多个开发人员时：

1. 团队一起完成阶段 1 + 阶段 2（代码目录重组需要协调）
2. 阶段 2 完成后：
   - 开发人员 A：US1（密码加密简化）
   - 开发人员 B：US2（原目录转码选项）
3. 各故事独立完成并集成

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应能独立完成和测试
- 实现前验证测试失败（TDD）
- 每个任务或逻辑组后提交
- 在任何检查点停止以独立验证故事
- 避免：模糊的任务、同文件冲突、破坏独立性的跨故事依赖
- **代码目录重组注意事项**：使用 `git mv` 而非普通文件移动以保留 Git 历史；80 个文件的 import 更新建议使用 IDE 的 Move Class 重构功能自动处理交叉引用
- **密码加密注意事项**：`PasswordEncryptionPostProcessor` 简化后，旧版 `{bcrypt}` 格式密码将失效，需在发布说明中明确告知用户升级后需将密码改为明文
- **原目录转码注意事项**：`targetFilePath` 从 `@NotBlank` 改为可选后，校验逻辑移至 Service 层（`sameDirectoryTranscode=false` 时必填），避免自定义 Validator 的复杂度
