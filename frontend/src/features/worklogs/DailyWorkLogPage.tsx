import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AccessTimeOutlined from '@mui/icons-material/AccessTimeOutlined';
import AddOutlined from '@mui/icons-material/AddOutlined';
import ChevronLeftOutlined from '@mui/icons-material/ChevronLeftOutlined';
import ChevronRightOutlined from '@mui/icons-material/ChevronRightOutlined';
import DeleteOutlineOutlined from '@mui/icons-material/DeleteOutlineOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import SendOutlined from '@mui/icons-material/SendOutlined';
import TrendingUpOutlined from '@mui/icons-material/TrendingUpOutlined';
import WarningAmberOutlined from '@mui/icons-material/WarningAmberOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { workLogApi } from '@/api/work';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { EmptyState } from '@/components/EmptyState';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatTile } from '@/components/StatTile';
import { StatusChip } from '@/components/StatusChip';
import type { StatusTone } from '@/theme/tokens';
import {
  formatDayLabel,
  formatDuration,
  shiftDate,
  toClock,
  toHours,
  todayIso,
} from '@/utils/time';
import type { WorkLogEntry } from '@/types/work';
import { TimelineRibbon } from './TimelineRibbon';
import { WorkEntryDrawer } from './WorkEntryDrawer';

const STATUS_TONE: Record<string, StatusTone> = {
  DRAFT: 'neutral',
  SUBMITTED: 'active',
  UNDER_REVIEW: 'active',
  APPROVED: 'success',
  REJECTED: 'critical',
  MODIFICATION_REQUESTED: 'pending',
};

