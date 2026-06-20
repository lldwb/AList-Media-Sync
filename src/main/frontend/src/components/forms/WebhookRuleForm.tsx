// ===================================================================
// Webhook 规则表单组件 — 创建/编辑（支持录播存储引擎关联）
// ===================================================================
import { useState, useEffect, type FormEvent } from 'react';
import type { StorageEngineVO, WebhookRuleVO, WebhookRuleCreateDTO, WebhookEventType, RuleAction } from '@/types/api';
import { validateRequired } from '@/utils/validate';

interface WebhookRuleFormProps {
  engines: StorageEngineVO[];
  initialValues?: WebhookRuleVO | null;
  onSubmit: (values: WebhookRuleCreateDTO) => Promise<void>;
  onCancel: () => void;
  loading?: boolean;
}

interface FormErrors {
  name?: string;
  recordingEngineId?: string;
  recordingPath?: string;
  targetEngineId?: string;
  targetFilePath?: string;
}

export function WebhookRuleForm({ engines, initialValues, onSubmit, onCancel, loading }: WebhookRuleFormProps) {
  const [name, setName] = useState(initialValues?.name ?? '');
  const [triggerEventType, setTriggerEventType] = useState<WebhookEventType>(initialValues?.triggerEventType ?? 'FILE_CLOSED');
  const [roomIdFilter, setRoomIdFilter] = useState(initialValues?.roomIdFilter?.toString() ?? '');
  const [action, setAction] = useState<RuleAction>(initialValues?.action ?? 'BOTH');
  const [recordingEngineId, setRecordingEngineId] = useState(initialValues?.recordingEngineId ?? 0);
  const [recordingPath, setRecordingPath] = useState(initialValues?.recordingPath ?? '');
  const [targetEngineId, setTargetEngineId] = useState(initialValues?.targetEngineId ?? 0);
  const [targetFilePath, setTargetFilePath] = useState(initialValues?.targetFilePath ?? '');
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  const isEdit = !!initialValues;

  // TRANSCODE_ONLY 和 BOTH 需要录播存储引擎
  const needsRecording = action === 'TRANSCODE_ONLY' || action === 'BOTH';

  useEffect(() => {
    setErrors({});
    setSubmitError(null);
  }, [initialValues]);

  const validate = (): boolean => {
    const newErrors: FormErrors = {};

    const nameCheck = validateRequired(name, '规则名称');
    if (!nameCheck.valid) newErrors.name = nameCheck.error;

    if (needsRecording) {
      if (!recordingEngineId) newErrors.recordingEngineId = '请选择录播存储引擎';
      if (!recordingPath.trim()) {
        newErrors.recordingPath = '录播文件路径不能为空';
      }
    }

    if (!targetEngineId) newErrors.targetEngineId = '请选择目标存储引擎';
    if (!targetFilePath.trim()) {
      newErrors.targetFilePath = '目标文件路径不能为空';
    } else if (!targetFilePath.trim().startsWith('/')) {
      newErrors.targetFilePath = '目标文件路径必须以 "/" 开头';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitError(null);

    if (!validate()) return;

    try {
      await onSubmit({
        name: name.trim(),
        triggerEventType,
        roomIdFilter: roomIdFilter.trim() ? parseInt(roomIdFilter, 10) : undefined,
        action,
        recordingEngineId: needsRecording ? recordingEngineId : undefined,
        recordingPath: needsRecording ? recordingPath.trim() : undefined,
        targetEngineId,
        targetFilePath: targetFilePath.trim(),
      });
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : '提交失败');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-lg shadow-xl max-w-lg w-full mx-4 p-6" onClick={(e) => e.stopPropagation()}>
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          {isEdit ? '编辑 Webhook 规则' : '创建 Webhook 规则'}
        </h3>

        {submitError && (
          <div className="mb-4 rounded-md bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
            {submitError}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* 规则名称 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              规则名称 <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className={`block w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                errors.name ? 'border-red-400' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
              }`}
              placeholder="例如：自动转码录制文件"
            />
            {errors.name && <p className="mt-1 text-xs text-red-600">{errors.name}</p>}
          </div>

          {/* 触发事件类型 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">触发事件类型</label>
            <select
              value={triggerEventType}
              onChange={(e) => setTriggerEventType(e.target.value as WebhookEventType)}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            >
              <option value="FILE_CLOSED">文件关闭</option>
              <option value="FILE_OPENED">文件打开</option>
              <option value="FILE_RENAMED">文件重命名</option>
              <option value="SESSION_STARTED">录制开始</option>
              <option value="SESSION_ENDED">录制结束</option>
              <option value="SPACE_FULL">空间不足</option>
              <option value="OTHER">其他</option>
            </select>
          </div>

          {/* 房间号 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">房间号（可选）</label>
            <input
              type="number"
              value={roomIdFilter}
              onChange={(e) => setRoomIdFilter(e.target.value)}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="留空则匹配所有房间"
            />
          </div>

          {/* 操作 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">触发后续操作</label>
            <select
              value={action}
              onChange={(e) => setAction(e.target.value as RuleAction)}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            >
              <option value="SYNC_ONLY">仅同步</option>
              <option value="TRANSCODE_ONLY">仅转 MP3</option>
              <option value="BOTH">同步 + 转 MP3</option>
            </select>
          </div>

          {/* 录播存储引擎（TRANSCODE_ONLY / BOTH 时显示） */}
          {needsRecording && (
            <>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  录播存储引擎 <span className="text-red-500">*</span>
                </label>
                <select
                  value={recordingEngineId || ''}
                  onChange={(e) => setRecordingEngineId(Number(e.target.value))}
                  className={`block w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                    errors.recordingEngineId ? 'border-red-400' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
                  }`}
                >
                  <option value="">请选择...</option>
                  {engines.map((eng) => (
                    <option key={eng.id} value={eng.id}>
                      {eng.name} ({eng.engineType === 'ALIST' ? 'AList' : '本地'})
                    </option>
                  ))}
                </select>
                {errors.recordingEngineId && <p className="mt-1 text-xs text-red-600">{errors.recordingEngineId}</p>}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  录播文件路径 <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={recordingPath}
                  onChange={(e) => setRecordingPath(e.target.value)}
                  className={`block w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                    errors.recordingPath ? 'border-red-400' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
                  }`}
                  placeholder="例如：/录播/2024"
                />
                {errors.recordingPath && <p className="mt-1 text-xs text-red-600">{errors.recordingPath}</p>}
              </div>
            </>
          )}

          {/* 目标存储引擎 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              目标存储引擎 <span className="text-red-500">*</span>
            </label>
            <select
              value={targetEngineId || ''}
              onChange={(e) => setTargetEngineId(Number(e.target.value))}
              className={`block w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                errors.targetEngineId ? 'border-red-400' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
              }`}
            >
              <option value="">请选择...</option>
              {engines.map((eng) => (
                <option key={eng.id} value={eng.id}>
                  {eng.name} ({eng.engineType === 'ALIST' ? 'AList' : '本地'})
                </option>
              ))}
            </select>
            {errors.targetEngineId && <p className="mt-1 text-xs text-red-600">{errors.targetEngineId}</p>}
          </div>

          {/* 目标文件路径 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              目标文件路径 <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={targetFilePath}
              onChange={(e) => setTargetFilePath(e.target.value)}
              className={`block w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                errors.targetFilePath ? 'border-red-400' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
              }`}
              placeholder="例如：/转码/输出"
            />
            {errors.targetFilePath && <p className="mt-1 text-xs text-red-600">{errors.targetFilePath}</p>}
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
