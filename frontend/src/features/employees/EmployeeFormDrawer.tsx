import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Drawer from '@mui/material/Drawer';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import CloseOutlined from '@mui/icons-material/CloseOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { employeeApi, type EmployeeCreatePayload } from '@/api/employees';
import { organisationApi } from '@/api/organisation';
import { platformApi } from '@/api/platform';
import type { Employee, EmployeeStatus, EmploymentType, WorkMode } from '@/types/domain';

const EMPLOYMENT_TYPES: EmploymentType[] = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN', 'CONSULTANT'];
const WORK_MODES: WorkMode[] = ['OFFICE', 'REMOTE', 'HYBRID'];
const STATUSES: EmployeeStatus[] = ['ACTIVE', 'ON_NOTICE', 'ON_LEAVE', 'INACTIVE', 'TERMINATED'];

interface Props {
  open: boolean;
  employee: Employee | null;
  onClose: () => void;
}

type FormState = EmployeeCreatePayload & { exitDate?: string | null };

const EMPTY: FormState = {
  employeeCode: '',
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  departmentId: 0,
  teamId: null,
  designationId: 0,
  reportingManagerId: null,
  joiningDate: new Date().toISOString().slice(0, 10),
  employmentType: 'FULL_TIME',
  workLocation: '',
  workMode: 'OFFICE',
  shiftStartTime: null,
  shiftEndTime: null,
  status: 'ACTIVE',
  roleCodes: ['EMPLOYEE'],
};

/**
 * One drawer for create and edit. On edit, employee code and work email are
 * read-only: they are identity, and changing them silently would break the
 * link to work logs and the sign-in account.
 */
