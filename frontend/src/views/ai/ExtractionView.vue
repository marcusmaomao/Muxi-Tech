<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ApiError, http } from "@/api/http";

interface ProjectRow { id: string; name: string }
interface FileRow { id: string; originalName: string }
interface ModelRow { id: string; displayName: string }
interface ItemRow { id: string; kind: string; state: string; evidenceQuote: string; reason?: string; payload: string }

const projects = ref<ProjectRow[]>([]);
const files = ref<FileRow[]>([]);
const models = ref<ModelRow[]>([]);
const items = ref<ItemRow[]>([]);
const projectId = ref("");
const fileId = ref("");
const modelId = ref("");
const jobId = ref("");
const message = ref("");

function label(row: ItemRow) {
  try {
    const payload = JSON.parse(row.payload) as { type?: string; name?: string; from?: string; to?: string };
    return `${row.kind} · ${payload.name ?? `${payload.from ?? ""} → ${payload.to ?? ""}`} · ${payload.type ?? ""}`;
  } catch {
    return row.kind;
  }
}

async function load() {
  projects.value = (await http.get<{ data: ProjectRow[] }>("/projects")).data.data ?? [];
  files.value = (await http.get<{ data: FileRow[] }>("/files")).data.data ?? [];
  models.value = (await http.get<{ data: ModelRow[] }>("/ai/models")).data.data ?? [];
}

async function start() {
  message.value = "";
  const branches = await http.get<{ data: { id: string; name: string }[] }>(`/projects/${projectId.value}/branches`);
  const main = (branches.data.data ?? []).find((branch) => branch.name === "main");
  if (!main) {
    message.value = "没有 main 分支";
    return;
  }
  try {
    const response = await http.post<{ data: { id: string } }>("/ai/extraction-jobs", {
      projectId: projectId.value,
      branchId: main.id,
      fileId: fileId.value,
      modelId: modelId.value,
    });
    jobId.value = response.data.data?.id ?? "";
    await refresh();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "抽取失败";
  }
}

async function refresh() {
  items.value = (await http.get<{ data: ItemRow[] }>(`/ai/extraction-jobs/${jobId.value}/items`)).data.data ?? [];
}

async function review(row: ItemRow, accept: boolean) {
  await http.post(`/ai/extraction-items/${row.id}/${accept ? "accept" : "reject"}`);
  await refresh();
}

async function acceptAll() {
  const pending = items.value.filter((row) => row.state === "REVIEW");
  for (const row of pending) {
    await http.post(`/ai/extraction-items/${row.id}/accept`);
  }
  await refresh();
}

async function publish() {
  try {
    await http.post(`/ai/extraction-jobs/${jobId.value}/publish`);
    message.value = "已发布";
    await refresh();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "发布失败";
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <main class="page" data-testid="extraction-page">
    <h1>抽取审核</h1>
    <p class="lead">抽取使用当前分支上的本体约束模型输出。不合法的结果进隔离区，审核通过后才发布到图库。每条结果都保留原文出处。</p>
    <form class="form-grid" @submit.prevent="start">
      <select v-model="projectId" data-testid="extract-project">
        <option value="">项目</option>
        <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
      </select>
      <select v-model="fileId" data-testid="extract-file">
        <option value="">文档</option>
        <option v-for="file in files" :key="file.id" :value="file.id">{{ file.originalName }}</option>
      </select>
      <select v-model="modelId" data-testid="extract-model">
        <option value="">模型</option>
        <option v-for="model in models" :key="model.id" :value="model.id">{{ model.displayName }}</option>
      </select>
      <el-button native-type="submit" type="primary" data-testid="start-extraction">抽取</el-button>
    </form>
    <p class="hint" data-testid="extract-message">{{ message }}</p>
    <button type="button" data-testid="accept-all" :disabled="!jobId" @click="acceptAll">全部通过</button>
    <button type="button" data-testid="publish-extraction" :disabled="!jobId" @click="publish">发布已通过</button>
    <ul data-testid="extraction-items">
      <li v-for="row in items" :key="row.id">
        <strong>{{ label(row) }}</strong>
        <span>{{ row.state }}</span>
        <span v-if="row.reason">{{ row.reason }}</span>
        <q>{{ row.evidenceQuote }}</q>
        <button v-if="row.state === 'REVIEW'" type="button" @click="review(row, true)">通过</button>
        <button v-if="row.state === 'REVIEW'" type="button" @click="review(row, false)">驳回</button>
      </li>
    </ul>
  </main>
</template>

<style scoped>
select, button { height: 32px; }
ul { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px 16px; }
q { display: block; color: var(--ink-soft); }
li { margin-bottom: 10px; }
</style>
