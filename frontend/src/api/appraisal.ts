import { apiClient, unwrap } from './axiosClient';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  AppraisalCycle,
  AppraisalDetail,
  AppraisalStep,
  AppraisalSummary,
  CycleLaunchResult,
  ManagerReview,
  SelfAssessment,
  WorkflowStep,
  WorkflowStepType,
} from '@/types/appraisal';

export interface CyclePayload {
  name: string;
  description?: string | null;
  scopeType: 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';
  periodYear: number;
  periodIndex: number;
  selfAssessmentOpens?: string | null;
  selfAssessmentCloses?: string | null;
  reviewOpens?: string | null;
  reviewCloses?: string | null;
}

export interface SelfAssessmentPayload {
  achievements?: string | null;
  challenges?: string | null;
  skillsDeveloped?: string | null;
  improvementAreas?: string | null;
  trainingNeeded?: string | null;
  futureGoals?: string | null;
  comments?: string | null;
  selfRating?: number | null;
}

export interface ReviewPayload {
  qualityRating?: number | null;
  productivityRating?: number | null;
  timelinessRating?: number | null;
  teamworkRating?: number | null;
  communicationRating?: number | null;
  initiativeRating?: number | null;
  problemSolvingRating?: number | null;
  disciplineRating?: number | null;
  kraRating?: number | null;
  kpiRating?: number | null;
  overallRating?: number | null;
  strengths?: string | null;
  improvements?: string | null;
  comments?: string | null;
}

export interface WorkflowStepPayload {
  stepType: WorkflowStepType;
  name: string;
  description?: string | null;
  required: boolean;
  active: boolean;
  slaDays?: number | null;
}

export const cycleApi = {
  list: () => unwrap<AppraisalCycle[]>(apiClient.get<ApiResponse<AppraisalCycle[]>>('/performance-cycles')),

  active: () =>
    unwrap<AppraisalCycle[]>(
      apiClient.get<ApiResponse<AppraisalCycle[]>>('/performance-cycles/active'),
    ),

  get: (id: number) =>
    unwrap<AppraisalCycle>(apiClient.get<ApiResponse<AppraisalCycle>>(`/performance-cycles/${id}`)),

  create: (payload: CyclePayload) =>
    unwrap<AppraisalCycle>(apiClient.post<ApiResponse<AppraisalCycle>>('/performance-cycles', payload)),

  update: (id: number, payload: CyclePayload) =>
    unwrap<AppraisalCycle>(
      apiClient.put<ApiResponse<AppraisalCycle>>(`/performance-cycles/${id}`, payload),
    ),

  open: (id: number) =>
    unwrap<CycleLaunchResult>(
      apiClient.post<ApiResponse<CycleLaunchResult>>(`/performance-cycles/${id}/open`),
    ),

  close: (id: number) =>
    unwrap<AppraisalCycle>(
      apiClient.post<ApiResponse<AppraisalCycle>>(`/performance-cycles/${id}/close`),
    ),

  workflow: () => unwrap<WorkflowStep[]>(apiClient.get<ApiResponse<WorkflowStep[]>>('/review-workflow')),

  saveWorkflow: (payload: WorkflowStepPayload[]) =>
    unwrap<WorkflowStep[]>(apiClient.put<ApiResponse<WorkflowStep[]>>('/review-workflow', payload)),
};

export const appraisalApi = {
  mine: () => unwrap<AppraisalSummary[]>(apiClient.get<ApiResponse<AppraisalSummary[]>>('/appraisals/my')),

  myForCycle: (cycleId: number) =>
    unwrap<AppraisalDetail>(
      apiClient.get<ApiResponse<AppraisalDetail>>(`/appraisals/my/cycle/${cycleId}`),
    ),

  queue: (params: { cycleId?: number; step?: AppraisalStep; page?: number; size?: number }) =>
    unwrap<PageResponse<AppraisalSummary>>(
      apiClient.get<ApiResponse<PageResponse<AppraisalSummary>>>('/appraisals/queue', { params }),
    ),

  detail: (id: number) =>
    unwrap<AppraisalDetail>(apiClient.get<ApiResponse<AppraisalDetail>>(`/appraisals/${id}`)),

  saveSelfAssessment: (id: number, payload: SelfAssessmentPayload) =>
    unwrap<SelfAssessment>(
      apiClient.put<ApiResponse<SelfAssessment>>(`/appraisals/${id}/self-assessment`, payload),
    ),

  submitSelfAssessment: (id: number, payload: SelfAssessmentPayload) =>
    unwrap<SelfAssessment>(
      apiClient.post<ApiResponse<SelfAssessment>>(`/appraisals/${id}/self-assessment/submit`, payload),
    ),

  saveReview: (id: number, payload: ReviewPayload) =>
    unwrap<ManagerReview>(apiClient.put<ApiResponse<ManagerReview>>(`/appraisals/${id}/review`, payload)),

  submitReview: (id: number, payload: ReviewPayload) =>
    unwrap<ManagerReview>(
      apiClient.post<ApiResponse<ManagerReview>>(`/appraisals/${id}/review/submit`, payload),
    ),

  advance: (id: number, comments?: string, skip = false) =>
    unwrap<AppraisalDetail>(
      apiClient.post<ApiResponse<AppraisalDetail>>(`/appraisals/${id}/advance`, { comments, skip }),
    ),

  returnStep: (id: number, comments: string) =>
    unwrap<AppraisalDetail>(
      apiClient.post<ApiResponse<AppraisalDetail>>(`/appraisals/${id}/return`, { comments }),
    ),
};
