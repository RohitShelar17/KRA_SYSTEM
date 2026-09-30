import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import CheckCircleOutlined from '@mui/icons-material/CheckCircleOutlined';
import ChevronLeftOutlined from '@mui/icons-material/ChevronLeftOutlined';
import ChevronRightOutlined from '@mui/icons-material/ChevronRightOutlined';
import EventBusyOutlined from '@mui/icons-material/EventBusyOutlined';
import EventNoteOutlined from '@mui/icons-material/EventNoteOutlined';
import TrendingUpOutlined from '@mui/icons-material/TrendingUpOutlined';
import { employeeApi } from '@/api/employees';
import { organisationApi } from '@/api/organisation';
import { workLogApi } from '@/api/work';
import { PageHeader } from '@/components/PageHeader';
import { StatTile } from '@/components/StatTile';
import { TableSkeleton } from '@/components/LoadingState';
import { useAuth } from '@/contexts/AuthContext';
import { statusTone, statusToneDark, type StatusTone } from '@/theme/tokens';
import { formatDayLabel, formatDuration, toHours, todayIso } from '@/utils/time';
import {
  WEEKDAY_LABELS,
  buildMonth,
  monthGridRange,
  monthLabel,
  summariseMonth,
  type CalendarDay,
} from './calendarModel';

/** How a day reads at a glance. Meaning first, colour second. */
interface DayTone {
  tone: StatusTone | 'blank';
  label: string;
}

const KIND_TONE: Record<CalendarDay['kind'], DayTone> = {
  BEFORE_JOINING: { tone: 'blank', label: 'Before joining' },
  WEEK_OFF: { tone: 'blank', label: 'Week off' },
  HOLIDAY: { tone: 'pending', label: 'Holiday' },
  FILED: { tone: 'neutral', label: 'Logged' },
  MISSED: { tone: 'critical', label: 'Not filed' },
  FUTURE: { tone: 'blank', label: 'Upcoming' },
};

/** A filed day is coloured by where it has got to, not merely that it exists. */
const STATUS_TONE: Record<string, DayTone> = {
  DRAFT: { tone: 'neutral', label: 'Draft' },
  SUBMITTED: { tone: 'active', label: 'Submitted' },
  UNDER_REVIEW: { tone: 'active', label: 'Under review' },
  APPROVED: { tone: 'success', label: 'Approved' },
  REJECTED: { tone: 'critical', label: 'Rejected' },
  MODIFICATION_REQUESTED: { tone: 'pending', label: 'Changes asked' },
};

const LEGEND: { tone: StatusTone | 'blank'; label: string }[] = [
  { tone: 'success', label: 'Approved' },
  { tone: 'active', label: 'Awaiting review' },
  { tone: 'neutral', label: 'Draft' },
  { tone: 'pending', label: 'Holiday / changes asked' },
  { tone: 'critical', label: 'Not filed' },
  { tone: 'blank', label: 'Week off or upcoming' },
];

