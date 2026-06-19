# 任务：转码临时文件可配置后缀

**输入**：来自 `/specs/002-transcode-temp-suffix-config/` 的设计文档

**前提条件**：plan.md（必需）、spec.md（用户故事必需）

**注意**：本功能是对 Feature 001（`specs/001-alist-media-sync`）转码子系统的增强。001 和 002 在**同一迭代周期中并行实现**——002 的任务代码直接内聚到 001 的 `TranscodeService`、`CleanupService`、`AppProperties` 等文件中。

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（例如，US1、US2、US3）
- 在描述中包含确切的文件路径

## 路径约定

- **项目根目录**：`C:\Users\Administrator\Documents\GitHub\AList-Media-Sync\`
- Java 源代码：`src/main/java/top/lldwb/alistmediasync/`
- 资源文件：`src/main/resources/`

---

## 阶段 1：设置（共享基础设施）

**目的**：扩展配置绑定以支持转码临时文件的相关配置项

> **注意**：此阶段依赖 Feature 001 的阶段 1-2（`AppProperties` 基础类已创建、`application.yaml` 基础结构已就绪）

- [ ] T001 [P] 在 `src/main/java/top/lldwb/alistmediasync/config/AppProperties.java` 中扩展 `Transcode` 内部类：添加字段 `tempSuffix`（String，默认 `.tmp`）、`tempDir`（String，默认 `${java.io.tmpdir}/alist-media-sync/transcode`）、`maxConcurrentTranscode`（int，默认 `Runtime.getRuntime().availableProcessors()`）、`maxSuffixLength`（int，50）；所有字段添加 `@NotEmpty`/`@NotNull`/`@Min`/`@Max` 验证注解和中文注释
- [ ] T002 在 `src/main/resources/application.yaml` 中添加转码临时文件配置项：在 `app.transcode` 命名空间下新增 `temp-suffix: ${TRANSCODE_TEMP_SUFFIX:.tmp}`、`temp-dir: ${TRANSCODE_TEMP_DIR:${java.io.tmpdir}/alist-media-sync/transcode}`、`max-concurrent-transcode: ${TRANSCODE_MAX_CONCURRENT:${app.pool.max-size}}`、`max-suffix-length: 50`；所有配置项添加中文注释说明用途和合法值范围（FR-001、FR-002、FR-007）

---

## 阶段 2：用户故事 1 — 配置自定义临时文件后缀（优先级：P1）🎯 MVP

**目标**：实现临时文件后缀的配置化——用户通过配置文件设置后缀（如 `.lldwb`），系统自动处理点号前缀，校验配置合法性

**独立测试**：修改 `application.yaml` 中的 `app.transcode.temp-suffix` → 重启系统 → 触发转码任务 → 验证生成的临时文件使用配置的后缀

**注意**：US1 是 Feature 002 的核心——可配置的后缀是所有后续功能的基础。

### 用户故事 1 的实现

- [ ] T003 [US1] 在 `src/main/java/top/lldwb/alistmediasync/util/TempSuffixValidator.java` 中创建后缀配置校验器（FR-004）：
  - 实现 `ApplicationRunner`，`getOrder()` 返回 `HIGHEST_PRECEDENCE + 1`（在 Feature 002 的 `AppProperties` 绑定后、其他 ApplicationRunner 之前执行）
  - `run()` 方法中从 `AppProperties.transcode` 读取 `tempSuffix`：
    - `null` 或空字符串 → 设置回退值为 `.tmp`，记录 `log.warn("临时文件后缀未配置，使用默认值：{}", ".tmp")`
    - 仅为 `"."` → 设置回退值为 `.tmp`，记录 `log.warn("临时文件后缀仅为点号，使用默认值：{}", ".tmp")`
    - 包含 `/` 或 `\` → 抛出 `IllegalArgumentException("临时文件后缀包含非法字符：[/, \\]")`，Spring Boot 退出（exit code 1）
    - 长度超过 `maxSuffixLength` → 截断到前 50 字符，记录 `log.warn("临时文件后缀超过最大长度 50，已截断为：{}", truncated)`
    - 不以 `.` 开头 → 自动补充点号前缀
  - 添加中文注释说明每条校验规则和对应的 FR
- [ ] T004 [US1] 在 `src/main/java/top/lldwb/alistmediasync/util/TempFileManager.java` 中实现临时文件管理器——后缀处理部分：
  - `normalizeSuffix(String rawSuffix)`：确保后缀以点号开头、无路径分隔符、长度合法；返回规范化后的后缀字符串
  - `buildTempFileName(String originalName, String suffix)`：生成 `{原文件名}.{原扩展名}.{uuid}.{后缀}` 格式的临时文件名，UUID 使用 `UUID.randomUUID().toString().substring(0, 8)` 前 8 位以确保简洁（FR-003、FR-006）
  - 添加中文注释说明后缀处理规则和 UUID 唯一性保证

**检查点**：此时，用户故事 1 应完全功能可用——管理员可通过配置文件自定义临时文件后缀，系统自动校验和处理

---

## 阶段 3：用户故事 2 — 临时文件服务器本地暂存（优先级：P1）

**目标**：实现转码输出的本地暂存→上传→清理完整生命周期，含磁盘空间检查、文件权限控制、上传失败重试

**独立测试**：触发转码任务 → 检查临时目录是否存在 `.tmp` 中间文件 → 验证上传后本地文件被删除 → 模拟上传失败验证重试

**注意**：此阶段的大多数任务直接操作 Feature 001 的 `TranscodeService.java` 和 `CleanupService.java`。如果 Feature 001 尚在构建中，此阶段可以与 001 的阶段 4（转码实现）合并执行。

### 用户故事 2 的实现

#### 临时文件管理工具

- [ ] T005 [P] [US2] 在 `TempFileManager.java`（T004 中已创建）中扩展文件操作方法（FR-005、FR-008、FR-009、FR-014）：
  - `createTempFile(Path tempDir, String originalFileName, String suffix)`：创建临时目录（如不存在）、生成唯一临时文件名、创建空文件、设置文件权限（POSIX 0600）、返回临时文件路径
  - `renameToFinal(Path tempFilePath, String targetExtension)`：将 `video.mp4.uuid.lldwb` 重命名为 `video.mp4.uuid.mp3`（去掉临时后缀，替换为目标格式扩展名）。**注意**：最终文件仍包含 UUID 前缀以确保唯一性，上传到 AList 时再去除 UUID 恢复原始文件名（FR-014）
  - `deleteQuietly(Path filePath)`：删除文件，失败则记录 WARN 日志不抛异常（FR-009）
  - `setFilePermissions(Path filePath)`：POSIX 系统设为 `PosixFilePermissions.fromString("rw-------")`（0600），Windows 降级处理
  - `setDirectoryPermissions(Path dirPath)`：POSIX 系统设为 `PosixFilePermissions.fromString("rwx------")`（0700），Windows 降级处理
  - 所有方法添加中文注释
- [ ] T006 [P] [US2] 在 `src/main/java/top/lldwb/alistmediasync/util/DiskSpaceChecker.java` 中创建磁盘空间检查工具（FR-011）：
  - `checkSufficient(Path tempDir, long estimatedOutputSize)`：
    1. 获取 `Files.getFileStore(tempDir).getUsableSpace()`
    2. 计算 `requiredSpace = (long)(estimatedOutputSize * 1.5)`
    3. 若 `usableSpace < requiredSpace`，抛出 `InsufficientDiskSpaceException(String.format("临时目录磁盘空间不足：需要至少 %d 字节（%.2f MB），可用 %d 字节（%.2f MB）", requiredSpace, requiredSpace/1048576.0, usableSpace, usableSpace/1048576.0))`
    4. 否则返回 `true`
  - `estimateOutputSize(long sourceDurationMs, int targetBitrate)`：`(sourceDurationMs / 1000) * (targetBitrate / 8)` 字节
  - 添加边界情况注释（Btrfs/ZFS 的 `getUsableSpace()` 可能不准确）
- [ ] T007 [US2] 在 `src/main/java/top/lldwb/alistmediasync/service/TranscodeService.java`（Feature 001 T040）中集成临时文件本地暂存策略（FR-005）：
  - 在 `doTranscode()` 方法中：
    1. 转码前调用 `DiskSpaceChecker.checkSufficient(tempDir, estimatedSize)`
    2. 调用 `TempFileManager.createTempFile(tempDir, originalFileName, tempSuffix)` 创建临时输出路径
    3. `Encoder().encode(new MultimediaObject(source), tempFile, attrs, listener)` 将输出写入临时文件
    4. 转码成功后调用 `TempFileManager.renameToFinal(tempFile, targetFormat)` 重命名去掉后缀
    5. 调用 `AListClient.uploadFile()` 上传（上传时去除文件名中的 UUID 前缀，仅保留原始文件名）
    6. 上传成功后调用 `TempFileManager.deleteQuietly(finalFile)` 清理本地文件
    7. 上传失败则保留本地文件，记录失败状态（FR-010）
  - 添加中文注释说明完整的临时文件生命周期

#### 并发上限控制

- [ ] T008 [US2] 在 `TranscodeService.java` 中添加并发上限控制（FR-007）：
  - 在构造函数或 `@PostConstruct` 中初始化 `Semaphore(maxConcurrentTranscode)`
  - 在 `transcodeFile()` 方法（`@Async` 入口）中包裹 `semaphore.acquire()` / `semaphore.release()`
  - 添加中文注释说明为什么使用 Semaphore 而非仅依赖线程池大小

#### 启动清理与目录初始化

- [ ] T009 [US2] 在 `src/main/java/top/lldwb/alistmediasync/service/CleanupService.java`（Feature 001 T053）中实现启动时临时文件清理与目录初始化（FR-012、FR-013）：
  - `@EventListener(ApplicationReadyEvent.class)` 或实现 `ApplicationRunner`（`getOrder()` 返回 `LOWEST_PRECEDENCE`，确保在所有初始化完成后执行）
  - `startupCleanup()`：
    1. 从 `AppProperties.transcode` 读取 `tempDir` 和 `tempSuffix`
    2. 检查 `tempDir` 是否存在 → 否：调用 `Files.createDirectories()` 创建，设置 POSIX 0700 权限；若创建失败（无权限等），记录 ERROR 日志并**抛出异常阻止启动**（FR-012）
    3. 是：调用 `Files.walk(tempDir)` 遍历目录
    4. 过滤所有以 `tempSuffix` 结尾的常规文件
    5. **无条件**删除每个匹配的残留文件（不论关联任务状态）
    6. 记录清理数量到日志：`log.info("启动时清理残留临时文件完成，共清理 {} 个文件", deletedCount)`
    7. 删除失败的文件记录 WARN：`log.warn("清理残留临时文件失败：{}，原因：{}", path, e.getMessage())`
  - 添加中文注释说明为什么"无条件全部清理"（澄清 Q4：系统重启意味着所有运行中任务已中断，临时文件不再有效）
- [ ] T010 [US2] 在 `TranscodeTaskController.java`（Feature 001 T041）中添加手动清理接口：
  - `DELETE /api/transcode-tasks/cleanup-temp`：调用 `CleanupService.startupCleanup()` 相同的清理逻辑（`WalkFileTree` 扫描 + 过滤后缀 + 删除），返回清理数量
  - 需要认证（通过 AuthInterceptor）
  - 添加中文注释说明接口用途

#### 手动重试上传

- [ ] T011 [US2] 在 `TranscodeTaskController.java`（Feature 001 T041）中添加手动重试上传接口（FR-010）：
  - `POST /api/transcode-tasks/{taskId}/retry-upload`：
    1. 查询 `TranscodeTask`，确认状态为 FAILED 且错误原因为上传失败
    2. 检查临时文件（`transcodeTask.tempFilePath`）是否仍存在 → 否：返回 HTTP 404 错误"临时文件已不存在，请重新执行转码"
    3. 是：重新执行上传流程（`AListClient.uploadFile()` → 成功删除 / 失败保留）
    4. 需要认证
    5. 返回重试结果（成功/失败+原因）

**检查点**：此时，用户故事 1 和 2 应完全功能可用——转码输出先写入本地临时目录，使用配置的后缀，完成后上传至 AList，上传失败可手动重试，启动时自动清理残留文件

---

## 阶段 4：用户故事 3 — 临时文件后缀配置校验（优先级：P2）

**目标**：完善后缀配置的启动校验逻辑，覆盖所有非法输入场景（空字符串、仅点号、路径分隔符、超长字符串）

**独立测试**：在 `application.yaml` 中填入各种非法后缀值 → 重新启动系统 → 验证系统是否给出明确错误提示或回退默认值

**注意**：US3 的校验逻辑已在 `TempSuffixValidator`（T003）中实现。此阶段聚焦于完善校验覆盖范围和添加集成测试。

### 用户故事 3 的实现

- [ ] T012 [US3] 完善 `TempSuffixValidator.java`（T003 中已创建）的校验逻辑：
  - 添加空白字符检测：后缀仅包含空格/制表符等 → 回退默认值 `.tmp`，记录 WARN
  - 添加特殊字符检测：后缀包含 `\0`（空字符）→ 拒绝启动，错误消息"临时文件后缀包含空字符"
  - 添加多重路径分隔符检测：包含 `..`（上级目录遍历）→ 拒绝启动，错误消息"临时文件后缀包含非法字符：[..]"
  - 确保校验失败时 `IllegalArgumentException` 的错误消息清晰、可操作（中文："临时文件后缀配置无效：[原因]。请在 application.yaml 中修改 app.transcode.temp-suffix 配置项。"）
- [ ] T013 [P] [US3] 在 `src/test/java/top/lldwb/alistmediasync/util/TempSuffixValidatorTest.java` 中编写后缀校验器的单元测试：
  - `testValidSuffixWithDot`：`.lldwb` → 通过
  - `testValidSuffixWithoutDot`：`lldwb` → 自动补充为 `.lldwb`
  - `testEmptySuffixFallback`：`""` → 回退 `.tmp` + WARN
  - `testNullSuffixFallback`：`null` → 回退 `.tmp` + WARN
  - `testDotOnlySuffixFallback`：`.` → 回退 `.tmp` + WARN
  - `testSuffixWithSlashRejected`：`/tmp` → `IllegalArgumentException`
  - `testSuffixWithBackslashRejected`：`\temp` → `IllegalArgumentException`
  - `testSuffixWithDotDotRejected`：`..` → `IllegalArgumentException`
  - `testOverlyLongSuffixTruncated`：51 个字符的字符串 → 截断到 50 字符 + WARN
  - `testWhitespaceOnlySuffixFallback`：`   ` → 回退 `.tmp` + WARN
  - 测试类添加 `@SpringBootTest` 或纯单元测试（Mock `AppProperties`）
- [ ] T014 [P] [US3] 在 `src/test/java/top/lldwb/alistmediasync/util/TempFileManagerTest.java` 中编写临时文件管理器的单元测试：
  - `testBuildTempFileNameWithDotSuffix`：后缀 `.lldwb` → 生成 `video.mp4.xxxxxxxx.lldwb`
  - `testBuildTempFileNameWithoutDotSuffix`：后缀 `lldwb` → 规范化后生成 `video.mp4.xxxxxxxx.lldwb`
  - `testTempFileNameContainsUuid`：两次调用 `buildTempFileName` 生成不同 UUID
  - `testCreateTempFileAndPermissions`：创建临时文件并验证文件名格式正确、文件存在
  - `testRenameToFinalRemovesSuffix`：`video.mp4.a1b2c3d4.lldwb` → `video.mp4.a1b2c3d4.mp3`（后缀替换）
  - `testDeleteQuietlySuccess`：创建临时文件 → 删除 → 验证文件不存在
  - `testDeleteQuietlyFailure`：模拟删除失败 → 验证不抛异常、记录 WARN 日志
  - 使用 JUnit 5 `@TempDir` 注解创建临时测试目录

**检查点**：此时，所有用户故事应各自独立功能可用——后缀配置经过完整校验，非法输入有明确反馈

---

## 阶段 5：润色与跨领域关注点

**目的**：确保与 Feature 001 的代码集成正确、补充文档、执行端到端验证

- [ ] T015 在 `src/main/java/top/lldwb/alistmediasync/util/DiskSpaceChecker.java` 中补充预估输出大小计算的边界情况处理：源文件为 0 字节 → 跳过空间检查、预估大小超过 `Long.MAX_VALUE / 2` → 使用 `Long.MAX_VALUE` 避免溢出、`getUsableSpace()` 返回 0（某些文件系统/JVM 实现可能返回 0）→ 记录 WARN 日志"无法获取可用磁盘空间，跳过空间检查"但不阻塞转码
- [ ] T016 [P] 在 `src/main/resources/application.yaml` 中为 `app.transcode.*` 配置项添加完整的文档级中文注释：每个配置项包含用途说明、默认值、合法值范围、Docker 环境变量映射示例
- [ ] T017 [P] 在 `src/test/java/top/lldwb/alistmediasync/service/CleanupServiceTest.java` 中编写清理服务的集成测试：
  - `testStartupCleanupCreatesDirIfMissing`：临时目录不存在 → 自动创建 + 权限设置
  - `testStartupCleanupDeletesStaleFiles`：在临时目录创建多个 `.lldwb` 和 `.mp3` 文件 → 启动清理仅删除 `.lldwb` 后缀文件
  - `testStartupCleanupLogsCount`：创建 5 个残留文件 → 清理后验证日志输出"共清理 5 个文件"
  - 使用 JUnit 5 `@TempDir` 和 Spring Boot Test
- [ ] T018 [P] 在 `src/test/java/top/lldwb/alistmediasync/service/TranscodeServiceDiskCheckTest.java` 中编写磁盘空间检查的集成测试：
  - `testSufficientSpace`：临时目录空间充足 → 检查通过
  - `testInsufficientSpace`：使用 Mock 模拟 `getUsableSpace()` 返回极小值 → 抛出 `InsufficientDiskSpaceException`
  - `testConcurrencyLimitEnforced`：设置 `maxConcurrentTranscode=2`，同时提交 5 个转码任务 → 验证同时执行的任务数 ≤ 2
- [ ] T019 执行端到端验证（参考 `specs/002-transcode-temp-suffix-config/spec.md` 中的验收场景）：
  1. 修改 `temp-suffix: .lldwb` → 重启应用 → 触发转码 → 验证临时文件后缀为 `.lldwb`
  2. 未配置后缀 → 重启应用 → 触发转码 → 验证临时文件后缀为 `.tmp`（默认值）
  3. 转码过程中检查临时目录 → 验证文件存在且后缀正确
  4. 转码完成 → 验证文件上传至 AList 目标路径（文件名不含临时后缀）
  5. 模拟上传失败（断网） → 验证临时文件保留、手动重试上传成功
  6. 重启应用 → 验证临时目录残留文件被清理
  7. 设置 `temp-suffix: /` → 验证应用拒绝启动并输出明确错误

---

## 依赖与执行顺序

### 阶段依赖

- **设置（阶段 1）**：依赖 Feature 001 的阶段 2（`AppProperties`、`application.yaml` 基础结构已就绪）
- **用户故事 1（阶段 2）**：依赖设置完成。US1 是 Feature 002 的核心——其他故事依赖可配置的后缀
- **用户故事 2（阶段 3）**：依赖 US1（后缀配置）+ Feature 001 的阶段 4（`TranscodeService` 基础结构）
- **用户故事 3（阶段 4）**：依赖 US1（后缀校验器）+ US2 不影响 US3——可并行
- **润色（阶段 5）**：依赖所有用户故事完成

### 用户故事依赖关系

```
US1 — 配置自定义临时文件后缀（P1）🎯 MVP
    ├──→ US2 — 临时文件服务器本地暂存（P1）
    └──→ US3 — 临时文件后缀配置校验（P2）
