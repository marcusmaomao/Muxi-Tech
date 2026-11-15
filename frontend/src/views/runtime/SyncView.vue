<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { ApiError, http } from "@/api/http";

interface ProjectRow { id: string; name: string }
interface SourceRow { id: string; name: string }
interface ElementRow { uid: string; elementType: string; apiName?: string; payload: string }
interface ColumnRow { name: string }
interface ForeignKeyRow { column: string; targetTable: string; targetColumn: string }
interface MappingRow { id: string; versionNo: number }
interface RunRow { id: string; status: string; mode: string; message?: string; linkCount?: number }
interface GraphRow { pk: string; props: Record<string, string> }

const projects = ref<ProjectRow[]>([]);
const sources = ref<SourceRow[]>([]);
const elements = ref<ElementRow[]>([]);
const tables = ref<string[]>([]);
const columns = ref<string[]>([]);
const foreignKeys = ref<ForeignKeyRow[]>([]);
const runs = ref<RunRow[]>([]);
const graphRows = ref<GraphRow[]>([]);
const projectId = ref("");
const datasourceId = ref("");
const branchId = ref("");
const objectUid = ref("");
const table = ref("");
const pkColumn = ref("id");
const incrementalKind = ref("UPDATED_AT");
const incrementalColumn = ref("updated_at");
const propertyUid = ref("");
const fieldColumn = ref("name");
const linkUid = ref("");
const linkColumn = ref("");
const targetUid = ref("");
const mappingId = ref("");
const versionNo = ref(0);
const previewText = ref("");
const message = ref("");

function payloadOf(row: ElementRow) {
  try {
    return JSON.parse(row.payload) as { displayName?: string; sourceUid?: string; targetUid?: string };
  } catch {
    return {};
  }
}
function labelOf(row: ElementRow) {
  return payloadOf(row).displayName || row.apiName || row.uid;
}
const objects = computed(() => elements.value.filter((row) => row.elementType === "OBJECT_TYPE"));
const properties = computed(() => elements.value.filter((row) => row.elementType === "PROPERTY" && payloadOf(row).sourceUid === undefined && row.payload.includes(objectUid.value)));
const linkTypes = computed(() => elements.value.filter((row) => row.elementType === "LINK_TYPE" && payloadOf(row).sourceUid === objectUid.value));
const selectedObject = computed(() => objects.value.find((row) => row.uid === objectUid.value));

async function loadBasics() {
  projects.value = (await http.get<{ data: ProjectRow[] }>("/projects")).data.data ?? [];
  sources.value = (await http.get<{ data: SourceRow[] }>("/datasources")).data.data ?? [];
}

async function loadModel() {
  elements.value = [];
  branchId.value = "";
  if (!projectId.value) {
    return;
  }
  const branches = await http.get<{ data: { id: string; name: string }[] }>(`/projects/${projectId.value}/branches`);
  const main = (branches.data.data ?? []).find((branch) => branch.name === "main");
  if (!main) {
    message.value = "没有 main 分支";
    return;
  }
  branchId.value = main.id;
  elements.value = (await http.get<{ data: ElementRow[] }>(`/branches/${branchId.value}/elements`)).data.data ?? [];
}

async function loadTables() {
  tables.value = [];
  if (!datasourceId.value) {
    return;
  }
  const response = await http.get<{ data: { name: string }[] }>(`/datasources/${datasourceId.value}/tables`);
  tables.value = (response.data.data ?? []).map((row) => row.name);
}

async function loadColumns() {
  columns.value = [];
  foreignKeys.value = [];
  if (!datasourceId.value || !table.value) {
    return;
  }
  const response = await http.get<{ data: ColumnRow[] }>(`/datasources/${datasourceId.value}/tables/${table.value}/columns`);
  columns.value = (response.data.data ?? []).map((row) => row.name);
  const keys = await http.get<{ data: ForeignKeyRow[] }>(`/datasources/${datasourceId.value}/tables/${table.value}/foreign-keys`);
  foreignKeys.value = keys.data.data ?? [];
}

function useForeignKey() {
  const key = foreignKeys.value[0];
  if (!key) {
    message.value = "这张表没有外键";
    return;
  }
  linkColumn.value = key.column;
}

async function save() {
  message.value = "";
  const links = linkUid.value
    ? [{ kind: "FOREIGN_KEY", linkUid: linkUid.value, column: linkColumn.value, targetObjectUid: targetUid.value }]
    : [];
  try {
    const response = await http.put<{ data: MappingRow }>(`/branches/${branchId.value}/bindings`, {
      datasourceId: datasourceId.value,
      definition: {
        objectUid: objectUid.value,
        table: table.value,
        pkColumn: pkColumn.value,
        incremental: { kind: incrementalKind.value, column: incrementalColumn.value },
        fields: [{ propertyUid: propertyUid.value, column: fieldColumn.value }],
        links,
      },
    });
    mappingId.value = response.data.data?.id ?? "";
    versionNo.value = response.data.data?.versionNo ?? 0;
    message.value = `已保存 v${versionNo.value}`;
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "保存失败";
  }
}

