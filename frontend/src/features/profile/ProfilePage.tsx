import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import LinearProgress from '@mui/material/LinearProgress';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { authApi } from '@/api/auth';
import { toErrorMessage } from '@/api/axiosClient';
import { employeeApi } from '@/api/employees';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { useAuth } from '@/contexts/AuthContext';
import { PASSWORD_RULE_TEXT, isStrongPassword, passwordStrength } from '@/utils/password';

export function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const [params] = useSearchParams();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const employee = useQuery({
    queryKey: ['employees', 'me'],
    queryFn: employeeApi.me,
    enabled: Boolean(user?.employeeId),
    retry: false,
  });

  useEffect(() => {
    if (params.get('changePassword')) {
      document.getElementById('change-password')?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [params]);

  const changePassword = useMutation({
    mutationFn: () => authApi.changePassword(currentPassword, newPassword),
    onSuccess: async () => {
      setDone(true);
      setError(null);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      await refreshUser();
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (newPassword !== confirmPassword) {
      setError('The two passwords do not match');
      return;
    }
    if (!isStrongPassword(newPassword)) {
      setError(PASSWORD_RULE_TEXT);
      return;
    }
    changePassword.mutate();
  }

  const strength = passwordStrength(newPassword);

  return (
    <Box>
      <PageHeader eyebrow="Account" title="My profile" description="Your record and sign-in settings." />

      {user?.mustChangePassword && (
        <Alert severity="warning" sx={{ mb: 4 }}>
          Your administrator set a temporary password. Choose your own before continuing.
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' } }}>
        <Card>
          <CardContent sx={{ p: 5 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 4, mb: 5 }}>
              <Avatar
                src={user?.profileImageUrl ?? undefined}
                sx={{ width: 52, height: 52, fontSize: 18, bgcolor: 'primary.main' }}
              >
                {(user?.fullName ?? '?')
                  .split(' ')
                  .slice(0, 2)
                  .map((part) => part[0])
                  .join('')}
              </Avatar>
              <Box>
                <Typography variant="h5">{user?.fullName}</Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  {user?.designation ?? 'No designation'}
                  {user?.employeeCode ? ` · ${user.employeeCode}` : ''}
                </Typography>
              </Box>
            </Box>

            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              <Row label="Work email" value={user?.email ?? '—'} />
              <Divider />
              <Row label="Phone" value={user?.phone ?? employee.data?.phone ?? '—'} />
              <Divider />
              <Row label="Department" value={user?.departmentName ?? '—'} />
              <Divider />
              <Row label="Team" value={user?.teamName ?? '—'} />
              <Divider />
              <Row label="Reports to" value={user?.reportingManagerName ?? 'No manager'} />
              {employee.data && (
                <>
                  <Divider />
                  <Row label="Joined" value={formatDate(employee.data.joiningDate)} />
                  <Divider />
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                      Status
                    </Typography>
                    <StatusChip status={employee.data.status} />
                  </Box>
                </>
              )}
            </Box>

            <Divider sx={{ my: 5 }} />
            <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 2 }}>
              Roles · {user?.dataScope.toLowerCase()} scope
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
              {(user?.roles ?? []).map((role) => (
                <Chip key={role} label={role.toLowerCase().replace(/_/g, ' ')} size="small" variant="outlined" />
              ))}
            </Box>

            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 4 }}>
              {user?.permissions.length ?? 0} permissions granted. Contact your administrator to change these.
            </Typography>
          </CardContent>
        </Card>

        <Card id="change-password">
          <CardContent sx={{ p: 5 }}>
            <Typography variant="h5" sx={{ mb: 0.5 }}>
              Change password
            </Typography>
            <Typography variant="body2" sx={{ color: 'text.secondary', mb: 5 }}>
              {PASSWORD_RULE_TEXT} All other sessions are signed out when you change it.
            </Typography>

            {error && (
              <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
                {error}
              </Alert>
            )}
            {done && (
              <Alert severity="success" sx={{ mb: 4 }} onClose={() => setDone(false)}>
                Password updated.
              </Alert>
            )}

            <Box component="form" onSubmit={submit} sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <TextField
                required
                size="small"
                type="password"
                label="Current password"
                autoComplete="current-password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
              />
              <Box>
                <TextField
                  required
                  fullWidth
                  size="small"
                  type="password"
                  label="New password"
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                />
                {newPassword && (
                  <Box sx={{ mt: 2 }}>
                    <LinearProgress
                      variant="determinate"
                      value={(strength / 4) * 100}
                      color={strength >= 4 ? 'success' : strength >= 2 ? 'warning' : 'error'}
                    />
                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                      {strength >= 4 ? 'Meets the policy' : 'Does not meet the policy yet'}
                    </Typography>
                  </Box>
                )}
              </Box>
              <TextField
                required
                size="small"
                type="password"
                label="Confirm new password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
              <Button
                type="submit"
                variant="contained"
                sx={{ alignSelf: 'flex-start' }}
                disabled={changePassword.isPending}
              >
                {changePassword.isPending ? 'Updating…' : 'Update password'}
              </Button>
            </Box>
          </CardContent>
        </Card>
      </Box>
    </Box>
  );
}

function Row({ label, value }: { label: string; value: string }) {
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
