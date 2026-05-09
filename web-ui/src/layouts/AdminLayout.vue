<template>
  <div class="layout-shell">
    <header class="topbar">
      <div class="topbar-brand">
        <div class="logo-wrap topbar-logo">
          <img :src="brandLogo" alt="Code Reviewer logo" />
        </div>
        <div class="topbar-title">
          <span>Code Reviewer</span>
          <em>v1.2</em>
        </div>
      </div>

      <div class="topbar-actions">
        <button class="icon-button" type="button" aria-label="通知">
          <Bell />
        </button>
        <button class="icon-button" type="button" aria-label="帮助" @click="openManual">
          <QuestionFilled />
        </button>
        <el-dropdown trigger="click" @command="handleUserCommand">
          <button class="user-card" type="button">
            <div class="user-text">
              <strong>{{ displayName }}</strong>
              <span>{{ roleLabel }}</span>
            </div>
            <div class="user-avatar">{{ usernameInitial }}</div>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="body-shell">
      <aside class="sidebar">
        <nav class="nav-list">
          <RouterLink
            v-for="item in visibleNavItems"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            active-class="is-active"
          >
            <span class="nav-icon">
              <component :is="item.icon" />
            </span>
            <span class="nav-label">{{ item.label }}</span>
          </RouterLink>
        </nav>
      </aside>

      <div class="content-shell">
        <section class="page-shell">
          <main class="content-area">
            <RouterView />
          </main>
        </section>
      </div>
    </div>

    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="420px" destroy-on-close>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="96px">
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input v-model="passwordForm.currentPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="passwordDialogVisible = false">取消</el-button>
          <el-button type="warning" :loading="passwordSubmitting" @click="submitPasswordChange">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, reactive, ref } from "vue";
import type { Component } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import {
  Bell,
  DataBoard,
  Document,
  FolderOpened,
  Operation,
  QuestionFilled,
  User
} from "@element-plus/icons-vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage } from "element-plus";
import { changePasswordApi } from "../api/auth";
import { useAuthStore } from "../stores/auth";
import brandLogo from "../assets/images/code-reviewer-logo.png";

interface NavItem {
  path: string;
  label: string;
  icon: Component;
  adminOnly?: boolean;
}

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const { displayName, isAdmin, username, role } = storeToRefs(authStore);
const passwordDialogVisible = ref(false);
const passwordSubmitting = ref(false);
const passwordFormRef = ref<FormInstance>();
const passwordForm = reactive({
  currentPassword: "",
  newPassword: "",
  confirmPassword: ""
});

const passwordRules: FormRules = {
  currentPassword: [{ required: true, message: "请输入当前密码", trigger: "blur" }],
  newPassword: [{ required: true, message: "请输入新密码", trigger: "blur" }],
  confirmPassword: [
    { required: true, message: "请再次输入新密码", trigger: "blur" },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error("两次输入的新密码不一致"));
          return;
        }
        callback();
      },
      trigger: "blur"
    }
  ]
};

const navItems: NavItem[] = [
  { path: "/dashboard", label: "看板", icon: markRaw(DataBoard) },
  { path: "/llm-models", label: "大模型管理", icon: markRaw(Operation) },
  { path: "/projects", label: "项目管理", icon: markRaw(FolderOpened) },
  { path: "/reviews", label: "审查记录", icon: markRaw(Document) },
  { path: "/users", label: "用户管理", icon: markRaw(User), adminOnly: true }
];

const visibleNavItems = computed(() => navItems.filter((item) => !item.adminOnly || isAdmin.value));

const usernameInitial = computed(() => {
  const value = (displayName.value || username.value || "U").trim();
  return value.slice(0, 1).toUpperCase();
});

const roleLabel = computed(() => (role.value === "ADMIN" ? "系统管理员" : "项目成员"));

const resetPasswordForm = () => {
  passwordForm.currentPassword = "";
  passwordForm.newPassword = "";
  passwordForm.confirmPassword = "";
  passwordFormRef.value?.clearValidate?.();
};

const resolveErrorMessage = (error: unknown, fallback: string) => {
  if (typeof error === "object" && error !== null) {
    const maybeAxios = error as { response?: { data?: { message?: string } } };
    return maybeAxios.response?.data?.message || fallback;
  }
  return fallback;
};

