import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AssignmentTurnedInOutlined from '@mui/icons-material/AssignmentTurnedInOutlined';
import GroupsOutlined from '@mui/icons-material/GroupsOutlined';
import TimelineOutlined from '@mui/icons-material/TimelineOutlined';
import TrendingUpOutlined from '@mui/icons-material/TrendingUpOutlined';
import { insightDashboardApi } from '@/api/insight';
import { organisationApi } from '@/api/organisation';
import { EmptyState } from '@/components/EmptyState';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatTile } from '@/components/StatTile';
import {
  BandDistribution,
  PeopleList,
  TeamComparison,
  TrendLine,
  percent,
  score,
} from './DashboardPieces';

/** The department head's view: their people, their hours, their scores. */
export function DepartmentDashboardPage() {
  const now = new Date();
  const [departmentId, setDepartmentId] = useState<number | ''>('');
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [year, setYear] = useState(now.getFullYear());

  const departments = useQuery({
    queryKey: ['departments', 'options'],
    queryFn: organisationApi.departmentOptions,
  });

  useEffect(() => {
    if (departmentId === '' && (departments.data ?? []).length > 0) {
      setDepartmentId(departments.data![0].id);
    }
  }, [departments.data, departmentId]);

  const dashboard = useQuery({
    queryKey: ['dashboard', 'department', departmentId, year, month],
    queryFn: () => insightDashboardApi.department(Number(departmentId), year, month),
    enabled: departmentId !== '',
  });

  const data = dashboard.data;

  return (
    <Box>
      <PageHeader
        eyebrow="Department"
        title="Department dashboard"
        description="One department's headcount, hours, scores and appraisal progress."
        actions={
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              select
              size="small"
              label="Department"
              value={departmentId}
              onChange={(event) => setDepartmentId(Number(event.target.value))}
              sx={{ minWidth: 200 }}
            >
              {(departments.data ?? []).map((option) => (
                <MenuItem key={option.id} value={option.id}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label="Month"
              value={month}
              onChange={(event) => setMonth(Number(event.target.value))}
              sx={{ minWidth: 140 }}
            >
              {Array.from({ length: 12 }, (_, index) => index + 1).map((value) => (
                <MenuItem key={value} value={value}>
                  {new Date(2000, value - 1, 1).toLocaleString('en-GB', { month: 'long' })}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label="Year"
              value={year}
              onChange={(event) => setYear(Number(event.target.value))}
              sx={{ minWidth: 110 }}
            >
              {[now.getFullYear(), now.getFullYear() - 1].map((value) => (
                <MenuItem key={value} value={value}>
                  {value}
                </MenuItem>
              ))}
            </TextField>
          </Box>
        }
      />

      {dashboard.isLoading && <FullPageLoader label="Loading department" />}

      {dashboard.isError && (
        <EmptyState
          title="That department could not be loaded"
          description="You may not have access to it, or it may have been deactivated."
        />
      )}

      {data && (
        <>
          <Box
            sx={{
              display: 'grid',
              gap: 4,
              gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
              mb: 4,
            }}
          >
            <StatTile
              label="Headcount"
              value={String(data.headcount)}
              caption={`${data.managers} reporting line${data.managers === 1 ? '' : 's'}`}
              icon={GroupsOutlined}
            />
            <StatTile
              label="Filed today"
              value={String(data.submittedToday)}
              caption={`${percent(data.submissionRatePercent)} of the month's working days filed`}
              progress={Math.min(data.submissionRatePercent, 100)}
              progressColor={data.submissionRatePercent >= 80 ? 'success' : 'warning'}
            />
            <StatTile
              label="Average score"
              value={score(data.averageScore)}
              caption={`${data.periodLabel} · ${data.scoredCount} scored`}
              icon={TimelineOutlined}
            />
            <StatTile
              label="Productive time"
              value={percent(data.productiveRatioPercent)}
              caption="Share of logged hours this month"
              icon={TrendingUpOutlined}
            />
          </Box>

          <Box
            sx={{
              display: 'grid',
              gap: 4,
              gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
              mb: 4,
            }}
          >
            <StatTile label="KRA achievement" value={score(data.kraAverage)} caption="Weighted average" />
            <StatTile label="KPI achievement" value={score(data.kpiAverage)} caption="Weighted average" />
            <StatTile
              label="Tasks"
              value={`${data.tasksCompleted} / ${data.tasksCompleted + data.tasksPending}`}
              caption={`${data.tasksPending} still open`}
              icon={AssignmentTurnedInOutlined}
            />
            <StatTile
              label="Appraisals"
              value={`${data.appraisalsComplete} / ${data.appraisalsComplete + data.appraisalsInFlight}`}
              caption={`${data.appraisalsInFlight} in flight`}
            />
          </Box>

          <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' } }}>
            <Panel title="Score distribution" subtitle="Where this department's people landed">
              <BandDistribution bands={data.bandDistribution} />
            </Panel>

            <Panel title="Score trend" subtitle="Monthly average over the last six months">
              <TrendLine points={data.scoreTrend} />
            </Panel>

            <Panel title="Department comparison" subtitle="Average score per department this period">
              <TeamComparison teams={data.teamComparison} />
            </Panel>

            <Panel title="Top performers" subtitle={`Highest scores in ${data.periodLabel}`}>
              <PeopleList people={data.topPerformers} empty="Nobody has been scored yet." />
            </Panel>

            <Panel
              title="Worth a conversation"
              subtitle="Lowest scores this period — a prompt, not a verdict"
            >
              <PeopleList people={data.needsSupport} empty="Nobody has been scored yet." />
            </Panel>
          </Box>
        </>
      )}
    </Box>
  );
}

function Panel({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
}) {
  return (
    <Card>
      <CardContent sx={{ p: 5 }}>
        <Typography variant="h6" sx={{ mb: 0.5 }}>
          {title}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>
          {subtitle}
        </Typography>
        {children}
      </CardContent>
    </Card>
  );
}
