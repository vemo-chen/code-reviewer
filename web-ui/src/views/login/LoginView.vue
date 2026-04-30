<template>
  <div class="login-shell">
    <section class="login-panel">
      <p class="eyebrow">Internal Access</p>
      <h1>Code Reviewer</h1>
      <p class="description">统一管理项目配置、AI 审查记录与团队研发质效，支持按项目权限精确查看数据。</p>

      <form class="login-form" @submit.prevent="handleLogin">
        <label>
          <span>用户名</span>
          <input v-model.trim="form.username" type="text" placeholder="请输入用户名" />
        </label>
        <label>
          <span>密码</span>
          <input v-model.trim="form.password" type="password" placeholder="请输入密码" />
        </label>
        <button type="submit" :disabled="submitting">{{ submitting ? "登录中..." : "进入系统" }}</button>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useAuthStore } from "../../stores/auth";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const submitting = ref(false);

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
    ElMessage.warning("请输入用户名和密码");
    return;
  }

  submitting.value = true;
  try {
    await authStore.login(form.username, form.password);
    ElMessage.success("登录成功");
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/dashboard";
    router.replace(redirect);
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, "登录失败，请检查用户名和密码"));
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.login-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background:
    radial-gradient(circle at 78% 14%, rgba(255, 140, 0, 0.14), transparent 18%),
    linear-gradient(180deg, #fafaf5 0%, #f4f4ef 100%);
}

.login-panel {
  width: min(100%, 460px);
  padding: 40px;
  border-radius: 12px;
  background: var(--cr-surface-paper);
  box-shadow: var(--cr-shadow-soft);
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
  margin-top: 30px;
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

.login-form button {
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

.login-form button:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}
</style>
