<template>
  <section class="list-page">
    <section class="query-panel">
      <el-form :inline="true" :model="queryForm">
        <el-form-item>
          <el-input v-model.trim="queryForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item>
          <el-input v-model.trim="queryForm.displayName" placeholder="请输入昵称" clearable />
        </el-form-item>
        <el-form-item>
          <el-input v-model.trim="queryForm.gitlabUsername" placeholder="请输入 GitLab 用户名" clearable />
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="queryForm.role"
            class="query-select"
            placeholder="角色"
            clearable
            filterable
          >
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="queryForm.status"
            class="query-select"
            placeholder="状态"
            clearable
            filterable
          >
            <el-option label="启用" value="ENABLE" />
            <el-option label="停用" value="DISABLE" />
          </el-select>
        </el-form-item>
        <el-form-item class="actions">
          <el-button type="warning" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="warning" plain @click="openCreateDrawer">新建用户</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-panel">
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="displayName" label="昵称" min-width="130" />
        <el-table-column prop="gitlabUsername" label="GitLab 用户名" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.gitlabUsername || "--" }}
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="120">
          <template #default="{ row }">
            <span :class="['status-pill', row.role === 'ADMIN' ? 'is-accent' : 'is-neutral']">
              {{ row.role === "ADMIN" ? "管理员" : "普通用户" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <span :class="['status-pill', row.status === 'ENABLE' ? 'is-primary' : 'is-danger']">
              {{ row.status === "ENABLE" ? "启用" : "停用" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="projectCount" label="关联项目数" min-width="120" />
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button link type="warning" @click="openEditDrawer(row)">编辑</el-button>
              <el-button link type="warning" @click="openAssignDrawer(row)">分配项目</el-button>
              <el-button link type="warning" @click="handleResetPassword(row)">重置密码</el-button>
              <el-button link :type="row.status === 'ENABLE' ? 'danger' : 'warning'" @click="toggleStatus(row)">
                {{ row.status === "ENABLE" ? "停用" : "启用" }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <div class="pagination-total">共{{ pagination.total }} 条</div>
        <div class="pagination-actions">
          <el-pagination
            v-model:current-page="pagination.pageNo"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            layout="prev, pager, next"
            background
            @current-change="loadUsers"
          />
          <el-select v-model="pagination.pageSize" class="page-size-select" @change="handlePageSizeChange">
            <el-option label="10条/页" :value="10" />
            <el-option label="20条/页" :value="20" />
            <el-option label="50条/页" :value="50" />
          </el-select>
        </div>
      </div>
    </section>

    <el-drawer
      v-model="userDrawerVisible"
      :show-close="false"
      size="520px"
      destroy-on-close
      class="user-edit-drawer"
    >
      <template #header>
        <div class="drawer-header">
          <div class="drawer-header__title">
            <h3>{{ drawerMode === "create" ? userDrawerCopy.createTitle : userDrawerCopy.editTitle }}</h3>
          </div>
          <div class="drawer-header__tools">
            <button
              type="button"
              class="drawer-close-btn"
              @click="userDrawerVisible = false"
              :aria-label="drawerMode === 'create' ? userDrawerCopy.closeCreate : userDrawerCopy.closeEdit"
            >
              &#215;
            </button>
          </div>
        </div>
      </template>
      <div class="drawer-body user-drawer-body">
        <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-position="top" class="user-form">
          <section class="form-section">
            <h4 class="form-section__title">{{ userDrawerCopy.baseSection }}</h4>
            <el-form-item :label="userDrawerCopy.usernameLabel" prop="username">
              <el-input v-model.trim="userForm.username" :placeholder="userDrawerCopy.usernamePlaceholder" />
            </el-form-item>
            <el-form-item :label="userDrawerCopy.displayNameLabel" prop="displayName">
              <el-input v-model.trim="userForm.displayName" :placeholder="userDrawerCopy.displayNamePlaceholder" />
            </el-form-item>
            <el-form-item :label="userDrawerCopy.gitlabUsernameLabel">
              <el-input v-model.trim="userForm.gitlabUsername" :placeholder="userDrawerCopy.gitlabUsernamePlaceholder" clearable />
            </el-form-item>
          </section>

          <section class="form-section">
            <h4 class="form-section__title">{{ userDrawerCopy.accountSection }}</h4>
            <el-form-item v-if="drawerMode === 'create'" :label="userDrawerCopy.passwordLabel" prop="password">
              <el-input v-model.trim="userForm.password" show-password :placeholder="userDrawerCopy.passwordPlaceholder" />
            </el-form-item>
            <el-form-item :label="userDrawerCopy.roleLabel" prop="role">
              <el-select v-model="userForm.role" class="full-width">
                <el-option :label="userDrawerCopy.roleAdmin" value="ADMIN" />
                <el-option :label="userDrawerCopy.roleUser" value="USER" />
              </el-select>
            </el-form-item>
            <el-form-item :label="userDrawerCopy.statusLabel" prop="status">
              <el-select v-model="userForm.status" class="full-width">
                <el-option :label="userDrawerCopy.statusEnable" value="ENABLE" />
                <el-option :label="userDrawerCopy.statusDisable" value="DISABLE" />
              </el-select>
            </el-form-item>
          </section>
        </el-form>
      </div>
      <div class="drawer-footer">
        <el-button @click="userDrawerVisible = false">{{ userDrawerCopy.cancel }}</el-button>
        <el-button type="warning" :loading="saving" @click="submitUser">{{ userDrawerCopy.save }}</el-button>
      </div>
    </el-drawer>

    <el-drawer
      v-model="assignDrawerVisible"
      :show-close="false"
      size="560px"
      destroy-on-close
      class="assign-project-drawer"
    >
      <template #header>
        <div class="drawer-header">
          <div class="drawer-header__title">
            <h3>{{ assignDrawerCopy.title }}</h3>
          </div>
          <div class="drawer-header__tools">
            <button
              type="button"
              class="drawer-close-btn"
              @click="assignDrawerVisible = false"
              :aria-label="assignDrawerCopy.closeLabel"
            >
              &#215;
            </button>
          </div>
        </div>
      </template>
      <div class="drawer-body assign-drawer-body">
        <div class="assign-header">
          <div>
            <strong>{{ assignTarget?.displayName || assignTarget?.username || "--" }}</strong>
            <p>{{ assignTarget?.username || "" }}</p>
          </div>
          <div class="assign-count">已选 {{ assignProjectIds.length }} 个项目</div>
        </div>
        <el-input v-model.trim="projectKeyword" placeholder="搜索项目名称或 GitLab URL" clearable />
        <div class="project-select-list">
          <label v-for="project in filteredProjects" :key="project.id" class="project-select-item">
            <el-checkbox :model-value="assignProjectIds.includes(project.id)" @change="toggleProject(project.id, $event)" />
            <div class="project-select-content">
              <strong>{{ project.projectName }}</strong>
              <span>{{ project.gitlabProjectUrl || '--' }}</span>
            </div>
          </label>
          <el-empty v-if="!filteredProjects.length" description="暂无可选项目" />
        </div>
      </div>
      <div class="drawer-footer">
        <el-button @click="assignDrawerVisible = false">取消</el-button>
        <el-button type="warning" :loading="assignSaving" @click="submitAssignProjects">保存</el-button>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import { fetchProjects } from "../../api/projects";
import {
  assignUserProjects,
  createUser,
  fetchUserProjects,
  fetchUsers,
  resetUserPassword,
  updateUser,
  updateUserStatus
} from "../../api/users";

interface UserItem {
  id: number;
  username: string;
  displayName: string;
  gitlabUsername: string;
  role: string;
  status: string;
  projectCount: number;
  createdAt: string | null;
  updatedAt: string | null;
}

interface ProjectOption {
  id: number;
  projectName: string;
  gitlabProjectUrl: string;
}

const loading = ref(false);
const saving = ref(false);
const assignSaving = ref(false);
const userDrawerVisible = ref(false);
const assignDrawerVisible = ref(false);
const drawerMode = ref<"create" | "edit">("create");
const userFormRef = ref<FormInstance>();
const records = ref<UserItem[]>([]);
const allProjects = ref<ProjectOption[]>([]);
const assignProjectIds = ref<number[]>([]);
const assignTarget = ref<UserItem | null>(null);
const projectKeyword = ref("");

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0
});

const queryForm = reactive({
  username: "",
  displayName: "",
  gitlabUsername: "",
  role: "",
  status: ""
});

const activeQuery = reactive({
  username: "",
  displayName: "",
  gitlabUsername: "",
  role: "",
  status: ""
});

const userForm = reactive({
  id: undefined as number | undefined,
  username: "",
  displayName: "",
  gitlabUsername: "",
  password: "",
  role: "USER",
  status: "ENABLE"
});

const userDrawerCopy = {
  createTitle: "\u65b0\u5efa\u7528\u6237",
  editTitle: "\u7f16\u8f91\u7528\u6237",
  closeCreate: "\u5173\u95ed\u65b0\u5efa\u7528\u6237\u62bd\u5c49",
  closeEdit: "\u5173\u95ed\u7f16\u8f91\u7528\u6237\u62bd\u5c49",
  baseSection: "\u57fa\u7840\u4fe1\u606f",
  accountSection: "\u8d26\u53f7\u4fe1\u606f",
  usernameLabel: "\u7528\u6237\u540d",
  usernamePlaceholder: "\u8bf7\u8f93\u5165\u7528\u6237\u540d",
  displayNameLabel: "\u663e\u793a\u540d",
  displayNamePlaceholder: "\u8bf7\u8f93\u5165\u663e\u793a\u540d",
  gitlabUsernameLabel: "GitLab \u7528\u6237\u540d",
  gitlabUsernamePlaceholder: "\u8bf7\u8f93\u5165 GitLab \u7528\u6237\u540d",
  passwordLabel: "\u521d\u59cb\u5bc6\u7801",
  passwordPlaceholder: "\u8bf7\u8f93\u5165\u521d\u59cb\u5bc6\u7801",
  roleLabel: "\u89d2\u8272",
  roleAdmin: "\u7ba1\u7406\u5458",
  roleUser: "\u666e\u901a\u7528\u6237",
  statusLabel: "\u72b6\u6001",
  statusEnable: "\u542f\u7528",
  statusDisable: "\u505c\u7528",
  cancel: "\u53d6\u6d88",
  save: "\u4fdd\u5b58"
} as const;

const assignDrawerCopy = {
  title: "\u5206\u914d\u9879\u76ee",
  closeLabel: "\u5173\u95ed\u5206\u914d\u9879\u76ee\u62bd\u5c49"
} as const;

const userRules: FormRules = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  displayName: [{ required: true, message: "请输入昵称", trigger: "blur" }],
  password: [{ required: true, message: "请输入初始密码", trigger: "blur" }],
  role: [{ required: true, message: "请选择角色", trigger: "change" }],
  status: [{ required: true, message: "请选择状态", trigger: "change" }]
};

const filteredProjects = computed(() => {
  const keyword = projectKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return allProjects.value;
  }
  return allProjects.value.filter((project) =>
    project.projectName.toLowerCase().includes(keyword)
      || (project.gitlabProjectUrl || "").toLowerCase().includes(keyword)
  );
});

const resolveErrorMessage = (error: unknown, fallback: string) => {
  if (typeof error === "object" && error !== null) {
    const maybeAxios = error as { response?: { data?: { message?: string } } };
    return maybeAxios.response?.data?.message || fallback;
  }
  return fallback;
};

const applyFilters = () => {
  activeQuery.username = queryForm.username.trim();
  activeQuery.displayName = queryForm.displayName.trim();
  activeQuery.gitlabUsername = queryForm.gitlabUsername.trim();
  activeQuery.role = queryForm.role;
  activeQuery.status = queryForm.status;
};

const loadUsers = async () => {
  loading.value = true;
  try {
    const response = await fetchUsers({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      username: activeQuery.username || undefined,
      displayName: activeQuery.displayName || undefined,
      gitlabUsername: activeQuery.gitlabUsername || undefined,
      role: activeQuery.role || undefined,
      status: activeQuery.status || undefined
    });
    const pageData = response.data.data;
    records.value = pageData.records || [];
    pagination.total = pageData.total || 0;
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, "加载用户列表失败"));
  } finally {
    loading.value = false;
  }
};

