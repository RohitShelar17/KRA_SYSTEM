import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import CloseOutlined from '@mui/icons-material/CloseOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { employeeApi } from '@/api/employees';
import { catalogueApi, taskApi, type TaskPayload } from '@/api/work';
import { todayIso } from '@/utils/time';
import type { Priority, Task, TaskStatus } from '@/types/work';

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const STATUSES: TaskStatus[] = ['NOT_STARTED', 'IN_PROGRESS', 'ON_HOLD', 'BLOCKED', 'COMPLETED'];

interface Props {
  open: boolean;
  task: Task | null;
  /** Managers assigning work see the assignee picker; employees do not. */
  allowAssignment: boolean;
  defaultAssigneeId?: number | null;
  onClose: () => void;
}

interface FormState {
  assigneeId: number | '';
  projectId: number | '';
  taskCategoryId: number | '';
  taskTemplateId: number | '';
  title: string;
  description: string;
  priority: Priority;
  status: TaskStatus;
  estimatedHours: string;
  assignedDate: string;
  dueDate: string;
  remarks: string;
}

const EMPTY: FormState = {
  assigneeId: '',
  projectId: '',
  taskCategoryId: '',
  taskTemplateId: '',
  title: '',
  description: '',
  priority: 'MEDIUM',
  status: 'NOT_STARTED',
  estimatedHours: '',
  assignedDate: todayIso(),
  dueDate: '',
  remarks: '',
};

export function TaskDrawer({ open, task, allowAssignment, defaultAssigneeId, onClose }: Props) {
  const queryClient = useQueryClient();
  const isEdit = Boolean(task);
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
  const employees = useQuery({
    queryKey: ['employees', 'options'],
    queryFn: employeeApi.options,
    enabled: open && allowAssignment,
  });

  useEffect(() => {
    if (!open) {
      return;
    }
    setError(null);
    if (task) {
      setForm({
        assigneeId: task.assigneeId,
        projectId: task.projectId ?? '',
        taskCategoryId: task.taskCategoryId,
        taskTemplateId: '',
        title: task.title,
        description: task.description ?? '',
        priority: task.priority,
        status: task.status,
        estimatedHours: task.estimatedMinutes ? String(task.estimatedMinutes / 60) : '',
        assignedDate: task.assignedDate,
        dueDate: task.dueDate ?? '',
        remarks: task.remarks ?? '',
      });
    } else {
      setForm({ ...EMPTY, assigneeId: defaultAssigneeId ?? '' });
    }
  }, [open, task, defaultAssigneeId]);

  const mutation = useMutation({
    mutationFn: (payload: TaskPayload) =>
      task ? taskApi.update(task.id, payload) : taskApi.create(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      onClose();
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  function submit() {
    setError(null);
    const hours = Number(form.estimatedHours);
    mutation.mutate({
      assigneeId: allowAssignment && form.assigneeId !== '' ? Number(form.assigneeId) : null,
      projectId: form.projectId === '' ? null : Number(form.projectId),
      taskCategoryId: Number(form.taskCategoryId),
      taskTemplateId: form.taskTemplateId === '' ? null : Number(form.taskTemplateId),
      title: form.title.trim(),
      description: form.description || null,
      priority: form.priority,
      status: form.status,
      estimatedMinutes: form.estimatedHours && hours > 0 ? Math.round(hours * 60) : null,
      assignedDate: form.assignedDate || null,
      dueDate: form.dueDate || null,
      remarks: form.remarks || null,
    });
  }

  const canSubmit = Boolean(form.title.trim() && form.taskCategoryId);

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
            <Typography variant="h5">{isEdit ? 'Edit task' : 'New task'}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {allowAssignment ? 'Assign work to someone in your team' : 'A task for yourself'}
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

          {!isEdit && (templates.data ?? []).length > 0 && (
            <Box sx={{ mb: 5 }}>
              <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 1.5 }}>
                Start from a template
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5 }}>
                {(templates.data ?? []).slice(0, 8).map((template) => (
                  <Chip
                    key={template.id}
                    label={template.name}
                    size="small"
                    variant={form.taskTemplateId === template.id ? 'filled' : 'outlined'}
                    color={form.taskTemplateId === template.id ? 'primary' : 'default'}
                    onClick={() =>
                      setForm((current) => ({
                        ...current,
                        taskTemplateId: template.id,
                        title: template.name,
                        taskCategoryId: template.taskCategoryId,
                        priority: template.defaultPriority,
                        estimatedHours: template.defaultEstimatedMinutes
                          ? String(template.defaultEstimatedMinutes / 60)
                          : current.estimatedHours,
                      }))
                    }
                    sx={{ cursor: 'pointer', height: 24 }}
                  />
                ))}
              </Box>
            </Box>
          )}

          {allowAssignment && (
            <TextField
              select
              label="Assign to"
              fullWidth
              size="small"
              value={form.assigneeId}
              onChange={(event) => setForm({ ...form, assigneeId: Number(event.target.value) })}
              helperText="Only people inside your access scope are listed"
              sx={{ mb: 4 }}
              disabled={isEdit}
            >
              {(employees.data ?? []).map((option) => (
                <MenuItem key={option.id} value={option.id}>
                  {option.label} {option.code ? `· ${option.code}` : ''}
                </MenuItem>
              ))}
            </TextField>
          )}

          <TextField
            label="Task title"
            required
            fullWidth
            size="small"
            value={form.title}
            onChange={(event) => setForm({ ...form, title: event.target.value })}
            sx={{ mb: 4 }}
          />

          <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr', mb: 4 }}>
            <TextField
              select
              label="Category"
              required
              size="small"
              value={form.taskCategoryId}
              onChange={(event) => setForm({ ...form, taskCategoryId: Number(event.target.value) })}
            >
              {(categories.data ?? []).map((category) => (
                <MenuItem key={category.id} value={category.id}>
                  {category.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Project"
              size="small"
              value={form.projectId}
              onChange={(event) =>
                setForm({
                  ...form,
                  projectId: event.target.value === '' ? '' : Number(event.target.value),
                })
              }
            >
              <MenuItem value="">No project</MenuItem>
              {(projects.data ?? []).map((project) => (
                <MenuItem key={project.id} value={project.id}>
                  {project.label}
                </MenuItem>
              ))}
            </TextField>
          </Box>

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

          <Divider sx={{ my: 4 }}>
            <Typography variant="overline" sx={{ color: 'text.secondary' }}>
              Planning
            </Typography>
          </Divider>

          <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr' }}>
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
              label="Status"
              size="small"
              value={form.status}
              onChange={(event) => setForm({ ...form, status: event.target.value as TaskStatus })}
            >
              {STATUSES.map((status) => (
                <MenuItem key={status} value={status}>
                  {humanise(status)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Assigned date"
              type="date"
              size="small"
              value={form.assignedDate}
              onChange={(event) => setForm({ ...form, assignedDate: event.target.value })}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              label="Due date"
              type="date"
              size="small"
              value={form.dueDate}
              onChange={(event) => setForm({ ...form, dueDate: event.target.value })}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              label="Estimated hours"
              type="number"
              size="small"
              value={form.estimatedHours}
              onChange={(event) => setForm({ ...form, estimatedHours: event.target.value })}
              inputProps={{ step: 0.5, min: 0 }}
              helperText="Actual hours come from the work log"
              sx={{ gridColumn: '1 / -1' }}
            />
          </Box>
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
            {mutation.isPending ? 'Saving…' : isEdit ? 'Save task' : 'Create task'}
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
