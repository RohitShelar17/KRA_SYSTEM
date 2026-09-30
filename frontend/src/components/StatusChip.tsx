import Chip from '@mui/material/Chip';
import { useTheme } from '@mui/material/styles';
import { statusTone, statusToneDark, type StatusTone } from '@/theme/tokens';

const TONE_BY_STATUS: Record<string, StatusTone> = {
  ACTIVE: 'success',
  COMPLETED: 'success',
  APPROVED: 'success',
  IN_PROGRESS: 'active',
  SUBMITTED: 'active',
  UNDER_REVIEW: 'active',
  ON_LEAVE: 'pending',
  ON_NOTICE: 'pending',
  PENDING: 'pending',
  DRAFT: 'neutral',
  NOT_STARTED: 'neutral',
  INACTIVE: 'neutral',
  ON_HOLD: 'pending',
  BLOCKED: 'critical',
  REJECTED: 'critical',
  CANCELLED: 'critical',
  TERMINATED: 'critical',
};

interface Props {
  status: string;
  tone?: StatusTone;
  label?: string;
}

/** Encodes state in form as well as colour, so it reads at a glance in a grid. */
export function StatusChip({ status, tone, label }: Props) {
  const theme = useTheme();
  const resolved = tone ?? TONE_BY_STATUS[status] ?? 'neutral';
  const palette = theme.palette.mode === 'light' ? statusTone : statusToneDark;
  const colours = palette[resolved];

  return (
    <Chip
      size="small"
      label={label ?? status.replace(/_/g, ' ').toLowerCase()}
      sx={{
        backgroundColor: colours.bg,
        color: colours.fg,
        border: `1px solid ${colours.border}`,
        textTransform: 'capitalize',
      }}
    />
  );
}
