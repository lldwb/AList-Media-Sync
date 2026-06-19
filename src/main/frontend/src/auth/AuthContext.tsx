// ===================================================================
// 认证上下文 — React Context + useAuth hook
// ===================================================================
import {
  createContext,
  useContext,
  useState,
  useCallback,
  useMemo,
  type ReactNode,
} from 'react';
import { api, AUTH_CREDENTIALS_KEY, AUTH_USERNAME_KEY } from '@/api/client';

export interface AuthState {
  username: string;
  credentials: string; // Base64(username:password)
}

interface AuthContextValue {
  auth: AuthState | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  redirectPath: string | null;
  setRedirectPath: (path: string | null) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthState | null>(() => {
    const creds = sessionStorage.getItem(AUTH_CREDENTIALS_KEY);
    const username = sessionStorage.getItem(AUTH_USERNAME_KEY);
    return creds && username ? { username, credentials: creds } : null;
  });

  const [redirectPath, setRedirectPath] = useState<string | null>(null);

  const login = useCallback(async (username: string, password: string) => {
    const credentials = btoa(`${username}:${password}`);

    // 先临时存储凭据以进行 API 调用验证
    sessionStorage.setItem(AUTH_CREDENTIALS_KEY, credentials);
    sessionStorage.setItem(AUTH_USERNAME_KEY, username);

    try {
      // 通过一次真实 API 调用验证凭据
      await api.get('/dashboard/stats');
      // 凭据有效，更新状态
      setAuth({ username, credentials });
    } catch (err) {
      // 验证失败，清除临时凭据
      sessionStorage.removeItem(AUTH_CREDENTIALS_KEY);
      sessionStorage.removeItem(AUTH_USERNAME_KEY);
      throw err;
    }
  }, []);

  const logout = useCallback(() => {
    sessionStorage.removeItem(AUTH_CREDENTIALS_KEY);
    sessionStorage.removeItem(AUTH_USERNAME_KEY);
    setAuth(null);
  }, []);

  const value = useMemo(
    () => ({ auth, login, logout, redirectPath, setRedirectPath }),
    [auth, login, logout, redirectPath],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** useAuth hook — 在 AuthProvider 内使用 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth 必须在 AuthProvider 内部使用');
  }
  return ctx;
}
