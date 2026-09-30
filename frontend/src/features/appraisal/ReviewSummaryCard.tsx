import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Rating from '@mui/material/Rating';
import Typography from '@mui/material/Typography';
import type { ManagerReview } from '@/types/appraisal';
import { formatRating, humanise } from './WorkflowTracker';

const LABELS: Record<string, string> = {
  quality: 'Quality of work',
  productivity: 'Productivity',
  timeliness: 'Timeliness',
  teamwork: 'Teamwork',
  communication: 'Communication',
  initiative: 'Initiative',
  problemSolving: 'Problem solving',
  discipline: 'Discipline',
  kra: 'KRA achievement',
  kpi: 'KPI achievement',
};

/** A submitted review, read-only: what the reviewer rated and what they wrote. */
export function ReviewSummaryCard({ review }: { review: ManagerReview }) {
  const entries = Object.entries(review.ratings ?? {});

  return (
    <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 4 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 3, mb: 3 }}>
        <Box>
          <Typography variant="subtitle2">{review.reviewerName ?? 'Reviewer'}</Typography>
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            {humanise(review.reviewerRole)}
            {review.submittedAt
              ? ` · ${new Date(review.submittedAt).toLocaleDateString('en-GB')}`
              : ''}
          </Typography>
        </Box>
        <Chip
          size="small"
          color="primary"
          variant="outlined"
          label={`Overall ${formatRating(review.overallRating)}`}
        />
      </Box>

      {entries.length > 0 && (
        <Box
          sx={{
            display: 'grid',
            gap: 1.5,
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
            mb: entries.length > 0 ? 3 : 0,
          }}
        >
          {entries.map(([key, value]) => (
            <Box key={key} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {LABELS[key] ?? key}
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Rating value={Number(value)} precision={0.5} size="small" readOnly />
                <Typography variant="body2" className="tabular">
                  {formatRating(Number(value))}
                </Typography>
              </Box>
            </Box>
          ))}
        </Box>
      )}

      {review.strengths && <Passage title="Strengths" body={review.strengths} />}
      {review.improvements && <Passage title="Areas to improve" body={review.improvements} />}
      {review.comments && <Passage title="Comments" body={review.comments} />}
    </Box>
  );
}

function Passage({ title, body }: { title: string; body: string }) {
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="caption" sx={{ color: 'text.secondary', textTransform: 'uppercase' }}>
        {title}
      </Typography>
      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
        {body}
      </Typography>
    </Box>
  );
}
