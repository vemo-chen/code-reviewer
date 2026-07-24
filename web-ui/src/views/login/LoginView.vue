<template>
  <div class="login-shell" :style="{ '--login-background': `url(${loginBackground})` }">
    <section class="login-panel">
      <p class="eyebrow">Internal Access</p>
      <h1>Code Reviewer</h1>
      <p class="description">Intelligent Analysis & Optimization</p>

      <div class="login-mode-tabs" role="tablist" aria-label="登录方式">
        <button :class="{ active: loginMode === 'local' }" type="button" @click="loginMode = 'local'">平台登录</button>
        <button :class="{ active: loginMode === 'sso' }" type="button" @click="loginMode = 'sso'">SSO</button>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <label>
          <span>{{ loginMode === "sso" ? "工号" : "用户名 / 邮箱" }}</span>
          <input v-model.trim="form.username" type="text" :placeholder="loginMode === 'sso' ? '请输入公司工号' : '请输入用户名或邮箱'" autocomplete="username" />
        </label>
        <label>
          <span>{{ loginMode === "sso" ? "公司密码" : "平台密码" }}</span>
          <div class="password-input">
            <input
              v-model.trim="form.password"
              :type="showPassword ? 'text' : 'password'"
              :placeholder="loginMode === 'sso' ? '请输入公司账号密码' : '请输入平台密码'"
              autocomplete="current-password"
            />
            <button
              class="toggle-password"
              type="button"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              :title="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <el-icon>
                <Hide v-if="showPassword" />
                <View v-else />
              </el-icon>
            </button>
          </div>
        </label>
        <button class="login-submit" type="submit" :disabled="submitting">{{ submitting ? "登录中..." : "进入系统" }}</button>
      </form>
      <div class="login-switch">
        <span>还没有账号？</span>
        <button type="button" @click="goRegister">注册账号</button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Hide, View } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useAuthStore } from "../../stores/auth";
import loginBackground from "../../assets/images/login-background.png";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const submitting = ref(false);
const loginMode = ref<"local" | "sso">("local");
const showPassword = ref(false);

const form = reactive({
  username: "",
  password: ""
});

const resolveErrorMessage = (error: unknown, fallback: string) => {
  if (typeof error === "object" && error !== null) {
    const maybeAxios = error as { response?: { data?: { message?: string } } };
    return maybeAxios.response?.data?.message || fallback;
  }
  return fallback;
};

const handleLogin = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning(loginMode.value === "sso" ? "请输入工号和公司密码" : "请输入用户名或邮箱和平台密码");
    return;
  }

  submitting.value = true;
  try {
    if (loginMode.value === "sso") {
      await authStore.ssoLogin(form.username, form.password);
    } else {
      await authStore.login(form.username, form.password);
    }
    ElMessage.success("登录成功");
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/dashboard";
    router.replace(redirect);
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, "登录失败，请检查用户名和密码"));
  } finally {
    submitting.value = false;
  }
};

const goRegister = () => {
  const query = typeof route.query.redirect === "string" ? { redirect: route.query.redirect } : undefined;
  router.push({ name: "register", query });
};
</script>

<style scoped>
.login-shell {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 32px 8vw 32px 32px;
  background-image: var(--login-background);
  background-position: center;
  background-size: 100% 100%;
  background-repeat: no-repeat;
}

.login-panel {
  width: min(100%, 460px);
  padding: 40px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 56px rgba(44, 54, 68, 0.2);
  backdrop-filter: blur(12px);
}

.eyebrow {
  margin: 0 0 12px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--cr-primary-deep);
}

h1 {
  margin: 0;
  font-size: 40px;
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: var(--cr-text);
}

.description {
  margin: 14px 0 0;
  color: var(--cr-text-soft);
  line-height: 1.8;
}

.login-form {
  display: grid;
  gap: 18px;
  margin-top: 22px;
}

.login-mode-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  margin-top: 24px;
  padding: 4px;
  border-radius: 8px;
  background: rgba(144, 77, 0, 0.08);
}

.login-mode-tabs button {
  min-height: 36px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--cr-text-soft);
  cursor: pointer;
  font-weight: 800;
}

.login-mode-tabs button.active {
  background: rgba(255, 255, 255, 0.9);
  color: var(--cr-primary-deep);
  box-shadow: 0 6px 14px rgba(144, 77, 0, 0.1);
}

.login-form label {
  display: grid;
  gap: 8px;
}

.login-form span {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--cr-text-soft);
}

.login-form input {
  width: 100%;
  border: none;
  border-bottom: 2px solid rgba(144, 77, 0, 0.12);
  padding: 14px 4px 12px;
  background: transparent;
  color: var(--cr-text);
  outline: none;
}

.login-form input:focus {
  border-bottom-color: var(--cr-primary);
}

.password-input {
  position: relative;
  display: flex;
  align-items: center;
  border-bottom: 2px solid rgba(144, 77, 0, 0.12);
}

.password-input:focus-within {
  border-bottom-color: var(--cr-primary);
}

.password-input input {
  border-bottom: none;
  padding-right: 40px;
}

.password-input input:focus {
  border-bottom-color: transparent;
}

.toggle-password {
  position: absolute;
  top: calc(50% + 2px);
  right: 0;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  padding: 0;
  background: transparent;
  color: var(--cr-text-soft);
  cursor: pointer;
}

.toggle-password:hover,
.toggle-password:focus-visible {
  color: var(--cr-primary-deep);
}

.login-submit {
  margin-top: 8px;
  border: none;
  border-radius: 8px;
  padding: 14px 18px;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  background: var(--cr-primary);
  box-shadow: 0 0.5rem 1.4rem rgba(255, 140, 0, 0.22);
}

.login-submit:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.login-switch {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 22px;
  color: var(--cr-text-soft);
  font-size: 14px;
}

.login-switch button {
  border: none;
  padding: 0;
  background: transparent;
  color: var(--cr-primary-deep);
  cursor: pointer;
  font-weight: 700;
}

@media (max-width: 640px) {
  .login-shell {
    justify-content: center;
    padding: 24px;
  }

  .login-panel {
    padding: 30px 24px;
  }
}
</style>
