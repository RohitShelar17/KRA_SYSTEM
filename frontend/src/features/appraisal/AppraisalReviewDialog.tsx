import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import ReplayOutlined from '@mui/icons-material/ReplayOutlined';
import SendOutlined from '@mui/icons-material/SendOutlined';
import SaveOutlined from '@mui/icons-material/SaveOutlined';
import SkipNextOutlined from '@mui/icons-material/SkipNextOutlined';
import { appraisalApi, type ReviewPayload } from '@/api/appraisal';
import { toErrorMessage } from '@/api/axiosClient';
import { FullPageLoader } from '@/components/LoadingState';
import { StatusChip } from '@/components/StatusChip';
import type { AppraisalStep, ManagerReview, ReviewerRole } from '@/types/appraisal';
import { AppraisalHistory } from './AppraisalHistory';
import { RatingField } from './RatingField';
import { ReviewSummaryCard } from './ReviewSummaryCard';
import { WorkflowTracker, formatRating, stepName } from './WorkflowTracker';

interface Props {
  appraisalId: number;
  onClose: () => void;
  onChanged: () => void;
}

type RatingKey =
  | 'quality'
  | 'productivity'
  | 'timeliness'
  | 'teamwork'
  | 'communication'
  | 'initiative'
  | 'problemSolving'
  | 'discipline'
  | 'kra'
  | 'kpi';

const COMPETENCIES: { key: RatingKey; label: string; hint?: string; required?: boolean }[] = [
  {
    key: 'quality',
    label: 'Quality of work',
    hint: 'Required — the score formula reads this one directly',
    required: true,
  },
  { key: 'productivity', label: 'Productivity' },
  { key: 'timeliness', label: 'Timeliness' },
  { key: 'teamwork', label: 'Teamwork' },
  { key: 'communication', label: 'Communication' },
  { key: 'initiative', label: 'Initiative' },
  { key: 'problemSolving', label: 'Problem solving' },
  { key: 'discipline', label: 'Discipline' },
  { key: 'kra', label: 'KRA achievement', hint: 'Your view, alongside the measured figure' },
  { key: 'kpi', label: 'KPI achievement' },
];

const ROLE_FOR_STEP: Record<AppraisalStep, ReviewerRole | null> = {
  SELF_ASSESSMENT: null,
  MANAGER_REVIEW: 'MANAGER',
  DEPARTMENT_HEAD_REVIEW: 'DEPARTMENT_HEAD',
  HR_REVIEW: 'HR',
  COMPLETED: null,
};

type Ratings = Record<RatingKey | 'overall', number | null>;

const NO_RATINGS: Ratings = {
  quality: null,
  productivity: null,
  timeliness: null,
  teamwork: null,
  communication: null,
  initiative: null,
  problemSolving: null,
  discipline: null,
  kra: null,
  kpi: null,
  overall: null,
};

/**
 * One appraisal, opened for review. The employee's account on the left, the
 * reviewer's ratings on the right, and the two hand-off actions — pass it on,
 * or send it back with a reason.
 */
