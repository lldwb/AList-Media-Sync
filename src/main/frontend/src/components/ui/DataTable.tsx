// ===================================================================
// 通用数据表格组件 — 泛型 <T>，内置前端分页控件
// ===================================================================
import type { ReactNode } from 'react';
import { usePagination } from '@/hooks/usePagination';

export interface ColumnDef<T> {
  key: string;
  header: string;
  /** 自定义渲染单元格 */
  render?: (item: T, index: number) => ReactNode;
  /** 列宽度类（Tailwind） */
  className?: string;
}

interface DataTableProps<T> {
  columns: ColumnDef<T>[];
  items: T[];
  /** 唯一键字段名 */
  keyField: keyof T;
  /** 每页条数（默认 20） */
  pageSize?: number;
  /** 空数据状态组件 */
  emptyState?: ReactNode;
}

export function DataTable<T>({
  columns,
  items,
  keyField,
  pageSize = 20,
  emptyState,
}: DataTableProps<T>) {
  const { currentItems, currentPage, totalPages, goToPage, total } =
    usePagination(items, pageSize);

  if (items.length === 0 && emptyState) {
    return <>{emptyState}</>;
  }

  const pageNumbers = getPageNumbers(currentPage, totalPages);

  return (
    <div>
      {/* 表格 */}
      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50">
            <tr>
              {columns.map((col) => (
                <th
                  key={col.key}
                  className={`px-4 py-3 text-left font-medium text-gray-600 ${col.className || ''}`}
                >
                  {col.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {currentItems.map((item, idx) => (
              <tr
                key={String(item[keyField])}
                className="hover:bg-gray-50 transition-colors"
              >
                {columns.map((col) => (
                  <td key={col.key} className={`px-4 py-3 ${col.className || ''}`}>
                    {col.render
                      ? col.render(item, idx)
                      : String((item as Record<string, unknown>)[col.key] ?? '')}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 分页控件 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4 text-sm text-gray-600">
          <span>
            共 {total} 条，第 {currentPage} / {totalPages} 页
          </span>
          <div className="flex items-center gap-1">
            <button
              type="button"
              disabled={currentPage === 1}
              onClick={() => goToPage(currentPage - 1)}
              className="px-3 py-1 rounded border border-gray-300 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-100"
            >
              上一页
            </button>
            {pageNumbers.map((p, i) =>
              p === '...' ? (
                <span key={`ellipsis-${i}`} className="px-2">
                  ...
                </span>
              ) : (
                <button
                  key={p}
                  type="button"
                  onClick={() => goToPage(p as number)}
                  className={`px-3 py-1 rounded border ${
                    currentPage === p
                      ? 'bg-blue-600 text-white border-blue-600'
                      : 'border-gray-300 hover:bg-gray-100'
                  }`}
                >
                  {p}
                </button>
              ),
            )}
            <button
              type="button"
              disabled={currentPage === totalPages}
              onClick={() => goToPage(currentPage + 1)}
              className="px-3 py-1 rounded border border-gray-300 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-100"
            >
              下一页
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/** 生成页码列表：1 ... 4 5 [6] 7 8 ... 10 */
function getPageNumbers(current: number, total: number): (number | '...')[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }

  const pages: (number | '...')[] = [1];

  if (current > 3) pages.push('...');

  const start = Math.max(2, current - 1);
  const end = Math.min(total - 1, current + 1);

  for (let i = start; i <= end; i++) {
    pages.push(i);
  }

  if (current < total - 2) pages.push('...');

  pages.push(total);
  return pages;
}
