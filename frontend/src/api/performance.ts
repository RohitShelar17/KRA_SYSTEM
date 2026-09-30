import { apiClient, unwrap } from './axiosClient';
import type { ApiResponse } from '@/types/api';
import type {
  FormulaConfig,
  FormulaParameter,
  PerformanceParameterCode,
  PerformanceScope,
  PerformanceSnapshot,
  PerformanceTrend,
  RatingBand,
  RecalculationSummary,
} from '@/types/performance';

export interface RecalculatePayload {
  scope: PerformanceScope;
  employeeIds?: number[];
  date?: string | null;
  year?: number | null;
  periodIndex?: number | null;
  reason?: string | null;
}

export interface FormulaPayload {
  scopeType: PerformanceScope;
  name: string;
  notes?: string | null;
  minSubmissionRatePct?: number | null;
  submissionPenaltyPct?: number | null;
  effectiveFrom?: string | null;
  parameters: { parameterCode: PerformanceParameterCode; label?: string; weightage: number }[];
}

export interface RatingBandPayload {
  name: string;
  description?: string | null;
  minScore: number;
  maxScore: number;
  colourHex?: string | null;
}

export const performanceApi = {
  mySnapshot: (scope: Exclude<PerformanceScope, 'DAILY'>, params: { year?: number; month?: number; quarter?: number }) =>
    unwrap<PerformanceSnapshot>(
      apiClient.get<ApiResponse<PerformanceSnapshot>>(`/performance/my/${scope.toLowerCase()}`, {
        params,
      }),
    ),

  myDaily: (date?: string) =>
    unwrap<PerformanceSnapshot>(
      apiClient.get<ApiResponse<PerformanceSnapshot>>('/performance/my/daily', { params: { date } }),
    ),

  myTrend: (scope?: PerformanceScope, year?: number) =>
    unwrap<PerformanceTrend>(
      apiClient.get<ApiResponse<PerformanceTrend>>('/performance/my/trend', {
        params: { scope, year },
      }),
    ),

  employeeSnapshot: (
    employeeId: number,
    scope: PerformanceScope,
    params: { date?: string; year?: number; month?: number; quarter?: number },
  ) =>
    unwrap<PerformanceSnapshot>(
      apiClient.get<ApiResponse<PerformanceSnapshot>>(
        `/employees/${employeeId}/performance/${scope.toLowerCase()}`,
        { params },
      ),
    ),

  employeeTrend: (employeeId: number, scope?: PerformanceScope, year?: number) =>
    unwrap<PerformanceTrend>(
      apiClient.get<ApiResponse<PerformanceTrend>>(`/employees/${employeeId}/performance/trend`, {
        params: { scope, year },
      }),
    ),

  recalculate: (payload: RecalculatePayload) =>
    unwrap<RecalculationSummary>(
      apiClient.post<ApiResponse<RecalculationSummary>>('/performance/recalculate', payload),
    ),

  adjust: (scope: PerformanceScope, id: number, newScore: number, reason: string) =>
    unwrap<PerformanceSnapshot>(
      apiClient.post<ApiResponse<PerformanceSnapshot>>(`/performance/${scope}/${id}/adjust`, {
        newScore,
        reason,
      }),
    ),

  finalize: (scope: PerformanceScope, id: number) =>
    unwrap<PerformanceSnapshot>(
      apiClient.post<ApiResponse<PerformanceSnapshot>>(`/performance/${scope}/${id}/finalize`),
    ),

  reopen: (scope: PerformanceScope, id: number, reason: string) =>
    unwrap<PerformanceSnapshot>(
      apiClient.post<ApiResponse<PerformanceSnapshot>>(`/performance/${scope}/${id}/reopen`, {
        reason,
      }),
    ),
};

export const performanceSettingsApi = {
  formulas: () =>
    unwrap<FormulaConfig[]>(apiClient.get<ApiResponse<FormulaConfig[]>>('/settings/formula')),

  activeFormula: (scope: PerformanceScope) =>
    unwrap<FormulaConfig>(
      apiClient.get<ApiResponse<FormulaConfig>>('/settings/formula/active', { params: { scope } }),
    ),

  availableParameters: (scope: PerformanceScope) =>
    unwrap<FormulaParameter[]>(
      apiClient.get<ApiResponse<FormulaParameter[]>>('/settings/formula/parameters', {
        params: { scope },
      }),
    ),

  saveFormula: (payload: FormulaPayload) =>
    unwrap<FormulaConfig>(apiClient.post<ApiResponse<FormulaConfig>>('/settings/formula', payload)),

  ratingBands: () =>
    unwrap<RatingBand[]>(apiClient.get<ApiResponse<RatingBand[]>>('/settings/rating-bands')),

  saveRatingBands: (payload: RatingBandPayload[]) =>
    unwrap<RatingBand[]>(apiClient.put<ApiResponse<RatingBand[]>>('/settings/rating-bands', payload)),
};
