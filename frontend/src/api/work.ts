import { apiClient, unwrap } from './axiosClient';
import type { ApiResponse, PageResponse } from '@/types/api';
import type { Option } from '@/types/domain';
import type {
  ApprovalAction,
  CategoryBreakdown,
  EmployeeDashboard,
  ManagerDashboard,
  MissingSubmission,
  Priority,
  Project,
  ProjectStatus,
  Recurrence,
  Task,
  TaskBoardCounts,
  TaskCategory,
  TaskStatus,
  TaskTemplate,
  Unlock,
  WorkLogDay,
  WorkLogDayStatus,
  WorkLogDaySummary,
} from '@/types/work';

export interface WorkLogEntryPayload {
  workDate: string;
  startTime: string;
  endTime: string;
  taskCategoryId: number;
  projectId?: number | null;
  taskId?: number | null;
  employeeKraId?: number | null;
  title: string;
  description?: string | null;
  priority?: Priority;
  status?: TaskStatus;
  completionPct?: number | null;
  billable: boolean;
  remarks?: string | null;
}

export interface TaskPayload {
  assigneeId?: number | null;
  projectId?: number | null;
  taskCategoryId: number;
  taskTemplateId?: number | null;
  title: string;
  description?: string | null;
  priority?: Priority;
  status?: TaskStatus;
  estimatedMinutes?: number | null;
  assignedDate?: string | null;
  dueDate?: string | null;
  remarks?: string | null;
}

export interface ProjectPayload {
  code: string;
  name: string;
  description?: string | null;
  departmentId?: number | null;
  projectManagerId?: number | null;
  clientName?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  status?: ProjectStatus;
  billable: boolean;
}

export interface CategoryPayload {
  code: string;
  name: string;
  description?: string | null;
  categoryType: string;
  productive: boolean;
  departmentId?: number | null;
  colourHex?: string | null;
  displayOrder: number;
}

export interface TemplatePayload {
  departmentId?: number | null;
  taskCategoryId: number;
  name: string;
  description?: string | null;
  defaultEstimatedMinutes?: number | null;
  defaultPriority?: Priority;
  recurrence?: Recurrence;
  displayOrder: number;
}

export const workLogApi = {
  today: () => unwrap<WorkLogDay>(apiClient.get<ApiResponse<WorkLogDay>>('/worklogs/today')),

  day: (date: string, employeeId?: number) =>
    unwrap<WorkLogDay>(
      apiClient.get<ApiResponse<WorkLogDay>>('/worklogs/day', { params: { date, employeeId } }),
    ),

  range: (from: string, to: string, employeeId?: number) =>
    unwrap<WorkLogDaySummary[]>(
      apiClient.get<ApiResponse<WorkLogDaySummary[]>>('/worklogs/range', {
        params: { from, to, employeeId },
      }),
    ),

  breakdown: (from: string, to: string, employeeId?: number) =>
    unwrap<CategoryBreakdown[]>(
      apiClient.get<ApiResponse<CategoryBreakdown[]>>('/worklogs/breakdown', {
        params: { from, to, employeeId },
      }),
    ),

  addEntry: (payload: WorkLogEntryPayload) =>
    unwrap<WorkLogDay>(apiClient.post<ApiResponse<WorkLogDay>>('/worklogs', payload)),

  updateEntry: (entryId: number, payload: WorkLogEntryPayload) =>
    unwrap<WorkLogDay>(apiClient.put<ApiResponse<WorkLogDay>>(`/worklogs/${entryId}`, payload)),

  deleteEntry: (entryId: number) =>
    unwrap<WorkLogDay>(apiClient.delete<ApiResponse<WorkLogDay>>(`/worklogs/${entryId}`)),

  submit: (workDate: string, comments?: string) =>
    unwrap<WorkLogDay>(apiClient.post<ApiResponse<WorkLogDay>>('/worklogs/submit', { workDate, comments })),

  pendingApprovals: (params: {
    employeeId?: number;
    status?: WorkLogDayStatus;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }) =>
    unwrap<PageResponse<WorkLogDaySummary>>(
      apiClient.get<ApiResponse<PageResponse<WorkLogDaySummary>>>('/worklogs/pending-approvals', {
        params,
      }),
    ),

  pendingCount: () =>
    unwrap<{ pending: number }>(
      apiClient.get<ApiResponse<{ pending: number }>>('/worklogs/pending-count'),
    ),

  review: (dayId: number, action: ApprovalAction, comments?: string) =>
    unwrap<WorkLogDay>(
      apiClient.post<ApiResponse<WorkLogDay>>(`/worklogs/${dayId}/review`, { action, comments }),
    ),

  missing: (date: string) =>
    unwrap<MissingSubmission[]>(
      apiClient.get<ApiResponse<MissingSubmission[]>>('/worklogs/missing', { params: { date } }),
    ),

  unlock: (payload: { employeeId: number; workDate: string; reason: string }) =>
    unwrap<Unlock>(apiClient.post<ApiResponse<Unlock>>('/worklogs/unlocks', payload)),

  unlocks: (liveOnly = true) =>
    unwrap<Unlock[]>(
      apiClient.get<ApiResponse<Unlock[]>>('/worklogs/unlocks', { params: { liveOnly } }),
    ),

  closeUnlock: (id: number) => apiClient.delete(`/worklogs/unlocks/${id}`),
};

