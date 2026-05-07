import http from "./http";

export interface ProjectQueryParams {
  pageNo?: number;
  pageSize?: number;
  projectName?: string;
  gitlabProjectUrl?: string;
  aiReviewEnabled?: boolean;
  wecomNotifyEnabled?: boolean;
}

export const fetchProjects = (params: ProjectQueryParams = {}) => http.get("/projects", { params });

export interface ProjectUpsertPayload {
  projectName: string;
  sourcePlatform: string;
  gitlabProjectUrl: string;
  gitlabWebhookToken?: string;
  reviewBranches?: string;
  ownerUserId?: number | null;
  templateId?: number | null;
  supportedFileExtensions?: string;
  memberUserIds?: number[];
  llmModelId?: number | null;
  aiReviewEnabled: boolean;
  reviewContextEnabled: boolean;
  gitlabNoteEnabled: boolean;
  wecomNotifyEnabled: boolean;
  wecomWebhookUrl?: string;
  promptContent: string;
  active: boolean;
}

export interface GitLabProjectTestPayload {
  gitlabProjectUrl: string;
  gitlabWebhookToken: string;
}

export interface GitLabProjectTestResponse {
  gitlabProjectId: number;
  projectName: string;
  pathWithNamespace: string;
  webUrl: string;
}

export interface GitLabBranchOption {
  name: string;
  defaultBranch?: boolean;
  protectedBranch?: boolean;
}

export const fetchProjectDetail = (id: number) => http.get(`/projects/${id}`);

export const fetchProjectLlmModels = (id: number) => http.get(`/projects/${id}/llm-models`);

export const testGitLabProject = (payload: GitLabProjectTestPayload) =>
  http.post("/projects/gitlab/test", payload);

export const fetchGitLabBranches = (payload: GitLabProjectTestPayload) =>
  http.post("/projects/gitlab/branches", payload);

export const createProject = (payload: ProjectUpsertPayload) => http.post("/projects", payload);

export const updateProject = (id: number, payload: ProjectUpsertPayload) =>
  http.put(`/projects/${id}`, payload);

export const deleteProject = (id: number) => http.delete(`/projects/${id}`);

export const refreshProject = (id: number) => http.post(`/projects/${id}/refresh`);
