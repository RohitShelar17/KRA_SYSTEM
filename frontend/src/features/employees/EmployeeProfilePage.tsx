import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
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
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import ArrowBackOutlined from '@mui/icons-material/ArrowBackOutlined';
import SwapHorizOutlined from '@mui/icons-material/SwapHorizOutlined';
import { employeeApi } from '@/api/employees';
import { toErrorMessage } from '@/api/axiosClient';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { useAuth } from '@/contexts/AuthContext';

export function EmployeeProfilePage() {
  const { id } = useParams<{ id: string }>();
  const employeeId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { can } = useAuth();

  const [managerDialogOpen, setManagerDialogOpen] = useState(false);
  const [newManagerId, setNewManagerId] = useState<number | ''>('');
  const [effectiveFrom, setEffectiveFrom] = useState(new Date().toISOString().slice(0, 10));
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const employee = useQuery({
    queryKey: ['employees', employeeId],
    queryFn: () => employeeApi.get(employeeId),
    enabled: Number.isFinite(employeeId),
  });

  const team = useQuery({
    queryKey: ['employees', employeeId, 'team'],
    queryFn: () => employeeApi.team(employeeId),
    enabled: Number.isFinite(employeeId),
  });

  const history = useQuery({
    queryKey: ['employees', employeeId, 'reporting-history'],
    queryFn: () => employeeApi.reportingHistory(employeeId),
    enabled: Number.isFinite(employeeId),
  });

  const managers = useQuery({
    queryKey: ['employees', 'options'],
    queryFn: employeeApi.options,
    enabled: managerDialogOpen,
  });

  const changeManager = useMutation({
    mutationFn: () => employeeApi.changeManager(employeeId, Number(newManagerId), effectiveFrom, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['employees'] });
      setManagerDialogOpen(false);
      setReason('');
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  if (employee.isLoading) {
    return <FullPageLoader label="Loading profile" />;
  }
  if (employee.isError || !employee.data) {
    return <Alert severity="error">This employee could not be loaded, or is outside your access scope.</Alert>;
  }

  const person = employee.data;

  return (
    <Box>
      <Button
        startIcon={<ArrowBackOutlined sx={{ fontSize: 17 }} />}
        onClick={() => navigate('/employees')}
        sx={{ mb: 4 }}
      >
        Back to employees
      </Button>

      <PageHeader
        eyebrow={person.employeeCode}
        title={person.fullName}
        description={`${person.designationName ?? 'No designation'} · ${person.departmentName ?? 'No department'}`}
        actions={
          can('EMPLOYEE_MANAGE') ? (
            <Button
              variant="outlined"
              startIcon={<SwapHorizOutlined sx={{ fontSize: 18 }} />}
              onClick={() => setManagerDialogOpen(true)}
            >
              Change manager
            </Button>
          ) : undefined
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', lg: '1fr 2fr' } }}>
        <Card>
          <CardContent sx={{ p: 5 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 4, mb: 5 }}>
              <Avatar
                src={person.profileImageUrl ?? undefined}
                sx={{ width: 52, height: 52, fontSize: 18, bgcolor: 'primary.main' }}
              >
                {person.firstName[0]}
                {person.lastName[0]}
              </Avatar>
              <Box>
                <Typography variant="h5">{person.fullName}</Typography>
                <StatusChip status={person.status} />
              </Box>
            </Box>

            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              <Field label="Work email" value={person.email} />
              <Divider />
              <Field label="Phone" value={person.phone ?? '—'} />
              <Divider />
              <Field label="Team" value={person.teamName ?? '—'} />
              <Divider />
              <Field label="Grade" value={person.gradeLevel ?? '—'} />
              <Divider />
              <Field label="Reports to" value={person.reportingManagerName ?? 'No manager'} />
              <Divider />
              <Field label="Joined" value={formatDate(person.joiningDate)} />
              {person.exitDate && (
                <>
                  <Divider />
                  <Field label="Exit date" value={formatDate(person.exitDate)} />
                </>
              )}
              <Divider />
              <Field label="Employment" value={humanise(person.employmentType)} />
              <Divider />
              <Field
                label="Work mode"
                value={`${humanise(person.workMode)}${person.workLocation ? ` · ${person.workLocation}` : ''}`}
              />
              <Divider />
              <Field
                label="Shift"
                value={
                  person.shiftStartTime && person.shiftEndTime
                    ? `${trim(person.shiftStartTime)} – ${trim(person.shiftEndTime)}`
                    : 'Company default'
                }
              />
            </Box>

            <Divider sx={{ my: 5 }} />
            <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 2 }}>
              Access
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
              {person.roles.length === 0 ? (
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  No sign-in account
                </Typography>
              ) : (
                person.roles.map((role) => (
                  <Chip key={role} label={humanise(role)} size="small" variant="outlined" />
                ))
              )}
            </Box>
            {person.userStatus && (
              <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 2 }}>
                Account status: {humanise(person.userStatus)}
              </Typography>
            )}
          </CardContent>
        </Card>

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <Card>
            <CardContent sx={{ p: 5 }}>
              <Typography variant="h5" sx={{ mb: 0.5 }}>
                Direct reports
              </Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
                {person.directReportCount === 0
                  ? 'Nobody reports to this employee.'
                  : `${person.directReportCount} people report directly to ${person.firstName}.`}
              </Typography>

              {(team.data ?? []).length > 0 && (
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Designation</TableCell>
                      <TableCell>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(team.data ?? []).map((member) => (
                      <TableRow
                        key={member.id}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => navigate(`/employees/${member.id}`)}
                      >
                        <TableCell>
                          <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{member.fullName}</Typography>
                          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                            {member.employeeCode}
                          </Typography>
                        </TableCell>
                        <TableCell>{member.designationName ?? '—'}</TableCell>
                        <TableCell>
                          <StatusChip status={member.status} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent sx={{ p: 5 }}>
              <Typography variant="h5" sx={{ mb: 0.5 }}>
                Reporting history
              </Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
                Effective-dated lines, so a past appraisal still resolves the manager of that period.
              </Typography>

              {(history.data ?? []).length === 0 ? (
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  No reporting line recorded yet.
                </Typography>
              ) : (
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>Manager</TableCell>
                      <TableCell>Type</TableCell>
                      <TableCell>From</TableCell>
                      <TableCell>To</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(history.data ?? []).map((line) => (
                      <TableRow key={line.id}>
                        <TableCell>
                          {line.managerName ?? `#${line.managerId}`}
                          {line.current && (
                            <Chip label="current" size="small" color="primary" sx={{ ml: 2 }} />
                          )}
                        </TableCell>
                        <TableCell>{humanise(line.relationType)}</TableCell>
                        <TableCell className="tabular">{formatDate(line.validFrom)}</TableCell>
                        <TableCell className="tabular">
                          {line.validTo ? formatDate(line.validTo) : '—'}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </Box>
      </Box>

      <Dialog open={managerDialogOpen} onClose={() => setManagerDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>Change reporting manager</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 4, pt: 2 }}>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            The current line closes the day before the effective date, and a new one opens. Both stay in the
            history.
          </Typography>
          <TextField
            select
            required
            size="small"
            label="New manager"
            value={newManagerId}
            onChange={(event) => setNewManagerId(Number(event.target.value))}
          >
            {(managers.data ?? [])
              .filter((option) => option.id !== employeeId)
              .map((option) => (
                <MenuItem key={option.id} value={option.id}>
                  {option.label} {option.code ? `· ${option.code}` : ''}
                </MenuItem>
              ))}
          </TextField>
          <TextField
            required
            size="small"
            type="date"
            label="Effective from"
            value={effectiveFrom}
            onChange={(event) => setEffectiveFrom(event.target.value)}
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            size="small"
            label="Reason"
            multiline
            minRows={2}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            helperText="Stored in the audit trail"
          />
        </DialogContent>
        <DialogActions sx={{ px: 6, pb: 5 }}>
          <Button variant="outlined" onClick={() => setManagerDialogOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={!newManagerId || changeManager.isPending}
            onClick={() => changeManager.mutate()}
          >
            {changeManager.isPending ? 'Saving…' : 'Change manager'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 3, alignItems: 'baseline' }}>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        {label}
      </Typography>
      <Typography variant="subtitle2" sx={{ textAlign: 'right', wordBreak: 'break-word' }}>
        {value}
      </Typography>
    </Box>
  );
}

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

function trim(time: string): string {
  const [hour, minute] = time.split(':');
  return `${hour}:${minute}`;
}

function humanise(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}
