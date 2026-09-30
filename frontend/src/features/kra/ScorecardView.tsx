import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import LinearProgress from '@mui/material/LinearProgress';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AutoModeOutlined from '@mui/icons-material/AutoModeOutlined';
import { useTheme } from '@mui/material/styles';
import { EmptyState } from '@/components/EmptyState';
import type { KpiProgress, KraProgress, KraScorecard } from '@/types/kra';

interface Props {
  scorecard: KraScorecard;
  /** Rendered on each KPI row for managers who can set targets and actuals. */
  renderKpiActions?: (kpi: KpiProgress, kra: KraProgress) => React.ReactNode;
  emptyAction?: React.ReactNode;
}

/**
 * An employee's scorecard for one period.
 *
 * <p>Achievement is shown against the cap, not against 100, so beating a
 * target reads as beating it. A KPI that could not be scored says why instead
 * of showing a zero that looks like failure.</p>
 */
export function ScorecardView({ scorecard, renderKpiActions, emptyAction }: Props) {
  if (scorecard.kras.length === 0) {
    return (
      <EmptyState
        title={`No KRAs assigned for ${scorecard.periodLabel}`}
        description="Assign a scorecard template to start measuring this period."
        action={emptyAction}
      />
    );
  }

  return (
    <Box>
      {!scorecard.weightageValid && (
        <Alert severity="warning" sx={{ mb: 4 }}>
          KRA weightages total {formatNumber(scorecard.weightageTotal)}%, not 100%. The overall figure
          is still a weighted mean, but the scorecard is not balanced.
        </Alert>
      )}

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        {scorecard.kras.map((kra) => (
          <KraCard key={kra.id} kra={kra} renderKpiActions={renderKpiActions} />
        ))}
      </Box>
    </Box>
  );
}

function KraCard({
  kra,
  renderKpiActions,
}: {
  kra: KraProgress;
  renderKpiActions?: (kpi: KpiProgress, kra: KraProgress) => React.ReactNode;
}) {
  const theme = useTheme();
  const tone = achievementColour(kra.achievementPercent, theme);

  return (
    <Card>
      <CardContent sx={{ p: 0 }}>
        <Box
          sx={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            gap: 4,
            px: 5,
            py: 4,
            borderLeft: 3,
            borderColor: tone,
          }}
        >
          <Box sx={{ flex: 1, minWidth: 220 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2.5, mb: 0.5 }}>
              <Typography variant="h5">{kra.name}</Typography>
              <Chip size="small" label={`${formatNumber(kra.weightage)}% weight`} variant="outlined" />
            </Box>
            {kra.description && (
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {kra.description}
              </Typography>
            )}
          </Box>

          <Box sx={{ textAlign: 'right', minWidth: 150 }}>
            {kra.scored ? (
              <>
                <Typography
                  className="tabular"
                  sx={{ fontSize: 26, fontWeight: 700, lineHeight: 1.1, color: tone }}
                >
                  {formatNumber(kra.achievementPercent)}%
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {kra.scoredKpiCount} of {kra.totalKpiCount} KPIs measured
                </Typography>
              </>
            ) : (
              <>
                <Typography sx={{ fontSize: 20, fontWeight: 600, color: 'text.disabled' }}>—</Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  Nothing measured yet
                </Typography>
              </>
            )}
          </Box>
        </Box>

        <Divider />

        <Box sx={{ px: 5, py: 4, display: 'flex', flexDirection: 'column', gap: 3.5 }}>
          {kra.kpis.map((kpi) => (
            <KpiRow key={kpi.id} kpi={kpi} actions={renderKpiActions?.(kpi, kra)} />
          ))}
        </Box>
      </CardContent>
    </Card>
  );
}

function KpiRow({ kpi, actions }: { kpi: KpiProgress; actions?: React.ReactNode }) {
  const theme = useTheme();
  const tone = achievementColour(kpi.achievementPercent, theme);
  const progress = kpi.achievementPercent ?? 0;

  return (
    <Box
      sx={{
        display: 'grid',
        gap: 3,
        gridTemplateColumns: { xs: '1fr', md: '1.6fr 0.8fr 0.8fr 1.2fr auto' },
        alignItems: 'center',
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
          <Typography variant="subtitle2">{kpi.name}</Typography>
          {kpi.derived && (
            <Tooltip title="Measured automatically from work already recorded">
              <AutoModeOutlined sx={{ fontSize: 15, color: 'primary.main' }} />
            </Tooltip>
          )}
          <Chip
            size="small"
            variant="outlined"
            label={`${formatNumber(kpi.weightage)}%`}
            sx={{ height: 18, fontSize: 10 }}
          />
        </Box>
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
          {kpi.targetDirection === 'LOWER_IS_BETTER'
            ? 'Lower is better'
            : kpi.targetDirection === 'BOOLEAN'
              ? 'Met or not met'
              : 'Higher is better'}
          {kpi.actualRecordedBy ? ` · ${kpi.actualRecordedBy}` : ''}
        </Typography>
      </Box>

      <Box>
        <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block', fontSize: 10.5 }}>
          Target
        </Typography>
        <Typography variant="body2" className="tabular">
          {formatValue(kpi.targetValue, kpi)}
        </Typography>
      </Box>

      <Box>
        <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block', fontSize: 10.5 }}>
          Actual
        </Typography>
        <Typography variant="body2" className="tabular" sx={{ fontWeight: 600 }}>
          {formatValue(kpi.actualValue, kpi)}
        </Typography>
      </Box>

      <Box>
        {kpi.scored ? (
          <>
            <LinearProgress
              variant="determinate"
              value={Math.min((progress / Number(kpi.capPercent || 120)) * 100, 100)}
              sx={{
                mb: 0.5,
                '& .MuiLinearProgress-bar': { backgroundColor: tone },
              }}
            />
            <Typography variant="caption" className="tabular" sx={{ color: tone, fontWeight: 600 }}>
              {formatNumber(kpi.achievementPercent)}% achieved
            </Typography>
          </>
        ) : (
          <Typography variant="caption" sx={{ color: 'text.secondary', fontStyle: 'italic' }}>
            {kpi.excludedReason ?? 'Not measured'}
          </Typography>
        )}
      </Box>

      <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>{actions}</Box>
    </Box>
  );
}

export function achievementColour(
  achievement: number | null,
  theme: { palette: { success: { main: string }; warning: { main: string }; error: { main: string }; text: { disabled: string } } },
): string {
  if (achievement === null) {
    return theme.palette.text.disabled;
  }
  if (achievement >= 100) {
    return theme.palette.success.main;
  }
  if (achievement >= 80) {
    return theme.palette.warning.main;
  }
  return theme.palette.error.main;
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return Number.isInteger(Number(value)) ? String(Number(value)) : Number(value).toFixed(2);
}

export function formatValue(value: number | null, kpi: KpiProgress): string {
  if (value === null || value === undefined) {
    return '—';
  }
  const numeric = Number(value);
  switch (kpi.measurementType) {
    case 'CURRENCY':
      return new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 0,
      }).format(numeric);
    case 'PERCENTAGE':
      return `${formatNumber(numeric)}%`;
    case 'BOOLEAN':
      return numeric > 0 ? 'Met' : 'Not met';
    case 'HOURS':
      return `${formatNumber(numeric)} h`;
    default:
      return kpi.unit ? `${formatNumber(numeric)} ${kpi.unit}` : formatNumber(numeric);
  }
}
