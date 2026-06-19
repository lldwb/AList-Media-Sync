# 实现计划：转码临时文件可配置后缀

**分支**：`002-transcode-temp-suffix-config` | **日期**：2026-06-19 | **规格**：[spec.md](./spec.md)

**输入**：来自 `/specs/002-transcode-temp-suffix-config/spec.md` 的功能规格

**参考实现**：
- specs/001-alist-media-sync/plan.md（本功能是对 001 转码子系统的增强）
- videoConversionMonitor（`https://github.com/lldwb/videoConversionMonitor`）：`.lldwb` 临时后缀 + 文件重命名模式

## 摘要

为 AList-Media-Sync 转码子系统添加完整的临时文件管理能力。核心交付物包括：(1) 可配置的临时文件后缀（通过 `application.yaml` 中的 `app.transcode.temp-suffix` 配置项），(2) 启动时自动扫描和清理残留临时文件，(3) 转码输出先写入本地临时目录再上传至 AList 目标存储，(4) 上传失败后的手动重试接口，(5) 可配置的并发转码任务上限，(6) 临时文件/目录权限控制（POSIX 0700/0600），(7) 转码前磁盘空间预估检查，(8) 配置文件后缀校验。本功能是对 001 转码子系统的增强，与 001 在同一迭代周期中并行实现。

## 技术上下文

**语言/版本**：Java 21（LTS）

**主要依赖**：Spring Boot 4.1.0（同 001）、Spring Data JPA、H2、Lombok

**存储**：H2 内嵌数据库（与 001 共享同一数据库实例 `alist_media_sync`）；临时文件存储在服务器本地磁盘（路径由 `app.transcode.temp-dir` 配置）

**测试**：Spring Boot Test（`@SpringBootTest`、JUnit 5）

**目标平台**：x86-64 Linux 服务器 / Docker 容器，部分功能需区分 POSIX/Windows 文件权限

**项目类型**：模块增强（对 001 已有基础设施的配置化和可靠性改进）

**性能目标**：启动清理 < 5 秒（10000 个以下残留文件场景），手动清理接口响应 < 3 秒

**约束**：必须与 001 的转码引擎无缝集成（代码内聚到同一 Service 层），配置文件校验在应用启动阶段执行，失败则拒绝启动

**规模/范围**：约 8-10 个 Java 文件修改/新增，约 10 个配置项新增到 `application.yaml`，2-3 个新增测试文件

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

| 章程原则 | 适用性 | 状态 |
|---------|--------|------|
| I. 分层架构（不可协商） | **适用** — 配置校验在 Service 层，清理接口在 Controller 层，临时文件操作通过工具类封装 | ✅ 通过 |
| II. 数据完整性优先 | **适用** — 上传失败状态持久化到 TaskExecution，清理操作记录日志 | ✅ 通过 |
| III. RESTful API 契约优先 | **适用** — 手动清理和重试接口通过 REST API 暴露，使用统一 `ApiResult<T>` 响应 | ✅ 通过 |
| IV. 中文优先 | **适用** — 所有配置注释、日志消息使用简体中文 | ✅ 通过 |
| V. 测试不可省略 | **适用** — 配置校验、临时文件创建/清理、磁盘空间检查均需单元测试 | ✅ 通过 |
| VI. 简洁至上（YAGNI） | **适用** — 不引入第三方文件清理框架（Java NIO 足够），不引入磁盘配额库（简单百分比计算即可） | ✅ 通过 |

**门禁结果**：全部通过，无违规项。

## 项目结构

### 文档（本功能）

```text
specs/002-transcode-temp-suffix-config/
├── spec.md              # 已有：功能规格
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── research.md          # 阶段 0 输出
├── data-model.md        # 阶段 1 输出
├── quickstart.md        # 阶段 1 输出
├── contracts/           # 阶段 1 输出
└── tasks.md             # 阶段 2 输出（/speckit-tasks 命令创建）
```

### 源代码（仓库根目录）

