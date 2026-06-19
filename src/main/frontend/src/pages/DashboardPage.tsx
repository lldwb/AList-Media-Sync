// ===================================================================
// 仪表板页面 — 统计卡片 + 点击跳转（US6 基础占位）
// ===================================================================
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@/api/client';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import type { DashboardStatsVO } from '@/types/api';

export function DashboardPage() {
  const navigate = useNavigate();
  const [stats, setStats] = useState<DashboardStatsVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.get<DashboardStatsVO>('/dashboard/stats')
      .then((data) => { if (!cancelled) setStats(data); })
      .catch((err) => { if (!cancelled) setError(err instanceof Error ? err.message : '加载失败'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  if (loading) return <LoadingSpinner size="lg" />;
  if (error) return <ErrorBanner message={error} onRetry={() => window.location.reload()} />;

  const cards = [
    {
      title: '活跃同步任务',
      value: stats?.activeSyncTasks ?? 0,
      color: 'bg-blue-500',
      path: '/sync-tasks',
    },
    {
      title: '等待转码任务',
      value: stats?.pendingTranscodeTasks ?? 0,
      color: 'bg-yellow-500',
      path: '/transcode-tasks',
    },
    {
      title: '今日处理文件',
      value: stats?.todayProcessedFiles ?? 0,
      color: 'bg-green-500',
      path: '/sync-tasks',
    },
    {
      title: '24h 成功率',
      value: `${stats?.last24hSuccessRate?.toFixed(1) ?? '0.0'}%`,
      color: 'bg-purple-500',
      path: null,
    },
  ];

  const isIdle = (stats?.activeSyncTasks ?? 0) === 0 && (stats?.pendingTranscodeTasks ?? 0) === 0;

  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-6">系统概览</h2>

      {isIdle && (
        <div className="mb-6 rounded-md bg-green-50 border border-green-200 px-4 py-3 text-sm text-green-700">
          系统当前空闲，无活跃任务
        </div>
      )}

      {/* 统计卡片 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {cards.map((card) => (
          <button
            key={card.title}
            type="button"
            disabled={!card.path}
            onClick={() => card.path && navigate(card.path)}
            className={`rounded-lg shadow-sm p-5 text-left transition-transform hover:scale-105 ${
              card.path ? 'cursor-pointer' : 'cursor-default'
            } bg-white border border-gray-200`}
          >
            <div className={`inline-block w-10 h-10 rounded-lg ${card.color} mb-3`} />
            <p className="text-sm text-gray-500">{card.title}</p>
            <p className="text-2xl font-bold text-gray-900 mt-1">{card.value}</p>
          </button>
        ))}
      </div>

      {/* 额外统计 */}
      <div className="mt-6 grid grid-cols-2 gap-4">
        <div className="rounded-lg bg-white border border-gray-200 p-4">
          <p className="text-sm text-gray-500">存储引擎总数</p>
          <p className="text-xl font-semibold text-gray-900">{stats?.totalEngines ?? 0}</p>
        </div>
        <div className="rounded-lg bg-white border border-gray-200 p-4">
          <p className="text-sm text-gray-500">Webhook 规则总数</p>
          <p className="text-xl font-semibold text-gray-900">{stats?.totalWebhookRules ?? 0}</p>
        </div>
      </div>
    </div>
  );
}
