import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import LinearProgress from '@mui/material/LinearProgress';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import LockOutlined from '@mui/icons-material/LockOutlined';
import PlayArrowOutlined from '@mui/icons-material/PlayArrowOutlined';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import { cycleApi, type CyclePayload } from '@/api/appraisal';
import { toErrorMessage } from '@/api/axiosClient';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { EmptyState } from '@/components/EmptyState';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { TableSkeleton } from '@/components/LoadingState';
import type { AppraisalCycle } from '@/types/appraisal';
import { WorkflowEditor } from './WorkflowEditor';

type Scope = 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';

interface FormState {
  name: string;
  description: string;
  scopeType: Scope;
  periodYear: number;
  periodIndex: number;
  selfAssessmentOpens: string;
  selfAssessmentCloses: string;
  reviewOpens: string;
  reviewCloses: string;
}

function blankForm(): FormState {
  const now = new Date();
  return {
    name: '',
    description: '',
    scopeType: 'QUARTERLY',
    periodYear: now.getFullYear(),
    periodIndex: Math.floor(now.getMonth() / 3) + 1,
    selfAssessmentOpens: '',
    selfAssessmentCloses: '',
    reviewOpens: '',
    reviewCloses: '',
  };
}

/**
 * HR's control of the appraisal calendar: when a cycle runs, when each window
 * is open, and the review flow every appraisal in it will walk.
 */
