import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import BusinessOutlined from '@mui/icons-material/BusinessOutlined';
import EventAvailableOutlined from '@mui/icons-material/EventAvailableOutlined';
import PeopleAltOutlined from '@mui/icons-material/PeopleAltOutlined';
import TimelineOutlined from '@mui/icons-material/TimelineOutlined';
import { insightDashboardApi } from '@/api/insight';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatTile } from '@/components/StatTile';
import {
  BandDistribution,
  Nothing,
  PeopleList,
  TeamComparison,
  TrendLine,
  percent,
  score,
} from './DashboardPieces';

/**
 * The organisation view.
 *
 * <p>It shows two things most dashboards leave out: whether the scheduled
 * scoring jobs actually ran, and the last few audited actions. Both are
 * invisible failures otherwise.</p>
 */
export function OrganisationDashboardPage() {
  const now = new Date();
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [year, setYear] = useState(now.getFullYear());

  const dashboard = useQuery({
    queryKey: ['dashboard', 'admin', year, month],
    queryFn: () => insightDashboardApi.admin(year, month),
  });

  const data = dashboard.data;

  return (
    <Box>
      <PageHeader
        eyebrow="Administration"
        title="Organisation dashboard"
        description="Headcount, compliance, scores and the health of the jobs that produce them."
        actions={
          <Box sx={{ display: 'flex', gap: 2 }}>
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

      {dashboard.isLoading && <FullPageLoader label="Loading the organisation view" />}

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
              label="People"
              value={String(data.activeEmployees)}
              caption={`${data.totalEmployees} on the books`}
              icon={PeopleAltOutlined}
            />
            <StatTile
              label="Structure"
              value={`${data.departments}`}
              caption={`${data.teams} teams · ${data.designations} designations`}
              icon={BusinessOutlined}
            />
            <StatTile
              label="Submission rate"
              value={percent(data.submissionRatePercent)}
              caption={`${data.submittedToday} filed today`}
              progress={Math.min(data.submissionRatePercent, 100)}
              progressColor={data.submissionRatePercent >= 80 ? 'success' : 'warning'}
            />
            <StatTile
              label="Average score"
              value={score(data.averageScore)}
              caption={`${data.periodLabel} · ${data.scoredCount} scored`}
              icon={TimelineOutlined}
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
            <StatTile
              label="Productive time"
              value={percent(data.productiveRatioPercent)}
              caption="Share of logged hours this month"
            />
            <StatTile
              label="Open cycles"
              value={String(data.openCycles)}
              caption={`${data.appraisalsComplete} appraisals signed off`}
              icon={EventAvailableOutlined}
            />
            <StatTile
              label="Scheduled jobs"
              value={`${data.scheduledJobs.filter((job) => job.healthy).length}/${data.scheduledJobs.length}`}
              caption="Healthy of the last runs"
              progressColor={
                data.scheduledJobs.some((job) => !job.healthy) ? 'error' : 'success'
              }
            />
            <StatTile
              label="Recent activity"
              value={String(data.recentActivity.length)}
              caption="Audited actions shown below"
            />
          </Box>

          <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' } }}>
            <Panel title="Band distribution" subtitle="Where the organisation landed this period">
              <BandDistribution bands={data.bandDistribution} />
            </Panel>

            <Panel title="Submission rate" subtitle="Working days over the last fortnight">
              <TrendLine points={data.submissionTrend} suffix="%" />
            </Panel>

            <Panel title="Departments" subtitle="Average score, highest first">
              <TeamComparison teams={data.departmentComparison} />
            </Panel>

            <Panel title="Top performers" subtitle={`Highest scores in ${data.periodLabel}`}>
              <PeopleList people={data.topPerformers} empty="Nobody has been scored yet." />
            </Panel>

            <Panel title="Scheduled jobs" subtitle="The runs that produce every score">
              {data.scheduledJobs.length === 0 ? (
                <Nothing>No scheduled run has been recorded yet.</Nothing>
              ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {data.scheduledJobs.map((job) => (
                    <Box
                      key={`${job.jobName}-${job.period}`}
                      sx={{ display: 'flex', justifyContent: 'space-between', gap: 3 }}
                    >
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="body2">
                          {job.jobName.toLowerCase().replace(/_/g, ' ')}
                        </Typography>
                        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                          {job.period} · {job.processed} processed
                          {job.failed > 0 ? ` · ${job.failed} failed` : ''}
                        </Typography>
                      </Box>
                      <Chip
                        size="small"
                        color={job.healthy ? 'success' : 'error'}
                        variant="outlined"
                        label={job.status.toLowerCase()}
                      />
                    </Box>
                  ))}
                </Box>
              )}
            </Panel>

            <Panel title="Recent audited actions" subtitle="Who changed what, most recent first">
              {data.recentActivity.length === 0 ? (
                <Nothing>Nothing audited yet.</Nothing>
              ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {data.recentActivity.map((entry, index) => (
                    <Box key={index} sx={{ display: 'flex', justifyContent: 'space-between', gap: 3 }}>
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="body2">
                          {entry.actorName ?? 'System'} · {entry.action.toLowerCase()}{' '}
                          {entry.entityType ?? entry.module.toLowerCase()}
                        </Typography>
                        {entry.reason && (
                          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                            {entry.reason}
                          </Typography>
                        )}
                      </Box>
                      <Typography variant="caption" sx={{ color: 'text.disabled', flexShrink: 0 }}>
                        {new Date(entry.at).toLocaleString('en-GB', {
                          day: '2-digit',
                          month: 'short',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </Typography>
                    </Box>
                  ))}
                </Box>
              )}
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
