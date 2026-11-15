<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { ApiError, http } from "@/api/http";
import DiffViewer, { type DiffResult } from "@/components/DiffViewer.vue";

interface ProjectRow { id: string; name: string }
interface BranchRow { id: string; name: string; headRev: string; status: string }
interface CommitRow { rev: string; message: string; source?: string; changeCount: number }
interface Conflict { uid: string; apiName?: string; reason: string; paths?: string[] }
interface MergeRow {
  id: string;
  sourceBranchId: string;
  targetBranchId: string;
  title: string;
  status: string;
  conflicts?: string | null;
}

const projects = ref<ProjectRow[]>([]);
const projectId = ref("");
const branches = ref<BranchRow[]>([]);
const commits = ref<CommitRow[]>([]);
const requests = ref<MergeRow[]>([]);
const branchName = ref("");
const parentId = ref("");
const historyId = ref("");
const diffFrom = ref("");
const diffTo = ref("");
const diff = ref<DiffResult | null>(null);
const mrSource = ref("");
const mrTarget = ref("");
const mrTitle = ref("");
const message = ref("");
const choices = ref<Record<string, Record<string, string>>>({});

const activeBranches = computed(() => branches.value.filter((branch) => branch.status === "ACTIVE"));

function branchNameOf(id: string) {
  return branches.value.find((branch) => branch.id === id)?.name ?? id;
}

