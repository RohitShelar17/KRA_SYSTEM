import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import LockOutlined from '@mui/icons-material/LockOutlined';
import SendOutlined from '@mui/icons-material/SendOutlined';
import SaveOutlined from '@mui/icons-material/SaveOutlined';
import { appraisalApi, type SelfAssessmentPayload } from '@/api/appraisal';
import { toErrorMessage } from '@/api/axiosClient';
import { EmptyState } from '@/components/EmptyState';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { AppraisalHistory } from './AppraisalHistory';
import { RatingField } from './RatingField';
import { ReviewSummaryCard } from './ReviewSummaryCard';
import { WorkflowTracker, formatRating, stepName } from './WorkflowTracker';

interface FormState {
  achievements: string;
  challenges: string;
  skillsDeveloped: string;
  improvementAreas: string;
  trainingNeeded: string;
  futureGoals: string;
  comments: string;
  selfRating: number | null;
}

const EMPTY: FormState = {
  achievements: '',
  challenges: '',
  skillsDeveloped: '',
  improvementAreas: '',
  trainingNeeded: '',
  futureGoals: '',
  comments: '',
  selfRating: null,
};

const FIELDS: { key: keyof Omit<FormState, 'selfRating'>; label: string; help: string }[] = [
  {
    key: 'achievements',
    label: 'What did you achieve this period?',
    help: 'The results you delivered. Concrete outcomes read better than activity.',
  },
  {
    key: 'challenges',
    label: 'What got in the way?',
    help: 'Blockers, dependencies or constraints that affected the work.',
  },
  {
    key: 'skillsDeveloped',
    label: 'What did you learn?',
    help: 'Skills, tools or domain knowledge you picked up.',
  },
  {
    key: 'improvementAreas',
    label: 'Where do you want to improve?',
    help: 'Honest gaps you would like to close.',
  },
  {
    key: 'trainingNeeded',
    label: 'What support or training would help?',
    help: 'Training, mentoring or resources you need.',
  },
  {
    key: 'futureGoals',
    label: 'What are your goals for the next period?',
    help: 'What you intend to take on next.',
  },
  { key: 'comments', label: 'Anything else', help: 'Optional context for your reviewers.' },
];

/**
 * The employee's half of an appraisal. It stays editable while the step is
 * theirs — including after a reviewer returns it — and freezes the moment it is
 * submitted, so a reviewer is never reading a document that shifts underneath
 * them.
 */
