import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import { formatDayLabel } from '@/utils/time';

export interface UnlockTarget {
  employeeId: number;
  employeeName: string;
  workDate: string;
}

interface Props {
  target: UnlockTarget | null;
  hours: number;
  busy?: boolean;
  error?: string | null;
  onConfirm: (reason: string) => void;
  onClose: () => void;
}

/**
 * Asks for the one thing the API will not do without: why.
 *
 * <p>An exception to a rule that nobody has to justify stops being an
 * exception, and this row is the only record of why the window was set aside.
 */
export function UnlockDialog({ target, hours, busy, error, onConfirm, onClose }: Props) {
  const [reason, setReason] = useState('');

  // A dialog opened for a different person should not still hold the last one's reason.
  useEffect(() => {
    setReason('');
  }, [target?.employeeId, target?.workDate]);

  const tooShort = reason.trim().length < 3;

  return (
    <Dialog open={Boolean(target)} onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Reopen {formatDayLabel(target?.workDate ?? '')}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 3 }}>
          {target?.employeeName} will be able to file this one date for the next {hours} hours. No
          other date changes, and nothing already filed is affected.
        </DialogContentText>

        {error && (
          <Alert severity="error" sx={{ mb: 3 }}>
            {error}
          </Alert>
        )}

        <TextField
          autoFocus
          fullWidth
          multiline
          minRows={2}
          label="Why is this being reopened?"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          inputProps={{ maxLength: 500 }}
          helperText="Recorded against the exception, with your name and the time."
        />
      </DialogContent>
      <DialogActions sx={{ px: 6, pb: 4 }}>
        <Button onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button
          variant="contained"
          disabled={busy || tooShort}
          onClick={() => onConfirm(reason.trim())}
        >
          Reopen the date
        </Button>
      </DialogActions>
    </Dialog>
  );
}
