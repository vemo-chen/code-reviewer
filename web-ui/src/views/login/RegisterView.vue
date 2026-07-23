<template>
  <div class="register-shell" :style="{ '--login-background': `url(${loginBackground})` }">
    <section class="register-panel">
      <p class="eyebrow">Member Registration</p>
      <h1>注册账号</h1>
      <p class="description">注册后将自动成为普通成员，可在权限范围内查看项目和审查记录。</p>

      <form class="register-form" @submit.prevent="handleRegister">
        <label>
          <span>用户名</span>
          <input v-model.trim="form.username" type="text" placeholder="用于登录系统" autocomplete="username" />
        </label>
        <label>
          <span>昵称</span>
          <input v-model.trim="form.displayName" type="text" placeholder="系统内展示名称" autocomplete="name" />
        </label>
        <label>
          <span>密码</span>
          <input v-model.trim="form.password" type="password" placeholder="至少 4 位" autocomplete="new-password" />
        </label>
        <label>
          <span>确认密码</span>
          <input v-model.trim="form.confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" />
        </label>
        <label>
          <span>GitLab 用户名</span>
          <input v-model.trim="form.gitlabUsername" type="text" placeholder="例如 zhangsan" />
          <small>用于识别你的 GitLab 账号，并把 GitLab 提交、合并请求和审查记录关联到你的系统账号。请填写 GitLab 个人资料中的用户名。</small>
        </label>
        <button type="submit" :disabled="submitting">{{ submitting ? "注册中..." : "注册并进入系统" }}</button>
      </form>

      <div class="register-switch">
        <span>已有账号？</span>
        <button type="button" @click="goLogin">返回登录</button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useAuthStore } from "../../stores/auth";
import loginBackground from "../../assets/images/login-background.png";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const submitting = ref(false);

const form = reactive({
  username: "",
  displayName: "",
  password: "",
  confirmPassword: "",
  gitlabUsername: ""
});

const resolveErrorMessage = (error: unknown, fallback: string) => {
  if (typeof error === "object" && error !== null) {
    const maybeAxios = error as { response?: { data?: { message?: string } } };
    return maybeAxios.response?.data?.message || fallback;
  }
  return fallback;
};

const resolveRedirect = () => {
  return typeof route.query.redirect === "string" ? route.query.redirect : "/dashboard";
};

const handleRegister = async () => {
  if (!form.username || !form.displayName || !form.password || !form.confirmPassword) {
    ElMessage.warning("请填写用户名、昵称和密码");
    return;
  }
  if (form.password.length < 4) {
    ElMessage.warning("密码至少 4 位");
    return;
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning("两次输入的密码不一致");
    return;
  }

  submitting.value = true;
  try {
    await authStore.register({
      username: form.username,
      displayName: form.displayName,
      password: form.password,
      gitlabUsername: form.gitlabUsername || undefined
    });
    ElMessage.success("注册成功");
    router.replace(resolveRedirect());
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, "注册失败，请稍后重试"));
  } finally {
    submitting.value = false;
  }
};

const goLogin = () => {
  const query = typeof route.query.redirect === "string" ? { redirect: route.query.redirect } : undefined;
  router.push({ name: "login", query });
};
</script>

<style scoped>
.register-shell {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 24px 8vw 24px 32px;
  background-image: var(--login-background);
  background-position: center;
  background-size: 100% 100%;
  background-repeat: no-repeat;
}

.register-panel {
  width: min(100%, 500px);
  padding: 24px 36px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
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
  font-size: 32px;
  line-height: 1.15;
  color: var(--cr-text);
}

.description {
  margin: 10px 0 0;
  color: var(--cr-text-soft);
  line-height: 1.6;
}

.register-form {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.register-form label {
  display: grid;
  gap: 6px;
}

.register-form span {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--cr-text-soft);
}

.register-form input {
  width: 100%;
  border: none;
  border-bottom: 2px solid rgba(144, 77, 0, 0.12);
  padding: 9px 4px 8px;
  background: transparent;
  color: var(--cr-text);
  outline: none;
}

.register-form input:focus {
  border-bottom-color: var(--cr-primary);
}

.register-form small {
  color: var(--cr-text-soft);
  font-size: 12px;
  line-height: 1.5;
}

.register-form button {
  margin-top: 4px;
  border: none;
  border-radius: 8px;
  padding: 12px 18px;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  background: var(--cr-primary);
  box-shadow: 0 0.5rem 1.4rem rgba(255, 140, 0, 0.22);
}

.register-form button:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.register-switch {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
  color: var(--cr-text-soft);
  font-size: 14px;
}

.register-switch button {
  border: none;
  padding: 0;
  background: transparent;
  color: var(--cr-primary-deep);
  cursor: pointer;
  font-weight: 700;
}

@media (max-width: 640px) {
  .register-shell {
    align-items: flex-start;
    justify-content: center;
    padding: 24px;
  }

  .register-panel {
    padding: 30px 24px;
  }

  h1 {
    font-size: 30px;
  }
}
</style>
