import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import DeleteOutlineOutlined from '@mui/icons-material/DeleteOutlineOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { organisationApi, type DesignationPayload } from '@/api/organisation';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { useAuth } from '@/contexts/AuthContext';
import type { Designation } from '@/types/domain';

const EMPTY: DesignationPayload = {
  code: '',
  name: '',
  gradeLevel: '',
  departmentId: null,
  description: '',
};

export function DesignationsPage() {
  const queryClient = useQueryClient();
  const { can } = useAuth();

  const [departmentFilter, setDepartmentFilter] = useState<number | ''>('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<Designation | null>(null);
  const [form, setForm] = useState<DesignationPayload>(EMPTY);
  const [pendingDelete, setPendingDelete] = useState<Designation | null>(null);
  const [error, setError] = useState<string | null>(null);

  const departments = useQuery({
    queryKey: ['departments', 'options'],
    queryFn: organisationApi.departmentOptions,
  });

  const designations = useQuery({
    queryKey: ['designations', departmentFilter],
    queryFn: () => organisationApi.designations(departmentFilter === '' ? undefined : departmentFilter),
  });

  const save = useMutation({
    mutationFn: (payload: DesignationPayload) =>
      editing
        ? organisationApi.updateDesignation(editing.id, payload)
        : organisationApi.createDesignation(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['designations'] });
      setDialogOpen(false);
      setEditing(null);
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const deactivate = useMutation({
    mutationFn: (id: number) => organisationApi.deactivateDesignation(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['designations'] });
      setPendingDelete(null);
    },
    onError: (caught) => {
      setError(toErrorMessage(caught));
      setPendingDelete(null);
    },
  });

  const rows = designations.data ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Organisation"
        title="Designations"
        description="Job titles and grades. Phase 3 attaches a KRA template to each designation, so keep them meaningful."
        actions={
          can('DESIGNATION_MANAGE') ? (
            <Button
              variant="contained"
              startIcon={<AddOutlined sx={{ fontSize: 18 }} />}
              onClick={() => {
                setEditing(null);
                setForm(EMPTY);
                setDialogOpen(true);
              }}
            >
              Add designation
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
            select
            size="small"
            label="Department"
            value={departmentFilter}
            onChange={(event) =>
              setDepartmentFilter(event.target.value === '' ? '' : Number(event.target.value))
            }
            sx={{ minWidth: 240 }}
          >
            <MenuItem value="">All departments</MenuItem>
            {(departments.data ?? []).map((option) => (
              <MenuItem key={option.id} value={option.id}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
        </Box>
      </Card>

      <Card>
        {designations.isLoading ? (
          <TableSkeleton rows={6} columns={4} />
        ) : rows.length === 0 ? (
          <EmptyState title="No designations" description="Add the job titles this organisation uses." />
        ) : (
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Code</TableCell>
                  <TableCell>Designation</TableCell>
                  <TableCell>Grade</TableCell>
                  <TableCell>Department</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((designation) => (
                  <TableRow key={designation.id} hover>
                    <TableCell sx={{ fontWeight: 600 }}>{designation.code}</TableCell>
                    <TableCell>
                      {designation.name}
                      {designation.description && (
                        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                          {designation.description}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell className="tabular">{designation.gradeLevel ?? '—'}</TableCell>
                    <TableCell>{designation.departmentName ?? 'Any'}</TableCell>
                    <TableCell align="right">
                      {can('DESIGNATION_MANAGE') && (
                        <>
                          <Tooltip title="Edit">
                            <IconButton
                              size="small"
                              onClick={() => {
                                setEditing(designation);
                                setForm({
                                  code: designation.code,
                                  name: designation.name,
                                  gradeLevel: designation.gradeLevel ?? '',
                                  departmentId: designation.departmentId,
                                  description: designation.description ?? '',
                                });
                                setDialogOpen(true);
                              }}
                            >
                              <EditOutlined sx={{ fontSize: 17 }} />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Deactivate">
                            <IconButton size="small" onClick={() => setPendingDelete(designation)}>
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
        )}
      </Card>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>
          {editing ? 'Edit designation' : 'Add designation'}
        </DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr', pt: 2 }}>
          <TextField
            label="Code"
            required
            size="small"
            value={form.code}
            onChange={(event) => setForm({ ...form, code: event.target.value.toUpperCase() })}
          />
          <TextField
            label="Name"
            required
            size="small"
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <TextField
            label="Grade level"
            size="small"
            value={form.gradeLevel ?? ''}
            onChange={(event) => setForm({ ...form, gradeLevel: event.target.value })}
            helperText="For example L3"
          />
          <TextField
            select
            label="Department"
            size="small"
            value={form.departmentId ?? ''}
            onChange={(event) =>
              setForm({
                ...form,
                departmentId: event.target.value === '' ? null : Number(event.target.value),
              })
            }
          >
            <MenuItem value="">Any department</MenuItem>
            {(departments.data ?? []).map((option) => (
              <MenuItem key={option.id} value={option.id}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Description"
            size="small"
            multiline
            minRows={2}
            sx={{ gridColumn: '1 / -1' }}
            value={form.description ?? ''}
            onChange={(event) => setForm({ ...form, description: event.target.value })}
          />
        </DialogContent>
        <DialogActions sx={{ px: 6, pb: 5 }}>
          <Button variant="outlined" onClick={() => setDialogOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={!form.code.trim() || !form.name.trim() || save.isPending}
            onClick={() => save.mutate(form)}
          >
            {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Create designation'}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Deactivate designation"
        message={`${pendingDelete?.name ?? 'This designation'} will no longer be selectable for new employees.`}
        confirmLabel="Deactivate"
        destructive
        busy={deactivate.isPending}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => pendingDelete && deactivate.mutate(pendingDelete.id)}
      />
    </Box>
  );
}