function conflictsOf(row: MergeRow): Conflict[] {
  if (!row.conflicts) {
    return [];
  }
  try {
    const parsed = JSON.parse(row.conflicts) as Conflict[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function statusText(status: string) {
  if (status === "OPEN") {
    return "待合并";
  }
  if (status === "CONFLICT") {
    return "有冲突";
  }
  if (status === "MERGED") {
    return "已合并";
  }
  return status;
}

async function loadProjects() {
  const response = await http.get<{ data: ProjectRow[] }>("/projects");
  projects.value = response.data.data ?? [];
}

async function loadBranches() {
  if (!projectId.value) {
    return;
  }
  const response = await http.get<{ data: BranchRow[] }>(`/projects/${projectId.value}/branches`);
  branches.value = response.data.data ?? [];
  const main = activeBranches.value.find((branch) => branch.name === "main");
  parentId.value = main?.id ?? "";
  if (!historyId.value || !branches.value.some((branch) => branch.id === historyId.value)) {
    historyId.value = main?.id ?? "";
  }
  if (!diffFrom.value) {
    diffFrom.value = main?.name ?? "";
  }
  if (!diffTo.value) {
    diffTo.value = main?.name ?? "";
  }
  if (!mrTarget.value) {
    mrTarget.value = main?.id ?? "";
  }
  await loadCommits();
  await loadRequests();
}

async function loadCommits() {
  commits.value = [];
  if (!historyId.value) {
    return;
  }
  const response = await http.get<{ data: CommitRow[] }>(`/branches/${historyId.value}/commits`);
  commits.value = response.data.data ?? [];
}

async function loadRequests() {
  if (!projectId.value) {
    return;
  }
  const response = await http.get<{ data: MergeRow[] }>(`/projects/${projectId.value}/merge-requests`);
  requests.value = response.data.data ?? [];
}

async function createBranch() {
  message.value = "";
  try {
    await http.post(`/projects/${projectId.value}/branches`, { name: branchName.value.trim(), parentBranchId: parentId.value });
    branchName.value = "";
    message.value = "分支已拉出";
    await loadBranches();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "创建失败";
  }
}

async function compare() {
  message.value = "";
  try {
    const response = await http.get<{ data: DiffResult }>(`/projects/${projectId.value}/diff`, {
      params: { from: `branch:${diffFrom.value}`, to: `branch:${diffTo.value}` },
    });
    diff.value = response.data.data;
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "比较失败";
  }
}

async function openRequest() {
  message.value = "";
  try {
    await http.post(`/projects/${projectId.value}/merge-requests`, {
      sourceBranchId: mrSource.value,
      targetBranchId: mrTarget.value,
      title: mrTitle.value.trim(),
    });
    mrTitle.value = "";
    message.value = "合并请求已发起";
    await loadRequests();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "发起失败";
  }
}

async function choose(row: MergeRow, conflict: Conflict, choice: "SOURCE" | "TARGET") {
  const current = { ...(choices.value[row.id] ?? {}), [conflict.uid]: choice };
  choices.value = { ...choices.value, [row.id]: current };
  try {
    await http.post(`/merge-requests/${row.id}/resolve`, {
      resolutions: Object.entries(current).map(([uid, picked]) => ({ uid, choice: picked })),
    });
    message.value = "冲突已处理";
    await loadRequests();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "解决失败";
  }
}

async function merge(row: MergeRow) {
  try {
    await http.post(`/merge-requests/${row.id}/merge`, {});
    message.value = "已合并";
    await loadRequests();
    await loadBranches();
    await loadCommits();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "合并失败";
  }
}

async function revert(row: CommitRow) {
  try {
    await http.post(`/branches/${historyId.value}/revert/${row.rev}`, {});
    message.value = "已回退";
    await loadCommits();
    await loadBranches();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "回退失败";
  }
}

watch(projectId, () => {
  diff.value = null;
  void loadBranches();
});
watch(historyId, () => {
  void loadCommits();
});

onMounted(() => {
  void loadProjects();
});
</script>

<template>
  <main class="page" data-testid="version-page">
    <h1>版本</h1>
    <p class="lead">查看提交，从父分支拉出新分支，比较字段差异，并处理合并冲突。IRI 仍使用项目自己的占位命名空间。</p>
    <select v-model="projectId" data-testid="version-project">
      <option value="">项目</option>
      <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
    </select>
    <p class="hint" data-testid="version-message">{{ message }}</p>

    <h2>分支</h2>
    <ul data-testid="branch-list">
      <li v-for="branch in activeBranches" :key="branch.id">
        {{ branch.name }} · 修订 {{ branch.headRev }}
        <router-link :to="`/model/${projectId}?branch=${branch.id}`" :data-testid="`model-branch-${branch.name}`">建模</router-link>
      </li>
    </ul>
    <form class="form-grid" @submit.prevent="createBranch">
      <span data-testid="branch-name"><el-input v-model="branchName" placeholder="分支名" /></span>
      <select v-model="parentId" data-testid="branch-parent">
        <option value="">父分支</option>
        <option v-for="branch in activeBranches" :key="branch.id" :value="branch.id">{{ branch.name }}</option>
      </select>
      <el-button native-type="submit" type="primary" data-testid="create-branch" :disabled="!projectId">拉出分支</el-button>
    </form>

    <h2>提交</h2>
    <select v-model="historyId" data-testid="history-branch">
      <option value="">分支</option>
      <option v-for="branch in activeBranches" :key="branch.id" :value="branch.id">{{ branch.name }}</option>
    </select>
    <ul data-testid="commit-list">
      <li v-for="(row, index) in commits" :key="row.rev">
        {{ row.rev }} · {{ row.message }} · {{ row.source || "MANUAL" }} · {{ row.changeCount }}
        <button v-if="index > 0" type="button" :data-testid="`revert-${row.rev}`" @click="revert(row)">回退到此</button>
      </li>
    </ul>

    <h2>差异</h2>
    <form class="form-grid" @submit.prevent="compare">
      <select v-model="diffFrom" data-testid="diff-from">
        <option value="">从</option>
        <option v-for="branch in activeBranches" :key="`from-${branch.id}`" :value="branch.name">{{ branch.name }}</option>
      </select>
      <select v-model="diffTo" data-testid="diff-to">
        <option value="">到</option>
        <option v-for="branch in activeBranches" :key="`to-${branch.id}`" :value="branch.name">{{ branch.name }}</option>
      </select>
      <el-button native-type="submit" data-testid="run-diff" :disabled="!projectId">比较</el-button>
    </form>
    <DiffViewer :diff="diff" />

    <h2>合并请求</h2>
    <form class="form-grid" @submit.prevent="openRequest">
      <select v-model="mrSource" data-testid="mr-source">
        <option value="">来源分支</option>
        <option v-for="branch in activeBranches" :key="`src-${branch.id}`" :value="branch.id">{{ branch.name }}</option>
      </select>
      <select v-model="mrTarget" data-testid="mr-target">
        <option value="">目标分支</option>
        <option v-for="branch in activeBranches" :key="`dst-${branch.id}`" :value="branch.id">{{ branch.name }}</option>
      </select>
      <span data-testid="mr-title"><el-input v-model="mrTitle" placeholder="标题" /></span>
      <el-button native-type="submit" type="primary" data-testid="open-mr" :disabled="!projectId">发起合并</el-button>
    </form>
    <ul data-testid="mr-list">
      <li v-for="row in requests" :key="row.id">
        <strong>{{ row.title }}</strong>
        {{ branchNameOf(row.sourceBranchId) }} → {{ branchNameOf(row.targetBranchId) }}
        <span :data-testid="`mr-status-${row.id}`">{{ statusText(row.status) }}</span>
        <div v-if="row.status === 'CONFLICT' && conflictsOf(row).length" data-testid="conflict-list">
          <div v-for="conflict in conflictsOf(row)" :key="conflict.uid">
            {{ conflict.apiName || conflict.uid }} · {{ conflict.reason }} · {{ (conflict.paths ?? []).join(" ") }}
            <button type="button" data-testid="keep-source" @click="choose(row, conflict, 'SOURCE')">保留来源</button>
            <button type="button" data-testid="keep-target" @click="choose(row, conflict, 'TARGET')">保留目标</button>
          </div>
        </div>
        <button v-if="row.status === 'OPEN'" type="button" data-testid="merge-mr" @click="merge(row)">合并</button>
      </li>
    </ul>
  </main>
</template>

<style scoped>
select, button { height: 32px; }
ul { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px 20px; }
li { margin-bottom: 8px; }
a { margin-left: 8px; }
</style>
