# frontend/auth/ — 认证模块

## 功能

前端认证状态管理，包括登录状态持久化（sessionStorage）、会话超时检测和路由守卫。

## 作用

- `AuthContext.tsx`：React Context 管理认证状态
- `useSessionTimeout.ts`：会话超时自动退出

## 模块关联

- 被 **router/index.tsx** 的 `ProtectedRoute` 使用
- 被 **pages/LoginPage.tsx** 使用
