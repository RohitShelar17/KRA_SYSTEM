import { apiClient, unwrap } from './axiosClient';
import type { ApiResponse } from '@/types/api';
import type { CurrentUser, TokenResponse } from '@/types/domain';

export const authApi = {
  login: (identifier: string, password: string, rememberMe: boolean) =>
    unwrap<TokenResponse>(
      apiClient.post<ApiResponse<TokenResponse>>('/auth/login', { identifier, password, rememberMe }),
    ),

  me: () => unwrap<CurrentUser>(apiClient.get<ApiResponse<CurrentUser>>('/auth/me')),

  logout: (refreshToken: string | null) =>
    apiClient.post('/auth/logout', refreshToken ? { refreshToken } : {}),

  forgotPassword: (email: string) => apiClient.post('/auth/forgot-password', { email }),

  resetPassword: (token: string, newPassword: string) =>
    apiClient.post('/auth/reset-password', { token, newPassword }),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiClient.post('/auth/change-password', { currentPassword, newPassword }),
};
