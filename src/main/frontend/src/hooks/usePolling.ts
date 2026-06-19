// ===================================================================
// 通用轮询 hook — 定时调用 fetcher，满足 stopCondition 时停止
// ===================================================================
import { useEffect, useRef, useState, useCallback } from 'react';

export function usePolling<T>(
  fetcher: () => Promise<T>,
  interval: number = 5000,
  stopCondition?: (data: T) => boolean,
) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [isPolling, setIsPolling] = useState(false);
  const mountedRef = useRef(true);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const poll = useCallback(async () => {
    try {
      const result = await fetcher();
      if (!mountedRef.current) return;
      setData(result);
      setError(null);
      const shouldStop = stopCondition?.(result) ?? false;
      setIsPolling(!shouldStop);
      if (!shouldStop) {
        timerRef.current = setTimeout(poll, interval);
      }
    } catch (err) {
      if (!mountedRef.current) return;
      setError(err instanceof Error ? err : new Error('轮询失败'));
      // 错误后继续重试
      timerRef.current = setTimeout(poll, interval);
    }
  }, [fetcher, interval, stopCondition]);

  useEffect(() => {
    mountedRef.current = true;
    setIsPolling(true);
    poll();

    return () => {
      mountedRef.current = false;
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, [poll]);

  return { data, error, isPolling };
}
