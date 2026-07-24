<template>
  <div class="register-shell" :style="{ '--login-background': `url(${loginBackground})` }">
    <section class="register-panel">
      <header class="register-header">
        <p class="eyebrow">Member Registration</p>
        <h1>注册账号</h1>
        <p class="description">注册后默认成为普通成员，可在权限范围内查看项目和审查记录。</p>
      </header>

      <form class="register-form" @submit.prevent="handleRegister">
        <div class="form-grid">
          <label>
            <span class="field-title">用户名</span>
            <input v-model.trim="form.username" type="text" placeholder="用于登录系统" autocomplete="username" />
          </label>
          <label>
            <span class="field-title">昵称</span>
            <input v-model.trim="form.displayName" type="text" placeholder="系统内展示名称" autocomplete="name" />
          </label>
          <label class="wide-field">
            <span class="field-title">
              邮箱
              <el-tooltip content="用于公司 SSO 登录后关联你的平台账号，请填写唯一的公司邮箱。" placement="top" :show-after="200">
                <el-icon class="field-help" tabindex="0">
                  <QuestionFilled />
                </el-icon>
              </el-tooltip>
            </span>
            <input v-model.trim="form.email" type="email" placeholder="请输入公司邮箱" autocomplete="email" />
            <small class="field-hint">用于 SSO 账号关联</small>
          </label>
          <label>
            <span class="field-title">密码</span>
            <div class="password-input">
              <input
                v-model.trim="form.password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="至少 4 位"
                autocomplete="new-password"
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
          <label>
            <span class="field-title">确认密码</span>
            <div class="password-input">
              <input
                v-model.trim="form.confirmPassword"
                :type="showConfirmPassword ? 'text' : 'password'"
                placeholder="再次输入密码"
                autocomplete="new-password"
              />
              <button
                class="toggle-password"
                type="button"
                :aria-label="showConfirmPassword ? '隐藏密码' : '显示密码'"
                :title="showConfirmPassword ? '隐藏密码' : '显示密码'"
                @click="showConfirmPassword = !showConfirmPassword"
              >
                <el-icon>
                  <Hide v-if="showConfirmPassword" />
                  <View v-else />
                </el-icon>
              </button>
            </div>
          </label>
          <label class="wide-field">
            <span class="field-title">
              GitLab 用户名
              <el-tooltip content="用于识别你的 GitLab 账号，并关联 GitLab 提交、合并请求和审查记录。请填写 GitLab 个人资料中的用户名。" placement="top" :show-after="200">
                <el-icon class="field-help" tabindex="0">
                  <QuestionFilled />
                </el-icon>
              </el-tooltip>
            </span>
            <input v-model.trim="form.gitlabUsername" type="text" placeholder="例如 zhangsan" />
            <small class="field-hint">用于关联 GitLab 记录</small>
          </label>
        </div>
        <button class="register-submit" type="submit" :disabled="submitting">{{ submitting ? "注册中..." : "注册并进入系统" }}</button>
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
import { Hide, QuestionFilled, View } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useAuthStore } from "../../stores/auth";
import loginBackground from "../../assets/images/login-background.png";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const submitting = ref(false);
const showPassword = ref(false);
const showConfirmPassword = ref(false);

const form = reactive({
  username: "",
  displayName: "",
  email: "",
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
  if (!form.username || !form.displayName || !form.email || !form.password || !form.confirmPassword) {
    ElMessage.warning("请填写用户名、昵称、邮箱和密码");
    return;
  }
  if (!form.email.includes("@")) {
    ElMessage.warning("邮箱格式不正确");
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
      email: form.email,
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
  width: min(100%, 560px);
  padding: 32px 40px 30px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 24px 56px rgba(44, 54, 68, 0.2);
  backdrop-filter: blur(12px);
}

.register-header {
  padding-bottom: 20px;
  border-bottom: 1px solid rgba(144, 77, 0, 0.12);
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
  font-size: 34px;
  line-height: 1.18;
  color: var(--cr-text);
}

.description {
  margin: 12px 0 0;
  color: var(--cr-text-soft);
  line-height: 1.7;
}

.register-form {
  display: grid;
  gap: 18px;
  margin-top: 22px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 18px;
  row-gap: 16px;
}

.register-form label {
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
}

.wide-field {
  grid-column: 1 / -1;
}

.field-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--cr-text);
}

.field-help {
  color: var(--cr-text-soft);
  cursor: help;
  font-size: 14px;
  outline: none;
}

.field-help:hover,
.field-help:focus-visible {
  color: var(--cr-primary-deep);
}

.register-form input {
  width: 100%;
  border: none;
  border-bottom: 2px solid rgba(144, 77, 0, 0.12);
  padding: 11px 4px 10px;
  background: transparent;
  color: var(--cr-text);
  outline: none;
}

.register-form input:focus {
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

.field-hint {
  color: var(--cr-text-soft);
  font-size: 12px;
  line-height: 1.6;
}

.register-submit {
  margin-top: 2px;
  border: none;
  border-radius: 8px;
  padding: 12px 18px;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  background: var(--cr-primary);
  box-shadow: 0 0.5rem 1.4rem rgba(255, 140, 0, 0.22);
}

.register-submit:disabled {
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

  .form-grid {
    grid-template-columns: 1fr;
  }

  h1 {
    font-size: 30px;
  }
}
</style>
