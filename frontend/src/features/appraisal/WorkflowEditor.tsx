import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import ArrowDownwardOutlined from '@mui/icons-material/ArrowDownwardOutlined';
import ArrowUpwardOutlined from '@mui/icons-material/ArrowUpwardOutlined';
import { cycleApi, type WorkflowStepPayload } from '@/api/appraisal';
import { toErrorMessage } from '@/api/axiosClient';
import { FullPageLoader } from '@/components/LoadingState';
import type { WorkflowStepType } from '@/types/appraisal';

const STEP_LABELS: Record<WorkflowStepType, string> = {
  SELF_ASSESSMENT: 'Self assessment',
  MANAGER_REVIEW: 'Manager review',
  DEPARTMENT_HEAD_REVIEW: 'Department head review',
  HR_REVIEW: 'HR review',
};

const ALL_TYPES: WorkflowStepType[] = [
  'SELF_ASSESSMENT',
  'MANAGER_REVIEW',
  'DEPARTMENT_HEAD_REVIEW',
  'HR_REVIEW',
];

/**
 * The flow, edited as a list. The server refuses a shape that cannot work — a
 * flow with nothing active, self assessment on its own, an optional last step —
 * so the same reasons are spelled out here before anyone saves one.
 */
export function WorkflowEditor() {
  const queryClient = useQueryClient();
  const [steps, setSteps] = useState<WorkflowStepPayload[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const workflow = useQuery({ queryKey: ['cycles', 'workflow'], queryFn: cycleApi.workflow });

  useEffect(() => {
    if (workflow.data) {
      setSteps(
        workflow.data.map((step) => ({
          stepType: step.stepType,
          name: step.name,
          description: step.description,
          required: step.required,
          active: step.active,
          slaDays: step.slaDays,
        })),
      );
    }
  }, [workflow.data]);

  const save = useMutation({
    mutationFn: () => cycleApi.saveWorkflow(steps),
    onSuccess: () => {
      setError(null);
      setNotice('Workflow saved. Appraisals in flight follow the new order.');
      void queryClient.invalidateQueries({ queryKey: ['cycles'] });
      void queryClient.invalidateQueries({ queryKey: ['appraisals'] });
    },
    onError: (caught) => {
      setNotice(null);
      setError(toErrorMessage(caught));
    },
  });

  function move(index: number, delta: number) {
    const next = [...steps];
    const target = index + delta;
    if (target < 0 || target >= next.length) {
      return;
    }
    [next[index], next[target]] = [next[target], next[index]];
    setSteps(next);
  }

  function update(index: number, patch: Partial<WorkflowStepPayload>) {
    setSteps(steps.map((step, position) => (position === index ? { ...step, ...patch } : step)));
  }

  function add(type: WorkflowStepType) {
    setSteps([
      ...steps,
      { stepType: type, name: STEP_LABELS[type], description: null, required: true, active: true, slaDays: 7 },
    ]);
  }

  const missing = ALL_TYPES.filter((type) => !steps.some((step) => step.stepType === type));
  const active = steps.filter((step) => step.active);

  // The same three refusals the server applies, said before the round trip.
  const problems: string[] = [];
  if (active.length === 0) {
    problems.push('At least one step has to be active.');
  } else {
    if (active.length === 1 && active[0].stepType === 'SELF_ASSESSMENT') {
      problems.push('A flow of self assessment alone would let people rate themselves into a final rating.');
    }
    if (!active[active.length - 1].required) {
      problems.push('The last step signs the appraisal off, so it cannot be optional.');
    }
  }

  if (workflow.isLoading) {
    return <FullPageLoader label="Loading workflow" />;
  }

  return (
    <Box>
      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      {notice && (
        <Alert severity="success" sx={{ mb: 3 }} onClose={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {steps.map((step, index) => (
          <Box
            key={step.stepType}
            sx={{
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              gap: 2,
              p: 3,
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 2,
              opacity: step.active ? 1 : 0.55,
            }}
          >
            <Chip
              size="small"
              label={step.active ? `Step ${active.indexOf(step) + 1}` : 'Off'}
              color={step.active ? 'primary' : 'default'}
              variant="outlined"
              sx={{ minWidth: 68 }}
            />
            <TextField
              size="small"
              label="Name"
              value={step.name}
              onChange={(event) => update(index, { name: event.target.value })}
              inputProps={{ maxLength: 120 }}
              sx={{ minWidth: 200 }}
            />
            <TextField
              size="small"
              label="Stage"
              value={STEP_LABELS[step.stepType]}
              disabled
              sx={{ minWidth: 190 }}
            />
            <TextField
              size="small"
              label="SLA days"
              type="number"
              value={step.slaDays ?? ''}
              onChange={(event) =>
                update(index, { slaDays: event.target.value === '' ? null : Number(event.target.value) })
              }
              inputProps={{ min: 1 }}
              sx={{ width: 110 }}
            />
            <FormControlLabel
              control={
                <Switch
                  checked={step.required}
                  onChange={(event) => update(index, { required: event.target.checked })}
                />
              }
              label="Required"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={step.active}
                  onChange={(event) => update(index, { active: event.target.checked })}
                />
              }
              label="Active"
            />
            <Box sx={{ ml: 'auto', display: 'flex' }}>
              <Tooltip title="Move earlier">
                <span>
                  <IconButton size="small" disabled={index === 0} onClick={() => move(index, -1)}>
                    <ArrowUpwardOutlined fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
              <Tooltip title="Move later">
                <span>
                  <IconButton
                    size="small"
                    disabled={index === steps.length - 1}
                    onClick={() => move(index, 1)}
                  >
                    <ArrowDownwardOutlined fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
            </Box>
          </Box>
        ))}
      </Box>

      {problems.length > 0 && (
        <Alert severity="warning" sx={{ mt: 3 }}>
          {problems.map((problem) => (
            <Typography key={problem} variant="body2">
              {problem}
            </Typography>
          ))}
        </Alert>
      )}

      <Box sx={{ display: 'flex', gap: 2, mt: 4, justifyContent: 'space-between', flexWrap: 'wrap' }}>
        {missing.length > 0 ? (
          <TextField
            select
            size="small"
            label="Add a stage"
            value=""
            onChange={(event) => add(event.target.value as WorkflowStepType)}
            sx={{ minWidth: 220 }}
            SelectProps={{ displayEmpty: true }}
          >
            {missing.map((type) => (
              <MenuItem key={type} value={type}>
                <AddOutlined fontSize="small" sx={{ mr: 1 }} />
                {STEP_LABELS[type]}
              </MenuItem>
            ))}
          </TextField>
        ) : (
          <Box />
        )}
        <Button
          variant="contained"
          disabled={save.isPending || problems.length > 0}
          onClick={() => save.mutate()}
        >
          Save workflow
        </Button>
      </Box>
    </Box>
  );
}
