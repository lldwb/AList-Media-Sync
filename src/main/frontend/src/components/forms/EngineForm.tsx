// ===================================================================
// 存储引擎表单组件 — 创建/编辑（支持 AList/LOCAL 双类型）
// ===================================================================
import { useState, useEffect, type FormEvent } from 'react';
import type { StorageEngineVO, StorageEngineCreateDTO, EngineType } from '@/types/api';
import { validateRequired, validateUrl } from '@/utils/validate';

interface EngineFormProps {
  initialValues?: StorageEngineVO | null;
  onSubmit: (values: StorageEngineCreateDTO) => Promise<void>;
  onCancel: () => void;
  loading?: boolean;
}

interface FormErrors {
  name?: string;
  engineType?: string;
  baseUrl?: string;
  token?: string;
  localPath?: string;
}

export function EngineForm({ initialValues, onSubmit, onCancel, loading }: EngineFormProps) {
  const [name, setName] = useState(initialValues?.name ?? '');
  const [engineType, setEngineType] = useState<EngineType>(
    (initialValues?.engineType as EngineType) ?? 'ALIST'
  );
  const [baseUrl, setBaseUrl] = useState(initialValues?.baseUrl ?? '');
  const [token, setToken] = useState('');
  const [localPath, setLocalPath] = useState(initialValues?.localPath ?? '');
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  const isEdit = !!initialValues;

  useEffect(() => {
    setErrors({});
    setSubmitError(null);
  }, [initialValues]);

  const validate = (): boolean => {
    const newErrors: FormErrors = {};

    const nameCheck = validateRequired(name, '名称');
    if (!nameCheck.valid) newErrors.name = nameCheck.error;

    if (engineType === 'ALIST') {
      const urlCheck = validateUrl(baseUrl, '服务器地址');
      if (!urlCheck.valid) newErrors.baseUrl = urlCheck.error;

      if (!isEdit) {
        const tokenCheck = validateRequired(token, 'API 令牌');
        if (!tokenCheck.valid) newErrors.token = tokenCheck.error;
      }
    } else if (engineType === 'LOCAL') {
      const pathCheck = validateRequired(localPath, '本地路径');
      if (!pathCheck.valid) newErrors.localPath = pathCheck.error;
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
      engineType,
      baseUrl: engineType === 'ALIST' ? baseUrl.trim() : undefined,
      token: engineType === 'ALIST' && token.trim() ? token.trim() : undefined,
      localPath: engineType === 'LOCAL' ? localPath.trim() : undefined,
    };

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
          {/* 名称 */}
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
              placeholder="例如：我的存储服务器"
            />
            {errors.name && <p className="mt-1 text-xs text-red-600">{errors.name}</p>}
          </div>

          {/* 引擎类型 */}
          <div>
            <label htmlFor="eng-type" className="block text-sm font-medium text-gray-700 mb-1">
              引擎类型 <span className="text-red-500">*</span>
            </label>
            <select
              id="eng-type"
              value={engineType}
              onChange={(e) => setEngineType(e.target.value as EngineType)}
              disabled={isEdit}
              className={`block w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                isEdit ? 'bg-gray-100 text-gray-500 cursor-not-allowed' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
              }`}
            >
              <option value="ALIST">AList 远程存储</option>
              <option value="LOCAL">本地路径</option>
            </select>
            {isEdit && <p className="mt-1 text-xs text-gray-400">引擎类型创建后不可更改</p>}
          </div>

          {/* AList 专属字段 */}
          {engineType === 'ALIST' && (
            <>
              <div>
                <label htmlFor="eng-url" className="block text-sm font-medium text-gray-700 mb-1">
                  服务器地址 <span className="text-red-500">*</span>
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
                  placeholder={isEdit ? '留空则不修改' : 'API Token'}
                />
                {errors.token && <p className="mt-1 text-xs text-red-600">{errors.token}</p>}
              </div>
            </>
          )}

          {/* LOCAL 专属字段 */}
          {engineType === 'LOCAL' && (
            <div>
              <label htmlFor="eng-path" className="block text-sm font-medium text-gray-700 mb-1">
                本地路径 <span className="text-red-500">*</span>
              </label>
              <input
                id="eng-path"
                type="text"
                value={localPath}
                onChange={(e) => setLocalPath(e.target.value)}
                className={`block w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-1 ${
                  errors.localPath ? 'border-red-400 focus:border-red-500 focus:ring-red-500' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
                }`}
                placeholder="例如：/data/media 或 C:\media"
              />
              {errors.localPath && <p className="mt-1 text-xs text-red-600">{errors.localPath}</p>}
            </div>
          )}

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
