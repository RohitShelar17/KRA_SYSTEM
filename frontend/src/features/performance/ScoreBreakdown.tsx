import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import LinearProgress from '@mui/material/LinearProgress';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import InfoOutlined from '@mui/icons-material/InfoOutlined';
import type { PerformanceSnapshot } from '@/types/performance';

/**
 * Why the score is what it is.
 *
 * <p>Each parameter shows its raw value, the weight it was given, and what it
 * actually contributed. Parameters that could not be measured are listed too,
 * with the reason and the note that their weight moved elsewhere — otherwise a
 * redistributed score looks like arithmetic nobody can check.</p>
 */
export function ScoreBreakdown({ snapshot }: { snapshot: PerformanceSnapshot }) {
  const measured = snapshot.contributions.filter((row) => row.measured);
  const skipped = snapshot.contributions.filter((row) => !row.measured);
  const redistributed = skipped.length > 0 && measured.length > 0;

  return (
    <Box>
      {redistributed && (
        <Alert severity="info" icon={<InfoOutlined sx={{ fontSize: 19 }} />} sx={{ mb: 4 }}>
          {skipped.length === 1 ? 'One parameter' : `${skipped.length} parameters`} could not be
          measured this period. Their weight was shared across the rest rather than counted as zero,
          so the figures below add up to the score.
        </Alert>
      )}

      {snapshot.penaltyApplied !== null && (
        <Alert severity="warning" sx={{ mb: 4 }}>
          A consistency penalty of {snapshot.penaltyApplied} points was applied because too few work
          logs were filed for the period.
        </Alert>
      )}

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
        {measured.map((row) => (
          <Box
            key={row.parameterCode}
            sx={{
              display: 'grid',
              gap: 3,
              gridTemplateColumns: { xs: '1fr', md: '1.6fr 0.7fr 0.9fr 1.4fr 0.7fr' },
              alignItems: 'center',
            }}
          >
            <Box>
              <Typography variant="subtitle2">{row.label}</Typography>
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {row.parameterCode.toLowerCase().replace(/_/g, ' ')}
              </Typography>
            </Box>

            <Box>
              <Label>Measured</Label>
              <Typography variant="body2" className="tabular" sx={{ fontWeight: 600 }}>
                {formatScore(row.rawValue)}
              </Typography>
            </Box>

            <Box>
              <Label>Weight</Label>
              <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1 }}>
                <Typography variant="body2" className="tabular">
                  {formatScore(row.weightage)}%
                </Typography>
                {Math.abs(Number(row.effectiveWeightage) - Number(row.weightage)) > 0.01 && (
                  <Tooltip title="Scaled up because another parameter could not be measured">
                    <Chip
                      size="small"
                      variant="outlined"
                      label={`→ ${formatScore(row.effectiveWeightage)}%`}
                      sx={{ height: 18, fontSize: 10 }}
                    />
                  </Tooltip>
                )}
              </Box>
            </Box>

            <Box>
              <LinearProgress
                variant="determinate"
                value={Math.min(Number(row.contribution) * 2, 100)}
                sx={{ mb: 0.5 }}
              />
            </Box>

            <Box sx={{ textAlign: 'right' }}>
              <Label>Adds</Label>
              <Typography variant="body2" className="tabular" sx={{ fontWeight: 600 }}>
                {formatScore(row.contribution)}
              </Typography>
            </Box>
          </Box>
        ))}
      </Box>

      {skipped.length > 0 && (
        <Box sx={{ mt: 5 }}>
          <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 2 }}>
            Not measured this period
          </Typography>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {skipped.map((row) => (
              <Box
                key={row.parameterCode}
                sx={{ display: 'flex', alignItems: 'baseline', gap: 3, flexWrap: 'wrap' }}
              >
                <Typography variant="body2" sx={{ minWidth: 170, color: 'text.secondary' }}>
                  {row.label}
                </Typography>
                <Chip
                  size="small"
                  variant="outlined"
                  label={`${formatScore(row.weightage)}% redistributed`}
                  sx={{ height: 19, fontSize: 10.5 }}
                />
                <Typography variant="caption" sx={{ color: 'text.disabled', fontStyle: 'italic' }}>
                  {row.skippedReason}
                </Typography>
              </Box>
            ))}
          </Box>
        </Box>
      )}

      <Box
        sx={{
          mt: 5,
          pt: 4,
          borderTop: 1,
          borderColor: 'divider',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'baseline',
        }}
      >
        <Typography variant="subtitle2">Total</Typography>
        <Typography variant="h5" className="tabular">
          {formatScore(snapshot.overallScore)}
        </Typography>
      </Box>

      <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block', mt: 2 }}>
        Computed {new Date(snapshot.computedAt).toLocaleString('en-GB')} using formula version{' '}
        {snapshot.formulaVersion}. Changing the formula later does not change this score.
      </Typography>
    </Box>
  );
}

function Label({ children }: { children: React.ReactNode }) {
  return (
    <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block', fontSize: 10.5 }}>
      {children}
    </Typography>
  );
}

export function formatScore(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  const numeric = Number(value);
  return Number.isInteger(numeric) ? String(numeric) : numeric.toFixed(2);
}