由于 001 和 002 并行实现，以下文件结构展示 002 专属的新增/修改内容（所有文件在 001 的目录结构之上工作）：

```text
src/main/java/top/lldwb/alistmediasync/
│
├── config/
│   └── AppProperties.java                 # 修改：添加 temp-suffix、temp-dir、max-concurrent-transcode 属性
│
├── service/
│   ├── TranscodeService.java              # 修改：集成临时文件后缀策略、并发上限控制、磁盘空间检查
│   └── CleanupService.java               # 修改：添加启动扫描清理、手动清理接口（如 001 中已创建）
│
├── controller/
│   └── TranscodeTaskController.java       # 修改：添加手动重试上传、手动清理接口
│
└── util/
    ├── TempFileManager.java               # 新增：临时文件创建/重命名/删除/权限设置
    ├── DiskSpaceChecker.java              # 新增：磁盘空间检查（1.5 倍预估大小）
    └── TempSuffixValidator.java           # 新增：启动时后缀配置校验
│
src/main/resources/
└── application.yaml                       # 修改：添加 app.transcode.temp-suffix、temp-dir、max-concurrent-transcode 配置项
```

**结构决策**：本功能不创建独立的包或模块——所有新增代码内聚到 001 已建立的包结构中。`TempFileManager`、`DiskSpaceChecker`、`TempSuffixValidator` 作为 `util` 包的工具类，由 `TranscodeService` 和 `CleanupService` 调用。这符合单一职责原则（工具类各司其职）且避免过度抽象。

## 技术设计决策

### 1. 配置项设计

**新增 `application.yaml` 配置项**（`app.transcode.*` 命名空间）：

```yaml
app:
  transcode:
    # 转码临时文件后缀（默认 .tmp）
    # 若不以点号开头，系统自动补充
    # 禁止包含路径分隔符（/ \），禁止为空或仅点号
    temp-suffix: ${TRANSCODE_TEMP_SUFFIX:.tmp}

    # 临时文件存储目录（默认系统临时目录子目录）
    # Docker 场景下建议挂载独立卷
    temp-dir: ${TRANSCODE_TEMP_DIR:${java.io.tmpdir}/alist-media-sync/transcode}

    # 最大并发转码任务数（默认 CPU 核心数）
    max-concurrent-transcode: ${TRANSCODE_MAX_CONCURRENT:${app.transcode.pool.max-size}}

    # 临时文件后缀最大长度（超过截断并警告）
    max-suffix-length: 50

  pool:
    max-size: 32  # 线程池最大线程数（与 max-concurrent-transcode 联动）
```

### 2. 临时文件命名与生命周期

**命名格式**：`{原文件名}.{原始扩展名}.{uuid}.{临时后缀}`

**示例**：`my_video.mp4.a1b2c3d4.lldwb`

**为什么包含 UUID**：多任务并发场景下，两个任务可能同时转换同名文件（来自不同源路径但目标路径相同），UUID 确保临时文件名全局唯一。冲突检测和策略处理在目标路径层面执行（由 001 的冲突策略负责）。

**完整生命周期**：

```
[扫描阶段] → 源文件检测为视频格式
    ↓
[磁盘检查] → 临时目录可用空间 >= 预估输出大小 × 1.5
    ↓
[转码中] → 输出写入 temp-dir/原文件名.ext.uuid.temp-suffix
    ↓ (EncoderProgressListener 持续更新进度)
[转码完成] → 临时文件重命名为 原文件名.ext.uuid.mp3（去掉临时后缀，替换为目标格式扩展名）
    ↓
[上传] → 通过 AListClient.uploadFile() 上传到目标存储
    ↓
[成功] → 删除本地文件（原文件名.ext.uuid.mp3）
    ↓
[失败] → 保留本地文件，TaskExecution 记录上传失败状态 + 原因
         用户可通过 POST /api/transcode-tasks/{taskId}/retry-upload 手动重试
         系统重启时 CleanupService 扫描并删除所有带 temp-suffix 的残留文件
```

### 3. 启动校验流程

