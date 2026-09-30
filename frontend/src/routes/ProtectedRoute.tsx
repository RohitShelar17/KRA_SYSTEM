import { Navigate, Outlet, useLocation } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import { FullPageLoader } from '@/components/LoadingState';
import { useAuth } from '@/contexts/AuthContext';

interface Props {
  /** Any one of these permissions admits the caller. Empty means any signed-in user. */
  permissions?: string[];
}

/**
 * The guard mirrors the backend check rather than replacing it: hiding a screen
 * is a courtesy, the API is what actually enforces access.
 */
export function ProtectedRoute({ permissions = [] }: Props) {
  const { user, initialising, canAny } = useAuth();
  const location = useLocation();

  if (initialising) {
    return <FullPageLoader label="Checking your session" />;
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (!canAny(permissions)) {
    return (
      <Box sx={{ py: 10 }}>
        <Alert severity="warning">
          You do not have permission to open this screen. Ask your administrator if you need access.
        </Alert>
      </Box>
    );
  }

  return <Outlet />;
}

export function PublicOnlyRoute() {
  const { user, initialising } = useAuth();
  if (initialising) {
    return <FullPageLoader label="Loading" />;
  }
  return user ? <Navigate to="/dashboard" replace /> : <Outlet />;
}
