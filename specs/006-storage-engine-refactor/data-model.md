# 数据模型：存储引擎重构与体验优化

**日期**：2026-06-20 | **功能**：006-storage-engine-refactor

## 实体变更概览

| 实体 | 变更类型 | 说明 |
|------|---------|------|
| StorageEngine | 修改 | 移除 username，新增 engineType，baseUrl 改为可选 |
| TranscodeTask | 修改 | TranscodeStatus 扩展为 7 状态，bitrate 改为可选，新增 tempSourcePath |
| WebhookRule | 修改 | 新增 recordingEngine 关联，targetPath 改为 targetFilePath |
| FileEntry | 新增 | 策略模式统一文件信息 DTO |
| DirectoryEntry | 新增 | 策略模式统一目录信息 DTO |

---

## StorageEngine（存储引擎）

### 变更

| 字段 | 操作 | 说明 |
|------|------|------|
| `username` | **删除** | 不再需要用户名字段（FR-001） |
| `engineType` | **新增** | `EngineType` 枚举：`ALIST`、`LOCAL`，创建后不可更改（FR-005） |
| `baseUrl` | **修改** | nullable=true，仅 ALIST 类型必填；LOCAL 类型为 null |
| `encryptedToken` | **修改** | nullable=true，仅 ALIST 类型必填；LOCAL 类型为 null |
| `localPath` | **新增** | 本地文件系统目录路径，仅 LOCAL 类型必填；ALIST 类型为 null |

### 字段定义

```java
@Entity
@Table(name = "storage_engine")
public class StorageEngine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** 引擎类型：ALIST / LOCAL，创建后不可更改 */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EngineType engineType = EngineType.ALIST;

    /** AList 服务器基础 URL（仅 ALIST 类型） */
    @Column(length = 500)
    private String baseUrl;

    /** AList API 令牌（仅 ALIST 类型，AES-256-GCM 加密） */
    @Column(length = 1000)
    @Convert(converter = CryptoConverter.class)
    private String encryptedToken;

    /** 本地文件系统目录路径（仅 LOCAL 类型） */
    @Column(length = 1000)
    private String localPath;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EngineStatus status = EngineStatus.OFFLINE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum EngineType { ALIST, LOCAL }
    public enum EngineStatus { ONLINE, OFFLINE, ERROR }
}
```

### 验证规则

- `engineType = ALIST` 时：`baseUrl` 和 `encryptedToken` 必填
- `engineType = LOCAL` 时：`localPath` 必填，且目录必须已存在且可读写
- `engineType` 创建后不可更改（编辑时禁用）
- `localPath` 目录不存在时返回错误，不自动创建

### 数据迁移

- 现有记录 `engineType` 默认设为 `ALIST`
- 现有 `username` 字段数据保留但不再使用（不删除列，避免迁移风险）

---

## TranscodeTask（转码任务）

### 变更

| 字段 | 操作 | 说明 |
|------|------|------|
| `status` | **修改** | TranscodeStatus 扩展为 7 状态模型（FR-007） |
| `bitrate` | **修改** | nullable=true，不填时使用系统默认码率（FR-009） |
| `tempSourcePath` | **新增** | 已下载源文件的临时路径，供转码/上传失败重试使用 |
| `SCANNING` | **删除** | 不再需要扫描状态（三步流程无扫描步骤） |

### TranscodeStatus 枚举

```java
public enum TranscodeStatus {
    PENDING(0),          // 待处理
    DOWNLOADING(1),      // 下载中
    DOWNLOAD_FAILED(2),  // 下载失败
    TRANSCODING(3),      // 转码中
    TRANSCODE_FAILED(4), // 转码失败
    UPLOADING(5),        // 上传中
    UPLOAD_FAILED(6),    // 上传失败
    COMPLETED(7);        // 完成
}
```

### 状态转换规则

