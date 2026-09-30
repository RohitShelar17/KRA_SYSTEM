export type PerformanceScope = 'DAILY' | 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';

export type PerformanceStatus =
  | 'COMPUTED'
  | 'SELF_ASSESSED'
  | 'MANAGER_REVIEWED'
  | 'HEAD_REVIEWED'
  | 'HR_REVIEWED'
  | 'FINALIZED';

export type PerformanceParameterCode =
  | 'TASK_COMPLETION'
  | 'KRA_ACHIEVEMENT'
  | 'KPI_ACHIEVEMENT'
  | 'PRODUCTIVITY'
  | 'QUALITY_RATING'
  | 'MANAGER_RATING'
  | 'DEPARTMENT_HEAD_RATING'
  | 'SELF_ASSESSMENT'
  | 'DAILY_AVERAGE'
  | 'MONTHLY_AVERAGE'
  | 'QUARTERLY_AVERAGE';

export interface ScoreContribution {
  parameterCode: PerformanceParameterCode;
  label: string;
  rawValue: number | null;
  weightage: number;
  effectiveWeightage: number;
  contribution: number;
  measured: boolean;
  skippedReason: string | null;
}

export interface Adjustment {
  id: number;
  previousScore: number | null;
  newScore: number;
  reason: string;
  adjustedByName: string | null;
  createdAt: string;
}

export interface PerformanceSnapshot {
  id: number;
  scope: PerformanceScope;
  employeeId: number;
  employeeName: string | null;
  employeeCode: string | null;
  periodLabel: string;
  periodStart: string;
  periodEnd: string;
  overallScore: number | null;
  scored: boolean;
  ratingBandName: string | null;
  ratingBandColour: string | null;
  status: PerformanceStatus;
  frozen: boolean;
  formulaVersion: number;
  computedAt: string;
  finalizedAt: string | null;
  finalizedByName: string | null;
  penaltyApplied: number | null;
  figures: Record<string, number | string>;
  contributions: ScoreContribution[];
  adjustments: Adjustment[];
}

export interface PerformanceTrendPoint {
  label: string;
  score: number | null;
  bandName: string | null;
  bandColour: string | null;
  frozen: boolean;
}

export interface PerformanceTrend {
  employeeId: number;
  employeeName: string;
  scope: PerformanceScope;
  latestScore: number | null;
  latestBandName: string | null;
  previousScore: number | null;
  changeFromPrevious: number | null;
  points: PerformanceTrendPoint[];
}

export interface FormulaParameter {
  id: number | null;
  parameterCode: PerformanceParameterCode;
  label: string;
  description: string;
  weightage: number;
  displayOrder: number;
}

export interface FormulaConfig {
  id: number;
  scopeType: PerformanceScope;
  version: number;
  name: string;
  notes: string | null;
  minSubmissionRatePct: number;
  submissionPenaltyPct: number;
  active: boolean;
  effectiveFrom: string;
  weightageTotal: number;
  parameters: FormulaParameter[];
}

export interface RatingBand {
  id: number;
  name: string;
  description: string | null;
  minScore: number;
  maxScore: number;
  colourHex: string | null;
  displayOrder: number;
}

export interface RecalculationSummary {
  scope: PerformanceScope;
  periodLabel: string;
  processed: number;
  skipped: number;
  failed: number;
  messages: string[];
}
