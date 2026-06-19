// ===================================================================
// 转码任务列表页 — US4
// ===================================================================
import { useState, useEffect, useCallback } from 'react';
import { api } from '@/api/client';
import { DataTable } from '@/components/ui/DataTable';
import type { ColumnDef } from '@/components/ui/DataTable';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { TranscodeTaskForm } from '@/components/forms/TranscodeTaskForm';
import { usePolling } from '@/hooks/usePolling';
import { formatDateTime, formatTranscodeStatus } from '@/utils/format';
import type {
  StorageEngineVO,
  TranscodeTaskVO,
  TranscodeTaskCreateDTO,
} from '@/types/api';

export function TranscodeTaskListPage() {
  const [items, setItems] = useState<TranscodeTaskVO[]>([]);
  const [engines, setEngines] = useState<StorageEngineVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formLoading, setFormLoading] = useState(false);
  const [cleanupResult, setCleanupResult] = useState<string | null>(null);

  const fetchTasks = useCallback(async () => {
    const data = await api.get<TranscodeTaskVO[]>('/transcode-tasks');
    setItems(data);
    // 同时加载引擎列表
    try {
      const engData = await api.get<StorageEngineVO[]>('/storage-engines');
      setEngines(engData);
    } catch { /* 引擎加载失败不影响列表 */ }
    return data;
  }, []);

  useEffect(() => {
    fetchTasks()
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false));
  }, [fetchTasks]);

  // 轮询更新执行中任务的进度
  usePolling(
    fetchTasks,
    5000,
    (data: TranscodeTaskVO[]) =>
      !data.some((t) => t.status === 'TRANSCODING' || t.status === 'UPLOADING' || t.status === 'SCANNING'),
  );

  /* ---- 操作 ---- */

  const handleCreate = async (values: TranscodeTaskCreateDTO) => {
    setFormLoading(true);
    try {
      await api.post<TranscodeTaskVO>('/transcode-tasks', values);
      setShowForm(false);
      await fetchTasks();
    } finally {
      setFormLoading(false);
    }
  };

  const handleRetryUpload = async (id: number) => {
    try {
      await api.post(`/transcode-tasks/${id}/retry-upload`);
      await fetchTasks();
    } catch (err) {
      alert(err instanceof Error ? err.message : '重试失败');
    }
  };

  const handleCleanup = async () => {
    try {
      const result = await api.del<{ deletedCount: number }>('/transcode-tasks/cleanup-temp');
      setCleanupResult(`已清理 ${result.deletedCount} 个残留临时文件`);
      setTimeout(() => setCleanupResult(null), 5000);
    } catch (err) {
      alert(err instanceof Error ? err.message : '清理失败');
    }
  };

  /* ---- 表格列 ---- */

  const columns: ColumnDef<TranscodeTaskVO>[] = [
    { key: 'id', header: 'ID' },
    {
      key: 'sourceFilePath',
      header: '源路径',
      className: 'max-w-[180px] truncate',
    },
    {
      key: 'targetFilePath',
      header: '目标路径',
      className: 'max-w-[180px] truncate',
    },
    { key: 'targetFormat', header: '格式' },
    {
      key: 'progress',
      header: '进度',
      render: (item) => (
        <div className="flex items-center gap-2">
          <div className="w-20 h-2 bg-gray-200 rounded-full overflow-hidden">
            <div
              className="h-full bg-blue-500 rounded-full transition-all"
              style={{ width: `${item.progressPercent || 0}%` }}
            />
          </div>
          <span className="text-xs text-gray-500">{item.progressPercent || 0}%</span>
        </div>
      ),
    },
    {
      key: 'status',
      header: '状态',
      render: (item) => <StatusBadge status={item.status} label={formatTranscodeStatus(item.status)} />,
    },
    {
      key: 'createdAt',
      header: '创建时间',
      render: (item) => formatDateTime(item.createdAt),
    },
    {
      key: 'actions',
      header: '操作',
      render: (item) => (
        <div className="flex items-center gap-2">
          {item.status === 'FAILED' && (
            <button
              type="button"
              onClick={() => handleRetryUpload(item.id)}
              className="text-xs px-2 py-1 rounded border border-gray-300 hover:bg-gray-100"
            >
              重试上传
            </button>
          )}
        </div>
      ),
    },
  ];

  if (loading) return <LoadingSpinner size="lg" />;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">转码任务</h2>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleCleanup}
            className="rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
          >
            清理残留文件
          </button>
          <button
            type="button"
            onClick={() => setShowForm(true)}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-700"
          >
            创建转码任务
          </button>
        </div>
      </div>

      {cleanupResult && (
        <div className="mb-4 rounded-md bg-green-50 border border-green-200 px-4 py-3 text-sm text-green-700">
          {cleanupResult}
        </div>
      )}

      {error && (
        <div className="mb-4">
          <ErrorBanner message={error} onRetry={fetchTasks} />
        </div>
      )}

      <DataTable
        columns={columns}
        items={items}
        keyField="id"
        emptyState={
          <EmptyState
            title="暂无转码任务"
            description="创建一个转码任务以开始文件转码"
            actionLabel="创建转码任务"
            onAction={() => setShowForm(true)}
          />
        }
      />

      {/* 创建表单 */}
      {showForm && (
        <TranscodeTaskForm
          engines={engines}
          onSubmit={handleCreate}
          onCancel={() => setShowForm(false)}
          loading={formLoading}
        />
      )}
    </div>
  );
}
