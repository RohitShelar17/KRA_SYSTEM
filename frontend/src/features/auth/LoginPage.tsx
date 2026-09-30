import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import LockOutlined from '@mui/icons-material/LockOutlined';
import MailOutline from '@mui/icons-material/MailOutline';
import ShieldOutlined from '@mui/icons-material/ShieldOutlined';
import VisibilityOffOutlined from '@mui/icons-material/VisibilityOffOutlined';
import VisibilityOutlined from '@mui/icons-material/VisibilityOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { BrandMark } from '@/components/BrandMark';
import { useAuth } from '@/contexts/AuthContext';
import { brand } from '@/theme/tokens';

const HIGHLIGHTS = [
  {
    title: 'Daily timeline tracking',
    body: 'Continuous timesheet capture with overlap prevention and unallocated-hour detection.',
  },
  {
    title: 'Weighted KRA & KPI scoring',
    body: 'Configurable formulas combining task completion, productive hours and manager ratings.',
  },
  {
    title: 'Four-stage appraisal governance',
    body: 'Self assessment, manager review, department head sign-off and an HR freeze lock.',
  },
];

export function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { signIn } = useAuth();

  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState<string | null>(
    params.get('expired') ? 'Your session expired. Sign in again to continue.' : null,
  );
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const user = await signIn(identifier.trim(), password, rememberMe);
      navigate(user.mustChangePassword ? '/profile?changePassword=1' : '/dashboard', { replace: true });
    } catch (caught) {
      setError(toErrorMessage(caught, 'Sign in failed. Check your credentials and try again.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Box
        component="header"
        sx={{
          px: { xs: 5, lg: 10 },
          py: 3.5,
          borderBottom: 1,
          borderColor: 'divider',
          bgcolor: 'background.paper',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 4,
        }}
      >
        <BrandMark subtitle="Enterprise" />
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <Box sx={{ display: { xs: 'none', md: 'flex' }, alignItems: 'center', gap: 2 }}>
            <ShieldOutlined sx={{ fontSize: 16, color: 'primary.main' }} />
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              Role-based access · Full audit trail
            </Typography>
          </Box>
        </Box>
      </Box>

      <Box
        component="main"
        sx={{ flex: 1, display: 'grid', placeItems: 'center', px: { xs: 4, sm: 6 }, py: { xs: 6, md: 10 } }}
      >
        <Paper
          sx={{
            width: '100%',
            maxWidth: 1040,
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
            overflow: 'hidden',
            borderRadius: 3,
          }}
        >
          <Box
            component="form"
            onSubmit={handleSubmit}
            sx={{ p: { xs: 6, sm: 10 }, display: 'flex', flexDirection: 'column' }}
          >
            <Chip
              label="Company sign-in"
              size="small"
              sx={{ alignSelf: 'flex-start', mb: 4, bgcolor: 'action.hover', color: 'primary.main' }}
            />
            <Typography variant="h3" sx={{ mb: 2 }}>
              Sign in to your workplace
            </Typography>
            <Typography variant="body2" sx={{ color: 'text.secondary', mb: 6 }}>
              Use the work email your administrator registered for you.
            </Typography>

            {error && (
              <Alert severity="error" sx={{ mb: 5 }} onClose={() => setError(null)}>
                {error}
              </Alert>
            )}

            <Typography variant="subtitle2" sx={{ mb: 1.5 }}>
              Work email or employee ID
            </Typography>
            <TextField
              fullWidth
              autoFocus
              required
              autoComplete="username"
              placeholder="name@company.com"
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <MailOutline sx={{ fontSize: 17, color: 'text.disabled' }} />
                  </InputAdornment>
                ),
              }}
              sx={{ mb: 5 }}
            />

            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 1.5 }}>
              <Typography variant="subtitle2">Password</Typography>
              <Link component={RouterLink} to="/forgot-password" variant="caption" underline="hover">
                Forgot password?
              </Link>
            </Box>
            <TextField
              fullWidth
              required
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LockOutlined sx={{ fontSize: 17, color: 'text.disabled' }} />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      size="small"
                      onClick={() => setShowPassword((value) => !value)}
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                    >
                      {showPassword ? (
                        <VisibilityOffOutlined sx={{ fontSize: 17 }} />
                      ) : (
                        <VisibilityOutlined sx={{ fontSize: 17 }} />
                      )}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />

            <FormControlLabel
              sx={{ mt: 3, mb: 5 }}
              control={
                <Checkbox
                  size="small"
                  checked={rememberMe}
                  onChange={(event) => setRememberMe(event.target.checked)}
                />
              }
              label={<Typography variant="body2">Keep me signed in on this device</Typography>}
            />

            <Button type="submit" variant="contained" size="large" disabled={submitting} fullWidth>
              {submitting ? 'Signing in…' : 'Sign in to dashboard'}
            </Button>

            <Divider sx={{ my: 6 }} />
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              Protected by encrypted transport. Repeated failed attempts lock the account temporarily.
            </Typography>
          </Box>

          <Box
            sx={{
              p: { xs: 6, sm: 10 },
              color: '#EEF0FF',
              background: `linear-gradient(155deg, ${brand.indigo700} 0%, ${brand.indigo900} 62%, #241F63 100%)`,
              display: 'flex',
              flexDirection: 'column',
              gap: 5,
            }}
          >
            <Chip
              label="KRA & performance cloud"
              size="small"
              sx={{
                alignSelf: 'flex-start',
                bgcolor: 'rgba(255,255,255,0.12)',
                color: '#EEF0FF',
                border: '1px solid rgba(255,255,255,0.18)',
              }}
            />
            <Typography variant="h3" sx={{ color: '#FFFFFF', maxWidth: 340 }}>
              Streamlining daily productivity and objective appraisals
            </Typography>
            <Typography variant="body2" sx={{ color: 'rgba(238,240,255,0.78)', maxWidth: 380 }}>
              Real-time work logging, weighted KRA scorecards and multi-stage appraisal cycles for
              high-performance enterprise teams.
            </Typography>

            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mt: 'auto' }}>
              {HIGHLIGHTS.map((item) => (
                <Box
                  key={item.title}
                  sx={{
                    p: 4,
                    borderRadius: 2,
                    bgcolor: 'rgba(255,255,255,0.08)',
                    border: '1px solid rgba(255,255,255,0.12)',
                  }}
                >
                  <Typography sx={{ fontSize: 13.5, fontWeight: 600, color: '#FFFFFF', mb: 0.5 }}>
                    {item.title}
                  </Typography>
                  <Typography variant="caption" sx={{ color: 'rgba(238,240,255,0.72)' }}>
                    {item.body}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Box>
        </Paper>
      </Box>
    </Box>
  );
}
