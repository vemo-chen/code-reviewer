import http from "./http";

export interface LoginPayload {
  username: string;
  password: string;
}

export interface AuthUser {
  token: string;
  userId: number;
  username: string;
  displayName: string;
  role: string;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export const loginApi = (payload: LoginPayload) => http.post("/auth/login", payload);

export const currentUserApi = () => http.get("/auth/me");

export const logoutApi = () => http.post("/auth/logout");

export const changePasswordApi = (payload: ChangePasswordPayload) =>
  http.post("/auth/change-password", payload);
