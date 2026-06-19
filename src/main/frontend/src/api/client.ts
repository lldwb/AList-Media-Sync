// ===================================================================
// HTTP 请求封装 — 基于 fetch + Basic Auth + 401 拦截
// ===================================================================
import type { ApiResult } from '@/types/api';

const API_BASE = '/api';
const TIMEOUT_MS = 15_000;

/** 存储认证凭据的 sessionStorage key */
const AUTH_CREDENTIALS_KEY = 'auth_credentials';
const AUTH_USERNAME_KEY = 'auth_username';

/* ---- 内部辅助 ---- */

function getAuthHeader(): Record<string, string> {
  const creds = sessionStorage.getItem(AUTH_CREDENTIALS_KEY);
  if (creds) {
    return { Authorization: `Basic ${creds}` };
  }
  return {};
}

function handle401(): void {
  sessionStorage.removeItem(AUTH_CREDENTIALS_KEY);
  sessionStorage.removeItem(AUTH_USERNAME_KEY);
  window.location.hash = '#/login';
}

/** 将 ISO 8601 字符串中的 T 替换为后端可接受的格式（可选） */
async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...getAuthHeader(),
  };

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), TIMEOUT_MS);

  try {
    const res = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });

    if (res.status === 401) {
      handle401();
      throw new Error('未认证，请重新登录');
    }

    if (!res.ok) {
      // 尝试解析为 ApiResult 获取错误消息
      let message: string;
      try {
        const errResult: ApiResult<unknown> = await res.json();
        message = errResult.message || `请求失败 (HTTP ${res.status})`;
      } catch {
        message = `请求失败 (HTTP ${res.status})`;
      }
      throw new Error(message);
    }

    const result: ApiResult<T> = await res.json();
    if (result.code !== 200) {
      throw new Error(result.message || '请求失败');
    }

    return result.data as T;
  } catch (err: unknown) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      throw new Error('请求超时，请检查网络连接');
    }
    throw err;
  } finally {
    clearTimeout(timeoutId);
  }
}

/* ---- 公开 API ---- */

export const api = {
  /** GET 请求 */
  get<T>(path: string): Promise<T> {
    return request<T>('GET', path);
  },

  /** POST 请求 */
  post<T>(path: string, body?: unknown): Promise<T> {
    return request<T>('POST', path, body);
  },

  /** PUT 请求 */
  put<T>(path: string, body?: unknown): Promise<T> {
    return request<T>('PUT', path, body);
  },

  /** DELETE 请求 */
  del<T>(path: string): Promise<T> {
    return request<T>('DELETE', path);
  },
};

/** 导出 credentials 读写方法，供 AuthContext 使用 */
export { AUTH_CREDENTIALS_KEY, AUTH_USERNAME_KEY };
