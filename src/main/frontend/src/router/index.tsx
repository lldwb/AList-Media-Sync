// ===================================================================
// React Router Hash 路由表
// ===================================================================
import { createHashRouter, Navigate } from 'react-router';
import type { ReactNode } from 'react';
import { useAuth } from '@/auth/AuthContext';

/* ---- 页面懒加载（按需加载，减小首屏体积） ---- */
import { AppLayout } from '@/components/layout/AppLayout';
import { LoginPage } from '@/pages/LoginPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { EngineListPage } from '@/pages/EngineListPage';
import { SyncTaskListPage } from '@/pages/SyncTaskListPage';
import { SyncTaskDetailPage } from '@/pages/SyncTaskDetailPage';
import { TranscodeTaskListPage } from '@/pages/TranscodeTaskListPage';
import { WebhookRuleListPage } from '@/pages/WebhookRuleListPage';
import { WebhookEventListPage } from '@/pages/WebhookEventListPage';

/* ---- 认证守卫 ---- */

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { auth } = useAuth();

  if (!auth) {
    // 记录当前路径，登录成功后跳回
    const currentPath = window.location.hash.slice(1) || '/';
    return <Navigate to={`/login?redirect=${encodeURIComponent(currentPath)}`} replace />;
  }

  return <>{children}</>;
}

/* ---- 路由配置 ---- */

export const router = createHashRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'engines', element: <EngineListPage /> },
      { path: 'sync-tasks', element: <SyncTaskListPage /> },
      { path: 'sync-tasks/:id', element: <SyncTaskDetailPage /> },
      { path: 'transcode-tasks', element: <TranscodeTaskListPage /> },
      { path: 'webhook-rules', element: <WebhookRuleListPage /> },
      { path: 'webhook-events', element: <WebhookEventListPage /> },
    ],
  },
]);
