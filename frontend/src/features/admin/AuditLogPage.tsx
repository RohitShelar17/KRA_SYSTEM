import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Collapse from '@mui/material/Collapse';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import ExpandMoreOutlined from '@mui/icons-material/ExpandMoreOutlined';
import { platformApi } from '@/api/platform';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import type { StatusTone } from '@/theme/tokens';

const MODULES = ['AUTH', 'EMPLOYEE', 'ORGANISATION', 'SECURITY', 'SETTINGS'];
const ACTIONS = ['CREATE', 'UPDATE', 'DELETE', 'ASSIGN', 'LOGIN', 'LOGIN_FAILED', 'LOGOUT', 'PASSWORD_RESET'];

const ACTION_TONE: Record<string, StatusTone> = {
  CREATE: 'success',
  UPDATE: 'active',
  ASSIGN: 'active',
  LOGIN: 'neutral',
  LOGOUT: 'neutral',
  DELETE: 'critical',
  LOGIN_FAILED: 'critical',
  PASSWORD_RESET: 'pending',
};

export function AuditLogPage() {
  const [module, setModule] = useState('');
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [expanded, setExpanded] = useState<number | null>(null);

  const logs = useQuery({
    queryKey: ['audit-logs', { module, action, page, size }],
    queryFn: () =>
      platformApi.auditLogs({
        module: module || undefined,
        action: action || undefined,
        page,
        size,
      }),
    placeholderData: keepPreviousData,
  });

  const rows = logs.data?.content ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Administration"
        title="Audit logs"
        description="Insert-only trail of every regulated action. Nothing here can be edited or deleted from the application."
      />

      <Card sx={{ mb: 4 }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3, p: 4 }}>
          <TextField
            select
            size="small"
            label="Module"
            value={module}
            onChange={(event) => {
              setModule(event.target.value);
              setPage(0);
            }}
            sx={{ minWidth: 200 }}
          >
            <MenuItem value="">All modules</MenuItem>
            {MODULES.map((value) => (
              <MenuItem key={value} value={value}>
                {value.toLowerCase()}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            size="small"
            label="Action"
            value={action}
            onChange={(event) => {
              setAction(event.target.value);
              setPage(0);
            }}
            sx={{ minWidth: 200 }}
          >
            <MenuItem value="">All actions</MenuItem>
            {ACTIONS.map((value) => (
              <MenuItem key={value} value={value}>
                {value.toLowerCase().replace('_', ' ')}
              </MenuItem>
            ))}
          </TextField>
        </Box>
      </Card>

      <Card>
        {logs.isLoading ? (
          <TableSkeleton rows={8} columns={5} />
        ) : rows.length === 0 ? (
          <EmptyState title="No audit entries" description="Nothing matches these filters yet." />
        ) : (
          <>
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell width={40} />
                    <TableCell>When</TableCell>
                    <TableCell>Actor</TableCell>
                    <TableCell>Action</TableCell>
                    <TableCell>Entity</TableCell>
                    <TableCell>Reason</TableCell>
                    <TableCell>IP</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((entry) => {
                    const open = expanded === entry.id;
                    const hasDetail = Boolean(entry.oldValue || entry.newValue);
                    return (
                      <>
                        <TableRow key={entry.id} hover>
                          <TableCell>
                            {hasDetail && (
                              <IconButton
                                size="small"
                                onClick={() => setExpanded(open ? null : entry.id)}
                                aria-label={open ? 'Hide detail' : 'Show detail'}
                                sx={{
                                  transform: open ? 'rotate(180deg)' : 'none',
                                  transition: 'transform 120ms',
                                }}
                              >
                                <ExpandMoreOutlined sx={{ fontSize: 17 }} />
                              </IconButton>
                            )}
                          </TableCell>
                          <TableCell className="tabular" sx={{ whiteSpace: 'nowrap' }}>
                            {formatTimestamp(entry.createdAt)}
                          </TableCell>
                          <TableCell>{entry.actorName ?? 'System'}</TableCell>
                          <TableCell>
                            <StatusChip
                              status={entry.action}
                              tone={ACTION_TONE[entry.action] ?? 'neutral'}
                              label={entry.action.toLowerCase().replace('_', ' ')}
                            />
                            <Typography variant="caption" sx={{ color: 'text.secondary', ml: 2 }}>
                              {entry.module.toLowerCase()}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            {entry.entityType ?? '—'}
                            {entry.entityId ? ` #${entry.entityId}` : ''}
                          </TableCell>
                          <TableCell sx={{ maxWidth: 220 }}>{entry.reason ?? '—'}</TableCell>
                          <TableCell className="tabular">{entry.ipAddress ?? '—'}</TableCell>
                        </TableRow>
                        {hasDetail && (
                          <TableRow key={`${entry.id}-detail`}>
                            <TableCell colSpan={7} sx={{ py: 0, borderBottom: open ? undefined : 'none' }}>
                              <Collapse in={open} unmountOnExit>
                                <Box
                                  sx={{
                                    display: 'grid',
                                    gap: 4,
                                    gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
                                    py: 4,
                                  }}
                                >
                                  <ValueBlock title="Before" value={entry.oldValue} />
                                  <ValueBlock title="After" value={entry.newValue} />
                                </Box>
                              </Collapse>
                            </TableCell>
                          </TableRow>
                        )}
                      </>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>

            <TablePagination
              component="div"
              count={logs.data?.totalElements ?? 0}
              page={page}
              onPageChange={(_, next) => setPage(next)}
              rowsPerPage={size}
              onRowsPerPageChange={(event) => {
                setSize(Number(event.target.value));
                setPage(0);
              }}
              rowsPerPageOptions={[25, 50, 100]}
            />
          </>
        )}
      </Card>
    </Box>
  );
}

function ValueBlock({ title, value }: { title: string; value: string | null }) {
  return (
    <Box>
      <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
        {title}
      </Typography>
      <Box
        component="pre"
        sx={{
          m: 0,
          p: 3,
          fontSize: 11.5,
          lineHeight: 1.6,
          bgcolor: 'action.hover',
          borderRadius: 1.5,
          overflowX: 'auto',
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
        }}
      >
        {value ? prettify(value) : '—'}
      </Box>
    </Box>
  );
}

function prettify(value: string): string {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}
