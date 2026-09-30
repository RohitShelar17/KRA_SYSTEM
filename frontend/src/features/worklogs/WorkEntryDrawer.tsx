import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import CloseOutlined from '@mui/icons-material/CloseOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { kraApi } from '@/api/kra';
import { catalogueApi, taskApi, workLogApi, type WorkLogEntryPayload } from '@/api/work';
import { formatDuration, minutesOfDay, toClock } from '@/utils/time';
import type { Priority, TaskStatus, WorkLogEntry } from '@/types/work';

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const STATUSES: TaskStatus[] = ['IN_PROGRESS', 'COMPLETED', 'ON_HOLD', 'BLOCKED', 'NOT_STARTED'];

interface Props {
  open: boolean;
  workDate: string;
  entry: WorkLogEntry | null;
  /** Pre-filled times when the drawer is opened from a gap on the ribbon. */
  presetStart?: string | null;
  presetEnd?: string | null;
  onClose: () => void;
}

interface FormState {
  startTime: string;
  endTime: string;
  taskCategoryId: number | '';
  projectId: number | '';
  taskId: number | '';
  employeeKraId: number | '';
  title: string;
  description: string;
  priority: Priority;
  status: TaskStatus;
  completionPct: number;
  billable: boolean;
  remarks: string;
}

const EMPTY: FormState = {
  startTime: '',
  endTime: '',
  taskCategoryId: '',
  projectId: '',
  taskId: '',
  employeeKraId: '',
  title: '',
  description: '',
  priority: 'MEDIUM',
  status: 'IN_PROGRESS',
  completionPct: 0,
  billable: false,
  remarks: '',
};

