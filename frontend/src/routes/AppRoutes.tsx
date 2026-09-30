import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from '@/layouts/AppLayout';
import { FullPageLoader } from '@/components/LoadingState';
import { ProtectedRoute, PublicOnlyRoute } from './ProtectedRoute';

// Every route is code-split: the sign-in bundle should not carry the admin screens.
const LoginPage = lazy(() => import('@/features/auth/LoginPage').then((m) => ({ default: m.LoginPage })));
const ForgotPasswordPage = lazy(() =>
  import('@/features/auth/ForgotPasswordPage').then((m) => ({ default: m.ForgotPasswordPage })),
);
const ResetPasswordPage = lazy(() =>
  import('@/features/auth/ResetPasswordPage').then((m) => ({ default: m.ResetPasswordPage })),
);
const DashboardPage = lazy(() =>
  import('@/features/dashboard/DashboardPage').then((m) => ({ default: m.DashboardPage })),
);
const EmployeeListPage = lazy(() =>
  import('@/features/employees/EmployeeListPage').then((m) => ({ default: m.EmployeeListPage })),
);
const EmployeeProfilePage = lazy(() =>
  import('@/features/employees/EmployeeProfilePage').then((m) => ({ default: m.EmployeeProfilePage })),
);
const MyTeamPage = lazy(() =>
  import('@/features/employees/MyTeamPage').then((m) => ({ default: m.MyTeamPage })),
);
const DepartmentsPage = lazy(() =>
  import('@/features/organisation/DepartmentsPage').then((m) => ({ default: m.DepartmentsPage })),
);
const TeamsPage = lazy(() =>
  import('@/features/organisation/TeamsPage').then((m) => ({ default: m.TeamsPage })),
);
const DesignationsPage = lazy(() =>
  import('@/features/organisation/DesignationsPage').then((m) => ({ default: m.DesignationsPage })),
);
const CompanySettingsPage = lazy(() =>
  import('@/features/organisation/CompanySettingsPage').then((m) => ({ default: m.CompanySettingsPage })),
);
const DailyWorkLogPage = lazy(() =>
  import('@/features/worklogs/DailyWorkLogPage').then((m) => ({ default: m.DailyWorkLogPage })),
);
const TeamWorkLogsPage = lazy(() =>
  import('@/features/worklogs/TeamWorkLogsPage').then((m) => ({ default: m.TeamWorkLogsPage })),
);
const ReviewQueuePage = lazy(() =>
  import('@/features/worklogs/ReviewQueuePage').then((m) => ({ default: m.ReviewQueuePage })),
);
const CalendarPage = lazy(() =>
  import('@/features/calendar/CalendarPage').then((m) => ({ default: m.CalendarPage })),
);
const MyTasksPage = lazy(() =>
  import('@/features/tasks/MyTasksPage').then((m) => ({ default: m.MyTasksPage })),
);
const TeamTasksPage = lazy(() =>
  import('@/features/tasks/TeamTasksPage').then((m) => ({ default: m.TeamTasksPage })),
);
const MyKraPage = lazy(() =>
  import('@/features/kra/MyKraPage').then((m) => ({ default: m.MyKraPage })),
);
const TeamKraPage = lazy(() =>
  import('@/features/kra/TeamKraPage').then((m) => ({ default: m.TeamKraPage })),
);
const KraTemplatesPage = lazy(() =>
  import('@/features/kra/KraTemplatesPage').then((m) => ({ default: m.KraTemplatesPage })),
);
const MyPerformancePage = lazy(() =>
  import('@/features/performance/MyPerformancePage').then((m) => ({ default: m.MyPerformancePage })),
);
const TeamPerformancePage = lazy(() =>
  import('@/features/performance/TeamPerformancePage').then((m) => ({ default: m.TeamPerformancePage })),
);
const DepartmentDashboardPage = lazy(() =>
  import('@/features/dashboard/DepartmentDashboardPage').then((m) => ({
    default: m.DepartmentDashboardPage,
  })),
);
const OrganisationDashboardPage = lazy(() =>
  import('@/features/dashboard/OrganisationDashboardPage').then((m) => ({
    default: m.OrganisationDashboardPage,
  })),
);
const ReportsPage = lazy(() =>
  import('@/features/reports/ReportsPage').then((m) => ({ default: m.ReportsPage })),
);
const NotificationsPage = lazy(() =>
  import('@/features/notifications/NotificationsPage').then((m) => ({
    default: m.NotificationsPage,
  })),
);
const SelfAssessmentPage = lazy(() =>
  import('@/features/appraisal/SelfAssessmentPage').then((m) => ({ default: m.SelfAssessmentPage })),
);
const AppraisalReviewPage = lazy(() =>
  import('@/features/appraisal/AppraisalReviewPage').then((m) => ({ default: m.AppraisalReviewPage })),
);
const AppraisalCyclesPage = lazy(() =>
  import('@/features/appraisal/AppraisalCyclesPage').then((m) => ({ default: m.AppraisalCyclesPage })),
);
const FormulaSettingsPage = lazy(() =>
  import('@/features/admin/FormulaSettingsPage').then((m) => ({ default: m.FormulaSettingsPage })),
);
const WorkCataloguePage = lazy(() =>
  import('@/features/admin/WorkCataloguePage').then((m) => ({ default: m.WorkCataloguePage })),
);
const RolesPage = lazy(() => import('@/features/admin/RolesPage').then((m) => ({ default: m.RolesPage })));
const AuditLogPage = lazy(() =>
  import('@/features/admin/AuditLogPage').then((m) => ({ default: m.AuditLogPage })),
);
const ProfilePage = lazy(() =>
  import('@/features/profile/ProfilePage').then((m) => ({ default: m.ProfilePage })),
);

