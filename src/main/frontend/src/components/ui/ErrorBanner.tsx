// ===================================================================
// 错误横幅组件 — 红色错误提示 + 可选的"
// ===================================================================

interface ErrorBannerProps {
  message: string;
  onClose?: () => void;
  onRetry?: () => void;
}

export function ErrorBanner({ message, onClose, onRetry }: ErrorBannerProps) {
  return (
    <div
      className="flex items-center justify-between gap-3 rounded-md bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700"
      role="alert"
    >
      <div className="flex items-center gap-2 min-w-0">
        <svg className="h-5 w-5 shrink-0 text-red-400" viewBox="0 0 20 20" fill="currentColor">
          <path
            fillRule="evenodd"
            d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
            clipRule="evenodd"
          />
        </svg>
        <span className="truncate">{message}</span>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        {onRetry && (
          <button
            type="button"
            className="text-sm font-medium text-red-700 underline hover:text-red-800"
            onClick={onRetry}
          >
            重试
          </button>
        )}
        {onClose && (
          <button
            type="button"
            className="text-red-400 hover:text-red-600"
            onClick={onClose}
          >
            <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
              <path
                fillRule="evenodd"
                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                clipRule="evenodd"
              />
            </svg>
          </button>
        )}
      </div>
    </div>
  );
}
