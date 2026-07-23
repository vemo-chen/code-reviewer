import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";
import AdminLayout from "../layouts/AdminLayout.vue";
import LoginView from "../views/login/LoginView.vue";
import RegisterView from "../views/login/RegisterView.vue";
import DashboardView from "../views/dashboard/DashboardView.vue";
import LlmModelListView from "../views/llm/LlmModelListView.vue";
import ManualView from "../views/manual/ManualView.vue";
import ProjectListView from "../views/projects/ProjectListView.vue";
import ReviewListView from "../views/reviews/ReviewListView.vue";
import UserListView from "../views/users/UserListView.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: LoginView,
      meta: { public: true, title: "登录" }
    },
    {
      path: "/register",
      name: "register",
      component: RegisterView,
      meta: { public: true, title: "注册" }
    },
    {
      path: "/manual",
      name: "manual",
      component: ManualView,
      meta: { public: true, title: "用户手册" }
    },
    {
      path: "/",
      component: AdminLayout,
      redirect: "/dashboard",
      children: [
        {
          path: "dashboard",
          name: "dashboard",
          component: DashboardView,
          meta: { title: "看板" }
        },
        {
          path: "llm-models",
          name: "llm-models",
          component: LlmModelListView,
          meta: { title: "大模型管理" }
        },
        {
          path: "projects",
          name: "projects",
          component: ProjectListView,
          meta: { title: "项目管理" }
        },
        {
          path: "reviews",
          name: "reviews",
          component: ReviewListView,
          meta: { title: "审查记录" }
        },
        {
          path: "users",
          name: "users",
          component: UserListView,
          meta: { title: "用户管理", adminOnly: true }
        }
      ]
    }
  ]
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore();

  if (!to.meta.public) {
    try {
      await authStore.initialize();
    } catch (error) {
      if (to.name !== "login") {
        return { name: "login", query: { redirect: to.fullPath } };
      }
    }
  }

  if (to.meta.public) {
    if ((to.name === "login" || to.name === "register") && authStore.isLoggedIn) {
      return { name: "dashboard" };
    }
    return true;
  }

  if (!authStore.isLoggedIn) {
    return { name: "login", query: { redirect: to.fullPath } };
  }

  if (to.meta.adminOnly && !authStore.isAdmin) {
    return { name: "dashboard" };
  }

  return true;
});

router.afterEach((to) => {
  const title = typeof to.meta.title === "string" ? to.meta.title : "Code Reviewer";
  document.title = `${title} - Code Reviewer`;
});

export default router;
