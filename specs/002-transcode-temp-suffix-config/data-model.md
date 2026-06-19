# 数据模型：转码临时文件可配置后缀

**功能**：转码临时文件可配置后缀 | **日期**：2026-06-19

## 概述

本功能不引入新的数据库实体。所有"实体"均为应用配置抽象。本文档描述这些配置实体的结构与关系。

## 配置实体

### 1. TranscodeConfig（`AppProperties.Transcode` 内部类）

应用配置对象，在 `application.yaml` 中定义，通过 `AppProperties` 绑定。这些配置影响转码临时文件的创建、存储和管理。

| 字段 | 类型 | 默认值 | 验证规则 | 说明 |
|------|------|--------|---------|------|
| `tempSuffix` | `String` | `.tmp` | 非空、不含路径分隔符 `/` `\`、不含 `..`、不含空字符 `\0`、不以仅为 `.`、长度 ≤ 50 | 转码临时文件后缀标识（自动补充点号前缀） |
| `tempDir` | `String` | `${java.io.tmpdir}/alist-media-sync/transcode` | 必须为有效的可写路径 | 临时文件存储目录路径 |
| `maxConcurrentTranscode` | `int` | `${app.pool.max-size}` (32) | ≥ 1 | 最大并发转码任务数 |
| `maxSuffixLength` | `int` | `50` | ≥ 1 | 后缀最大字符长度 |

### 2. TempFile（概念实体，不持久化）

代表转码过程中产生的临时输出文件。`TempFileManager` 在文件系统中管理此实体。

| 字段 | 类型 | 说明 |
|------|------|------|
| `localPath` | `Path` | 本地临时文件完整路径 |
| `uuid` | `String` | 唯一标识（UUID 前 8 位，避免并发冲突） |
| `taskId` | `Long` | 关联的 `TranscodeTask` 实体 ID |
| `originalFileName` | `String` | 原始源文件名（如 `my_video.mp4`） |
| `finalExtension` | `String` | 最终目标扩展名（如 `.mp3`） |
| `createdAt` | `LocalDateTime` | 文件创建时间 |
| `fileSize` | `long` | 当前文件大小（字节） |
| `status` | `TempFileStatus` | 状态枚举：TRANSCODING / COMPLETED / UPLOADING / UPLOADED / FAILED |

## 文件命名规则

```
格式：{原文件名}.{源扩展名}.{uuid}.{临时后缀}

示例：
  源文件: my_video.mp4
  临时文件: my_video.mp4.a1b2c3d4.lldwb
  转码完成（重命名）: my_video.mp4.a1b2c3d4.mp3
  上传到 AList（去 uuid）: my_video.mp3
```

## 文件生命周期

```
创建 → 转码中 → 转码完成（重命名去后缀） → 上传中 → 上传成功（删除）→ 不存在
                                                      → 上传失败（保留）→ 手动重试
                                                                         → 重启清理（删除）
```

## 配置绑定

所有配置通过 `@ConfigurationProperties(prefix="app.transcode")` 绑定。Spring Boot Relaxed Binding 支持下划线/连字符/大小写变体。

```yaml
app:
  transcode:
    temp-suffix: .tmp
    temp-dir: ${java.io.tmpdir}/alist-media-sync/transcode
    max-concurrent-transcode: 32
    max-suffix-length: 50
```
