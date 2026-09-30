import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
import Divider from '@mui/material/Divider';
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
import CheckCircleOutlineOutlined from '@mui/icons-material/CheckCircleOutlineOutlined';
import ReplayOutlined from '@mui/icons-material/ReplayOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { workLogApi } from '@/api/work';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { TimelineRibbon } from '@/features/worklogs/TimelineRibbon';
import { formatDuration, formatShortDate, toClock, toHours } from '@/utils/time';
import type { ApprovalAction, WorkLogDay, WorkLogDaySummary, WorkLogDayStatus } from '@/types/work';

const STATUS_FILTERS: WorkLogDayStatus[] = [
  'SUBMITTED',
  'APPROVED',
  'MODIFICATION_REQUESTED',
  'REJECTED',
];

export function ReviewQueuePage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<WorkLogDayStatus>('SUBMITTED');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [selected, setSelected] = useState<WorkLogDaySummary | null>(null);
  const [decision, setDecision] = useState<ApprovalAction>('APPROVED');
  const [comments, setComments] = useState('');
  const [error, setError] = useState<string | null>(null);

  const queue = useQuery({
    queryKey: ['worklog', 'queue', { status, page, size }],
    queryFn: () => workLogApi.pendingApprovals({ status, page, size }),
    placeholderData: keepPreviousData,
  });

  const detail = useQuery({
    queryKey: ['worklog', 'day', selected?.workDate, selected?.employeeId],
    queryFn: () => workLogApi.day(selected!.workDate, selected!.employeeId),
    enabled: Boolean(selected),
  });

  const review = useMutation({
    mutationFn: () => workLogApi.review(selected!.id, decision, comments || undefined),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['worklog'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      closeDialog();
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  function openReview(row: WorkLogDaySummary, action: ApprovalAction) {
    setSelected(row);
    setDecision(action);
    setComments('');
    setError(null);
  }

  function closeDialog() {
    setSelected(null);
    setComments('');
  }

  const rows = queue.data?.content ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Management"
        title="Review queue"
        description="Days your team has filed. Approve them, or send one back with a note saying what to change."
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Card sx={{ mb: 4 }}>
        <Box sx={{ p: 4, display: 'flex', gap: 3, flexWrap: 'wrap' }}>
          <TextField
            select
            size="small"
            label="Status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as WorkLogDayStatus);
              setPage(0);
            }}
            sx={{ minWidth: 220 }}
          >
            {STATUS_FILTERS.map((value) => (
              <MenuItem key={value} value={value}>
                {humanise(value)}
              </MenuItem>
            ))}
          </TextField>
        </Box>
      </Card>

      <Card>
        {queue.isLoading ? (
          <TableSkeleton rows={6} columns={6} />
        ) : rows.length === 0 ? (
          <EmptyState
            title={status === 'SUBMITTED' ? 'Nothing waiting for review' : 'No days with this status'}
            description={
              status === 'SUBMITTED'
                ? 'When your team submits a day, it appears here.'
                : 'Try a different status filter.'
            }
          />
        ) : (
          <>
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Employee</TableCell>
                    <TableCell>Date</TableCell>
                    <TableCell>Logged</TableCell>
                    <TableCell>Productive</TableCell>
                    <TableCell>Coverage</TableCell>
                    <TableCell>Entries</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Decision</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.id} hover>
                      <TableCell>
                        <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{row.employeeName}</Typography>
                        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                          {row.employeeCode}
                          {row.departmentName ? ` · ${row.departmentName}` : ''}
                        </Typography>
                      </TableCell>
                      <TableCell className="tabular">{formatShortDate(row.workDate)}</TableCell>
                      <TableCell className="tabular">{formatDuration(row.loggedMinutes)}</TableCell>
                      <TableCell className="tabular">{formatDuration(row.productiveMinutes)}</TableCell>
                      <TableCell sx={{ minWidth: 120 }}>
                        <LinearProgress
                          variant="determinate"
                          value={Math.min(row.loggedPercent, 100)}
                          color={row.loggedPercent >= 90 ? 'success' : 'warning'}
                        />
                        <Typography variant="caption" className="tabular" sx={{ color: 'text.secondary' }}>
                          {row.loggedPercent}% of shift
                        </Typography>
                      </TableCell>
                      <TableCell className="tabular">{row.entryCount}</TableCell>
                      <TableCell>
                        <StatusChip status={row.status} />
                      </TableCell>
                      <TableCell align="right">
                        <Box sx={{ display: 'flex', gap: 2, justifyContent: 'flex-end' }}>
                          <Button size="small" variant="outlined" onClick={() => openReview(row, 'APPROVED')}>
                            Open
                          </Button>
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>

            <TablePagination
              component="div"
              count={queue.data?.totalElements ?? 0}
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

      <Dialog open={Boolean(selected)} onClose={closeDialog} maxWidth="md" fullWidth>
        <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>
          {selected?.employeeName} · {selected ? formatShortDate(selected.workDate) : ''}
        </DialogTitle>
        <DialogContent>
          {detail.isLoading || !detail.data ? (
            <Box sx={{ py: 8 }}>
              <LinearProgress />
            </Box>
          ) : (
            <ReviewDetail day={detail.data} />
          )}

          <Divider sx={{ my: 5 }} />

          {error && (
            <Alert severity="error" sx={{ mb: 4 }}>
              {error}
            </Alert>
          )}

          <TextField
            select
            fullWidth
            size="small"
            label="Decision"
            value={decision}
            onChange={(event) => setDecision(event.target.value as ApprovalAction)}
            sx={{ mb: 4 }}
          >
            <MenuItem value="APPROVED">Approve the day</MenuItem>
            <MenuItem value="MODIFICATION_REQUESTED">Send back for changes</MenuItem>
            <MenuItem value="REJECTED">Reject</MenuItem>
            {selected?.status === 'APPROVED' && <MenuItem value="REOPENED">Reopen for editing</MenuItem>}
          </TextField>

          <TextField
            fullWidth
            size="small"
            multiline
            minRows={2}
            label={decision === 'APPROVED' ? 'Comment (optional)' : 'Comment (required)'}
            value={comments}
            onChange={(event) => setComments(event.target.value)}
            helperText={
              decision === 'APPROVED'
                ? 'The employee sees this on their day.'
                : 'Say what needs to change so they can act on it.'
            }
          />
        </DialogContent>
        <DialogActions sx={{ px: 6, pb: 5 }}>
          <Button variant="outlined" onClick={closeDialog}>
            Cancel
          </Button>
          <Button
            variant="contained"
            color={decision === 'APPROVED' ? 'primary' : decision === 'REJECTED' ? 'error' : 'warning'}
            startIcon={
              decision === 'APPROVED' ? (
                <CheckCircleOutlineOutlined sx={{ fontSize: 18 }} />
              ) : (
                <ReplayOutlined sx={{ fontSize: 18 }} />
              )
            }
            disabled={review.isPending || (decision !== 'APPROVED' && !comments.trim())}
            onClick={() => review.mutate()}
          >
            {review.isPending ? 'Saving…' : 'Record decision'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function ReviewDetail({ day }: { day: WorkLogDay }) {
  return (
    <Box>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 4, mb: 4 }}>
        <Metric label="Logged" value={`${toHours(day.totals.loggedMinutes)} h`} />
        <Metric label="Productive" value={`${toHours(day.totals.productiveMinutes)} h`} />
        <Metric label="Meetings" value={formatDuration(day.totals.meetingMinutes)} />
        <Metric label="Break" value={formatDuration(day.totals.breakMinutes)} />
        <Metric
          label="Unallocated"
          value={formatDuration(day.totals.unallocatedMinutes)}
          warn={day.totals.unallocatedMinutes > 60}
        />
      </Box>

      <Box sx={{ mb: 5 }}>
        <TimelineRibbon segments={day.timeline} shiftStart={day.shiftStart} shiftEnd={day.shiftEnd} />
      </Box>

      <Card>
        <CardContent sx={{ p: 4 }}>
          <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 2 }}>
            Entries
          </Typography>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {day.entries.map((entry) => (
              <Box
                key={entry.id}
                sx={{ display: 'flex', gap: 3, alignItems: 'baseline', flexWrap: 'wrap' }}
              >
                <Typography variant="caption" className="tabular" sx={{ minWidth: 104 }}>
                  {toClock(entry.startTime)} – {toClock(entry.endTime)}
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 600, flex: 1, minWidth: 160 }}>
                  {entry.title}
                </Typography>
                <Chip size="small" variant="outlined" label={entry.taskCategoryName} />
                <StatusChip status={entry.status} />
                <Typography variant="caption" className="tabular" sx={{ color: 'text.secondary' }}>
                  {formatDuration(entry.durationMinutes)}
                </Typography>
              </Box>
            ))}
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}

function Metric({ label, value, warn }: { label: string; value: string; warn?: boolean }) {
  return (
    <Box>
      <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
        {label}
      </Typography>
      <Typography
        variant="subtitle1"
        className="tabular"
        sx={{ fontWeight: 600, color: warn ? 'error.main' : 'text.primary' }}
      >
        {value}
      </Typography>
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
