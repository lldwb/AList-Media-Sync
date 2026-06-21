# frontend/utils/ — 工具函数

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和前端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

前端通用工具函数。

## 内容

- `format.ts`：日期时间格式化、状态标签格式化
- `validate.ts`：表单字段校验（路径、正整数）
- `cron.ts`：Cron 表达式工具

## 模块关联

- 被 **components/forms/** 中的表单组件使用（校验）
- 被 **pages/** 中的页面组件使用（格式化）
