// ===================================================================
// 格式化工具 — 日期时间/文件大小/状态文本
// ===================================================================
import {
  EXECUTION_STATUS_LABELS,
  TRANSCODE_STATUS_LABELS,
  SYNC_MODE_LABELS,
  SCHEDULE_TYPE_LABELS,
  CONFLICT_STRATEGY_LABELS,
  WEBHOOK_EVENT_TYPE_LABELS,
  RULE_ACTION_LABELS,
  WEBHOOK_EVENT_STATUS_LABELS,
} from '@/types/api';
import type {
  ExecutionStatus,
  TranscodeStatus,
  SyncMode,
  ScheduleType,
  ConflictStrategy,
  WebhookEventType,
  RuleAction,
  WebhookEventStatus,
} from '@/types/api';

/* ---- 日期时间 ---- */

/** ISO 8601 字符串 → 本地化显示 "2024-06-19 14:30:00" */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '-';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** ISO 8601 → 短日期 "06-19 14:30" */
export function formatShortDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '-';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/* ---- 文件大小 ---- */

/** 字节 → 可读格式 */
export function formatFileSize(bytes: number | null | undefined): string {
  if (bytes == null) return '-';
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const k = 1024;
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const size = bytes / Math.pow(k, i);
  return `${size.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

/* ---- 状态枚举 → 中文文本 ---- */

export function formatExecutionStatus(status: ExecutionStatus): string {
  return EXECUTION_STATUS_LABELS[status] || status;
}

export function formatTranscodeStatus(status: TranscodeStatus): string {
  return TRANSCODE_STATUS_LABELS[status] || status;
}

export function formatSyncMode(mode: SyncMode): string {
  return SYNC_MODE_LABELS[mode] || mode;
}

export function formatScheduleType(type: ScheduleType): string {
  return SCHEDULE_TYPE_LABELS[type] || type;
}

export function formatConflictStrategy(strategy: ConflictStrategy): string {
  return CONFLICT_STRATEGY_LABELS[strategy] || strategy;
}

export function formatWebhookEventType(type: WebhookEventType): string {
  return WEBHOOK_EVENT_TYPE_LABELS[type] || type;
}

export function formatRuleAction(action: RuleAction): string {
  return RULE_ACTION_LABELS[action] || action;
}

export function formatWebhookEventStatus(status: WebhookEventStatus): string {
  return WEBHOOK_EVENT_STATUS_LABELS[status] || status;
}

/* ---- 百分比 ---- */

export function formatPercent(value: number | null | undefined): string {
  if (value == null) return '-';
  return `${value.toFixed(1)}%`;
}
