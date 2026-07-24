import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { currentUserApi, loginApi, logoutApi, registerApi, ssoLoginApi } from "../api/auth";
import type { RegisterPayload } from "../api/auth";

const TOKEN_KEY = "code-reviewer-token";
const USER_KEY = "code-reviewer-auth-user";

interface AuthUser {
  userId: number;
  username: string;
  displayName: string;
  role: string;
}

const readStoredUser = (): AuthUser | null => {
  const raw = window.localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthUser;
  } catch (error) {
    window.localStorage.removeItem(USER_KEY);
    return null;
  }
};

export const useAuthStore = defineStore("auth", () => {
  const token = ref(window.localStorage.getItem(TOKEN_KEY) ?? "");
  const user = ref<AuthUser | null>(readStoredUser());
  const initialized = ref(false);

  const username = computed(() => user.value?.username ?? "");
  const displayName = computed(() => user.value?.displayName ?? user.value?.username ?? "");
  const role = computed(() => user.value?.role ?? "");
  const isAdmin = computed(() => role.value === "ADMIN");
  const isLoggedIn = computed(() => Boolean(token.value && user.value?.userId));

  const persist = () => {
    if (token.value) {
      window.localStorage.setItem(TOKEN_KEY, token.value);
    } else {
      window.localStorage.removeItem(TOKEN_KEY);
    }

    if (user.value) {
      window.localStorage.setItem(USER_KEY, JSON.stringify(user.value));
    } else {
      window.localStorage.removeItem(USER_KEY);
    }
  };

  const applyAuth = (payload: { token: string; userId: number; username: string; displayName: string; role: string }) => {
    token.value = payload.token;
    user.value = {
      userId: payload.userId,
      username: payload.username,
      displayName: payload.displayName,
      role: payload.role
    };
    initialized.value = true;
    persist();
  };

  const clearAuth = () => {
    token.value = "";
    user.value = null;
    initialized.value = true;
    persist();
  };

  const login = async (usernameValue: string, password: string) => {
    const response = await loginApi({ username: usernameValue, password });
    applyAuth(response.data.data);
  };

  const ssoLogin = async (employeeCode: string, password: string) => {
    const response = await ssoLoginApi({ employeeCode, password });
    applyAuth(response.data.data);
  };

  const register = async (payload: RegisterPayload) => {
    const response = await registerApi(payload);
    applyAuth(response.data.data);
  };

  const fetchCurrentUser = async () => {
    if (!token.value) {
      clearAuth();
      return null;
    }
    const response = await currentUserApi();
    applyAuth(response.data.data);
    return user.value;
  };

  const initialize = async () => {
    if (initialized.value) {
      return;
    }
    if (!token.value) {
      clearAuth();
      return;
    }
    try {
      await fetchCurrentUser();
    } catch (error) {
      clearAuth();
      throw error;
    }
  };

  const logout = async () => {
    try {
      if (token.value) {
        await logoutApi();
      }
    } catch (error) {
      // Ignore logout network errors and clear local state anyway.
    }
    clearAuth();
  };

  return {
    token,
    user,
    username,
    displayName,
    role,
    isAdmin,
    isLoggedIn,
    initialized,
    login,
    ssoLogin,
    register,
    fetchCurrentUser,
    initialize,
    logout,
    clearAuth
  };
});