export function AppraisalCyclesPage() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FormState>(blankForm());
  const [editing, setEditing] = useState<AppraisalCycle | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [confirm, setConfirm] = useState<{ cycle: AppraisalCycle; action: 'open' | 'close' } | null>(
    null,
  );
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const cycles = useQuery({ queryKey: ['cycles', 'list'], queryFn: cycleApi.list });

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['cycles'] });
    void queryClient.invalidateQueries({ queryKey: ['appraisals'] });
  }

  const payload: CyclePayload = {
    name: form.name.trim(),
    description: form.description.trim() || null,
    scopeType: form.scopeType,
    periodYear: form.periodYear,
    periodIndex: form.scopeType === 'ANNUAL' ? 1 : form.periodIndex,
    selfAssessmentOpens: form.selfAssessmentOpens || null,
    selfAssessmentCloses: form.selfAssessmentCloses || null,
    reviewOpens: form.reviewOpens || null,
    reviewCloses: form.reviewCloses || null,
  };

  const save = useMutation({
    mutationFn: () => (editing ? cycleApi.update(editing.id, payload) : cycleApi.create(payload)),
    onSuccess: () => {
      setDialogOpen(false);
      setError(null);
      refresh();
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const launch = useMutation({
    mutationFn: (cycle: AppraisalCycle) => cycleApi.open(cycle.id),
    onSuccess: (result) => {
      setConfirm(null);
      setError(null);
      setNotice(
        `${result.cycleName} is open. ${result.participantsCreated} appraisal${result.participantsCreated === 1 ? '' : 's'} created` +
          (result.alreadyPresent > 0 ? `, ${result.alreadyPresent} already existed` : '') +
          '.' +
          (result.messages.length > 0 ? ` ${result.messages.join(' ')}` : ''),
      );
      refresh();
    },
    onError: (caught) => {
      setConfirm(null);
      setError(toErrorMessage(caught));
    },
  });

  const close = useMutation({
    mutationFn: (cycle: AppraisalCycle) => cycleApi.close(cycle.id),
    onSuccess: (cycle) => {
      setConfirm(null);
      setError(null);
      setNotice(`${cycle.name} is closed. Nothing further can be submitted against it.`);
      refresh();
    },
    onError: (caught) => {
      setConfirm(null);
      setError(toErrorMessage(caught));
    },
  });

  function openCreate() {
    setEditing(null);
    setForm(blankForm());
    setError(null);
    setDialogOpen(true);
  }

  function openEdit(cycle: AppraisalCycle) {
    setEditing(cycle);
    setForm({
      name: cycle.name,
      description: cycle.description ?? '',
      scopeType: cycle.scopeType,
      periodYear: cycle.periodYear,
      periodIndex: cycle.periodIndex,
      selfAssessmentOpens: cycle.selfAssessmentOpens ?? '',
      selfAssessmentCloses: cycle.selfAssessmentCloses ?? '',
      reviewOpens: cycle.reviewOpens ?? '',
      reviewCloses: cycle.reviewCloses ?? '',
    });
    setError(null);
    setDialogOpen(true);
  }

  const rows = cycles.data ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Administration"
        title="Appraisal cycles"
        description="Open a review period, and every eligible employee gets an appraisal on the configured workflow."
        actions={
          <Button variant="contained" startIcon={<AddOutlined />} onClick={openCreate}>
            New cycle
          </Button>
        }
      />

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

      {cycles.isLoading && <TableSkeleton rows={4} columns={6} />}

      {!cycles.isLoading && rows.length === 0 && (
        <EmptyState
          title="No cycles yet"
          description="Create one for the period you want reviewed, then open it to create everyone's appraisal."
          action={
            <Button variant="contained" onClick={openCreate}>
              New cycle
            </Button>
          }
        />
      )}

      {rows.length > 0 && (
        <Card sx={{ mb: 4 }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Cycle</TableCell>
                <TableCell>Period</TableCell>
                <TableCell>Windows</TableCell>
                <TableCell>Status</TableCell>
                <TableCell sx={{ minWidth: 180 }}>Progress</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((cycle) => (
                <TableRow key={cycle.id} hover>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {cycle.name}
                    </Typography>
                    {cycle.description && (
                      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                        {cycle.description}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{cycle.periodLabel}</Typography>
                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                      {cycle.periodStart} → {cycle.periodEnd}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Window
                      label="Self assessment"
                      from={cycle.selfAssessmentOpens}
                      to={cycle.selfAssessmentCloses}
                      open={cycle.selfAssessmentOpen}
                    />
                    <Window
                      label="Review"
                      from={cycle.reviewOpens}
                      to={cycle.reviewCloses}
                      open={cycle.reviewOpen}
                    />
                  </TableCell>
                  <TableCell>
                    <StatusChip status={cycle.status} />
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                      {cycle.completedCount} of {cycle.participantCount} complete
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={Math.min(100, cycle.completionPercent)}
                      sx={{ mt: 0.5, height: 6, borderRadius: 3 }}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit">
                      <span>
                        <IconButton
                          size="small"
                          disabled={cycle.status === 'CLOSED'}
                          onClick={() => openEdit(cycle)}
                        >
                          <EditOutlined fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title="Open the cycle and create appraisals">
                      <span>
                        <IconButton
                          size="small"
                          color="primary"
                          disabled={cycle.status !== 'DRAFT'}
                          onClick={() => setConfirm({ cycle, action: 'open' })}
                        >
                          <PlayArrowOutlined fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title="Close the cycle">
                      <span>
                        <IconButton
                          size="small"
                          disabled={cycle.status === 'DRAFT' || cycle.status === 'CLOSED'}
                          onClick={() => setConfirm({ cycle, action: 'close' })}
                        >
                          <LockOutlined fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      <Card>
        <CardContent sx={{ p: 5 }}>
          <Typography variant="h5" sx={{ mb: 0.5 }}>
            Review workflow
          </Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
            The stages every appraisal walks, in order. Changing this changes appraisals already in
            flight, so a step you remove disappears from the flow rather than blocking it.
          </Typography>
          <WorkflowEditor />
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{editing ? `Edit ${editing.name}` : 'New appraisal cycle'}</DialogTitle>
        <DialogContent dividers>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, pt: 1 }}>
            <TextField
              label="Name"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              inputProps={{ maxLength: 150 }}
              required
              fullWidth
            />
            <TextField
              label="Description"
              value={form.description}
              onChange={(event) => setForm({ ...form, description: event.target.value })}
              inputProps={{ maxLength: 500 }}
              multiline
              minRows={2}
              fullWidth
            />
            <Box sx={{ display: 'flex', gap: 3 }}>
              <TextField
                select
                label="Period type"
                value={form.scopeType}
                onChange={(event) => setForm({ ...form, scopeType: event.target.value as Scope })}
                disabled={Boolean(editing)}
                sx={{ flex: 1 }}
              >
                <MenuItem value="MONTHLY">Monthly</MenuItem>
                <MenuItem value="QUARTERLY">Quarterly</MenuItem>
                <MenuItem value="ANNUAL">Annual</MenuItem>
              </TextField>
              <TextField
                select
                label="Year"
                value={form.periodYear}
                onChange={(event) => setForm({ ...form, periodYear: Number(event.target.value) })}
                disabled={Boolean(editing)}
                sx={{ width: 130 }}
              >
                {[0, -1, 1].map((offset) => new Date().getFullYear() + offset).sort().map((year) => (
                  <MenuItem key={year} value={year}>
                    {year}
                  </MenuItem>
                ))}
              </TextField>
              {form.scopeType !== 'ANNUAL' && (
                <TextField
                  select
                  label={form.scopeType === 'MONTHLY' ? 'Month' : 'Quarter'}
                  value={form.periodIndex}
                  onChange={(event) => setForm({ ...form, periodIndex: Number(event.target.value) })}
                  disabled={Boolean(editing)}
                  sx={{ width: 150 }}
                >
                  {(form.scopeType === 'MONTHLY'
                    ? Array.from({ length: 12 }, (_, index) => index + 1)
                    : [1, 2, 3, 4]
                  ).map((value) => (
                    <MenuItem key={value} value={value}>
                      {form.scopeType === 'MONTHLY'
                        ? new Date(2000, value - 1, 1).toLocaleString('en-GB', { month: 'long' })
                        : `Q${value}`}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            </Box>

            <Typography variant="subtitle2" sx={{ mt: 1 }}>
              Self assessment window
            </Typography>
            <Box sx={{ display: 'flex', gap: 3 }}>
              <DateField
                label="Opens"
                value={form.selfAssessmentOpens}
                onChange={(value) => setForm({ ...form, selfAssessmentOpens: value })}
              />
              <DateField
                label="Closes"
                value={form.selfAssessmentCloses}
                onChange={(value) => setForm({ ...form, selfAssessmentCloses: value })}
              />
            </Box>

            <Typography variant="subtitle2" sx={{ mt: 1 }}>
              Review window
            </Typography>
            <Box sx={{ display: 'flex', gap: 3 }}>
              <DateField
                label="Opens"
                value={form.reviewOpens}
                onChange={(value) => setForm({ ...form, reviewOpens: value })}
              />
              <DateField
                label="Closes"
                value={form.reviewCloses}
                onChange={(value) => setForm({ ...form, reviewCloses: value })}
              />
            </Box>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              Leave a window blank to keep it open for as long as the cycle is.
            </Typography>

            {error && <Alert severity="error">{error}</Alert>}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={save.isPending || form.name.trim().length === 0}
            onClick={() => save.mutate()}
          >
            {editing ? 'Save changes' : 'Create cycle'}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirm !== null}
        title={confirm?.action === 'open' ? 'Open this cycle?' : 'Close this cycle?'}
        message={
          confirm?.action === 'open'
            ? `Every eligible employee gets an appraisal on the configured workflow, and ${confirm.cycle.name} starts accepting self assessments.`
            : `Nothing further can be submitted against ${confirm?.cycle.name ?? 'this cycle'}. Appraisals still in flight stay where they are.`
        }
        confirmLabel={confirm?.action === 'open' ? 'Open cycle' : 'Close cycle'}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          if (!confirm) {
            return;
          }
          if (confirm.action === 'open') {
            launch.mutate(confirm.cycle);
          } else {
            close.mutate(confirm.cycle);
          }
        }}
      />
    </Box>
  );
}

function DateField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <TextField
      label={label}
      type="date"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      InputLabelProps={{ shrink: true }}
      sx={{ flex: 1 }}
    />
  );
}

function Window({
  label,
  from,
  to,
  open,
}: {
  label: string;
  from: string | null;
  to: string | null;
  open: boolean;
}) {
  return (
    <Typography variant="caption" sx={{ display: 'block', color: open ? 'success.main' : 'text.secondary' }}>
      {label}: {from ?? 'any time'} → {to ?? 'cycle end'}
      {open ? ' · open' : ''}
    </Typography>
  );
}
