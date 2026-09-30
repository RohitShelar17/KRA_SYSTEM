// Phase 6 types: notifications, the report catalogue and the two roll-up
// dashboards. The API serialises with non-null inclusion, so a field typed
// `T | null` here can arrive absent — compare with `== null` when reading one.

export type NotificationSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL';

export interface AppNotification {
  id: number;
  eventKey: string;
  title: string;
  body: string;
  link: string | null;
  severity: NotificationSeverity;
  entityType: string | null;
  entityId: number | null;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

export type ReportFilterKey =
  | 'EMPLOYEE_ID'
  | 'DEPARTMENT_ID'
  | 'MANAGER_ID'
  | 'DESIGNATION_ID'
  | 'FROM_DATE'
  | 'TO_DATE'
  | 'YEAR'
  | 'MONTH'
  | 'QUARTER'
  | 'RATING_BAND_ID'
  | 'CYCLE_ID'
  | 'STATUS'
  | 'LIMIT';

export type ColumnType =
  | 'TEXT'
  | 'NUMBER'
  | 'PERCENT'
  | 'SCORE'
  | 'DATE'
  | 'DATETIME'
  | 'DURATION'
  | 'STATUS';

export interface ReportDefinition {
  key: string;
  title: string;
  description: string;
  group: string;
  filters: ReportFilterKey[];
}

export interface ReportColumn {
  key: string;
  label: string;
  type: ColumnType;
}

export interface ReportResult {
  key: string;
  title: string;
  description: string;
  columns: ReportColumn[];
  rows: (string | number | boolean | null)[][];
  page: number;
  size: number;
  totalRows: number;
  totalPages: number;
  truncated: boolean;
  generatedAt: string;
}

export type ReportFormat = 'CSV' | 'XLSX' | 'PDF';
export type ReportRequestStatus = 'QUEUED' | 'RUNNING' | 'READY' | 'FAILED';

export interface ReportRequestSummary {
  id: number;
  reportKey: string;
  reportTitle: string;
  format: ReportFormat;
  status: ReportRequestStatus;
  rowCount: number | null;
  fileName: string | null;
  byteSize: number | null;
  errorDetail: string | null;
  requestedByName: string | null;
  createdAt: string;
  completedAt: string | null;
  downloadUrl: string | null;
}

export interface BandSlice {
  name: string;
  colourHex: string | null;
  count: number;
}

export interface TeamScore {
  name: string;
  average: number | null;
  scored: number;
}

export interface PersonScore {
  employeeCode: string | null;
  name: string | null;
  department: string | null;
  score: number | null;
  band: string | null;
}

export interface TrendPoint {
  label: string;
  value: number | null;
}

export interface DepartmentDashboard {
  departmentId: number;
  departmentName: string;
  periodLabel: string;
  headcount: number;
  managers: number;
  submittedToday: number;
  submissionRatePercent: number;
  productiveRatioPercent: number | null;
  averageScore: number | null;
  scoredCount: number;
  kraAverage: number | null;
  kpiAverage: number | null;
  tasksCompleted: number;
  tasksPending: number;
  appraisalsInFlight: number;
  appraisalsComplete: number;
  bandDistribution: BandSlice[];
  teamComparison: TeamScore[];
  topPerformers: PersonScore[];
  needsSupport: PersonScore[];
  scoreTrend: TrendPoint[];
}

export interface JobRunSummary {
  jobName: string;
  period: string;
  status: string;
  processed: number;
  failed: number;
  message: string | null;
  startedAt: string;
  finishedAt: string | null;
  healthy: boolean;
}

export interface AuditEntrySummary {
  actorName: string | null;
  module: string;
  action: string;
  entityType: string | null;
  entityId: number | null;
  reason: string | null;
  at: string;
}

export interface AdminDashboard {
  periodLabel: string;
  totalEmployees: number;
  activeEmployees: number;
  departments: number;
  teams: number;
  designations: number;
  submittedToday: number;
  submissionRatePercent: number;
  productiveRatioPercent: number | null;
  averageScore: number | null;
  scoredCount: number;
  openCycles: number;
  appraisalsComplete: number;
  bandDistribution: BandSlice[];
  departmentComparison: TeamScore[];
  topPerformers: PersonScore[];
  needsSupport: PersonScore[];
  submissionTrend: TrendPoint[];
  scheduledJobs: JobRunSummary[];
  recentActivity: AuditEntrySummary[];
}
