import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import Stepper from '@mui/material/Stepper';
import Typography from '@mui/material/Typography';
import type { AppraisalStep, WorkflowStep } from '@/types/appraisal';

interface Props {
  workflow: WorkflowStep[];
  currentStep: AppraisalStep;
  completed: boolean;
}

/**
 * The flow an appraisal is walking, drawn from the configured steps rather
 * than a fixed list — an organisation that removed the department head review
 * sees three stages here, not four with one greyed out.
 */
export function WorkflowTracker({ workflow, currentStep, completed }: Props) {
  const active = completed
    ? workflow.length
    : Math.max(
        0,
        workflow.findIndex((step) => step.stepType === currentStep),
      );

  return (
    <Box>
      <Stepper activeStep={active} alternativeLabel sx={{ mb: 1 }}>
        {workflow.map((step) => (
          <Step key={step.stepType} completed={completed || workflow.indexOf(step) < active}>
            <StepLabel
              optional={
                step.required ? undefined : (
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    optional
                  </Typography>
                )
              }
            >
              {step.name}
            </StepLabel>
          </Step>
        ))}
      </Stepper>
      {completed && (
        <Box sx={{ display: 'flex', justifyContent: 'center' }}>
          <Chip size="small" color="success" variant="outlined" label="Sign-off complete" />
        </Box>
      )}
    </Box>
  );
}

/** The name an organisation gave a step, falling back to the enum reading. */
export function stepName(workflow: WorkflowStep[], step: AppraisalStep): string {
  if (step === 'COMPLETED') {
    return 'Completed';
  }
  const configured = workflow.find((entry) => entry.stepType === step);
  return configured?.name ?? humanise(step);
}

export function humanise(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ');
}

export function formatRating(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : Number(value).toFixed(1);
}
