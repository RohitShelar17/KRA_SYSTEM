import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import { cycleApi } from '@/api/appraisal';
import { employeeApi } from '@/api/employees';
import { organisationApi } from '@/api/organisation';
import { performanceSettingsApi } from '@/api/performance';
import type { ReportFilterKey } from '@/types/insight';

export type FilterValues = Record<string, string>;

interface Props {
  filters: ReportFilterKey[];
  values: FilterValues;
  onChange: (values: FilterValues) => void;
}

const MONTHS = Array.from({ length: 12 }, (_, index) => index + 1);
const TASK_STATUSES = ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'ON_HOLD', 'BLOCKED', 'CANCELLED'];

/**
 * One filter panel for fifteen reports.
 *
 * <p>It renders only the filters the chosen report declared, which is why the
 * filter vocabulary is a fixed enum on the server: the screen can be generic
 * without guessing what a given report understands.</p>
 */
export function ReportFilters({ filters, values, onChange }: Props) {
  const has = (key: ReportFilterKey) => filters.includes(key);
  const set = (key: string, value: string) => onChange({ ...values, [key]: value });

  const employees = useQuery({
    queryKey: ['employees', 'options'],
    queryFn: employeeApi.options,
    enabled: has('EMPLOYEE_ID'),
  });

  const departments = useQuery({
    queryKey: ['departments', 'options'],
    queryFn: organisationApi.departmentOptions,
    enabled: has('DEPARTMENT_ID'),
  });

  const bands = useQuery({
    queryKey: ['performance', 'bands'],
    queryFn: performanceSettingsApi.ratingBands,
    enabled: has('RATING_BAND_ID'),
  });

  const cycles = useQuery({
    queryKey: ['cycles', 'list'],
    queryFn: cycleApi.list,
    enabled: has('CYCLE_ID'),
  });

  const thisYear = new Date().getFullYear();

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3 }}>
      {has('FROM_DATE') && (
        <TextField
          size="small"
          type="date"
          label="From"
          value={values.fromDate ?? ''}
          onChange={(event) => set('fromDate', event.target.value)}
          InputLabelProps={{ shrink: true }}
          sx={{ minWidth: 160 }}
        />
      )}

      {has('TO_DATE') && (
        <TextField
          size="small"
          type="date"
          label="To"
          value={values.toDate ?? ''}
          onChange={(event) => set('toDate', event.target.value)}
          InputLabelProps={{ shrink: true }}
          sx={{ minWidth: 160 }}
        />
      )}

      {has('YEAR') && (
        <TextField
          select
          size="small"
          label="Year"
          value={values.year ?? String(thisYear)}
          onChange={(event) => set('year', event.target.value)}
          sx={{ minWidth: 120 }}
        >
          {[thisYear, thisYear - 1, thisYear - 2].map((year) => (
            <MenuItem key={year} value={String(year)}>
              {year}
            </MenuItem>
          ))}
        </TextField>
      )}

      {has('MONTH') && (
        <TextField
          select
          size="small"
          label="Month"
          value={values.month ?? ''}
          onChange={(event) => set('month', event.target.value)}
          sx={{ minWidth: 150 }}
        >
          <MenuItem value="">Whole year</MenuItem>
          {MONTHS.map((month) => (
            <MenuItem key={month} value={String(month)}>
              {new Date(2000, month - 1, 1).toLocaleString('en-GB', { month: 'long' })}
            </MenuItem>
          ))}
        </TextField>
      )}

      {has('QUARTER') && (
        <TextField
          select
          size="small"
          label="Quarter"
          value={values.quarter ?? ''}
          onChange={(event) => set('quarter', event.target.value)}
          sx={{ minWidth: 130 }}
        >
          <MenuItem value="">Current</MenuItem>
          {[1, 2, 3, 4].map((quarter) => (
            <MenuItem key={quarter} value={String(quarter)}>
              Q{quarter}
            </MenuItem>
          ))}
        </TextField>
      )}

      {has('DEPARTMENT_ID') && (
        <TextField
          select
          size="small"
          label="Department"
          value={values.departmentId ?? ''}
          onChange={(event) => set('departmentId', event.target.value)}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="">Everyone in scope</MenuItem>
          {(departments.data ?? []).map((option) => (
            <MenuItem key={option.id} value={String(option.id)}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>
      )}

      {has('EMPLOYEE_ID') && (
        <TextField
          select
          size="small"
          label="Employee"
          value={values.employeeId ?? ''}
          onChange={(event) => set('employeeId', event.target.value)}
          sx={{ minWidth: 220 }}
        >
          <MenuItem value="">Everyone in scope</MenuItem>
          {(employees.data ?? []).map((option) => (
            <MenuItem key={option.id} value={String(option.id)}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>
      )}

      {has('RATING_BAND_ID') && (
        <TextField
          select
          size="small"
          label="Rating band"
          value={values.ratingBandId ?? ''}
          onChange={(event) => set('ratingBandId', event.target.value)}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">Every band</MenuItem>
          {(bands.data ?? []).map((band) => (
            <MenuItem key={band.id} value={String(band.id)}>
              {band.name}
            </MenuItem>
          ))}
        </TextField>
      )}

      {has('CYCLE_ID') && (
        <TextField
          select
          size="small"
          label="Cycle"
          value={values.cycleId ?? ''}
          onChange={(event) => set('cycleId', event.target.value)}
          sx={{ minWidth: 220 }}
        >
          <MenuItem value="">Every cycle</MenuItem>
          {(cycles.data ?? []).map((cycle) => (
            <MenuItem key={cycle.id} value={String(cycle.id)}>
              {cycle.name}
            </MenuItem>
          ))}
        </TextField>
      )}

      {has('STATUS') && (
        <TextField
          select
          size="small"
          label="Status"
          value={values.status ?? ''}
          onChange={(event) => set('status', event.target.value)}
          sx={{ minWidth: 170 }}
        >
          <MenuItem value="">Any status</MenuItem>
          {TASK_STATUSES.map((status) => (
            <MenuItem key={status} value={status}>
              {status.toLowerCase().replace(/_/g, ' ')}
            </MenuItem>
          ))}
        </TextField>
      )}

      {has('LIMIT') && (
        <TextField
          size="small"
          type="number"
          label="How many"
          value={values.limit ?? '20'}
          onChange={(event) => set('limit', event.target.value)}
          inputProps={{ min: 1, max: 200 }}
          sx={{ width: 130 }}
        />
      )}
    </Box>
  );
}
