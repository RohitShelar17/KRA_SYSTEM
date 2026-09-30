import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import DeleteOutlineOutlined from '@mui/icons-material/DeleteOutlineOutlined';
import HistoryOutlined from '@mui/icons-material/HistoryOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { performanceSettingsApi, type FormulaPayload } from '@/api/performance';
import { PageHeader } from '@/components/PageHeader';
import type { PerformanceParameterCode, PerformanceScope } from '@/types/performance';

const SCOPES: PerformanceScope[] = ['DAILY', 'MONTHLY', 'QUARTERLY', 'ANNUAL'];

interface Row {
  parameterCode: PerformanceParameterCode | '';
  label: string;
  weightage: string;
}

/**
 * The formula as an editable object, with the 100% rule visible as you type.
 *
 * <p>Saving never edits the running version: it creates a new one and retires
 * the old, so scores already computed keep the formula they were computed
 * under. The version history below makes that visible.</p>
 */
export function FormulaSettingsPage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState(1);
  const scope = SCOPES[tab];

  const [rows, setRows] = useState<Row[]>([]);
  const [name, setName] = useState('');
  const [notes, setNotes] = useState('');
  const [minSubmission, setMinSubmission] = useState('0');
  const [penalty, setPenalty] = useState('0');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const active = useQuery({
    queryKey: ['formula', 'active', scope],
    queryFn: () => performanceSettingsApi.activeFormula(scope),
  });
  const available = useQuery({
    queryKey: ['formula', 'parameters', scope],
    queryFn: () => performanceSettingsApi.availableParameters(scope),
  });
  const history = useQuery({ queryKey: ['formula', 'all'], queryFn: performanceSettingsApi.formulas });

  useEffect(() => {
    if (!active.data) {
      return;
    }
    setRows(
      active.data.parameters.map((parameter) => ({
        parameterCode: parameter.parameterCode,
        label: parameter.label,
        weightage: String(parameter.weightage),
      })),
    );
    setName(`${scope.charAt(0)}${scope.slice(1).toLowerCase()} performance v${active.data.version + 1}`);
    setNotes('');
    setMinSubmission(String(active.data.minSubmissionRatePct ?? 0));
    setPenalty(String(active.data.submissionPenaltyPct ?? 0));
    setError(null);
    setSaved(false);
  }, [active.data, scope]);

  const total = rows.reduce((sum, row) => sum + (Number(row.weightage) || 0), 0);
  const balanced = Math.abs(total - 100) < 0.01;
  const complete = rows.length > 0 && rows.every((row) => row.parameterCode !== '');

  const save = useMutation({
    mutationFn: () => {
      const payload: FormulaPayload = {
        scopeType: scope,
        name: name.trim(),
        notes: notes || null,
        minSubmissionRatePct: Number(minSubmission) || 0,
        submissionPenaltyPct: Number(penalty) || 0,
        parameters: rows.map((row) => ({
          parameterCode: row.parameterCode as PerformanceParameterCode,
          label: row.label,
          weightage: Number(row.weightage),
        })),
      };
      return performanceSettingsApi.saveFormula(payload);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['formula'] });
      setSaved(true);
      setError(null);
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const used = new Set(rows.map((row) => row.parameterCode));
  const versions = (history.data ?? []).filter((config) => config.scopeType === scope);

  return (
    <Box>
      <PageHeader
        eyebrow="Administration"
        title="Scoring formula"
        description="What counts towards a score and how much. Weights must total 100%, and saving creates a new version rather than changing existing scores."
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      {saved && (
        <Alert severity="success" sx={{ mb: 4 }} onClose={() => setSaved(false)}>
          New version activated. Scores already computed keep the version they were computed under;
          run the engine to apply this one.
        </Alert>
      )}

      <Card sx={{ mb: 4 }}>
        <Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ px: 3 }}>
          {SCOPES.map((value) => (
            <Tab key={value} label={value.charAt(0) + value.slice(1).toLowerCase()} />
          ))}
        </Tabs>
      </Card>

      <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', lg: '3fr 2fr' } }}>
        <Card>
          <CardContent sx={{ p: 5 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 4 }}>
              <Box>
                <Typography variant="h5">
                  {scope.charAt(0) + scope.slice(1).toLowerCase()} formula
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  Currently running version {active.data?.version ?? '—'}.
                </Typography>
              </Box>
              <Chip
                color={balanced ? 'success' : 'warning'}
                variant={balanced ? 'filled' : 'outlined'}
                label={`${total.toFixed(2)}%`}
              />
            </Box>

            <Alert severity={balanced ? 'success' : 'info'} sx={{ mb: 4 }}>
              Weights total <strong>{total.toFixed(2)}%</strong>
              {balanced ? ' — balanced.' : ' — must be exactly 100% to save.'}
            </Alert>

            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mb: 4 }}>
              {rows.map((row, index) => (
                <Box
                  key={index}
                  sx={{
                    display: 'grid',
                    gap: 2.5,
                    gridTemplateColumns: { xs: '1fr', md: '2fr 1.6fr 0.8fr auto' },
                    alignItems: 'start',
                  }}
                >
                  <TextField
                    select
                    size="small"
                    label="Parameter"
                    value={row.parameterCode}
                    onChange={(event) =>
                      update(index, {
                        parameterCode: event.target.value as PerformanceParameterCode,
                        label:
                          (available.data ?? []).find((p) => p.parameterCode === event.target.value)
                            ?.label ?? row.label,
                      })
                    }
                  >
                    {(available.data ?? [])
                      .filter((p) => p.parameterCode === row.parameterCode || !used.has(p.parameterCode))
                      .map((parameter) => (
                        <MenuItem key={parameter.parameterCode} value={parameter.parameterCode}>
                          {humanise(parameter.parameterCode)}
                        </MenuItem>
                      ))}
                  </TextField>

                  <TextField
                    size="small"
                    label="Shown to employees as"
                    value={row.label}
                    onChange={(event) => update(index, { label: event.target.value })}
                  />

                  <TextField
                    size="small"
                    type="number"
                    label="Weight"
                    value={row.weightage}
                    onChange={(event) => update(index, { weightage: event.target.value })}
                    InputProps={{ endAdornment: <Typography variant="caption">%</Typography> }}
                  />

                  <Tooltip title="Remove">
                    <IconButton
                      size="small"
                      onClick={() => setRows(rows.filter((_, position) => position !== index))}
                      sx={{ mt: 1 }}
                    >
                      <DeleteOutlineOutlined sx={{ fontSize: 18 }} />
                    </IconButton>
                  </Tooltip>
                </Box>
              ))}
            </Box>

            <Button
              size="small"
              startIcon={<AddOutlined sx={{ fontSize: 17 }} />}
              disabled={(available.data ?? []).every((p) => used.has(p.parameterCode))}
              onClick={() => setRows([...rows, { parameterCode: '', label: '', weightage: '' }])}
            >
              Add parameter
            </Button>

            <Divider sx={{ my: 5 }}>
              <Typography variant="overline" sx={{ color: 'text.secondary' }}>
                Consistency penalty
              </Typography>
            </Divider>

            <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>
              Someone who files too few work logs has not produced enough evidence for the rest of
              the figures to stand on. Set the threshold to zero to switch this off.
            </Typography>

            <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr', mb: 4 }}>
              <TextField
                size="small"
                type="number"
                label="Minimum submission rate"
                value={minSubmission}
                onChange={(event) => setMinSubmission(event.target.value)}
                InputProps={{ endAdornment: <Typography variant="caption">%</Typography> }}
              />
              <TextField
                size="small"
                type="number"
                label="Penalty if below"
                value={penalty}
                onChange={(event) => setPenalty(event.target.value)}
                InputProps={{ endAdornment: <Typography variant="caption">pts</Typography> }}
              />
            </Box>

            <TextField
              fullWidth
              size="small"
              label="Version name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              sx={{ mb: 3 }}
            />
            <TextField
              fullWidth
              size="small"
              multiline
              minRows={2}
              label="Why this changed"
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              helperText="Recorded in the audit trail alongside the old and new weights"
              sx={{ mb: 4 }}
            />

            <Button
              variant="contained"
              disabled={!balanced || !complete || !name.trim() || save.isPending}
              onClick={() => save.mutate()}
            >
              {save.isPending ? 'Saving…' : 'Save as a new version'}
            </Button>
          </CardContent>
        </Card>

        <Card sx={{ alignSelf: 'start' }}>
          <CardContent sx={{ p: 5 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1 }}>
              <HistoryOutlined sx={{ fontSize: 19, color: 'text.secondary' }} />
              <Typography variant="h5">Version history</Typography>
            </Box>
            <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
              Every score records the version it was computed under.
            </Typography>

            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              {versions.map((config) => (
                <Box
                  key={config.id}
                  sx={{
                    p: 3,
                    borderRadius: 1.5,
                    border: 1,
                    borderColor: config.active ? 'primary.main' : 'divider',
                    bgcolor: config.active ? 'action.hover' : undefined,
                  }}
                >
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1 }}>
                    <Typography variant="subtitle2">v{config.version}</Typography>
                    {config.active && <Chip size="small" color="primary" label="running" />}
                    <Typography variant="caption" sx={{ color: 'text.secondary', ml: 'auto' }}>
                      from {config.effectiveFrom}
                    </Typography>
                  </Box>
                  <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                    {config.parameters
                      .map((p) => `${humanise(p.parameterCode)} ${p.weightage}%`)
                      .join(' · ')}
                  </Typography>
                  {config.notes && (
                    <Typography variant="caption" sx={{ color: 'text.disabled', fontStyle: 'italic' }}>
                      {config.notes}
                    </Typography>
                  )}
                </Box>
              ))}
            </Box>
          </CardContent>
        </Card>
      </Box>
    </Box>
  );

  function update(index: number, patch: Partial<Row>) {
    setRows((current) =>
      current.map((row, position) => (position === index ? { ...row, ...patch } : row)),
    );
  }
}

function humanise(code: string): string {
  return code
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}
