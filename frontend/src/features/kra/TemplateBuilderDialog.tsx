import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import DeleteOutlineOutlined from '@mui/icons-material/DeleteOutlineOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { organisationApi } from '@/api/organisation';
import { kraTemplateApi, type KraTemplatePayload } from '@/api/kra';
import type { ActualSource, MeasurementType, PeriodType, TargetDirection } from '@/types/kra';

const MEASUREMENT_TYPES: MeasurementType[] = [
  'PERCENTAGE',
  'NUMERIC',
  'CURRENCY',
  'HOURS',
  'COUNT',
  'BOOLEAN',
  'RATING',
];
const DIRECTIONS: { value: TargetDirection; label: string }[] = [
  { value: 'HIGHER_IS_BETTER', label: 'Higher is better' },
  { value: 'LOWER_IS_BETTER', label: 'Lower is better' },
  { value: 'BOOLEAN', label: 'Met or not met' },
];
const SOURCES: { value: ActualSource; label: string }[] = [
  { value: 'MANUAL', label: 'Entered by the manager' },
  { value: 'WORK_LOG_PRODUCTIVE_HOURS', label: 'Productive hours from the work log' },
  { value: 'TASK_COMPLETION_RATE', label: 'Task completion rate' },
  { value: 'TASK_ON_TIME_RATE', label: 'On-time completion rate' },
];
const PERIODS: PeriodType[] = ['MONTHLY', 'QUARTERLY', 'ANNUAL'];

interface KpiDraft {
  id: number | null;
  name: string;
  measurementType: MeasurementType;
  targetDirection: TargetDirection;
  unit: string;
  defaultTargetValue: string;
  weightage: string;
  actualSource: ActualSource;
}

interface KraDraft {
  id: number | null;
  name: string;
  description: string;
  weightage: string;
  kpis: KpiDraft[];
}

function emptyKpi(): KpiDraft {
  return {
    id: null,
    name: '',
    measurementType: 'PERCENTAGE',
    targetDirection: 'HIGHER_IS_BETTER',
    unit: '%',
    defaultTargetValue: '',
    weightage: '100',
    actualSource: 'MANUAL',
  };
}

function emptyKra(): KraDraft {
  return { id: null, name: '', description: '', weightage: '', kpis: [emptyKpi()] };
}

/**
 * Builds a scorecard template with the weightage rule visible while you type.
 *
 * <p>The server enforces the same rule; showing the running totals here just
 * means nobody has to submit to find out they are at 90%.</p>
 */
