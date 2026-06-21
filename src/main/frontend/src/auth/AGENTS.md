# frontend/auth/ — 认证模块

> **文件权重**：四级文件（地方性法规级），低于 `constitution.md`（宪法）、`AGENTS.md` 根级（法律）和前端 `AGENTS.md`（行政法规）。适用于本模块修改或他模块修改涉及本模块时。

## 功能

前端认证状态管理，包括登录状态持久化（sessionStorage）、会话超时检测和路由守卫。

## 作用

- `AuthContext.tsx`：React Context 管理认证状态
- `useSessionTimeout.ts`：会话超时自动退出

## 模块关联

- 被 **router/index.tsx** 的 `ProtectedRoute` 使用
- 被 **pages/LoginPage.tsx** 使用
