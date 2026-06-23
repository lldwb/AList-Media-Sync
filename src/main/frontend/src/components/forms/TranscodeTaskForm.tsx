// ===================================================================
// 转码任务表单组件 — 创建独立转码任务
// ===================================================================
import { useState, type FormEvent } from 'react';
import type { StorageEngineVO, TranscodeTaskCreateDTO, TargetFormat } from '@/types/api';
import { DirectoryTreeSelector } from '@/components/ui/DirectoryTreeSelector';
import { validatePath, validatePositiveInt } from '@/utils/validate';

interface TranscodeTaskFormProps {
  engines: StorageEngineVO[];
  onSubmit: (values: TranscodeTaskCreateDTO) => Promise<void>;
  onCancel: () => void;
  loading?: boolean;
}

interface FormErrors {
  sourceEngineId?: string;
  targetEngineId?: string;
  sourceFilePath?: string;
  targetFilePath?: string;
  bitrate?: string;
}

export function TranscodeTaskForm({ engines, onSubmit, onCancel, loading }: TranscodeTaskFormProps) {
  const [sourceEngineId, setSourceEngineId] = useState(0);
  const [targetEngineId, setTargetEngineId] = useState(0);
  const [sourceFilePath, setSourceFilePath] = useState('');
  const [targetFilePath, setTargetFilePath] = useState('');
  const [targetFormat, setTargetFormat] = useState<TargetFormat>('MP3');
  const [bitrate, setBitrate] = useState('128');
  const [sourceDirectoryTranscode, setSourceDirectoryTranscode] = useState(false);
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  const validate = (): boolean => {
    const newErrors: FormErrors = {};

    if (!sourceEngineId) newErrors.sourceEngineId = '请选择源存储引擎';

    // 源目录转码时目标路径可选且不需要目标引擎
    if (sourceDirectoryTranscode) {
      // 勾选源目录转码但未选源引擎时提示
      if (!sourceEngineId) {
        newErrors.sourceEngineId = '请先选择源存储引擎';
      }
    } else {
      if (!targetEngineId) newErrors.targetEngineId = '请选择目标存储引擎';
    }

    const srcCheck = validatePath(sourceFilePath, '源文件路径');
    if (!srcCheck.valid) newErrors.sourceFilePath = srcCheck.error;

    // 源目录转码时目标路径可选，不校验
    if (!sourceDirectoryTranscode) {
      const tgtCheck = validatePath(targetFilePath, '目标文件路径');
      if (!tgtCheck.valid) newErrors.targetFilePath = tgtCheck.error;
    }

    const bitCheck = validatePositiveInt(bitrate, '码率');
    if (!bitCheck.valid) newErrors.bitrate = bitCheck.error;

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitError(null);

    if (!validate()) return;

    try {
      await onSubmit({
        sourceEngineId: sourceEngineId || undefined,
        targetEngineId: sourceDirectoryTranscode ? undefined : targetEngineId,
        sourceFilePath: sourceFilePath.trim(),
        targetFilePath: sourceDirectoryTranscode ? '' : targetFilePath.trim(),
        targetFormat,
        bitrate: parseInt(bitrate, 10) * 1000, // kbps → bps
        sourceDirectoryTranscode: sourceDirectoryTranscode,
      });
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : '提交失败');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-lg shadow-xl max-w-lg w-full mx-4 p-6" onClick={(e) => e.stopPropagation()}>
        <h3 className="text-lg font-semibold text-gray-900 mb-4">创建转码任务</h3>

        {submitError && (
          <div className="mb-4 rounded-md bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
            {submitError}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                源存储引擎 <span className="text-red-500">*</span>
              </label>
              <select
                value={sourceEngineId || ''}
                onChange={(e) => setSourceEngineId(Number(e.target.value))}
                className={`block w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                  errors.sourceEngineId ? 'border-red-400' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
                }`}
              >
                <option value="">请选择...</option>
                {engines.map((eng) => (
                  <option key={eng.id} value={eng.id}>{eng.name}</option>
                ))}
              </select>
              {errors.sourceEngineId && <p className="mt-1 text-xs text-red-600">{errors.sourceEngineId}</p>}
            </div>
            {/* 源目录转码时隐藏目标存储引擎 */}
            {!sourceDirectoryTranscode && (
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
                  <option key={eng.id} value={eng.id}>{eng.name}</option>
                ))}
              </select>
              {errors.targetEngineId && <p className="mt-1 text-xs text-red-600">{errors.targetEngineId}</p>}
            </div>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              源文件路径 <span className="text-red-500">*</span>
            </label>
            <DirectoryTreeSelector
              engineId={sourceEngineId}
              value={sourceFilePath}
              onChange={setSourceFilePath}
              placeholder="/media/recording.flv"
              disabled={!sourceEngineId}
            />
            {errors.sourceFilePath && <p className="mt-1 text-xs text-red-600">{errors.sourceFilePath}</p>}
          </div>

          {/* 源目录转码时隐藏目标文件路径 */}
          {!sourceDirectoryTranscode && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              目标文件路径 <span className="text-red-500">*</span>
            </label>
            <DirectoryTreeSelector
              engineId={targetEngineId}
              value={targetFilePath}
              onChange={setTargetFilePath}
              placeholder="/media/recording.mp3"
              disabled={!targetEngineId}
            />
            {errors.targetFilePath && <p className="mt-1 text-xs text-red-600">{errors.targetFilePath}</p>}
          </div>
          )}

          <div>
            <label className="flex items-center gap-2 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={sourceDirectoryTranscode}
                onChange={(e) => {
                  setSourceDirectoryTranscode(e.target.checked);
                  if (e.target.checked) {
                    // 自动填充源文件所在目录作为目标路径
                    const idx = sourceFilePath.lastIndexOf('/');
                    setTargetFilePath(idx > 0 ? sourceFilePath.substring(0, idx) : '/');
                    setErrors((prev) => ({ ...prev, targetFilePath: undefined, targetEngineId: undefined }));
                  }
                }}
                className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
              />
              <span className="text-sm text-gray-700">
                源目录转码（输出至源文件所在目录）
              </span>
            </label>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">目标格式</label>
              <select
                value={targetFormat}
                onChange={(e) => setTargetFormat(e.target.value as TargetFormat)}
                className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="MP3">MP3</option>
                <option value="MP4">MP4</option>
                <option value="FLV">FLV</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                码率（kbps） <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                min={1}
                value={bitrate}
                onChange={(e) => setBitrate(e.target.value)}
                className={`block w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                  errors.bitrate ? 'border-red-400' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
                }`}
                placeholder="128"
              />
              {errors.bitrate && <p className="mt-1 text-xs text-red-600">{errors.bitrate}</p>}
            </div>
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
              {loading ? '创建中...' : '创建'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
