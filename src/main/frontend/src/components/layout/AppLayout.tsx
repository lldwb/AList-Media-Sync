// ===================================================================
// 主布局组件 — 左侧侧边栏 + 右侧内容区
// ===================================================================
import { Outlet } from 'react-router';
import { Sidebar } from '@/components/layout/Sidebar';
import { useSessionTimeout } from '@/auth/useSessionTimeout';

export function AppLayout() {
  // 启用 30 分钟会话超时
  useSessionTimeout();

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <main className="flex-1 overflow-y-auto bg-gray-50 p-6">
        <Outlet />
      </main>
    </div>
  );
}
