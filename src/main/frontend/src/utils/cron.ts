// ===================================================================
// Cron 表达式 TypeScript 解析器（5 字段标准 cron）
// ===================================================================

export interface CronResult {
  valid: boolean;
  description?: string;      // 中文人类可读描述
  nextExecution?: Date;      // 下次执行时间
  error?: string;            // 校验失败时的错误信息
}

/** 单字段支持的值：数字、星号、范围(1-5)、步进(\*\/5)、列表(1,3,5) */
const FIELD_REGEX = /^(\*|\d+|\d+-\d+|\d+,\d+(?:,\d+)*|\*\/\d+|\d+-\d+\/\d+)$/;

/** 各字段的值域 */
const FIELD_RANGES: [number, number][] = [
  [0, 59],  // 分钟
  [0, 23],  // 小时
  [1, 31],  // 日
  [1, 12],  // 月
  [0, 7],   // 星期 (0=周日)
];

// 月份名称常量（后续版本扩展描述生成时使用）
// const MONTH_NAMES = ['', '一月', '二月', ...];

const WEEKDAY_NAMES: string[] = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

/** 解析单个字段，返回所有匹配值 */
function parseField(field: string, min: number, max: number): number[] | null {
  const values: number[] = [];

  // 步进 */N 或 范围/N
  const stepMatch = field.match(/^(\*|(\d+)-(\d+))\/(\d+)$/);
  if (stepMatch) {
    let start = min;
    let end = max;
    if (stepMatch[2] !== undefined) {
      start = parseInt(stepMatch[2] || '', 10);
      end = parseInt(stepMatch[3] || '', 10);
    }
    const step = parseInt(stepMatch[4] || '0', 10);
    if (step <= 0 || start > end || start < min || end > max) return null;
    for (let v = start; v <= end; v += step) {
      values.push(v);
    }
    return values;
  }

  // *
  if (field === '*') {
    for (let v = min; v <= max; v++) values.push(v);
    return values;
  }

  // 范围 1-5
  const rangeMatch = field.match(/^(\d+)-(\d+)$/);
  if (rangeMatch) {
    const s = parseInt(rangeMatch[1] || '0', 10);
    const e = parseInt(rangeMatch[2] || '0', 10);
    if (s > e || s < min || e > max) return null;
    for (let v = s; v <= e; v++) values.push(v);
    return values;
  }

  // 列表 1,3,5
  if (field.includes(',')) {
    const parts = field.split(',');
    for (const p of parts) {
      const n = parseInt(p, 10);
      if (isNaN(n) || n < min || n > max) return null;
      values.push(n);
    }
    return values;
  }

  // 单数字
  const n = parseInt(field, 10);
  if (isNaN(n) || n < min || n > max) return null;
  values.push(n);
  return values;
}

/** 计算下次执行时间：从 base 开始，逐步递增分钟直到匹配所有字段 */
function calcNextExecution(fields: number[][]): Date {
  const now = new Date();
  now.setSeconds(0, 0);
  now.setMinutes(now.getMinutes() + 1); // 从下一秒开始

  const MAX_ITER = 366 * 24 * 60; // 最多扫描一年
  for (let i = 0; i < MAX_ITER; i++) {
    const m = now.getMinutes();
    const h = now.getHours();
    const dom = now.getDate();
    const mon = now.getMonth() + 1;
    const dow = now.getDay();

    if (
      fields[0]!.includes(m) &&
      fields[1]!.includes(h) &&
      fields[2]!.includes(dom) &&
      fields[3]!.includes(mon) &&
      fields[4]!.includes(dow)
    ) {
      return now;
    }

    now.setMinutes(now.getMinutes() + 1);
  }

  return now; // fallback（不应到达）
}

/** 生成人类可读中文描述 */
function buildDescription(fields: string[]): string {
  const [minField, hourField, dayField, monthField, weekField] = fields;
  const parts: string[] = [];

  // 先解析频率模式
  const allEveryMinute =
    minField === '*' && hourField === '*' && dayField === '*' && monthField === '*' && weekField === '*';

  const everyHourMatch = minField?.match(/^\*?\/(\d+)$/);

  if (allEveryMinute) {
    return '每分钟执行一次';
  }

  // 固定时刻（如 30 2 * * *）
  if (
    minField?.match(/^\d+$/) &&
    hourField?.match(/^\d+$/) &&
    dayField === '*' && monthField === '*' && weekField === '*'
  ) {
    const h = parseInt(hourField, 10);
    const m = parseInt(minField, 10);
    return `每天 ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')} 执行`;
  }

  // 固定间隔执行（* */N * * * 或 */N * * * *）
  if (everyHourMatch && hourField !== undefined) {
    if (hourField === '*') {
      const n = parseInt(minField!.replace('*/', ''), 10);
      if (n > 0) return `每 ${n} 分钟执行一次`;
    }
  }

  // 构建时间部分
  if (hourField && hourField !== '*' && minField && minField !== '*') {
    const h = hourField.match(/^\d+$/);
    const m = minField.match(/^\d+$/);
    if (h && m) {
      parts.push(`${String(parseInt(h[0], 10)).padStart(2, '0')}:${String(parseInt(m[0], 10)).padStart(2, '0')}`);
    }
  }

  // 构建频率部分
  let freq = '';
  if (weekField && weekField !== '*' && weekField.match(/^\d+$/)) {
    freq = WEEKDAY_NAMES[parseInt(weekField, 10)] ?? '';
  } else if (dayField && dayField !== '*' && dayField.match(/^\d+$/)) {
    freq = `每月 ${parseInt(dayField, 10)} 日`;
  }

  if (freq) {
    parts.push(freq);
  } else if (dayField === '*' && monthField === '*' && weekField === '*') {
    parts.unshift('每天');
  }

  return parts.join(' ') + ' 执行';
}

/**
 * 解析 cron 表达式，返回校验结果与预览信息
 */
export function parseCron(expr: string): CronResult {
  const trimmed = expr.trim();
  if (!trimmed) {
    return { valid: false, error: '请输入 cron 表达式' };
  }

  const fields = trimmed.split(/\s+/);
  if (fields.length !== 5) {
    return { valid: false, error: 'cron 表达式需包含 5 个字段：分 时 日 月 周' };
  }

  const parsed: number[][] = [];
  for (let i = 0; i < 5; i++) {
    const field = fields[i]!;
    // 星期字段特殊处理：7 等价于 0
    const [min, max] = FIELD_RANGES[i]!;
    if (!FIELD_REGEX.test(field)) {
      return { valid: false, error: `第 ${i + 1} 个字段格式无效："${field}"` };
    }
    const values = parseField(field, min, max);
    if (!values) {
      return { valid: false, error: `第 ${i + 1} 个字段超出范围："${field}"` };
    }
    parsed.push(values);
  }

  try {
    const nextExecution = calcNextExecution(parsed);
    const description = buildDescription(fields as string[]);
    return { valid: true, description, nextExecution };
  } catch {
    return { valid: false, error: '无法计算下次执行时间' };
  }
}
