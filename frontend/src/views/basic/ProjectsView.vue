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
const namespace = ref("");
const domain = ref("");
const scopeInclude = ref("");
const scopeExclude = ref("");
const message = ref("");
const users = ref<{ id: string; username: string }[]>([]);
const memberProjectId = ref("");
const memberUserId = ref("");
const memberRole = ref("EDITOR");

async function load() {
  const response = await http.get<{ data: ProjectRow[] }>("/projects");
  rows.value = response.data.data ?? [];
  try {
    const userResponse = await http.get<{ data: { records: { id: string; username: string }[] } }>("/system/users");
    users.value = userResponse.data.data?.records ?? [];
  } catch {
    users.value = [];
  }
}

async function addMember() {
  const current = await http.get<{ data: { userId: string; memberRole: string }[] }>(`/projects/${memberProjectId.value}/members`);
  const members = (current.data.data ?? []).map((member) => ({ userId: member.userId, role: member.memberRole }));
  members.push({ userId: memberUserId.value, role: memberRole.value });
  await http.put(`/projects/${memberProjectId.value}/members`, { members });
  message.value = "成员已加入";
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
    <p class="lead">每个项目单独配置命名空间，IRI 用这个命名空间加上 API 名。域名仍是占位，等所有者确定后再改到正式域名。</p>
    <form class="form-grid" @submit.prevent="createProject">
      <span data-testid="project-code"><el-input v-model="code" placeholder="编码" /></span>
      <span data-testid="project-name"><el-input v-model="name" placeholder="名称" /></span>
      <span data-testid="project-domain"><el-input v-model="domain" placeholder="领域" /></span>
      <span data-testid="project-namespace" class="wide"><el-input v-model="namespace" placeholder="https://example.com/ont/#" /></span>
      <span data-testid="project-include"><el-input v-model="scopeInclude" placeholder="范围包含" /></span>
      <span data-testid="project-exclude"><el-input v-model="scopeExclude" placeholder="范围排除" /></span>
      <el-button native-type="submit" type="primary" data-testid="create-project">新建</el-button>
      <span class="hint">{{ message }}</span>
    </form>
    <form class="form-grid" data-testid="member-form" @submit.prevent="addMember">
      <select v-model="memberProjectId" data-testid="member-project">
        <option value="">项目</option>
        <option v-for="row in rows" :key="row.id" :value="row.id">{{ row.name }}</option>
      </select>
      <select v-model="memberUserId" data-testid="member-user">
        <option value="">用户</option>
        <option v-for="user in users" :key="user.id" :value="user.id">{{ user.username }}</option>
      </select>
      <select v-model="memberRole" data-testid="member-role">
        <option value="EDITOR">EDITOR</option>
        <option value="VIEWER">VIEWER</option>
        <option value="OWNER">OWNER</option>
      </select>
      <el-button native-type="submit" data-testid="add-member">加入项目</el-button>
    </form>
    <div data-testid="project-table">
      <el-table :data="rows">
        <el-table-column prop="code" label="编码" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="domain" label="领域" />
        <el-table-column prop="namespace" label="命名空间" />
        <el-table-column label="">
          <template #default="{ row }">
            <router-link :to="`/model/${row.id}`" :data-testid="`model-${row.code}`">建模</router-link>
            <router-link :to="`/model/${row.id}/canvas`" :data-testid="`canvas-${row.code}`">画布</router-link>
            <router-link :to="`/model/${row.id}/cq`" :data-testid="`cq-${row.code}`">测试</router-link>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </main>
</template>
