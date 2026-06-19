// ===================================================================
// Webhook 规则列表页 — US5
// ===================================================================
import { useState, useEffect, useCallback } from 'react';
import { api } from '@/api/client';
import { DataTable } from '@/components/ui/DataTable';
import type { ColumnDef } from '@/components/ui/DataTable';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { WebhookRuleForm } from '@/components/forms/WebhookRuleForm';
import {
  formatDateTime,
  formatWebhookEventType,
  formatRuleAction,
} from '@/utils/format';
import type {
  StorageEngineVO,
  WebhookRuleVO,
  WebhookRuleCreateDTO,
} from '@/types/api';

export function WebhookRuleListPage() {
  const [items, setItems] = useState<WebhookRuleVO[]>([]);
  const [engines, setEngines] = useState<StorageEngineVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [showForm, setShowForm] = useState(false);
  const [editingItem, setEditingItem] = useState<WebhookRuleVO | null>(null);
  const [formLoading, setFormLoading] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<WebhookRuleVO | null>(null);

  const fetchAll = useCallback(async () => {
    const [rules, engData] = await Promise.all([
      api.get<WebhookRuleVO[]>('/webhook-rules'),
      api.get<StorageEngineVO[]>('/storage-engines'),
    ]);
    setItems(rules);
    setEngines(engData);
  }, []);

  useEffect(() => {
    fetchAll()
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false));
  }, [fetchAll]);

  /* ---- 操作 ---- */

  const handleCreate = async (values: WebhookRuleCreateDTO) => {
    setFormLoading(true);
    try {
      await api.post<WebhookRuleVO>('/webhook-rules', values);
      setShowForm(false);
      await fetchAll();
    } finally {
      setFormLoading(false);
    }
  };

  const handleUpdate = async (values: WebhookRuleCreateDTO) => {
    if (!editingItem) return;
    setFormLoading(true);
    try {
      await api.put<WebhookRuleVO>(`/webhook-rules/${editingItem.id}`, values);
      setEditingItem(null);
      await fetchAll();
    } finally {
      setFormLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await api.del(`/webhook-rules/${deleteTarget.id}`);
      setDeleteTarget(null);
      await fetchAll();
    } catch (err) {
      alert(err instanceof Error ? err.message : '删除失败');
    }
  };

  const handleToggle = async (rule: WebhookRuleVO) => {
    const action = rule.enabled ? 'disable' : 'enable';
    try {
      await api.post<WebhookRuleVO>(`/webhook-rules/${rule.id}/${action}`);
      await fetchAll();
    } catch (err) {
      alert(err instanceof Error ? err.message : '操作失败');
    }
  };

  /* ---- 表格列 ---- */
  const columns: ColumnDef<WebhookRuleVO>[] = [
    { key: 'name', header: '规则名称' },
    {
      key: 'triggerEventType',
      header: '触发事件',
      render: (item) => formatWebhookEventType(item.triggerEventType),
    },
    {
      key: 'roomIdFilter',
      header: '房间号',
      render: (item) => item.roomIdFilter?.toString() ?? '全部',
    },
    {
      key: 'action',
      header: '后续动作',
      render: (item) => formatRuleAction(item.action),
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
        <h2 className="text-2xl font-bold text-gray-900">Webhook 规则</h2>
        <button
          type="button"
          onClick={() => setShowForm(true)}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-700"
        >
          创建规则
        </button>
      </div>

      {error && (
        <div className="mb-4">
          <ErrorBanner message={error} onRetry={fetchAll} />
        </div>
      )}

      <DataTable
        columns={columns}
        items={items}
        keyField="id"
        emptyState={
          <EmptyState
            title="暂无 Webhook 规则"
            description="创建规则以在录播姬事件到达时自动执行同步或转码操作"
            actionLabel="创建第一个规则"
            onAction={() => setShowForm(true)}
          />
        }
      />

      {/* 创建表单 */}
      {showForm && (
        <WebhookRuleForm
          engines={engines}
          onSubmit={handleCreate}
          onCancel={() => setShowForm(false)}
          loading={formLoading}
        />
      )}

      {/* 编辑表单 */}
      {editingItem && (
        <WebhookRuleForm
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
          message={`确定要删除 Webhook 规则「${deleteTarget.name}」吗？此操作不可撤销。`}
          onConfirm={handleDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      )}
    </div>
  );
}
