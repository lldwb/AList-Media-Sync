// ===================================================================
// AList-Media-Sync 前端 API 类型定义
// 与后端 DTO/VO 字段一一对应
// ===================================================================

/* ---- 通用响应包装 ---- */

/** 统一 API 响应包装 */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T | null;
}

/* ---- 存储引擎 ---- */

export type EngineType = 'ALIST' | 'LOCAL';

export interface StorageEngineVO {
  id: number;
  name: string;
  engineType: EngineType;
  baseUrl: string | null;
  localPath: string | null;
  status: 'ONLINE' | 'OFFLINE' | 'ERROR';
  createdAt: string; // ISO 8601
  updatedAt: string;
  // 移除: username
}

export interface StorageEngineCreateDTO {
  name: string;
  engineType: EngineType;
  baseUrl?: string;
  token?: string;
  localPath?: string;
  // 移除: username
}

export interface StorageEngineUpdateDTO {
  name?: string;
  baseUrl?: string;
  token?: string;
  localPath?: string;
  // 移除: username
  // engineType 不可更改
}

/* ---- 目录浏览 ---- */

export interface DirectoryEntryVO {
  name: string;
  path: string;
  hasChildren: boolean;
}

/* ---- 同步任务 ---- */

export type SyncMode = 'NEW_ONLY' | 'FULL' | 'MOVE';
export type ScheduleType = 'CRON' | 'INTERVAL' | 'MANUAL';
export type ConflictStrategy = 'OVERWRITE' | 'SKIP' | 'RENAME';

export interface SyncTaskVO {
  id: number;
  name: string;
  sourceEngineId: number;
  sourceEngineName: string;
  targetEngineId: number;
  targetEngineName: string;
  sourcePath: string;
  targetPath: string;
  syncMode: SyncMode;
  transcodeEnabled: boolean;
  targetFormat?: string;
  conflictStrategy: ConflictStrategy;
  excludePatterns?: string;
  scheduleType: ScheduleType;
  cronExpression?: string;
  intervalSeconds?: number;
  enabled: boolean;
  lastExecutedAt?: string;
  createdAt: string;
}

export interface SyncTaskCreateDTO {
  name: string;
  sourceEngineId: number;
  targetEngineId: number;
  sourcePath: string;
  targetPath: string;
  syncMode: SyncMode;
  transcodeEnabled: boolean;
  targetFormat: string;
  conflictStrategy: ConflictStrategy;
  excludePatterns?: string;
  scheduleType: ScheduleType;
  cronExpression?: string;
  intervalSeconds?: number;
}

export interface SyncTaskUpdateDTO {
  name?: string;
  sourceEngineId?: number;
  targetEngineId?: number;
  sourcePath?: string;
  targetPath?: string;
  syncMode?: SyncMode;
  transcodeEnabled?: boolean;
  targetFormat?: string;
  conflictStrategy?: ConflictStrategy;
  excludePatterns?: string;
  scheduleType?: ScheduleType;
  cronExpression?: string;
  intervalSeconds?: number;
}

/* ---- 任务执行记录 ---- */

export type TaskType = 'SYNC' | 'TRANSCODE' | 'WEBHOOK';
export type ExecutionStatus =
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'PARTIAL_SUCCESS'
  | 'INTERRUPTED';

export interface TaskExecution {
  id: number;
  syncTaskId: number;
  taskType: TaskType;
  startTime: string;
  endTime?: string;
  status: ExecutionStatus;
  totalFiles: number;
  successFiles: number;
  failedFiles: number;
  failureDetails?: string; // JSON 字符串
}

export interface FailureDetail {
  fileName: string;
  reason: string;
}

/* ---- 转码任务（8 状态模型） ---- */

export type TargetFormat = 'MP3' | 'MP4' | 'FLV';

/** 转码状态（8 状态模型：三步流程，每步可独立失败和重试） */
export type TranscodeStatus =
  | 'PENDING'
  | 'DOWNLOADING'
  | 'DOWNLOAD_FAILED'
  | 'TRANSCODING'
  | 'TRANSCODE_FAILED'
  | 'UPLOADING'
  | 'UPLOAD_FAILED'
  | 'COMPLETED';
  // 移除: SCANNING, FAILED

