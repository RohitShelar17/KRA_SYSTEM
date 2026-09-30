import axios, {
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios';
import type { ApiError, ApiResponse } from '@/types/api';
import type { TokenResponse } from '@/types/domain';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

const ACCESS_TOKEN_KEY = 'kra.accessToken';
const REFRESH_TOKEN_KEY = 'kra.refreshToken';

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_TOKEN_KEY),
  refresh: () => localStorage.getItem(REFRESH_TOKEN_KEY),
  set(access: string, refresh: string) {
    localStorage.setItem(ACCESS_TOKEN_KEY, access);
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh);
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};

export const apiClient: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.access();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * A 401 triggers one refresh attempt. Concurrent 401s queue behind that single
 * attempt, so a page firing six requests does not rotate the refresh token six
 * times — which the backend would treat as token reuse and revoke everything.
 */
let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = tokenStore.refresh();
  if (!refreshToken) {
    return null;
  }
  try {
    const response = await axios.post<ApiResponse<TokenResponse>>(
      `${BASE_URL}/auth/refresh`,
      { refreshToken },
      { headers: { 'Content-Type': 'application/json' } },
    );
    const tokens = response.data.data;
    tokenStore.set(tokens.accessToken, tokens.refreshToken);
    return tokens.accessToken;
  } catch {
    tokenStore.clear();
    return null;
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as InternalAxiosRequestConfig & { _retried?: boolean };
    const status = error.response?.status;
    const isAuthCall = original?.url?.includes('/auth/login') || original?.url?.includes('/auth/refresh');

    if (status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      refreshing = refreshing ?? refreshAccessToken().finally(() => {
        refreshing = null;
      });
      const token = await refreshing;
      if (token) {
        original.headers.Authorization = `Bearer ${token}`;
        return apiClient(original);
      }
      redirectToLogin();
    }
    return Promise.reject(error);
  },
);

function redirectToLogin() {
  tokenStore.clear();
  if (!window.location.pathname.startsWith('/login')) {
    window.location.assign('/login?expired=1');
  }
}

/** Unwraps the success envelope so callers work with the payload directly. */
export async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await promise;
  return response.data.data;
}

/** Turns any failure into a message worth showing, preferring field errors. */
export function toErrorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  const axiosError = error as AxiosError<ApiError>;
  const payload = axiosError?.response?.data;
  if (payload?.fieldErrors?.length) {
    return payload.fieldErrors.map((f) => f.message).join(', ');
  }
  return payload?.message ?? fallback;
}

export function errorCode(error: unknown): string | undefined {
  return (error as AxiosError<ApiError>)?.response?.data?.code;
}
