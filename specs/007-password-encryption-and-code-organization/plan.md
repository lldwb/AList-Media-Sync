# 实现计划：密码加密优化与代码目录重组

**分支**：`007-password-encryption-and-code-organization` | **日期**：2026-06-20 | **规格**：[spec.md](./spec.md)

**输入**：来自 `/specs/007-password-encryption-and-code-organization/spec.md` 的功能规格

## 摘要

本功能包含 3 个独立变更：

1. **密码加密简化（P1）**：简化 `PasswordEncryptionPostProcessor`，移除 `{bcrypt}` 前缀检测逻辑，始终执行加密。所有配置值视为明文，静默加密，不输出日志。简化 `AuthInterceptor`，移除明文回退逻辑。
2. **原目录转码选项（P2）**：为转码任务创建接口添加 `sameDirectoryTranscode` 布尔选项。启用时 `targetFilePath` 可选，后端自动使用源文件所在目录作为目标路径。
3. **代码目录重组（P2）**：将 64 个 `src/main` Java 文件和 16 个 `src/test` Java 文件按 5 个功能模块（common/storage/sync/transcode/webhook）重新组织包结构。

## 技术上下文

- **语言/版本**：Java 21
- **主要框架**：Spring Boot 4.1.0，Spring Security Crypto（仅 BCrypt）
- **项目类型**：单体 Spring Boot 应用
- **约束**：分层架构不可协商、所有写操作 `@Transactional`、API 统一 `ApiResult<T>` 格式、中文优先

## 变更范围

### 变更 1：密码加密简化（4 个文件修改 + 1 个文件删除）

**影响的文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `config/PasswordEncryptionPostProcessor.java` | 修改 | 移除 `{bcrypt}` 前缀检测、`BCRYPT_HASH_PATTERN`、`isPlainPassword()` 方法、`encryptIfPlain()` 中的跳过逻辑。`postProcessEnvironment()` 简化为：空值检测 → BCrypt 加密 → 注入 Environment |
| `interceptor/AuthInterceptor.java` | 修改 | 移除明文回退比较分支（第 87-93 行），仅保留 `{bcrypt}` 格式验证 |
| `PasswordEncryptionPostProcessorTest.java` | 修改 | 移除 `shouldSkipAlreadyEncryptedPassword`、`shouldReEncryptInvalidBcryptPrefix`、`isPlainPassword` 测试；新增"旧版 `{bcrypt}` 密码被当作明文重新加密"测试 |
| `specs/005-standalone-bootstrap/contracts/password-encryption.md` | 修改 | 更新契约：移除 `{bcrypt}` 前缀检测步骤，更新日志输出说明 |

**关键设计决策**：

```
postProcessEnvironment(environment, application)  // 简化后流程
    │
    ├─ 1. 读取 app.auth.password 属性
    │     └─ 若环境变量 APP_AUTH_PASSWORD 存在，以环境变量值为准
    │
    ├─ 2. 判断值类型
    │     ├─ null / 空字符串 → 记录 WARN → 结束
    │     └─ 其他值（包括含 {bcrypt} 前缀的旧格式）→ 全部视为明文 → 继续步骤 3
    │
    ├─ 3. BCrypt 加密
    │     ├─ BCryptPasswordEncoder 默认构造（随机盐值）
    │     └─ 拼接 "{bcrypt}" + 哈希
    │
    └─ 4. 更新 Environment（仅内存，不回写文件）
          └─ 以最高优先级注入 MapPropertySource
```

**AuthInterceptor 简化**：
- 移除：`if (!storedPassword.startsWith("{bcrypt}"))` 分支（明文回退，第 87-93 行）
- 保留：`{bcrypt}` 格式的 BCrypt.checkpw 验证
- 补充防御性检查：如果密码到达时仍不是 `{bcrypt}` 格式（极端情况），记录 ERROR 并拒绝

### 变更 2：原目录转码选项（3 个文件修改）

**影响的文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `dto/transcode/TranscodeTaskCreateDTO.java` | 修改 | 新增 `sameDirectoryTranscode`（boolean，默认 false），`targetFilePath` 的 `@NotBlank` 改为条件校验 |
| `service/TranscodeService.java` | 修改 | `createTask()` 方法签名新增 `sameDirectoryTranscode` 参数，启用时自动从 `sourceFilePath` 提取目录路径作为 `targetFilePath` |
| `controller/TranscodeTaskController.java` | 修改 | `create()` 方法传递 `dto.getSameDirectoryTranscode()` 给 Service |

**`createTask()` 逻辑新增**：
```java
// 在 createTask() 中
if (sameDirectoryTranscode) {
    targetPath = extractDirPath(sourcePath); // 从源文件路径提取目录部分
    // 忽略传入的 targetPath
}
```

