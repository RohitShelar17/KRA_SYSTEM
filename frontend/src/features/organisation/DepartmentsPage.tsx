import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
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
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import DeleteOutlineOutlined from '@mui/icons-material/DeleteOutlineOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { employeeApi } from '@/api/employees';
import { organisationApi, type DepartmentPayload } from '@/api/organisation';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { useAuth } from '@/contexts/AuthContext';
import type { Department } from '@/types/domain';

const EMPTY: DepartmentPayload = {
  code: '',
  name: '',
  description: '',
  parentDepartmentId: null,
  headEmployeeId: null,
};

export function DepartmentsPage() {
  const queryClient = useQueryClient();
  const { can } = useAuth();

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [query, setQuery] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<Department | null>(null);
  const [form, setForm] = useState<DepartmentPayload>(EMPTY);
  const [pendingDelete, setPendingDelete] = useState<Department | null>(null);
  const [error, setError] = useState<string | null>(null);

  const departments = useQuery({
    queryKey: ['departments', { query, page, size }],
    queryFn: () => organisationApi.departments({ query: query || undefined, page, size, activeOnly: true }),
    placeholderData: keepPreviousData,
  });

  const options = useQuery({
    queryKey: ['departments', 'options'],
    queryFn: organisationApi.departmentOptions,
    enabled: dialogOpen,
  });

  const employees = useQuery({
    queryKey: ['employees', 'options'],
    queryFn: employeeApi.options,
    enabled: dialogOpen && can('EMPLOYEE_READ'),
  });

  const save = useMutation({
    mutationFn: (payload: DepartmentPayload) =>
      editing
        ? organisationApi.updateDepartment(editing.id, payload)
        : organisationApi.createDepartment(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['departments'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      closeDialog();
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const deactivate = useMutation({
    mutationFn: (id: number) => organisationApi.deactivateDepartment(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['departments'] });
      setPendingDelete(null);
    },
    onError: (caught) => {
      setError(toErrorMessage(caught));
      setPendingDelete(null);
    },
  });

  function openCreate() {
    setEditing(null);
    setForm(EMPTY);
    setError(null);
    setDialogOpen(true);
  }

  function openEdit(department: Department) {
    setEditing(department);
    setForm({
      code: department.code,
      name: department.name,
      description: department.description ?? '',
      parentDepartmentId: department.parentDepartmentId,
      headEmployeeId: department.headEmployeeId,
    });
    setError(null);
    setDialogOpen(true);
  }

  function closeDialog() {
    setDialogOpen(false);
    setEditing(null);
  }

  const rows = departments.data?.content ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Organisation"
        title="Departments"
        description="Departments are configuration, not code. Add, rename or restructure them without a release."
        actions={
          can('DEPARTMENT_MANAGE') ? (
            <Button variant="contained" startIcon={<AddOutlined sx={{ fontSize: 18 }} />} onClick={openCreate}>
              Add department
            </Button>
          ) : undefined
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Card sx={{ mb: 4 }}>
        <Box sx={{ p: 4 }}>
          <TextField
            size="small"
            placeholder="Search by name or code"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
            sx={{ maxWidth: 320, width: '100%' }}
          />
        </Box>
      </Card>

      <Card>
        {departments.isLoading ? (
          <TableSkeleton rows={6} columns={5} />
        ) : rows.length === 0 ? (
          <EmptyState title="No departments yet" description="Create the first department to place employees." />
        ) : (
          <>
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Code</TableCell>
                    <TableCell>Department</TableCell>
                    <TableCell>Head</TableCell>
                    <TableCell>Parent</TableCell>
                    <TableCell align="right">Headcount</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((department) => (
                    <TableRow key={department.id} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{department.code}</TableCell>
                      <TableCell>
                        {department.name}
                        {department.description && (
                          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                            {department.description}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>{department.headEmployeeName ?? '—'}</TableCell>
                      <TableCell>{department.parentDepartmentName ?? '—'}</TableCell>
                      <TableCell align="right" className="tabular">
                        {department.employeeCount}
                      </TableCell>
                      <TableCell align="right">
                        {can('DEPARTMENT_MANAGE') && (
                          <>
                            <Tooltip title="Edit">
                              <IconButton size="small" onClick={() => openEdit(department)}>
                                <EditOutlined sx={{ fontSize: 17 }} />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Deactivate">
                              <IconButton size="small" onClick={() => setPendingDelete(department)}>
                                <DeleteOutlineOutlined sx={{ fontSize: 17 }} />
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
              count={departments.data?.totalElements ?? 0}
              page={page}
              onPageChange={(_, next) => setPage(next)}
              rowsPerPage={size}
              onRowsPerPageChange={(event) => {
                setSize(Number(event.target.value));
                setPage(0);
              }}
              rowsPerPageOptions={[10, 25, 50]}
            />
          </>
        )}
      </Card>

      <Dialog open={dialogOpen} onClose={closeDialog} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>
          {editing ? 'Edit department' : 'Add department'}
        </DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr', pt: 2 }}>
          <TextField
            label="Code"
            required
            size="small"
            value={form.code}
            onChange={(event) => setForm({ ...form, code: event.target.value.toUpperCase() })}
            helperText="Letters, numbers, hyphen or underscore"
          />
          <TextField
            label="Name"
            required
            size="small"
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <TextField
            label="Description"
            size="small"
            multiline
            minRows={2}
            sx={{ gridColumn: '1 / -1' }}
            value={form.description ?? ''}
            onChange={(event) => setForm({ ...form, description: event.target.value })}
          />
          <TextField
            select
            label="Parent department"
            size="small"
            value={form.parentDepartmentId ?? ''}
            onChange={(event) =>
              setForm({
                ...form,
                parentDepartmentId: event.target.value === '' ? null : Number(event.target.value),
              })
            }
          >
            <MenuItem value="">None</MenuItem>
            {(options.data ?? [])
              .filter((option) => option.id !== editing?.id)
              .map((option) => (
                <MenuItem key={option.id} value={option.id}>
                  {option.label}
                </MenuItem>
              ))}
          </TextField>
          <TextField
            select
            label="Department head"
            size="small"
            value={form.headEmployeeId ?? ''}
            onChange={(event) =>
              setForm({
                ...form,
                headEmployeeId: event.target.value === '' ? null : Number(event.target.value),
              })
            }
          >
            <MenuItem value="">Not assigned</MenuItem>
            {(employees.data ?? []).map((option) => (
              <MenuItem key={option.id} value={option.id}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions sx={{ px: 6, pb: 5 }}>
          <Button variant="outlined" onClick={closeDialog}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={!form.code.trim() || !form.name.trim() || save.isPending}
            onClick={() => save.mutate(form)}
          >
            {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Create department'}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Deactivate department"
        message={`${pendingDelete?.name ?? 'This department'} will be hidden from new assignments. It must have no active employees or sub-departments.`}
        confirmLabel="Deactivate"
        destructive
        busy={deactivate.isPending}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => pendingDelete && deactivate.mutate(pendingDelete.id)}
      />
    </Box>
  );
}
