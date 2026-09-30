import type { SvgIconComponent } from '@mui/icons-material';
import AssessmentOutlined from '@mui/icons-material/AssessmentOutlined';
import BadgeOutlined from '@mui/icons-material/BadgeOutlined';
import CalculateOutlined from '@mui/icons-material/CalculateOutlined';
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined';
import CategoryOutlined from '@mui/icons-material/CategoryOutlined';
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import EventNoteOutlined from '@mui/icons-material/EventNoteOutlined';
import EventRepeatOutlined from '@mui/icons-material/EventRepeatOutlined';
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined';
import FlagOutlined from '@mui/icons-material/FlagOutlined';
import GroupsOutlined from '@mui/icons-material/GroupsOutlined';
import InsightsOutlined from '@mui/icons-material/InsightsOutlined';
import HistoryEduOutlined from '@mui/icons-material/HistoryEduOutlined';
import PeopleAltOutlined from '@mui/icons-material/PeopleAltOutlined';
import RateReviewOutlined from '@mui/icons-material/RateReviewOutlined';
import SummarizeOutlined from '@mui/icons-material/SummarizeOutlined';
import DomainOutlined from '@mui/icons-material/DomainOutlined';
import NotificationsNoneOutlined from '@mui/icons-material/NotificationsNoneOutlined';
import SecurityOutlined from '@mui/icons-material/SecurityOutlined';
import SettingsOutlined from '@mui/icons-material/SettingsOutlined';
import TaskAltOutlined from '@mui/icons-material/TaskAltOutlined';
import TimelineOutlined from '@mui/icons-material/TimelineOutlined';
import WorkspacesOutlined from '@mui/icons-material/WorkspacesOutlined';

export interface NavItem {
  label: string;
  path: string;
  icon: SvgIconComponent;
  /** Any one of these permissions reveals the item. Empty means always visible. */
  permissions: string[];
  /** Items whose backing phase has not shipped are shown disabled, not hidden. */
  comingSoon?: boolean;
}

export interface NavSection {
  heading: string;
  items: NavItem[];
}

/**
 * The sidebar is generated from permissions, not roles: an administrator who
 * edits a role's permission bundle changes what that role sees, with no code
 * change. Items from later phases stay visible but disabled so the shape of the
 * product is legible from day one.
 */
