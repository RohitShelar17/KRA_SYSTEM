import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TablePagination from '@mui/material/TablePagination';
import Typography from '@mui/material/Typography';
import DoneAllOutlined from '@mui/icons-material/DoneAllOutlined';
import { notificationApi } from '@/api/insight';
import { EmptyState } from '@/components/EmptyState';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import type { NotificationSeverity } from '@/types/insight';

const TONE: Record<NotificationSeverity, 'default' | 'info' | 'success' | 'warning' | 'error'> = {
  INFO: 'info',
  SUCCESS: 'success',
  WARNING: 'warning',
  CRITICAL: 'error',
};

/** The full feed, with the unread ones first-class rather than a filter buried in a menu. */
export function NotificationsPage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const unreadOnly = tab === 1;

  const feed = useQuery({
    queryKey: ['notifications', 'feed', unreadOnly, page, size],
    queryFn: () => notificationApi.feed(unreadOnly, page, size),
  });

  const markRead = useMutation({
    mutationFn: (ids: number[]) => notificationApi.markRead(ids),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const markAll = useMutation({
    mutationFn: notificationApi.markAllRead,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const rows = feed.data?.content ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Account"
        title="Notifications"
        description="What the system has told you, and when."
        actions={
          <Button
            variant="outlined"
            startIcon={<DoneAllOutlined />}
            onClick={() => markAll.mutate()}
            disabled={markAll.isPending}
          >
            Mark all read
          </Button>
        }
      />

      <Card>
        <Tabs
          value={tab}
          onChange={(_, value) => {
            setTab(value);
            setPage(0);
          }}
          sx={{ px: 3 }}
        >
          <Tab label="Everything" />
          <Tab label="Unread" />
        </Tabs>

        {feed.isLoading && <FullPageLoader label="Loading notifications" />}

        {!feed.isLoading && rows.length === 0 && (
          <EmptyState
            title={unreadOnly ? 'Nothing unread' : 'No notifications yet'}
            description="Submissions, reviews, scores and appraisal steps all arrive here."
          />
        )}

        {rows.map((notification) => (
          <Box
            key={notification.id}
            sx={{
              display: 'flex',
              gap: 3,
              px: 4,
              py: 3,
              borderTop: '1px solid',
              borderColor: 'divider',
              bgcolor: notification.read ? undefined : 'action.hover',
            }}
          >
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 0.5 }}>
                <Typography variant="body2" sx={{ fontWeight: notification.read ? 500 : 600 }}>
                  {notification.title}
                </Typography>
                <Chip
                  size="small"
                  color={TONE[notification.severity]}
                  variant="outlined"
                  label={notification.eventKey.toLowerCase().replace(/_/g, ' ')}
                />
              </Box>
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {notification.body}
              </Typography>
              <Typography variant="caption" sx={{ color: 'text.disabled' }}>
                {new Date(notification.createdAt).toLocaleString('en-GB', {
                  weekday: 'short',
                  day: '2-digit',
                  month: 'short',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
                {notification.read && notification.readAt
                  ? ` · read ${new Date(notification.readAt).toLocaleDateString('en-GB')}`
                  : ''}
              </Typography>
            </Box>

            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexShrink: 0 }}>
              {notification.link && (
                <Button size="small" component={RouterLink} to={notification.link}>
                  Open
                </Button>
              )}
              {!notification.read && (
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => markRead.mutate([notification.id])}
                  disabled={markRead.isPending}
                >
                  Mark read
                </Button>
              )}
            </Box>
          </Box>
        ))}

        {feed.data && feed.data.totalElements > 0 && (
          <TablePagination
            component="div"
            count={feed.data.totalElements}
            page={page}
            rowsPerPage={size}
            onPageChange={(_, next) => setPage(next)}
            onRowsPerPageChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
            rowsPerPageOptions={[10, 20, 50]}
          />
        )}
      </Card>
    </Box>
  );
}