```

-US2 和 US3 可以并行（US3 的校验逻辑在 `TempSuffixValidator` 中，US2 的文件操作在 `TempFileManager` 中，两者无代码冲突）

### 每个用户故事内部

- T003（校验器）先于 T004（文件管理器中的后缀处理）——但两者可并行（不同文件）
- T005-T006 可并行（不同文件）
- T007（集成到 TranscodeService）依赖 T005（TempFileManager 全部方法就绪）
- T009（启动清理）可与 T007 并行

### 并行机会

- T005、T006 可完全并行（不同工具类）
- T013、T014 可完全并行（不同测试类）
- T016、T017、T018 可完全并行（不同文件）
- T001 和 T002 操作同一文件（`AppProperties.java`），不可并行

### 与 Feature 001 的交叉依赖

| 本任务 ID | 依赖的 001 任务 | 说明 |
|----------|---------------|------|
| T001 | 001 T002（AppProperties 创建） | 扩展已存在的 AppProperties |
| T002 | 001 T018（application.yaml 扩展） | 在已有配置文件中添加新配置项 |
| T005（TempFileManager 扩展） | 001 T034（TempFileManager 创建） | 在 001 中已创建的工具类基础上扩展 |
| T007（TranscodeService 集成） | 001 T040（TranscodeService 核心） | 在 001 的转码引擎中集成临时文件策略 |
| T009（CleanupService 集成） | 001 T053（CleanupService 创建） | 在 001 的清理服务中实现具体逻辑 |
| T010、T011（Controller 端点） | 001 T041（TranscodeTaskController） | 在 001 的 Controller 中添加接口 |

**推荐实现顺序**：先完成 001 阶段 2-3（基础 + 同步），使应用结构就绪 → 再并行推进 001 阶段 4 + 002 阶段 2-3（转码 + 临时文件）。

---

## 实现策略

### MVP 优先（仅用户故事 1 + 用户故事 2）

1. 完成阶段 1：设置（配置扩展）
2. 完成阶段 2：用户故事 1（可配置后缀）
3. 完成阶段 3：用户故事 2（本地暂存核心流程）
4. **停止并验证**：触发转码任务 → 验证临时文件使用配置后缀、本地暂存、上传后清理
5. 如果就绪则部署/演示

### 增量交付

1. 完成设置 → 配置属性可用
2. 添加用户故事 1 → 后缀可配置化（MVP 中最小的可交付增量）
3. 添加用户故事 2 → 完整的本地暂存→上传→清理流程（Feature 002 核心价值）
4. 添加用户故事 3 → 配置校验完善（防御性质量加固）
5. 完成润色 → 测试覆盖 + 端到端验证

### 与 Feature 001 协同策略

Feature 001 和 002 的推荐协同执行方式：

1. **001 阶段 1-3** → 项目骨架 + 存储引擎 + 同步引擎（不依赖 002）
2. **001 阶段 4 + 002 全部阶段** → 转码引擎 + 临时文件管理（代码内聚到相同文件）
3. **001 阶段 5-7** → Webhook + 监控 + 润色（不依赖 002）

002 的任务量较小（约 20 个），大多数代码直接写入 001 创建的文件中。建议：在 001 的 `TranscodeService` 实现时就完整集成 002 的所有需求，而非分两次修改同一个文件。

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 002 不创建独立的包或模块——所有新增代码内聚到 001 的目录结构中
- 002 的任务文件包含了明确的"依赖 001 任务 X"引用，确保协同实现时不遗漏
- 每个用户故事应能独立完成和测试（在 001 的基础设施已就绪前提下）
- 所有注释、日志消息、提交信息使用简体中文（章程 IV）
- 避免：模糊的任务、同文件冲突、破坏独立性的跨故事依赖