export function AppraisalReviewDialog({ appraisalId, onClose, onChanged }: Props) {
  const [ratings, setRatings] = useState<Ratings>(NO_RATINGS);
  const [strengths, setStrengths] = useState('');
  const [improvements, setImprovements] = useState('');
  const [comments, setComments] = useState('');
  const [handOffNote, setHandOffNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const detail = useQuery({
    queryKey: ['appraisals', 'detail', appraisalId],
    queryFn: () => appraisalApi.detail(appraisalId),
  });

  const record = detail.data;
  const assessment = record?.selfAssessment ?? null;

  // The draft being written belongs to the role that owns the current step. It
  // only exists once something has been saved, so a first-time reviewer starts
  // from a blank form rather than waiting for a row to appear.
  const draft: ManagerReview | null = useMemo(() => {
    const role = ROLE_FOR_STEP[record?.currentStep ?? 'COMPLETED'];
    if (!record || !role) {
      return null;
    }
    return record.reviews.find((review) => review.reviewerRole === role && review.editable) ?? null;
  }, [record]);

  useEffect(() => {
    if (draft) {
      setRatings({
        ...NO_RATINGS,
        ...Object.fromEntries(
          Object.entries(draft.ratings ?? {}).map(([key, value]) => [key, Number(value)]),
        ),
        overall: draft.overallRating == null ? null : Number(draft.overallRating),
      } as Ratings);
      setStrengths(draft.strengths ?? '');
      setImprovements(draft.improvements ?? '');
      setComments(draft.comments ?? '');
    } else {
      setRatings(NO_RATINGS);
      setStrengths('');
      setImprovements('');
      setComments('');
    }
    setError(null);
    setNotice(null);
  }, [draft?.id, record?.id, record?.currentStep]);

  const payload = useMemo<ReviewPayload>(
    () => ({
      qualityRating: ratings.quality,
      productivityRating: ratings.productivity,
      timelinessRating: ratings.timeliness,
      teamworkRating: ratings.teamwork,
      communicationRating: ratings.communication,
      initiativeRating: ratings.initiative,
      problemSolvingRating: ratings.problemSolving,
      disciplineRating: ratings.discipline,
      kraRating: ratings.kra,
      kpiRating: ratings.kpi,
      overallRating: ratings.overall,
      strengths: strengths || null,
      improvements: improvements || null,
      comments: comments || null,
    }),
    [ratings, strengths, improvements, comments],
  );

  function after(message: string) {
    setError(null);
    setNotice(message);
    onChanged();
    void detail.refetch();
  }

  const save = useMutation({
    mutationFn: () => appraisalApi.saveReview(appraisalId, payload),
    onSuccess: () => after(`Draft saved at ${new Date().toLocaleTimeString('en-GB')}`),
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const submit = useMutation({
    mutationFn: () => appraisalApi.submitReview(appraisalId, payload),
    onSuccess: () => after('Review submitted. The appraisal has moved to the next step.'),
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const advance = useMutation({
    mutationFn: (skip: boolean) => appraisalApi.advance(appraisalId, handOffNote || undefined, skip),
    onSuccess: () => {
      setHandOffNote('');
      after('Moved on to the next step.');
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const sendBack = useMutation({
    mutationFn: () => appraisalApi.returnStep(appraisalId, handOffNote),
    onSuccess: () => {
      setHandOffNote('');
      after('Sent back with your note.');
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const busy = save.isPending || submit.isPending || advance.isPending || sendBack.isPending;
  const canReview = Boolean(record?.canReview);
  const submitted = record?.reviews.filter((review) => review.status === 'SUBMITTED') ?? [];

  // Mirrors the server rule, so the reviewer is told before the round trip.
  const derivedOverall = useMemo(() => {
    if (ratings.overall !== null) {
      return ratings.overall;
    }
    const competencies = COMPETENCIES.filter((entry) => entry.key !== 'kra' && entry.key !== 'kpi')
      .map((entry) => ratings[entry.key])
      .filter((value): value is number => value !== null);
    return competencies.length === 0
      ? null
      : competencies.reduce((total, value) => total + value, 0) / competencies.length;
  }, [ratings]);

  const blockers: string[] = [];
  if (canReview) {
    if (ratings.quality === null) {
      blockers.push('a quality rating');
    }
    if (derivedOverall === null) {
      blockers.push('an overall rating, or enough competencies to derive one');
    }
    if (!comments.trim() && !strengths.trim()) {
      blockers.push('something written the employee can act on');
    }
  }

  return (
    <Dialog open fullWidth maxWidth="lg" onClose={busy ? undefined : onClose}>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 3, alignItems: 'center' }}>
          <Box>
            <Typography variant="h5">
              {record?.employeeName ?? 'Appraisal'}
              {record?.designation ? ` · ${record.designation}` : ''}
            </Typography>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {record ? `${record.cycleName} · ${record.periodLabel}` : ''}
            </Typography>
          </Box>
          {record && <StatusChip status={record.status} label={stepName(record.workflow, record.currentStep)} />}
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        {detail.isLoading && <FullPageLoader label="Loading appraisal" />}

        {record && (
          <>
            <Box sx={{ mb: 4 }}>
              <WorkflowTracker
                workflow={record.workflow}
                currentStep={record.currentStep}
                completed={record.status === 'COMPLETED'}
              />
            </Box>

            {error && (
              <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
                {error}
              </Alert>
            )}
            {notice && (
              <Alert severity="success" sx={{ mb: 3 }} onClose={() => setNotice(null)}>
                {notice}
              </Alert>
            )}

            <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
              <Box>
                <Typography variant="h6" sx={{ mb: 2 }}>
                  Their self assessment
                </Typography>
                {!assessment || assessment.status !== 'SUBMITTED' ? (
                  <Alert severity="info">
                    {assessment
                      ? 'Started, but not submitted yet.'
                      : 'Nothing submitted yet. You can still record your review once it reaches your step.'}
                  </Alert>
                ) : (
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
                    <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
                      <StatusChip status="SUBMITTED" />
                      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                        Self rating {formatRating(assessment.selfRating)}
                      </Typography>
                    </Box>
                    <Passage title="Achievements" body={assessment.achievements} />
                    <Passage title="Challenges" body={assessment.challenges} />
                    <Passage title="Skills developed" body={assessment.skillsDeveloped} />
                    <Passage title="Wants to improve" body={assessment.improvementAreas} />
                    <Passage title="Support needed" body={assessment.trainingNeeded} />
                    <Passage title="Next period goals" body={assessment.futureGoals} />
                    <Passage title="Comments" body={assessment.comments} />
                  </Box>
                )}

                <Divider sx={{ my: 4 }} />

                <Typography variant="h6" sx={{ mb: 2 }}>
                  Measured this period
                </Typography>
                <Row
                  label="Computed score"
                  value={record.computedScore == null ? 'Not computed yet' : Number(record.computedScore).toFixed(2)}
                />
                <Row
                  label="Final score"
                  value={record.finalScore == null ? 'Pending sign-off' : Number(record.finalScore).toFixed(2)}
                />
                <Row label="Reporting manager" value={record.managerName ?? '—'} />

                {submitted.length > 0 && (
                  <>
                    <Divider sx={{ my: 4 }} />
                    <Typography variant="h6" sx={{ mb: 2 }}>
                      Reviews already submitted
                    </Typography>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
                      {submitted.map((review) => (
                        <ReviewSummaryCard key={review.id} review={review} />
                      ))}
                    </Box>
                  </>
                )}

                <Divider sx={{ my: 4 }} />
                <Typography variant="h6" sx={{ mb: 2 }}>
                  History
                </Typography>
                <AppraisalHistory history={record.history} />
              </Box>

              <Box>
                <Typography variant="h6" sx={{ mb: 0.5 }}>
                  Your review
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
                  Leave anything you have no view on unrated — an unrated competency is left out
                  rather than counted as a zero.
                </Typography>

                {!canReview && (
                  <Alert severity="info" sx={{ mb: 3 }}>
                    {record.status === 'COMPLETED'
                      ? 'This appraisal is complete.'
                      : `This appraisal is at ${stepName(record.workflow, record.currentStep).toLowerCase()}, so there is nothing for you to write here.`}
                  </Alert>
                )}

                <Box sx={{ opacity: canReview ? 1 : 0.6 }}>
                  {COMPETENCIES.map((entry) => (
                    <RatingField
                      key={entry.key}
                      label={entry.label}
                      hint={entry.hint}
                      required={entry.required}
                      disabled={!canReview}
                      value={ratings[entry.key]}
                      onChange={(value) => setRatings({ ...ratings, [entry.key]: value })}
                    />
                  ))}
                  <Box sx={{ mt: 2 }}>
                    <RatingField
                      label="Overall rating"
                      hint={
                        derivedOverall === null
                          ? 'Derived from the competencies if you leave it blank'
                          : `Currently ${formatRating(derivedOverall)}${ratings.overall === null ? ' (derived)' : ''}`
                      }
                      disabled={!canReview}
                      value={ratings.overall}
                      onChange={(value) => setRatings({ ...ratings, overall: value })}
                    />
                  </Box>

                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mt: 4 }}>
                    <TextField
                      label="Strengths"
                      value={strengths}
                      disabled={!canReview}
                      onChange={(event) => setStrengths(event.target.value)}
                      multiline
                      minRows={2}
                      inputProps={{ maxLength: 4000 }}
                      fullWidth
                    />
                    <TextField
                      label="Areas to improve"
                      value={improvements}
                      disabled={!canReview}
                      onChange={(event) => setImprovements(event.target.value)}
                      multiline
                      minRows={2}
                      inputProps={{ maxLength: 4000 }}
                      fullWidth
                    />
                    <TextField
                      label="Comments"
                      value={comments}
                      disabled={!canReview}
                      onChange={(event) => setComments(event.target.value)}
                      multiline
                      minRows={2}
                      inputProps={{ maxLength: 4000 }}
                      fullWidth
                    />
                  </Box>

                  {canReview && blockers.length > 0 && (
                    <Alert severity="warning" sx={{ mt: 3 }}>
                      Before submitting, add {blockers.join(', ')}.
                    </Alert>
                  )}

                  {canReview && (
                    <Box sx={{ display: 'flex', gap: 2, mt: 3, justifyContent: 'flex-end' }}>
                      <Button
                        variant="outlined"
                        startIcon={<SaveOutlined />}
                        disabled={busy}
                        onClick={() => save.mutate()}
                      >
                        Save draft
                      </Button>
                      <Button
                        variant="contained"
                        startIcon={<SendOutlined />}
                        disabled={busy || blockers.length > 0}
                        onClick={() => submit.mutate()}
                      >
                        Submit review
                      </Button>
                    </Box>
                  )}
                </Box>

                <Divider sx={{ my: 4 }} />

                <Typography variant="h6" sx={{ mb: 0.5 }}>
                  Hand-off
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
                  Send it back for more detail, or move it on without a review where the workflow
                  allows it.
                </Typography>
                <TextField
                  label="Note"
                  placeholder="Required when sending back"
                  value={handOffNote}
                  onChange={(event) => setHandOffNote(event.target.value)}
                  multiline
                  minRows={2}
                  inputProps={{ maxLength: 1000 }}
                  fullWidth
                />
                <Box sx={{ display: 'flex', gap: 2, mt: 3, justifyContent: 'flex-end' }}>
                  <Button
                    color="warning"
                    variant="outlined"
                    startIcon={<ReplayOutlined />}
                    disabled={busy || record.status === 'COMPLETED' || !handOffNote.trim()}
                    onClick={() => sendBack.mutate()}
                  >
                    Send back
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<SkipNextOutlined />}
                    disabled={busy || !record.canAdvance}
                    onClick={() => advance.mutate(true)}
                  >
                    Skip this step
                  </Button>
                  <Button
                    variant="outlined"
                    disabled={busy || !record.canAdvance}
                    onClick={() => advance.mutate(false)}
                  >
                    Approve and move on
                  </Button>
                </Box>
              </Box>
            </Box>
          </>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function Passage({ title, body }: { title: string; body: string | null }) {
  if (!body) {
    return null;
  }
  return (
    <Box>
      <Typography variant="caption" sx={{ color: 'text.secondary', textTransform: 'uppercase' }}>
        {title}
      </Typography>
      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
        {body}
      </Typography>
    </Box>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 3, py: 0.75 }}>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        {label}
      </Typography>
      <Typography variant="body2" className="tabular" sx={{ fontWeight: 500 }}>
        {value}
      </Typography>
    </Box>
  );
}