export function TemplateBuilderDialog({
  open,
  templateId,
  onClose,
  onSaved,
}: {
  open: boolean;
  templateId: number | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const isEdit = templateId !== null;
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [designationId, setDesignationId] = useState<number | ''>('');
  const [departmentId, setDepartmentId] = useState<number | ''>('');
  const [periodType, setPeriodType] = useState<PeriodType>('QUARTERLY');
  const [kras, setKras] = useState<KraDraft[]>([emptyKra()]);
  const [error, setError] = useState<string | null>(null);

  const designations = useQuery({
    queryKey: ['designations', 'options'],
    queryFn: () => organisationApi.designationOptions(),
    enabled: open,
  });
  const departments = useQuery({
    queryKey: ['departments', 'options'],
    queryFn: organisationApi.departmentOptions,
    enabled: open,
  });
  const existing = useQuery({
    queryKey: ['kra-templates', templateId],
    queryFn: () => kraTemplateApi.get(templateId!),
    enabled: open && isEdit,
  });

  useEffect(() => {
    if (!open) {
      return;
    }
    setError(null);
    if (isEdit && existing.data) {
      const template = existing.data;
      setCode(template.code);
      setName(template.name);
      setDescription(template.description ?? '');
      setDesignationId(template.designationId ?? '');
      setDepartmentId(template.departmentId ?? '');
      setPeriodType(template.periodType);
      setKras(
        template.items.map((item) => ({
          id: item.id,
          name: item.name,
          description: item.description ?? '',
          weightage: String(item.weightage),
          kpis: item.kpis.map((kpi) => ({
            id: kpi.id,
            name: kpi.name,
            measurementType: kpi.measurementType,
            targetDirection: kpi.targetDirection,
            unit: kpi.unit ?? '',
            defaultTargetValue: kpi.defaultTargetValue === null ? '' : String(kpi.defaultTargetValue),
            weightage: String(kpi.weightage),
            actualSource: kpi.actualSource,
          })),
        })),
      );
    } else if (!isEdit) {
      setCode('');
      setName('');
      setDescription('');
      setDesignationId('');
      setDepartmentId('');
      setPeriodType('QUARTERLY');
      setKras([emptyKra()]);
    }
  }, [open, isEdit, existing.data]);

  const kraTotal = kras.reduce((sum, kra) => sum + toNumber(kra.weightage), 0);
  const kraBalanced = Math.abs(kraTotal - 100) < 0.01;
  const allKpisBalanced = kras.every(
    (kra) => Math.abs(kra.kpis.reduce((sum, kpi) => sum + toNumber(kpi.weightage), 0) - 100) < 0.01,
  );

  const save = useMutation({
    mutationFn: () => {
      const payload: KraTemplatePayload = {
        code: code.trim().toUpperCase(),
        name: name.trim(),
        description: description || null,
        designationId: designationId === '' ? null : Number(designationId),
        departmentId: departmentId === '' ? null : Number(departmentId),
        periodType,
        published: existing.data?.published ?? false,
        items: kras.map((kra, kraIndex) => ({
          id: kra.id,
          name: kra.name.trim(),
          description: kra.description || null,
          weightage: toNumber(kra.weightage),
          displayOrder: (kraIndex + 1) * 10,
          kpis: kra.kpis.map((kpi, kpiIndex) => ({
            id: kpi.id,
            name: kpi.name.trim(),
            measurementType: kpi.measurementType,
            targetDirection: kpi.targetDirection,
            unit: kpi.unit || null,
            defaultTargetValue: kpi.defaultTargetValue === '' ? null : toNumber(kpi.defaultTargetValue),
            weightage: toNumber(kpi.weightage),
            capPercent: 120,
            actualSource: kpi.actualSource,
            displayOrder: (kpiIndex + 1) * 10,
          })),
        })),
      };
      return isEdit ? kraTemplateApi.update(templateId!, payload) : kraTemplateApi.create(payload);
    },
    onSuccess: () => {
      onSaved();
      onClose();
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const namesFilled =
    code.trim() &&
    name.trim() &&
    kras.every((kra) => kra.name.trim() && kra.kpis.every((kpi) => kpi.name.trim()));

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>
        {isEdit ? 'Edit scorecard template' : 'New scorecard template'}
      </DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', md: '1fr 2fr 1fr' }, mb: 4 }}>
          <TextField
            label="Code"
            required
            size="small"
            value={code}
            onChange={(event) => setCode(event.target.value.toUpperCase())}
          />
          <TextField
            label="Template name"
            required
            size="small"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <TextField
            select
            label="Review cycle"
            size="small"
            value={periodType}
            onChange={(event) => setPeriodType(event.target.value as PeriodType)}
          >
            {PERIODS.map((period) => (
              <MenuItem key={period} value={period}>
                {period.charAt(0) + period.slice(1).toLowerCase()}
              </MenuItem>
            ))}
          </TextField>
        </Box>

        <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, mb: 4 }}>
          <TextField
            select
            label="Designation"
            size="small"
            value={designationId}
            onChange={(event) =>
              setDesignationId(event.target.value === '' ? '' : Number(event.target.value))
            }
            helperText="Templates for a designation are offered first when assigning"
          >
            <MenuItem value="">Any designation</MenuItem>
            {(designations.data ?? []).map((option) => (
              <MenuItem key={option.id} value={option.id}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Department"
            size="small"
            value={departmentId}
            onChange={(event) =>
              setDepartmentId(event.target.value === '' ? '' : Number(event.target.value))
            }
          >
            <MenuItem value="">All departments</MenuItem>
            {(departments.data ?? []).map((option) => (
              <MenuItem key={option.id} value={option.id}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
        </Box>

        <Alert severity={kraBalanced && allKpisBalanced ? 'success' : 'info'} sx={{ mb: 4 }}>
          KRA weightages total <strong>{kraTotal.toFixed(2)}%</strong>
          {kraBalanced ? ' — balanced.' : ' — must be exactly 100% to save.'}
          {!allKpisBalanced && ' Each KRA also needs its KPIs to total 100%.'}
        </Alert>

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          {kras.map((kra, kraIndex) => {
            const kpiTotal = kra.kpis.reduce((sum, kpi) => sum + toNumber(kpi.weightage), 0);
            const kpiBalanced = Math.abs(kpiTotal - 100) < 0.01;

            return (
              <Box
                key={kraIndex}
                sx={{
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 1.5,
                  p: 4,
                  borderLeft: 3,
                  borderLeftColor: kpiBalanced ? 'success.main' : 'warning.main',
                }}
              >
                <Box sx={{ display: 'flex', gap: 3, alignItems: 'flex-start', mb: 3 }}>
                  <TextField
                    label={`KRA ${kraIndex + 1}`}
                    required
                    size="small"
                    value={kra.name}
                    onChange={(event) => updateKra(kraIndex, { name: event.target.value })}
                    sx={{ flex: 1 }}
                  />
                  <TextField
                    label="Weight"
                    required
                    size="small"
                    type="number"
                    value={kra.weightage}
                    onChange={(event) => updateKra(kraIndex, { weightage: event.target.value })}
                    sx={{ width: 110 }}
                    InputProps={{ endAdornment: <Typography variant="caption">%</Typography> }}
                  />
                  <Tooltip title="Remove this KRA">
                    <span>
                      <IconButton
                        size="small"
                        disabled={kras.length === 1}
                        onClick={() => setKras(kras.filter((_, index) => index !== kraIndex))}
                      >
                        <DeleteOutlineOutlined sx={{ fontSize: 18 }} />
                      </IconButton>
                    </span>
                  </Tooltip>
                </Box>

                <TextField
                  label="Description"
                  size="small"
                  fullWidth
                  value={kra.description}
                  onChange={(event) => updateKra(kraIndex, { description: event.target.value })}
                  sx={{ mb: 3 }}
                />

                <Divider sx={{ mb: 3 }}>
                  <Chip
                    size="small"
                    color={kpiBalanced ? 'success' : 'warning'}
                    variant={kpiBalanced ? 'filled' : 'outlined'}
                    label={`KPIs · ${kpiTotal.toFixed(2)}%`}
                  />
                </Divider>

                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                  {kra.kpis.map((kpi, kpiIndex) => (
                    <Box
                      key={kpiIndex}
                      sx={{
                        display: 'grid',
                        gap: 2.5,
                        gridTemplateColumns: {
                          xs: '1fr',
                          lg: '2fr 1.2fr 1.4fr 0.9fr 0.9fr 1.6fr auto',
                        },
                        alignItems: 'start',
                      }}
                    >
                      <TextField
                        label="KPI"
                        required
                        size="small"
                        value={kpi.name}
                        onChange={(event) => updateKpi(kraIndex, kpiIndex, { name: event.target.value })}
                      />
                      <TextField
                        select
                        label="Measured as"
                        size="small"
                        value={kpi.measurementType}
                        onChange={(event) =>
                          updateKpi(kraIndex, kpiIndex, {
                            measurementType: event.target.value as MeasurementType,
                          })
                        }
                      >
                        {MEASUREMENT_TYPES.map((type) => (
                          <MenuItem key={type} value={type}>
                            {type.charAt(0) + type.slice(1).toLowerCase()}
                          </MenuItem>
                        ))}
                      </TextField>
                      <TextField
                        select
                        label="Direction"
                        size="small"
                        value={kpi.targetDirection}
                        onChange={(event) =>
                          updateKpi(kraIndex, kpiIndex, {
                            targetDirection: event.target.value as TargetDirection,
                          })
                        }
                      >
                        {DIRECTIONS.map((direction) => (
                          <MenuItem key={direction.value} value={direction.value}>
                            {direction.label}
                          </MenuItem>
                        ))}
                      </TextField>
                      <TextField
                        label="Target"
                        size="small"
                        type="number"
                        value={kpi.defaultTargetValue}
                        onChange={(event) =>
                          updateKpi(kraIndex, kpiIndex, { defaultTargetValue: event.target.value })
                        }
                      />
                      <TextField
                        label="Weight"
                        required
                        size="small"
                        type="number"
                        value={kpi.weightage}
                        onChange={(event) =>
                          updateKpi(kraIndex, kpiIndex, { weightage: event.target.value })
                        }
                      />
                      <TextField
                        select
                        label="Actual comes from"
                        size="small"
                        value={kpi.actualSource}
                        onChange={(event) =>
                          updateKpi(kraIndex, kpiIndex, {
                            actualSource: event.target.value as ActualSource,
                          })
                        }
                      >
                        {SOURCES.map((source) => (
                          <MenuItem key={source.value} value={source.value}>
                            {source.label}
                          </MenuItem>
                        ))}
                      </TextField>
                      <Tooltip title="Remove this KPI">
                        <span>
                          <IconButton
                            size="small"
                            disabled={kra.kpis.length === 1}
                            onClick={() =>
                              updateKra(kraIndex, {
                                kpis: kra.kpis.filter((_, index) => index !== kpiIndex),
                              })
                            }
                            sx={{ mt: 1 }}
                          >
                            <DeleteOutlineOutlined sx={{ fontSize: 17 }} />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </Box>
                  ))}
                </Box>

                <Button
                  size="small"
                  startIcon={<AddOutlined sx={{ fontSize: 16 }} />}
                  onClick={() => updateKra(kraIndex, { kpis: [...kra.kpis, emptyKpi()] })}
                  sx={{ mt: 3 }}
                >
                  Add KPI
                </Button>
              </Box>
            );
          })}
        </Box>

        <Button
          startIcon={<AddOutlined sx={{ fontSize: 18 }} />}
          onClick={() => setKras([...kras, emptyKra()])}
          sx={{ mt: 4 }}
        >
          Add KRA
        </Button>
      </DialogContent>

      <DialogActions sx={{ px: 6, pb: 5 }}>
        <Button variant="outlined" onClick={onClose}>
          Cancel
        </Button>
        <Button
          variant="contained"
          disabled={!namesFilled || !kraBalanced || !allKpisBalanced || save.isPending}
          onClick={() => save.mutate()}
        >
          {save.isPending ? 'Saving…' : isEdit ? 'Save template' : 'Create template'}
        </Button>
      </DialogActions>
    </Dialog>
  );

  function updateKra(index: number, patch: Partial<KraDraft>) {
    setKras((current) =>
      current.map((kra, kraIndex) => (kraIndex === index ? { ...kra, ...patch } : kra)),
    );
  }

  function updateKpi(kraIndex: number, kpiIndex: number, patch: Partial<KpiDraft>) {
    setKras((current) =>
      current.map((kra, index) =>
        index === kraIndex
          ? {
              ...kra,
              kpis: kra.kpis.map((kpi, position) =>
                position === kpiIndex ? { ...kpi, ...patch } : kpi,
              ),
            }
          : kra,
      ),
    );
  }
}

function toNumber(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}
