// ===================================================================
// 状态标签组件 — 颜色语义映射
// ===================================================================
import type { ExecutionStatus, TranscodeStatus, WebhookEventStatus } from '@/types/api';

type StatusVariant = ExecutionStatus | TranscodeStatus | WebhookEventStatus | string;

interface StatusBadgeProps {
  status: StatusVariant;
  /** 自定义标签文字（可选，默认使用 status 值） */
  label?: string;
}

/** 根据状态值映射 Tailwind 颜色类 */
function getBadgeClasses(status: StatusVariant): string {
  const lower = status.toLowerCase();

  if (['running', 'transcoding', 'uploading', 'scanning', 'processing'].includes(lower)) {
    return 'bg-blue-100 text-blue-800';
  }
  if (['success', 'completed', 'online'].includes(lower)) {
    return 'bg-green-100 text-green-800';
  }
  if (['failed', 'error'].includes(lower)) {
    return 'bg-red-100 text-red-800';
  }
  if (['partial_success', 'pending'].includes(lower)) {
    return 'bg-yellow-100 text-yellow-800';
  }
  if (['interrupted', 'disabled', 'offline', 'duplicate'].includes(lower)) {
    return 'bg-gray-100 text-gray-600';
  }
  return 'bg-gray-100 text-gray-600';
}

/** 运行中的状态显示脉动动画 */
function isRunning(status: StatusVariant): boolean {
  const lower = status.toLowerCase();
  return ['running', 'transcoding', 'uploading', 'scanning', 'processing'].includes(lower);
}

export function StatusBadge({ status, label }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${getBadgeClasses(status)}`}
    >
      {isRunning(status) && (
        <span className="relative flex h-2 w-2">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75" />
          <span className="relative inline-flex rounded-full h-2 w-2 bg-blue-500" />
        </span>
      )}
      {label || status}
    </span>
  );
}
