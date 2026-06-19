// ===================================================================
// 前端分页 hook — 接收全量 items[]，返回分页数据
// ===================================================================
import { useMemo, useState } from 'react';

const DEFAULT_PAGE_SIZE = 20;

export function usePagination<T>(items: T[], pageSize: number = DEFAULT_PAGE_SIZE) {
  const [currentPage, setCurrentPage] = useState(1);

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil(items.length / pageSize)),
    [items.length, pageSize],
  );

  // 当 items 变化时，确保页码不越界
  const safePage = useMemo(() => {
    if (currentPage > totalPages) return totalPages;
    if (currentPage < 1) return 1;
    return currentPage;
  }, [currentPage, totalPages]);

  const currentItems = useMemo(() => {
    const start = (safePage - 1) * pageSize;
    return items.slice(start, start + pageSize);
  }, [items, safePage, pageSize]);

  const goToPage = (page: number) => {
    const clamped = Math.max(1, Math.min(page, totalPages));
    setCurrentPage(clamped);
  };

  return {
    currentItems,
    currentPage: safePage,
    totalPages,
    pageSize,
    goToPage,
    total: items.length,
  } as const;
}
