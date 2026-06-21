# frontend/types/ — TypeScript 类型定义

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和前端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

前端 API 类型定义，与后端 DTO/VO 字段一一对应。

## 作用

- `api.ts`：定义所有 API 接口类型（StorageEngineVO, SyncTaskVO, TranscodeTaskVO, WebhookRuleVO 等）、枚举映射（ENGINE_TYPE_LABELS, TRANSCODE_STATUS_LABELS 等）
- `TranscodeTaskCreateDTO` 含 `sameDirectoryTranscode` 字段

## 模块关联

- 被 **api/client.ts** 使用（ApiResult 类型）
- 被所有 **pages/** 和 **components/forms/** 使用
- 修改后端 DTO 时需同步更新此文件
