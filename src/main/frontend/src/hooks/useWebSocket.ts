// ===================================================================
// WebSocket 连接管理 Hook
// 负责建立连接、消息分发、断线指数退避重连、页面卸载时断开
// ===================================================================
import { useEffect, useRef, useCallback, useState } from 'react';
import type { WsMessage } from '@/types/api';
import { AUTH_CREDENTIALS_KEY } from '@/api/client';

/** WebSocket 连接状态 */
export type WsConnectionState = 'CONNECTING' | 'OPEN' | 'CLOSED' | 'AUTH_FAILED';

/** 消息处理回调 */
export type WsMessageHandler = (message: WsMessage) => void;

/** 重连配置 */
const INITIAL_RECONNECT_MS = 1000;  // 初始重连间隔 1 秒
const MAX_RECONNECT_MS = 30000;     // 最大重连间隔 30 秒

/**
 * 构建 WebSocket URL
 * 根据当前页面协议自动选择 ws:// 或 wss://
 */
function buildWsUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws/events`;
}

/**
 * useWebSocket Hook
 *
 * @param onMessage 收到消息时的回调函数
 * @returns 连接状态
 *
 * @example
 * ```tsx
 * const { connectionState } = useWebSocket((message) => {
 *   switch (message.type) {
 *     case 'SYNC_PROGRESS':
 *       setSyncTasks(prev => prev.map(t => t.id === message.payload.taskId ? { ...t, ...message.payload } : t));
 *       break;
 *   }
 * });
 * ```
 */
export function useWebSocket(onMessage: WsMessageHandler) {
  const [connectionState, setConnectionState] = useState<WsConnectionState>('CLOSED');
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectAttemptRef = useRef(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onMessageRef = useRef(onMessage);

  // 保持回调最新
  onMessageRef.current = onMessage;

  const clearReconnect = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
  }, []);

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    const creds = sessionStorage.getItem(AUTH_CREDENTIALS_KEY);
    if (!creds) {
      setConnectionState('AUTH_FAILED');
      return;
    }

    setConnectionState('CONNECTING');
    const url = buildWsUrl();
    const ws = new WebSocket(url);

    ws.onopen = () => {
      setConnectionState('OPEN');
      reconnectAttemptRef.current = 0;
    };

    ws.onmessage = (event) => {
      try {
        const message: WsMessage = JSON.parse(event.data);
        onMessageRef.current(message);
      } catch (e) {
        console.error('解析 WebSocket 消息失败', e);
      }
    };

    ws.onclose = (event) => {
      setConnectionState('CLOSED');

      // 认证失败（HTTP 401 在 onerror 中处理，onclose code 可能为 1006）
      // 检查是否因认证问题关闭
      if (event.code === 4001) {
        setConnectionState('AUTH_FAILED');
        sessionStorage.removeItem(AUTH_CREDENTIALS_KEY);
        window.location.hash = '#/login';
        return;
      }

      // 指数退避重连
      const attempt = reconnectAttemptRef.current;
      const delay = Math.min(INITIAL_RECONNECT_MS * Math.pow(2, attempt), MAX_RECONNECT_MS);
      reconnectAttemptRef.current = attempt + 1;

      console.log(`WebSocket 断开，${delay}ms 后重连（第 ${attempt + 1} 次）`);
      reconnectTimerRef.current = setTimeout(connect, delay);
    };

    ws.onerror = () => {
      // onclose 会在 onerror 之后触发，统一在 onclose 中处理重连
      // 检查认证凭据是否仍然有效
      const currentCreds = sessionStorage.getItem(AUTH_CREDENTIALS_KEY);
      if (!currentCreds) {
        setConnectionState('AUTH_FAILED');
      }
    };

    wsRef.current = ws;
  }, []);

  const disconnect = useCallback(() => {
    clearReconnect();
    if (wsRef.current) {
      wsRef.current.onclose = null; // 防止触发重连
      wsRef.current.close();
      wsRef.current = null;
    }
    setConnectionState('CLOSED');
  }, [clearReconnect]);

  useEffect(() => {
    connect();

    return () => {
      disconnect();
    };
  }, [connect, disconnect]);

  return { connectionState };
}