`TempSuffixValidator` 实现 `ApplicationRunner` 或 `@PostConstruct`，在校验失败时抛出异常阻止 Spring 容器启动。

**校验规则**（按 FR-004）：

1. 后缀为 `null` 或空字符串 → **回退为默认值 `.tmp`**，记录 WARN 日志
2. 后缀仅为点号（`.`） → **回退为默认值 `.tmp`**，记录 WARN 日志
3. 后缀包含路径分隔符（`/` 或 `\`） → **拒绝启动**，错误消息："临时文件后缀包含非法字符：[/, \]"
4. 后缀长度超过 `max-suffix-length`（50） → 截断到前 50 个字符，记录 WARN 日志
5. 后缀不以点号开头 → 自动补充点号前缀
6. 如未能获取默认后缀且用户配置无效 → **拒绝启动**

**Docker 环境特别考虑**：Docker 容器内 `java.io.tmpdir` 通常为 `/tmp`。`docker-compose.yml` 已配置 `stop_grace_period: 35s`，足够完成优雅关闭。临时文件目录应在 Docker 命名卷中或绑定挂载中，以便运维人员手动检查。

### 4. 磁盘空间检查

`DiskSpaceChecker.checkSufficient(Path tempDir, long estimatedOutputSize)`：

```java
public boolean checkSufficient(Path tempDir, long estimatedOutputSize) {
    long requiredSpace = (long) (estimatedOutputSize * 1.5);
    long usableSpace = tempDir.toFile().getUsableSpace();
    if (usableSpace < requiredSpace) {
        throw new InsufficientDiskSpaceException(
            String.format("临时目录磁盘空间不足：需要至少 %d 字节，可用 %d 字节", requiredSpace, usableSpace)
        );
    }
    return true;
}
```

**预估输出大小**：MP3 128kbps 输出 ≈ 源文件时长（秒） × 128000 / 8 字节。时长通过 `MultimediaObject.getInfo().getDuration()` 获取（毫秒）。

**边界情况**：`File.getUsableSpace()` 在某些 Linux 文件系统（Btrfs、ZFS）返回不准确值。作为一种近似检测，它仍优于不做任何检查。

### 5. 文件权限控制

**Java 实现跨平台权限设置**：

```java
// POSIX (Linux/macOS)
if (System.getProperty("os.name").toLowerCase().contains("linux")
    || System.getProperty("os.name").toLowerCase().contains("mac")) {
    // 临时目录：0700 (仅运行用户可读写执行)
    Set<PosixFilePermission> dirPerms = PosixFilePermissions.fromString("rwx------");
    Files.setPosixFilePermissions(tempDir, dirPerms);

    // 临时文件：0600 (仅运行用户可读写)
    Set<PosixFilePermission> filePerms = PosixFilePermissions.fromString("rw-------");
    Files.setPosixFilePermissions(tempFile, filePerms);
}

// Windows: 通过 NIO FileAttribute 限制（仅当前用户）
// 注：Java 在 Windows 上的权限控制有限，依赖 NTFS ACL 需 JNA 库
// 此处通过 java.nio.file.attribute 设置基本属性
```

**Windows 限制说明**：Java 标准库在 Windows 上无法精确设置"仅当前用户可读写"的等效权限（需要 Win32 API 的 ACL 操作）。当前实现通过 `Files.createDirectories` 和 `Files.createFile` 的默认行为（继承父目录权限）作为近似方案。如未来需要精确权限控制，可引入 JNA 平台依赖（当前按 YAGNI 原则不予引入）。

### 6. 残留文件清理

**启动清理**（`CleanupService.startupCleanup()`）：

```java
@EventListener(ApplicationReadyEvent.class)
public void startupCleanup() {
    Path tempDir = Path.of(appProperties.getTranscode().getTempDir());
    if (!Files.exists(tempDir)) {
        // 创建目录（FR-012）
        Files.createDirectories(tempDir);
        setDirectoryPermissions(tempDir);
        log.info("临时文件目录已创建：{}", tempDir);
        return;
    }

    String suffix = appProperties.getTranscode().getTempSuffix();
    long deletedCount = Files.walk(tempDir)
        .filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().endsWith(suffix))
        .peek(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                log.warn("清理残留临时文件失败：{}，原因：{}", path, e.getMessage());
            }
        })
        .count();

    log.info("启动时清理残留临时文件完成，共清理 {} 个文件", deletedCount);
}
```

**手动清理接口**（`DELETE /api/transcode-tasks/cleanup-temp`）：执行相同的清理逻辑，需要认证。

**为什么无条件清理**（按 FR-013 + 澄清 Q4）：系统重启意味着所有"运行中"转码任务被标记为"中断"，临时文件不再有任何关联的有效任务——全部清理，用户需重新执行转码。

### 7. 并发上限控制

`TranscodeService` 中添加信号量控制：

```java
private final Semaphore semaphore;

