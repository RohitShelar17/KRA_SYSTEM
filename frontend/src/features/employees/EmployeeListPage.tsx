import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import ForwardToInboxOutlined from '@mui/icons-material/ForwardToInboxOutlined';
import PersonOffOutlined from '@mui/icons-material/PersonOffOutlined';
import SearchOutlined from '@mui/icons-material/SearchOutlined';
import { employeeApi } from '@/api/employees';
import { organisationApi } from '@/api/organisation';
import { toErrorMessage } from '@/api/axiosClient';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { useAuth } from '@/contexts/AuthContext';
import type { Employee, EmployeeStatus, Invite } from '@/types/domain';
import { EmployeeFormDrawer } from './EmployeeFormDrawer';
import { InviteDialog } from './InviteDialog';

const STATUS_FILTERS: (EmployeeStatus | 'ALL')[] = ['ALL', 'ACTIVE', 'ON_NOTICE', 'ON_LEAVE', 'INACTIVE'];

export function EmployeeListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { can } = useAuth();
  const [params, setParams] = useSearchParams();

  const [query, setQuery] = useState(params.get('query') ?? '');
  const [departmentId, setDepartmentId] = useState<number | ''>('');
  const [status, setStatus] = useState<EmployeeStatus | 'ALL'>('ALL');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Employee | null>(null);
  const [pendingDeactivate, setPendingDeactivate] = useState<{ id: number; name: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const departments = useQuery({
    queryKey: ['departments', 'options'],
    queryFn: organisationApi.departmentOptions,
  });

  const [issuedInvite, setIssuedInvite] = useState<Invite | null>(null);

  const employees = useQuery({
    queryKey: ['employees', { query, departmentId, status, page, size }],
    queryFn: () =>
      employeeApi.search({
        query: query || undefined,
        departmentId: departmentId === '' ? undefined : departmentId,
        status: status === 'ALL' ? undefined : status,
        activeOnly: status === 'ALL',
        page,
        size,
      }),
    placeholderData: keepPreviousData,
  });

  const invite = useMutation({
    mutationFn: (id: number) => employeeApi.invite(id),
    onSuccess: (issued) => {
      setIssuedInvite(issued);
      setError(null);
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const deactivate = useMutation({
    mutationFn: (id: number) => employeeApi.deactivate(id, 'Deactivated from the employee directory'),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['employees'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      setPendingDeactivate(null);
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  async function openEdit(id: number) {
    const employee = await employeeApi.get(id);
    setEditing(employee);
    setDrawerOpen(true);
  }

  const rows = employees.data?.content ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Organisation"
        title="Employees"
        description="Everyone within your access scope, with their placement and reporting line."
        actions={
          can('EMPLOYEE_MANAGE') ? (
            <Button
              variant="contained"
              startIcon={<AddOutlined sx={{ fontSize: 18 }} />}
              onClick={() => {
                setEditing(null);
                setDrawerOpen(true);
              }}
            >
              Add employee
            </Button>
          ) : undefined
        }
      />

      <Card sx={{ mb: 4 }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3, p: 4 }}>
          <TextField
            size="small"
            placeholder="Name, code or email"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
              const next = new URLSearchParams(params);
              if (event.target.value) {
                next.set('query', event.target.value);
              } else {
                next.delete('query');
              }
              setParams(next, { replace: true });
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchOutlined sx={{ fontSize: 17, color: 'text.disabled' }} />
                </InputAdornment>
              ),
            }}
            sx={{ minWidth: 260, flex: 1 }}
          />
          <TextField
            select
            size="small"
            label="Department"
            value={departmentId}
            onChange={(event) => {
              setDepartmentId(event.target.value === '' ? '' : Number(event.target.value));
              setPage(0);
            }}
            sx={{ minWidth: 190 }}
          >
            <MenuItem value="">All departments</MenuItem>
            {(departments.data ?? []).map((option) => (
              <MenuItem key={option.id} value={option.id}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            size="small"
            label="Status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as EmployeeStatus | 'ALL');
              setPage(0);
            }}
            sx={{ minWidth: 170 }}
          >
            {STATUS_FILTERS.map((value) => (
              <MenuItem key={value} value={value}>
                {value === 'ALL' ? 'Active employees' : humanise(value)}
              </MenuItem>
            ))}
          </TextField>
        </Box>
      </Card>

      {error && (
        <Typography variant="body2" sx={{ color: 'error.main', mb: 3 }}>
          {error}
        </Typography>
      )}

      <Card>
        {employees.isLoading ? (
          <TableSkeleton rows={8} columns={6} />
        ) : rows.length === 0 ? (
          <EmptyState
            title="No employees match these filters"
            description="Try a different department or clear the search."
          />
        ) : (
          <>
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Employee</TableCell>
                    <TableCell>Designation</TableCell>
                    <TableCell>Department</TableCell>
                    <TableCell>Reports to</TableCell>
                    <TableCell>Joined</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((employee) => (
                    <TableRow
                      key={employee.id}
                      hover
                      sx={{ cursor: 'pointer' }}
                      onClick={() => navigate(`/employees/${employee.id}`)}
                    >
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                          <Avatar
                            src={employee.profileImageUrl ?? undefined}
                            sx={{ width: 30, height: 30, fontSize: 11.5, bgcolor: 'primary.main' }}
                          >
                            {initials(employee.fullName)}
                          </Avatar>
                          <Box sx={{ minWidth: 0 }}>
                            <Typography sx={{ fontSize: 13, fontWeight: 600 }}>
                              {employee.fullName}
                            </Typography>
                            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                              {employee.employeeCode} · {employee.email}
                            </Typography>
                          </Box>
                        </Box>
                      </TableCell>
                      <TableCell>{employee.designationName ?? '—'}</TableCell>
                      <TableCell>
                        {employee.departmentName ?? '—'}
                        {employee.teamName && (
                          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                            {employee.teamName}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>{employee.reportingManagerName ?? '—'}</TableCell>
                      <TableCell className="tabular">{formatDate(employee.joiningDate)}</TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
                          <StatusChip status={employee.status} />
                          {employee.userStatus === 'PENDING_ACTIVATION' && (
                            <StatusChip status="PENDING" tone="pending" label="Invite pending" />
                          )}
                          {employee.userStatus === 'LOCKED' && (
                            <StatusChip status="LOCKED" tone="critical" label="Locked out" />
                          )}
                        </Box>
                      </TableCell>
                      <TableCell
                        align="right"
                        sx={{ whiteSpace: 'nowrap' }}
                        onClick={(event) => event.stopPropagation()}
                      >
                        {can('EMPLOYEE_MANAGE') && (
                          <>
                            <Tooltip title="Edit">
                              <IconButton size="small" onClick={() => void openEdit(employee.id)}>
                                <EditOutlined sx={{ fontSize: 17 }} />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Re-send sign-in invite">
                              <IconButton
                                size="small"
                                disabled={invite.isPending}
                                onClick={() => invite.mutate(employee.id)}
                              >
                                <ForwardToInboxOutlined sx={{ fontSize: 17 }} />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Deactivate">
                              <IconButton
                                size="small"
                                onClick={() =>
                                  setPendingDeactivate({ id: employee.id, name: employee.fullName })
                                }
                              >
                                <PersonOffOutlined sx={{ fontSize: 17 }} />
                              </IconButton>
                            </Tooltip>
                          </>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>

            <TablePagination
              component="div"
              count={employees.data?.totalElements ?? 0}
              page={page}
              onPageChange={(_, next) => setPage(next)}
              rowsPerPage={size}
              onRowsPerPageChange={(event) => {
                setSize(Number(event.target.value));
                setPage(0);
              }}
              rowsPerPageOptions={[10, 25, 50, 100]}
            />
          </>
        )}
      </Card>

      <InviteDialog invite={issuedInvite} onClose={() => setIssuedInvite(null)} />

      <EmployeeFormDrawer
        open={drawerOpen}
        employee={editing}
        onClose={() => {
          setDrawerOpen(false);
          setEditing(null);
        }}
      />

      <ConfirmDialog
        open={Boolean(pendingDeactivate)}
        title="Deactivate employee"
        message={`${pendingDeactivate?.name ?? 'This employee'} will lose access immediately. Their work logs and performance history are kept.`}
        confirmLabel="Deactivate"
        destructive
        busy={deactivate.isPending}
        onCancel={() => setPendingDeactivate(null)}
        onConfirm={() => pendingDeactivate && deactivate.mutate(pendingDeactivate.id)}
      />
    </Box>
  );
}

function initials(name: string): string {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

function humanise(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}
