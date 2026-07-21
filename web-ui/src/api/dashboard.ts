import http from "./http";

export interface ReviewTaskQueryParams {
  pageNo?: number;
  pageSize?: number;
  projectId?: number;
  status?: string;
  fixStatus?: string;
  riskLevel?: string;
  gitlabUsername?: string;
  operatorName?: string;
  targetTitle?: string;
  startDate?: string;
  endDate?: string;
  sortField?: "updatedAt";
  sortOrder?: "asc" | "desc";
}

export interface ReviewSubmitterOption {
  gitlabUsername: string;
  operatorName: string;
  displayLabel: string;
}

export interface ReviewFixFlowItem {
  id: number;
  taskId: number;
  fromStatus: string;
  toStatus: string;
  operatorUserId: number | null;
  operatorName: string;
  comment: string;
  createdAt: string | null;
}

export const fetchReviewTasks = (pageNo = 1, pageSize = 10, params: ReviewTaskQueryParams = {}) =>
  http.get("/dashboard/review-tasks", { params: { pageNo, pageSize, ...params } });

export const fetchReviewTaskDetail = (taskId: number) => http.get(`/dashboard/review-tasks/${taskId}`);

export const fetchReviewSubmitters = () => http.get("/dashboard/review-submitters");

export const retryReviewTask = (taskId: number) => http.post(`/dashboard/review-tasks/${taskId}/retry`);

export const interruptReviewTask = (taskId: number) => http.post(`/dashboard/review-tasks/${taskId}/interrupt`);

export const batchRetryReviewTask = (taskIds: number[]) =>
  http.post("/dashboard/review-tasks/batch-retry", { taskIds });

export const submitFixReview = (taskId: number, comment?: string) =>
  http.post(`/review-tasks/${taskId}/submit-fix-review`, { comment });

export const approveFixReview = (taskId: number, comment?: string) =>
  http.post(`/review-tasks/${taskId}/approve-fix`, { comment });

export const rejectFixReview = (taskId: number, comment: string) =>
  http.post(`/review-tasks/${taskId}/reject-fix`, { comment });

export const fetchFixFlows = (taskId: number) => http.get(`/review-tasks/${taskId}/fix-flows`);

export const fetchProjectStats = () => http.get("/dashboard/project-stats");

export const fetchDeveloperStats = () => http.get("/dashboard/developer-stats");

export const fetchScoreStats = (startDate: string, endDate: string) =>
  http.get("/dashboard/score-stats", { params: { startDate, endDate } });
