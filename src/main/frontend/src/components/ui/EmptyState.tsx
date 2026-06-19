// ===================================================================
// 空数据状态组件
// ===================================================================
import type { ReactNode } from 'react';

interface EmptyStateProps {
  /** 图标（ReactNode） */
  icon?: ReactNode;
  /** 主标题 */
  title: string;
  /** 描述文字 */
  description?: string;
  /** 操作按钮文字 */
  actionLabel?: string;
  /** 操作按钮回调 */
  onAction?: () => void;
}

export function EmptyState({
  icon,
  title,
  description,
  actionLabel,
  onAction,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      {icon ? (
        <div className="text-gray-400 mb-4">{icon}</div>
      ) : (
        <svg
          className="h-12 w-12 text-gray-300 mb-4"
          fill="none"
          viewBox="0 0 24 24"
          strokeWidth={1}
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4"
          />
        </svg>
      )}
      <h3 className="text-lg font-medium text-gray-900 mb-1">{title}</h3>
      {description && (
        <p className="text-sm text-gray-500 mb-4 max-w-md">{description}</p>
      )}
      {actionLabel && onAction && (
        <button
          type="button"
          className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
          onClick={onAction}
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
}