const loadProjects = async () => {
  const response = await fetchProjects({ pageNo: 1, pageSize: 500 });
  const pageData = response.data.data;
  allProjects.value = pageData.records || [];
};

const handleSearch = async () => {
  pagination.pageNo = 1;
  applyFilters();
  await loadUsers();
};

const resetFilters = async () => {
  queryForm.username = "";
  queryForm.displayName = "";
  queryForm.gitlabUsername = "";
  queryForm.role = "";
  queryForm.status = "";
  pagination.pageNo = 1;
  applyFilters();
  await loadUsers();
};

const handlePageSizeChange = () => {
  pagination.pageNo = 1;
  loadUsers();
};

const resetUserForm = () => {
  userForm.id = undefined;
  userForm.username = "";
  userForm.displayName = "";
  userForm.gitlabUsername = "";
  userForm.password = "";
  userForm.role = "USER";
  userForm.status = "ENABLE";
};

const openCreateDrawer = () => {
  drawerMode.value = "create";
  resetUserForm();
  userDrawerVisible.value = true;
};

const openEditDrawer = (row: UserItem) => {
  drawerMode.value = "edit";
  userForm.id = row.id;
  userForm.username = row.username;
  userForm.displayName = row.displayName;
  userForm.gitlabUsername = row.gitlabUsername || "";
  userForm.password = "";
  userForm.role = row.role;
  userForm.status = row.status;
  userDrawerVisible.value = true;
};