export interface TranscodeTaskVO {
  id: number;
  sourceFilePath: string;
  targetFilePath: string;
  sourceFormat?: string;
  targetFormat: TargetFormat;
  bitrate: number | null; // null 表示使用系统默认码率
  progressPercent: number; // 0-100
  status: TranscodeStatus;
  canRetry: boolean; // 失败状态时为 true
  retryCount?: number; // 自动重试已执行次数
  errorMessage?: string;
  createdAt: string;
  // 移除: tempFilePath（内部实现细节）
}

export interface TranscodeTaskCreateDTO {
  sourceEngineId?: number;
  targetEngineId?: number;
  sourceFilePath: string;
  targetFilePath: string;
  targetFormat: TargetFormat;
  bitrate?: number;
  /** 源目录转码选项（默认 false），启用时 targetFilePath 可选 */
  sameDirectoryTranscode?: boolean;
}

/* ---- Webhook 规则 ---- */

export type WebhookEventType =
  | 'FILE_OPENED'
  | 'FILE_CLOSED'
  | 'FILE_RENAMED'
  | 'SESSION_STARTED'
  | 'SESSION_ENDED'
  | 'SPACE_FULL'
  | 'OTHER';

export type RuleAction = 'SYNC_ONLY' | 'TRANSCODE_ONLY' | 'BOTH';

export interface WebhookRuleVO {
  id: number;
  name: string;
  triggerEventType: WebhookEventType;
  roomIdFilter?: number;
  action: RuleAction;
  recordingEngineId?: number;
  recordingEngineName?: string;
  recordingPath?: string;
  targetEngineId: number;
  targetEngineName: string;
  targetFilePath: string; // 原 targetPath
  enabled: boolean;
  createdAt: string;
}

export interface WebhookRuleCreateDTO {
  name: string;
  triggerEventType: WebhookEventType;
  roomIdFilter?: number;
  action: RuleAction;
  recordingEngineId?: number;
  recordingPath?: string;
  targetEngineId?: number;
  targetFilePath?: string; // 原 targetPath
}

/* ---- Webhook 事件 ---- */

export type WebhookEventStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'DUPLICATE';

export interface WebhookEventVO {
  id: number;
  eventId: string;
  eventType: WebhookEventType;
  eventTimestamp: string;
  sessionId?: string;
  roomId?: number;
  relativePath?: string;
  fileName?: string;
  fileSize?: number;
  duration?: number;
  status: WebhookEventStatus;
}

/* ---- 仪表板统计 ---- */

export interface DashboardStatsVO {
  activeSyncTasks: number;
  pendingTranscodeTasks: number;
  todayProcessedFiles: number;
  last24hSuccessRate: number;
  totalEngines: number;
  totalWebhookRules: number;
}

/* ---- 通用枚举映射 ---- */

/** 引擎类型 → 中文描述 */
export const ENGINE_TYPE_LABELS: Record<EngineType, string> = {
  ALIST: 'AList 远程存储',
  LOCAL: '本地路径',
};

/** 同步模式 → 中文描述 */
export const SYNC_MODE_LABELS: Record<SyncMode, string> = {
  NEW_ONLY: '仅新增',
  FULL: '完全同步',
  MOVE: '移动模式',
};

/** 调度类型 → 中文描述 */
export const SCHEDULE_TYPE_LABELS: Record<ScheduleType, string> = {
  CRON: 'Cron 表达式',
  INTERVAL: '固定间隔',
  MANUAL: '手动触发',
};

/** 冲突策略 → 中文描述 */
export const CONFLICT_STRATEGY_LABELS: Record<ConflictStrategy, string> = {
  OVERWRITE: '覆盖目标',
  SKIP: '跳过已存在',
  RENAME: '自动重命名',
};

/** 执行状态 → 中文描述 */
export const EXECUTION_STATUS_LABELS: Record<ExecutionStatus, string> = {
  RUNNING: '运行中',
  SUCCESS: '成功',
  FAILED: '失败',
  PARTIAL_SUCCESS: '部分成功',
  INTERRUPTED: '中断',
};

