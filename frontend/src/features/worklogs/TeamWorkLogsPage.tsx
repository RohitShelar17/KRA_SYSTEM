import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import LinearProgress from '@mui/material/LinearProgress';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import ChevronLeftOutlined from '@mui/icons-material/ChevronLeftOutlined';
import ChevronRightOutlined from '@mui/icons-material/ChevronRightOutlined';
import LockOpenOutlined from '@mui/icons-material/LockOpenOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { organisationApi } from '@/api/organisation';
import { workLogApi, workDashboardApi } from '@/api/work';
import { useAuth } from '@/contexts/AuthContext';
import { EmptyState } from '@/components/EmptyState';
import { PageHeader } from '@/components/PageHeader';
import { StatTile } from '@/components/StatTile';
import { StatusChip } from '@/components/StatusChip';
import { formatDayLabel, formatDuration, shiftDate, todayIso, toHours } from '@/utils/time';
import { UnlockDialog, type UnlockTarget } from './UnlockDialog';

/**
 * The manager's compliance view for a single date: who filed, who did not, and
 * how much of the shift each person accounted for.
 */
/** Whole days between an ISO date and today. */
function daysAgo(date: string): number {
  const then = new Date(`${date}T00:00:00`).getTime();
  const now = new Date(`${todayIso()}T00:00:00`).getTime();
  return Math.round((now - then) / 86_400_000);
}

