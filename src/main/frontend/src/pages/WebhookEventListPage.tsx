// ===================================================================
// Webhook 事件列表页 — WebSocket 实时更新
// ===================================================================
import { useState, useEffect, useCallback } from 'react';
import { api } from '@/api/client';
import { DataTable } from '@/components/ui/DataTable';
import type { ColumnDef } from '@/components/ui/DataTable';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useWebSocket } from '@/hooks/useWebSocket';
import {
  formatDateTime,
  formatWebhookEventType,
  formatWebhookEventStatus,
} from '@/utils/format';
import type { WebhookEventVO, WsMessage } from '@/types/api';

export function WebhookEventListPage() {
  const [items, setItems] = useState<WebhookEventVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchEvents = useCallback(async () => {
    const data = await api.get<WebhookEventVO[]>('/webhooks/events');
    setItems(data);
  }, []);

  useEffect(() => {
    fetchEvents()
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false));
  }, [fetchEvents]);

  // WebSocket 接收 WEBHOOK_EVENT 消息，增量更新本地状态
  useWebSocket((message: WsMessage) => {
    if (message.type === 'WEBHOOK_EVENT') {
      setItems(prev =>
        prev.map(e =>
          e.id === message.payload.eventId ? { ...e, ...message.payload } : e
        )
      );
    }
  });

  const columns: ColumnDef<WebhookEventVO>[] = [
    {
      key: 'eventType',
      header: '事件类型',
      render: (item) => formatWebhookEventType(item.eventType),
    },
    { key: 'eventId', header: 'Event ID', className: 'font-mono text-xs' },
    {
      key: 'eventTimestamp',
      header: '发生时间',
      render: (item) => formatDateTime(item.eventTimestamp),
    },
    {
      key: 'roomId',
      header: '房间号',
      render: (item) => item.roomId?.toString() ?? '-',
    },
    {
      key: 'fileName',
      header: '文件名',
      className: 'max-w-[200px] truncate',
      render: (item) => item.fileName ?? '-',
    },
    {
      key: 'status',
      header: '处理状态',
      render: (item) => (
        <StatusBadge
          status={item.status}
          label={formatWebhookEventStatus(item.status)}
        />
      ),
    },
  ];

  if (loading) return <LoadingSpinner size="lg" />;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">Webhook 事件</h2>
        <button
          type="button"
          onClick={fetchEvents}
          className="rounded-md border border-gray-300 px-3 py-2 text-sm hover:bg-gray-50"
        >
          刷新
        </button>
      </div>

      {error && (
        <div className="mb-4">
          <ErrorBanner message={error} onRetry={fetchEvents} />
        </div>
      )}

      <DataTable
        columns={columns}
        items={items}
        keyField="id"
        emptyState={
          <EmptyState
            title="暂未接收到任何 Webhook 事件"
            description={
              '录播姬 Webhook URL 格式：http://<本服务地址>:8080/api/webhooks/recorder\n' +
              '在录播姬「设置 → 高级 → Webhook」中填入此 URL 即可开始接收事件。'
            }
          />
        }
      />
    </div>
  );
}
