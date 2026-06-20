// ===================================================================
// 存储引擎列表页 — 支持 AList/LOCAL 双类型
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
import { EngineForm } from '@/components/forms/EngineForm';
import { formatDateTime } from '@/utils/format';
import type {
  StorageEngineVO,
  StorageEngineCreateDTO,
  StorageEngineUpdateDTO,
} from '@/types/api';

export function EngineListPage() {
  const [items, setItems] = useState<StorageEngineVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [showForm, setShowForm] = useState(false);
  const [editingItem, setEditingItem] = useState<StorageEngineVO | null>(null);
  const [formLoading, setFormLoading] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<StorageEngineVO | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const [testingId, setTestingId] = useState<number | null>(null);
  const [testResults, setTestResults] = useState<Record<number, boolean>>({});

  const fetchEngines = useCallback(async () => {
    setError(null);
    try {
      const data = await api.get<StorageEngineVO[]>('/storage-engines');
      setItems(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchEngines();
  }, [fetchEngines]);

  /* ---- CRUD 操作 ---- */

  const handleCreate = async (values: StorageEngineCreateDTO) => {
    setFormLoading(true);
    try {
      await api.post<StorageEngineVO>('/storage-engines', values);
      setShowForm(false);
      await fetchEngines();
    } finally {
      setFormLoading(false);
    }
  };

  const handleUpdate = async (values: StorageEngineCreateDTO) => {
    if (!editingItem) return;
    setFormLoading(true);
    try {
      const updateDTO: StorageEngineUpdateDTO = {
        name: values.name,
        baseUrl: values.baseUrl,
        localPath: values.localPath,
      };
      if (values.token) {
        updateDTO.token = values.token;
      }
      await api.put<StorageEngineVO>(`/storage-engines/${editingItem.id}`, updateDTO);
      setEditingItem(null);
      await fetchEngines();
    } finally {
      setFormLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleteError(null);
    try {
      await api.del(`/storage-engines/${deleteTarget.id}`);
      setDeleteTarget(null);
      await fetchEngines();
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : '删除失败');
    }
  };

  const handleTestConnection = async (id: number) => {
    setTestingId(id);
    try {
      const result = await api.post<{ connected: boolean }>(`/storage-engines/${id}/test`);
      setTestResults((prev) => ({ ...prev, [id]: result.connected }));
    } catch {
      setTestResults((prev) => ({ ...prev, [id]: false }));
    } finally {
      setTestingId(null);
    }
  };

  /* ---- 表格列定义 ---- */
  const columns: ColumnDef<StorageEngineVO>[] = [
    { key: 'name', header: '名称' },
    {
      key: 'engineType',
      header: '类型',
      render: (item) => (
        <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
          item.engineType === 'ALIST' ? 'bg-blue-100 text-blue-700' : 'bg-green-100 text-green-700'
        }`}>
          {item.engineType === 'ALIST' ? 'AList' : '本地'}
        </span>
      ),
    },
    {
      key: 'baseUrl',
      header: '地址/路径',
      className: 'max-w-[200px] truncate',
      render: (item) => item.engineType === 'ALIST' ? (item.baseUrl ?? '-') : (item.localPath ?? '-'),
    },
    {
      key: 'status',
      header: '状态',
      render: (item) => (
        <StatusBadge
          status={item.status}
          label={item.status === 'ONLINE' ? '在线' : item.status === 'OFFLINE' ? '离线' : '异常'}
        />
      ),
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
          <button
            type="button"
            disabled={testingId === item.id}
            onClick={() => handleTestConnection(item.id)}
            className="text-xs px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50"
          >
            {testingId === item.id ? '测试中...' : '测试连接'}
          </button>
          {testResults[item.id] !== undefined && (
            <span className={`text-xs ${testResults[item.id] ? 'text-green-600' : 'text-red-600'}`}>
              {testResults[item.id] ? '✓ 成功' : '✗ 失败'}
            </span>
          )}
          <button
            type="button"
            className="text-xs text-blue-600 hover:text-blue-800"
            onClick={() => setEditingItem(item)}
          >
            编辑
          </button>
          <button
            type="button"
            className="text-xs text-red-600 hover:text-red-800"
            onClick={() => setDeleteTarget(item)}
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
        <h2 className="text-2xl font-bold text-gray-900">存储引擎</h2>
        <button
          type="button"
          onClick={() => setShowForm(true)}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-700"
        >
          添加引擎
        </button>
      </div>

      {error && (
        <div className="mb-4">
          <ErrorBanner message={error} onRetry={fetchEngines} />
        </div>
      )}

      <DataTable
        columns={columns}
        items={items}
        keyField="id"
        emptyState={
          <EmptyState
            title="暂无存储引擎"
            description="添加您的第一个存储引擎以开始使用"
            actionLabel="创建第一个引擎"
            onAction={() => setShowForm(true)}
          />
        }
      />

      {showForm && (
        <EngineForm
          onSubmit={handleCreate}
          onCancel={() => setShowForm(false)}
          loading={formLoading}
        />
      )}

      {editingItem && (
        <EngineForm
          initialValues={editingItem}
          onSubmit={handleUpdate}
          onCancel={() => setEditingItem(null)}
          loading={formLoading}
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          title="确认删除"
          message={
            deleteError
              ? `删除失败：${deleteError}`
              : `确定要删除存储引擎「${deleteTarget.name}」吗？此操作不可撤销。`
          }
          confirmLabel={deleteError ? '重试' : '确认删除'}
          onConfirm={handleDelete}
          onCancel={() => {
            setDeleteTarget(null);
            setDeleteError(null);
          }}
        />
      )}
    </div>
  );
}