const submitUser = async () => {
  if (!userFormRef.value) {
    return;
  }
  await userFormRef.value.validate(async (valid) => {
    if (!valid) {
      return;
    }
    saving.value = true;
    try {
      const payload = {
        username: userForm.username.trim(),
        displayName: userForm.displayName.trim(),
        gitlabUsername: userForm.gitlabUsername.trim() || undefined,
        password: userForm.password.trim() || undefined,
        role: userForm.role,
        status: userForm.status
      };
      if (drawerMode.value === "create") {
        await createUser(payload);
        ElMessage.success("用户创建成功");
      } else if (userForm.id) {
        await updateUser(userForm.id, payload);
        ElMessage.success("用户更新成功");
      }
      userDrawerVisible.value = false;
      await loadUsers();
    } catch (error) {
      ElMessage.error(resolveErrorMessage(error, drawerMode.value === "create" ? "用户创建失败" : "用户更新失败"));
    } finally {
      saving.value = false;
    }
  });
};

const handleResetPassword = async (row: UserItem) => {
  try {
    const result = await ElMessageBox.prompt(`请输入用户 ${row.displayName} 的新密码`, "重置密码", {
      confirmButtonText: "保存",
      cancelButtonText: "取消",
      inputType: "password",
      inputPlaceholder: "请输入新密码"
    });
    await resetUserPassword(row.id, result.value);
    ElMessage.success("密码已重置");
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(resolveErrorMessage(error, "重置密码失败"));
    }
  }
};

