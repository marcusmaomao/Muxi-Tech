<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ApiError, http } from "@/api/http";
import { isApiName } from "@/utils/apiName";

interface ElementRow {
  uid: string;
  elementType: string;
  apiName?: string;
  ownerUid?: string;
  payload: string;
}
interface ProjectRow { id: string; code: string; name: string; namespace: string }
interface CaseRow { apiName: string; passed: boolean; message: string }
interface RunReport {
  gateOpen: boolean;
  coverage: { cqPassed: number; cqTotal: number; elementPassed: number; elementTotal: number };
  cases: CaseRow[];
}

const route = useRoute();
const projectId = computed(() => String(route.params.projectId ?? ""));
const projects = ref<ProjectRow[]>([]);
const branchId = ref("");
const namespace = ref("");
const rows = ref<ElementRow[]>([]);
const message = ref("");
const report = ref("");
const cqName = ref("");
const cqApi = ref("");
const typeName = ref("");
const typeApi = ref("");
const linkName = ref("");
const linkApi = ref("");
const linkSource = ref("");
const linkTarget = ref("");
const fixtureName = ref("");
const fixtureApi = ref("");
const fixtureTurtle = ref("");
const testName = ref("");
const testApi = ref("");
const testQuery = ref("");
const testFixture = ref("");
const acceptCq = ref("");

const ofType = (type: string) => rows.value.filter((row) => row.elementType === type);
const labelOf = (row: ElementRow) => {
  try {
    const payload = JSON.parse(row.payload) as { displayName?: string };
    return payload.displayName || row.apiName || row.uid;
  } catch {
    return row.apiName || row.uid;
  }
};

async function loadProjects() {
  const response = await http.get<{ data: ProjectRow[] }>("/projects");
  projects.value = response.data.data ?? [];
}

async function load() {
  if (!projectId.value) {
    await loadProjects();
    return;
  }
  const project = await http.get<{ data: ProjectRow }>(`/projects/${projectId.value}`);
  namespace.value = project.data.data?.namespace ?? "";
  const branches = await http.get<{ data: { id: string; name: string }[] }>(`/projects/${projectId.value}/branches`);
  const main = (branches.data.data ?? []).find((branch) => branch.name === "main");
  if (!main) {
    message.value = "没有 main 分支";
    return;
  }
  branchId.value = main.id;
  const elements = await http.get<{ data: ElementRow[] }>(`/branches/${branchId.value}/elements`);
  rows.value = elements.data.data ?? [];
}

async function createCq() {
  if (!isApiName(cqApi.value) || !cqName.value.trim()) {
    message.value = "请填写能力问题的名称和 API 名";
    return;
  }
  await submit("/cqs", { displayName: cqName.value.trim(), apiName: cqApi.value.trim(), priority: "P0" }, "能力问题已保存");
  cqName.value = "";
  cqApi.value = "";
}

async function createType() {
  const cq = ofType("CQ")[0];
  if (!cq || !isApiName(typeApi.value) || !typeName.value.trim()) {
    message.value = "请先有能力问题，再填写对象名称和 API 名";
    return;
  }
  await submit("/object-types", { displayName: typeName.value.trim(), apiName: typeApi.value.trim(), cqUids: [cq.uid] }, "对象已保存");
  typeName.value = "";
  typeApi.value = "";
}

async function createLink() {
  const cq = ofType("CQ")[0];
  if (!cq || !linkSource.value || !linkTarget.value || !isApiName(linkApi.value)) {
    message.value = "请填写关系的两端和 API 名";
    return;
  }
  await submit("/link-types", {
    displayName: linkName.value.trim(),
    apiName: linkApi.value.trim(),
    sourceUid: linkSource.value,
    targetUid: linkTarget.value,
    cardinality: "ONE_TO_MANY",
    cqUids: [cq.uid],
  }, "关系已保存");
  linkName.value = "";
  linkApi.value = "";
}

async function createFixture() {
  if (!isApiName(fixtureApi.value) || !fixtureTurtle.value.trim()) {
    message.value = "请填写夹具 API 名和 Turtle";
    return;
  }
  await submit("/fixtures", { displayName: fixtureName.value.trim() || fixtureApi.value.trim(), apiName: fixtureApi.value.trim(), turtle: fixtureTurtle.value }, "夹具已保存");
}

async function createTest() {
  const cq = ofType("CQ")[0];
  if (!cq || !isApiName(testApi.value) || !testQuery.value.trim() || !testFixture.value) {
    message.value = "请填写用例、查询，并选择夹具";
    return;
  }
  await submit(`/cqs/${cq.uid}/tests`, {
    displayName: testName.value.trim() || testApi.value.trim(),
    apiName: testApi.value.trim(),
    language: "SPARQL",
    layer: "ABOX",
    query: testQuery.value,
    fixtureUid: testFixture.value,
    assert: { kind: "COUNT", op: ">=", value: 1 },
  }, "用例已保存");
}

async function runAll() {
  message.value = "";
  try {
    const response = await http.post<{ data: RunReport }>(`/branches/${branchId.value}/cq-runs`, {});
    const data = response.data.data;
    const lines = (data?.cases ?? []).map((item) => `${item.apiName} ${item.passed ? "通过" : "失败"} ${item.message}`);
    const coverage = data?.coverage;
    lines.push(`CQ ${coverage?.cqPassed ?? 0}/${coverage?.cqTotal ?? 0} · 元素 ${coverage?.elementPassed ?? 0}/${coverage?.elementTotal ?? 0}`);
    lines.push(data?.gateOpen ? "门禁通过" : "门禁未通过");
    report.value = lines.join("\n");
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "运行失败";
  }
}

