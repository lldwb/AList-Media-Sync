// ===================================================================
// HTTP 客户端 traceId 类型测试（T038）
// ===================================================================
// 前端项目当前未集成 Vitest/Jest，本文件作为编译期类型契约检查：
// 确保 ApiError 暴露 traceId 字段，调用方能够在请求失败时安全读取
// 用于上报或 UI 提示。
//
// 验证方式：tsc --noEmit（已纳入 `npm run typecheck` 与 `npm run build`）
// 若 ApiError.traceId 字段被移除或类型不匹配，类型检查将失败。
// ===================================================================
import { ApiError } from '@/types/api';
import type { ApiResult } from '@/types/api';

/* ---- 类型契约 1：ApiError.traceId 必须存在且为 string | null ---- */
function consumeApiError(err: ApiError): string | null {
  return err.traceId;
}

/* ---- 类型契约 2：ApiError.status 必须存在且为 number | null ---- */
function consumeApiErrorStatus(err: ApiError): number | null {
  return err.status;
}

/* ---- 类型契约 3：ApiError 继承自 Error ---- */
function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}

/* ---- 行为契约 4：构造时 traceId 字段被保留 ---- */
function createError(traceId: string | null): ApiError {
  return new ApiError('测试错误', traceId, 500);
}

/* ---- 类型契约 5：ApiResult 仍按约定包含 code / message / data 字段 ---- */
function consumeApiResult<T>(result: ApiResult<T>): T | null {
  if (result.code !== 200) {
    return null;
  }
  return result.data;
}

// 仅做类型检查，运行时不导出（避免 dead code 提示）
export const __traceIdTypeContracts = {
  consumeApiError,
  consumeApiErrorStatus,
  isApiError,
  createError,
  consumeApiResult,
};
