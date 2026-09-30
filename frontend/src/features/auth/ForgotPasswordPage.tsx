import { useState, type FormEvent } from 'react';
import { Link as RouterLink } from 'react-router-dom';
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

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await authApi.forgotPassword(email.trim());
      setSent(true);
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
          Reset your password
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary', mb: 6 }}>
          Enter your work email and we will send a reset link if an account exists.
        </Typography>

        {sent ? (
          <Alert severity="success" sx={{ mb: 5 }}>
            If that address has an account, a reset link is on its way. The link expires in 30 minutes.
          </Alert>
        ) : (
          <Box component="form" onSubmit={handleSubmit}>
            {error && (
              <Alert severity="error" sx={{ mb: 4 }}>
                {error}
              </Alert>
            )}
            <TextField
              fullWidth
              required
              type="email"
              label="Work email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              sx={{ mb: 5 }}
            />
            <Button type="submit" fullWidth variant="contained" size="large" disabled={submitting}>
              {submitting ? 'Sending…' : 'Send reset link'}
            </Button>
          </Box>
        )}

        <Typography variant="body2" sx={{ mt: 6, textAlign: 'center' }}>
          <Link component={RouterLink} to="/login" underline="hover">
            Back to sign in
          </Link>
        </Typography>
      </Paper>
    </Box>
  );
}