export const taskApi = {
  mine: (params: { status?: TaskStatus; openOnly?: boolean; page?: number; size?: number }) =>
    unwrap<PageResponse<Task>>(
      apiClient.get<ApiResponse<PageResponse<Task>>>('/tasks/my', { params }),
    ),

  team: (params: {
    assigneeId?: number;
    projectId?: number;
    status?: TaskStatus;
    priority?: Priority;
    openOnly?: boolean;
    query?: string;
    page?: number;
    size?: number;
  }) =>
    unwrap<PageResponse<Task>>(
      apiClient.get<ApiResponse<PageResponse<Task>>>('/tasks/team', { params }),
    ),

  counts: (employeeId?: number) =>
    unwrap<TaskBoardCounts>(
      apiClient.get<ApiResponse<TaskBoardCounts>>('/tasks/counts', { params: { employeeId } }),
    ),

  open: () => unwrap<Task[]>(apiClient.get<ApiResponse<Task[]>>('/tasks/open')),

  create: (payload: TaskPayload) => unwrap<Task>(apiClient.post<ApiResponse<Task>>('/tasks', payload)),

  update: (id: number, payload: TaskPayload) =>
    unwrap<Task>(apiClient.put<ApiResponse<Task>>(`/tasks/${id}`, payload)),

  changeStatus: (id: number, status: TaskStatus, completionPct?: number, remarks?: string) =>
    unwrap<Task>(
      apiClient.patch<ApiResponse<Task>>(`/tasks/${id}/status`, { status, completionPct, remarks }),
    ),

  cancel: (id: number) => apiClient.delete(`/tasks/${id}`),
};

export const catalogueApi = {
  projects: () => unwrap<Project[]>(apiClient.get<ApiResponse<Project[]>>('/projects')),
  projectOptions: () => unwrap<Option[]>(apiClient.get<ApiResponse<Option[]>>('/projects/options')),
  createProject: (payload: ProjectPayload) =>
    unwrap<Project>(apiClient.post<ApiResponse<Project>>('/projects', payload)),
  updateProject: (id: number, payload: ProjectPayload) =>
    unwrap<Project>(apiClient.put<ApiResponse<Project>>(`/projects/${id}`, payload)),
  deactivateProject: (id: number) => apiClient.delete(`/projects/${id}`),

  categories: () => unwrap<TaskCategory[]>(apiClient.get<ApiResponse<TaskCategory[]>>('/task-categories')),
  availableCategories: () =>
    unwrap<TaskCategory[]>(apiClient.get<ApiResponse<TaskCategory[]>>('/task-categories/available')),
  createCategory: (payload: CategoryPayload) =>
    unwrap<TaskCategory>(apiClient.post<ApiResponse<TaskCategory>>('/task-categories', payload)),
  updateCategory: (id: number, payload: CategoryPayload) =>
    unwrap<TaskCategory>(apiClient.put<ApiResponse<TaskCategory>>(`/task-categories/${id}`, payload)),
  deactivateCategory: (id: number) => apiClient.delete(`/task-categories/${id}`),

  templates: (departmentId?: number) =>
    unwrap<TaskTemplate[]>(
      apiClient.get<ApiResponse<TaskTemplate[]>>('/task-templates', { params: { departmentId } }),
    ),
  availableTemplates: () =>
    unwrap<TaskTemplate[]>(apiClient.get<ApiResponse<TaskTemplate[]>>('/task-templates/available')),
  createTemplate: (payload: TemplatePayload) =>
    unwrap<TaskTemplate>(apiClient.post<ApiResponse<TaskTemplate>>('/task-templates', payload)),
  updateTemplate: (id: number, payload: TemplatePayload) =>
    unwrap<TaskTemplate>(apiClient.put<ApiResponse<TaskTemplate>>(`/task-templates/${id}`, payload)),
  deactivateTemplate: (id: number) => apiClient.delete(`/task-templates/${id}`),
};

export const workDashboardApi = {
  employee: () =>
    unwrap<EmployeeDashboard>(apiClient.get<ApiResponse<EmployeeDashboard>>('/dashboard/employee')),
  manager: (date?: string) =>
    unwrap<ManagerDashboard>(
      apiClient.get<ApiResponse<ManagerDashboard>>('/dashboard/manager', { params: { date } }),
    ),
};