/** 转码状态 → 中文描述（8 状态模型） */
export const TRANSCODE_STATUS_LABELS: Record<TranscodeStatus, string> = {
  PENDING: '等待中',
  DOWNLOADING: '下载中',
  DOWNLOAD_FAILED: '下载失败',
  TRANSCODING: '转码中',
  TRANSCODE_FAILED: '转码失败',
  UPLOADING: '上传中',
  UPLOAD_FAILED: '上传失败',
  COMPLETED: '已完成',
};

/** Webhook 事件类型 → 中文描述 */
export const WEBHOOK_EVENT_TYPE_LABELS: Record<WebhookEventType, string> = {
  FILE_OPENED: '文件打开',
  FILE_CLOSED: '文件关闭',
  FILE_RENAMED: '文件重命名',
  SESSION_STARTED: '录制开始',
  SESSION_ENDED: '录制结束',
  SPACE_FULL: '空间不足',
  OTHER: '其他',
};

/** 规则动作 → 中文描述 */
export const RULE_ACTION_LABELS: Record<RuleAction, string> = {
  SYNC_ONLY: '仅同步',
  TRANSCODE_ONLY: '仅转 MP3',
  BOTH: '同步 + 转 MP3',
};

/** Webhook 事件状态 → 中文描述 */
export const WEBHOOK_EVENT_STATUS_LABELS: Record<WebhookEventStatus, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '失败',
  DUPLICATE: '重复',
};

/* ---- WebSocket 消息 ---- */

/** WebSocket 消息类型 */
export type MessageType =
  | 'SYNC_PROGRESS'
  | 'TRANSCODE_PROGRESS'
  | 'TASK_EVENT'
  | 'WEBHOOK_EVENT'
  | 'DASHBOARD_UPDATE';

/** 通用 WebSocket 消息结构（可辨识联合，switch on type 自动收窄 payload 类型） */
export type WsMessage =
  | { type: 'SYNC_PROGRESS'; payload: SyncProgressPayload; timestamp: string }
  | { type: 'TRANSCODE_PROGRESS'; payload: TranscodeProgressPayload; timestamp: string }
  | { type: 'TASK_EVENT'; payload: TaskEventPayload; timestamp: string }
  | { type: 'WEBHOOK_EVENT'; payload: WebhookEventPayload; timestamp: string }
  | { type: 'DASHBOARD_UPDATE'; payload: DashboardUpdatePayload; timestamp: string };

/** WebSocket 消息联合载荷类型 */
export type WsPayload =
  | SyncProgressPayload
  | TranscodeProgressPayload
  | TaskEventPayload
  | WebhookEventPayload
  | DashboardUpdatePayload;

/** SYNC_PROGRESS 消息载荷 */
export interface SyncProgressPayload {
  type: 'SYNC_PROGRESS';
  taskId: number;
  executionId?: number;
  status: string;
  successFiles: number;
  failedFiles: number;
  totalFiles: number;
  progressPercent?: number;
}

/** TRANSCODE_PROGRESS 消息载荷 */
export interface TranscodeProgressPayload {
  type: 'TRANSCODE_PROGRESS';
  taskId: number;
  status: TranscodeStatus;
  progressPercent?: number;
  retryCount?: number;
  errorMessage?: string;
}

/** TASK_EVENT 消息载荷 */
export interface TaskEventPayload {
  type: 'TASK_EVENT';
  action: 'CREATED' | 'DELETED' | 'COMPLETED' | 'BATCH_DELETED';
  taskType: 'SYNC' | 'TRANSCODE';
  taskId?: number;
  count?: number;
  status?: string;
}

/** WEBHOOK_EVENT 消息载荷 */
export interface WebhookEventPayload {
  type: 'WEBHOOK_EVENT';
  eventId: number;
  eventType: WebhookEventType;
  status: WebhookEventStatus;
}

/** DASHBOARD_UPDATE 消息载荷 */
export interface DashboardUpdatePayload {
  type: 'DASHBOARD_UPDATE';
  totalSyncTasks: number;
  activeSyncTasks: number;
  totalTranscodeTasks: number;
  activeTranscodeTasks: number;
  todayWebhookEvents: number;
  engineOnlineCount?: number;
}