export function AppRoutes() {
  return (
    <Suspense fallback={<FullPageLoader />}>
      <Routes>
        <Route element={<PublicOnlyRoute />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
        </Route>

        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/notifications" element={<NotificationsPage />} />

            <Route element={<ProtectedRoute permissions={['REPORT_VIEW']} />}>
              <Route path="/reports" element={<ReportsPage />} />
            </Route>

            <Route
              element={<ProtectedRoute permissions={['PERFORMANCE_READ_TEAM', 'PERFORMANCE_READ_ALL']} />}
            >
              <Route path="/department" element={<DepartmentDashboardPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['PERFORMANCE_READ_ALL', 'SETTINGS_MANAGE']} />}>
              <Route path="/organisation" element={<OrganisationDashboardPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['DASHBOARD_VIEW']} />}>
              <Route path="/dashboard" element={<DashboardPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['WORKLOG_WRITE_SELF']} />}>
              <Route path="/worklogs" element={<DailyWorkLogPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['TASK_READ_SELF']} />}>
              <Route path="/tasks" element={<MyTasksPage />} />
            </Route>

            <Route
              element={<ProtectedRoute permissions={['WORKLOG_WRITE_SELF', 'WORKLOG_READ_TEAM']} />}
            >
              <Route path="/calendar" element={<CalendarPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['KRA_READ_SELF']} />}>
              <Route path="/my-kra" element={<MyKraPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['PERFORMANCE_READ_SELF']} />}>
              <Route path="/performance" element={<MyPerformancePage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['SELF_ASSESSMENT_SUBMIT']} />}>
              <Route path="/self-assessment" element={<SelfAssessmentPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['PERFORMANCE_REVIEW']} />}>
              <Route path="/team/appraisals" element={<AppraisalReviewPage />} />
            </Route>

            <Route
              element={<ProtectedRoute permissions={['PERFORMANCE_READ_TEAM', 'PERFORMANCE_READ_ALL']} />}
            >
              <Route path="/team/performance" element={<TeamPerformancePage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['KRA_ASSIGN', 'KRA_READ_TEAM']} />}>
              <Route path="/team/kras" element={<TeamKraPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['KRA_TEMPLATE_MANAGE']} />}>
              <Route path="/kra-templates" element={<KraTemplatesPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['WORKLOG_READ_TEAM']} />}>
              <Route path="/team/worklogs" element={<TeamWorkLogsPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['WORKLOG_APPROVE']} />}>
              <Route path="/team/reviews" element={<ReviewQueuePage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['TASK_READ_TEAM']} />}>
              <Route path="/team/tasks" element={<TeamTasksPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['EMPLOYEE_READ']} />}>
              <Route path="/employees" element={<EmployeeListPage />} />
              <Route path="/employees/:id" element={<EmployeeProfilePage />} />
              <Route path="/team" element={<MyTeamPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['DEPARTMENT_READ']} />}>
              <Route path="/departments" element={<DepartmentsPage />} />
              <Route path="/teams" element={<TeamsPage />} />
              <Route path="/designations" element={<DesignationsPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['SETTINGS_MANAGE']} />}>
              <Route path="/admin/settings" element={<CompanySettingsPage />} />
              <Route path="/admin/catalogue" element={<WorkCataloguePage />} />
              <Route path="/admin/formula" element={<FormulaSettingsPage />} />
              <Route path="/admin/cycles" element={<AppraisalCyclesPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['ROLE_MANAGE']} />}>
              <Route path="/admin/roles" element={<RolesPage />} />
            </Route>

            <Route element={<ProtectedRoute permissions={['AUDIT_READ']} />}>
              <Route path="/admin/audit" element={<AuditLogPage />} />
            </Route>
          </Route>
        </Route>

        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Suspense>
  );
}
