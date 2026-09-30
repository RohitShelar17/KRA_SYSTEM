import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Rating from '@mui/material/Rating';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import ClearOutlined from '@mui/icons-material/ClearOutlined';

interface Props {
  label: string;
  hint?: string;
  // The API omits nulls, so an unrated field arrives as undefined.
  value: number | null | undefined;
  onChange: (value: number | null) => void;
  disabled?: boolean;
  required?: boolean;
}

const WORDS = ['Below expectation', 'Needs improvement', 'Meets expectation', 'Exceeds', 'Outstanding'];

/**
 * A 1-5 rating with half steps, and a way to leave it unrated. Unrated matters:
 * the score engine redistributes a parameter it cannot measure rather than
 * counting a blank as a zero, so a reviewer who has no view on teamwork should
 * be able to say so instead of guessing.
 */
export function RatingField({ label, hint, value, onChange, disabled, required }: Props) {
  const rating = value ?? null;
  const index = rating === null ? -1 : Math.ceil(rating) - 1;

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 3,
        py: 1.5,
        borderBottom: '1px solid',
        borderColor: 'divider',
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="body2" sx={{ fontWeight: 500 }}>
          {label}
          {required && <span style={{ color: '#DC2626' }}> *</span>}
        </Typography>
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
          {rating === null ? (hint ?? 'Not rated') : WORDS[index] ?? ''}
        </Typography>
      </Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexShrink: 0 }}>
        <Rating
          value={rating}
          precision={0.5}
          max={5}
          readOnly={disabled}
          onChange={(_, next) => onChange(next)}
        />
        <Typography variant="body2" className="tabular" sx={{ width: 28, color: 'text.secondary' }}>
          {rating === null ? '—' : rating.toFixed(1)}
        </Typography>
        <Tooltip title="Clear this rating">
          <span>
            <IconButton
              size="small"
              disabled={disabled || rating === null}
              onClick={() => onChange(null)}
              aria-label={`Clear ${label} rating`}
            >
              <ClearOutlined fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
      </Box>
    </Box>
  );
}
