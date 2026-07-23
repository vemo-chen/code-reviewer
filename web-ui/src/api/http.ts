import axios from "axios";

const TOKEN_KEY = "code-reviewer-token";
const USER_KEY = "code-reviewer-auth-user";
let redirectingToLogin = false;

const clearLocalAuth = () => {
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(USER_KEY);
};

const jumpToLogin = () => {
  if (redirectingToLogin) {
    return;
  }
  redirectingToLogin = true;
  clearLocalAuth();

  const { pathname, search, hash } = window.location;
  const currentPath = `${pathname}${search}${hash}`;
  const onLoginPage = pathname === "/login";
  const target = onLoginPage
    ? "/login"
    : `/login?redirect=${encodeURIComponent(currentPath || "/dashboard")}`;

  window.location.replace(target);
};

const http = axios.create({
  baseURL: "/api",
  timeout: 15000
});

http.interceptors.request.use((config) => {
  const token = window.localStorage.getItem("code-reviewer-token");
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    const requestUrl = String(error?.config?.url || "");
    const isPublicAuthRequest = requestUrl.includes("/auth/login") || requestUrl.includes("/auth/register");

    if (status === 401 && !isPublicAuthRequest) {
      jumpToLogin();
    }

    return Promise.reject(error);
  }
);

export default http;