export function EmployeeFormDrawer({ open, employee, onClose }: Props) {
  const queryClient = useQueryClient();
  const isEdit = Boolean(employee);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [error, setError] = useState<string | null>(null);

  const departments = useQuery({
    queryKey: ['departments', 'options'],
    queryFn: organisationApi.departmentOptions,
    enabled: open,
  });
  const designations = useQuery({
    queryKey: ['designations', 'options', form.departmentId],
    queryFn: () => organisationApi.designationOptions(),
    enabled: open,
  });
  const teams = useQuery({
    queryKey: ['teams', 'options', form.departmentId],
    queryFn: () => organisationApi.teamOptions(form.departmentId || undefined),
    enabled: open && Boolean(form.departmentId),
  });
  const managers = useQuery({
    queryKey: ['employees', 'options'],
    queryFn: employeeApi.options,
    enabled: open,
  });
  const roles = useQuery({ queryKey: ['roles'], queryFn: platformApi.roles, enabled: open });

  useEffect(() => {
    if (!open) {
      return;
    }
    setError(null);
    if (employee) {
      setForm({
        employeeCode: employee.employeeCode,
        firstName: employee.firstName,
        lastName: employee.lastName,
        email: employee.email,
        phone: employee.phone ?? '',
        departmentId: employee.departmentId,
        teamId: employee.teamId,
        designationId: employee.designationId,
        reportingManagerId: employee.reportingManagerId,
        joiningDate: employee.joiningDate,
        exitDate: employee.exitDate,
        employmentType: employee.employmentType,
        workLocation: employee.workLocation ?? '',
        workMode: employee.workMode,
        shiftStartTime: employee.shiftStartTime,
        shiftEndTime: employee.shiftEndTime,
        status: employee.status,
        roleCodes: employee.roles.length ? employee.roles : ['EMPLOYEE'],
      });
    } else {
      setForm(EMPTY);
    }
  }, [open, employee]);

  const mutation = useMutation({
    mutationFn: async (payload: FormState) => {
      if (employee) {
        return employeeApi.update(employee.id, {
          firstName: payload.firstName,
          lastName: payload.lastName,
          phone: payload.phone,
          departmentId: payload.departmentId,
          teamId: payload.teamId,
          designationId: payload.designationId,
          joiningDate: payload.joiningDate,
          exitDate: payload.exitDate ?? null,
          employmentType: payload.employmentType,
          workLocation: payload.workLocation,
          workMode: payload.workMode,
          shiftStartTime: payload.shiftStartTime,
          shiftEndTime: payload.shiftEndTime,
          status: payload.status ?? 'ACTIVE',
          roleCodes: payload.roleCodes,
        });
      }
      return employeeApi.create(payload);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['employees'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      onClose();
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const canSubmit = useMemo(
    () =>
      form.firstName.trim() &&
      form.lastName.trim() &&
      form.email.trim() &&
      form.departmentId > 0 &&
      form.designationId > 0 &&
      form.roleCodes.length > 0 &&
      (isEdit || form.employeeCode.trim()),
    [form, isEdit],
  );

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{ sx: { width: { xs: '100%', sm: 480 } } }}
    >
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <Box
          sx={{
            px: 6,
            py: 4,
            borderBottom: 1,
            borderColor: 'divider',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Box>
            <Typography variant="h5">{isEdit ? 'Edit employee' : 'Add employee'}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {isEdit
                ? employee?.employeeCode
                : 'A sign-in account is created with the roles you choose'}
            </Typography>
          </Box>
          <IconButton onClick={onClose} aria-label="Close">
            <CloseOutlined sx={{ fontSize: 19 }} />
          </IconButton>
        </Box>

        <Box sx={{ flex: 1, overflowY: 'auto', px: 6, py: 5 }}>
          {error && (
            <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
              {error}
            </Alert>
          )}

          <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr' }}>
            <TextField
              label="Employee code"
              required
              disabled={isEdit}
              value={form.employeeCode}
              onChange={(event) => set('employeeCode', event.target.value)}
              size="small"
            />
            <TextField
              label="Work email"
              required
              type="email"
              disabled={isEdit}
              value={form.email}
              onChange={(event) => set('email', event.target.value)}
              size="small"
            />
            <TextField
              label="First name"
              required
              value={form.firstName}
              onChange={(event) => set('firstName', event.target.value)}
              size="small"
            />
            <TextField
              label="Last name"
              required
              value={form.lastName}
              onChange={(event) => set('lastName', event.target.value)}
              size="small"
            />
            <TextField
              label="Phone"
              value={form.phone ?? ''}
              onChange={(event) => set('phone', event.target.value)}
              size="small"
              sx={{ gridColumn: '1 / -1' }}
            />
          </Box>

          <Divider sx={{ my: 5 }}>
            <Typography variant="overline" sx={{ color: 'text.secondary' }}>
              Placement
            </Typography>
          </Divider>

          <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr' }}>
            <TextField
              select
              required
              label="Department"
              value={form.departmentId || ''}
              onChange={(event) => {
                set('departmentId', Number(event.target.value));
                set('teamId', null);
              }}
              size="small"
            >
              {(departments.data ?? []).map((option) => (
                <MenuItem key={option.id} value={option.id}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              required
              label="Designation"
              value={form.designationId || ''}
              onChange={(event) => set('designationId', Number(event.target.value))}
              size="small"
            >
              {(designations.data ?? []).map((option) => (
                <MenuItem key={option.id} value={option.id}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              label="Team"
              value={form.teamId ?? ''}
              onChange={(event) =>
                set('teamId', event.target.value === '' ? null : Number(event.target.value))
              }
              size="small"
              disabled={!form.departmentId}
            >
              <MenuItem value="">No team</MenuItem>
              {(teams.data ?? []).map((option) => (
                <MenuItem key={option.id} value={option.id}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              label="Reporting manager"
              value={form.reportingManagerId ?? ''}
              onChange={(event) =>
                set('reportingManagerId', event.target.value === '' ? null : Number(event.target.value))
              }
              size="small"
              disabled={isEdit}
              helperText={isEdit ? 'Change from the profile page' : undefined}
            >
              <MenuItem value="">No manager</MenuItem>
              {(managers.data ?? [])
                .filter((option) => option.id !== employee?.id)
                .map((option) => (
                  <MenuItem key={option.id} value={option.id}>
                    {option.label} {option.code ? `· ${option.code}` : ''}
                  </MenuItem>
                ))}
            </TextField>
          </Box>

          <Divider sx={{ my: 5 }}>
            <Typography variant="overline" sx={{ color: 'text.secondary' }}>
              Employment
            </Typography>
          </Divider>

          <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr' }}>
            <TextField
              label="Joining date"
              type="date"
              required
              value={form.joiningDate}
              onChange={(event) => set('joiningDate', event.target.value)}
              size="small"
              InputLabelProps={{ shrink: true }}
            />
            {isEdit && (
              <TextField
                label="Exit date"
                type="date"
                value={form.exitDate ?? ''}
                onChange={(event) => set('exitDate', event.target.value || null)}
                size="small"
                InputLabelProps={{ shrink: true }}
              />
            )}
            <TextField
              select
              label="Employment type"
              value={form.employmentType}
              onChange={(event) => set('employmentType', event.target.value as EmploymentType)}
              size="small"
            >
              {EMPLOYMENT_TYPES.map((type) => (
                <MenuItem key={type} value={type}>
                  {humanise(type)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Work mode"
              value={form.workMode}
              onChange={(event) => set('workMode', event.target.value as WorkMode)}
              size="small"
            >
              {WORK_MODES.map((mode) => (
                <MenuItem key={mode} value={mode}>
                  {humanise(mode)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Work location"
              value={form.workLocation ?? ''}
              onChange={(event) => set('workLocation', event.target.value)}
              size="small"
            />
            <TextField
              select
              label="Status"
              value={form.status ?? 'ACTIVE'}
              onChange={(event) => set('status', event.target.value as EmployeeStatus)}
              size="small"
            >
              {STATUSES.map((status) => (
                <MenuItem key={status} value={status}>
                  {humanise(status)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Shift start"
              type="time"
              value={form.shiftStartTime ?? ''}
              onChange={(event) => set('shiftStartTime', event.target.value || null)}
              size="small"
              InputLabelProps={{ shrink: true }}
              helperText="Leave blank to use the company shift"
            />
            <TextField
              label="Shift end"
              type="time"
              value={form.shiftEndTime ?? ''}
              onChange={(event) => set('shiftEndTime', event.target.value || null)}
              size="small"
              InputLabelProps={{ shrink: true }}
            />
          </Box>

          <Divider sx={{ my: 5 }}>
            <Typography variant="overline" sx={{ color: 'text.secondary' }}>
              Access
            </Typography>
          </Divider>

          <TextField
            select
            required
            fullWidth
            size="small"
            label="Roles"
            SelectProps={{ multiple: true }}
            value={form.roleCodes}
            onChange={(event) =>
              set(
                'roleCodes',
                typeof event.target.value === 'string'
                  ? event.target.value.split(',')
                  : (event.target.value as unknown as string[]),
              )
            }
            helperText="Roles decide which permissions and screens this person gets"
          >
            {(roles.data ?? []).map((role) => (
              <MenuItem key={role.code} value={role.code}>
                {role.name} · {role.dataScope.toLowerCase()} scope
              </MenuItem>
            ))}
          </TextField>
        </Box>

        <Box
          sx={{
            px: 6,
            py: 4,
            borderTop: 1,
            borderColor: 'divider',
            display: 'flex',
            justifyContent: 'flex-end',
            gap: 3,
          }}
        >
          <Button variant="outlined" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={() => mutation.mutate(form)}
            disabled={!canSubmit || mutation.isPending}
          >
            {mutation.isPending ? 'Saving…' : isEdit ? 'Save changes' : 'Create employee'}
          </Button>
        </Box>
      </Box>
    </Drawer>
  );
}

function humanise(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}