export function SelfAssessmentPage() {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<number | ''>('');
  const [form, setForm] = useState<FormState>(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const mine = useQuery({ queryKey: ['appraisals', 'my'], queryFn: appraisalApi.mine });

  useEffect(() => {
    const list = mine.data ?? [];
    if (selected === '' && list.length > 0) {
      setSelected((list.find((entry) => entry.waitingOnMe) ?? list[0]).id);
    }
  }, [mine.data, selected]);

  const detail = useQuery({
    queryKey: ['appraisals', 'detail', selected],
    queryFn: () => appraisalApi.detail(Number(selected)),
    enabled: selected !== '',
  });

  const record = detail.data;
  const assessment = record?.selfAssessment ?? null;

  useEffect(() => {
    setForm(
      assessment
        ? {
            achievements: assessment.achievements ?? '',
            challenges: assessment.challenges ?? '',
            skillsDeveloped: assessment.skillsDeveloped ?? '',
            improvementAreas: assessment.improvementAreas ?? '',
            trainingNeeded: assessment.trainingNeeded ?? '',
            futureGoals: assessment.futureGoals ?? '',
            comments: assessment.comments ?? '',
            selfRating: assessment.selfRating ?? null,
          }
        : EMPTY,
    );
    setSaved(null);
    setError(null);
  }, [assessment?.id, assessment?.status, record?.id]);

  const editable = Boolean(record?.canSubmitSelfAssessment);

  const payload = useMemo<SelfAssessmentPayload>(
    () => ({
      achievements: form.achievements || null,
      challenges: form.challenges || null,
      skillsDeveloped: form.skillsDeveloped || null,
      improvementAreas: form.improvementAreas || null,
      trainingNeeded: form.trainingNeeded || null,
      futureGoals: form.futureGoals || null,
      comments: form.comments || null,
      selfRating: form.selfRating,
    }),
    [form],
  );

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['appraisals'] });
  }

  const save = useMutation({
    mutationFn: () => appraisalApi.saveSelfAssessment(Number(selected), payload),
    onSuccess: () => {
      setError(null);
      setSaved(`Draft saved at ${new Date().toLocaleTimeString('en-GB')}`);
      refresh();
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const submit = useMutation({
    mutationFn: () => appraisalApi.submitSelfAssessment(Number(selected), payload),
    onSuccess: () => {
      setError(null);
      setSaved(null);
      refresh();
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  if (mine.isLoading) {
    return <FullPageLoader label="Loading your appraisals" />;
  }

  const list = mine.data ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Performance & KRA"
        title="Self assessment"
        description="Your account of the period. Your reviewers read it alongside your measured score."
        actions={
          list.length > 1 ? (
            <TextField
              select
              size="small"
              label="Cycle"
              value={selected}
              onChange={(event) => setSelected(Number(event.target.value))}
              sx={{ minWidth: 240 }}
            >
              {list.map((entry) => (
                <MenuItem key={entry.id} value={entry.id}>
                  {entry.cycleName}
                </MenuItem>
              ))}
            </TextField>
          ) : undefined
        }
      />

      {list.length === 0 && (
        <EmptyState
          title="No appraisal cycle is open for you"
          description="When HR opens a review cycle you will find your self assessment here."
        />
      )}

      {detail.isLoading && selected !== '' && <FullPageLoader label="Loading appraisal" />}

      {record && (
        <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', lg: '3fr 2fr' } }}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <Card>
              <CardContent sx={{ p: 5 }}>
                <Box
                  sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    gap: 3,
                    mb: 4,
                  }}
                >
                  <Box>
                    <Typography variant="h5">{record.cycleName}</Typography>
                    <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                      {record.periodLabel} · {record.periodStart} to {record.periodEnd}
                    </Typography>
                  </Box>
                  <StatusChip status={assessment?.status ?? 'NOT_STARTED'} />
                </Box>

                {error && (
                  <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
                    {error}
                  </Alert>
                )}
                {saved && (
                  <Alert severity="success" sx={{ mb: 3 }} onClose={() => setSaved(null)}>
                    {saved}
                  </Alert>
                )}
                {!editable && (
                  <Alert severity="info" icon={<LockOutlined sx={{ fontSize: 19 }} />} sx={{ mb: 3 }}>
                    {assessment?.status === 'SUBMITTED'
                      ? `Submitted${assessment.submittedAt ? ` on ${new Date(assessment.submittedAt).toLocaleDateString('en-GB')}` : ''}. It is now with ${stepName(record.workflow, record.currentStep).toLowerCase()}.`
                      : 'This assessment is not open for editing right now.'}
                  </Alert>
                )}

                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {FIELDS.map((field) => (
                    <TextField
                      key={field.key}
                      label={field.label}
                      helperText={field.help}
                      value={form[field.key]}
                      disabled={!editable}
                      onChange={(event) => setForm({ ...form, [field.key]: event.target.value })}
                      multiline
                      minRows={3}
                      inputProps={{ maxLength: 4000 }}
                      fullWidth
                    />
                  ))}

                  <Box>
                    <Typography variant="subtitle2" sx={{ mb: 1 }}>
                      How would you rate your own performance?
                    </Typography>
                    <RatingField
                      label="Overall self rating"
                      hint="Optional, but reviewers find it useful"
                      value={form.selfRating}
                      disabled={!editable}
                      onChange={(value) => setForm({ ...form, selfRating: value })}
                    />
                  </Box>
                </Box>

                {editable && (
                  <Box sx={{ display: 'flex', gap: 2, mt: 5, justifyContent: 'flex-end' }}>
                    <Button
                      variant="outlined"
                      startIcon={<SaveOutlined />}
                      disabled={save.isPending || submit.isPending}
                      onClick={() => save.mutate()}
                    >
                      Save draft
                    </Button>
                    <Button
                      variant="contained"
                      startIcon={<SendOutlined />}
                      disabled={save.isPending || submit.isPending}
                      onClick={() => submit.mutate()}
                    >
                      Submit for review
                    </Button>
                  </Box>
                )}
              </CardContent>
            </Card>

            {record.reviews.filter((review) => review.status === 'SUBMITTED').length > 0 && (
              <Card>
                <CardContent sx={{ p: 5 }}>
                  <Typography variant="h5" sx={{ mb: 0.5 }}>
                    What your reviewers said
                  </Typography>
                  <Typography variant="body2" sx={{ color: 'text.secondary', mb: 4 }}>
                    Only submitted reviews appear here, and only they count towards your score.
                  </Typography>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                    {record.reviews
                      .filter((review) => review.status === 'SUBMITTED')
                      .map((review) => (
                        <ReviewSummaryCard key={review.id} review={review} />
                      ))}
                  </Box>
                </CardContent>
              </Card>
            )}
          </Box>

          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <Card>
              <CardContent sx={{ p: 5 }}>
                <Typography variant="h6" sx={{ mb: 4 }}>
                  Where this appraisal is
                </Typography>
                <WorkflowTracker
                  workflow={record.workflow}
                  currentStep={record.currentStep}
                  completed={record.status === 'COMPLETED'}
                />
                <Divider sx={{ my: 4 }} />
                <Row label="Reviewing manager" value={record.managerName ?? '—'} />
                <Row
                  label="Measured score"
                  value={record.computedScore == null ? 'Not computed yet' : formatRating(record.computedScore)}
                />
                <Row
                  label="Final score"
                  value={
                    record.finalScore == null ? (
                      'Pending sign-off'
                    ) : (
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <span className="tabular">{Number(record.finalScore).toFixed(2)}</span>
                        {record.finalRatingBandName && (
                          <Chip
                            size="small"
                            label={record.finalRatingBandName}
                            sx={{
                              bgcolor: record.finalRatingBandColour ?? undefined,
                              color: record.finalRatingBandColour ? '#fff' : undefined,
                            }}
                          />
                        )}
                      </Box>
                    )
                  }
                />
              </CardContent>
            </Card>

            <Card>
              <CardContent sx={{ p: 5 }}>
                <Typography variant="h6" sx={{ mb: 3 }}>
                  History
                </Typography>
                <AppraisalHistory history={record.history} />
              </CardContent>
            </Card>
          </Box>
        </Box>
      )}
    </Box>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 3, py: 1 }}>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        {label}
      </Typography>
      <Typography variant="body2" component="div" sx={{ fontWeight: 500 }}>
        {value}
      </Typography>
    </Box>
  );
}
