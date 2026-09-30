import { apiClient, unwrap } from './axiosClient';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  CompanySettings,
  Department,
  Designation,
  Holiday,
  Option,
  Team,
} from '@/types/domain';

export interface DepartmentPayload {
  code: string;
  name: string;
  description?: string | null;
  parentDepartmentId?: number | null;
  headEmployeeId?: number | null;
}

export interface DesignationPayload {
  code: string;
  name: string;
  gradeLevel?: string | null;
  departmentId?: number | null;
  description?: string | null;
}

export interface TeamPayload {
  departmentId: number;
  code: string;
  name: string;
  description?: string | null;
  teamLeadEmployeeId?: number | null;
}

export const organisationApi = {
  departments: (params: { query?: string; activeOnly?: boolean; page?: number; size?: number }) =>
    unwrap<PageResponse<Department>>(
      apiClient.get<ApiResponse<PageResponse<Department>>>('/departments', { params }),
    ),
  departmentOptions: () =>
    unwrap<Option[]>(apiClient.get<ApiResponse<Option[]>>('/departments/options')),
  createDepartment: (payload: DepartmentPayload) =>
    unwrap<Department>(apiClient.post<ApiResponse<Department>>('/departments', payload)),
  updateDepartment: (id: number, payload: DepartmentPayload) =>
    unwrap<Department>(apiClient.put<ApiResponse<Department>>(`/departments/${id}`, payload)),
  deactivateDepartment: (id: number) => apiClient.delete(`/departments/${id}`),

  designations: (departmentId?: number) =>
    unwrap<Designation[]>(
      apiClient.get<ApiResponse<Designation[]>>('/designations', { params: { departmentId } }),
    ),
  designationOptions: (departmentId?: number) =>
    unwrap<Option[]>(
      apiClient.get<ApiResponse<Option[]>>('/designations/options', { params: { departmentId } }),
    ),
  createDesignation: (payload: DesignationPayload) =>
    unwrap<Designation>(apiClient.post<ApiResponse<Designation>>('/designations', payload)),
  updateDesignation: (id: number, payload: DesignationPayload) =>
    unwrap<Designation>(apiClient.put<ApiResponse<Designation>>(`/designations/${id}`, payload)),
  deactivateDesignation: (id: number) => apiClient.delete(`/designations/${id}`),

  teams: (departmentId?: number) =>
    unwrap<Team[]>(apiClient.get<ApiResponse<Team[]>>('/teams', { params: { departmentId } })),
  teamOptions: (departmentId?: number) =>
    unwrap<Option[]>(apiClient.get<ApiResponse<Option[]>>('/teams/options', { params: { departmentId } })),
  createTeam: (payload: TeamPayload) =>
    unwrap<Team>(apiClient.post<ApiResponse<Team>>('/teams', payload)),
  updateTeam: (id: number, payload: TeamPayload) =>
    unwrap<Team>(apiClient.put<ApiResponse<Team>>(`/teams/${id}`, payload)),
  deactivateTeam: (id: number) => apiClient.delete(`/teams/${id}`),

  settings: () =>
    unwrap<CompanySettings>(apiClient.get<ApiResponse<CompanySettings>>('/settings/company')),
  updateSettings: (payload: Partial<CompanySettings>) =>
    unwrap<CompanySettings>(apiClient.put<ApiResponse<CompanySettings>>('/settings/company', payload)),

  holidays: (year: number) =>
    unwrap<Holiday[]>(apiClient.get<ApiResponse<Holiday[]>>('/settings/holidays', { params: { year } })),
  addHoliday: (payload: { holidayDate: string; name: string; holidayType: string }) =>
    unwrap<Holiday>(apiClient.post<ApiResponse<Holiday>>('/settings/holidays', payload)),
  removeHoliday: (id: number) => apiClient.delete(`/settings/holidays/${id}`),
};
