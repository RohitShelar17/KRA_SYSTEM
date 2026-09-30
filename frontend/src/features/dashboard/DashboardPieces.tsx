import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useTheme } from '@mui/material/styles';
import type { BandSlice, PersonScore, TeamScore, TrendPoint } from '@/types/insight';

/** Score with two decimals, or an em dash when nothing was measured. */
export function score(value: number | null | undefined): string {
  return value == null ? '—' : Number(value).toFixed(2);
}

export function percent(value: number | null | undefined): string {
  return value == null ? '—' : `${Number(value).toFixed(1)}%`;
}

/** How many people landed in each band — the shape of a department, at a glance. */
export function BandDistribution({ bands }: { bands: BandSlice[] }) {
  const theme = useTheme();

  if (bands.length === 0) {
    return <Nothing>No scores have been computed for this period yet.</Nothing>;
  }

  return (
    <Box sx={{ height: 240 }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={bands} margin={{ top: 4, right: 8, bottom: 4, left: -24 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} vertical={false} />
          <XAxis
            dataKey="name"
            tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
            tickLine={false}
            axisLine={{ stroke: theme.palette.divider }}
          />
          <YAxis
            allowDecimals={false}
            tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
            tickLine={false}
            axisLine={false}
          />
          <ChartTooltip
            cursor={{ fill: theme.palette.action.hover }}
            contentStyle={{
              backgroundColor: theme.palette.background.paper,
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 8,
              fontSize: 12,
            }}
          />
          <Bar dataKey="count" radius={[4, 4, 0, 0]} maxBarSize={48}>
            {bands.map((band, index) => (
              <Cell key={index} fill={band.colourHex ?? theme.palette.primary.main} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </Box>
  );
}

/** A line of averages or rates over time. */
export function TrendLine({ points, suffix = '' }: { points: TrendPoint[]; suffix?: string }) {
  const theme = useTheme();
  const measured = points.filter((point) => point.value != null);

  if (measured.length === 0) {
    return <Nothing>Nothing measured over this window yet.</Nothing>;
  }

  return (
    <Box sx={{ height: 240 }}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={points} margin={{ top: 4, right: 12, bottom: 4, left: -24 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} vertical={false} />
          <XAxis
            dataKey="label"
            tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
            tickLine={false}
            axisLine={{ stroke: theme.palette.divider }}
          />
          <YAxis
            tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
            tickLine={false}
            axisLine={false}
          />
          <ChartTooltip
            formatter={(value: number) => `${value}${suffix}`}
            contentStyle={{
              backgroundColor: theme.palette.background.paper,
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 8,
              fontSize: 12,
            }}
          />
          {/* connectNulls is deliberately off: a month nobody scored should
              read as a gap, not as a straight line through it. */}
          <Line
            type="monotone"
            dataKey="value"
            stroke={theme.palette.primary.main}
            strokeWidth={2}
            dot={{ r: 3 }}
            connectNulls={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </Box>
  );
}

export function TeamComparison({ teams }: { teams: TeamScore[] }) {
  if (teams.length === 0) {
    return <Nothing>No department has a scored month yet.</Nothing>;
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {teams.map((team) => (
        <Box key={team.name} sx={{ display: 'flex', justifyContent: 'space-between', gap: 3 }}>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {team.name ?? 'Unassigned'}
          </Typography>
          <Typography variant="body2" className="tabular" sx={{ fontWeight: 500 }}>
            {score(team.average)}{' '}
            <Typography component="span" variant="caption" sx={{ color: 'text.disabled' }}>
              ({team.scored})
            </Typography>
          </Typography>
        </Box>
      ))}
    </Box>
  );
}

export function PeopleList({ people, empty }: { people: PersonScore[]; empty: string }) {
  if (people.length === 0) {
    return <Nothing>{empty}</Nothing>;
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {people.map((person) => (
        <Box
          key={`${person.employeeCode}-${person.name}`}
          sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 3 }}
        >
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2">{person.name}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {[person.employeeCode, person.department].filter(Boolean).join(' · ')}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            {person.band && <Chip size="small" variant="outlined" label={person.band} />}
            <Typography variant="body2" className="tabular" sx={{ fontWeight: 500 }}>
              {score(person.score)}
            </Typography>
          </Box>
        </Box>
      ))}
    </Box>
  );
}

export function Nothing({ children }: { children: React.ReactNode }) {
  return (
    <Typography variant="body2" sx={{ color: 'text.secondary', py: 6, textAlign: 'center' }}>
      {children}
    </Typography>
  );
}
