import Box from '@mui/material/Box';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import { semantic, slate } from '@/theme/tokens';
import { formatDuration, minutesOfDay, toClock } from '@/utils/time';
import type { TimelineSegment } from '@/types/work';

interface Props {
  segments: TimelineSegment[];
  shiftStart: string;
  shiftEnd: string;
  onSelectGap?: (start: string, end: string) => void;
  onSelectEntry?: (entryId: number) => void;
}

const KIND_FALLBACK: Record<string, string> = {
  WORK: '#4338CA',
  MEETING: semantic.amber,
  BREAK: slate.s400,
  ADMIN: slate.s500,
  TRAINING: semantic.emerald,
  SUPPORT: semantic.rose,
  OTHER: '#6366F1',
};

/**
 * The day as a single proportional band, with hour ticks above it.
 *
 * <p>Unallocated stretches are clickable: the fastest way to fix a gap is to
 * start an entry that already knows the times it needs to fill.</p>
 */
export function TimelineRibbon({ segments, shiftStart, shiftEnd, onSelectGap, onSelectEntry }: Props) {
  const theme = useTheme();
  const dayStart = minutesOfDay(shiftStart);
  const dayEnd = minutesOfDay(shiftEnd);
  const span = Math.max(dayEnd - dayStart, 1);

  const firstHour = Math.ceil(dayStart / 60);
  const lastHour = Math.floor(dayEnd / 60);
  const ticks: number[] = [];
  for (let hour = firstHour; hour <= lastHour; hour += 1) {
    ticks.push(hour);
  }

  return (
    <Box>
      <Box sx={{ position: 'relative', height: 18, mb: 1 }}>
        {ticks.map((hour) => {
          const left = ((hour * 60 - dayStart) / span) * 100;
          return (
            <Typography
              key={hour}
              variant="caption"
              className="tabular"
              sx={{
                position: 'absolute',
                left: `${left}%`,
                transform: 'translateX(-50%)',
                color: 'text.disabled',
                fontSize: 10.5,
                whiteSpace: 'nowrap',
              }}
            >
              {String(hour).padStart(2, '0')}:00
            </Typography>
          );
        })}
      </Box>

      <Box
        sx={{
          display: 'flex',
          height: 46,
          borderRadius: 1.5,
          overflow: 'hidden',
          border: 1,
          borderColor: 'divider',
          bgcolor: theme.palette.mode === 'light' ? slate.s50 : 'rgba(148,163,184,0.06)',
        }}
      >
        {segments.map((segment, index) => {
          const width = (segment.durationMinutes / span) * 100;
          const isGap = segment.kind === 'UNALLOCATED';
          const colour = isGap
            ? 'transparent'
            : segment.colourHex ?? KIND_FALLBACK[segment.kind] ?? '#4338CA';

          return (
            <Tooltip
              key={`${segment.startTime}-${index}`}
              title={
                <Box sx={{ py: 0.5 }}>
                  <Typography sx={{ fontSize: 12, fontWeight: 600 }}>{segment.label}</Typography>
                  <Typography sx={{ fontSize: 11, opacity: 0.85 }}>
                    {toClock(segment.startTime)} – {toClock(segment.endTime)} ·{' '}
                    {formatDuration(segment.durationMinutes)}
                  </Typography>
                  {isGap && (
                    <Typography sx={{ fontSize: 11, opacity: 0.85 }}>Click to fill this gap</Typography>
                  )}
                </Box>
              }
            >
              <Box
                onClick={() => {
                  if (isGap) {
                    onSelectGap?.(segment.startTime, segment.endTime);
                  } else if (segment.entryId) {
                    onSelectEntry?.(segment.entryId);
                  }
                }}
                sx={{
                  width: `${width}%`,
                  minWidth: 2,
                  bgcolor: colour,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  px: width > 8 ? 2 : 0,
                  borderRight: index === segments.length - 1 ? 0 : 1,
                  borderColor: 'rgba(255,255,255,0.35)',
                  // Gaps read as absence, not as another activity.
                  backgroundImage: isGap
                    ? `repeating-linear-gradient(45deg, ${theme.palette.error.main}22 0 6px, transparent 6px 12px)`
                    : 'none',
                  transition: 'filter 120ms',
                  '&:hover': { filter: 'brightness(1.08)' },
                }}
              >
                {width > 12 && (
                  <Box sx={{ minWidth: 0 }}>
                    <Typography
                      sx={{
                        fontSize: 11,
                        fontWeight: 600,
                        color: isGap ? 'error.main' : '#FFFFFF',
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }}
                    >
                      {segment.label}
                    </Typography>
                    <Typography
                      className="tabular"
                      sx={{
                        fontSize: 10,
                        color: isGap ? 'error.main' : 'rgba(255,255,255,0.85)',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {toClock(segment.startTime)}–{toClock(segment.endTime)}
                    </Typography>
                  </Box>
                )}
              </Box>
            </Tooltip>
          );
        })}
      </Box>
    </Box>
  );
}
