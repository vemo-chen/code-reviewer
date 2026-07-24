import http from "./http";

export interface LoginPayload {
  username: string;
  password: string;
}

export interface RegisterPayload {
  username: string;
  displayName: string;
  email: string;
  password: string;
  gitlabUsername?: string;
}

export interface SsoLoginPayload {
  employeeCode: string;
  password: string;
}

export interface AuthUser {
  token: string;
  userId: number;
  username: string;
  displayName: string;
  role: string;
}

export interface AuthProfile {
  userId: number;
  username: string;
  displayName: string;
  email?: string;
  employeeCode?: string;
  ssoUserId?: number;
  authSource?: string;
  passwordInitialized: boolean;
  gitlabUsername?: string;
  role: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export interface SetPasswordPayload {
  newPassword: string;
}

export interface UpdateGitlabUsernamePayload {
  gitlabUsername?: string;
}

export const loginApi = (payload: LoginPayload) => http.post("/auth/login", payload);

export const ssoLoginApi = (payload: SsoLoginPayload) => http.post("/auth/sso-login", payload);

export const registerApi = (payload: RegisterPayload) => http.post("/auth/register", payload);

export const currentUserApi = () => http.get("/auth/me");

export const profileApi = () => http.get("/auth/profile");

export const logoutApi = () => http.post("/auth/logout");

export const changePasswordApi = (payload: ChangePasswordPayload) =>
  http.post("/auth/change-password", payload);

export const setPasswordApi = (payload: SetPasswordPayload) =>
  http.post("/auth/set-password", payload);

export const updateGitlabUsernameApi = (payload: UpdateGitlabUsernamePayload) =>
  http.post("/auth/profile/gitlab-username", payload);
