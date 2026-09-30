import { apiClient, unwrap } from './axiosClient';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  AdminDashboard,
  AppNotification,
  DepartmentDashboard,
  ReportDefinition,
  ReportFormat,
  ReportRequestSummary,
  ReportResult,
} from '@/types/insight';

export type ReportFilters = Record<string, string>;

export const notificationApi = {
  feed: (unread: boolean, page = 0, size = 20) =>
    unwrap<PageResponse<AppNotification>>(
      apiClient.get<ApiResponse<PageResponse<AppNotification>>>('/notifications', {
        params: { unread, page, size },
      }),
    ),

  unreadCount: () =>
    unwrap<{ unread: number }>(
      apiClient.get<ApiResponse<{ unread: number }>>('/notifications/unread-count'),
    ),

  markRead: (ids: number[]) =>
    unwrap<{ updated: number }>(
      apiClient.post<ApiResponse<{ updated: number }>>('/notifications/read', { ids }),
    ),

  markAllRead: () =>
    unwrap<{ updated: number }>(
      apiClient.post<ApiResponse<{ updated: number }>>('/notifications/read-all'),
    ),
};

export const reportApi = {
  catalogue: () =>
    unwrap<ReportDefinition[]>(apiClient.get<ApiResponse<ReportDefinition[]>>('/reports')),

  run: (key: string, filters: ReportFilters, page = 0, size = 25) =>
    unwrap<ReportResult>(
      apiClient.post<ApiResponse<ReportResult>>(`/reports/${key}/run`, { filters, page, size }),
    ),

  export: (key: string, format: ReportFormat, filters: ReportFilters) =>
    unwrap<ReportRequestSummary>(
      apiClient.post<ApiResponse<ReportRequestSummary>>(`/reports/${key}/export`, {
        format,
        filters,
      }),
    ),

  exports: (page = 0, size = 10) =>
    unwrap<PageResponse<ReportRequestSummary>>(
      apiClient.get<ApiResponse<PageResponse<ReportRequestSummary>>>('/reports/exports', {
        params: { page, size },
      }),
    ),

  exportStatus: (id: number) =>
    unwrap<ReportRequestSummary>(
      apiClient.get<ApiResponse<ReportRequestSummary>>(`/reports/exports/${id}`),
    ),

  /**
   * Downloads through the API client so the bearer token travels with the
   * request; a plain link would be an unauthenticated GET and fail.
   */
  download: async (request: ReportRequestSummary) => {
    const response = await apiClient.get(`/reports/exports/${request.id}/download`, {
      responseType: 'blob',
    });
    const url = URL.createObjectURL(response.data as Blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = request.fileName ?? `${request.reportKey}.${request.format.toLowerCase()}`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  },
};

export const insightDashboardApi = {
  department: (departmentId: number, year?: number, month?: number) =>
    unwrap<DepartmentDashboard>(
      apiClient.get<ApiResponse<DepartmentDashboard>>(`/dashboard/department/${departmentId}`, {
        params: { year, month },
      }),
    ),

  admin: (year?: number, month?: number) =>
    unwrap<AdminDashboard>(
      apiClient.get<ApiResponse<AdminDashboard>>('/dashboard/admin', { params: { year, month } }),
    ),
};
