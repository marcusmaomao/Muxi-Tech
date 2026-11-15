import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import AppLayout from "@/layouts/AppLayout.vue";
import LoginView from "@/views/login/LoginView.vue";
import WorkbenchView from "@/views/workbench/WorkbenchView.vue";
import UsersView from "@/views/system/UsersView.vue";
import SimpleListView from "@/views/system/SimpleListView.vue";
import LogsView from "@/views/system/LogsView.vue";
import ProjectsView from "@/views/basic/ProjectsView.vue";
import DatasourcesView from "@/views/basic/DatasourcesView.vue";
import FilesView from "@/views/basic/FilesView.vue";
import ModelingView from "@/views/model/ModelingView.vue";
import VersionView from "@/views/model/VersionView.vue";
import CanvasView from "@/views/model/CanvasView.vue";
import CqView from "@/views/model/CqView.vue";
import QualityView from "@/views/model/QualityView.vue";
import ModelsView from "@/views/ai/ModelsView.vue";
import AiModelingView from "@/views/ai/AiModelingView.vue";
import SyncView from "@/views/runtime/SyncView.vue";
import GraphView from "@/views/runtime/GraphView.vue";
import ExtractionView from "@/views/ai/ExtractionView.vue";

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
        { path: "basic/projects", name: "projects", component: ProjectsView },
        { path: "basic/datasources", name: "datasources", component: DatasourcesView },
        { path: "basic/files", name: "files", component: FilesView },
        { path: "model/versions", name: "versions", component: VersionView },
        { path: "model/canvas", name: "canvas-home", component: CanvasView },
        { path: "model/cq", name: "cq-home", component: CqView },
        { path: "model/quality", name: "quality-home", component: QualityView },
        { path: "model/:projectId/canvas", name: "canvas", component: CanvasView },
        { path: "model/:projectId/cq", name: "cq", component: CqView },
        { path: "model/:projectId/quality", name: "quality", component: QualityView },
        { path: "model/:projectId", name: "modeling", component: ModelingView },
        { path: "ai/models", name: "models", component: ModelsView },
        { path: "ai/modeling", name: "ai-modeling", component: AiModelingView },
        { path: "runtime/sync", name: "sync", component: SyncView },
        { path: "runtime/graph", name: "graph", component: GraphView },
        { path: "ai/extraction", name: "extraction", component: ExtractionView },
        { path: "system/users", name: "users", component: UsersView },
        { path: "system/roles", component: SimpleListView, props: { kind: "roles" } },
        { path: "system/menus", component: SimpleListView, props: { kind: "menus" } },
        { path: "system/depts", component: SimpleListView, props: { kind: "depts" } },
        { path: "system/posts", component: SimpleListView, props: { kind: "posts" } },
        { path: "system/dicts", component: SimpleListView, props: { kind: "dicts" } },
        { path: "system/configs", component: SimpleListView, props: { kind: "configs" } },
        { path: "system/notices", component: SimpleListView, props: { kind: "notices" } },
        { path: "system/logs", component: LogsView },
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
