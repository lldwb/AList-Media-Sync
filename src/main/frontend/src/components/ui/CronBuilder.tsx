// ===================================================================
// Cron 图形化配置组件 — 五字段选择器 + 预设快捷模式 + 实时预览
// ===================================================================
import { useState, useEffect, useMemo } from 'react';
import { parseCron, buildCronExpression, CRON_PRESETS, type CronFields } from '@/utils/cron';
import { formatDateTime } from '@/utils/format';

interface CronBuilderProps {
  value: string;
  onChange: (expression: string) => void;
}

/** 将 cron 表达式拆分为 CronFields */
function parseToFields(expr: string): CronFields {
  const parts = expr.trim().split(/\s+/);
  if (parts.length !== 5) {
    return { minute: '*', hour: '*', dayOfMonth: '*', month: '*', dayOfWeek: '*' };
  }
  return {
    minute: parts[0]!,
    hour: parts[1]!,
    dayOfMonth: parts[2]!,
    month: parts[3]!,
    dayOfWeek: parts[4]!,
  };
}

/** 单字段快速选择组件 */
function FieldSelector({
  label,
  value,
  onChange,
  min,
  max,
  unit,
}: {
  label: string;
  value: string;
  onChange: (val: string) => void;
  min: number;
  max: number;
  unit?: string;
}) {
  const [mode, setMode] = useState<'every' | 'specific' | 'range' | 'step'>(
    value === '*' ? 'every'
    : value.includes('/') ? 'step'
    : value.includes('-') ? 'range'
    : 'specific'
  );
  const [specificVal, setSpecificVal] = useState(
    value.match(/^\d+$/) ? parseInt(value, 10) : 0
  );
  const [rangeStart, setRangeStart] = useState(0);
  const [rangeEnd, setRangeEnd] = useState(0);
  const [stepVal, setStepVal] = useState(
    value.includes('/') ? parseInt(value.split('/')[1]!, 10) : 1
  );

  useEffect(() => {
    onChange(
      mode === 'every' ? '*'
      : mode === 'specific' ? String(specificVal)
      : mode === 'range' ? `${rangeStart}-${rangeEnd}`
      : `*/${stepVal}`
    );
  }, [mode, specificVal, rangeStart, rangeEnd, stepVal]);

  const options = Array.from({ length: max - min + 1 }, (_, i) => min + i);

  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs font-medium text-gray-500">{label}</span>

      {/* 模式选择 */}
      <div className="flex flex-wrap gap-1">
        {(['every', 'specific', 'range', 'step'] as const).map((m) => (
          <button
            key={m}
            type="button"
            onClick={() => setMode(m)}
            className={`text-xs px-2 py-0.5 rounded ${
              mode === m ? 'bg-blue-100 text-blue-700 font-medium' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            {m === 'every' ? `每${unit || '个'}` : m === 'specific' ? '指定' : m === 'range' ? '范围' : '步进'}
          </button>
        ))}
      </div>

      {/* 具体值选择器 */}
      {mode === 'specific' && (
        <select
          value={specificVal}
          onChange={(e) => setSpecificVal(Number(e.target.value))}
          className="text-xs rounded border border-gray-300 px-2 py-1"
        >
          {options.map((n) => (
            <option key={n} value={n}>{n}</option>
          ))}
        </select>
      )}

      {/* 范围选择 */}
      {mode === 'range' && (
        <div className="flex items-center gap-1">
          <select value={rangeStart} onChange={(e) => setRangeStart(Number(e.target.value))} className="text-xs rounded border border-gray-300 px-2 py-1">
            {options.map((n) => (<option key={n} value={n}>{n}</option>))}
          </select>
          <span className="text-xs text-gray-400">-</span>
          <select value={rangeEnd} onChange={(e) => setRangeEnd(Number(e.target.value))} className="text-xs rounded border border-gray-300 px-2 py-1">
            {options.map((n) => (<option key={n} value={n}>{n}</option>))}
          </select>
        </div>
      )}

      {/* 步进 */}
      {mode === 'step' && (
        <div className="flex items-center gap-1">
          <span className="text-xs text-gray-500">每</span>
          <select value={stepVal} onChange={(e) => setStepVal(Number(e.target.value))} className="text-xs rounded border border-gray-300 px-2 py-1">
            {[1,2,3,4,5,6,10,12,15,20,30].map((n) => (<option key={n} value={n}>{n}</option>))}
          </select>
          <span className="text-xs text-gray-500">{unit || '个'}</span>
        </div>
      )}
    </div>
  );
}

export function CronBuilder({ value, onChange }: CronBuilderProps) {
  const [manualMode, setManualMode] = useState(false);
  const [manualExpr, setManualExpr] = useState(value);
  const [fields, setFields] = useState<CronFields>(parseToFields(value));

  // 内置预设 → 图形化同步
  const isPreset = useMemo(() => {
    return CRON_PRESETS.some((p) => p.value === value);
  }, [value]);

  // 实时解析预览
  const preview = useMemo(() => parseCron(value), [value]);

  // 从图形化字段生成表达式
  const updateFromFields = (f: CronFields) => {
    setFields(f);
    const expr = buildCronExpression(f);
    onChange(expr);
    setManualExpr(expr);
  };

  const updateField = (key: keyof CronFields, val: string) => {
    const newFields = { ...fields, [key]: val };
    updateFromFields(newFields);
  };

  // 手动模式同步
  const handleManualChange = (expr: string) => {
    setManualExpr(expr);
    onChange(expr);
    // 同步图形化字段
    const parsed = parseToFields(expr);
    setFields(parsed);
  };

  return (
    <div className="space-y-3">
      {/* 模式切换 */}
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => setManualMode(false)}
          className={`text-xs px-3 py-1 rounded ${!manualMode ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'}`}
        >
          图形化
        </button>
        <button
          type="button"
          onClick={() => setManualMode(true)}
          className={`text-xs px-3 py-1 rounded ${manualMode ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'}`}
        >
          手动输入
        </button>
      </div>

      {manualMode ? (
        /* 手动输入模式 */
        <div>
          <input
            type="text"
            value={manualExpr}
            onChange={(e) => handleManualChange(e.target.value)}
            className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm font-mono focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            placeholder="分 时 日 月 周（例如：0 8 * * *）"
          />
        </div>
      ) : (
        /* 图形化模式 */
        <div>
          {/* 预设快捷模式 */}
          <div className="mb-3">
            <span className="text-xs font-medium text-gray-500 mb-1 block">快捷预设</span>
            <div className="flex flex-wrap gap-1">
              {CRON_PRESETS.map((preset) => (
                <button
                  key={preset.value}
                  type="button"
                  onClick={() => {
                    onChange(preset.value);
                    setManualExpr(preset.value);
                    setFields(parseToFields(preset.value));
                  }}
                  className={`text-xs px-2 py-1 rounded ${
                    value === preset.value ? 'bg-blue-100 text-blue-700 font-medium' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  {preset.label}
                </button>
              ))}
            </div>
          </div>

          {/* 五字段选择器 */}
          <div className="grid grid-cols-5 gap-2">
            <FieldSelector label="分钟" value={fields.minute} onChange={(v) => updateField('minute', v)} min={0} max={59} unit="分钟" />
            <FieldSelector label="小时" value={fields.hour} onChange={(v) => updateField('hour', v)} min={0} max={23} unit="小时" />
            <FieldSelector label="日" value={fields.dayOfMonth} onChange={(v) => updateField('dayOfMonth', v)} min={1} max={31} unit="日" />
            <FieldSelector label="月" value={fields.month} onChange={(v) => updateField('month', v)} min={1} max={12} unit="月" />
            <FieldSelector label="周" value={fields.dayOfWeek} onChange={(v) => updateField('dayOfWeek', v)} min={0} max={7} unit="天" />
          </div>
        </div>
      )}

      {/* 预览 */}
      <div className="rounded-md bg-gray-50 border border-gray-200 px-3 py-2 space-y-1">
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-500">表达式：</span>
          <code className="text-sm font-mono text-gray-700">{value}</code>
        </div>
        {preview.valid ? (
          <>
            <div className="flex items-center gap-2">
              <span className="text-xs text-gray-500">描述：</span>
              <span className="text-sm text-gray-700">{preview.description}</span>
            </div>
            {preview.nextExecution && (
              <div className="flex items-center gap-2">
                <span className="text-xs text-gray-500">下次执行：</span>
                <span className="text-sm text-gray-700">{formatDateTime(preview.nextExecution.toISOString())}</span>
              </div>
            )}
          </>
        ) : (
          <p className="text-xs text-red-600">{preview.error}</p>
        )}
      </div>
    </div>
  );
}
