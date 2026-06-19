// ===================================================================
// 表单校验工具
// 每个函数返回 { valid, error? }
// ===================================================================

export interface ValidationResult {
  valid: boolean;
  error?: string;
}

/** 必填校验 */
export function validateRequired(value: string, label: string): ValidationResult {
  if (!value || value.trim().length === 0) {
    return { valid: false, error: `${label}不能为空` };
  }
  return { valid: true };
}

/** URL 格式校验（http/https） */
export function validateUrl(value: string, label: string): ValidationResult {
  if (!value || value.trim().length === 0) {
    return { valid: false, error: `${label}不能为空` };
  }
  try {
    const url = new URL(value.trim());
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return { valid: false, error: `${label}必须以 http:// 或 https:// 开头` };
    }
  } catch {
    return { valid: false, error: `${label}不是有效的 URL 格式` };
  }
  return { valid: true };
}

/** Cron 表达式格式校验（委托 cron 解析器） */
export async function validateCron(value: string): Promise<ValidationResult> {
  const { parseCron } = await import('@/utils/cron');
  const result = parseCron(value);
  if (!result.valid) {
    return { valid: false, error: result.error || 'Cron 表达式格式无效' };
  }
  return { valid: true };
}

/** cron 格式同步校验（不引入异步，用于表单提交） */
export function validateCronSync(value: string): ValidationResult {
  const trimmed = value.trim();
  if (!trimmed) {
    return { valid: false, error: '请输入 cron 表达式' };
  }
  const fields = trimmed.split(/\s+/);
  if (fields.length !== 5) {
    return { valid: false, error: 'cron 表达式需包含 5 个字段' };
  }
  return { valid: true };
}

/** 路径格式校验（必须以 / 开头） */
export function validatePath(value: string, label: string): ValidationResult {
  if (!value || value.trim().length === 0) {
    return { valid: false, error: `${label}不能为空` };
  }
  if (!value.startsWith('/')) {
    return { valid: false, error: `${label}必须以 "/" 开头` };
  }
  return { valid: true };
}

/** 正整数校验 */
export function validatePositiveInt(
  value: number | string | undefined,
  label: string,
): ValidationResult {
  if (value === undefined || value === null || value === '') {
    return { valid: false, error: `${label}不能为空` };
  }
  const n = typeof value === 'string' ? parseInt(value, 10) : value;
  if (isNaN(n) || n <= 0 || !Number.isInteger(n)) {
    return { valid: false, error: `${label}必须为正整数` };
  }
  return { valid: true };
}
