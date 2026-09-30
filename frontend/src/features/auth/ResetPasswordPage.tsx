import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { authApi } from '@/api/auth';
import { toErrorMessage } from '@/api/axiosClient';
import { BrandMark } from '@/components/BrandMark';
import { PASSWORD_RULE_TEXT, isStrongPassword } from '@/utils/password';

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token') ?? '';

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (password !== confirm) {
      setError('The two passwords do not match');
      return;
    }
    if (!isStrongPassword(password)) {
      setError(PASSWORD_RULE_TEXT);
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await authApi.resetPassword(token, password);
      navigate('/login?reset=1', { replace: true });
    } catch (caught) {
      setError(toErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', px: 4, py: 10 }}>
      <Paper sx={{ width: '100%', maxWidth: 430, p: 8 }}>
        <Box sx={{ mb: 6 }}>
          <BrandMark subtitle="Enterprise" />
        </Box>
        <Typography variant="h4" sx={{ mb: 2 }}>
          Choose a new password
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary', mb: 6 }}>
          {PASSWORD_RULE_TEXT}
        </Typography>

        {!token && (
          <Alert severity="warning" sx={{ mb: 5 }}>
            This link is missing its reset token. Request a new link from the sign-in page.
          </Alert>
        )}

        <Box component="form" onSubmit={handleSubmit}>
          {error && (
            <Alert severity="error" sx={{ mb: 4 }}>
              {error}
            </Alert>
          )}
          <TextField
            fullWidth
            required
            type="password"
            label="New password"
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            sx={{ mb: 4 }}
          />
          <TextField
            fullWidth
            required
            type="password"
            label="Confirm new password"
            autoComplete="new-password"
            value={confirm}
            onChange={(event) => setConfirm(event.target.value)}
            sx={{ mb: 5 }}
          />
          <Button type="submit" fullWidth variant="contained" size="large" disabled={submitting || !token}>
            {submitting ? 'Updating…' : 'Update password'}
          </Button>
        </Box>

        <Typography variant="body2" sx={{ mt: 6, textAlign: 'center' }}>
          <Link component={RouterLink} to="/login" underline="hover">
            Back to sign in
          </Link>
        </Typography>
      </Paper>
    </Box>
  );
}
