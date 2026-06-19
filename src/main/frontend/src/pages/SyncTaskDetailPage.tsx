// ===================================================================
// 同步任务详情页 — US3 (基本信息 + 执行历史 Tab)
// ===================================================================
import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router';
import { api } from '@/api/client';
import { DataTable } from '@/components/ui/DataTable';
import type { ColumnDef } from '@/components/ui/DataTable';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { EmptyState } from '@/components/ui/EmptyState';
import { StatusBadge } from '@/components/ui/StatusBadge';
import {
  formatDateTime,
  formatSyncMode,
  formatScheduleType,
  formatConflictStrategy,
} from '@/utils/format';
import type { SyncTaskVO, TaskExecution, FailureDetail } from '@/types/api';

type TabType = 'info' | 'history';

export function SyncTaskDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [task, setTask] = useState<SyncTaskVO | null>(null);
  const [executions, setExecutions] = useState<TaskExecution[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabType>('info');
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());

  useEffect(() => {
    if (!id) return;

    let cancelled = false;
    Promise.all([
      api.get<SyncTaskVO>(`/sync-tasks/${id}`),
      api.get<TaskExecution[]>(`/sync-tasks/${id}/executions`),
    ])
      .then(([taskData, execData]) => {
        if (!cancelled) {
          setTask(taskData);
          setExecutions(execData);
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : '加载失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [id]);

  if (loading) return <LoadingSpinner size="lg" />;
  if (error) return <ErrorBanner message={error} onRetry={() => window.location.reload()} />;
  if (!task) return <EmptyState title="任务不存在" />;

  /* ---- 执行历史列定义 ---- */
  const execColumns: ColumnDef<TaskExecution>[] = [
    {
      key: 'startTime',
      header: '开始时间',
      render: (item) => formatDateTime(item.startTime),
    },
    {
      key: 'endTime',
      header: '结束时间',
      render: (item) => formatDateTime(item.endTime),
    },
    {
      key: 'status',
      header: '状态',
      render: (item) => <StatusBadge status={item.status} />,
    },
    { key: 'totalFiles', header: '总文件数' },
    { key: 'successFiles', header: '成功数' },
    {
      key: 'failedFiles',
      header: '失败数',
      render: (item) => (
        <span className={item.failedFiles > 0 ? 'text-red-600 font-medium' : ''}>
          {item.failedFiles}
          {item.failedFiles > 0 && (
            <button
              type="button"
              className="ml-1 text-xs text-blue-600 hover:underline"
              onClick={() => {
                setExpandedRows((prev) => {
                  const next = new Set(prev);
                  if (next.has(item.id)) next.delete(item.id);
                  else next.add(item.id);
                  return next;
                });
              }}
            >
              {expandedRows.has(item.id) ? '收起' : '展开'}
            </button>
          )}
        </span>
      ),
    },
  ];

  return (
    <div>
      {/* 返回按钮 */}
      <button
        type="button"
        onClick={() => navigate('/sync-tasks')}
        className="text-sm text-blue-600 hover:text-blue-800 mb-4 inline-flex items-center gap-1"
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
        </svg>
        返回任务列表
      </button>

      <h2 className="text-2xl font-bold text-gray-900 mb-6">{task.name}</h2>

      {/* Tab 切换 */}
      <div className="border-b border-gray-200 mb-6">
        <nav className="flex gap-6">
          {(['info', 'history'] as TabType[]).map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => setActiveTab(tab)}
              className={`pb-3 text-sm font-medium border-b-2 ${
                activeTab === tab
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {tab === 'info' ? '基本信息' : '执行历史'}
            </button>
          ))}
        </nav>
      </div>

      {/* 基本信息 Tab */}
      {activeTab === 'info' && (
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
            <InfoItem label="任务名称" value={task.name} />
            <InfoItem label="源存储引擎" value={task.sourceEngineName} />
            <InfoItem label="目标存储引擎" value={task.targetEngineName} />
            <InfoItem label="源目录路径" value={task.sourcePath} />
            <InfoItem label="目标目录路径" value={task.targetPath} />
            <InfoItem label="同步模式" value={formatSyncMode(task.syncMode)} />
            <InfoItem label="执行计划" value={formatScheduleType(task.scheduleType)} />
            <InfoItem label="冲突策略" value={formatConflictStrategy(task.conflictStrategy)} />
            <InfoItem label="同步后转 MP3" value={task.transcodeEnabled ? '是' : '否'} />
            <InfoItem label="启用状态" value={task.enabled ? '已启用' : '已禁用'} />
            <InfoItem label="最后执行" value={formatDateTime(task.lastExecutedAt)} />
            <InfoItem label="创建时间" value={formatDateTime(task.createdAt)} />
          </dl>
        </div>
      )}

      {/* 执行历史 Tab */}
      {activeTab === 'history' && (
        <div>
          <DataTable
            columns={execColumns}
            items={executions}
            keyField="id"
            emptyState={
              <EmptyState title="暂无执行记录" description="此任务尚未执行过" />
            }
          />

          {/* 展开的失败详情 */}
          {executions.map((exec) =>
            expandedRows.has(exec.id) && exec.failureDetails ? (
              <div key={exec.id} className="mt-2 bg-red-50 border border-red-200 rounded-md p-4">
                <h4 className="text-sm font-medium text-red-800 mb-2">
                  失败文件详情（执行 #{exec.id}）
                </h4>
                {(() => {
                  let failures: FailureDetail[] = [];
                  try {
                    failures = JSON.parse(exec.failureDetails) as FailureDetail[];
                  } catch { /* ignore parse errors */ }
                  return failures.length > 0 ? (
                    <ul className="space-y-1">
                      {failures.map((f, i) => (
                        <li key={i} className="text-sm text-red-700">
                          <span className="font-medium">{f.fileName}</span>：{f.reason}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-sm text-red-700">{exec.failureDetails}</p>
                  );
                })()}
              </div>
            ) : null,
          )}
        </div>
      )}
    </div>
  );
}

/** 单个信息字段 */
function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-gray-500 mb-0.5">{label}</dt>
      <dd className="text-sm text-gray-900">{value || '-'}</dd>
    </div>
  );
}