export function WorkEntryDrawer({ open, workDate, entry, presetStart, presetEnd, onClose }: Props) {
  const queryClient = useQueryClient();
  const isEdit = Boolean(entry);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [error, setError] = useState<string | null>(null);

  const categories = useQuery({
    queryKey: ['task-categories', 'available'],
    queryFn: catalogueApi.availableCategories,
    enabled: open,
    staleTime: 5 * 60 * 1000,
  });
  const templates = useQuery({
    queryKey: ['task-templates', 'available'],
    queryFn: catalogueApi.availableTemplates,
    enabled: open,
    staleTime: 5 * 60 * 1000,
  });
  const projects = useQuery({
    queryKey: ['projects', 'options'],
    queryFn: catalogueApi.projectOptions,
    enabled: open,
    staleTime: 5 * 60 * 1000,
  });
  const openTasks = useQuery({ queryKey: ['tasks', 'open'], queryFn: taskApi.open, enabled: open });
  const activeKras = useQuery({
    queryKey: ['kra', 'active', workDate],
    queryFn: () => kraApi.activeOptions(workDate),
    enabled: open,
  });

  useEffect(() => {
    if (!open) {
      return;
    }
    setError(null);
    if (entry) {
      setForm({
        startTime: toClock(entry.startTime),
        endTime: toClock(entry.endTime),
        taskCategoryId: entry.taskCategoryId,
        projectId: entry.projectId ?? '',
        taskId: entry.taskId ?? '',
        employeeKraId: entry.employeeKraId ?? '',
        title: entry.title,
        description: entry.description ?? '',
        priority: entry.priority,
        status: entry.status,
        completionPct: entry.completionPct,
        billable: entry.billable,
        remarks: entry.remarks ?? '',
      });
    } else {
      setForm({
        ...EMPTY,
        startTime: presetStart ? toClock(presetStart) : '',
        endTime: presetEnd ? toClock(presetEnd) : '',
      });
    }
  }, [open, entry, presetStart, presetEnd]);

  const duration = useMemo(() => {
    if (!form.startTime || !form.endTime) {
      return null;
    }
    const minutes = minutesOfDay(form.endTime) - minutesOfDay(form.startTime);
    return minutes > 0 ? minutes : null;
  }, [form.startTime, form.endTime]);

  const selectedCategory = (categories.data ?? []).find((c) => c.id === form.taskCategoryId);

  const mutation = useMutation({
    mutationFn: (payload: WorkLogEntryPayload) =>
      entry ? workLogApi.updateEntry(entry.id, payload) : workLogApi.addEntry(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['worklog'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
      onClose();
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  function submit() {
    setError(null);
    mutation.mutate({
      workDate,
      startTime: `${form.startTime}:00`,
      endTime: `${form.endTime}:00`,
      taskCategoryId: Number(form.taskCategoryId),
      projectId: form.projectId === '' ? null : Number(form.projectId),
      taskId: form.taskId === '' ? null : Number(form.taskId),
      employeeKraId: form.employeeKraId === '' ? null : Number(form.employeeKraId),
      title: form.title.trim(),
      description: form.description || null,
      priority: form.priority,
      status: form.status,
      completionPct: form.completionPct,
      billable: form.billable,
      remarks: form.remarks || null,
    });
  }

  const canSubmit = Boolean(form.startTime && form.endTime && form.taskCategoryId && form.title.trim())
    && duration !== null;

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{ sx: { width: { xs: '100%', sm: 460 } } }}
    >
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <Box
          sx={{
            px: 6,
            py: 4,
            borderBottom: 1,
            borderColor: 'divider',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Box>
            <Typography variant="h5">{isEdit ? 'Edit work entry' : 'Add work entry'}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {workDate}
            </Typography>
          </Box>
          <IconButton onClick={onClose} aria-label="Close">
            <CloseOutlined sx={{ fontSize: 19 }} />
          </IconButton>
        </Box>

        <Box sx={{ flex: 1, overflowY: 'auto', px: 6, py: 5 }}>
          {error && (
            <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
              {error}
            </Alert>
          )}

          {templates.data && templates.data.length > 0 && !isEdit && (
            <Box sx={{ mb: 5 }}>
              <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 1.5 }}>
                Quick fill from your department
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5 }}>
                {templates.data.slice(0, 8).map((template) => (
                  <Chip
                    key={template.id}
                    label={template.name}
                    size="small"
                    variant="outlined"
                    onClick={() =>
                      setForm((current) => ({
                        ...current,
                        title: template.name,
                        taskCategoryId: template.taskCategoryId,
                        priority: template.defaultPriority,
                      }))
                    }
                    sx={{ cursor: 'pointer', height: 24 }}
                  />
                ))}
              </Box>
            </Box>
          )}

          <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr' }}>
            <TextField
              label="Start time"
              type="time"
              required
              size="small"
              value={form.startTime}
              onChange={(event) => setForm({ ...form, startTime: event.target.value })}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              label="End time"
              type="time"
              required
              size="small"
              value={form.endTime}
              onChange={(event) => setForm({ ...form, endTime: event.target.value })}
              InputLabelProps={{ shrink: true }}
            />
          </Box>

          <Box
            sx={{
              mt: 3,
              mb: 5,
              px: 4,
              py: 2.5,
              borderRadius: 1.5,
              bgcolor: 'action.hover',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              Calculated duration
            </Typography>
            <Typography variant="subtitle2" className="tabular">
              {duration === null ? '—' : formatDuration(duration)}
            </Typography>
          </Box>

          <TextField
            label="What did you work on?"
            required
            fullWidth
            size="small"
            value={form.title}
            onChange={(event) => setForm({ ...form, title: event.target.value })}
            sx={{ mb: 4 }}
          />

          <TextField
            select
            label="Activity category"
            required
            fullWidth
            size="small"
            value={form.taskCategoryId}
            onChange={(event) => setForm({ ...form, taskCategoryId: Number(event.target.value) })}
            helperText={
              selectedCategory
                ? selectedCategory.productive
                  ? 'Counts as productive time'
                  : 'Does not count as productive time'
                : 'Decides how this time is classified'
            }
            sx={{ mb: 4 }}
          >
            {(categories.data ?? []).map((category) => (
              <MenuItem key={category.id} value={category.id}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Box
                    sx={{
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      bgcolor: category.colourHex ?? 'primary.main',
                    }}
                  />
                  {category.name}
                </Box>
              </MenuItem>
            ))}
          </TextField>

          <Autocomplete
            size="small"
            options={openTasks.data ?? []}
            getOptionLabel={(option) => option.title}
            value={(openTasks.data ?? []).find((task) => task.id === form.taskId) ?? null}
            onChange={(_, value) =>
              setForm({
                ...form,
                taskId: value?.id ?? '',
                title: form.title || value?.title || '',
                projectId: value?.projectId ?? form.projectId,
              })
            }
            renderInput={(params) => (
              <TextField
                {...params}
                label="Link to a task"
                helperText="Optional. Linking keeps the task's actual hours accurate."
              />
            )}
            sx={{ mb: 4 }}
          />

          <TextField
            select
            label="Project"
            fullWidth
            size="small"
            value={form.projectId}
            onChange={(event) =>
              setForm({
                ...form,
                projectId: event.target.value === '' ? '' : Number(event.target.value),
              })
            }
            sx={{ mb: 4 }}
          >
            <MenuItem value="">No project</MenuItem>
            {(projects.data ?? []).map((project) => (
              <MenuItem key={project.id} value={project.id}>
                {project.label}
              </MenuItem>
            ))}
          </TextField>

          {(activeKras.data ?? []).length > 0 && (
            <TextField
              select
              label="Counts towards"
              fullWidth
              size="small"
              value={form.employeeKraId}
              onChange={(event) =>
                setForm({
                  ...form,
                  employeeKraId: event.target.value === '' ? '' : Number(event.target.value),
                })
              }
              helperText="Optional. Attributing time to a KRA is how it reaches your scorecard."
              sx={{ mb: 4 }}
            >
              <MenuItem value="">No result area</MenuItem>
              {(activeKras.data ?? []).map((option) => (
                <MenuItem key={option.id} value={option.id}>
                  {option.label}
                  {option.code ? ` · ${option.code}` : ''}
                </MenuItem>
              ))}
            </TextField>
          )}

          <Divider sx={{ my: 4 }}>
            <Typography variant="overline" sx={{ color: 'text.secondary' }}>
              Detail
            </Typography>
          </Divider>

          <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr', mb: 4 }}>
            <TextField
              select
              label="Priority"
              size="small"
              value={form.priority}
              onChange={(event) => setForm({ ...form, priority: event.target.value as Priority })}
            >
              {PRIORITIES.map((priority) => (
                <MenuItem key={priority} value={priority}>
                  {humanise(priority)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Work status"
              size="small"
              value={form.status}
              onChange={(event) => {
                const status = event.target.value as TaskStatus;
                setForm({
                  ...form,
                  status,
                  completionPct: status === 'COMPLETED' ? 100 : form.completionPct,
                });
              }}
            >
              {STATUSES.map((status) => (
                <MenuItem key={status} value={status}>
                  {humanise(status)}
                </MenuItem>
              ))}
            </TextField>
          </Box>

          <TextField
            label="Completion %"
            type="number"
            size="small"
            fullWidth
            value={form.completionPct}
            onChange={(event) =>
              setForm({
                ...form,
                completionPct: Math.max(0, Math.min(100, Number(event.target.value))),
              })
            }
            sx={{ mb: 4 }}
          />

          <TextField
            label="Description"
            fullWidth
            multiline
            minRows={3}
            size="small"
            value={form.description}
            onChange={(event) => setForm({ ...form, description: event.target.value })}
            sx={{ mb: 4 }}
          />

          <FormControlLabel
            control={
              <Switch
                checked={form.billable}
                onChange={(event) => setForm({ ...form, billable: event.target.checked })}
              />
            }
            label={<Typography variant="body2">Billable to a client</Typography>}
          />
        </Box>

        <Box
          sx={{
            px: 6,
            py: 4,
            borderTop: 1,
            borderColor: 'divider',
            display: 'flex',
            justifyContent: 'flex-end',
            gap: 3,
          }}
        >
          <Button variant="outlined" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button variant="contained" onClick={submit} disabled={!canSubmit || mutation.isPending}>
            {mutation.isPending ? 'Saving…' : isEdit ? 'Save entry' : 'Add entry'}
          </Button>
        </Box>
      </Box>
    </Drawer>
  );
}

function humanise(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}
