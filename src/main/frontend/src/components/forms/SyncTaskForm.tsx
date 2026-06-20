// ===================================================================
// 同步任务表单组件 — 创建/编辑 + 条件字段切换 + cron 预览
// ===================================================================
import { useState, useEffect, type FormEvent } from 'react';
import type {
  StorageEngineVO,
  SyncTaskVO,
  SyncTaskCreateDTO,
  SyncMode,
  ScheduleType,
  ConflictStrategy,
} from '@/types/api';
import { parseCron } from '@/utils/cron';
import { CronBuilder } from '@/components/ui/CronBuilder';
import { validateRequired, validatePath, validatePositiveInt } from '@/utils/validate';
import type { ValidationResult } from '@/utils/validate';

interface SyncTaskFormProps {
  /** 可用引擎列表（下拉选项） */
  engines: StorageEngineVO[];
  /** 编辑模式时的初始值 */
  initialValues?: SyncTaskVO | null;
  /** 提交回调 */
  onSubmit: (values: SyncTaskCreateDTO) => Promise<void>;
  /** 取消回调 */
  onCancel: () => void;
  loading?: boolean;
}

interface FormErrors {
  name?: string;
  sourceEngineId?: string;
  targetEngineId?: string;
  sourcePath?: string;
  targetPath?: string;
  cronExpression?: string;
  intervalSeconds?: string;
}

