# frontend/types/ — TypeScript 类型定义

## 功能

前端 API 类型定义，与后端 DTO/VO 字段一一对应。

## 作用

- `api.ts`：定义所有 API 接口类型（StorageEngineVO, SyncTaskVO, TranscodeTaskVO, WebhookRuleVO 等）、枚举映射（ENGINE_TYPE_LABELS, TRANSCODE_STATUS_LABELS 等）
- `TranscodeTaskCreateDTO` 含 `sameDirectoryTranscode` 字段

## 模块关联

- 被 **api/client.ts** 使用（ApiResult 类型）
- 被所有 **pages/** 和 **components/forms/** 使用
- 修改后端 DTO 时需同步更新此文件
