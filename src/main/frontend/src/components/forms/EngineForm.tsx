// ===================================================================
// 存储引擎表单组件 — 创建/编辑
// ===================================================================
import { useState, useEffect, type FormEvent } from 'react';
import type { StorageEngineVO, StorageEngineCreateDTO } from '@/types/api';
import { validateRequired, validateUrl } from '@/utils/validate';

interface EngineFormProps {
  /** 编辑模式时的初始值（创建时为 null） */
  initialValues?: StorageEngineVO | null;
  /** 提交回调 */
  onSubmit: (values: StorageEngineCreateDTO) => Promise<void>;
  /** 取消回调 */
  onCancel: () => void;
  /** 加载状态 */
  loading?: boolean;
}

interface FormErrors {
  name?: string;
  baseUrl?: string;
  username?: string;
  token?: string;
}

export function EngineForm({ initialValues, onSubmit, onCancel, loading }: EngineFormProps) {
  const [name, setName] = useState(initialValues?.name ?? '');
  const [baseUrl, setBaseUrl] = useState(initialValues?.baseUrl ?? '');
  const [username, setUsername] = useState(initialValues?.username ?? '');
  const [token, setToken] = useState(''); // 编辑时不回显 Token
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  const isEdit = !!initialValues;

  // 编辑模式下重置错误
  useEffect(() => {
    setErrors({});
    setSubmitError(null);
  }, [initialValues]);

  const validate = (): boolean => {
    const newErrors: FormErrors = {};

    const nameCheck = validateRequired(name, '名称');
    if (!nameCheck.valid) newErrors.name = nameCheck.error;

    const urlCheck = validateUrl(baseUrl, '服务器地址');
    if (!urlCheck.valid) newErrors.baseUrl = urlCheck.error;

    const userCheck = validateRequired(username, '用户名');
    if (!userCheck.valid) newErrors.username = userCheck.error;

    // 创建时 Token 必填，编辑时可选
    if (!isEdit) {
      const tokenCheck = validateRequired(token, 'API 令牌');
      if (!tokenCheck.valid) newErrors.token = tokenCheck.error;
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitError(null);

    if (!validate()) return;

    const values: StorageEngineCreateDTO = {
      name: name.trim(),
      baseUrl: baseUrl.trim(),
      username: username.trim(),
      token: token.trim(),
    };

    // 编辑时 Token 不传（可选），但 DTO 要求 token 字段存在
    if (isEdit && !token.trim()) {
      values.token = ''; // 后端 update 时忽略空 token
    }

    try {
      await onSubmit(values);
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : '提交失败');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-lg shadow-xl max-w-lg w-full mx-4 p-6" onClick={(e) => e.stopPropagation()}>
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          {isEdit ? `编辑存储引擎：${initialValues?.name}` : '添加存储引擎'}
        </h3>

        {submitError && (
          <div className="mb-4 rounded-md bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
            {submitError}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="eng-name" className="block text-sm font-medium text-gray-700 mb-1">
              名称 <span className="text-red-500">*</span>
            </label>
            <input
              id="eng-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className={`block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-1 ${
                errors.name ? 'border-red-400 focus:border-red-500 focus:ring-red-500' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
              }`}
              placeholder="例如：我的 AList 服务器"
            />
            {errors.name && <p className="mt-1 text-xs text-red-600">{errors.name}</p>}
          </div>

          <div>
            <label htmlFor="eng-url" className="block text-sm font-medium text-gray-700 mb-1">
              AList API 地址 <span className="text-red-500">*</span>
            </label>
            <input
              id="eng-url"
              type="text"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              className={`block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-1 ${
                errors.baseUrl ? 'border-red-400 focus:border-red-500 focus:ring-red-500' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
              }`}
              placeholder="例如：https://alist.example.com"
            />
            {errors.baseUrl && <p className="mt-1 text-xs text-red-600">{errors.baseUrl}</p>}
          </div>

          <div>
            <label htmlFor="eng-user" className="block text-sm font-medium text-gray-700 mb-1">
              用户名 <span className="text-red-500">*</span>
            </label>
            <input
              id="eng-user"
              type="text"
              autoComplete="off"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className={`block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-1 ${
                errors.username ? 'border-red-400 focus:border-red-500 focus:ring-red-500' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
              }`}
              placeholder="AList 登录用户名"
            />
            {errors.username && <p className="mt-1 text-xs text-red-600">{errors.username}</p>}
          </div>

          <div>
            <label htmlFor="eng-token" className="block text-sm font-medium text-gray-700 mb-1">
              API 令牌 {!isEdit && <span className="text-red-500">*</span>}
            </label>
            <input
              id="eng-token"
              type="password"
              autoComplete="off"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              className={`block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-1 ${
                errors.token ? 'border-red-400 focus:border-red-500 focus:ring-red-500' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
              }`}
              placeholder={isEdit ? '留空则不修改' : 'AList API Token'}
            />
            {errors.token && <p className="mt-1 text-xs text-red-600">{errors.token}</p>}
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onCancel}
              className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={loading}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
            >
              {loading ? '保存中...' : '保存'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
