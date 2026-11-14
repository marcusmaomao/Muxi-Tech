import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import AppLayout from "@/layouts/AppLayout.vue";
import LoginView from "@/views/login/LoginView.vue";
import WorkbenchView from "@/views/workbench/WorkbenchView.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: LoginView, meta: { public: true } },
    {
      path: "/",
      component: AppLayout,
      children: [
        { path: "", redirect: { name: "workbench" } },
        { path: "workbench", name: "workbench", component: WorkbenchView },
      ],
    },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.meta.public) {
    if (to.name === "login" && auth.isAuthenticated) {
      return { name: "workbench" };
    }
    return true;
  }
  if (!auth.isAuthenticated) {
    return { name: "login" };
  }
  return true;
});

export default router;
