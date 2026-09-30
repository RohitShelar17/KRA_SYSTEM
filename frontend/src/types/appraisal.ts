// The API serialises with non-null inclusion, so a field typed `T | null` here
// arrives absent rather than null. Compare with `== null` when reading one.
export type WorkflowStepType =
  | 'SELF_ASSESSMENT'
  | 'MANAGER_REVIEW'
  | 'DEPARTMENT_HEAD_REVIEW'
  | 'HR_REVIEW';

export type AppraisalStep = WorkflowStepType | 'COMPLETED';
export type CycleStatus = 'DRAFT' | 'OPEN' | 'IN_REVIEW' | 'CLOSED';
export type AppraisalStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';
export type SubmissionStatus = 'DRAFT' | 'SUBMITTED';
export type ReviewerRole = 'MANAGER' | 'DEPARTMENT_HEAD' | 'HR';
export type StepActionType = 'SUBMITTED' | 'APPROVED' | 'RETURNED' | 'SKIPPED' | 'COMPLETED';

export interface WorkflowStep {
  id: number | null;
  stepOrder: number;
  stepType: WorkflowStepType;
  name: string;
  description: string | null;
  required: boolean;
  slaDays: number | null;
  active: boolean;
}

export interface AppraisalCycle {
  id: number;
  name: string;
  description: string | null;
  scopeType: 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';
  periodYear: number;
  periodIndex: number;
  periodLabel: string;
  periodStart: string;
  periodEnd: string;
  selfAssessmentOpens: string | null;
  selfAssessmentCloses: string | null;
  reviewOpens: string | null;
  reviewCloses: string | null;
  status: CycleStatus;
  selfAssessmentOpen: boolean;
  reviewOpen: boolean;
  openedAt: string | null;
  closedAt: string | null;
  participantCount: number;
  completedCount: number;
  completionPercent: number;
}

export interface SelfAssessment {
  id: number;
  appraisalId: number;
  employeeId: number;
  employeeName: string | null;
  achievements: string | null;
  challenges: string | null;
  skillsDeveloped: string | null;
  improvementAreas: string | null;
  trainingNeeded: string | null;
  futureGoals: string | null;
  comments: string | null;
  selfRating: number | null;
  status: SubmissionStatus;
  editable: boolean;
  submittedAt: string | null;
}

export interface ManagerReview {
  id: number;
  appraisalId: number;
  employeeId: number;
  employeeName: string | null;
  reviewerEmployeeId: number;
  reviewerName: string | null;
  reviewerRole: ReviewerRole;
  ratings: Record<string, number>;
  overallRating: number | null;
  strengths: string | null;
  improvements: string | null;
  comments: string | null;
  status: SubmissionStatus;
  editable: boolean;
  submittedAt: string | null;
}

export interface StepActionEntry {
  id: number;
  stepType: string;
  action: StepActionType;
  fromStep: string | null;
  toStep: string | null;
  actorName: string | null;
  comments: string | null;
  createdAt: string;
}

export interface AppraisalSummary {
  id: number;
  cycleId: number;
  cycleName: string;
  periodLabel: string;
  employeeId: number;
  employeeName: string | null;
  employeeCode: string | null;
  designation: string | null;
  currentStep: AppraisalStep;
  currentStepName: string;
  status: AppraisalStatus;
  computedScore: number | null;
  finalScore: number | null;
  selfAssessmentSubmitted: boolean;
  managerReviewSubmitted: boolean;
  waitingOnMe: boolean;
}

export interface AppraisalDetail {
  id: number;
  cycleId: number;
  cycleName: string;
  periodLabel: string;
  periodStart: string;
  periodEnd: string;
  employeeId: number;
  employeeName: string | null;
  employeeCode: string | null;
  designation: string | null;
  managerName: string | null;
  currentStep: AppraisalStep;
  status: AppraisalStatus;
  stepPosition: number;
  stepCount: number;
  workflow: WorkflowStep[];
  selfAssessment: SelfAssessment | null;
  reviews: ManagerReview[];
  history: StepActionEntry[];
  computedScore: number | null;
  finalScore: number | null;
  finalRatingBandName: string | null;
  finalRatingBandColour: string | null;
  completedAt: string | null;
  canSubmitSelfAssessment: boolean;
  canReview: boolean;
  canAdvance: boolean;
}

export interface CycleLaunchResult {
  cycleId: number;
  cycleName: string;
  participantsCreated: number;
  alreadyPresent: number;
  messages: string[];
}
