import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Switch from '@mui/material/Switch';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import DeleteOutlineOutlined from '@mui/icons-material/DeleteOutlineOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { organisationApi } from '@/api/organisation';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import type { CompanySettings, HolidayType } from '@/types/domain';

const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
const HOLIDAY_TYPES: HolidayType[] = ['PUBLIC', 'RESTRICTED', 'COMPANY', 'WEEK_OFF'];

/**
 * Everything the Phase 2 timesheet validator will read. Editing these changes
 * how work logs are validated, so the page states that plainly.
 */
export function CompanySettingsPage() {
  const queryClient = useQueryClient();
  const year = new Date().getFullYear();

  const [form, setForm] = useState<CompanySettings | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [holiday, setHoliday] = useState({
    holidayDate: '',
    name: '',
    holidayType: 'PUBLIC' as HolidayType,
  });

  const settings = useQuery({ queryKey: ['settings', 'company'], queryFn: organisationApi.settings });
  const holidays = useQuery({
    queryKey: ['settings', 'holidays', year],
    queryFn: () => organisationApi.holidays(year),
  });

  useEffect(() => {
    if (settings.data) {
      setForm(settings.data);
    }
  }, [settings.data]);

  const save = useMutation({
    mutationFn: (payload: CompanySettings) =>
      organisationApi.updateSettings({
        workStartTime: normaliseTime(payload.workStartTime),
        workEndTime: normaliseTime(payload.workEndTime),
        breakMinutes: payload.breakMinutes,
        workingDays: payload.workingDays,
        worklogApprovalEnabled: payload.worklogApprovalEnabled,
        overtimeAllowed: payload.overtimeAllowed,
        maxOvertimeMinutes: payload.maxOvertimeMinutes,
        backdateWindowDays: payload.backdateWindowDays,
        worklogCutoffTime: normaliseTime(payload.worklogCutoffTime),
        minSubmissionRatePct: payload.minSubmissionRatePct,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
      setSaved(true);
      setError(null);
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const addHoliday = useMutation({
    mutationFn: () => organisationApi.addHoliday(holiday),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['settings', 'holidays'] });
      setHoliday({ holidayDate: '', name: '', holidayType: 'PUBLIC' });
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const removeHoliday = useMutation({
    mutationFn: (id: number) => organisationApi.removeHoliday(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['settings', 'holidays'] }),
  });

  if (settings.isLoading || !form) {
    return <FullPageLoader label="Loading settings" />;
  }

  const workingDays = form.workingDays.split(',').filter(Boolean);

  function toggleDay(day: string) {
    if (!form) {
      return;
    }
    const next = workingDays.includes(day)
      ? workingDays.filter((value) => value !== day)
      : [...DAYS.filter((value) => workingDays.includes(value) || value === day)];
    setForm({ ...form, workingDays: next.join(',') });
  }

  return (
    <Box>
      <PageHeader
        eyebrow="Administration"
        title="Company settings"
        description="Working hours, overtime and submission policy. The daily work log validates against these values."
        actions={
          <Button variant="contained" onClick={() => save.mutate(form)} disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save settings'}
          </Button>
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      {saved && !error && (
        <Alert severity="success" sx={{ mb: 4 }} onClose={() => setSaved(false)}>
          Settings saved. New work log entries validate against them immediately.
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' } }}>
        <Card>
          <CardContent sx={{ p: 5 }}>
            <Typography variant="h5" sx={{ mb: 4 }}>
              Working hours
            </Typography>
            <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr' }}>
              <TextField
                label="Shift start"
                type="time"
                size="small"
                value={normaliseTime(form.workStartTime)}
                onChange={(event) => setForm({ ...form, workStartTime: event.target.value })}
                InputLabelProps={{ shrink: true }}
              />
              <TextField
                label="Shift end"
                type="time"
                size="small"
                value={normaliseTime(form.workEndTime)}
                onChange={(event) => setForm({ ...form, workEndTime: event.target.value })}
                InputLabelProps={{ shrink: true }}
              />
              <TextField
                label="Break (minutes)"
                type="number"
                size="small"
                value={form.breakMinutes}
                onChange={(event) => setForm({ ...form, breakMinutes: Number(event.target.value) })}
              />
              <TextField
                label="Work log cut-off"
                type="time"
                size="small"
                value={normaliseTime(form.worklogCutoffTime)}
                onChange={(event) => setForm({ ...form, worklogCutoffTime: event.target.value })}
                InputLabelProps={{ shrink: true }}
                helperText="Reminders fire after this time"
              />
            </Box>

            <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mt: 5, mb: 2 }}>
              Working days
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
              {DAYS.map((day) => {
                const active = workingDays.includes(day);
                return (
                  <Chip
                    key={day}
                    label={day}
                    onClick={() => toggleDay(day)}
                    variant={active ? 'filled' : 'outlined'}
                    color={active ? 'primary' : 'default'}
                    sx={{ cursor: 'pointer' }}
                  />
                );
              })}
            </Box>

            <Divider sx={{ my: 5 }} />
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              Contracted time per day:{' '}
              <strong>{((netMinutes(form) || 0) / 60).toFixed(1)} hours</strong> after the break.
            </Typography>
          </CardContent>
        </Card>

        <Card>
          <CardContent sx={{ p: 5 }}>
            <Typography variant="h5" sx={{ mb: 4 }}>
              Work log policy
            </Typography>

            <FormControlLabel
              control={
                <Switch
                  checked={form.worklogApprovalEnabled}
                  onChange={(event) =>
                    setForm({ ...form, worklogApprovalEnabled: event.target.checked })
                  }
                />
              }
              label={
                <Box>
                  <Typography variant="subtitle2">Manager approval required</Typography>
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    When off, a submitted day is final and the score skips the manager component.
                  </Typography>
                </Box>
              }
              sx={{ alignItems: 'flex-start', mb: 4 }}
            />

            <FormControlLabel
              control={
                <Switch
                  checked={form.overtimeAllowed}
                  onChange={(event) => setForm({ ...form, overtimeAllowed: event.target.checked })}
                />
              }
              label={
                <Box>
                  <Typography variant="subtitle2">Allow overtime</Typography>
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    Employees may log beyond contracted hours up to the cap below.
                  </Typography>
                </Box>
              }
              sx={{ alignItems: 'flex-start', mb: 5 }}
            />

            <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr' }}>
              <TextField
                label="Max overtime (minutes)"
                type="number"
                size="small"
                disabled={!form.overtimeAllowed}
                value={form.maxOvertimeMinutes}
                onChange={(event) => setForm({ ...form, maxOvertimeMinutes: Number(event.target.value) })}
              />
              <TextField
                label="Back-dating window (days)"
                type="number"
                size="small"
                value={form.backdateWindowDays}
                onChange={(event) => setForm({ ...form, backdateWindowDays: Number(event.target.value) })}
                helperText="Beyond this, a manager reopens the date on Team work logs"
              />
              <TextField
                label="Minimum submission rate (%)"
                type="number"
                size="small"
                sx={{ gridColumn: '1 / -1' }}
                value={form.minSubmissionRatePct}
                onChange={(event) =>
                  setForm({ ...form, minSubmissionRatePct: Number(event.target.value) })
                }
                helperText="Below this, the monthly score applies the consistency penalty"
              />
            </Box>
          </CardContent>
        </Card>
      </Box>

      <Card sx={{ mt: 4 }}>
        <CardContent sx={{ p: 5 }}>
          <Typography variant="h5" sx={{ mb: 0.5 }}>
            Holiday calendar {year}
          </Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
            Days excluded from expected working hours and from the monthly score denominator.
          </Typography>

          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3, mb: 5 }}>
            <TextField
              label="Date"
              type="date"
              size="small"
              value={holiday.holidayDate}
              onChange={(event) => setHoliday({ ...holiday, holidayDate: event.target.value })}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              label="Name"
              size="small"
              value={holiday.name}
              onChange={(event) => setHoliday({ ...holiday, name: event.target.value })}
              sx={{ minWidth: 220 }}
            />
            <TextField
              select
              label="Type"
              size="small"
              value={holiday.holidayType}
              onChange={(event) =>
                setHoliday({ ...holiday, holidayType: event.target.value as HolidayType })
              }
              sx={{ minWidth: 160 }}
            >
              {HOLIDAY_TYPES.map((type) => (
                <MenuItem key={type} value={type}>
                  {type.toLowerCase().replace('_', ' ')}
                </MenuItem>
              ))}
            </TextField>
            <Button
              variant="outlined"
              startIcon={<AddOutlined sx={{ fontSize: 18 }} />}
              disabled={!holiday.holidayDate || !holiday.name.trim() || addHoliday.isPending}
              onClick={() => addHoliday.mutate()}
            >
              Add holiday
            </Button>
          </Box>

          {(holidays.data ?? []).length === 0 ? (
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              No holidays recorded for {year}.
            </Typography>
          ) : (
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Day</TableCell>
                  <TableCell>Holiday</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {(holidays.data ?? []).map((row) => (
                  <TableRow key={row.id} hover>
                    <TableCell className="tabular">
                      {new Date(row.holidayDate).toLocaleDateString('en-GB', {
                        day: '2-digit',
                        month: 'short',
                        year: 'numeric',
                      })}
                    </TableCell>
                    <TableCell>{row.dayOfWeek}</TableCell>
                    <TableCell>{row.name}</TableCell>
                    <TableCell sx={{ textTransform: 'capitalize' }}>
                      {row.holidayType.toLowerCase().replace('_', ' ')}
                    </TableCell>
                    <TableCell align="right">
                      <IconButton size="small" onClick={() => removeHoliday.mutate(row.id)}>
                        <DeleteOutlineOutlined sx={{ fontSize: 17 }} />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </Box>
  );
}

function normaliseTime(value: string): string {
  const [hour = '00', minute = '00'] = value.split(':');
  return `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`;
}

function netMinutes(settings: CompanySettings): number {
  const start = toMinutes(settings.workStartTime);
  const end = toMinutes(settings.workEndTime);
  return Math.max(end - start - settings.breakMinutes, 0);
}

function toMinutes(value: string): number {
  const [hour, minute] = value.split(':').map(Number);
  return hour * 60 + (minute || 0);
}