export const navigation: NavSection[] = [
  {
    heading: 'Work tracking',
    items: [
      { label: 'Dashboard', path: '/dashboard', icon: DashboardOutlined, permissions: ['DASHBOARD_VIEW'] },
      {
        label: 'Daily Work Log',
        path: '/worklogs',
        icon: EventNoteOutlined,
        permissions: ['WORKLOG_WRITE_SELF'],
      },
      {
        label: 'My Tasks',
        path: '/tasks',
        icon: TaskAltOutlined,
        permissions: ['TASK_READ_SELF'],
      },
      {
        label: 'Calendar',
        path: '/calendar',
        icon: CalendarMonthOutlined,
        permissions: ['WORKLOG_WRITE_SELF', 'WORKLOG_READ_TEAM'],
      },
    ],
  },
  {
    heading: 'Performance & KRA',
    items: [
      {
        label: 'My KRA & KPIs',
        path: '/my-kra',
        icon: FlagOutlined,
        permissions: ['KRA_READ_SELF'],
      },
      {
        label: 'Scorecard Templates',
        path: '/kra-templates',
        icon: FlagOutlined,
        permissions: ['KRA_TEMPLATE_MANAGE'],
      },
      {
        label: 'My Performance',
        path: '/performance',
        icon: TimelineOutlined,
        permissions: ['PERFORMANCE_READ_SELF'],
      },
      {
        label: 'Self Assessment',
        path: '/self-assessment',
        icon: HistoryEduOutlined,
        permissions: ['SELF_ASSESSMENT_SUBMIT'],
      },
    ],
  },
  {
    heading: 'Management',
    items: [
      {
        label: 'Team Work Logs',
        path: '/team/worklogs',
        icon: FactCheckOutlined,
        permissions: ['WORKLOG_READ_TEAM'],
      },
      {
        label: 'Review Queue',
        path: '/team/reviews',
        icon: AssessmentOutlined,
        permissions: ['WORKLOG_APPROVE', 'PERFORMANCE_REVIEW'],
      },
      {
        label: 'Team Scorecards',
        path: '/team/kras',
        icon: InsightsOutlined,
        permissions: ['KRA_ASSIGN', 'KRA_READ_TEAM'],
      },
      {
        label: 'Team Tasks',
        path: '/team/tasks',
        icon: TaskAltOutlined,
        permissions: ['TASK_READ_TEAM'],
      },
      {
        label: 'Appraisal Reviews',
        path: '/team/appraisals',
        icon: RateReviewOutlined,
        permissions: ['PERFORMANCE_REVIEW'],
      },
      {
        label: 'Performance Console',
        path: '/team/performance',
        icon: TimelineOutlined,
        permissions: ['PERFORMANCE_READ_TEAM', 'PERFORMANCE_READ_ALL'],
      },
      { label: 'My Team', path: '/team', icon: GroupsOutlined, permissions: ['EMPLOYEE_READ'] },
      {
        label: 'Department Dashboard',
        path: '/department',
        icon: DomainOutlined,
        permissions: ['PERFORMANCE_READ_TEAM', 'PERFORMANCE_READ_ALL'],
      },
    ],
  },
  {
    heading: 'Organisation',
    items: [
      { label: 'Employees', path: '/employees', icon: PeopleAltOutlined, permissions: ['EMPLOYEE_READ'] },
      {
        label: 'Departments',
        path: '/departments',
        icon: WorkspacesOutlined,
        permissions: ['DEPARTMENT_READ'],
      },
      { label: 'Teams', path: '/teams', icon: GroupsOutlined, permissions: ['DEPARTMENT_READ'] },
      {
        label: 'Designations',
        path: '/designations',
        icon: BadgeOutlined,
        permissions: ['DEPARTMENT_READ'],
      },
    ],
  },
  {
    heading: 'Insight',
    items: [
      {
        label: 'Reports',
        path: '/reports',
        icon: SummarizeOutlined,
        permissions: ['REPORT_VIEW'],
      },
      {
        label: 'Organisation Dashboard',
        path: '/organisation',
        icon: DomainOutlined,
        permissions: ['PERFORMANCE_READ_ALL', 'SETTINGS_MANAGE'],
      },
      {
        label: 'Notifications',
        path: '/notifications',
        icon: NotificationsNoneOutlined,
        permissions: ['NOTIFICATION_READ'],
      },
    ],
  },
  {
    heading: 'Administration',
    items: [
      {
        label: 'Roles & Permissions',
        path: '/admin/roles',
        icon: SecurityOutlined,
        permissions: ['ROLE_MANAGE'],
      },
      {
        label: 'Scoring Formula',
        path: '/admin/formula',
        icon: CalculateOutlined,
        permissions: ['SETTINGS_MANAGE'],
      },
      {
        label: 'Appraisal Cycles',
        path: '/admin/cycles',
        icon: EventRepeatOutlined,
        permissions: ['SETTINGS_MANAGE'],
      },
      {
        label: 'Work Catalogue',
        path: '/admin/catalogue',
        icon: CategoryOutlined,
        permissions: ['SETTINGS_MANAGE'],
      },
      {
        label: 'Company Settings',
        path: '/admin/settings',
        icon: SettingsOutlined,
        permissions: ['SETTINGS_MANAGE'],
      },
      {
        label: 'Audit Logs',
        path: '/admin/audit',
        icon: HistoryEduOutlined,
        permissions: ['AUDIT_READ'],
      },
    ],
  },
];
