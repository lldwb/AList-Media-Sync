// ===================================================================
// 转码任务列表页 — 8 状态模型 + canRetry + WebSocket 实时更新
// ===================================================================
import { useState, useEffect, useCallback } from 'react';
import { api } from '@/api/client';
import { DataTable } from '@/components/ui/DataTable';
import type { ColumnDef } from '@/components/ui/DataTable';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { TranscodeTaskForm } from '@/components/forms/TranscodeTaskForm';
import { useWebSocket } from '@/hooks/useWebSocket';
import { deleteFailedTranscodeTasks, deleteCompletedTranscodeTasks, retryAllTranscodeTasks } from '@/api/client';
import { formatDateTime, formatTranscodeStatus } from '@/utils/format';
import type {
  StorageEngineVO,
  TranscodeTaskVO,
  TranscodeTaskCreateDTO,
  WsMessage,
} from '@/types/api';

export function TranscodeTaskListPage() {
  const [items, setItems] = useState<TranscodeTaskVO[]>([]);
  const [engines, setEngines] = useState<StorageEngineVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formLoading, setFormLoading] = useState(false);
  const [cleanupResult, setCleanupResult] = useState<string | null>(null);
  const [retryingId, setRetryingId] = useState<number | null>(null);

  // 批量操作状态
  const [batchAction, setBatchAction] = useState<'failed' | 'completed' | 'retry' | null>(null);
  const [batchLoading, setBatchLoading] = useState(false);
  const [batchResult, setBatchResult] = useState<string | null>(null);

  const fetchTasks = useCallback(async () => {
    const data = await api.get<TranscodeTaskVO[]>('/transcode-tasks');
    setItems(data);
    try {
      const engData = await api.get<StorageEngineVO[]>('/storage-engines');
      setEngines(engData);
    } catch { /* 非关键 */ }
    return data;
  }, []);

  useEffect(() => {
    fetchTasks()
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false));
  }, [fetchTasks]);

  // WebSocket 接收 TRANSCODE_PROGRESS 和 TASK_EVENT 消息，增量更新本地状态
  useWebSocket((message: WsMessage) => {
    switch (message.type) {
      case 'TRANSCODE_PROGRESS':
        setItems(prev =>
          prev.map(t =>
            t.id === message.payload.taskId ? { ...t, ...message.payload } : t
          )
        );
        break;
      case 'TASK_EVENT':
        if (message.payload.taskType === 'TRANSCODE') {
          // 任务删除/完成时重新加载列表
          if (message.payload.action === 'DELETED' || message.payload.action === 'BATCH_DELETED') {
            fetchTasks();
          } else if (message.payload.action === 'CREATED') {
            // 新任务创建，刷新列表以获取完整数据
            fetchTasks();
          }
        }
        break;
    }
  });

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

  const handleRetry = async (id: number) => {
    setRetryingId(id);
    try {
      await api.post(`/transcode-tasks/${id}/retry`);
      await fetchTasks();
    } catch (err) {
      alert(err instanceof Error ? err.message : '重试失败');
    } finally {
      setRetryingId(null);
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

  /* ---- 批量操作 ---- */

  const handleBatchAction = async () => {
    setBatchLoading(true);
    try {
      let result: { deletedCount?: number; submittedCount?: number };
      let message: string;
      switch (batchAction) {
        case 'failed':
          result = await deleteFailedTranscodeTasks();
          message = `已清理 ${result.deletedCount} 个失败任务`;
          break;
        case 'completed':
          result = await deleteCompletedTranscodeTasks();
          message = `已清理 ${result.deletedCount} 个成功任务`;
          break;
        case 'retry':
          result = await retryAllTranscodeTasks();
          message = `已提交 ${result.submittedCount} 个任务进行重试`;
          break;
      }
      setBatchResult(message);
      setTimeout(() => setBatchResult(null), 5000);
      setBatchAction(null);
      await fetchTasks();
    } catch (err) {
      alert(err instanceof Error ? err.message : '操作失败');
    } finally {
      setBatchLoading(false);
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
          {item.canRetry && (
            <button
              type="button"
              disabled={retryingId === item.id}
              onClick={() => handleRetry(item.id)}
              className="text-xs px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50"
            >
              {retryingId === item.id ? '重试中...' : '重试'}
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
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setBatchAction('failed')}
            className="rounded-md border border-red-300 px-3 py-2 text-sm text-red-700 hover:bg-red-50"
          >
            清理失败任务
          </button>
          <button
            type="button"
            onClick={() => setBatchAction('completed')}
            className="rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
          >
            清理成功任务
          </button>
          <button
            type="button"
            onClick={() => setBatchAction('retry')}
            className="rounded-md border border-blue-300 px-3 py-2 text-sm text-blue-700 hover:bg-blue-50"
          >
            重试所有失败文件
          </button>
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

      {batchResult && (
        <div className="mb-4 rounded-md bg-blue-50 border border-blue-200 px-4 py-3 text-sm text-blue-700">
          {batchResult}
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

      {showForm && (
        <TranscodeTaskForm
          engines={engines}
          onSubmit={handleCreate}
          onCancel={() => setShowForm(false)}
          loading={formLoading}
        />
      )}

      {/* 批量操作确认对话框 */}
      {batchAction && (
        <ConfirmDialog
          title={
            batchAction === 'failed' ? '清理失败任务' :
            batchAction === 'completed' ? '清理成功任务' : '重试所有失败文件'
          }
          message={
            batchAction === 'failed' ? '确定要清理所有失败任务吗？此操作不可撤销。' :
            batchAction === 'completed' ? '确定要清理所有成功任务吗？此操作不可撤销。' :
            '确定要重试所有失败文件吗？'
          }
          confirmLabel={batchLoading ? (batchAction === 'retry' ? '重试中...' : '清理中...') : '确定'}
          disabled={batchLoading}
          onConfirm={handleBatchAction}
          onCancel={() => setBatchAction(null)}
        />
      )}
    </div>
  );
}