const toggleStatus = async (row: UserItem) => {
  const nextStatus = row.status === "ENABLE" ? "DISABLE" : "ENABLE";
  try {
    await updateUserStatus(row.id, nextStatus);
    ElMessage.success(nextStatus === "ENABLE" ? "用户已启用" : "用户已停用");
    await loadUsers();
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, "更新用户状态失败"));
  }
};

const openAssignDrawer = async (row: UserItem) => {
  assignTarget.value = row;
  projectKeyword.value = "";
  assignDrawerVisible.value = true;
  try {
    await loadProjects();
    const response = await fetchUserProjects(row.id);
    assignProjectIds.value = response.data.data.projectIds || [];
  } catch (error) {
    assignDrawerVisible.value = false;
    ElMessage.error(resolveErrorMessage(error, "加载用户项目失败"));
  }
};

const toggleProject = (projectId: number, checked: boolean | string | number) => {
  const isChecked = Boolean(checked);
  if (isChecked) {
    if (!assignProjectIds.value.includes(projectId)) {
      assignProjectIds.value = [...assignProjectIds.value, projectId];
    }
    return;
  }
  assignProjectIds.value = assignProjectIds.value.filter((id) => id !== projectId);
};

const submitAssignProjects = async () => {
  if (!assignTarget.value) {
    return;
  }
  assignSaving.value = true;
  try {
    await assignUserProjects(assignTarget.value.id, assignProjectIds.value);
    ElMessage.success("项目授权已更新");
    assignDrawerVisible.value = false;
    await loadUsers();
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, "项目授权更新失败"));
  } finally {
    assignSaving.value = false;
  }
};

const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return "--";
  }
  return value.replace("T", " ").slice(0, 19);
};

onMounted(async () => {
  applyFilters();
  await loadUsers();
});
</script>

<style scoped>
.list-page {
  display: grid;
  gap: 20px;
}

