import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import CheckOutlined from '@mui/icons-material/CheckOutlined';
import ContentCopyOutlined from '@mui/icons-material/ContentCopyOutlined';
import type { Invite } from '@/types/domain';

interface Props {
  invite: Invite | null;
  onClose: () => void;
}

/**
 * What an administrator sees after issuing an invite.
 *
 * <p>The link is shown rather than hidden because it is the fallback when
 * email is off or slow. The wording is careful about what it is: a way to set
 * a password once, not a password — an administrator who thinks they are
 * handing over a credential will treat it far more casually than they should.
 */
export function InviteDialog({ invite, onClose }: Props) {
  const [copied, setCopied] = useState(false);

  // A fresh dialog should never open still showing the last "copied" tick.
  useEffect(() => {
    setCopied(false);
  }, [invite?.resetLink]);

  async function copy() {
    if (!invite) {
      return;
    }
    try {
      await navigator.clipboard.writeText(invite.resetLink);
      setCopied(true);
    } catch {
      // Clipboard access can be refused; the link is on screen to select by
      // hand, so this is not worth an error message.
      setCopied(false);
    }
  }

  return (
    <Dialog open={Boolean(invite)} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Sign-in invite for {invite?.employeeName}</DialogTitle>
      <DialogContent>
        <Alert severity={invite?.emailed ? 'success' : 'info'} sx={{ mb: 3 }}>
          {invite?.emailed
            ? `Emailed to ${invite.signInName}. The link below is the same one, in case it does not arrive.`
            : 'Email delivery is switched off on this deployment, so nothing was sent. Pass the link below on yourself.'}
        </Alert>

        <Typography variant="overline" sx={{ color: 'text.secondary' }}>
          Signs in as
        </Typography>
        <Typography variant="body2" sx={{ mb: 3 }}>
          {invite?.signInName}
        </Typography>

        <Typography variant="overline" sx={{ color: 'text.secondary' }}>
          Password link
        </Typography>
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            p: 2,
            mt: 0.5,
            mb: 2,
            border: 1,
            borderColor: 'divider',
            borderRadius: 1.5,
            bgcolor: 'action.hover',
          }}
        >
          <Typography
            sx={{
              flex: 1,
              fontFamily: 'monospace',
              fontSize: 12,
              wordBreak: 'break-all',
              userSelect: 'all',
            }}
          >
            {invite?.resetLink}
          </Typography>
          <Tooltip title={copied ? 'Copied' : 'Copy link'}>
            <IconButton size="small" onClick={() => void copy()}>
              {copied ? (
                <CheckOutlined sx={{ fontSize: 17, color: 'success.main' }} />
              ) : (
                <ContentCopyOutlined sx={{ fontSize: 17 }} />
              )}
            </IconButton>
          </Tooltip>
        </Box>

        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          This link lets {invite?.employeeName?.split(' ')[0]} choose their own password. It works
          once, expires on {formatExpiry(invite?.expiresAt)}, and any invite issued before this one
          has stopped working. It is not a password — nobody, including you, can see what they
          choose.
        </Typography>
      </DialogContent>
      <DialogActions sx={{ px: 6, pb: 4 }}>
        <Button onClick={onClose} variant="contained">
          Done
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function formatExpiry(value: string | undefined): string {
  if (!value) {
    return '—';
  }
  return new Date(value).toLocaleString('en-GB', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
