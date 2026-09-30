import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import CalculateOutlined from '@mui/icons-material/CalculateOutlined';
import LockOpenOutlined from '@mui/icons-material/LockOpenOutlined';
import LockOutlined from '@mui/icons-material/LockOutlined';
import TuneOutlined from '@mui/icons-material/TuneOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { employeeApi } from '@/api/employees';
import { performanceApi } from '@/api/performance';
import { EmptyState } from '@/components/EmptyState';
import { PageHeader } from '@/components/PageHeader';
import { StatTile } from '@/components/StatTile';
import { useAuth } from '@/contexts/AuthContext';
import type { PerformanceScope } from '@/types/performance';
import { ScoreBreakdown, formatScore } from './ScoreBreakdown';

const SCOPES: PerformanceScope[] = ['MONTHLY', 'QUARTERLY', 'ANNUAL'];

/**
 * The manager and HR console: run the engine, read the breakdown, correct a
 * score with a reason, and freeze it.
 */
export function TeamPerformancePage() {
  const queryClient = useQueryClient();
  const { can } = useAuth();
  const canAdjust = can('PERFORMANCE_ADJUST');
  const canFinalize = can('PERFORMANCE_FINALIZE');
  const canReopen = can('PERFORMANCE_REOPEN');

  const now = new Date();
  const [employeeId, setEmployeeId] = useState<number | ''>('');
  const [scope, setScope] = useState<PerformanceScope>('MONTHLY');
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [quarter, setQuarter] = useState(Math.floor(now.getMonth() / 3) + 1);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [adjustOpen, setAdjustOpen] = useState(false);
  const [newScore, setNewScore] = useState('');
  const [reason, setReason] = useState('');
  const [reopenOpen, setReopenOpen] = useState(false);

  const employees = useQuery({ queryKey: ['employees', 'options'], queryFn: employeeApi.options });

  useEffect(() => {
    if (employeeId === '' && (employees.data ?? []).length > 0) {
      setEmployeeId(employees.data![0].id);
    }
  }, [employees.data, employeeId]);

  const params = {
    year,
    month: scope === 'MONTHLY' ? month : undefined,
    quarter: scope === 'QUARTERLY' ? quarter : undefined,
  };

  const snapshot = useQuery({
    queryKey: ['performance', 'employee', employeeId, scope, params],
    queryFn: () => performanceApi.employeeSnapshot(Number(employeeId), scope, params),
    enabled: employeeId !== '',
    retry: false,
  });

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['performance'] });
  }

  const recalculate = useMutation({
    mutationFn: () =>
      performanceApi.recalculate({
        scope,
        year,
        periodIndex: scope === 'MONTHLY' ? month : scope === 'QUARTERLY' ? quarter : null,
        reason: 'Run from the performance console',
      }),
    onSuccess: (summary) => {
      refresh();
      setError(null);
      setNotice(
        `${summary.processed} scored, ${summary.skipped} skipped, ${summary.failed} failed for ${summary.periodLabel}.`
          + (summary.messages.length > 0 ? ` ${summary.messages[0]}` : ''),
      );
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const adjust = useMutation({
    mutationFn: () =>
      performanceApi.adjust(scope, snapshot.data!.id, Number(newScore), reason),
    onSuccess: () => {
      refresh();
      setAdjustOpen(false);
      setNewScore('');
      setReason('');
      setError(null);
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const finalize = useMutation({
    mutationFn: () => performanceApi.finalize(scope, snapshot.data!.id),
    onSuccess: () => {
      refresh();
      setError(null);
      setNotice('Score finalised. It can no longer be recomputed or adjusted.');
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const reopen = useMutation({
    mutationFn: () => performanceApi.reopen(scope, snapshot.data!.id, reason),
    onSuccess: () => {
      refresh();
      setReopenOpen(false);
      setReason('');
      setError(null);
      setNotice('Score reopened. The reason has been recorded in the audit trail.');
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const data = snapshot.data;

  return (
    <Box>
      <PageHeader
        eyebrow="Management"
        title="Performance console"
        description="Run the engine, read how a score was reached, correct it with a reason, and freeze it."
        actions={
          <Button
            variant="contained"
            startIcon={<CalculateOutlined sx={{ fontSize: 18 }} />}
            onClick={() => recalculate.mutate()}
            disabled={recalculate.isPending}
          >
            {recalculate.isPending ? 'Running…' : 'Run the engine'}
          </Button>
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      {notice && (
        <Alert severity="info" sx={{ mb: 4 }} onClose={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      <Card sx={{ mb: 4 }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3, p: 4 }}>
          <TextField
            select
            size="small"
            label="Employee"
            value={employeeId}
            onChange={(event) => setEmployeeId(Number(event.target.value))}
            sx={{ minWidth: 240 }}
          >
            {(employees.data ?? []).map((option) => (
              <MenuItem key={option.id} value={option.id}>
                {option.label}
                {option.code ? ` · ${option.code}` : ''}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            select
            size="small"
            label="Grain"
            value={scope}
            onChange={(event) => setScope(event.target.value as PerformanceScope)}
            sx={{ minWidth: 150 }}
          >
            {SCOPES.map((value) => (
              <MenuItem key={value} value={value}>
                {value.charAt(0) + value.slice(1).toLowerCase()}
              </MenuItem>
            ))}
          </TextField>

          {scope === 'MONTHLY' && (
            <TextField
              select
              size="small"
              label="Month"
              value={month}
              onChange={(event) => setMonth(Number(event.target.value))}
              sx={{ minWidth: 150 }}
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
              sx={{ minWidth: 130 }}
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
            {[now.getFullYear(), now.getFullYear() - 1].map((value) => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </TextField>
        </Box>
      </Card>

      {snapshot.isError && (
        <EmptyState
          title="No score for this period"
          description="Run the engine to compute it, or pick another period."
          action={
            <Button variant="contained" onClick={() => recalculate.mutate()}>
              Run the engine
            </Button>
          }
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
              label="Score"
              value={formatScore(data.overallScore)}
              caption={`${data.employeeName} · ${data.periodLabel}`}
              progress={Math.min(Number(data.overallScore ?? 0), 100)}
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
              label="Status"
              value={data.status.toLowerCase().replace(/_/g, ' ')}
              caption={data.frozen ? 'Frozen' : 'Open to recalculation'}
              icon={data.frozen ? LockOutlined : undefined}
            />
            <StatTile
              label="Adjustments"
              value={data.adjustments.length}
              caption={data.adjustments.length === 0 ? 'Score is as computed' : 'Manually corrected'}
              icon={TuneOutlined}
            />
            <StatTile
              label="Formula"
              value={`v${data.formulaVersion}`}
              caption={`Computed ${new Date(data.computedAt).toLocaleDateString('en-GB')}`}
            />
          </Box>

          <Box sx={{ display: 'flex', gap: 3, mb: 4, flexWrap: 'wrap' }}>
            {canAdjust && !data.frozen && (
              <Button
                variant="outlined"
                startIcon={<TuneOutlined sx={{ fontSize: 18 }} />}
                onClick={() => {
                  setNewScore(data.overallScore === null ? '' : String(data.overallScore));
                  setReason('');
                  setAdjustOpen(true);
                }}
              >
                Adjust score
              </Button>
            )}
            {canFinalize && !data.frozen && (
              <Button
                variant="contained"
                startIcon={<LockOutlined sx={{ fontSize: 18 }} />}
                onClick={() => finalize.mutate()}
                disabled={finalize.isPending || !data.scored}
              >
                {finalize.isPending ? 'Finalising…' : 'Finalise'}
              </Button>
            )}
            {canReopen && data.frozen && (
              <Button
                variant="outlined"
                color="warning"
                startIcon={<LockOpenOutlined sx={{ fontSize: 18 }} />}
                onClick={() => {
                  setReason('');
                  setReopenOpen(true);
                }}
              >
                Reopen
              </Button>
            )}
          </Box>

          <Card>
            <CardContent sx={{ p: 5 }}>
              <Typography variant="h5" sx={{ mb: 0.5 }}>
                How this score was reached
              </Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary', mb: 5 }}>
                The same breakdown the employee sees.
              </Typography>
              <ScoreBreakdown snapshot={data} />

              {data.adjustments.length > 0 && (
                <Box sx={{ mt: 5, pt: 4, borderTop: 1, borderColor: 'divider' }}>
                  <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 2 }}>
                    Manual adjustments
                  </Typography>
                  {data.adjustments.map((row) => (
                    <Box key={row.id} sx={{ display: 'flex', gap: 3, mb: 2, flexWrap: 'wrap' }}>
                      <Typography variant="caption" className="tabular" sx={{ color: 'text.secondary' }}>
                        {new Date(row.createdAt).toLocaleString('en-GB')}
                      </Typography>
                      <Typography variant="body2">
                        <strong>{row.adjustedByName}</strong> changed {formatScore(row.previousScore)}{' '}
                        to {formatScore(row.newScore)} — {row.reason}
                      </Typography>
                    </Box>
                  ))}
                </Box>
              )}
            </CardContent>
          </Card>
        </>
      )}

      <Dialog open={adjustOpen} onClose={() => setAdjustOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>Adjust the score</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 4 }}>
            The computed score stays on record. Your correction and reason are added to the audit
            trail and shown to the employee.
          </Alert>
          <TextField
            autoFocus
            fullWidth
            size="small"
            type="number"
            label="Corrected score"
            value={newScore}
            onChange={(event) => setNewScore(event.target.value)}
            sx={{ mb: 4 }}
          />
          <TextField
            fullWidth
            size="small"
            multiline
            minRows={3}
            required
            label="Reason for the change"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            helperText="Mandatory. This is what an auditor will read."
          />
        </DialogContent>
        <DialogActions sx={{ px: 6, pb: 5 }}>
          <Button variant="outlined" onClick={() => setAdjustOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={newScore === '' || !reason.trim() || adjust.isPending}
            onClick={() => adjust.mutate()}
          >
            {adjust.isPending ? 'Saving…' : 'Save adjustment'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={reopenOpen} onClose={() => setReopenOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>Reopen a finalised score</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 4 }}>
            Reopening a finalised appraisal is recorded against your name.
          </Alert>
          <TextField
            autoFocus
            fullWidth
            size="small"
            multiline
            minRows={3}
            required
            label="Reason for reopening"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </DialogContent>
        <DialogActions sx={{ px: 6, pb: 5 }}>
          <Button variant="outlined" onClick={() => setReopenOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            color="warning"
            disabled={!reason.trim() || reopen.isPending}
            onClick={() => reopen.mutate()}
          >
            {reopen.isPending ? 'Reopening…' : 'Reopen'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