async function preview() {
  const response = await http.post<{ data: unknown }>(`/bindings/${mappingId.value}/preview?limit=20`);
  previewText.value = JSON.stringify(response.data.data, null, 2);
}

async function sync(mode: string) {
  message.value = "";
  try {
    const response = await http.post<{ data: RunRow }>(`/bindings/${mappingId.value}/sync`, { mode });
    message.value = response.data.data?.message ?? response.data.data?.status ?? "";
    await loadRuns();
    await loadGraph();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "同步失败";
  }
}

async function loadRuns() {
  if (!branchId.value) {
    return;
  }
  runs.value = (await http.get<{ data: RunRow[] }>("/sync-runs", { params: { branchId: branchId.value } })).data.data ?? [];
}

async function loadGraph() {
  if (!selectedObject.value?.apiName) {
    return;
  }
  graphRows.value = (await http.get<{ data: GraphRow[] }>("/graph/objects", { params: { type: selectedObject.value.apiName } })).data.data ?? [];
}

watch(projectId, () => {
  void loadModel();
});
watch(datasourceId, () => {
  void loadTables();
});
watch(table, () => {
  void loadColumns();
});
watch(objectUid, () => {
  mappingId.value = "";
});

onMounted(() => {
  void loadBasics();
});
</script>

<template>
  <main class="page" data-testid="sync-page">
    <h1>数据同步</h1>
    <p class="lead">把表映射到对象、字段映射到属性、外键映射到关系。每次保存生成一个新版本。预览不入库，同步按全量或水位增量写入图库。</p>
    <form class="form-grid" @submit.prevent="save">
      <select v-model="projectId" data-testid="sync-project">
        <option value="">项目</option>
        <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
      </select>
      <select v-model="datasourceId" data-testid="sync-datasource">
        <option value="">数据源</option>
        <option v-for="source in sources" :key="source.id" :value="source.id">{{ source.name }}</option>
      </select>
      <select v-model="objectUid" data-testid="sync-object">
        <option value="">对象</option>
        <option v-for="row in objects" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
      </select>
      <select v-model="table" data-testid="sync-table">
        <option value="">表</option>
        <option v-for="name in tables" :key="name" :value="name">{{ name }}</option>
      </select>
      <select v-model="pkColumn" data-testid="sync-pk">
        <option value="">主键</option>
        <option v-for="name in columns" :key="name" :value="name">{{ name }}</option>
      </select>
      <select v-model="incrementalKind" data-testid="sync-incremental-kind">
        <option value="UPDATED_AT">更新时间</option>
        <option value="PK">主键水位</option>
      </select>
      <select v-model="incrementalColumn" data-testid="sync-incremental-column">
        <option value="">水位字段</option>
        <option v-for="name in columns" :key="`w-${name}`" :value="name">{{ name }}</option>
      </select>
      <select v-model="propertyUid" data-testid="sync-property">
        <option value="">属性</option>
        <option v-for="row in properties" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
      </select>
      <select v-model="fieldColumn" data-testid="sync-column">
        <option value="">字段</option>
        <option v-for="name in columns" :key="`f-${name}`" :value="name">{{ name }}</option>
      </select>
      <select v-model="linkUid" data-testid="sync-link">
        <option value="">关系（可空）</option>
        <option v-for="row in linkTypes" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
      </select>
      <select v-model="targetUid" data-testid="sync-target">
        <option value="">关系终点</option>
        <option v-for="row in objects" :key="`t-${row.uid}`" :value="row.uid">{{ labelOf(row) }}</option>
      </select>
      <select v-model="linkColumn" data-testid="sync-fk">
        <option value="">外键字段</option>
        <option v-for="name in columns" :key="`k-${name}`" :value="name">{{ name }}</option>
      </select>
      <button type="button" data-testid="use-foreign-key" @click="useForeignKey">使用外键</button>
      <el-button native-type="submit" type="primary" data-testid="save-mapping">保存映射</el-button>
    </form>
    <p class="hint" data-testid="sync-message">{{ message }}</p>
    <div class="actions">
      <button type="button" data-testid="preview-mapping" :disabled="!mappingId" @click="preview">预览</button>
      <button type="button" data-testid="sync-full" :disabled="!mappingId" @click="sync('FULL')">全量同步</button>
      <button type="button" data-testid="sync-incremental" :disabled="!mappingId" @click="sync('INCREMENTAL')">增量同步</button>
    </div>
    <pre class="log" data-testid="preview-result">{{ previewText }}</pre>
    <ul data-testid="sync-runs">
      <li v-for="run in runs" :key="run.id">{{ run.mode }} {{ run.status }} {{ run.message }}</li>
    </ul>
    <ul data-testid="sync-objects">
      <li v-for="row in graphRows" :key="row.pk">{{ row.pk }} {{ Object.values(row.props).join(" ") }}</li>
    </ul>
  </main>
</template>

<style scoped>
select, button { height: 32px; }
.actions { display: flex; gap: 8px; margin: 12px 0; }
.log, ul { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px 16px; min-height: 48px; }
</style>
