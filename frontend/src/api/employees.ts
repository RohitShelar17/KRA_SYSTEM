import { apiClient, unwrap } from './axiosClient';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  Employee,
  EmployeeStatus,
  EmployeeSummary,
  EmploymentType,
  Invite,
  Option,
  ReportingLine,
  WorkMode,
} from '@/types/domain';

export interface EmployeeSearchParams {
  query?: string;
  departmentId?: number;
  teamId?: number;
  designationId?: number;
  managerId?: number;
  status?: EmployeeStatus;
  activeOnly?: boolean;
  page?: number;
  size?: number;
  sort?: string;
  direction?: 'asc' | 'desc';
}

export interface EmployeeCreatePayload {
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string | null;
  departmentId: number;
  teamId?: number | null;
  designationId: number;
  reportingManagerId?: number | null;
  joiningDate: string;
  employmentType: EmploymentType;
  workLocation?: string | null;
  workMode: WorkMode;
  shiftStartTime?: string | null;
  shiftEndTime?: string | null;
  status?: EmployeeStatus;
  roleCodes: string[];
}

export type EmployeeUpdatePayload = Omit<
  EmployeeCreatePayload,
  'employeeCode' | 'email' | 'reportingManagerId'
> & { exitDate?: string | null; status: EmployeeStatus };

export const employeeApi = {
  search: (params: EmployeeSearchParams) =>
    unwrap<PageResponse<EmployeeSummary>>(
      apiClient.get<ApiResponse<PageResponse<EmployeeSummary>>>('/employees', { params }),
    ),
  options: () => unwrap<Option[]>(apiClient.get<ApiResponse<Option[]>>('/employees/options')),
  get: (id: number) => unwrap<Employee>(apiClient.get<ApiResponse<Employee>>(`/employees/${id}`)),
  me: () => unwrap<Employee>(apiClient.get<ApiResponse<Employee>>('/employees/me')),
  invite: (id: number) =>
    unwrap<Invite>(apiClient.post<ApiResponse<Invite>>(`/employees/${id}/invite`)),
  team: (id: number) =>
    unwrap<EmployeeSummary[]>(apiClient.get<ApiResponse<EmployeeSummary[]>>(`/employees/${id}/team`)),
  reportingHistory: (id: number) =>
    unwrap<ReportingLine[]>(
      apiClient.get<ApiResponse<ReportingLine[]>>(`/employees/${id}/reporting-history`),
    ),
  create: (payload: EmployeeCreatePayload) =>
    unwrap<Employee>(apiClient.post<ApiResponse<Employee>>('/employees', payload)),
  update: (id: number, payload: EmployeeUpdatePayload) =>
    unwrap<Employee>(apiClient.put<ApiResponse<Employee>>(`/employees/${id}`, payload)),
  changeManager: (id: number, managerId: number, effectiveFrom: string, reason?: string) =>
    unwrap<Employee>(
      apiClient.put<ApiResponse<Employee>>(`/employees/${id}/manager`, {
        managerId,
        effectiveFrom,
        reason,
      }),
    ),
  deactivate: (id: number, reason?: string) =>
    apiClient.delete(`/employees/${id}`, { params: { reason } }),
};
