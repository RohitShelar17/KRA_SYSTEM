import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import ArrowForwardOutlined from '@mui/icons-material/ArrowForwardOutlined';
import CheckCircleOutlined from '@mui/icons-material/CheckCircleOutlined';
import ReplayOutlined from '@mui/icons-material/ReplayOutlined';
import SendOutlined from '@mui/icons-material/SendOutlined';
import SkipNextOutlined from '@mui/icons-material/SkipNextOutlined';
import type { StepActionEntry, StepActionType } from '@/types/appraisal';
import { humanise } from './WorkflowTracker';

const ICONS: Record<StepActionType, typeof SendOutlined> = {
  SUBMITTED: SendOutlined,
  APPROVED: ArrowForwardOutlined,
  RETURNED: ReplayOutlined,
  SKIPPED: SkipNextOutlined,
  COMPLETED: CheckCircleOutlined,
};

const COLOURS: Record<StepActionType, string> = {
  SUBMITTED: 'info.main',
  APPROVED: 'primary.main',
  RETURNED: 'warning.main',
  SKIPPED: 'text.secondary',
  COMPLETED: 'success.main',
};

/**
 * Every hand-off on the record, in order. An appraisal that was returned twice
 * should say so: the trail is what makes a disputed rating auditable.
 */
export function AppraisalHistory({ history }: { history: StepActionEntry[] }) {
  if (history.length === 0) {
    return (
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        Nothing has happened yet.
      </Typography>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column' }}>
      {history.map((entry, index) => {
        const Icon = ICONS[entry.action] ?? SendOutlined;
        return (
          <Box key={entry.id} sx={{ display: 'flex', gap: 2.5 }}>
            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              <Box
                sx={{
                  width: 30,
                  height: 30,
                  borderRadius: '50%',
                  border: '1px solid',
                  borderColor: 'divider',
                  bgcolor: 'background.paper',
                  display: 'grid',
                  placeItems: 'center',
                  color: COLOURS[entry.action] ?? 'text.secondary',
                }}
              >
                <Icon sx={{ fontSize: 16 }} />
              </Box>
              {index < history.length - 1 && (
                <Box sx={{ flex: 1, width: '1px', bgcolor: 'divider', my: 0.5 }} />
              )}
            </Box>
            <Box sx={{ pb: index < history.length - 1 ? 3 : 0, minWidth: 0 }}>
              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                {humanise(entry.action)} · {humanise(entry.stepType)}
              </Typography>
              <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                {entry.actorName ?? 'System'} ·{' '}
                {new Date(entry.createdAt).toLocaleString('en-GB', {
                  day: '2-digit',
                  month: 'short',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </Typography>
              {entry.comments && (
                <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
                  “{entry.comments}”
                </Typography>
              )}
            </Box>
          </Box>
        );
      })}
    </Box>
  );
}
