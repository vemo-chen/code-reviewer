import http from "./http";

export interface UserQueryParams {
  pageNo?: number;
  pageSize?: number;
  username?: string;
  displayName?: string;
  gitlabUsername?: string;
  role?: string;
  status?: string;
}

export interface UserUpsertPayload {
  username: string;
  displayName: string;
  gitlabUsername?: string;
  password?: string;
  role: string;
  status: string;
}

export const fetchUsers = (params: UserQueryParams = {}) => http.get("/users", { params });

export const fetchUserDetail = (userId: number) => http.get(`/users/${userId}`);

export const createUser = (payload: UserUpsertPayload) => http.post("/users", payload);

export const updateUser = (userId: number, payload: UserUpsertPayload) =>
  http.put(`/users/${userId}`, payload);

export const updateUserStatus = (userId: number, status: string) =>
  http.put(`/users/${userId}/status`, { status });

export const resetUserPassword = (userId: number, password: string) =>
  http.post(`/users/${userId}/reset-password`, { password });

export const fetchUserProjects = (userId: number) => http.get(`/users/${userId}/projects`);

export const assignUserProjects = (userId: number, projectIds: number[]) =>
  http.put(`/users/${userId}/projects`, { projectIds });

export const fetchProjectUsers = (projectId: number) => http.get(`/projects/${projectId}/users`);
