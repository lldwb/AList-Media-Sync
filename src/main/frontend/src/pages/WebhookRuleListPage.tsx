// ===================================================================
// Webhook 规则列表页 — 支持录播存储引擎关联和地址复制
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
  const [webhookUrl, setWebhookUrl] = useState<string>('');
  const [copyLabel, setCopyLabel] = useState<string>('复制');

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

  const fetchAddress = useCallback(async () => {
    try {
      const addr = await api.get<string>('/webhooks/address');
      setWebhookUrl(addr);
    } catch {
      setWebhookUrl(window.location.origin + '/api/webhooks/recorder');
    }
  }, []);

  useEffect(() => {
    Promise.all([fetchAll(), fetchAddress()])
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false));
  }, [fetchAll, fetchAddress]);

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

  const handleCopyAddress = async () => {
    try {
      await navigator.clipboard.writeText(webhookUrl);
    } catch {
      // 降级方案
      const textarea = document.createElement('textarea');
      textarea.value = webhookUrl;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
    }
    setCopyLabel('已复制');
    setTimeout(() => setCopyLabel('复制'), 2000);
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

      {/* Webhook 地址复制栏 */}
      <div className="mb-4 flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3">
        <span className="text-sm font-medium text-gray-700 shrink-0">Webhook 地址：</span>
        <input
          type="text"
          readOnly
          value={webhookUrl}
          className="flex-1 rounded border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-600 focus:outline-none"
        />
        <button
          type="button"
          onClick={handleCopyAddress}
          className={`shrink-0 rounded px-3 py-1.5 text-sm font-medium transition-colors ${
            copyLabel === '已复制'
              ? 'bg-green-100 text-green-700 border border-green-300'
              : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-100'
          }`}
        >
          {copyLabel === '已复制' ? (
            <span className="flex items-center gap-1">
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
              </svg>
              已复制
            </span>
          ) : (
            <span className="flex items-center gap-1">
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M15.666 3.888A2.25 2.25 0 0013.5 2.25h-3c-1.03 0-1.9.693-2.166 1.638m7.332 0c.055.194.084.4.084.612v0a.75.75 0 01-.75.75H9a.75.75 0 01-.75-.75v0c0-.212.03-.418.084-.612m7.332 0c.646.049 1.288.11 1.927.184 1.1.128 1.907 1.077 1.907 2.185V19.5a2.25 2.25 0 01-2.25 2.25H6.75A2.25 2.25 0 014.5 19.5V6.257c0-1.108.806-2.057 1.907-2.185a48.208 48.208 0 011.927-.184" />
              </svg>
              复制
            </span>
          )}
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

      {showForm && (
        <WebhookRuleForm
          engines={engines}
          onSubmit={handleCreate}
          onCancel={() => setShowForm(false)}
          loading={formLoading}
        />
      )}

      {editingItem && (
        <WebhookRuleForm
          engines={engines}
          initialValues={editingItem}
          onSubmit={handleUpdate}
          onCancel={() => setEditingItem(null)}
          loading={formLoading}
        />
      )}

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
