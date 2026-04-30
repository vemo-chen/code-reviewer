import http from "./http";

export interface ProjectTemplateQueryParams {
  pageNo?: number;
  pageSize?: number;
  templateName?: string;
}

export interface ProjectTemplateUpsertPayload {
  templateName: string;
  templateDesc?: string;
  fileExtensions?: string;
  baseReviewPrompt?: string;
}

export const fetchProjectTemplates = (params: ProjectTemplateQueryParams = {}) =>
  http.get("/project-templates", { params });

export const fetchProjectTemplateDetail = (id: number) =>
  http.get(`/project-templates/${id}`);

export const createProjectTemplate = (payload: ProjectTemplateUpsertPayload) =>
  http.post("/project-templates", payload);

export const updateProjectTemplate = (
  id: number,
  payload: ProjectTemplateUpsertPayload
) => http.put(`/project-templates/${id}`, payload);

export const deleteProjectTemplate = (id: number) =>
  http.delete(`/project-templates/${id}`);
