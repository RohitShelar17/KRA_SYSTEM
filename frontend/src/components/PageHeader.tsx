import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

interface Props {
  title: string;
  description?: string;
  eyebrow?: string;
  actions?: ReactNode;
}

export function PageHeader({ title, description, eyebrow, actions }: Props) {
  return (
    <Box
      sx={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 3,
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        mb: 5,
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        {eyebrow && (
          <Typography variant="overline" sx={{ color: 'primary.main', display: 'block', mb: 0.5 }}>
            {eyebrow}
          </Typography>
        )}
        <Typography variant="h4" sx={{ mb: description ? 1 : 0 }}>
          {title}
        </Typography>
        {description && (
          <Typography variant="body2" sx={{ color: 'text.secondary', maxWidth: 640 }}>
            {description}
          </Typography>
        )}
      </Box>
      {actions && <Box sx={{ display: 'flex', gap: 2, flexShrink: 0 }}>{actions}</Box>}
    </Box>
  );
}
