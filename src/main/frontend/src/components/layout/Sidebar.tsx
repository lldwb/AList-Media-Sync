// ===================================================================
// 侧边栏导航组件
// ===================================================================
import { useLocation, useNavigate } from 'react-router';
import { useAuth } from '@/auth/AuthContext';

interface NavItem {
  path: string;
  label: string;
  icon: string; // SVG path d 属性
}

const NAV_ITEMS: NavItem[] = [
  {
    path: '/',
    label: '系统概览',
    icon: 'M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z',
  },
  {
    path: '/engines',
    label: '存储引擎',
    icon: 'M5 12h14M12 5l7 7-7 7',
  },
  {
    path: '/sync-tasks',
    label: '同步任务',
    icon: 'M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15',
  },
  {
    path: '/transcode-tasks',
    label: '转码任务',
    icon: 'M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3',
  },
  {
    path: '/webhook-rules',
    label: 'Webhook 规则',
    icon: 'M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z',
  },
  {
    path: '/webhook-events',
    label: 'Webhook 事件',
    icon: 'M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9',
  },
];

export function Sidebar() {
  const location = useLocation();
  const navigate = useNavigate();
  const { auth, logout } = useAuth();

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/';
    return location.pathname.startsWith(path);
  };

  return (
    <aside className="w-60 bg-gray-900 text-white flex flex-col shrink-0 h-full">
      {/* Logo / 标题 */}
      <div className="flex items-center gap-2 px-4 py-5 border-b border-gray-700">
        <svg className="h-7 w-7 text-blue-400" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5m-13.5-9L12 3m0 0 4.5 4.5M12 3v13.5" />
        </svg>
        <span className="text-lg font-semibold tracking-tight">AList Sync</span>
      </div>

      {/* 导航菜单 */}
      <nav className="flex-1 py-3 overflow-y-auto">
        {NAV_ITEMS.map((item) => (
          <button
            key={item.path}
            type="button"
            onClick={() => navigate(item.path)}
            className={`w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors ${
              isActive(item.path)
                ? 'bg-blue-600 text-white font-medium'
                : 'text-gray-300 hover:bg-gray-800 hover:text-white'
            }`}
          >
            <svg className="h-5 w-5 shrink-0" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" d={item.icon} />
            </svg>
            {item.label}
          </button>
        ))}
      </nav>

      {/* 底部用户信息 */}
      <div className="px-4 py-3 border-t border-gray-700 text-sm text-gray-400">
        <div className="flex items-center justify-between">
          <span className="truncate">{auth?.username || '未登录'}</span>
          <button
            type="button"
            className="text-gray-500 hover:text-white transition-colors"
            title="退出登录"
            onClick={() => {
              logout();
              navigate('/login');
            }}
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15m3 0l3-3m0 0l-3-3m3 3H9" />
            </svg>
          </button>
        </div>
      </div>
    </aside>
  );
}
