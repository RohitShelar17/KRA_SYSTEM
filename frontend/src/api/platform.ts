import { apiClient, unwrap } from './axiosClient';
import type { ApiResponse, PageResponse } from '@/types/api';
import type { AuditEntry, FoundationOverview, Permission, Role } from '@/types/domain';

export const platformApi = {
  overview: () =>
    unwrap<FoundationOverview>(apiClient.get<ApiResponse<FoundationOverview>>('/dashboard/overview')),

  roles: () => unwrap<Role[]>(apiClient.get<ApiResponse<Role[]>>('/roles')),
  permissions: () => unwrap<Permission[]>(apiClient.get<ApiResponse<Permission[]>>('/roles/permissions')),
  updateRolePermissions: (id: number, permissionCodes: string[]) =>
    unwrap<Role>(apiClient.put<ApiResponse<Role>>(`/roles/${id}/permissions`, { permissionCodes })),

  auditLogs: (params: {
    module?: string;
    action?: string;
    entityType?: string;
    page?: number;
    size?: number;
  }) =>
    unwrap<PageResponse<AuditEntry>>(
      apiClient.get<ApiResponse<PageResponse<AuditEntry>>>('/audit-logs', { params }),
    ),
};