```text
合法转换：
  PENDING → DOWNLOADING
  DOWNLOADING → TRANSCODING
  DOWNLOADING → DOWNLOAD_FAILED
  DOWNLOAD_FAILED → DOWNLOADING（重试，删除部分下载文件）
  TRANSCODING → UPLOADING
  TRANSCODING → TRANSCODE_FAILED
  TRANSCODE_FAILED → TRANSCODING（重试，保留源临时文件）
  UPLOADING → COMPLETED
  UPLOADING → UPLOAD_FAILED
  UPLOAD_FAILED → UPLOADING（重试，保留源+输出临时文件）
```

### 临时文件生命周期

| 状态 | tempSourcePath | tempFilePath | 说明 |
|------|---------------|-------------|------|
| DOWNLOADING | 正在写入 | null | 下载源文件到临时目录 |
| DOWNLOAD_FAILED | 删除 | null | 不保留部分下载文件 |
| TRANSCODING | 保留 | 正在写入 | 从源临时文件转码 |
| TRANSCODE_FAILED | 保留 | null/保留 | 保留源文件供重试 |
| UPLOADING | 保留 | 保留 | 上传转码输出 |
| UPLOAD_FAILED | 保留 | 保留 | 保留两者供重试 |
| COMPLETED | 删除 | 删除 | 上传成功后立即清理 |

### 数据迁移

- 现有 `PENDING` → 保持 `PENDING`
- 现有 `SCANNING` → 映射为 `DOWNLOADING`
- 现有 `TRANSCODING` → 保持 `TRANSCODING`
- 现有 `UPLOADING` → 保持 `UPLOADING`
- 现有 `COMPLETED` → 保持 `COMPLETED`
- 现有 `FAILED` → 映射为 `TRANSCODE_FAILED`（最常见失败场景）

---

## WebhookRule（Webhook 规则）

### 变更

| 字段 | 操作 | 说明 |
|------|------|------|
| `recordingEngine` | **新增** | 录播存储引擎关联（ManyToOne），用户显式选择（FR-010） |
| `recordingPath` | **新增** | 录播文件路径（FR-010） |
| `targetPath` | **重命名** | → `targetFilePath`，语义更明确（FR-011） |

### 字段定义

```java
@Entity
@Table(name = "webhook_rule")
public class WebhookRule {
    // ... 原有字段 ...

    /** 录播存储引擎（源端，用户显式选择） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recording_engine_id")
    private StorageEngine recordingEngine;

    /** 录播文件路径 */
    @Column(length = 1000)
    private String recordingPath;

    /** 目标存储引擎（原有 targetEngine） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_engine_id", nullable = false)
    private StorageEngine targetEngine;

    /** 目标文件路径（原 targetPath） */
    @Column(nullable = false, length = 1000)
    private String targetFilePath;
}
```

### 验证规则

- `action = TRANSCODE_ONLY` 或 `BOTH` 时：`recordingEngine` 和 `recordingPath` 必填
- `action = SYNC_ONLY` 时：`recordingEngine` 和 `recordingPath` 可为空

---

## FileEntry（文件信息 DTO）

策略模式统一的文件信息返回结构，非持久化实体。

```java
public record FileEntry(
    String name,
    String path,
    boolean isDirectory,
    long size,
    LocalDateTime modifiedTime
) {}
```

## DirectoryEntry（目录信息 DTO）

树状目录浏览组件使用的目录信息，非持久化实体。

```java
public record DirectoryEntry(
    String name,
    String path,
    boolean hasChildren
) {}
```

---

## 关系图

```text
StorageEngine (1) ←──→ (N) SyncTask.sourceEngine
StorageEngine (1) ←──→ (N) SyncTask.targetEngine
StorageEngine (1) ←──→ (N) WebhookRule.recordingEngine  [新增]
StorageEngine (1) ←──→ (N) WebhookRule.targetEngine
StorageEngine (1) ←──→ (N) TranscodeTask (via sourceEngineId/targetEngineId)

WebhookRule (1) ←──→ (N) WebhookEvent
WebhookRule (1) ←──→ (N) TranscodeTask
SyncTask (1) ←──→ (N) TranscodeTask
SyncTask (1) ←──→ (N) TaskExecution
```
