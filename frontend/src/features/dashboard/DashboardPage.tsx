import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import LinearProgress from '@mui/material/LinearProgress';
import Typography from '@mui/material/Typography';
import AccessTimeOutlined from '@mui/icons-material/AccessTimeOutlined';
import AssignmentTurnedInOutlined from '@mui/icons-material/AssignmentTurnedInOutlined';
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined';
import GroupsOutlined from '@mui/icons-material/GroupsOutlined';
import TaskAltOutlined from '@mui/icons-material/TaskAltOutlined';
import TrendingUpOutlined from '@mui/icons-material/TrendingUpOutlined';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useTheme } from '@mui/material/styles';
import { workDashboardApi } from '@/api/work';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatTile } from '@/components/StatTile';
import { StatusChip } from '@/components/StatusChip';
import { useAuth } from '@/contexts/AuthContext';
import { formatDuration, formatShortDate, toHours } from '@/utils/time';

export function DashboardPage() {
  const theme = useTheme();
  const { user, can } = useAuth();
  const isManager = can('WORKLOG_READ_TEAM');

  const employee = useQuery({
    queryKey: ['dashboard', 'employee'],
    queryFn: workDashboardApi.employee,
    retry: false,
  });

  const manager = useQuery({
    queryKey: ['dashboard', 'manager'],
    queryFn: () => workDashboardApi.manager(),
    enabled: isManager,
  });

  if (employee.isLoading) {
    return <FullPageLoader label="Loading your dashboard" />;
  }

  const data = employee.data;

  return (
    <Box>
      <PageHeader
        eyebrow={greeting()}
        title={firstName(user?.fullName)}
        description={
          user?.designation
            ? `${user.designation}${user.departmentName ? ` · ${user.departmentName}` : ''}`
            : 'Welcome to KRAPulse'
        }
        actions={
          <Button variant="contained" component={RouterLink} to="/worklogs">
            Open today's log
          </Button>
        }
      />

      {employee.isError && (
        <Alert severity="info" sx={{ mb: 6 }}>
          This account is not linked to an employee record, so there is no personal work data to show.
        </Alert>
      )}

      {data && (
        <>
          {data.unsubmittedDates.length > 0 && (
            <Alert
              severity="warning"
              sx={{ mb: 6 }}
              action={
                <Button size="small" component={RouterLink} to="/worklogs">
                  Fill them in
                </Button>
              }
            >
              {data.unsubmittedDates.length === 1
                ? '1 working day in the last week has not been submitted'
                : `${data.unsubmittedDates.length} working days in the last week have not been submitted`}
              : {data.unsubmittedDates.map(formatShortDate).join(', ')}
            </Alert>
          )}

          <Box
            sx={{
              display: 'grid',
              gap: 4,
              gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
              mb: 4,
            }}
          >
            <StatTile
              label="Logged today"
              value={toHours(data.todayLoggedMinutes)}
              unit={`/ ${toHours(data.officeMinutes)} hrs`}
              caption={`${data.todayEntryCount} ${data.todayEntryCount === 1 ? 'entry' : 'entries'} recorded`}
              icon={AccessTimeOutlined}
              progress={
                data.officeMinutes ? (data.todayLoggedMinutes / data.officeMinutes) * 100 : 0
              }
              trailing={<StatusChip status={data.todayStatus} />}
            />
            <StatTile
              label="Productive today"
              value={toHours(data.todayProductiveMinutes)}
              unit="hrs"
              caption={
                data.todayUnallocatedMinutes > 0
                  ? `${formatDuration(data.todayUnallocatedMinutes)} still unallocated`
                  : 'Full shift accounted for'
              }
              icon={TrendingUpOutlined}
              progress={
                data.todayLoggedMinutes
                  ? (data.todayProductiveMinutes / data.todayLoggedMinutes) * 100
                  : 0
              }
              progressColor="success"
            />
            <StatTile
              label="This week"
              value={toHours(data.weekLoggedMinutes)}
              unit={`/ ${toHours(data.weekExpectedMinutes)} hrs`}
              caption="Logged against expected hours so far"
              icon={AssignmentTurnedInOutlined}
              progress={
                data.weekExpectedMinutes
                  ? (data.weekLoggedMinutes / data.weekExpectedMinutes) * 100
                  : 0
              }
            />
            <StatTile
              label="Open tasks"
              value={data.openTasks}
              caption={
                data.overdueTasks > 0
                  ? `${data.overdueTasks} overdue · ${data.tasksDueToday} due today`
                  : `${data.completedTasks} completed to date`
              }
              icon={TaskAltOutlined}
              progressColor={data.overdueTasks > 0 ? 'error' : 'primary'}
            />
          </Box>

          <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', lg: '3fr 2fr' }, mb: 4 }}>
            <Card>
              <CardContent sx={{ p: 5 }}>
                <Typography variant="h5" sx={{ mb: 0.5 }}>
                  Hours logged, last seven days
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary', mb: 5 }}>
                  Productive time shown against everything recorded.
                </Typography>

                <Box sx={{ height: 260 }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={data.lastDays.map((point) => ({
                        label: point.label,
                        logged: Number((point.loggedMinutes / 60).toFixed(2)),
                        productive: Number((point.productiveMinutes / 60).toFixed(2)),
                      }))}
                      margin={{ top: 4, right: 8, bottom: 4, left: -20 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} vertical={false} />
                      <XAxis
                        dataKey="label"
                        tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                        tickLine={false}
                        axisLine={{ stroke: theme.palette.divider }}
                      />
                      <YAxis
                        tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                        tickLine={false}
                        axisLine={false}
                        unit="h"
                      />
                      <ChartTooltip
                        cursor={{ fill: theme.palette.action.hover }}
                        contentStyle={{
                          backgroundColor: theme.palette.background.paper,
                          border: `1px solid ${theme.palette.divider}`,
                          borderRadius: 8,
                          fontSize: 12,
                        }}
                      />
                      <Legend wrapperStyle={{ fontSize: 12 }} />
                      <Bar
                        dataKey="logged"
                        name="Logged"
                        fill={theme.palette.primary.main}
                        radius={[4, 4, 0, 0]}
                        maxBarSize={34}
                      />
                      <Bar
                        dataKey="productive"
                        name="Productive"
                        fill={theme.palette.success.main}
                        radius={[4, 4, 0, 0]}
                        maxBarSize={34}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </Box>
              </CardContent>
            </Card>

            <Card>
              <CardContent sx={{ p: 5 }}>
                <Typography variant="h5" sx={{ mb: 0.5 }}>
                  Where the week went
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
                  Time by activity category.
                </Typography>

                {data.categoryBreakdown.length === 0 ? (
                  <Typography variant="body2" sx={{ color: 'text.secondary', py: 8, textAlign: 'center' }}>
                    Nothing logged in the last seven days.
                  </Typography>
                ) : (
                  <>
                    <Box sx={{ height: 180 }}>
                      <ResponsiveContainer width="100%" height="100%">
                        <PieChart>
                          <Pie
                            data={data.categoryBreakdown}
                            dataKey="minutes"
                            nameKey="categoryName"
                            innerRadius={44}
                            outerRadius={72}
                            paddingAngle={2}
                          >
                            {data.categoryBreakdown.map((slice, index) => (
                              <Cell
                                key={slice.categoryName}
                                fill={slice.colourHex ?? fallbackColour(index, theme.palette.primary.main)}
                              />
                            ))}
                          </Pie>
                          <ChartTooltip
                            formatter={(value: number) => formatDuration(value)}
                            contentStyle={{
                              backgroundColor: theme.palette.background.paper,
                              border: `1px solid ${theme.palette.divider}`,
                              borderRadius: 8,
                              fontSize: 12,
                            }}
                          />
                        </PieChart>
                      </ResponsiveContainer>
                    </Box>

                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 3 }}>
                      {data.categoryBreakdown.slice(0, 5).map((slice, index) => (
                        <Box
                          key={slice.categoryName}
                          sx={{ display: 'flex', alignItems: 'center', gap: 2.5 }}
                        >
                          <Box
                            sx={{
                              width: 8,
                              height: 8,
                              borderRadius: '50%',
                              bgcolor: slice.colourHex ?? fallbackColour(index, theme.palette.primary.main),
                            }}
                          />
                          <Typography variant="body2" sx={{ flex: 1 }}>
                            {slice.categoryName}
                          </Typography>
                          <Typography variant="caption" className="tabular" sx={{ color: 'text.secondary' }}>
                            {formatDuration(slice.minutes)} · {slice.percent}%
                          </Typography>
                        </Box>
                      ))}
                    </Box>
                  </>
                )}
              </CardContent>
            </Card>
          </Box>
        </>
      )}

      {isManager && manager.data && (
        <Card>
          <CardContent sx={{ p: 5 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 4 }}>
              <Box>
                <Typography variant="h5">Your team today</Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  Submission compliance and what is waiting on you.
                </Typography>
              </Box>
              <Button size="small" component={RouterLink} to="/team/reviews">
                Open review queue
              </Button>
            </Box>

            <Box
              sx={{
                display: 'grid',
                gap: 4,
                gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
                mb: 5,
              }}
            >
              <StatTile label="Team size" value={manager.data.teamSize} icon={GroupsOutlined} />
              <StatTile
                label="Submitted today"
                value={manager.data.submittedForDate}
                unit={`/ ${manager.data.teamSize}`}
                caption={`${manager.data.submissionRatePercent}% submission rate`}
                progress={manager.data.submissionRatePercent}
                progressColor={manager.data.submissionRatePercent >= 80 ? 'success' : 'warning'}
              />
              <StatTile
                label="Waiting on you"
                value={manager.data.pendingApprovals}
                caption="Days submitted for review"
                icon={FactCheckOutlined}
              />
              <StatTile
                label="Team hours today"
                value={toHours(manager.data.teamLoggedMinutes)}
                unit="hrs"
                caption={`${manager.data.teamOpenTasks} open tasks · ${manager.data.teamOverdueTasks} overdue`}
              />
            </Box>

            {manager.data.missing.length > 0 && (
              <>
                <Divider sx={{ mb: 4 }} />
                <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 2 }}>
                  Not submitted today
                </Typography>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
                  {manager.data.missing.map((row) => (
                    <Chip
                      key={row.employeeId}
                      size="small"
                      variant="outlined"
                      color={row.startedButNotSubmitted ? 'warning' : 'default'}
                      label={
                        row.startedButNotSubmitted
                          ? `${row.employeeName} · draft ${formatDuration(row.draftMinutes)}`
                          : row.employeeName
                      }
                    />
                  ))}
                </Box>
              </>
            )}

            {manager.data.awaitingReview.length > 0 && (
              <>
                <Divider sx={{ my: 4 }} />
                <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 2 }}>
                  Oldest waiting for review
                </Typography>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
                  {manager.data.awaitingReview.map((row) => (
                    <Box
                      key={row.id}
                      sx={{ display: 'flex', alignItems: 'center', gap: 3, flexWrap: 'wrap' }}
                    >
                      <Typography variant="body2" sx={{ fontWeight: 600, minWidth: 160 }}>
                        {row.employeeName}
                      </Typography>
                      <Typography variant="caption" className="tabular" sx={{ color: 'text.secondary' }}>
                        {formatShortDate(row.workDate)}
                      </Typography>
                      <Box sx={{ flex: 1, minWidth: 120 }}>
                        <LinearProgress
                          variant="determinate"
                          value={Math.min(row.loggedPercent, 100)}
                          color={row.loggedPercent >= 90 ? 'success' : 'warning'}
                        />
                      </Box>
                      <Typography variant="caption" className="tabular" sx={{ color: 'text.secondary' }}>
                        {formatDuration(row.loggedMinutes)}
                      </Typography>
                    </Box>
                  ))}
                </Box>
              </>
            )}
          </CardContent>
        </Card>
      )}
    </Box>
  );
}

function fallbackColour(index: number, primary: string): string {
  const palette = [primary, '#0EA5E9', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#0891B2'];
  return palette[index % palette.length];
}

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) {
    return 'Good morning';
  }
  return hour < 17 ? 'Good afternoon' : 'Good evening';
}

function firstName(fullName?: string | null): string {
  if (!fullName) {
    return 'Welcome';
  }
  return fullName.split(' ')[0];
}
