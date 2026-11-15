<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ApiError, http } from "@/api/http";
import { readSession } from "@/api/session";

interface ProjectRow { id: string; name: string }
interface FileRow { id: string; originalName: string }
interface ModelRow { id: string; displayName: string; modelName: string }
interface CandidateRow {
  id: string;
  elementType: string;
  state: string;
  evidenceQuote: string;
  payload: string;
  message?: string;
}

const projects = ref<ProjectRow[]>([]);
const files = ref<FileRow[]>([]);
const models = ref<ModelRow[]>([]);
const projectId = ref("");
const fileId = ref("");
const modelId = ref("");
const branchId = ref("");
const jobId = ref("");
const status = ref("");
const log = ref<string[]>([]);
const candidates = ref<CandidateRow[]>([]);
const edits = ref<Record<string, string>>({});
const message = ref("");

function label(row: CandidateRow) {
  try {
    const payload = JSON.parse(row.payload) as { displayName?: string; apiName?: string };
    return `${row.elementType} · ${payload.displayName ?? payload.apiName ?? row.id}`;
  } catch {
    return row.elementType;
  }
}

async function loadBasics() {
  const projectResponse = await http.get<{ data: ProjectRow[] }>("/projects");
  projects.value = projectResponse.data.data ?? [];
  const fileResponse = await http.get<{ data: FileRow[] }>("/files");
  files.value = fileResponse.data.data ?? [];
  const modelResponse = await http.get<{ data: ModelRow[] }>("/ai/models");
  models.value = modelResponse.data.data ?? [];
}

async function start() {
  message.value = "";
  const branches = await http.get<{ data: { id: string; name: string }[] }>(`/projects/${projectId.value}/branches`);
  const main = (branches.data.data ?? []).find((branch) => branch.name === "main");
  if (!main) {
    message.value = "没有 main 分支";
    return;
  }
  branchId.value = main.id;
  const response = await http.post<{ data: { id: string } }>("/ai/modeling-jobs", {
    projectId: projectId.value,
    branchId: branchId.value,
    fileId: fileId.value,
    modelId: modelId.value,
    background: true,
  });
  jobId.value = response.data.data?.id ?? "";
  void listen();
}

async function listen() {
  const session = readSession();
  const response = await fetch(`/api/v1/ai/jobs/${jobId.value}/events`, {
    headers: { Authorization: `Bearer ${session?.accessToken ?? ""}` },
  });
  const reader = response.body?.getReader();
  if (!reader) {
    return;
  }
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const chunk = await reader.read();
    if (chunk.done) {
      break;
    }
    buffer += decoder.decode(chunk.value, { stream: true });
    log.value = buffer.split("\n").filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trim());
    await refresh();
  }
  await refresh();
}

async function refresh() {
  if (!jobId.value) {
    return;
  }
  const job = await http.get<{ data: { status: string } }>(`/ai/jobs/${jobId.value}`);
  status.value = job.data.data?.status ?? "";
  const response = await http.get<{ data: CandidateRow[] }>(`/ai/jobs/${jobId.value}/candidates`);
  candidates.value = response.data.data ?? [];
  for (const row of candidates.value) {
    if (edits.value[row.id] == null) {
      edits.value[row.id] = label(row);
    }
  }
}

async function saveEdit(row: CandidateRow) {
  const displayName = edits.value[row.id]?.replace(/^.*·\s*/, "").trim();
  await http.put(`/ai/candidates/${row.id}`, { displayName });
  await refresh();
}

async function adopt(row: CandidateRow) {
  try {
    await http.post(`/ai/candidates/${row.id}/adopt`);
    message.value = "已采纳";
    await refresh();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "采纳失败";
  }
}

async function reject(row: CandidateRow) {
  await http.post(`/ai/candidates/${row.id}/reject`);
  await refresh();
}

onMounted(() => {
  void loadBasics();
});
</script>

<template>
  <main class="page" data-testid="ai-modeling-page">
    <h1>AI 建模</h1>
    <p class="lead">文档先按原文切成证据。四轮只根据证据生成候选，采纳后才写入分支。</p>
    <form class="form-grid" @submit.prevent="start">
      <select v-model="projectId" data-testid="ai-project">
        <option value="">项目</option>
        <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
      </select>
      <select v-model="fileId" data-testid="ai-file">
        <option value="">文档</option>
        <option v-for="file in files" :key="file.id" :value="file.id">{{ file.originalName }}</option>
      </select>
      <select v-model="modelId" data-testid="ai-model">
        <option value="">模型</option>
        <option v-for="model in models" :key="model.id" :value="model.id">{{ model.displayName }}</option>
      </select>
      <el-button native-type="submit" type="primary" data-testid="start-job">开始</el-button>
    </form>
    <p class="hint" data-testid="ai-status">{{ status }} {{ message }}</p>
    <pre class="log" data-testid="ai-log">{{ log.join("\n") }}</pre>
    <ul data-testid="candidate-list">
      <li v-for="row in candidates" :key="row.id">
        <strong>{{ label(row) }}</strong>
        <span>{{ row.state }}</span>
        <q>{{ row.evidenceQuote }}</q>
        <input v-if="row.state === 'PENDING'" v-model="edits[row.id]" :data-testid="`edit-${row.id}`" />
        <button v-if="row.state === 'PENDING'" type="button" @click="saveEdit(row)">保存</button>
        <button v-if="row.state === 'PENDING'" type="button" @click="adopt(row)">采纳</button>
        <button v-if="row.state === 'PENDING'" type="button" @click="reject(row)">驳回</button>
      </li>
    </ul>
  </main>
</template>

<style scoped>
select, button { height: 32px; }
.log, ul { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px 16px; }
q { display: block; color: var(--ink-soft); }
li { margin-bottom: 10px; }
</style>
