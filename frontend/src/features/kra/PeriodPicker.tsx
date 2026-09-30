import Box from '@mui/material/Box';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import type { PeriodType } from '@/types/kra';

export interface PeriodSelection {
  periodType: PeriodType;
  year: number;
  periodIndex: number;
}

const MONTHS = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

/** Review periods only: daily and weekly targets exist, but nobody is appraised on them. */
const TYPES: PeriodType[] = ['MONTHLY', 'QUARTERLY', 'ANNUAL'];

export function PeriodPicker({
  value,
  onChange,
}: {
  value: PeriodSelection;
  onChange: (next: PeriodSelection) => void;
}) {
  const years = yearRange();

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3 }}>
      <TextField
        select
        size="small"
        label="Cycle"
        value={value.periodType}
        onChange={(event) => {
          const periodType = event.target.value as PeriodType;
          onChange({ ...value, periodType, periodIndex: defaultIndex(periodType) });
        }}
        sx={{ minWidth: 150 }}
      >
        {TYPES.map((type) => (
          <MenuItem key={type} value={type}>
            {type.charAt(0) + type.slice(1).toLowerCase()}
          </MenuItem>
        ))}
      </TextField>

      {value.periodType !== 'ANNUAL' && (
        <TextField
          select
          size="small"
          label="Period"
          value={value.periodIndex}
          onChange={(event) => onChange({ ...value, periodIndex: Number(event.target.value) })}
          sx={{ minWidth: 170 }}
        >
          {periodOptions(value.periodType).map((option) => (
            <MenuItem key={option.value} value={option.value}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>
      )}

      <TextField
        select
        size="small"
        label="Year"
        value={value.year}
        onChange={(event) => onChange({ ...value, year: Number(event.target.value) })}
        sx={{ minWidth: 120 }}
      >
        {years.map((year) => (
          <MenuItem key={year} value={year}>
            {year}
          </MenuItem>
        ))}
      </TextField>
    </Box>
  );
}

export function currentPeriod(periodType: PeriodType = 'QUARTERLY'): PeriodSelection {
  return {
    periodType,
    year: new Date().getFullYear(),
    periodIndex: defaultIndex(periodType),
  };
}

function defaultIndex(periodType: PeriodType): number {
  const now = new Date();
  switch (periodType) {
    case 'MONTHLY':
      return now.getMonth() + 1;
    case 'QUARTERLY':
      return Math.floor(now.getMonth() / 3) + 1;
    default:
      return 1;
  }
}

function periodOptions(periodType: PeriodType): { value: number; label: string }[] {
  if (periodType === 'MONTHLY') {
    return MONTHS.map((label, index) => ({ value: index + 1, label }));
  }
  return [1, 2, 3, 4].map((quarter) => ({
    value: quarter,
    label: `Q${quarter} · ${MONTHS[(quarter - 1) * 3].slice(0, 3)}–${MONTHS[quarter * 3 - 1].slice(0, 3)}`,
  }));
}

function yearRange(): number[] {
  const current = new Date().getFullYear();
  return [current + 1, current, current - 1, current - 2];
}
