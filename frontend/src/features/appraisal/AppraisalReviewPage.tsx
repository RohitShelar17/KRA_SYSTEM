import { useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { appraisalApi, cycleApi } from '@/api/appraisal';
import { EmptyState } from '@/components/EmptyState';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { TableSkeleton } from '@/components/LoadingState';
import type { AppraisalStep } from '@/types/appraisal';
import { AppraisalReviewDialog } from './AppraisalReviewDialog';

const STEPS: { value: AppraisalStep | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Every step' },
  { value: 'SELF_ASSESSMENT', label: 'Waiting on the employee' },
  { value: 'MANAGER_REVIEW', label: 'Manager review' },
  { value: 'DEPARTMENT_HEAD_REVIEW', label: 'Department head review' },
  { value: 'HR_REVIEW', label: 'HR review' },
  { value: 'COMPLETED', label: 'Completed' },
];

/**
 * The reviewer's console: everyone in scope whose appraisal is in flight, and
 * the form that turns a manager's judgement into the quality and manager
 * ratings the score formula reads.
 */
export function AppraisalReviewPage() {
  const queryClient = useQueryClient();
  const [cycleId, setCycleId] = useState<number | ''>('');
  const [step, setStep] = useState<AppraisalStep | 'ALL'>('ALL');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [openId, setOpenId] = useState<number | null>(null);

  const cycles = useQuery({ queryKey: ['cycles', 'list'], queryFn: cycleApi.list });

  useEffect(() => {
    const open = (cycles.data ?? []).filter((cycle) => cycle.status !== 'DRAFT');
    if (cycleId === '' && open.length > 0) {
      setCycleId(open[0].id);
    }
  }, [cycles.data, cycleId]);

  const queue = useQuery({
    queryKey: ['appraisals', 'queue', cycleId, step, page, size],
    queryFn: () =>
      appraisalApi.queue({
        cycleId: cycleId === '' ? undefined : Number(cycleId),
        step: step === 'ALL' ? undefined : step,
        page,
        size,
      }),
    enabled: cycleId !== '',
  });

  const rows = queue.data?.content ?? [];
  const waiting = useMemo(() => rows.filter((row) => row.waitingOnMe).length, [rows]);

  return (
    <Box>
      <PageHeader
        eyebrow="Management"
        title="Appraisal reviews"
        description="Read a report's self assessment, rate the period and pass it on. Only submitted reviews reach the score."
      />

      <Card sx={{ mb: 4, p: 4, display: 'flex', flexWrap: 'wrap', gap: 3, alignItems: 'center' }}>
        <TextField
          select
          size="small"
          label="Cycle"
          value={cycleId}
          onChange={(event) => {
            setCycleId(Number(event.target.value));
            setPage(0);
          }}
          sx={{ minWidth: 240 }}
        >
          {(cycles.data ?? []).map((cycle) => (
            <MenuItem key={cycle.id} value={cycle.id}>
              {cycle.name} · {cycle.periodLabel}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Step"
          value={step}
          onChange={(event) => {
            setStep(event.target.value as AppraisalStep | 'ALL');
            setPage(0);
          }}
          sx={{ minWidth: 240 }}
        >
          {STEPS.map((entry) => (
            <MenuItem key={entry.value} value={entry.value}>
              {entry.label}
            </MenuItem>
          ))}
        </TextField>
        {waiting > 0 && (
          <Chip color="primary" size="small" label={`${waiting} on this page need you`} />
        )}
      </Card>

      {cycleId === '' && !cycles.isLoading && (
        <EmptyState
          title="No appraisal cycle has been opened"
          description="Once HR opens a cycle, the people you can review appear here."
        />
      )}

      {queue.isLoading && <TableSkeleton rows={6} columns={6} />}

      {queue.data && rows.length === 0 && (
        <EmptyState title="Nothing at this step" description="Try a different step or cycle." />
      )}

      {rows.length > 0 && (
        <Card>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Employee</TableCell>
                <TableCell>Current step</TableCell>
                <TableCell>Self assessment</TableCell>
                <TableCell align="right">Measured</TableCell>
                <TableCell align="right">Final</TableCell>
                <TableCell align="right">Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.id} hover>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {row.employeeName ?? `Employee ${row.employeeId}`}
                    </Typography>
                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                      {[row.employeeCode, row.designation].filter(Boolean).join(' · ') || '—'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <StatusChip status={row.status} label={row.currentStepName} />
                      {row.waitingOnMe && <Chip size="small" color="primary" label="You" />}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <StatusChip status={row.selfAssessmentSubmitted ? 'SUBMITTED' : 'DRAFT'} />
                  </TableCell>
                  <TableCell align="right" className="tabular">
                    {row.computedScore == null ? '—' : Number(row.computedScore).toFixed(2)}
                  </TableCell>
                  <TableCell align="right" className="tabular">
                    {row.finalScore == null ? '—' : Number(row.finalScore).toFixed(2)}
                  </TableCell>
                  <TableCell align="right">
                    <Button size="small" variant="outlined" onClick={() => setOpenId(row.id)}>
                      Open
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <TablePagination
            component="div"
            count={queue.data?.totalElements ?? 0}
            page={page}
            rowsPerPage={size}
            onPageChange={(_, next) => setPage(next)}
            onRowsPerPageChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
            rowsPerPageOptions={[10, 20, 50]}
          />
        </Card>
      )}

      {queue.isError && (
        <Alert severity="error" sx={{ mt: 3 }}>
          The review queue could not be loaded.
        </Alert>
      )}

      {openId !== null && (
        <AppraisalReviewDialog
          appraisalId={openId}
          onClose={() => setOpenId(null)}
          onChanged={() => void queryClient.invalidateQueries({ queryKey: ['appraisals'] })}
        />
      )}
    </Box>
  );
}
