export type EmployeeStatus = 'ACTIVE' | 'ON_NOTICE' | 'INACTIVE' | 'TERMINATED' | 'ON_LEAVE';
export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERN' | 'CONSULTANT';
export type WorkMode = 'OFFICE' | 'REMOTE' | 'HYBRID';
export type DataScope = 'SELF' | 'REPORTS' | 'DEPARTMENT' | 'ORGANISATION';
export type HolidayType = 'PUBLIC' | 'RESTRICTED' | 'COMPANY' | 'WEEK_OFF';
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'PENDING_ACTIVATION';
export type RelationType = 'PRIMARY' | 'DOTTED_LINE';

export interface CurrentUser {
  userId: number;
  employeeId: number | null;
  employeeCode: string | null;
  username: string;
  fullName: string;
  email: string;
  phone: string | null;
  departmentId: number | null;
  departmentName: string | null;
  teamId: number | null;
  teamName: string | null;
  designation: string | null;
  reportingManagerId: number | null;
  reportingManagerName: string | null;
  profileImageUrl: string | null;
  dataScope: DataScope;
  mustChangePassword: boolean;
  roles: string[];
  permissions: string[];
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: CurrentUser;
}

export interface Option {
  id: number;
  code: string | null;
  label: string;
}

export interface Department {
  id: number;
  code: string;
  name: string;
  description: string | null;
  parentDepartmentId: number | null;
  parentDepartmentName: string | null;
  headEmployeeId: number | null;
  headEmployeeName: string | null;
  employeeCount: number;
  active: boolean;
}

export interface Designation {
  id: number;
  code: string;
  name: string;
  gradeLevel: string | null;
  departmentId: number | null;
  departmentName: string | null;
  description: string | null;
  active: boolean;
}

export interface Team {
  id: number;
  departmentId: number;
  departmentName: string | null;
  code: string;
  name: string;
  description: string | null;
  teamLeadEmployeeId: number | null;
  teamLeadName: string | null;
  active: boolean;
}

export interface EmployeeSummary {
  id: number;
  employeeCode: string;
  fullName: string;
  email: string;
  departmentName: string | null;
  teamName: string | null;
  designationName: string | null;
  reportingManagerName: string | null;
  status: EmployeeStatus;
  joiningDate: string;
  profileImageUrl: string | null;
  active: boolean;
  /**
   * The sign-in account's state, which is a different question from
   * employment: somebody present and employed is PENDING_ACTIVATION until they
   * use their invite. Jackson omits nulls, so an employee with no account at
   * all arrives with this undefined.
   */
  userStatus?: UserStatus | null;
}

export interface Employee extends Omit<EmployeeSummary, 'departmentName' | 'teamName' | 'designationName'> {
  firstName: string;
  lastName: string;
  phone: string | null;
  departmentId: number;
  departmentName: string | null;
  teamId: number | null;
  teamName: string | null;
  designationId: number;
  designationName: string | null;
  gradeLevel: string | null;
  reportingManagerId: number | null;
  exitDate: string | null;
  employmentType: EmploymentType;
  workLocation: string | null;
  workMode: WorkMode;
  shiftStartTime: string | null;
  shiftEndTime: string | null;
  userId: number | null;
  userStatus: UserStatus | null;
  roles: string[];
  directReportCount: number;
}

export interface ReportingLine {
  id: number;
  employeeId: number;
  managerId: number;
  managerName: string | null;
  relationType: RelationType;
  validFrom: string;
  validTo: string | null;
  current: boolean;
}

export interface CompanySettings {
  companyId: number;
  companyName: string | null;
  workStartTime: string;
  workEndTime: string;
  breakMinutes: number;
  workingDays: string;
  worklogApprovalEnabled: boolean;
  overtimeAllowed: boolean;
  maxOvertimeMinutes: number;
  backdateWindowDays: number;
  worklogCutoffTime: string;
  minSubmissionRatePct: number;
  netWorkingMinutes: number;
}

export interface Holiday {
  id: number;
  holidayDate: string;
  name: string;
  holidayType: HolidayType;
  dayOfWeek: string;
}

/**
 * The result of issuing a sign-in invite. `resetLink` sets a password, it is
 * not one: single-use, time-limited, and shown so an administrator can pass it
 * on when email is off or has not arrived.
 */
export interface Invite {
  employeeId: number;
  employeeName: string;
  signInName: string;
  resetLink: string;
  expiresAt: string;
  emailed: boolean;
}

export interface Role {
  id: number;
  code: string;
  name: string;
  description: string | null;
  dataScope: DataScope;
  systemRole: boolean;
  active: boolean;
  permissions: string[];
  permissionCount: number;
}

export interface Permission {
  id: number;
  code: string;
  name: string;
  module: string;
  description: string | null;
}

export interface FoundationOverview {
  totalEmployees: number;
  activeEmployees: number;
  onNoticeEmployees: number;
  totalDepartments: number;
  totalTeams: number;
  totalDesignations: number;
  directReports: number;
  visibleEmployees: number;
  dataScope: DataScope;
  headcountByDepartment: { departmentId: number; departmentName: string; headcount: number }[];
}

export interface AuditEntry {
  id: number;
  actorName: string | null;
  actorUserId: number | null;
  action: string;
  module: string;
  entityType: string | null;
  entityId: number | null;
  oldValue: string | null;
  newValue: string | null;
  reason: string | null;
  ipAddress: string | null;
  createdAt: string;
}