export function SyncTaskForm({ engines, initialValues, onSubmit, onCancel, loading }: SyncTaskFormProps) {
  const [name, setName] = useState(initialValues?.name ?? '');
  const [sourceEngineId, setSourceEngineId] = useState(initialValues?.sourceEngineId ?? 0);
  const [targetEngineId, setTargetEngineId] = useState(initialValues?.targetEngineId ?? 0);
  const [sourcePath, setSourcePath] = useState(initialValues?.sourcePath ?? '');
  const [targetPath, setTargetPath] = useState(initialValues?.targetPath ?? '');
  const [syncMode, setSyncMode] = useState<SyncMode>(initialValues?.syncMode ?? 'NEW_ONLY');
  const [scheduleType, setScheduleType] = useState<ScheduleType>(initialValues?.scheduleType ?? 'MANUAL');
  const [intervalSeconds, setIntervalSeconds] = useState(initialValues?.intervalSeconds?.toString() ?? '');
  const [cronExpression, setCronExpression] = useState(initialValues?.cronExpression ?? '');
  const [excludePatterns, setExcludePatterns] = useState(initialValues?.excludePatterns ?? '');
  const [conflictStrategy, setConflictStrategy] = useState<ConflictStrategy>(initialValues?.conflictStrategy ?? 'SKIP');
  const [convertToMp3, setConvertToMp3] = useState(initialValues?.transcodeEnabled ?? false);

  const [errors, setErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [cronPreview, setCronPreview] = useState<string | null>(null);
  const [cronNextTime, setCronNextTime] = useState<string | null>(null);

  const isEdit = !!initialValues;

  // 实时 cron 预览
  useEffect(() => {
    if (scheduleType !== 'CRON' || !cronExpression.trim()) {
      setCronPreview(null);
      setCronNextTime(null);
      return;
    }
    const result = parseCron(cronExpression);
    if (result.valid) {
      setCronPreview(result.description ?? null);
      setCronNextTime(result.nextExecution?.toLocaleString('zh-CN') ?? null);
    } else {
      setCronPreview(null);
      setCronNextTime(null);
    }
  }, [cronExpression, scheduleType]);

  const validate = (): boolean => {
    const newErrors: FormErrors = {};

    const nameCheck = validateRequired(name, '任务名称');
    if (!nameCheck.valid) newErrors.name = nameCheck.error;

    if (!sourceEngineId) newErrors.sourceEngineId = '请选择源存储引擎';
    if (!targetEngineId) newErrors.targetEngineId = '请选择目标存储引擎';

    const srcPathCheck = validatePath(sourcePath, '源目录路径');
    if (!srcPathCheck.valid) newErrors.sourcePath = srcPathCheck.error;

    const tgtPathCheck = validatePath(targetPath, '目标目录路径');
    if (!tgtPathCheck.valid) newErrors.targetPath = tgtPathCheck.error;

    if (scheduleType === 'CRON') {
      const cronCheck = validateRequired(cronExpression, 'Cron 表达式');
      if (!cronCheck.valid) {
        newErrors.cronExpression = cronCheck.error;
      } else {
        const parsed = parseCron(cronExpression);
        if (!parsed.valid) {
          newErrors.cronExpression = parsed.error;
        }
      }
    }

    if (scheduleType === 'INTERVAL') {
      const intCheck: ValidationResult = validatePositiveInt(intervalSeconds, '间隔秒数');
      if (!intCheck.valid) newErrors.intervalSeconds = intCheck.error;
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitError(null);

    if (!validate()) return;

    const values: SyncTaskCreateDTO = {
      name: name.trim(),
      sourceEngineId,
      targetEngineId,
      sourcePath: sourcePath.trim(),
      targetPath: targetPath.trim(),
      syncMode,
      scheduleType,
      conflictStrategy,
      transcodeEnabled: convertToMp3,
      targetFormat: 'MP3',
      excludePatterns: excludePatterns.trim() || undefined,
      cronExpression: scheduleType === 'CRON' ? cronExpression.trim() : undefined,
      intervalSeconds: scheduleType === 'INTERVAL' ? parseInt(intervalSeconds, 10) : undefined,
    };

    try {
      await onSubmit(values);
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : '提交失败');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto p-6" onClick={(e) => e.stopPropagation()}>
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          {isEdit ? '编辑同步任务' : '创建同步任务'}
        </h3>

        {submitError && (
          <div className="mb-4 rounded-md bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
            {submitError}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* 基本信息 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              任务名称 <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className={`block w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                errors.name ? 'border-red-400' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
              }`}
              placeholder="例如：每日视频同步"
            />
            {errors.name && <p className="mt-1 text-xs text-red-600">{errors.name}</p>}
          </div>

          {/* 引擎选择 */}
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
          </div>

          {/* 路径 */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                源目录路径 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={sourcePath}
                onChange={(e) => setSourcePath(e.target.value)}
                className={`block w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                  errors.sourcePath ? 'border-red-400' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
                }`}
                placeholder="例如：/media/videos"
              />
              {errors.sourcePath && <p className="mt-1 text-xs text-red-600">{errors.sourcePath}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                目标目录路径 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={targetPath}
                onChange={(e) => setTargetPath(e.target.value)}
                className={`block w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                  errors.targetPath ? 'border-red-400' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
                }`}
                placeholder="例如：/sync/videos"
              />
              {errors.targetPath && <p className="mt-1 text-xs text-red-600">{errors.targetPath}</p>}
            </div>
          </div>

          {/* 同步模式 & 冲突策略 */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">同步模式</label>
              <select
                value={syncMode}
                onChange={(e) => setSyncMode(e.target.value as SyncMode)}
                className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="NEW_ONLY">仅新增</option>
                <option value="FULL">完全同步</option>
                <option value="MOVE">移动模式</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">冲突策略</label>
              <select
                value={conflictStrategy}
                onChange={(e) => setConflictStrategy(e.target.value as ConflictStrategy)}
                className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="SKIP">跳过已存在</option>
                <option value="OVERWRITE">覆盖目标</option>
                <option value="RENAME">自动重命名</option>
              </select>
            </div>
          </div>

          {/* 执行计划 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">执行计划</label>
            <div className="flex gap-4">
              {(['MANUAL', 'INTERVAL', 'CRON'] as ScheduleType[]).map((t) => (
                <label key={t} className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="scheduleType"
                    value={t}
                    checked={scheduleType === t}
                    onChange={() => setScheduleType(t)}
                    className="h-4 w-4 text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-700">
                    {t === 'MANUAL' ? '手动触发' : t === 'INTERVAL' ? '固定间隔' : 'Cron 表达式'}
                  </span>
                </label>
              ))}
            </div>
          </div>

          {/* 条件字段 */}
          {scheduleType === 'INTERVAL' && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                间隔秒数 <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                min={1}
                value={intervalSeconds}
                onChange={(e) => setIntervalSeconds(e.target.value)}
                className={`block w-48 rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
                  errors.intervalSeconds ? 'border-red-400' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
                }`}
                placeholder="例如：3600"
              />
              {errors.intervalSeconds && <p className="mt-1 text-xs text-red-600">{errors.intervalSeconds}</p>}
            </div>
          )}

          {scheduleType === 'CRON' && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Cron 表达式 <span className="text-red-500">*</span>
              </label>
              <CronBuilder value={cronExpression} onChange={setCronExpression} />
              {errors.cronExpression && <p className="mt-1 text-xs text-red-600">{errors.cronExpression}</p>}
            </div>
          )}

          {/* 文件排除规则 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              文件排除规则（可选）
            </label>
            <textarea
              value={excludePatterns}
              onChange={(e) => setExcludePatterns(e.target.value)}
              rows={2}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="每行一个 glob 模式，例如：*.tmp"
            />
          </div>

          {/* 同步后转 MP3 */}
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={convertToMp3}
              onChange={(e) => setConvertToMp3(e.target.checked)}
              className="h-4 w-4 text-blue-600 rounded focus:ring-blue-500"
            />
            <span className="text-sm text-gray-700">同步后自动转 MP3</span>
          </label>

          {/* 操作按钮 */}
          <div className="flex justify-end gap-3 pt-2 border-t">
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