.query-panel,
.table-panel {
  padding: 20px 22px;
  border-radius: 16px;
  background: var(--cr-surface-paper);
  box-shadow: var(--cr-shadow-card);
}

.actions {
  margin-left: auto;
}

.query-select {
  width: 156px;
}

.action-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.action-group :deep(.el-button.is-link) {
  height: 22px;
  min-height: 22px;
  margin-left: 0;
  padding: 0 7px;
  border: 1px solid #e9ebec;
  border-radius: 4px;
  background: #ffffff;
  color: #303133;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
}

.action-group :deep(.el-button.is-link:hover),
.action-group :deep(.el-button.is-link:focus-visible) {
  border-color: var(--cr-primary);
  background: rgba(255, 140, 0, 0.06);
  color: var(--cr-primary);
}

.action-group :deep(.el-button.is-link.is-disabled) {
  border-color: #e9ebec;
  background: #ffffff;
  color: rgba(86, 67, 52, 0.34);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 0 7px;
  border: 1px solid currentColor;
  border-radius: 4px;
  background: #ffffff;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
}

.is-primary {
  color: #389e0d;
  border-color: #b7eb8f;
  background: #f6ffed;
}

.is-accent {
  color: #d46b08;
  border-color: #ffd591;
  background: #fff7e6;
}

.is-neutral {
  color: #606266;
  border-color: #e9ebec;
  background: #ffffff;
}

.is-danger {
  color: #b32222;
  border-color: #ffccc7;
  background: #fff2f0;
}

.pagination-bar {
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.pagination-total {
  color: var(--cr-text-soft);
  font-size: 13px;
}

.pagination-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-size-select {
  width: 116px;
}

:deep(.user-edit-drawer .el-drawer__header),
:deep(.assign-project-drawer .el-drawer__header) {
  margin-bottom: 0;
  padding: 14px 20px 12px;
  border-bottom: 1px solid #ebeef5;
  background: rgba(255, 255, 255, 0.96);
}

.drawer-body {
  padding-right: 8px;
}

.user-drawer-body {
  display: grid;
  gap: 18px;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.drawer-header__title h3 {
  margin: 0;
  color: var(--cr-text);
  font-size: 16px;
  font-weight: 800;
}

.drawer-header__tools {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-left: auto;
}

.drawer-close-btn {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e9ebec;
  border-radius: 9px;
  background: #ffffff;
  color: rgba(96, 98, 102, 0.9);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.18s ease;
}

.drawer-close-btn:hover,
.drawer-close-btn:focus-visible {
  border-color: #dcdfe6;
  background: #f8fafc;
  color: var(--cr-text);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 20px;
}

.user-form {
  display: grid;
  gap: 16px;
}

.form-section {
  display: grid;
  gap: 4px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  background: #f8fafc;
}

.form-section__title {
  margin: 0 0 4px;
  color: var(--cr-text);
  font-size: 18px;
  font-weight: 800;
}

.user-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.user-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.user-form :deep(.el-form-item__label) {
  padding: 0 0 8px;
  color: rgba(86, 67, 52, 0.78);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
}

.user-form :deep(.el-input__wrapper),
.user-form :deep(.el-select__wrapper) {
  min-height: 40px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px #ebeef5 inset;
  background: rgba(255, 255, 255, 0.96);
}

.user-form :deep(.el-input__wrapper.is-focus),
.user-form :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px rgba(230, 162, 60, 0.42) inset;
}

.full-width {
  width: 100%;
}

.assign-drawer-body {
  display: grid;
  gap: 16px;
}

.assign-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.assign-header strong {
  color: var(--cr-text);
  font-size: 18px;
}

.assign-header p {
  margin: 4px 0 0;
  color: var(--cr-text-soft);
  font-size: 13px;
}

.assign-count {
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255, 140, 0, 0.12);
  color: var(--cr-primary-deep);
  font-size: 12px;
  font-weight: 700;
}

.project-select-list {
  display: grid;
  gap: 12px;
  max-height: 460px;
  overflow-y: auto;
  padding-right: 6px;
}

.project-select-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid rgba(221, 193, 174, 0.34);
  background: rgba(250, 250, 245, 0.72);
}

.project-select-content {
  display: grid;
  gap: 6px;
}

.project-select-content strong {
  color: var(--cr-text);
}

.project-select-content span {
  color: var(--cr-text-soft);
  font-size: 12px;
  word-break: break-all;
}
</style>