export function CalendarPage() {
  const theme = useTheme();
  const navigate = useNavigate();
  const { user, can } = useAuth();
  const today = todayIso();

  const [cursor, setCursor] = useState(() => {
    const now = new Date();
    return { year: now.getFullYear(), month: now.getMonth() };
  });
  // Null means "my own calendar"; the backend still enforces the access scope.
  const [subjectId, setSubjectId] = useState<number | null>(null);

  const canSeeOthers = can('WORKLOG_READ_TEAM') && can('EMPLOYEE_READ');
  const viewingSelf = subjectId === null;
  const range = useMemo(() => monthGridRange(cursor.year, cursor.month), [cursor]);

  const settings = useQuery({
    queryKey: ['settings', 'company'],
    queryFn: () => organisationApi.settings(),
    staleTime: 5 * 60 * 1000,
  });

  // The grid can straddle a year boundary in January and December, so both
  // years are fetched rather than silently losing a holiday in the padding.
  const years = useMemo(() => {
    const first = Number(range.from.slice(0, 4));
    const last = Number(range.to.slice(0, 4));
    return first === last ? [first] : [first, last];
  }, [range]);

  const holidays = useQuery({
    queryKey: ['settings', 'holidays', years],
    queryFn: async () => (await Promise.all(years.map((year) => organisationApi.holidays(year)))).flat(),
    staleTime: 5 * 60 * 1000,
  });

  const summaries = useQuery({
    queryKey: ['worklog', 'range', range.from, range.to, subjectId],
    queryFn: () => workLogApi.range(range.from, range.to, subjectId ?? undefined),
  });

  const subject = useQuery({
    queryKey: ['employee', subjectId ?? 'me'],
    queryFn: () => (subjectId === null ? employeeApi.me() : employeeApi.get(subjectId)),
  });

  const people = useQuery({
    queryKey: ['employees', 'options'],
    queryFn: () => employeeApi.options(),
    enabled: canSeeOthers,
    staleTime: 5 * 60 * 1000,
  });

  const days = useMemo(
    () =>
      buildMonth({
        year: cursor.year,
        month: cursor.month,
        today,
        workingDays: settings.data?.workingDays ?? 'MON,TUE,WED,THU,FRI',
        holidays: holidays.data ?? [],
        summaries: summaries.data ?? [],
        joiningDate: subject.data?.joiningDate ?? null,
      }),
    [cursor, today, settings.data, holidays.data, summaries.data, subject.data],
  );

  const totals = useMemo(
    () => summariseMonth(days, settings.data?.netWorkingMinutes ?? 480),
    [days, settings.data],
  );

  const loading = settings.isLoading || summaries.isLoading || subject.isLoading;
  const failed = settings.isError || summaries.isError || subject.isError;
  const palette = theme.palette.mode === 'light' ? statusTone : statusToneDark;

  function step(months: number) {
    setCursor((current) => {
      const moved = new Date(current.year, current.month + months, 1);
      return { year: moved.getFullYear(), month: moved.getMonth() };
    });
  }

  function openDay(day: CalendarDay) {
    // Only your own log is editable, so only your own day is worth opening.
    if (viewingSelf && day.kind !== 'BEFORE_JOINING') {
      navigate(`/worklogs?date=${day.date}`);
    }
  }

  const onCurrentMonth = cursor.year === new Date().getFullYear() && cursor.month === new Date().getMonth();

  return (
    <Box>
      <PageHeader
        eyebrow="Work tracking"
        title="Calendar"
        description={
          viewingSelf
            ? 'Your month at a glance: what is filed, what is still a draft, and which working days have nothing against them. Pick a day to open its log.'
            : 'Read-only view of a colleague’s month, within your access scope.'
        }
        actions={
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            {canSeeOthers && (
              <TextField
                select
                size="small"
                label="Whose calendar"
                value={subjectId ?? 'me'}
                onChange={(event) =>
                  setSubjectId(event.target.value === 'me' ? null : Number(event.target.value))
                }
                sx={{ minWidth: 220 }}
              >
                <MenuItem value="me">{user?.fullName ? `${user.fullName} (me)` : 'Me'}</MenuItem>
                {(people.data ?? [])
                  .filter((option) => option.id !== user?.employeeId)
                  .map((option) => (
                    <MenuItem key={option.id} value={option.id}>
                      {option.label}
                    </MenuItem>
                  ))}
              </TextField>
            )}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <IconButton onClick={() => step(-1)} aria-label="Previous month" size="small">
                <ChevronLeftOutlined fontSize="small" />
              </IconButton>
              <Typography variant="subtitle2" sx={{ minWidth: 150, textAlign: 'center' }}>
                {monthLabel(cursor.year, cursor.month)}
              </Typography>
              <IconButton onClick={() => step(1)} aria-label="Next month" size="small">
                <ChevronRightOutlined fontSize="small" />
              </IconButton>
            </Box>
            <Button
              variant="outlined"
              size="small"
              disabled={onCurrentMonth}
              onClick={() => setCursor({ year: new Date().getFullYear(), month: new Date().getMonth() })}
            >
              This month
            </Button>
          </Box>
        }
      />

      {failed && (
        <Alert severity="error" sx={{ mb: 4 }}>
          The calendar could not be loaded. Refresh the page, or try a different month.
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
          label="Days logged"
          value={totals.filedDays}
          unit={`/ ${totals.workingDays} working days`}
          caption={
            totals.workingDays
              ? `${Math.round((totals.filedDays / totals.workingDays) * 100)}% of the month accounted for`
              : 'No working days this month'
          }
          icon={EventNoteOutlined}
          progress={totals.workingDays ? (totals.filedDays / totals.workingDays) * 100 : 0}
        />
        <StatTile
          label="Hours logged"
          value={toHours(totals.loggedMinutes)}
          unit={`/ ${toHours(totals.expectedMinutes)} hrs`}
          caption={`${formatDuration(totals.productiveMinutes)} of it productive`}
          icon={TrendingUpOutlined}
          progress={totals.expectedMinutes ? (totals.loggedMinutes / totals.expectedMinutes) * 100 : 0}
        />
        <StatTile
          label="Approved"
          value={totals.approvedDays}
          unit="days"
          caption={
            totals.awaitingDays
              ? `${totals.awaitingDays} still awaiting review`
              : 'Nothing waiting on a reviewer'
          }
          icon={CheckCircleOutlined}
          progress={totals.filedDays ? (totals.approvedDays / totals.filedDays) * 100 : 0}
          progressColor="success"
        />
        <StatTile
          label="Not filed"
          value={totals.missedDays}
          unit={totals.missedDays === 1 ? 'day' : 'days'}
          caption={
            totals.missedDays
              ? 'Working days that have passed with nothing recorded'
              : 'Every working day so far is accounted for'
          }
          icon={EventBusyOutlined}
          progress={totals.workingDays ? (totals.missedDays / totals.workingDays) * 100 : 0}
          progressColor={totals.missedDays ? 'error' : 'success'}
        />
      </Box>

      <Card>
        <CardContent sx={{ p: 4, '&:last-child': { pb: 4 } }}>
          {loading ? (
            <TableSkeleton rows={6} columns={7} />
          ) : (
            <>
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(7, minmax(0, 1fr))',
                  gap: 1.5,
                  mb: 1.5,
                }}
              >
                {WEEKDAY_LABELS.map((label) => (
                  <Typography
                    key={label}
                    variant="overline"
                    sx={{ color: 'text.secondary', textAlign: 'center' }}
                  >
                    {label}
                  </Typography>
                ))}
              </Box>

              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(7, minmax(0, 1fr))',
                  gap: 1.5,
                }}
              >
                {days.map((day) => {
                  const descriptor =
                    day.summary != null
                      ? STATUS_TONE[day.summary.status] ?? KIND_TONE.FILED
                      : KIND_TONE[day.kind];
                  const colours = descriptor.tone === 'blank' ? null : palette[descriptor.tone];
                  const clickable = viewingSelf && day.kind !== 'BEFORE_JOINING';

                  return (
                    <Tooltip
                      key={day.date}
                      title={`${formatDayLabel(day.date)} — ${day.holidayName ?? descriptor.label}${
                        day.summary ? ` · ${formatDuration(day.summary.loggedMinutes)} logged` : ''
                      }`}
                      enterDelay={400}
                    >
                      <ButtonBase
                        onClick={() => openDay(day)}
                        disabled={!clickable}
                        focusRipple
                        sx={{
                          display: 'block',
                          textAlign: 'left',
                          width: '100%',
                          minHeight: 86,
                          p: 2,
                          borderRadius: 1.5,
                          border: 1,
                          borderColor: colours?.border ?? 'divider',
                          bgcolor: colours?.bg ?? 'transparent',
                          color: colours?.fg ?? 'text.secondary',
                          opacity: day.inMonth ? 1 : 0.35,
                          cursor: clickable ? 'pointer' : 'default',
                          outline: day.isToday ? `2px solid ${theme.palette.primary.main}` : 'none',
                          outlineOffset: -1,
                          transition: 'border-color 120ms, box-shadow 120ms',
                          '&:hover': clickable
                            ? { borderColor: 'primary.main', boxShadow: theme.shadows[1] }
                            : undefined,
                          '&.Mui-disabled': { opacity: day.inMonth ? 0.75 : 0.3 },
                        }}
                      >
                        <Typography
                          className="tabular"
                          sx={{
                            fontSize: 13,
                            fontWeight: day.isToday ? 700 : 500,
                            lineHeight: 1.2,
                            mb: 0.5,
                          }}
                        >
                          {day.dayOfMonth}
                        </Typography>

                        {day.summary ? (
                          <>
                            <Typography
                              className="tabular"
                              sx={{ fontSize: 15, fontWeight: 700, lineHeight: 1.1 }}
                            >
                              {toHours(day.summary.loggedMinutes)}
                              <Typography component="span" sx={{ fontSize: 11, fontWeight: 500, ml: 0.25 }}>
                                h
                              </Typography>
                            </Typography>
                            <Typography sx={{ fontSize: 10.5, opacity: 0.85, mt: 0.25 }}>
                              {descriptor.label}
                            </Typography>
                          </>
                        ) : (
                          <Typography sx={{ fontSize: 10.5, opacity: 0.85, wordBreak: 'break-word' }}>
                            {day.holidayName ?? (day.kind === 'FUTURE' ? '' : descriptor.label)}
                          </Typography>
                        )}
                      </ButtonBase>
                    </Tooltip>
                  );
                })}
              </Box>

              <Box
                sx={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 3,
                  mt: 4,
                  pt: 3,
                  borderTop: 1,
                  borderColor: 'divider',
                }}
              >
                {LEGEND.map((item) => (
                  <Box key={item.label} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Box
                      sx={{
                        width: 12,
                        height: 12,
                        borderRadius: 0.75,
                        border: 1,
                        borderColor: item.tone === 'blank' ? 'divider' : palette[item.tone].border,
                        bgcolor: item.tone === 'blank' ? 'transparent' : palette[item.tone].bg,
                      }}
                    />
                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                      {item.label}
                    </Typography>
                  </Box>
                ))}
              </Box>
            </>
          )}
        </CardContent>
      </Card>
    </Box>
  );
}
