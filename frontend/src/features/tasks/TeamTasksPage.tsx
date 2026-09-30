import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import InputAdornment from '@mui/material/InputAdornment';
import LinearProgress from '@mui/material/LinearProgress';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import SearchOutlined from '@mui/icons-material/SearchOutlined';
import { employeeApi } from '@/api/employees';
import { taskApi } from '@/api/work';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { formatDuration, formatShortDate } from '@/utils/time';
import type { Task, TaskStatus } from '@/types/work';
import { PriorityChip } from './MyTasksPage';
import { TaskDrawer } from './TaskDrawer';

const STATUS_FILTERS: (TaskStatus | 'ALL')[] = [
  'ALL',
  'NOT_STARTED',
  'IN_PROGRESS',
  'ON_HOLD',
  'BLOCKED',
  'COMPLETED',
];

export function TeamTasksPage() {
  const [query, setQuery] = useState('');
  const [assigneeId, setAssigneeId] = useState<number | ''>('');
  const [status, setStatus] = useState<TaskStatus | 'ALL'>('ALL');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Task | null>(null);

  const employees = useQuery({ queryKey: ['employees', 'options'], queryFn: employeeApi.options });

  const tasks = useQuery({
    queryKey: ['tasks', 'team', { query, assigneeId, status, page, size }],
    queryFn: () =>
      taskApi.team({
        query: query || undefined,
        assigneeId: assigneeId === '' ? undefined : assigneeId,
        status: status === 'ALL' ? undefined : status,
        page,
        size,
      }),
    placeholderData: keepPreviousData,
  });

  const rows = tasks.data?.content ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Management"
        title="Team tasks"
        description="Everything assigned across your reporting line, with the hours actually logged against each."
        actions={
          <Button
            variant="contained"
            startIcon={<AddOutlined sx={{ fontSize: 18 }} />}
            onClick={() => {
              setEditing(null);
              setDrawerOpen(true);
            }}
          >
            Assign task
          </Button>
        }
      />

      <Card sx={{ mb: 4 }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3, p: 4 }}>
          <TextField
            size="small"
            placeholder="Search task titles"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchOutlined sx={{ fontSize: 17, color: 'text.disabled' }} />
                </InputAdornment>
              ),
            }}
            sx={{ minWidth: 240, flex: 1 }}
          />
          <TextField
            select
            size="small"
            label="Assignee"
            value={assigneeId}
            onChange={(event) => {
              setAssigneeId(event.target.value === '' ? '' : Number(event.target.value));
              setPage(0);
            }}
            sx={{ minWidth: 200 }}
          >
            <MenuItem value="">Everyone in scope</MenuItem>
            {(employees.data ?? []).map((option) => (
              <MenuItem key={option.id} value={option.id}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            size="small"
            label="Status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as TaskStatus | 'ALL');
              setPage(0);
            }}
            sx={{ minWidth: 180 }}
          >
            {STATUS_FILTERS.map((value) => (
              <MenuItem key={value} value={value}>
                {value === 'ALL' ? 'All statuses' : humanise(value)}
              </MenuItem>
            ))}
          </TextField>
        </Box>
      </Card>

      <Card>
        {tasks.isLoading ? (
          <TableSkeleton rows={7} columns={6} />
        ) : rows.length === 0 ? (
          <EmptyState
            title="No tasks match these filters"
            description="Assign a task, or widen the filters."
          />
        ) : (
          <>
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Task</TableCell>
                    <TableCell>Assignee</TableCell>
                    <TableCell>Priority</TableCell>
                    <TableCell>Due</TableCell>
                    <TableCell>Progress</TableCell>
                    <TableCell>Logged</TableCell>
                    <TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((task) => (
                    <TableRow
                      key={task.id}
                      hover
                      sx={{ cursor: 'pointer' }}
                      onClick={() => {
                        setEditing(task);
                        setDrawerOpen(true);
                      }}
                    >
                      <TableCell>
                        <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{task.title}</Typography>
                        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                          {task.taskCategoryName}
                          {task.projectName ? ` · ${task.projectName}` : ''}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        {task.assigneeName}
                        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                          {task.assigneeCode}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <PriorityChip priority={task.priority} />
                      </TableCell>
                      <TableCell className="tabular">
                        {task.dueDate ? (
                          <Typography
                            variant="body2"
                            sx={{
                              color: task.overdue ? 'error.main' : 'text.primary',
                              fontWeight: task.overdue ? 600 : 400,
                            }}
                          >
                            {formatShortDate(task.dueDate)}
                          </Typography>
                        ) : (
                          '—'
                        )}
                      </TableCell>
                      <TableCell sx={{ minWidth: 110 }}>
                        <LinearProgress
                          variant="determinate"
                          value={task.completionPct}
                          color={task.completionPct === 100 ? 'success' : 'primary'}
                        />
                        <Typography variant="caption" className="tabular" sx={{ color: 'text.secondary' }}>
                          {task.completionPct}%
                        </Typography>
                      </TableCell>
                      <TableCell className="tabular">{formatDuration(task.actualMinutes)}</TableCell>
                      <TableCell>
                        <StatusChip status={task.status} />
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
        allowAssignment
        onClose={() => {
          setDrawerOpen(false);
          setEditing(null);
        }}
      />
    </Box>
  );
}

function humanise(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}