async function markCovered() {
  const row = rows.value.find((item) => item.uid === acceptCq.value);
  if (!row) {
    message.value = "请选择能力问题";
    return;
  }
  const payload = JSON.parse(row.payload) as Record<string, unknown>;
  payload.acceptStatus = "已覆盖";
  await submit(`/cqs/${row.uid}`, payload, "已记下手工状态", "put");
}

async function removeLink(row: ElementRow) {
  try {
    await http.delete(`/branches/${branchId.value}/elements/${row.uid}`);
    message.value = "关系已删除";
    await load();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "删除失败";
  }
}

async function submit(path: string, body: unknown, ok: string, method: "post" | "put" = "post") {
  message.value = "";
  try {
    if (method === "put") {
      await http.put(`/branches/${branchId.value}${path}`, body);
    } else {
      await http.post(`/branches/${branchId.value}${path}`, body);
    }
    message.value = ok;
    await load();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "保存失败";
  }
}

watch(projectId, () => {
  void load();
}, { immediate: true });
</script>

<template>
  <main class="page" data-testid="cq-page">
    <h1>能力问题测试</h1>
    <p class="lead">用例、夹具和覆盖率都来自实际运行。手工把状态改成已覆盖，不会抬高覆盖率。命名空间仍是这个项目自己的占位。</p>
    <p v-if="namespace" data-testid="cq-namespace">{{ namespace }}</p>
    <ul v-if="!projectId" data-testid="cq-projects">
      <li v-for="project in projects" :key="project.id">
        <router-link :to="`/model/${project.id}/cq`">{{ project.name }}</router-link>
      </li>
    </ul>
    <template v-else>
      <p class="hint" data-testid="cq-message">{{ message }}</p>
      <form class="form-grid" @submit.prevent="createCq">
        <span data-testid="cq-name"><el-input v-model="cqName" placeholder="能力问题" /></span>
        <span data-testid="cq-api"><el-input v-model="cqApi" placeholder="API 名" /></span>
        <button type="submit" data-testid="create-cq">新建能力问题</button>
      </form>
      <form class="form-grid" @submit.prevent="createType">
        <span data-testid="type-name"><el-input v-model="typeName" placeholder="对象名称" /></span>
        <span data-testid="type-api"><el-input v-model="typeApi" placeholder="对象 API 名" /></span>
        <button type="submit" data-testid="create-type">新建对象</button>
      </form>
      <form class="form-grid" @submit.prevent="createLink">
        <span data-testid="link-name"><el-input v-model="linkName" placeholder="关系名称" /></span>
        <span data-testid="link-api"><el-input v-model="linkApi" placeholder="关系 API 名" /></span>
        <select v-model="linkSource" data-testid="link-source">
          <option value="">起点</option>
          <option v-for="row in ofType('OBJECT_TYPE')" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
        </select>
        <select v-model="linkTarget" data-testid="link-target">
          <option value="">终点</option>
          <option v-for="row in ofType('OBJECT_TYPE')" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
        </select>
        <button type="submit" data-testid="create-link">新建关系</button>
      </form>
      <form class="form-grid" @submit.prevent="createFixture">
        <span data-testid="fixture-name"><el-input v-model="fixtureName" placeholder="夹具名称" /></span>
        <span data-testid="fixture-api"><el-input v-model="fixtureApi" placeholder="夹具 API 名" /></span>
        <textarea v-model="fixtureTurtle" data-testid="fixture-turtle" rows="4" placeholder="夹具 Turtle"></textarea>
        <button type="submit" data-testid="create-fixture">保存夹具</button>
      </form>
      <form class="form-grid" @submit.prevent="createTest">
        <span data-testid="test-name"><el-input v-model="testName" placeholder="用例名称" /></span>
        <span data-testid="test-api"><el-input v-model="testApi" placeholder="用例 API 名" /></span>
        <select v-model="testFixture" data-testid="test-fixture">
          <option value="">夹具</option>
          <option v-for="row in ofType('FIXTURE')" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
        </select>
        <textarea v-model="testQuery" data-testid="test-query" rows="4" placeholder="SPARQL"></textarea>
        <button type="submit" data-testid="create-test">保存用例</button>
      </form>
      <div class="kind-row">
        <button type="button" data-testid="run-cq" @click="runAll">运行全部</button>
      </div>
      <form class="form-grid" @submit.prevent="markCovered">
        <select v-model="acceptCq" data-testid="accept-cq">
          <option value="">选择能力问题</option>
          <option v-for="row in ofType('CQ')" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
        </select>
        <button type="submit" data-testid="mark-covered">标为已覆盖</button>
      </form>
      <pre v-if="report" data-testid="cq-report">{{ report }}</pre>
      <ul data-testid="cq-links">
        <li v-for="row in ofType('LINK_TYPE')" :key="row.uid">
          {{ labelOf(row) }}
          <button type="button" :data-testid="`delete-link-${row.apiName}`" @click="removeLink(row)">删除关系</button>
        </li>
      </ul>
      <ul data-testid="cq-list">
        <li v-for="row in ofType('CQ')" :key="row.uid">{{ labelOf(row) }}</li>
      </ul>
    </template>
  </main>
</template>

<style scoped>
.form-grid { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; align-items: center; }
textarea { min-width: 320px; }
.hint { min-height: 1.2em; }
</style>
