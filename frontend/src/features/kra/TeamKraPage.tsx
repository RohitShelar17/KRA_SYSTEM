import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import EditOutlined from '@mui/icons-material/EditOutlined';
import FlagOutlined from '@mui/icons-material/FlagOutlined';
import PlaylistAddCheckOutlined from '@mui/icons-material/PlaylistAddCheckOutlined';
import IconButton from '@mui/material/IconButton';
import { toErrorMessage } from '@/api/axiosClient';
import { employeeApi } from '@/api/employees';
import { kraApi, kraTemplateApi } from '@/api/kra';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatTile } from '@/components/StatTile';
import { useAuth } from '@/contexts/AuthContext';
import type { KpiProgress, KraProgress } from '@/types/kra';
import { PeriodPicker, currentPeriod, type PeriodSelection } from './PeriodPicker';
import { ScorecardView, formatNumber } from './ScorecardView';

/**
 * The manager's view of a report's scorecard: assign a template, adjust
 * weightages, set targets and record actuals.
 */
export function TeamKraPage() {
  const queryClient = useQueryClient();
  const { can } = useAuth();
  const canAssign = can('KRA_ASSIGN');
  const canManageKpis = can('KPI_MANAGE');

  const [employeeId, setEmployeeId] = useState<number | ''>('');
  const [period, setPeriod] = useState<PeriodSelection>(currentPeriod());
  const [error, setError] = useState<string | null>(null);
  const [assignOpen, setAssignOpen] = useState(false);
  const [valueDialog, setValueDialog] = useState<{
    kpi: KpiProgress;
    kra: KraProgress;
    mode: 'target' | 'actual';
  } | null>(null);
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');

  const employees = useQuery({ queryKey: ['employees', 'options'], queryFn: employeeApi.options });

  useEffect(() => {
    if (employeeId === '' && (employees.data ?? []).length > 0) {
      setEmployeeId(employees.data![0].id);
    }
  }, [employees.data, employeeId]);

  const scorecard = useQuery({
    queryKey: ['kra', 'employee', employeeId, period],
    queryFn: () =>
      kraApi.employeeScorecard(Number(employeeId), {
        periodType: period.periodType,
        year: period.year,
        periodIndex: period.periodIndex,
      }),
    enabled: employeeId !== '',
  });

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['kra'] });
  }

  const saveValue = useMutation({
    mutationFn: () => {
      const { kpi, kra, mode } = valueDialog!;
      const value = Number(amount);
      return mode === 'target'
        ? kraApi.setTarget(kpi.id, {
            periodType: kra.periodType,
            periodStart: kra.periodStart,
            periodEnd: kra.periodEnd,
            targetValue: value,
            notes: note || null,
          })
        : kraApi.recordActual(kpi.id, {
            periodStart: kra.periodStart,
            periodEnd: kra.periodEnd,
            actualValue: value,
            note: note || null,
          });
    },
    onSuccess: () => {
      refresh();
      closeValueDialog();
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  function openValueDialog(kpi: KpiProgress, kra: KraProgress, mode: 'target' | 'actual') {
    setValueDialog({ kpi, kra, mode });
    setAmount(
      mode === 'target'
        ? kpi.targetValue !== null
          ? String(kpi.targetValue)
          : ''
        : kpi.actualValue !== null
          ? String(kpi.actualValue)
          : '',
    );
    setNote('');
    setError(null);
  }

  function closeValueDialog() {
    setValueDialog(null);
    setAmount('');
    setNote('');
  }

  const data = scorecard.data;

  return (
    <Box>
      <PageHeader
        eyebrow="Management"
        title="Team scorecards"
        description="Assign what each person is measured on, set the targets, and record what actually happened."
        actions={
          canAssign && employeeId !== '' ? (
            <Button
              variant="contained"
              startIcon={<FlagOutlined sx={{ fontSize: 18 }} />}
              onClick={() => {
                setError(null);
                setAssignOpen(true);
              }}
            >
              Assign template
            </Button>
          ) : undefined
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Card sx={{ mb: 4 }}>
        <Box sx={{ p: 4, display: 'flex', flexWrap: 'wrap', gap: 3, alignItems: 'center' }}>
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
          <PeriodPicker value={period} onChange={setPeriod} />
        </Box>
      </Card>

      {scorecard.isLoading && <FullPageLoader label="Loading scorecard" />}

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
              caption={data.employeeName}
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
              trailing={
                data.weightageValid ? (
                  <Chip size="small" color="success" label="balanced" />
                ) : (
                  <Chip size="small" color="warning" label="not 100%" />
                )
              }
            />
            <StatTile
              label="KPIs measured"
              value={data.scoredKpiCount}
              unit={`/ ${data.kpiCount}`}
              caption="Targets and actuals in place"
              progress={data.kpiCount ? (data.scoredKpiCount / data.kpiCount) * 100 : 0}
            />
            <StatTile label="Period" value={data.periodLabel} caption={data.designation ?? '—'} />
          </Box>

          <ScorecardView
            scorecard={data}
            emptyAction={
              canAssign ? (
                <Button variant="contained" onClick={() => setAssignOpen(true)}>
                  Assign a template
                </Button>
              ) : undefined
            }
            renderKpiActions={
              canManageKpis
                ? (kpi, kra) => (
                    <>
                      <Tooltip title="Set target">
                        <IconButton size="small" onClick={() => openValueDialog(kpi, kra, 'target')}>
                          <FlagOutlined sx={{ fontSize: 16 }} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip
                        title={
                          kpi.derived
                            ? 'Measured automatically from recorded work'
                            : 'Record actual'
                        }
                      >
                        <span>
                          <IconButton
                            size="small"
                            disabled={kpi.derived}
                            onClick={() => openValueDialog(kpi, kra, 'actual')}
                          >
                            <PlaylistAddCheckOutlined sx={{ fontSize: 17 }} />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </>
                  )
                : undefined
            }
          />

          {canAssign && data.kras.length > 0 && (
            <WeightageEditor
              employeeId={Number(employeeId)}
              kras={data.kras}
              onSaved={refresh}
              onError={setError}
            />
          )}
        </>
      )}

      <AssignDialog
        open={assignOpen}
        employeeId={employeeId === '' ? null : Number(employeeId)}
        period={period}
        onClose={() => setAssignOpen(false)}
        onAssigned={refresh}
        onError={setError}
      />

      <Dialog open={Boolean(valueDialog)} onClose={closeValueDialog} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>
          {valueDialog?.mode === 'target' ? 'Set target' : 'Record actual'}
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
            {valueDialog?.kpi.name} · {valueDialog?.kra.periodLabel}
            {valueDialog?.kpi.unit ? ` · measured in ${valueDialog.kpi.unit}` : ''}
          </Typography>
          <TextField
            autoFocus
            fullWidth
            size="small"
            type="number"
            label={valueDialog?.mode === 'target' ? 'Target value' : 'Actual value'}
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            helperText={
              valueDialog?.kpi.targetDirection === 'LOWER_IS_BETTER'
                ? 'Lower is better for this KPI'
                : valueDialog?.kpi.targetDirection === 'BOOLEAN'
                  ? 'Enter 1 for met, 0 for not met'
                  : 'Higher is better for this KPI'
            }
            sx={{ mb: 4 }}
          />
          <TextField
            fullWidth
            size="small"
            multiline
            minRows={2}
            label="Note (optional)"
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
        </DialogContent>
        <DialogActions sx={{ px: 6, pb: 5 }}>
          <Button variant="outlined" onClick={closeValueDialog}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={amount === '' || saveValue.isPending}
            onClick={() => saveValue.mutate()}
          >
            {saveValue.isPending ? 'Saving…' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function AssignDialog({
  open,
  employeeId,
  period,
  onClose,
  onAssigned,
  onError,
}: {
  open: boolean;
  employeeId: number | null;
  period: PeriodSelection;
  onClose: () => void;
  onAssigned: () => void;
  onError: (message: string) => void;
}) {
  const [templateId, setTemplateId] = useState<number | ''>('');
  const [seedTargets, setSeedTargets] = useState(true);
  const [replaceExisting, setReplaceExisting] = useState(false);

  const templates = useQuery({
    queryKey: ['kra-templates', 'assignable'],
    queryFn: () => kraTemplateApi.assignable(),
    enabled: open,
  });

  const assign = useMutation({
    mutationFn: () =>
      kraApi.assign(employeeId!, {
        templateId: Number(templateId),
        periodType: period.periodType,
        periodYear: period.year,
        periodIndex: period.periodIndex,
        seedTargets,
        replaceExisting,
      }),
    onSuccess: () => {
      onAssigned();
      onClose();
      setTemplateId('');
    },
    onError: (caught) => onError(toErrorMessage(caught)),
  });

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>Assign a scorecard template</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
          The template is copied onto this employee for the selected period. Later edits to the
          template will not change what they are measured on.
        </Typography>

        <TextField
          select
          fullWidth
          size="small"
          label="Template"
          value={templateId}
          onChange={(event) => setTemplateId(Number(event.target.value))}
          helperText="Only published templates can be assigned"
          sx={{ mb: 4 }}
        >
          {(templates.data ?? []).map((option) => (
            <MenuItem key={option.id} value={option.id}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>

        <Alert severity="info" sx={{ mb: 4 }}>
          Assigning for <strong>{periodLabel(period)}</strong>.
        </Alert>

        <FormControlLabel
          control={
            <Switch checked={seedTargets} onChange={(event) => setSeedTargets(event.target.checked)} />
          }
          label={
            <Box>
              <Typography variant="subtitle2">Copy the template default targets</Typography>
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                Saves entering each target by hand; you can change them afterwards.
              </Typography>
            </Box>
          }
          sx={{ alignItems: 'flex-start', mb: 3 }}
        />
        <FormControlLabel
          control={
            <Switch
              checked={replaceExisting}
              onChange={(event) => setReplaceExisting(event.target.checked)}
            />
          }
          label={
            <Box>
              <Typography variant="subtitle2">Replace an existing assignment</Typography>
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                The old KRAs are closed, not deleted, so their history stays.
              </Typography>
            </Box>
          }
          sx={{ alignItems: 'flex-start' }}
        />
      </DialogContent>
      <DialogActions sx={{ px: 6, pb: 5 }}>
        <Button variant="outlined" onClick={onClose}>
          Cancel
        </Button>
        <Button
          variant="contained"
          disabled={templateId === '' || assign.isPending}
          onClick={() => assign.mutate()}
        >
          {assign.isPending ? 'Assigning…' : 'Assign'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function WeightageEditor({
  employeeId,
  kras,
  onSaved,
  onError,
}: {
  employeeId: number;
  kras: KraProgress[];
  onSaved: () => void;
  onError: (message: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [values, setValues] = useState<Record<number, string>>({});
  const [reason, setReason] = useState('');

  const total = kras.reduce((sum, kra) => sum + Number(values[kra.id] ?? kra.weightage), 0);
  const balanced = Math.abs(total - 100) < 0.01;

  const save = useMutation({
    mutationFn: () =>
      kraApi.updateWeightages(
        employeeId,
        kras.map((kra) => ({ id: kra.id, weightage: Number(values[kra.id] ?? kra.weightage) })),
        reason || undefined,
      ),
    onSuccess: () => {
      onSaved();
      setOpen(false);
      setValues({});
      setReason('');
    },
    onError: (caught) => onError(toErrorMessage(caught)),
  });

  return (
    <>
      <Box sx={{ mt: 4, display: 'flex', justifyContent: 'flex-end' }}>
        <Button
          size="small"
          variant="outlined"
          startIcon={<EditOutlined sx={{ fontSize: 16 }} />}
          onClick={() => setOpen(true)}
        >
          Adjust weightages
        </Button>
      </Box>

      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>Adjust KRA weightages</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
            Weightages are saved as a set and must total 100%. The change is audited with your reason.
          </Typography>

          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mb: 4 }}>
            {kras.map((kra) => (
              <Box key={kra.id} sx={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                <Typography variant="body2" sx={{ flex: 1 }}>
                  {kra.name}
                </Typography>
                <TextField
                  size="small"
                  type="number"
                  value={values[kra.id] ?? String(kra.weightage)}
                  onChange={(event) => setValues({ ...values, [kra.id]: event.target.value })}
                  sx={{ width: 110 }}
                  InputProps={{ endAdornment: <Typography variant="caption">%</Typography> }}
                />
              </Box>
            ))}
          </Box>

          <Alert severity={balanced ? 'success' : 'warning'} sx={{ mb: 4 }}>
            Total: <strong>{total.toFixed(2)}%</strong>
            {balanced ? ' — balanced' : ' — must be exactly 100% to save'}
          </Alert>

          <TextField
            fullWidth
            size="small"
            multiline
            minRows={2}
            label="Reason for the change"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </DialogContent>
        <DialogActions sx={{ px: 6, pb: 5 }}>
          <Button variant="outlined" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={!balanced || save.isPending}
            onClick={() => save.mutate()}
          >
            {save.isPending ? 'Saving…' : 'Save weightages'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

function periodLabel(period: PeriodSelection): string {
  if (period.periodType === 'ANNUAL') {
    return String(period.year);
  }
  if (period.periodType === 'QUARTERLY') {
    return `Q${period.periodIndex} ${period.year}`;
  }
  return `${new Date(period.year, period.periodIndex - 1, 1).toLocaleString('en-GB', {
    month: 'long',
  })} ${period.year}`;
}