public TranscodeService(AppProperties appProperties) {
    this.semaphore = new Semaphore(appProperties.getTranscode().getMaxConcurrentTranscode());
}

public CompletableFuture<TranscodeResult> transcodeFile(TranscodeCandidate candidate) {
    semaphore.acquire();  // 阻塞直到有空位
    try {
        return self.doTranscode(candidate);
    } finally {
        semaphore.release();
    }
}
```

**为什么用 Semaphore 而不是仅依赖线程池大小**：线程池的 `max-size` 控制的是执行线程数，但 `@Async` 方法内部的 FFmpeg 进程可能已完成但上传阶段仍在进行（此时线程已归还池但"逻辑任务"仍在执行）。Semaphore 提供逻辑层面的并发控制。

## 数据模型概要

本功能不引入新的数据库实体。修改现有实体/配置：

**TranscodeConfig（`AppProperties.Transcode` 内部类）**：
| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `tempSuffix` | String | `.tmp` | 临时文件后缀（自动补充点号前缀） |
| `tempDir` | String | `${java.io.tmpdir}/alist-media-sync/transcode` | 临时文件目录路径 |
| `maxConcurrentTranscode` | int | CPU 核心数 | 最大并发转码任务数 |
| `maxSuffixLength` | int | 50 | 后缀最大长度 |

**TempFile（概念实体，不持久化）**：
| 字段 | 类型 | 说明 |
|------|------|------|
| `localPath` | Path | 本地文件完整路径 |
| `uuid` | String | 唯一标识（避免并发冲突） |
| `taskId` | Long | 关联的 TranscodeTask ID |
| `createdAt` | LocalDateTime | 创建时间 |
| `fileSize` | long | 文件大小（字节） |
| `status` | enum | 转码中 / 转码完成 / 上传中 / 上传完成 / 失败 |

## 与其他规格的关系

| 规格 | 关系 | 说明 |
|------|------|------|
| 001-alist-media-sync | **父规格（并行实现）** | 本功能是对 001 转码子系统的增强。001 的 `TranscodeService`、`CleanupService`、`AppProperties` 直接集成 002 的所有需求。两者应在同一实现周期中完成，避免先构建再重构。 |
| 003-docker-deploy | **基础设施依赖** | 临时目录路径和权限配置需考虑 Docker 容器环境（例如 `/app/transcode-temp` 挂载独立卷）。Docker 部署文件已就绪，无需修改。 |

## 复杂性追踪

| 偏离项 | 说明 |
|--------|------|
| FR-007 默认值偏离 | spec.md FR-007 要求默认值为"CPU 核心数"（动态获取），plan 的实际配置默认值为 `app.pool.max-size`（静态 32，通过 `${TRANSCODE_MAX_CONCURRENT:${app.pool.max-size}}` 引用）。**理由**：Docker 容器内 `Runtime.getRuntime().availableProcessors()` 可能返回宿主机核心数而非容器分配的核心数，导致默认值偏大。使用静态默认值 32 作为保守上限，用户可通过环境变量 `TRANSCODE_MAX_CONCURRENT` 覆盖。在非容器环境部署时，建议用户在 `application.yaml` 中显式设置此值为 CPU 核心数。
