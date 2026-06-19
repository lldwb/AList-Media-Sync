// ===================================================================
// 会话超时 hook — 30 分钟无交互自动登出
// ===================================================================
import { useEffect, useRef } from 'react';
import { useAuth } from '@/auth/AuthContext';

const SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 分钟

/** 监听用户交互事件，超时后自动登出并跳转登录页 */
export function useSessionTimeout() {
  const { auth, logout } = useAuth();
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    // 未认证时不启动超时计时
    if (!auth) {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      return;
    }

    const resetTimer = () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      timerRef.current = setTimeout(() => {
        logout();
        window.location.hash = '#/login';
      }, SESSION_TIMEOUT_MS);
    };

    // 首次设置
    resetTimer();

    // 监听交互事件
    const events = ['mousedown', 'keydown', 'scroll', 'touchstart'] as const;
    for (const evt of events) {
      window.addEventListener(evt, resetTimer, { passive: true });
    }

    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      for (const evt of events) {
        window.removeEventListener(evt, resetTimer);
      }
    };
  }, [auth, logout]);
}
