export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TaskStatus =
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'ON_HOLD'
  | 'BLOCKED'
  | 'CANCELLED';
export type ProjectStatus = 'PLANNED' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';
export type CategoryType = 'WORK' | 'MEETING' | 'BREAK' | 'ADMIN' | 'TRAINING' | 'SUPPORT' | 'OTHER';
export type Recurrence = 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY';
export type WorkLogDayStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'MODIFICATION_REQUESTED';
export type ApprovalAction =
  | 'SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'MODIFICATION_REQUESTED'
  | 'REOPENED';

export interface Project {
  id: number;
  code: string;
  name: string;
  description: string | null;
  departmentId: number | null;
  departmentName: string | null;
  projectManagerId: number | null;
  projectManagerName: string | null;
  clientName: string | null;
  startDate: string | null;
  endDate: string | null;
  status: ProjectStatus;
  billable: boolean;
  active: boolean;
  openTaskCount: number;
}

export interface TaskCategory {
  id: number;
  code: string;
  name: string;
  description: string | null;
  categoryType: CategoryType;
  productive: boolean;
  departmentId: number | null;
  departmentName: string | null;
  colourHex: string | null;
  displayOrder: number;
  active: boolean;
}

export interface TaskTemplate {
  id: number;
  departmentId: number | null;
  departmentName: string | null;
  taskCategoryId: number;
  taskCategoryName: string;
  colourHex: string | null;
  name: string;
  description: string | null;
  defaultEstimatedMinutes: number | null;
  defaultPriority: Priority;
  recurrence: Recurrence;
  displayOrder: number;
  active: boolean;
}

export interface Task {
  id: number;
  assigneeId: number;
  assigneeName: string;
  assigneeCode: string;
  assignedById: number | null;
  assignedByName: string | null;
  projectId: number | null;
  projectName: string | null;
  taskCategoryId: number;
  taskCategoryName: string;
  colourHex: string | null;
  title: string;
  description: string | null;
  priority: Priority;
  status: TaskStatus;
  completionPct: number;
  estimatedMinutes: number | null;
  actualMinutes: number;
  assignedDate: string;
  dueDate: string | null;
  completedAt: string | null;
  remarks: string | null;
  overdue: boolean;
  selfAssigned: boolean;
}

export interface TaskBoardCounts {
  total: number;
  notStarted: number;
  inProgress: number;
  completed: number;
  onHold: number;
  blocked: number;
  overdue: number;
}

export interface WorkLogEntry {
  id: number;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  taskCategoryId: number;
  taskCategoryName: string;
  categoryType: CategoryType;
  colourHex: string | null;
  projectId: number | null;
  projectName: string | null;
  taskId: number | null;
  taskTitle: string | null;
  employeeKraId: number | null;
  kraName: string | null;
  title: string;
  description: string | null;
  priority: Priority;
  status: TaskStatus;
  completionPct: number;
  productive: boolean;
  billable: boolean;
  remarks: string | null;
}

export interface TimelineSegment {
  startTime: string;
  endTime: string;
  durationMinutes: number;
  label: string;
  kind: CategoryType | 'UNALLOCATED';
  colourHex: string | null;
  entryId: number | null;
}

export interface DayTotals {
  officeMinutes: number;
  loggedMinutes: number;
  productiveMinutes: number;
  meetingMinutes: number;
  breakMinutes: number;
  unallocatedMinutes: number;
  overtimeMinutes: number;
  entryCount: number;
  loggedPercent: number;
  productivePercent: number;
}

export interface ApprovalHistoryItem {
  id: number;
  action: ApprovalAction;
  fromStatus: string;
  toStatus: string;
  actorName: string | null;
  comments: string | null;
  createdAt: string;
}

export interface WorkLogDay {
  id: number | null;
  employeeId: number;
  employeeName: string;
  employeeCode: string;
  workDate: string;
  dayOfWeek: string;
  status: WorkLogDayStatus;
  editable: boolean;
  holiday: boolean;
  holidayName: string | null;
  shiftStart: string;
  shiftEnd: string;
  totals: DayTotals;
  entries: WorkLogEntry[];
  timeline: TimelineSegment[];
  history: ApprovalHistoryItem[];
  submittedAt: string | null;
  reviewedByName: string | null;
  reviewedAt: string | null;
  reviewComments: string | null;
  approvalRequired: boolean;
}

export interface WorkLogDaySummary {
  id: number;
  employeeId: number;
  employeeName: string;
  employeeCode: string;
  departmentName: string | null;
  workDate: string;
  status: WorkLogDayStatus;
  loggedMinutes: number;
  productiveMinutes: number;
  officeMinutes: number;
  unallocatedMinutes: number;
  entryCount: number;
  submittedAt: string | null;
  loggedPercent: number;
}

export interface MissingSubmission {
  employeeId: number;
  employeeName: string;
  employeeCode: string;
  departmentName: string | null;
  workDate: string;
  startedButNotSubmitted: boolean;
  draftMinutes: number;
}

/**
 * A back-dating exception: one person, one date, time-boxed. `live` is false
 * once it has been revoked or has run out.
 */
export interface Unlock {
  id: number;
  employeeId: number;
  employeeName: string | null;
  employeeCode: string | null;
  workDate: string;
  reason: string;
  grantedByName: string | null;
  grantedAt: string;
  expiresAt: string;
  revokedAt: string | null;
  live: boolean;
}

export interface CategoryBreakdown {
  categoryName: string;
  colourHex: string | null;
  minutes: number;
  percent: number;
}

export interface EmployeeDashboard {
  today: string;
  todayStatus: WorkLogDayStatus;
  officeMinutes: number;
  todayLoggedMinutes: number;
  todayProductiveMinutes: number;
  todayUnallocatedMinutes: number;
  todayEntryCount: number;
  weekLoggedMinutes: number;
  weekExpectedMinutes: number;
  openTasks: number;
  completedTasks: number;
  overdueTasks: number;
  tasksDueToday: number;
  lastDays: {
    date: string;
    label: string;
    loggedMinutes: number;
    productiveMinutes: number;
    status: WorkLogDayStatus;
  }[];
  categoryBreakdown: CategoryBreakdown[];
  unsubmittedDates: string[];
}

export interface ManagerDashboard {
  date: string;
  teamSize: number;
  submittedForDate: number;
  missingForDate: number;
  pendingApprovals: number;
  teamLoggedMinutes: number;
  teamProductiveMinutes: number;
  teamOpenTasks: number;
  teamOverdueTasks: number;
  submissionRatePercent: number;
  missing: MissingSubmission[];
  awaitingReview: WorkLogDaySummary[];
}
