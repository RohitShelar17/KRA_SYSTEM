import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import LinearProgress from '@mui/material/LinearProgress';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { taskApi } from '@/api/work';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { formatDuration, formatShortDate } from '@/utils/time';
import type { Task, TaskStatus } from '@/types/work';
import { TaskDrawer } from './TaskDrawer';

const TABS: { label: string; status?: TaskStatus; openOnly?: boolean }[] = [
  { label: 'Open', openOnly: true },
  { label: 'In progress', status: 'IN_PROGRESS' },
  { label: 'Not started', status: 'NOT_STARTED' },
  { label: 'Completed', status: 'COMPLETED' },
  { label: 'All' },
];

const QUICK_STATUSES: TaskStatus[] = ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'ON_HOLD', 'BLOCKED'];

export function MyTasksPage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Task | null>(null);
  const [error, setError] = useState<string | null>(null);

  const active = TABS[tab];

  const tasks = useQuery({
    queryKey: ['tasks', 'my', { tab, page, size }],
    queryFn: () =>
      taskApi.mine({ status: active.status, openOnly: active.openOnly ?? false, page, size }),
    placeholderData: keepPreviousData,
  });

  const counts = useQuery({ queryKey: ['tasks', 'counts'], queryFn: () => taskApi.counts() });

  const changeStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: TaskStatus }) =>
      taskApi.changeStatus(id, status),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const rows = tasks.data?.content ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Work tracking"
        title="My tasks"
        description="What you have been assigned and what you set yourself. Actual hours come from your work log."
        actions={
          <Button
            variant="contained"
            startIcon={<AddOutlined sx={{ fontSize: 18 }} />}
            onClick={() => {
              setEditing(null);
              setDrawerOpen(true);
            }}
          >
            New task
          </Button>
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {counts.data && counts.data.overdue > 0 && (
        <Alert severity="warning" sx={{ mb: 4 }}>
          {counts.data.overdue} {counts.data.overdue === 1 ? 'task is' : 'tasks are'} past their due date.
        </Alert>
      )}

      <Card sx={{ mb: 4 }}>
        <Tabs
          value={tab}
          onChange={(_, value) => {
            setTab(value);
            setPage(0);
          }}
          sx={{ px: 3 }}
        >
          {TABS.map((entry, index) => (
            <Tab
              key={entry.label}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  {entry.label}
                  {counts.data && (
                    <Chip
                      size="small"
                      label={countFor(entry, counts.data)}
                      sx={{ height: 18, fontSize: 10.5 }}
                    />
                  )}
                </Box>
              }
              value={index}
            />
          ))}
        </Tabs>
      </Card>

      <Card>
        {tasks.isLoading ? (
          <TableSkeleton rows={6} columns={6} />
        ) : rows.length === 0 ? (
          <EmptyState
            title="No tasks here"
            description="Create a task for yourself, or wait for your manager to assign one."
          />
        ) : (
          <>
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Task</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Priority</TableCell>
                    <TableCell>Due</TableCell>
                    <TableCell>Progress</TableCell>
                    <TableCell>Hours</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((task) => (
                    <TableRow key={task.id} hover>
                      <TableCell>
                        <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{task.title}</Typography>
                        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                          {task.projectName ?? 'No project'}
                          {task.assignedByName && !task.selfAssigned
                            ? ` · assigned by ${task.assignedByName}`
                            : ' · self-created'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                          <Box
                            sx={{
                              width: 8,
                              height: 8,
                              borderRadius: '50%',
                              bgcolor: task.colourHex ?? 'primary.main',
                            }}
                          />
                          {task.taskCategoryName}
                        </Box>
                      </TableCell>
                      <TableCell>
                        <PriorityChip priority={task.priority} />
                      </TableCell>
                      <TableCell className="tabular">
                        {task.dueDate ? (
                          <Typography
                            variant="body2"
                            sx={{ color: task.overdue ? 'error.main' : 'text.primary', fontWeight: task.overdue ? 600 : 400 }}
                          >
                            {formatShortDate(task.dueDate)}
                          </Typography>
                        ) : (
                          '—'
                        )}
                      </TableCell>
                      <TableCell sx={{ minWidth: 120 }}>
                        <LinearProgress
                          variant="determinate"
                          value={task.completionPct}
                          color={task.completionPct === 100 ? 'success' : 'primary'}
                          sx={{ mb: 0.5 }}
                        />
                        <Typography variant="caption" className="tabular" sx={{ color: 'text.secondary' }}>
                          {task.completionPct}%
                        </Typography>
                      </TableCell>
                      <TableCell className="tabular">
                        {formatDuration(task.actualMinutes)}
                        {task.estimatedMinutes ? (
                          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                            est {formatDuration(task.estimatedMinutes)}
                          </Typography>
                        ) : null}
                      </TableCell>
                      <TableCell sx={{ minWidth: 150 }}>
                        <TextField
                          select
                          size="small"
                          value={task.status}
                          onChange={(event) =>
                            changeStatus.mutate({
                              id: task.id,
                              status: event.target.value as TaskStatus,
                            })
                          }
                          SelectProps={{ sx: { fontSize: 12 } }}
                          fullWidth
                        >
                          {QUICK_STATUSES.map((status) => (
                            <MenuItem key={status} value={status} sx={{ fontSize: 12.5 }}>
                              {humanise(status)}
                            </MenuItem>
                          ))}
                        </TextField>
                      </TableCell>
                      <TableCell align="right">
                        <Tooltip title="Edit">
                          <IconButton
                            size="small"
                            onClick={() => {
                              setEditing(task);
                              setDrawerOpen(true);
                            }}
                          >
                            <EditOutlined sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>

            <TablePagination
              component="div"
              count={tasks.data?.totalElements ?? 0}
              page={page}
              onPageChange={(_, next) => setPage(next)}
              rowsPerPage={size}
              onRowsPerPageChange={(event) => {
                setSize(Number(event.target.value));
                setPage(0);
              }}
              rowsPerPageOptions={[10, 25, 50]}
            />
          </>
        )}
      </Card>

      <TaskDrawer
        open={drawerOpen}
        task={editing}
        allowAssignment={false}
        onClose={() => {
          setDrawerOpen(false);
          setEditing(null);
        }}
      />
    </Box>
  );
}

export function PriorityChip({ priority }: { priority: string }) {
  const tone =
    priority === 'CRITICAL'
      ? 'critical'
      : priority === 'HIGH'
        ? 'pending'
        : priority === 'MEDIUM'
          ? 'active'
          : 'neutral';
  return <StatusChip status={priority} tone={tone} label={priority.toLowerCase()} />;
}

function countFor(
  entry: { label: string; status?: TaskStatus; openOnly?: boolean },
  counts: { total: number; notStarted: number; inProgress: number; completed: number },
): number {
  if (entry.openOnly) {
    return counts.notStarted + counts.inProgress;
  }
  switch (entry.status) {
    case 'IN_PROGRESS':
      return counts.inProgress;
    case 'NOT_STARTED':
      return counts.notStarted;
    case 'COMPLETED':
      return counts.completed;
    default:
      return counts.total;
  }
}

function humanise(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}