const submitPasswordChange = async () => {
  if (!passwordFormRef.value) {
    return;
  }
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) {
      return;
    }
    passwordSubmitting.value = true;
    try {
      await changePasswordApi({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword
      });
      ElMessage.success("密码修改成功，请重新登录");
      passwordDialogVisible.value = false;
      resetPasswordForm();
      authStore.clearAuth();
      router.replace("/login");
    } catch (error) {
      ElMessage.error(resolveErrorMessage(error, "修改密码失败"));
    } finally {
      passwordSubmitting.value = false;
    }
  });
};

const handleUserCommand = async (command: string) => {
  if (command === "logout") {
    await authStore.logout();
    router.push("/login");
    return;
  }
  if (command === "password") {
    resetPasswordForm();
    passwordDialogVisible.value = true;
  }
};

const openManual = () => {
  window.open("/manual", "_blank", "noopener,noreferrer");
};
</script>

<style scoped>
.layout-shell {
  min-height: 100vh;
  background: var(--cr-background);
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 28px;
  background: rgba(250, 250, 245, 0.92);
  backdrop-filter: blur(12px);
  box-shadow: var(--cr-shadow-soft);
}

.topbar-brand {
  display: flex;
  align-items: center;
  gap: 18px;
}

.logo-wrap {
  width: 144px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  overflow: hidden;
}

.topbar-logo {
  height: 48px;
}

.topbar-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.topbar-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.topbar-title span {
  padding: 6px 12px;
  border: 1px solid rgba(230, 162, 60, 0.2);
  border-radius: 999px;
  background: rgba(255, 247, 230, 0.64);
  color: #7a4200;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0;
  line-height: 22px;
}

.topbar-title em {
  padding: 2px 8px;
  border: 1px solid rgba(230, 162, 60, 0.24);
  border-radius: 999px;
  background: rgba(255, 247, 230, 0.76);
  color: #b96800;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
  letter-spacing: 0.04em;
  line-height: 18px;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.icon-button,
.user-card {
  border: none;
  cursor: pointer;
  color: rgba(86, 67, 52, 0.72);
  transition: background 180ms ease, color 180ms ease, transform 180ms ease;
}

.icon-button {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: transparent;
  font-size: 17px;
}

.icon-button:hover,
.user-card:hover {
  background: rgba(255, 255, 255, 0.75);
  color: var(--cr-primary);
}

.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 6px 4px 12px;
  border-radius: 999px;
  background: transparent;
}

.user-text {
  display: grid;
  justify-items: end;
  gap: 2px;
}

.user-text strong {
  color: var(--cr-text);
  font-size: 13px;
  font-weight: 700;
}

.user-text span {
  color: rgba(86, 67, 52, 0.62);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--cr-primary), var(--cr-primary-deep));
  color: #fff;
  font-weight: 800;
  box-shadow: 0 8px 18px rgba(255, 140, 0, 0.18);
}

.body-shell {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  min-height: calc(100vh - 72px);
}

.sidebar {
  background: var(--cr-surface-low);
  padding: 24px 16px 20px;
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 72px;
  height: calc(100vh - 72px);
  overflow-y: auto;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 8px;
  color: rgba(86, 67, 52, 0.9);
  text-decoration: none;
  font-size: 14px;
  font-weight: 600;
  transition: all 180ms ease;
}

.nav-item:hover {
  transform: translateX(4px);
  color: var(--cr-primary);
  background: rgba(255, 255, 255, 0.48);
}

.nav-item.is-active {
  background: var(--cr-surface-paper);
  color: var(--cr-primary);
  box-shadow: 0 2px 12px rgba(144, 77, 0, 0.05);
}

.nav-icon {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.content-shell {
  min-width: 0;
}

.page-shell {
  padding: 28px 28px 32px;
}

.content-area {
  min-width: 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 1080px) {
  .body-shell {
    grid-template-columns: 1fr;
  }

  .topbar {
    padding: 0 18px;
  }

  .sidebar {
    border-bottom: 1px solid var(--cr-outline);
  }

  .page-shell {
    padding: 20px 18px 24px;
  }
}
</style>
