import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import FlagOutlined from '@mui/icons-material/FlagOutlined';
import InsightsOutlined from '@mui/icons-material/InsightsOutlined';
import SpeedOutlined from '@mui/icons-material/SpeedOutlined';
import { useTheme } from '@mui/material/styles';
import { kraApi } from '@/api/kra';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatTile } from '@/components/StatTile';
import { PeriodPicker, currentPeriod, type PeriodSelection } from './PeriodPicker';
import { ScorecardView, achievementColour, formatNumber } from './ScorecardView';

export function MyKraPage() {
  const theme = useTheme();
  const [period, setPeriod] = useState<PeriodSelection>(currentPeriod());

  const scorecard = useQuery({
    queryKey: ['kra', 'my', period],
    queryFn: () =>
      kraApi.myScorecard({
        periodType: period.periodType,
        year: period.year,
        periodIndex: period.periodIndex,
      }),
    retry: false,
  });

  if (scorecard.isLoading) {
    return <FullPageLoader label="Loading your scorecard" />;
  }

  const data = scorecard.data;

  return (
    <Box>
      <PageHeader
        eyebrow="Performance & KRA"
        title="My KRAs & KPIs"
        description="What you are measured on this period, with where each figure came from."
      />

      {scorecard.isError && (
        <Alert severity="info" sx={{ mb: 4 }}>
          No scorecard is available for this account yet.
        </Alert>
      )}

      <Card sx={{ mb: 4 }}>
        <Box sx={{ p: 4, display: 'flex', flexWrap: 'wrap', gap: 3, alignItems: 'center' }}>
          <PeriodPicker value={period} onChange={setPeriod} />
          {data && (
            <Typography variant="caption" sx={{ color: 'text.secondary', ml: 'auto' }}>
              {data.periodStart} to {data.periodEnd}
            </Typography>
          )}
        </Box>
      </Card>

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
              label="Overall achievement"
              value={data.scored ? formatNumber(data.overallAchievement) : '—'}
              unit={data.scored ? '%' : undefined}
              caption={data.scored ? `Weighted across ${data.kraCount} KRAs` : 'Nothing measured yet'}
              icon={SpeedOutlined}
              progress={data.overallAchievement ?? 0}
              progressColor={
                (data.overallAchievement ?? 0) >= 100
                  ? 'success'
                  : (data.overallAchievement ?? 0) >= 80
                    ? 'warning'
                    : 'error'
              }
            />
            <StatTile
              label="Result areas"
              value={data.kraCount}
              caption={`${formatNumber(data.weightageTotal)}% total weight`}
              icon={FlagOutlined}
            />
            <StatTile
              label="KPIs measured"
              value={data.scoredKpiCount}
              unit={`/ ${data.kpiCount}`}
              caption={
                data.kpiCount === data.scoredKpiCount
                  ? 'Every KPI has a target and an actual'
                  : 'Some KPIs are still waiting on data'
              }
              icon={InsightsOutlined}
              progress={data.kpiCount ? (data.scoredKpiCount / data.kpiCount) * 100 : 0}
            />
            <StatTile
              label="Period"
              value={data.periodLabel}
              caption={data.designation ?? 'Your scorecard'}
            />
          </Box>

          {data.scored && (
            <Alert
              severity={
                (data.overallAchievement ?? 0) >= 100
                  ? 'success'
                  : (data.overallAchievement ?? 0) >= 80
                    ? 'info'
                    : 'warning'
              }
              sx={{ mb: 4 }}
              icon={false}
            >
              <Typography variant="body2">
                You are at{' '}
                <strong style={{ color: achievementColour(data.overallAchievement, theme) }}>
                  {formatNumber(data.overallAchievement)}%
                </strong>{' '}
                of target for {data.periodLabel}, measured across {data.scoredKpiCount} of{' '}
                {data.kpiCount} KPIs. Figures marked with an automatic icon are read from your work log
                and task board rather than entered by hand.
              </Typography>
            </Alert>
          )}

          <ScorecardView scorecard={data} />
        </>
      )}
    </Box>
  );
}
