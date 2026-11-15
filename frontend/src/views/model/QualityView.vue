<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ApiError, http } from "@/api/http";

interface ProjectRow { id: string; code: string; name: string }
interface IssueRow { id: string; code: string; severity: string; message: string; state: string; ignoreReason?: string }
interface QualityReport { runId: string; score: number; blocked: boolean; dimensions: Record<string, number>; issues: IssueRow[] }
interface TrendRow { runId: string; score: number; finishedAt?: string }
interface ElementRow { uid: string; elementType: string; apiName?: string; payload: string }

const route = useRoute();
const projectId = computed(() => String(route.params.projectId ?? ""));
const projects = ref<ProjectRow[]>([]);
const branchId = ref("");
const report = ref<QualityReport | null>(null);
const trend = ref<TrendRow[]>([]);
const axioms = ref<ElementRow[]>([]);
const version = ref("");
const ignoreReason = ref("");
const message = ref("");

const labelOf = (row: ElementRow) => {
  try {
    return (JSON.parse(row.payload) as { displayName?: string }).displayName || row.apiName || row.uid;
  } catch {
    return row.apiName || row.uid;
  }
};

async function load() {
  if (!projectId.value) {
    const response = await http.get<{ data: ProjectRow[] }>("/projects");
    projects.value = response.data.data ?? [];
    return;
  }
  const branches = await http.get<{ data: { id: string; name: string }[] }>(`/projects/${projectId.value}/branches`);
  const main = (branches.data.data ?? []).find((branch) => branch.name === "main");
  if (!main) {
    message.value = "没有 main 分支";
    return;
  }
  branchId.value = main.id;
  const elements = await http.get<{ data: ElementRow[] }>(`/branches/${branchId.value}/elements`);
  axioms.value = (elements.data.data ?? []).filter((row) => row.elementType === "AXIOM");
  const history = await http.get<{ data: TrendRow[] }>(`/projects/${projectId.value}/quality/trend`);
  trend.value = history.data.data ?? [];
}

async function runCheck() {
  message.value = "";
  try {
    const response = await http.post<{ data: QualityReport }>(`/branches/${branchId.value}/quality-runs`);
    report.value = response.data.data ?? null;
    await load();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "检查失败";
  }
}

async function ignore(issue: IssueRow) {
  if (!ignoreReason.value.trim()) {
    message.value = "忽略问题必须填写理由";
    return;
  }
  try {
    const response = await http.put<{ data: QualityReport }>(`/sem-issues/${issue.id}`, { state: "IGNORED", ignoreReason: ignoreReason.value.trim() });
    report.value = response.data.data ?? null;
    message.value = "已忽略";
    await load();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "忽略失败";
  }
}

async function publish() {
  message.value = "";
  try {
    const response = await http.post<{ data: { id: string; version: string } }>(`/branches/${branchId.value}/releases`, { version: version.value.trim() });
    const release = response.data.data;
    const compiled = await http.get<{ data: { types?: Array<{ apiName?: string }> } }>(`/releases/${release?.id}/compiled-model`);
    const names = (compiled.data.data?.types ?? []).map((type) => type.apiName).filter(Boolean).join("、");
    message.value = `已发布 ${release?.version}：${names}`;
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "发布失败";
  }
}

async function removeAxiom(row: ElementRow) {
  try {
    await http.delete(`/branches/${branchId.value}/elements/${row.uid}`);
    message.value = "公理已删除";
    await load();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "删除失败";
  }
}

watch(projectId, () => {
  report.value = null;
  void load();
}, { immediate: true });
</script>

<template>
  <main class="page" data-testid="quality-page">
    <h1>质量中心</h1>
    <p class="lead">分数和问题都来自一次检查运行。有不可满足的类时不能发布。命名空间仍是项目自己的占位。</p>
    <ul v-if="!projectId">
      <li v-for="project in projects" :key="project.id">
        <router-link :to="`/model/${project.id}/quality`">{{ project.name }}</router-link>
      </li>
    </ul>
    <template v-else>
      <div class="kind-row">
        <button type="button" data-testid="run-quality" @click="runCheck">运行检查</button>
      </div>
      <p v-if="report" data-testid="quality-score">{{ report.score }}</p>
      <p v-if="report" data-testid="quality-run">运行 {{ report.runId }}</p>
      <ul v-if="report" data-testid="quality-dimensions">
        <li v-for="(points, name) in report.dimensions" :key="name">{{ name }} {{ points }}</li>
      </ul>
      <ul data-testid="quality-issues">
        <li v-for="issue in report?.issues ?? []" :key="issue.id">
          {{ issue.state }} {{ issue.severity }} {{ issue.message }}
          <button v-if="issue.state === 'OPEN'" type="button" @click="ignore(issue)">忽略</button>
        </li>
      </ul>
      <span data-testid="ignore-reason"><el-input v-model="ignoreReason" placeholder="忽略理由" /></span>
      <ul data-testid="quality-trend">
        <li v-for="point in trend" :key="point.runId">{{ point.runId }} {{ point.score }}</li>
      </ul>
      <form class="form-grid" @submit.prevent="publish">
        <span data-testid="release-version"><el-input v-model="version" placeholder="版本号" /></span>
        <button type="submit" data-testid="publish-release">发布</button>
      </form>
      <p data-testid="publish-message">{{ message }}</p>
      <ul data-testid="quality-axioms">
        <li v-for="row in axioms" :key="row.uid">
          {{ labelOf(row) }}
          <button type="button" :data-testid="`delete-axiom-${row.apiName}`" @click="removeAxiom(row)">删除公理</button>
        </li>
      </ul>
    </template>
  </main>
</template>

<style scoped>
.form-grid { display: flex; gap: 8px; align-items: center; margin: 12px 0; }
</style>
