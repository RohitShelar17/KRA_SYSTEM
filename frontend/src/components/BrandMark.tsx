import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

interface Props {
  compact?: boolean;
  subtitle?: string;
}

/** The KRAPulse mark: indigo tile, check path, sky accent dot. */
export function BrandMark({ compact = false, subtitle = 'Enterprise Ops' }: Props) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2.5 }}>
      <Box
        component="svg"
        viewBox="0 0 32 32"
        sx={{ width: 32, height: 32, flexShrink: 0 }}
        aria-hidden="true"
      >
        <rect width="32" height="32" rx="8" fill="#4338CA" />
        <path
          d="M8 16.5L13 21.5L24 10.5"
          stroke="#FFFFFF"
          strokeWidth="3.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        />
        <circle cx="24" cy="10.5" r="3" fill="#60A5FA" />
      </Box>
      {!compact && (
        <Box sx={{ minWidth: 0 }}>
          <Typography
            component="span"
            sx={{ fontSize: 17, fontWeight: 800, letterSpacing: '-0.02em', lineHeight: 1.15, display: 'block' }}
          >
            KRA
            <Box component="span" sx={{ color: 'primary.main' }}>
              Pulse
            </Box>
          </Typography>
          <Typography
            variant="caption"
            sx={{ color: 'text.secondary', fontSize: 10.5, letterSpacing: '0.08em', textTransform: 'uppercase' }}
          >
            {subtitle}
          </Typography>
        </Box>
      )}
    </Box>
  );
}
