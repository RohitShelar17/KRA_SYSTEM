export type MeasurementType =
  | 'PERCENTAGE'
  | 'NUMERIC'
  | 'CURRENCY'
  | 'HOURS'
  | 'COUNT'
  | 'BOOLEAN'
  | 'RATING'
  | 'CUSTOM';

export type TargetDirection = 'HIGHER_IS_BETTER' | 'LOWER_IS_BETTER' | 'BOOLEAN';
export type PeriodType = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';
export type KraStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED';
export type ActualSource =
  | 'MANUAL'
  | 'WORK_LOG_PRODUCTIVE_HOURS'
  | 'TASK_COMPLETION_RATE'
  | 'TASK_ON_TIME_RATE'
  | 'INTEGRATION';

export interface KpiTemplateItem {
  id: number | null;
  name: string;
  description: string | null;
  measurementType: MeasurementType;
  targetDirection: TargetDirection;
  unit: string | null;
  defaultTargetValue: number | null;
  weightage: number;
  capPercent: number;
  actualSource: ActualSource;
  displayOrder: number;
}

export interface KraTemplateItem {
  id: number | null;
  name: string;
  description: string | null;
  weightage: number;
  displayOrder: number;
  kpiWeightageTotal: number;
  kpis: KpiTemplateItem[];
}

export interface KraTemplate {
  id: number;
  code: string;
  name: string;
  description: string | null;
  designationId: number | null;
  designationName: string | null;
  departmentId: number | null;
  departmentName: string | null;
  periodType: PeriodType;
  published: boolean;
  active: boolean;
  weightageTotal: number;
  kraCount: number;
  kpiCount: number;
  items: KraTemplateItem[];
}

export interface KpiProgress {
  id: number;
  name: string;
  description: string | null;
  measurementType: MeasurementType;
  targetDirection: TargetDirection;
  unit: string | null;
  weightage: number;
  capPercent: number;
  actualSource: ActualSource;
  derived: boolean;
  targetValue: number | null;
  actualValue: number | null;
  achievementPercent: number | null;
  scored: boolean;
  excludedReason: string | null;
  actualRecordedBy: string | null;
  actualNote: string | null;
}

export interface KraProgress {
  id: number;
  employeeId: number;
  employeeName: string;
  name: string;
  description: string | null;
  weightage: number;
  periodType: PeriodType;
  periodYear: number;
  periodIndex: number;
  periodLabel: string;
  periodStart: string;
  periodEnd: string;
  status: KraStatus;
  achievementPercent: number | null;
  scored: boolean;
  scoredKpiCount: number;
  totalKpiCount: number;
  kpis: KpiProgress[];
}

export interface KraScorecard {
  employeeId: number;
  employeeName: string;
  employeeCode: string;
  designation: string | null;
  periodType: PeriodType;
  periodYear: number;
  periodIndex: number;
  periodLabel: string;
  periodStart: string;
  periodEnd: string;
  overallAchievement: number | null;
  scored: boolean;
  weightageTotal: number;
  weightageValid: boolean;
  kraCount: number;
  kpiCount: number;
  scoredKpiCount: number;
  kras: KraProgress[];
}

export interface KpiHistoryPoint {
  periodStart: string;
  periodEnd: string;
  targetValue: number | null;
  actualValue: number | null;
  achievementPercent: number | null;
  source: ActualSource | null;
  recordedByName: string | null;
  note: string | null;
}
