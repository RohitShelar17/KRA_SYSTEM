import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Badge from '@mui/material/Badge';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import NotificationsNoneOutlined from '@mui/icons-material/NotificationsNoneOutlined';
import { notificationApi } from '@/api/insight';
import { useAuth } from '@/contexts/AuthContext';
import type { AppNotification, NotificationSeverity } from '@/types/insight';

const DOT: Record<NotificationSeverity, string> = {
  INFO: 'info.main',
  SUCCESS: 'success.main',
  WARNING: 'warning.main',
  CRITICAL: 'error.main',
};

/**
 * The bell in the top bar.
 *
 * <p>Polls rather than holding a socket: a count that is a minute stale costs
 * nothing, and the page stays honest if the connection drops.</p>
 */
export function NotificationBell() {
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);

  const enabled = can('NOTIFICATION_READ');

  const count = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: notificationApi.unreadCount,
    enabled,
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  });

  const feed = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () => notificationApi.feed(false, 0, 8),
    enabled: enabled && anchor !== null,
  });

  const markRead = useMutation({
    mutationFn: (ids: number[]) => notificationApi.markRead(ids),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const markAll = useMutation({
    mutationFn: notificationApi.markAllRead,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  if (!enabled) {
    return null;
  }

  const unread = count.data?.unread ?? 0;
  const rows = feed.data?.content ?? [];

  function open(notification: AppNotification) {
    if (!notification.read) {
      markRead.mutate([notification.id]);
    }
    setAnchor(null);
  }

  return (
    <>
      <Tooltip title={unread > 0 ? `${unread} unread` : 'Notifications'}>
        <IconButton onClick={(event) => setAnchor(event.currentTarget)} aria-label="Notifications">
          <Badge badgeContent={unread} color="error" max={99}>
            <NotificationsNoneOutlined sx={{ fontSize: 20 }} />
          </Badge>
        </IconButton>
      </Tooltip>

      <Menu
        anchorEl={anchor}
        open={anchor !== null}
        onClose={() => setAnchor(null)}
        slotProps={{ paper: { sx: { width: 380, maxHeight: 480 } } }}
      >
        <Box sx={{ px: 3, py: 2, display: 'flex', justifyContent: 'space-between', gap: 2 }}>
          <Typography variant="subtitle2">Notifications</Typography>
          {unread > 0 && (
            <Button size="small" onClick={() => markAll.mutate()} disabled={markAll.isPending}>
              Mark all read
            </Button>
          )}
        </Box>
        <Divider />

        {rows.length === 0 && (
          <Box sx={{ px: 3, py: 5, textAlign: 'center' }}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {feed.isLoading ? 'Loading…' : 'Nothing here yet.'}
            </Typography>
          </Box>
        )}

        {rows.map((notification) => (
          <MenuItem
            key={notification.id}
            component={notification.link ? RouterLink : 'li'}
            to={notification.link ?? undefined}
            onClick={() => open(notification)}
            sx={{
              alignItems: 'flex-start',
              gap: 2,
              py: 2,
              whiteSpace: 'normal',
              bgcolor: notification.read ? undefined : 'action.hover',
            }}
          >
            <Box
              sx={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                mt: 0.75,
                flexShrink: 0,
                bgcolor: notification.read ? 'transparent' : DOT[notification.severity],
                border: notification.read ? '1px solid' : 'none',
                borderColor: 'divider',
              }}
            />
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" sx={{ fontWeight: notification.read ? 400 : 600 }}>
                {notification.title}
              </Typography>
              <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                {notification.body}
              </Typography>
              <Typography variant="caption" sx={{ color: 'text.disabled' }}>
                {new Date(notification.createdAt).toLocaleString('en-GB', {
                  day: '2-digit',
                  month: 'short',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </Typography>
            </Box>
          </MenuItem>
        ))}

        <Divider />
        <MenuItem component={RouterLink} to="/notifications" onClick={() => setAnchor(null)}>
          <Typography variant="body2" sx={{ width: '100%', textAlign: 'center' }}>
            See all notifications
          </Typography>
        </MenuItem>
      </Menu>
    </>
  );
}