export function TeamWorkLogsPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [date, setDate] = useState(todayIso());
  const [unlockTarget, setUnlockTarget] = useState<UnlockTarget | null>(null);
  const [unlockError, setUnlockError] = useState<string | null>(null);

  const dashboard = useQuery({
    queryKey: ['dashboard', 'manager', date],
    queryFn: () => workDashboardApi.manager(date),
  });

  const filed = useQuery({
    queryKey: ['worklog', 'team-filed', date],
    queryFn: () => workLogApi.pendingApprovals({ from: date, to: date, size: 100 }),
  });

  const missing = useQuery({
    queryKey: ['worklog', 'missing', date],
    queryFn: () => workLogApi.missing(date),
  });

  const settings = useQuery({
    queryKey: ['settings', 'company'],
    queryFn: () => organisationApi.settings(),
    staleTime: 5 * 60 * 1000,
  });

  const unlocks = useQuery({
    queryKey: ['worklog', 'unlocks'],
    queryFn: () => workLogApi.unlocks(true),
  });

  const grantUnlock = useMutation({
    mutationFn: (payload: { employeeId: number; workDate: string; reason: string }) =>
      workLogApi.unlock(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['worklog', 'unlocks'] });
      setUnlockTarget(null);
      setUnlockError(null);
    },
    onError: (caught) => setUnlockError(toErrorMessage(caught)),
  });

  const closeUnlock = useMutation({
    mutationFn: (id: number) => workLogApi.closeUnlock(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['worklog', 'unlocks'] }),
  });

  const isToday = date === todayIso();
  const data = dashboard.data;

  // Only worth offering where the window actually blocks them; inside it the
  // API would refuse, and rightly so.
  const window = settings.data?.backdateWindowDays ?? 3;
  const outsideWindow = daysAgo(date) > window;
  // Mirrors app.worklog.unlock-valid-hours; the server is the authority and
  // the response carries the real expiry, so this is only what the prompt says.
  const unlockHours = 72;
  const openFor = new Set(
    (unlocks.data ?? []).filter((row) => row.workDate === date).map((row) => row.employeeId),
  );

  return (
    <Box>
      <PageHeader
        eyebrow="Management"
        title="Team work logs"
        description="Submission compliance for a single day across everyone in your access scope."
      />

      <Card sx={{ mb: 4 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, px: 5, py: 4 }}>
          <IconButton size="small" onClick={() => setDate(shiftDate(date, -1))} aria-label="Previous day">
            <ChevronLeftOutlined sx={{ fontSize: 19 }} />
          </IconButton>
          <Typography variant="subtitle1" sx={{ fontWeight: 600, minWidth: 260, textAlign: 'center' }}>
            {formatDayLabel(date)}
          </Typography>
          <IconButton
            size="small"
            onClick={() => setDate(shiftDate(date, 1))}
            disabled={isToday}
            aria-label="Next day"
          >
            <ChevronRightOutlined sx={{ fontSize: 19 }} />
          </IconButton>
        </Box>
      </Card>

      {data && (
        <Box
          sx={{
            display: 'grid',
            gap: 4,
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
            mb: 4,
          }}
        >
          <StatTile
            label="Team size"
            value={data.teamSize}
            caption="People you review"
          />
          <StatTile
            label="Submitted"
            value={data.submittedForDate}
            unit={`/ ${data.teamSize}`}
            caption={`${data.submissionRatePercent}% submission rate`}
            progress={data.submissionRatePercent}
            progressColor={data.submissionRatePercent >= 80 ? 'success' : 'warning'}
          />
          <StatTile
            label="Not submitted"
            value={data.missingForDate}
            caption={data.missingForDate === 0 ? 'Everyone has filed' : 'Needs a nudge'}
            progressColor="error"
          />
          <StatTile
            label="Team hours logged"
            value={toHours(data.teamLoggedMinutes)}
            unit="hrs"
            caption={`${toHours(data.teamProductiveMinutes)} h productive`}
          />
        </Box>
      )}

      <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', lg: '3fr 2fr' } }}>
        <Card>
          <CardContent sx={{ p: 5 }}>
            <Typography variant="h5" sx={{ mb: 4 }}>
              Filed for this day
            </Typography>

            {(filed.data?.content ?? []).length === 0 ? (
              <EmptyState title="Nobody has filed this day yet" />
            ) : (
              <TableContainer sx={{ overflowX: 'auto' }}>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>Employee</TableCell>
                      <TableCell>Logged</TableCell>
                      <TableCell>Coverage</TableCell>
                      <TableCell>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(filed.data?.content ?? []).map((row) => (
                      <TableRow key={row.id} hover>
                        <TableCell>
                          <Typography sx={{ fontSize: 13, fontWeight: 600 }}>
                            {row.employeeName}
                          </Typography>
                          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                            {row.employeeCode}
                          </Typography>
                        </TableCell>
                        <TableCell className="tabular">{formatDuration(row.loggedMinutes)}</TableCell>
                        <TableCell sx={{ minWidth: 120 }}>
                          <LinearProgress
                            variant="determinate"
                            value={Math.min(row.loggedPercent, 100)}
                            color={row.loggedPercent >= 90 ? 'success' : 'warning'}
                          />
                          <Typography
                            variant="caption"
                            className="tabular"
                            sx={{ color: 'text.secondary' }}
                          >
                            {row.loggedPercent}%
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <StatusChip status={row.status} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardContent sx={{ p: 5 }}>
            <Typography variant="h5" sx={{ mb: 0.5 }}>
              Not submitted
            </Typography>
            <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
              A started draft still counts as not submitted.
            </Typography>

            {(missing.data ?? []).length === 0 ? (
              <Alert severity="success">Everyone in your scope has filed this day.</Alert>
            ) : (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                {(missing.data ?? []).map((row) => (
                  <Box
                    key={row.employeeId}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 3,
                      p: 3,
                      borderRadius: 1.5,
                      border: 1,
                      borderColor: 'divider',
                    }}
                  >
                    <Box sx={{ minWidth: 0 }}>
                      <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{row.employeeName}</Typography>
                      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                        {row.employeeCode}
                        {row.departmentName ? ` · ${row.departmentName}` : ''}
                      </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexShrink: 0 }}>
                      {row.startedButNotSubmitted ? (
                        <Chip
                          size="small"
                          color="warning"
                          variant="outlined"
                          label={`draft ${formatDuration(row.draftMinutes)}`}
                        />
                      ) : (
                        <Chip size="small" variant="outlined" label="nothing logged" />
                      )}
                      {/*
                        Not offered for yourself: the API refuses a manager
                        reopening their own date, and a button that always
                        fails is worse than no button.
                      */}
                      {outsideWindow && row.employeeId !== user?.employeeId &&
                        (openFor.has(row.employeeId) ? (
                          <Chip size="small" color="success" variant="outlined" label="reopened" />
                        ) : (
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<LockOpenOutlined sx={{ fontSize: 16 }} />}
                            onClick={() =>
                              setUnlockTarget({
                                employeeId: row.employeeId,
                                employeeName: row.employeeName,
                                workDate: date,
                              })
                            }
                          >
                            Reopen
                          </Button>
                        ))}
                    </Box>
                  </Box>
                ))}
              </Box>
            )}
          </CardContent>
        </Card>
      </Box>

      {(unlocks.data ?? []).length > 0 && (
        <Card sx={{ mt: 4 }}>
          <CardContent sx={{ p: 5 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
              Dates you have reopened
            </Typography>
            <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
              Each one lets that person file that single date until it expires. Close one early if it
              is no longer needed.
            </Typography>

            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {(unlocks.data ?? []).map((row) => (
                <Box
                  key={row.id}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 3,
                    p: 3,
                    borderRadius: 1.5,
                    border: 1,
                    borderColor: 'divider',
                  }}
                >
                  <Box sx={{ minWidth: 0 }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 600 }}>
                      {row.employeeName ?? 'Unknown'} — {formatDayLabel(row.workDate)}
                    </Typography>
                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                      {row.reason} · closes {new Date(row.expiresAt).toLocaleString('en-GB', {
                        day: '2-digit',
                        month: 'short',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </Typography>
                  </Box>
                  <Button
                    size="small"
                    color="inherit"
                    disabled={closeUnlock.isPending}
                    onClick={() => closeUnlock.mutate(row.id)}
                  >
                    Close now
                  </Button>
                </Box>
              ))}
            </Box>
          </CardContent>
        </Card>
      )}

      <UnlockDialog
        target={unlockTarget}
        hours={unlockHours}
        busy={grantUnlock.isPending}
        error={unlockError}
        onConfirm={(reason) =>
          unlockTarget &&
          grantUnlock.mutate({
            employeeId: unlockTarget.employeeId,
            workDate: unlockTarget.workDate,
            reason,
          })
        }
        onClose={() => {
          setUnlockTarget(null);
          setUnlockError(null);
        }}
      />
    </Box>
  );
}
