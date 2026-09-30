import { apiClient, unwrap } from './axiosClient';
import type { ApiResponse } from '@/types/api';
import type { Option } from '@/types/domain';
import type {
  KpiHistoryPoint,
  KpiProgress,
  KraScorecard,
  KraTemplate,
  PeriodType,
} from '@/types/kra';

export interface KpiTemplateItemPayload {
  id?: number | null;
  name: string;
  description?: string | null;
  measurementType: string;
  targetDirection: string;
  unit?: string | null;
  defaultTargetValue?: number | null;
  weightage: number;
  capPercent?: number | null;
  actualSource?: string | null;
  displayOrder: number;
}

export interface KraTemplateItemPayload {
  id?: number | null;
  name: string;
  description?: string | null;
  weightage: number;
  displayOrder: number;
  kpis: KpiTemplateItemPayload[];
}

export interface KraTemplatePayload {
  code: string;
  name: string;
  description?: string | null;
  designationId?: number | null;
  departmentId?: number | null;
  periodType: PeriodType;
  published: boolean;
  items: KraTemplateItemPayload[];
}

export interface AssignKraPayload {
  templateId: number;
  periodType: PeriodType;
  periodYear: number;
  periodIndex: number;
  seedTargets: boolean;
  replaceExisting: boolean;
}

export interface ScorecardQuery {
  periodType?: PeriodType;
  year?: number;
  periodIndex?: number;
}

export const kraTemplateApi = {
  list: () => unwrap<KraTemplate[]>(apiClient.get<ApiResponse<KraTemplate[]>>('/kra-templates')),

  get: (id: number) =>
    unwrap<KraTemplate>(apiClient.get<ApiResponse<KraTemplate>>(`/kra-templates/${id}`)),

  assignable: (designationId?: number) =>
    unwrap<Option[]>(
      apiClient.get<ApiResponse<Option[]>>('/kra-templates/assignable', { params: { designationId } }),
    ),

  create: (payload: KraTemplatePayload) =>
    unwrap<KraTemplate>(apiClient.post<ApiResponse<KraTemplate>>('/kra-templates', payload)),

  update: (id: number, payload: KraTemplatePayload) =>
    unwrap<KraTemplate>(apiClient.put<ApiResponse<KraTemplate>>(`/kra-templates/${id}`, payload)),

  publish: (id: number, published: boolean) =>
    unwrap<KraTemplate>(
      apiClient.post<ApiResponse<KraTemplate>>(`/kra-templates/${id}/publish`, null, {
        params: { published },
      }),
    ),

  deactivate: (id: number) => apiClient.delete(`/kra-templates/${id}`),
};

export const kraApi = {
  myScorecard: (query: ScorecardQuery) =>
    unwrap<KraScorecard>(apiClient.get<ApiResponse<KraScorecard>>('/kras/my', { params: query })),

  employeeScorecard: (employeeId: number, query: ScorecardQuery) =>
    unwrap<KraScorecard>(
      apiClient.get<ApiResponse<KraScorecard>>(`/employees/${employeeId}/kras`, { params: query }),
    ),

  /** KRAs open on a date, for attributing work-log time. */
  activeOptions: (date?: string) =>
    unwrap<Option[]>(apiClient.get<ApiResponse<Option[]>>('/kras/active', { params: { date } })),

  assign: (employeeId: number, payload: AssignKraPayload) =>
    unwrap<KraScorecard>(
      apiClient.post<ApiResponse<KraScorecard>>(`/employees/${employeeId}/kras/assign`, payload),
    ),

  updateWeightages: (
    employeeId: number,
    items: { id: number; weightage: number }[],
    reason?: string,
  ) =>
    unwrap<KraScorecard>(
      apiClient.put<ApiResponse<KraScorecard>>(`/employees/${employeeId}/kras/weightages`, {
        items,
        reason,
      }),
    ),

  setTarget: (
    kpiId: number,
    payload: {
      periodType: PeriodType;
      periodStart: string;
      periodEnd: string;
      targetValue: number;
      stretchValue?: number | null;
      notes?: string | null;
    },
  ) =>
    unwrap<KpiProgress>(
      apiClient.post<ApiResponse<KpiProgress>>(`/employee-kpis/${kpiId}/targets`, payload),
    ),

  recordActual: (
    kpiId: number,
    payload: { periodStart: string; periodEnd: string; actualValue: number; note?: string | null },
  ) =>
    unwrap<KpiProgress>(
      apiClient.post<ApiResponse<KpiProgress>>(`/employee-kpis/${kpiId}/actuals`, payload),
    ),

  history: (kpiId: number) =>
    unwrap<KpiHistoryPoint[]>(
      apiClient.get<ApiResponse<KpiHistoryPoint[]>>(`/employee-kpis/${kpiId}/history`),
    ),
};
