<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http, ApiError } from "@/api/http";
import { isAbsoluteNamespace } from "@/utils/namespace";
import BasicNav from "./BasicNav.vue";

interface ProjectRow {
  id: string;
  code: string;
  name: string;
  domain?: string;
  namespace: string;
}

const rows = ref<ProjectRow[]>([]);
const code = ref("");
const name = ref("");
const namespace = ref("https://muxi.example/ont/");
const domain = ref("");
const scopeInclude = ref("");
const scopeExclude = ref("");
const message = ref("");

async function load() {
  const response = await http.get<{ data: ProjectRow[] }>("/projects");
  rows.value = response.data.data ?? [];
}

async function createProject() {
  message.value = "";
  if (!isAbsoluteNamespace(namespace.value)) {
    message.value = "命名空间必须是以 # 或 / 结尾的绝对 URI";
    return;
  }
  try {
    await http.post("/projects", {
      code: code.value,
      name: name.value,
      namespace: namespace.value,
      domain: domain.value,
      scopeInclude: scopeInclude.value,
      scopeExclude: scopeExclude.value,
    });
    code.value = "";
    name.value = "";
    domain.value = "";
    message.value = "已创建";
    await load();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "创建失败";
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <main class="page" data-testid="projects-page">
    <BasicNav />
    <h1>项目</h1>
    <p class="lead">一个项目是一块本体范围。命名空间要用绝对 URI，并以 # 或 / 结尾。</p>
    <form class="form-grid" @submit.prevent="createProject">
      <span data-testid="project-code"><el-input v-model="code" placeholder="编码" /></span>
      <span data-testid="project-name"><el-input v-model="name" placeholder="名称" /></span>
      <span data-testid="project-domain"><el-input v-model="domain" placeholder="领域" /></span>
      <span data-testid="project-namespace" class="wide"><el-input v-model="namespace" placeholder="命名空间" /></span>
      <span data-testid="project-include"><el-input v-model="scopeInclude" placeholder="范围包含" /></span>
      <span data-testid="project-exclude"><el-input v-model="scopeExclude" placeholder="范围排除" /></span>
      <el-button native-type="submit" type="primary" data-testid="create-project">新建</el-button>
      <span class="hint">{{ message }}</span>
    </form>
    <div data-testid="project-table">
      <el-table :data="rows">
        <el-table-column prop="code" label="编码" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="domain" label="领域" />
        <el-table-column prop="namespace" label="命名空间" />
      </el-table>
    </div>
  </main>
</template>
