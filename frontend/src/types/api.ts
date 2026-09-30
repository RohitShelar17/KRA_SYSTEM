export interface ApiResponse<T> {
  success: boolean;
  message: string | null;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

/** The error envelope every endpoint returns; `code` is what the UI reacts to. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  traceId?: string;
  fieldErrors?: ApiFieldError[];
}
