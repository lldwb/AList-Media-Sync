// ===================================================================
// 登录页 — 独立布局，无侧边栏
// ===================================================================
import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '@/auth/AuthContext';

export function LoginPage() {
  const { login, setRedirectPath } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    // 前端校验必填
    if (!username.trim() || !password.trim()) {
      setError('请输入用户名和密码');
      return;
    }

    setError(null);
    setLoading(true);

    try {
      await login(username.trim(), password);
      const redirect = searchParams.get('redirect') || '/';
      setRedirectPath(redirect);
      navigate(redirect, { replace: true });
    } catch (err) {
      const message = err instanceof Error ? err.message : '登录失败';
      setError(message);
      // 保留用户名不清空
      setPassword('');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <div className="bg-white rounded-lg shadow-md p-8 w-full max-w-sm">
        {/* Logo */}
        <div className="flex flex-col items-center mb-6">
          <svg
            className="h-12 w-12 text-blue-500 mb-3"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={1.5}
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5m-13.5-9L12 3m0 0 4.5 4.5M12 3v13.5"
            />
          </svg>
          <h1 className="text-xl font-bold text-gray-900">AList Media Sync</h1>
          <p className="text-sm text-gray-500 mt-1">管理后台登录</p>
        </div>

        {/* 错误提示 */}
        {error && (
          <div className="mb-4 rounded-md bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {/* 登录表单 */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="username" className="block text-sm font-medium text-gray-700 mb-1">
              用户名
            </label>
            <input
              id="username"
              type="text"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="请输入用户名"
              disabled={loading}
            />
          </div>
          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
              密码
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="请输入密码"
              disabled={loading}
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? '验证中...' : '登录'}
          </button>
        </form>
      </div>
    </div>
  );
}
