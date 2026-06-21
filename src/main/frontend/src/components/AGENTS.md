# frontend/components/ — UI 组件

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和前端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

可复用的 UI 组件，分为布局组件、表单组件和基础 UI 组件。

## 结构

- **layout/**：`AppLayout.tsx`（侧边栏+内容区布局）、`Sidebar.tsx`（导航菜单，含路由高亮）
- **forms/**：`EngineForm.tsx`、`SyncTaskForm.tsx`、`TranscodeTaskForm.tsx`、`WebhookRuleForm.tsx`
- **ui/**：`DataTable.tsx`（通用数据表格）、`StatusBadge.tsx`、`LoadingSpinner.tsx`、`EmptyState.tsx`、`ErrorBanner.tsx`、`ConfirmDialog.tsx`、`CronBuilder.tsx`、`DirectoryTreeSelector.tsx`

## 模块关联

- 被所有 **pages/** 使用
- **TranscodeTaskForm** 需要修改：源目录转码时隐藏目标路径和目标引擎选择，文案"源目录转码"
