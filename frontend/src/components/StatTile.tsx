import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import LinearProgress from '@mui/material/LinearProgress';
import type { SvgIconComponent } from '@mui/icons-material';
import type { ReactNode } from 'react';

interface Props {
  label: string;
  value: ReactNode;
  unit?: string;
  caption?: string;
  icon?: SvgIconComponent;
  /** 0-100; renders the progress rail under the value when provided. */
  progress?: number;
  progressColor?: 'primary' | 'success' | 'warning' | 'error' | 'info';
  trailing?: ReactNode;
}

export function StatTile({
  label,
  value,
  unit,
  caption,
  icon: Icon,
  progress,
  progressColor = 'primary',
  trailing,
}: Props) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent
        sx={{ display: 'flex', flexDirection: 'column', gap: 2, height: '100%', p: 4, '&:last-child': { pb: 4 } }}
      >
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
          <Typography variant="overline" sx={{ color: 'text.secondary' }}>
            {label}
          </Typography>
          {Icon && (
            <Box
              sx={{
                width: 26,
                height: 26,
                borderRadius: 1.5,
                display: 'grid',
                placeItems: 'center',
                bgcolor: 'action.hover',
                color: 'primary.main',
                flexShrink: 0,
              }}
            >
              <Icon sx={{ fontSize: 15 }} />
            </Box>
          )}
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1.5, flexWrap: 'wrap' }}>
          <Typography
            className="tabular"
            sx={{ fontSize: 30, fontWeight: 700, lineHeight: 1.1, letterSpacing: '-0.02em' }}
          >
            {value}
          </Typography>
          {unit && (
            <Typography variant="body2" sx={{ color: 'text.secondary', fontWeight: 500 }}>
              {unit}
            </Typography>
          )}
          {trailing && <Box sx={{ ml: 'auto' }}>{trailing}</Box>}
        </Box>

        {progress !== undefined && (
          <LinearProgress
            variant="determinate"
            value={Math.min(Math.max(progress, 0), 100)}
            color={progressColor}
          />
        )}

        {caption && (
          <Typography variant="caption" sx={{ color: 'text.secondary', mt: 'auto' }}>
            {caption}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}
