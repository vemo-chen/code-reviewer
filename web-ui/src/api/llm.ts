import http from "./http";

export interface LlmModelQueryParams {
  pageNo?: number;
  pageSize?: number;
  configName?: string;
  providerCode?: string;
  scopeType?: string;
  enabled?: boolean;
}

export interface LlmModelUpsertPayload {
  configName: string;
  providerCode: string;
  providerName: string;
  providerType: string;
  baseUrl: string;
  apiKey?: string;
  modelName: string;
  enabled: boolean;
  scopeType: string;
  maintainerProjectId?: number | null;
  timeoutMs?: number | null;
  maxTokens?: number | null;
  temperature?: number | null;
  thinkingEnabled?: boolean;
  remark?: string;
  projectIds?: number[];
}

export const fetchLlmModels = (params: LlmModelQueryParams = {}) =>
  http.get("/llm/models", { params });

export const fetchLlmModelDetail = (id: number) => http.get(`/llm/models/${id}`);

export const createLlmModel = (payload: LlmModelUpsertPayload) =>
  http.post("/llm/models", payload);

export const updateLlmModel = (id: number, payload: LlmModelUpsertPayload) =>
  http.put(`/llm/models/${id}`, payload);

export const enableLlmModel = (id: number) => http.post(`/llm/models/${id}/enable`);

export const disableLlmModel = (id: number) => http.post(`/llm/models/${id}/disable`);

export const testLlmModel = (id: number) => http.post(`/llm/models/${id}/test`);

export const deleteLlmModel = (id: number) => http.delete(`/llm/models/${id}`);