**提取目录路径的辅助方法**：
```java
private String extractDirPath(String fullPath) {
    int idx = fullPath.lastIndexOf('/');
    return idx > 0 ? fullPath.substring(0, idx) : "/";
}
```
注意：该方法已存在于 `TranscodeService` 中（`getDirPath()`），可直接复用。

**DTO 校验策略**：
- `sourceFilePath`：保持 `@NotBlank`
- `targetFilePath`：移除 `@NotBlank`，改为自定义校验或 Service 层校验（`sameDirectoryTranscode=false` 时必填）
- `targetFormat`：保持 `@NotNull`
- `sameDirectoryTranscode`：新增字段，默认 `false`

### 变更 3：代码目录重组（80 个文件移动 + 80 次 package/import 更新）

#### 3.1 目标包结构

```
top.lldwb.alistmediasync/
├── AListMediaSyncApplication.java          ← 根包，不动
│
├── common/                                 ← 通用模块
│   ├── config/
│   │   ├── AppProperties.java
│   │   ├── AsyncConfig.java
│   │   ├── WebMvcConfig.java
│   │   ├── RestClientConfig.java
│   │   ├── GlobalExceptionHandler.java
│   │   └── PasswordEncryptionPostProcessor.java
│   ├── dto/
│   │   ├── ApiResult.java
│   │   └── DashboardStatsVO.java
│   ├── entity/
│   │   └── CryptoConverter.java              ← JPA 通用加密转换器（FR-016）
│   ├── interceptor/
│   │   └── AuthInterceptor.java
│   ├── util/
│   │   ├── MagicBytesDetector.java
│   │   ├── TempFileManager.java
│   │   ├── DiskSpaceChecker.java
│   │   ├── TempSuffixValidator.java
│   │   └── ServerAddressLogger.java
│   ├── client/
│   │   └── AListClient.java
│   ├── service/
│   │   ├── DashboardService.java
│   │   └── CleanupService.java
│   └── controller/
│       └── DashboardController.java
│
├── storage/                                ← 存储引擎模块
│   ├── entity/
│   │   └── StorageEngine.java
│   ├── repository/
│   │   └── StorageEngineRepository.java
│   ├── dto/
│   │   └── storage/
│   │       ├── StorageEngineCreateDTO.java
│   │       ├── StorageEngineUpdateDTO.java
│   │       └── StorageEngineVO.java
│   ├── service/
│   │   ├── StorageEngineService.java
│   │   └── engine/
│   │       ├── StorageEngineStrategy.java
│   │       ├── AListStorageStrategy.java
│   │       └── LocalStorageStrategy.java
│   └── controller/
│       └── StorageEngineController.java
│
├── sync/                                   ← 同步任务模块
│   ├── entity/
│   │   ├── SyncTask.java
│   │   └── TaskExecution.java
│   ├── repository/
│   │   ├── SyncTaskRepository.java
│   │   └── TaskExecutionRepository.java
│   ├── dto/
│   │   └── sync/
│   │       ├── SyncTaskCreateDTO.java
│   │       ├── SyncTaskUpdateDTO.java
│   │       ├── SyncTaskVO.java
│   │       ├── SyncProgressVO.java
│   │       ├── TaskExecutionVO.java
│   │       ├── DirectoryEntryVO.java
│   │       └── FileEntry.java
│   ├── service/
│   │   ├── SyncService.java
│   │   ├── SyncTaskManageService.java
│   │   └── ScheduleService.java
│   └── controller/
│       └── SyncTaskController.java
│
├── transcode/                              ← 转码模块
│   ├── entity/
│   │   └── TranscodeTask.java
│   ├── repository/
│   │   └── TranscodeTaskRepository.java
│   ├── dto/
│   │   └── transcode/
│   │       ├── TranscodeTaskCreateDTO.java
│   │       └── TranscodeTaskVO.java
│   ├── service/
│   │   ├── TranscodeService.java
│   │   ├── TranscodeFileProcessor.java
│   │   ├── TranscodeCandidate.java
│   │   └── TranscodeResult.java
│   └── controller/
│       └── TranscodeTaskController.java
│
└── webhook/                                ← Webhook 模块
    ├── entity/
    │   ├── WebhookRule.java
    │   └── WebhookEvent.java
    ├── repository/
    │   ├── WebhookRuleRepository.java
    │   └── WebhookEventRepository.java
    ├── dto/
    │   └── webhook/
    │       ├── WebhookRuleCreateDTO.java
    │       ├── WebhookRuleVO.java
    │       └── WebhookEventVO.java
    ├── service/
    │   ├── WebhookService.java
    │   └── WebhookRuleService.java
    └── controller/
        ├── WebhookController.java
        ├── WebhookRuleController.java
        └── WebhookEventController.java
```

#### 3.2 测试目录结构（镜像）

