import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import LockOutlined from '@mui/icons-material/LockOutlined';
import TrendingDownOutlined from '@mui/icons-material/TrendingDownOutlined';
import TrendingUpOutlined from '@mui/icons-material/TrendingUpOutlined';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useTheme } from '@mui/material/styles';
import { performanceApi } from '@/api/performance';
import { EmptyState } from '@/components/EmptyState';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatTile } from '@/components/StatTile';
import type { PerformanceScope } from '@/types/performance';
import { ScoreBreakdown, formatScore } from './ScoreBreakdown';

const SCOPES: { value: Exclude<PerformanceScope, 'DAILY'>; label: string }[] = [
  { value: 'MONTHLY', label: 'Monthly' },
  { value: 'QUARTERLY', label: 'Quarterly' },
  { value: 'ANNUAL', label: 'Annual' },
];

export function MyPerformancePage() {
  const theme = useTheme();
  const now = new Date();
  const [tab, setTab] = useState(0);
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [quarter, setQuarter] = useState(Math.floor(now.getMonth() / 3) + 1);

  const scope = SCOPES[tab].value;

  const snapshot = useQuery({
    queryKey: ['performance', 'my', scope, year, month, quarter],
    queryFn: () =>
      performanceApi.mySnapshot(scope, {
        year,
        month: scope === 'MONTHLY' ? month : undefined,
        quarter: scope === 'QUARTERLY' ? quarter : undefined,
      }),
    retry: false,
  });

  const trend = useQuery({
    queryKey: ['performance', 'my', 'trend', scope, year],
    queryFn: () => performanceApi.myTrend(scope, year),
    retry: false,
  });

  const data = snapshot.data;
  const change = trend.data?.changeFromPrevious ?? null;

  return (
    <Box>
      <PageHeader
        eyebrow="Performance & KRA"
        title="My performance"
        description="Your score for each period, and exactly how it was reached."
      />

      <Card sx={{ mb: 4 }}>
        <Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ px: 3 }}>
          {SCOPES.map((entry) => (
            <Tab key={entry.value} label={entry.label} />
          ))}
        </Tabs>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3, p: 4, pt: 3 }}>
          {scope === 'MONTHLY' && (
            <TextField
              select
              size="small"
              label="Month"
              value={month}
              onChange={(event) => setMonth(Number(event.target.value))}
              sx={{ minWidth: 160 }}
            >
              {Array.from({ length: 12 }, (_, index) => index + 1).map((value) => (
                <MenuItem key={value} value={value}>
                  {new Date(2000, value - 1, 1).toLocaleString('en-GB', { month: 'long' })}
                </MenuItem>
              ))}
            </TextField>
          )}
          {scope === 'QUARTERLY' && (
            <TextField
              select
              size="small"
              label="Quarter"
              value={quarter}
              onChange={(event) => setQuarter(Number(event.target.value))}
              sx={{ minWidth: 140 }}
            >
              {[1, 2, 3, 4].map((value) => (
                <MenuItem key={value} value={value}>
                  Q{value}
                </MenuItem>
              ))}
            </TextField>
          )}
          <TextField
            select
            size="small"
            label="Year"
            value={year}
            onChange={(event) => setYear(Number(event.target.value))}
            sx={{ minWidth: 120 }}
          >
            {[now.getFullYear(), now.getFullYear() - 1, now.getFullYear() - 2].map((value) => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </TextField>
        </Box>
      </Card>

      {snapshot.isLoading && <FullPageLoader label="Loading your score" />}

      {snapshot.isError && (
        <EmptyState
          title="No score for this period yet"
          description="Scores are computed after the period closes, or when an administrator runs the engine."
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
              label={`${SCOPES[tab].label} score`}
              value={formatScore(data.overallScore)}
              caption={data.periodLabel}
              progress={Math.min(Number(data.overallScore ?? 0), 100)}
              progressColor={
                Number(data.overallScore ?? 0) >= 90
                  ? 'success'
                  : Number(data.overallScore ?? 0) >= 70
                    ? 'primary'
                    : 'warning'
              }
              trailing={
                data.ratingBandName ? (
                  <Chip
                    size="small"
                    label={data.ratingBandName}
                    sx={{
                      bgcolor: data.ratingBandColour ?? undefined,
                      color: data.ratingBandColour ? '#fff' : undefined,
                    }}
                  />
                ) : undefined
              }
            />
            <StatTile
              label="Change"
              value={change === null ? '—' : `${change > 0 ? '+' : ''}${formatScore(change)}`}
              caption={
                trend.data?.previousScore
                  ? `Previous period ${formatScore(trend.data.previousScore)}`
                  : 'No previous period to compare'
              }
              icon={change !== null && change < 0 ? TrendingDownOutlined : TrendingUpOutlined}
              progressColor={change !== null && change < 0 ? 'error' : 'success'}
            />
            <StatTile
              label="Status"
              value={data.status.toLowerCase().replace(/_/g, ' ')}
              caption={
                data.frozen
                  ? `Finalised${data.finalizedByName ? ` by ${data.finalizedByName}` : ''}`
                  : 'Still open to recalculation'
              }
              icon={data.frozen ? LockOutlined : undefined}
            />
            <StatTile
              label="Formula"
              value={`v${data.formulaVersion}`}
              caption="The version this score was computed under"
            />
          </Box>

          {data.frozen && (
            <Alert severity="success" icon={<LockOutlined sx={{ fontSize: 19 }} />} sx={{ mb: 4 }}>
              This score is finalised and can no longer change.
              {data.finalizedAt
                ? ` Frozen on ${new Date(data.finalizedAt).toLocaleDateString('en-GB')}.`
                : ''}
            </Alert>
          )}

          {data.adjustments.length > 0 && (
            <Alert severity="info" sx={{ mb: 4 }}>
              This score was manually adjusted. {data.adjustments[0].adjustedByName} changed it from{' '}
              {formatScore(data.adjustments[0].previousScore)} to{' '}
              {formatScore(data.adjustments[0].newScore)}: {data.adjustments[0].reason}
            </Alert>
          )}

          <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', lg: '3fr 2fr' } }}>
            <Card>
              <CardContent sx={{ p: 5 }}>
                <Typography variant="h5" sx={{ mb: 0.5 }}>
                  How this score was reached
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary', mb: 5 }}>
                  Every parameter in the formula, what it measured and what it contributed.
                </Typography>
                <ScoreBreakdown snapshot={data} />
              </CardContent>
            </Card>

            <Card>
              <CardContent sx={{ p: 5 }}>
                <Typography variant="h5" sx={{ mb: 0.5 }}>
                  Trend
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
                  {SCOPES[tab].label.toLowerCase()} scores across {year}.
                </Typography>

                {(trend.data?.points ?? []).length === 0 ? (
                  <Typography variant="body2" sx={{ color: 'text.secondary', py: 8, textAlign: 'center' }}>
                    No history yet.
                  </Typography>
                ) : (
                  <Box sx={{ height: 260 }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart
                        data={(trend.data?.points ?? []).map((point) => ({
                          label: point.label,
                          score: point.score === null ? 0 : Number(point.score),
                          colour: point.bandColour ?? theme.palette.primary.main,
                        }))}
                        margin={{ top: 4, right: 8, bottom: 4, left: -22 }}
                      >
                        <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} vertical={false} />
                        <XAxis
                          dataKey="label"
                          tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                          tickLine={false}
                          axisLine={{ stroke: theme.palette.divider }}
                        />
                        <YAxis
                          domain={[0, 120]}
                          tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                          tickLine={false}
                          axisLine={false}
                        />
                        <ReferenceLine
                          y={100}
                          stroke={theme.palette.success.main}
                          strokeDasharray="4 4"
                          label={{ value: 'target', fontSize: 10, fill: theme.palette.success.main }}
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
                        <Bar dataKey="score" radius={[4, 4, 0, 0]} maxBarSize={40}>
                          {(trend.data?.points ?? []).map((point, index) => (
                            <Cell
                              key={index}
                              fill={point.bandColour ?? theme.palette.primary.main}
                            />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </Box>
                )}

                <Box sx={{ mt: 4, display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {Object.entries(data.figures).map(([key, value]) => (
                    <Box key={key} sx={{ display: 'flex', justifyContent: 'space-between', gap: 3 }}>
                      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                        {humanise(key)}
                      </Typography>
                      <Typography variant="body2" className="tabular" sx={{ fontWeight: 500 }}>
                        {formatFigure(key, value)}
                      </Typography>
                    </Box>
                  ))}
                </Box>
              </CardContent>
            </Card>
          </Box>
        </>
      )}
    </Box>
  );
}

function humanise(key: string): string {
  return key
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, (character) => character.toUpperCase())
    .replace(' Pct', ' %')
    .trim();
}

function formatFigure(key: string, value: number | string): string {
  if (value === '' || value === null) {
    return '—';
  }
  if (key.toLowerCase().includes('minutes')) {
    const minutes = Number(value);
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
  }
  return String(value);
}
