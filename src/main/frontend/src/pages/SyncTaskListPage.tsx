// ===================================================================
// 同步任务列表页 — US3
// ===================================================================
import { useState, useEffect, useCallback } from 'react';
import { api } from '@/api/client';
import { DataTable } from '@/components/ui/DataTable';
import type { ColumnDef } from '@/components/ui/DataTable';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { SyncTaskForm } from '@/components/forms/SyncTaskForm';
import { usePolling } from '@/hooks/usePolling';
import { formatDateTime, formatSyncMode, formatScheduleType } from '@/utils/format';
import type {
  StorageEngineVO,
  SyncTaskVO,
  SyncTaskCreateDTO,
  SyncTaskUpdateDTO,
} from '@/types/api';

export function SyncTaskListPage() {
  const [items, setItems] = useState<SyncTaskVO[]>([]);
  const [engines, setEngines] = useState<StorageEngineVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 表单状态
  const [showForm, setShowForm] = useState(false);
  const [editingItem, setEditingItem] = useState<SyncTaskVO | null>(null);
  const [formLoading, setFormLoading] = useState(false);

  // 删除状态
  const [deleteTarget, setDeleteTarget] = useState<SyncTaskVO | null>(null);

  /* ---- 数据加载 ---- */

  const fetchTasks = useCallback(async () => {
    const data = await api.get<SyncTaskVO[]>('/sync-tasks');
    setItems(data);
    // 同步获取引擎列表
    try {
      const engData = await api.get<StorageEngineVO[]>('/storage-engines');
      setEngines(engData);
    } catch { /* 引擎加载失败不影响任务列表显示 */ }
  }, []);

  useEffect(() => {
    fetchTasks()
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false));
  }, [fetchTasks]);

  /* ---- 轮询：刷新运行中的任务进度 ---- */

  usePolling<SyncTaskVO[]>(
    () => fetchTasks().then(() => Promise.resolve(items)),
    5000,
    () => false,
  );

  /* ---- 操作 ---- */

  const handleCreate = async (values: SyncTaskCreateDTO) => {
    setFormLoading(true);
    try {
      await api.post<SyncTaskVO>('/sync-tasks', values);
      setShowForm(false);
      await fetchTasks();
    } finally {
      setFormLoading(false);
    }
  };

  const handleUpdate = async (values: SyncTaskCreateDTO) => {
    if (!editingItem) return;
    setFormLoading(true);
    try {
      const updateDTO: SyncTaskUpdateDTO = { ...values };
      await api.put<SyncTaskVO>(`/sync-tasks/${editingItem.id}`, updateDTO);
      setEditingItem(null);
      await fetchTasks();
    } finally {
      setFormLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await api.del(`/sync-tasks/${deleteTarget.id}`);
      setDeleteTarget(null);
      await fetchTasks();
    } catch (err) {
      alert(err instanceof Error ? err.message : '删除失败');
    }
  };

  const handleToggle = async (task: SyncTaskVO) => {
    const action = task.enabled ? 'disable' : 'enable';
    try {
      await api.post<SyncTaskVO>(`/sync-tasks/${task.id}/${action}`);
      await fetchTasks();
    } catch (err) {
      alert(err instanceof Error ? err.message : `操作失败`);
    }
  };

  const handleExecute = async (task: SyncTaskVO) => {
    try {
      await api.post(`/sync-tasks/${task.id}/execute`);
      await fetchTasks();
    } catch (err) {
      alert(err instanceof Error ? err.message : '手动触发失败');
    }
  };

  /* ---- 表格列 ---- */

  const columns: ColumnDef<SyncTaskVO>[] = [
    { key: 'name', header: '任务名称' },
    { key: 'sourceEngineName', header: '源引擎' },
    { key: 'targetEngineName', header: '目标引擎' },
    {
      key: 'syncMode',
      header: '同步模式',
      render: (item) => formatSyncMode(item.syncMode),
    },
    {
      key: 'scheduleType',
      header: '执行计划',
      render: (item) => formatScheduleType(item.scheduleType),
    },
    {
      key: 'enabled',
      header: '状态',
      render: (item) => (
        <label className="relative inline-flex items-center cursor-pointer">
          <input
            type="checkbox"
            checked={item.enabled}
            onChange={() => handleToggle(item)}
            className="sr-only peer"
          />
          <div className="w-9 h-5 bg-gray-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-blue-600" />
        </label>
      ),
    },
    {
      key: 'lastExecutedAt',
      header: '最后执行',
      render: (item) => (
        <div>
          <div className="text-xs">{formatDateTime(item.lastExecutedAt)}</div>
        </div>
      ),
    },
    {
      key: 'actions',
      header: '操作',
      render: (item) => (
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => handleExecute(item)}
            className="text-xs px-2 py-1 rounded border border-gray-300 hover:bg-gray-100"
          >
            立即执行
          </button>
          <button
            type="button"
            onClick={() => setEditingItem(item)}
            className="text-xs text-blue-600 hover:text-blue-800"
          >
            编辑
          </button>
          <button
            type="button"
            onClick={() => setDeleteTarget(item)}
            className="text-xs text-red-600 hover:text-red-800"
          >
            删除
          </button>
        </div>
      ),
    },
  ];

  if (loading) return <LoadingSpinner size="lg" />;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">同步任务</h2>
        <button
          type="button"
          onClick={() => setShowForm(true)}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-700"
        >
          创建任务
        </button>
      </div>

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
            title="暂无同步任务"
            description="创建一个同步任务以开始在存储引擎之间同步文件"
            actionLabel="创建第一个任务"
            onAction={() => setShowForm(true)}
          />
        }
      />

      {/* 创建表单 */}
      {showForm && (
        <SyncTaskForm
          engines={engines}
          onSubmit={handleCreate}
          onCancel={() => setShowForm(false)}
          loading={formLoading}
        />
      )}

      {/* 编辑表单 */}
      {editingItem && (
        <SyncTaskForm
          engines={engines}
          initialValues={editingItem}
          onSubmit={handleUpdate}
          onCancel={() => setEditingItem(null)}
          loading={formLoading}
        />
      )}

      {/* 删除确认 */}
      {deleteTarget && (
        <ConfirmDialog
          title="确认删除"
          message={`确定要删除同步任务「${deleteTarget.name}」吗？此操作不可撤销。`}
          onConfirm={handleDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      )}
    </div>
  );
}