export function DailyWorkLogPage() {
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  // The calendar links straight to a day. Read the parameter once, as the
  // starting point only: the arrows below own the date from then on, and
  // rewriting the URL on every step would make the back button walk the month.
  const [date, setDate] = useState(() => {
    const requested = searchParams.get('date');
    return requested && /^\d{4}-\d{2}-\d{2}$/.test(requested) ? requested : todayIso();
  });
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<WorkLogEntry | null>(null);
  const [preset, setPreset] = useState<{ start: string; end: string } | null>(null);
  const [pendingDelete, setPendingDelete] = useState<WorkLogEntry | null>(null);
  const [confirmSubmit, setConfirmSubmit] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const day = useQuery({
    queryKey: ['worklog', 'day', date],
    queryFn: () => workLogApi.day(date),
  });

  const deleteEntry = useMutation({
    mutationFn: (entryId: number) => workLogApi.deleteEntry(entryId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['worklog'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      setPendingDelete(null);
    },
    onError: (caught) => {
      setError(toErrorMessage(caught));
      setPendingDelete(null);
    },
  });

  const submitDay = useMutation({
    mutationFn: () => workLogApi.submit(date),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['worklog'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      setConfirmSubmit(false);
      setError(null);
    },
    onError: (caught) => {
      setError(toErrorMessage(caught));
      setConfirmSubmit(false);
    },
  });

  if (day.isLoading) {
    return <FullPageLoader label="Loading your day" />;
  }
  if (day.isError || !day.data) {
    return <Alert severity="error">Could not load this day. Refresh to try again.</Alert>;
  }

  const data = day.data;
  const totals = data.totals;
  const isToday = date === todayIso();

  function openAdd(start?: string, end?: string) {
    setEditing(null);
    setPreset(start && end ? { start, end } : null);
    setDrawerOpen(true);
  }

  function openEdit(entryId: number) {
    const entry = data.entries.find((row) => row.id === entryId) ?? null;
    if (entry) {
      setEditing(entry);
      setPreset(null);
      setDrawerOpen(true);
    }
  }

  return (
    <Box>
      <PageHeader
        eyebrow="Work tracking"
        title="Daily work log"
        description="Record where your hours went. Overlaps and the daily limit are checked as you save."
        actions={
          <Box sx={{ display: 'flex', gap: 2 }}>
            {data.editable && (
              <Button
                variant="outlined"
                startIcon={<AddOutlined sx={{ fontSize: 18 }} />}
                onClick={() => openAdd()}
              >
                Add entry
              </Button>
            )}
            {data.editable && data.totals.entryCount > 0 && (
              <Button
                variant="contained"
                startIcon={<SendOutlined sx={{ fontSize: 17 }} />}
                onClick={() => setConfirmSubmit(true)}
              >
                Submit day
              </Button>
            )}
          </Box>
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Card sx={{ mb: 4 }}>
        <Box
          sx={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            gap: 3,
            px: 5,
            py: 4,
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <IconButton size="small" onClick={() => setDate(shiftDate(date, -1))} aria-label="Previous day">
              <ChevronLeftOutlined sx={{ fontSize: 19 }} />
            </IconButton>
            <Box sx={{ minWidth: 240, textAlign: 'center' }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                {formatDayLabel(date)}
              </Typography>
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                Shift {toClock(data.shiftStart)} – {toClock(data.shiftEnd)}
              </Typography>
            </Box>
            <IconButton
              size="small"
              onClick={() => setDate(shiftDate(date, 1))}
              disabled={isToday}
              aria-label="Next day"
            >
              <ChevronRightOutlined sx={{ fontSize: 19 }} />
            </IconButton>
          </Box>

          {!isToday && (
            <Button size="small" variant="text" onClick={() => setDate(todayIso())}>
              Back to today
            </Button>
          )}

          <Box sx={{ flex: 1 }} />

          {data.holiday && (
            <Chip label={data.holidayName ?? 'Holiday'} size="small" color="warning" variant="outlined" />
          )}
          <StatusChip
            status={data.status}
            tone={STATUS_TONE[data.status]}
            label={data.status.toLowerCase().replace(/_/g, ' ')}
          />
        </Box>
      </Card>

      {data.status === 'MODIFICATION_REQUESTED' && (
        <Alert severity="warning" sx={{ mb: 4 }}>
          <strong>{data.reviewedByName ?? 'Your manager'}</strong> returned this day:{' '}
          {data.reviewComments}
        </Alert>
      )}
      {data.status === 'REJECTED' && (
        <Alert severity="error" sx={{ mb: 4 }}>
          Rejected by {data.reviewedByName ?? 'your manager'}: {data.reviewComments}
        </Alert>
      )}
      {data.status === 'APPROVED' && (
        <Alert severity="success" sx={{ mb: 4 }}>
          Approved{data.reviewedByName ? ` by ${data.reviewedByName}` : ''}. This day is now read-only.
        </Alert>
      )}
      {data.status === 'SUBMITTED' && (
        <Alert severity="info" sx={{ mb: 4 }}>
          Submitted and waiting for review. You cannot edit it until it is returned.
        </Alert>
      )}

      <Box
        sx={{
          display: 'grid',
          gap: 4,
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
          mb: 4,
        }}
      >
        <StatTile
          label="Office hours"
          value={toHours(totals.officeMinutes)}
          unit="hrs"
          caption={`${toClock(data.shiftStart)} – ${toClock(data.shiftEnd)}`}
          icon={AccessTimeOutlined}
        />
        <StatTile
          label="Logged hours"
          value={toHours(totals.loggedMinutes)}
          unit={`/ ${toHours(totals.officeMinutes)}`}
          caption={`${totals.entryCount} ${totals.entryCount === 1 ? 'entry' : 'entries'} recorded`}
          progress={totals.loggedPercent}
          progressColor={totals.loggedPercent >= 90 ? 'success' : 'primary'}
          trailing={
            <Chip
              size="small"
              label={`${totals.loggedPercent}%`}
              color={totals.loggedPercent >= 90 ? 'success' : 'default'}
            />
          }
        />
        <StatTile
          label="Productive hours"
          value={toHours(totals.productiveMinutes)}
          unit="hrs"
          caption={`${totals.productivePercent}% of logged time`}
          icon={TrendingUpOutlined}
          progress={totals.productivePercent}
          progressColor="success"
        />
        <StatTile
          label="Unallocated"
          value={toHours(totals.unallocatedMinutes)}
          unit="hrs"
          caption={
            totals.breakMinutes || totals.meetingMinutes
              ? `Break ${formatDuration(totals.breakMinutes)} · Meetings ${formatDuration(totals.meetingMinutes)}`
              : 'No breaks or meetings logged'
          }
          icon={WarningAmberOutlined}
          progress={
            totals.officeMinutes ? (totals.unallocatedMinutes / totals.officeMinutes) * 100 : 0
          }
          progressColor={totals.unallocatedMinutes > 60 ? 'error' : 'warning'}
        />
      </Box>

      <Card sx={{ mb: 4 }}>
        <CardContent sx={{ p: 5 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 4 }}>
            <Box>
              <Typography variant="h5">Day timeline</Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {data.editable
                  ? 'Click a striped gap to fill it, or a block to edit that entry.'
                  : 'Read-only while this day is under review.'}
              </Typography>
            </Box>
            {totals.overtimeMinutes > 0 && (
              <Chip
                size="small"
                color="warning"
                label={`${formatDuration(totals.overtimeMinutes)} overtime`}
              />
            )}
          </Box>

          <TimelineRibbon
            segments={data.timeline}
            shiftStart={data.shiftStart}
            shiftEnd={data.shiftEnd}
            onSelectGap={(start, end) => data.editable && openAdd(start, end)}
            onSelectEntry={(entryId) => data.editable && openEdit(entryId)}
          />
        </CardContent>
      </Card>

      <Card>
        <CardContent sx={{ p: 5 }}>
          <Typography variant="h5" sx={{ mb: 4 }}>
            Entries
          </Typography>

          {data.entries.length === 0 ? (
            <EmptyState
              title="Nothing logged for this day yet"
              description="Add your first entry, or click a gap on the timeline to start from a time slot."
              action={
                data.editable ? (
                  <Button variant="contained" onClick={() => openAdd()}>
                    Add work entry
                  </Button>
                ) : undefined
              }
            />
          ) : (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              {data.entries.map((entry) => (
                <Box
                  key={entry.id}
                  sx={{
                    display: 'flex',
                    gap: 4,
                    p: 4,
                    borderRadius: 1.5,
                    border: 1,
                    borderColor: 'divider',
                    borderLeft: 3,
                    borderLeftColor: entry.colourHex ?? 'primary.main',
                  }}
                >
                  <Box sx={{ minWidth: 118 }}>
                    <Typography variant="subtitle2" className="tabular">
                      {toClock(entry.startTime)} – {toClock(entry.endTime)}
                    </Typography>
                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                      {formatDuration(entry.durationMinutes)}
                    </Typography>
                  </Box>

                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', mb: 1 }}>
                      <Typography sx={{ fontSize: 14, fontWeight: 600 }}>{entry.title}</Typography>
                      <StatusChip status={entry.status} />
                      {!entry.productive && (
                        <Chip size="small" variant="outlined" label="non-productive" />
                      )}
                      {entry.billable && <Chip size="small" variant="outlined" label="billable" />}
                    </Box>

                    {entry.description && (
                      <Typography variant="body2" sx={{ color: 'text.secondary', mb: 1.5 }}>
                        {entry.description}
                      </Typography>
                    )}

                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                      <Meta label="Category" value={entry.taskCategoryName} />
                      {entry.projectName && <Meta label="Project" value={entry.projectName} />}
                      {entry.taskTitle && <Meta label="Task" value={entry.taskTitle} />}
                      <Meta label="Completion" value={`${entry.completionPct}%`} />
                    </Box>
                  </Box>

                  {data.editable && (
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
                      <Tooltip title="Edit">
                        <IconButton size="small" onClick={() => openEdit(entry.id)}>
                          <EditOutlined sx={{ fontSize: 17 }} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Remove">
                        <IconButton size="small" onClick={() => setPendingDelete(entry)}>
                          <DeleteOutlineOutlined sx={{ fontSize: 17 }} />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  )}
                </Box>
              ))}
            </Box>
          )}

          {data.history.length > 0 && (
            <>
              <Divider sx={{ my: 5 }} />
              <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 2 }}>
                Review history
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {data.history.map((item) => (
                  <Box key={item.id} sx={{ display: 'flex', gap: 3, alignItems: 'baseline' }}>
                    <Typography variant="caption" className="tabular" sx={{ color: 'text.secondary' }}>
                      {new Date(item.createdAt).toLocaleString('en-GB', {
                        day: '2-digit',
                        month: 'short',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </Typography>
                    <Typography variant="body2">
                      <strong>{item.actorName ?? 'System'}</strong>{' '}
                      {item.action.toLowerCase().replace(/_/g, ' ')}
                      {item.comments ? ` — ${item.comments}` : ''}
                    </Typography>
                  </Box>
                ))}
              </Box>
            </>
          )}
        </CardContent>
      </Card>

      <WorkEntryDrawer
        open={drawerOpen}
        workDate={date}
        entry={editing}
        presetStart={preset?.start}
        presetEnd={preset?.end}
        onClose={() => {
          setDrawerOpen(false);
          setEditing(null);
          setPreset(null);
        }}
      />

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Remove entry"
        message={`Remove "${pendingDelete?.title ?? ''}" from this day? The time becomes unallocated again.`}
        confirmLabel="Remove"
        destructive
        busy={deleteEntry.isPending}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => pendingDelete && deleteEntry.mutate(pendingDelete.id)}
      />

      <ConfirmDialog
        open={confirmSubmit}
        title="Submit this day"
        message={
          data.approvalRequired
            ? `${toHours(totals.loggedMinutes)} hours across ${totals.entryCount} entries will go to your manager for review. You cannot edit the day until it is returned.`
            : `${toHours(totals.loggedMinutes)} hours across ${totals.entryCount} entries will be filed. Approval is switched off, so the day closes immediately.`
        }
        confirmLabel="Submit"
        busy={submitDay.isPending}
        onCancel={() => setConfirmSubmit(false)}
        onConfirm={() => submitDay.mutate()}
      />
    </Box>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block', fontSize: 10.5 }}>
        {label}
      </Typography>
      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
        {value}
      </Typography>
    </Box>
  );
}