```
src/test/java/top/lldwb/alistmediasync/
├── AListMediaSyncApplicationTests.java     ← 根包
│
├── common/
│   ├── config/
│   │   └── PasswordEncryptionPostProcessorTest.java
│   ├── util/
│   │   └── ServerAddressLoggerTest.java
│   ├── service/
│   │   ├── DashboardServiceTest.java
│   │   └── CleanupServiceTest.java
│   └── controller/
│       └── DashboardControllerTest.java
│
├── storage/
│   ├── service/
│   │   ├── StorageEngineServiceTest.java
│   │   └── engine/
│   │       ├── AListStorageStrategyTest.java
│   │       └── LocalStorageStrategyTest.java
│
├── sync/
│   └── service/
│       ├── SyncServiceTest.java
│       ├── SyncTaskManageServiceTest.java
│       └── ScheduleServiceTest.java
│
├── transcode/
│   └── service/
│       ├── TranscodeServiceTest.java
│       └── TranscodeFileProcessorTest.java
│
└── webhook/
    └── service/
        ├── WebhookServiceTest.java
        └── WebhookRuleServiceTest.java
```

#### 3.3 重组操作步骤

重组操作采用 **纯文件移动 + package/import 更新** 策略，不涉及任何业务逻辑变更：

1. **创建目标目录结构**：在 `src/main` 和 `src/test` 下分别创建 5 个子包的完整目录树
2. **移动源文件**：使用 `git mv` 将每个文件移动到目标位置（保留 Git 历史）
3. **更新 package 声明**：每个文件的 `package top.lldwb.alistmediasync.xxx;` 改为 `package top.lldwb.alistmediasync.<模块>.<分层>;`
4. **更新 import 语句**：所有交叉引用更新为新的全限定类名
5. **更新 SPI 文件**：`META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor` 全限定类名更新为 `top.lldwb.alistmediasync.common.config.PasswordEncryptionPostProcessor`
6. **验证编译**：`./mvnw clean compile test`

#### 3.4 交叉引用关系

重组后跨模块引用规则（必须通过完整包路径 import）：
- `sync` → `storage`（SyncTask 引用 StorageEngine）
- `sync` → `transcode`（SyncService 调用 TranscodeService）
- `transcode` → `storage`（TranscodeService 引用 StorageEngine/StorageEngineStrategy）
- `webhook` → `storage`（WebhookRule 引用 StorageEngine）
- `webhook` → `sync`（WebhookService 调用 SyncService）
- `common` ← 所有模块（config、util、dto/ApiResult、interceptor）
- `common/dto/DashboardStatsVO` → 聚合 `storage`/`sync`/`transcode`/`webhook` 的 Repository

## 验证方案

### 变更 1 验证（密码加密简化）

1. 启动应用，使用明文密码 `admin123` 登录管理界面 → 应成功
2. 检查日志 → 不应出现"检测到明文密码，已自动加密为 BCrypt 格式"
3. 将 `app.auth.password` 设为空，启动 → 应出现 WARN 日志
4. 将 `app.auth.password` 设为 `{bcrypt}$2a$10$...`，启动 → 使用原密码登录应失败（验证 `{bcrypt}` 不再被识别）
5. 运行 `PasswordEncryptionPostProcessorTest` → 所有测试通过
6. 两次启动用同一密码登录 → 均成功（每次启动独立加密，不同盐值）

### 变更 2 验证（原目录转码）

1. 创建转码任务：`sameDirectoryTranscode=true`，不填 `targetFilePath` → 应成功创建，目标路径自动设为源文件目录
2. 创建转码任务：`sameDirectoryTranscode=true`，同时填了 `targetFilePath` → 应忽略 `targetFilePath`，使用源文件目录
3. 创建转码任务：`sameDirectoryTranscode=false`，不填 `targetFilePath` → 应返回校验错误
4. 创建转码任务：`sameDirectoryTranscode=false`，填了 `targetFilePath` → 应按指定路径创建
5. 源文件在根目录 `/test.flv`，勾选原目录转码 → 目标路径为 `/`

### 变更 3 验证（代码目录重组）

1. `./mvnw clean compile` → 无编译错误
2. `./mvnw clean test` → 所有 16 个测试通过
3. 检查目录结构 → 符合 FR-010 定义
4. 检查 `META-INF/spring/...EnvironmentPostProcessor` → 全限定类名正确
5. 启动应用 → 组件扫描正常，所有功能可用

## 复杂性追踪

| 需求 | 复杂点 | 缓解 |
|------|--------|------|
| FR-005 | AuthInterceptor 明文回退的防御性保留 | 添加 ERROR 日志 + 401 拒绝（防御性），确保 `PasswordEncryptionPostProcessor` 总是在拦截器之前执行 |
| FR-008 | targetFilePath 从 @NotBlank 到条件必填 | 在 Service 层而非 DTO 层做条件校验，避免自定义 Validator 的复杂度 |
| FR-017/018 | 80 个文件的 import 更新 | 使用 IDE 的重构能力（Move Class）自动更新引用，而非手动修改 |
