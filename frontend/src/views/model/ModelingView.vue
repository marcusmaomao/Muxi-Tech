<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ApiError, http } from "@/api/http";
import { isApiName } from "@/utils/apiName";

interface ElementRow {
  uid: string;
  elementType: string;
  apiName?: string;
  payload: string;
}

const route = useRoute();
const projectId = computed(() => String(route.params.projectId ?? ""));
const branchId = ref("");
const rows = ref<ElementRow[]>([]);
const message = ref("");
const kind = ref("CQ");
const displayName = ref("");
const apiName = ref("");
const cqUid = ref("");
const ownerUid = ref("");
const targetUid = ref("");
const minCount = ref("1");
const maxCount = ref("1");
const defaultValue = ref("");
const preview = ref("");
const downloadUrl = ref("");
const branchName = ref("");
const editUid = ref("");
const editDescription = ref("");

const kinds = [
  ["CQ", "能力问题"],
  ["TERM", "术语"],
  ["DATA_TYPE", "类型"],
  ["ENUM_TYPE", "枚举"],
  ["ENUM_VALUE", "枚举值"],
  ["OBJECT_TYPE", "对象"],
  ["PROPERTY", "属性"],
  ["LINK_TYPE", "关系"],
  ["AXIOM", "公理"],
  ["DATA_RULE", "规则"],
] as const;

const ofType = (type: string) => rows.value.filter((row) => row.elementType === type);
const labelOf = (row: ElementRow) => {
  try {
    const payload = JSON.parse(row.payload) as { displayName?: string };
    return payload.displayName || row.apiName || row.uid;
  } catch {
    return row.apiName || row.uid;
  }
};

async function load() {
  const branches = await http.get<{ data: { id: string; name: string }[] }>(`/projects/${projectId.value}/branches`);
  const all = branches.data.data ?? [];
  const requested = String(route.query.branch ?? "");
  const chosen = all.find((branch) => branch.id === requested) ?? all.find((branch) => branch.name === "main");
  if (!chosen) {
    message.value = "没有 main 分支";
    return;
  }
  branchId.value = chosen.id;
  branchName.value = chosen.name;
  const elements = await http.get<{ data: ElementRow[] }>(`/branches/${branchId.value}/elements`);
  rows.value = elements.data.data ?? [];
}

async function saveDescription() {
  message.value = "";
  const row = rows.value.find((item) => item.uid === editUid.value && item.elementType === "OBJECT_TYPE");
  if (!row || !editDescription.value.trim()) {
    message.value = "请选择对象并填写说明";
    return;
  }
  try {
    const payload = JSON.parse(row.payload) as Record<string, unknown>;
    payload.description = editDescription.value.trim();
    await http.put(`/branches/${branchId.value}/object-types/${row.uid}`, payload);
    editDescription.value = "";
    message.value = "说明已保存";
    await load();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "保存失败";
  }
}

async function createElement() {
  message.value = "";
  if (!isApiName(apiName.value) || !displayName.value.trim()) {
    message.value = "请填写名称，API 名以字母开头，只含字母、数字和下划线";
    return;
  }
  const payload: Record<string, unknown> = {
    displayName: displayName.value.trim(),
    apiName: apiName.value.trim(),
  };
  if (kind.value === "DATA_TYPE") {
    payload.baseType = "DECIMAL";
    payload.unit = "kV";
  }
  if (kind.value === "OBJECT_TYPE" || kind.value === "LINK_TYPE") {
    if (!cqUid.value) {
      message.value = "对象或关系必须关联至少一个能力问题";
      return;
    }
    payload.cqUids = [cqUid.value];
  }
  if (kind.value === "ENUM_VALUE") {
    payload.ownerUid = ownerUid.value;
  }
  if (kind.value === "PROPERTY") {
    payload.ownerUid = ownerUid.value;
    payload.required = true;
    payload.minCount = Number(minCount.value);
    payload.maxCount = Number(maxCount.value);
    payload.defaultValue = defaultValue.value;
    payload.valueType = { kind: "BASIC", basic: "DECIMAL", unit: "kV" };
  }
  if (kind.value === "LINK_TYPE") {
    payload.sourceUid = ownerUid.value;
    payload.targetUid = targetUid.value;
    payload.cardinality = "MANY_TO_ONE";
    payload.invertible = false;
  }
  if (kind.value === "AXIOM") {
    payload.kind = "SUBCLASS";
    payload.subjectUid = ownerUid.value;
    payload.objectUid = targetUid.value;
  }
  if (kind.value === "DATA_RULE") {
    payload.kind = "RANGE";
    payload.targetObjectUid = ownerUid.value;
    payload.targetPropertyUid = targetUid.value;
    payload.min = 0;
    payload.max = 1000;
    payload.severity = "Violation";
    payload.message = displayName.value.trim();
  }
  const path: Record<string, string> = {
    CQ: "cqs",
    TERM: "terms",
    DATA_TYPE: "data-types",
    ENUM_TYPE: "enums",
    ENUM_VALUE: "enum-values",
    OBJECT_TYPE: "object-types",
    PROPERTY: "properties",
    LINK_TYPE: "link-types",
    AXIOM: "axioms",
    DATA_RULE: "data-rules",
  };
  try {
    await http.post(`/branches/${branchId.value}/${path[kind.value]}`, payload);
    displayName.value = "";
    apiName.value = "";
    message.value = "已提交";
    await load();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "提交失败";
  }
}

async function exportOwl(format: string) {
  message.value = "";
  try {
    const response = await http.get<string>(`/branches/${branchId.value}/export`, {
      params: { format },
      responseType: "text",
    });
    preview.value = response.data;
    if (downloadUrl.value) {
      URL.revokeObjectURL(downloadUrl.value);
    }
    downloadUrl.value = URL.createObjectURL(new Blob([response.data], { type: "text/plain;charset=utf-8" }));
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "导出失败";
  }
}

watch(kind, () => {
  message.value = "";
});

watch(() => `${route.params.projectId ?? ""}:${String(route.query.branch ?? "")}`, () => {
  void load();
}, { immediate: true });
</script>

<template>
  <main class="page" data-testid="modeling-page">
    <h1>表单建模</h1>
    <p class="lead">术语、类型、枚举、对象、属性和关系都写成一次提交。对象和关系要挂上能力问题。导出的 IRI 使用命名空间和 API 名。</p>
    <p data-testid="modeling-branch">当前分支 {{ branchName }}</p>
    <div class="kind-row">
      <button type="button" data-testid="export-ttl" @click="exportOwl('ttl')">导出 Turtle</button>
      <button type="button" data-testid="export-rdfxml" @click="exportOwl('rdfxml')">导出 RDF/XML</button>
      <button type="button" data-testid="export-owl" @click="exportOwl('owl')">导出 OWL</button>
    </div>
    <div class="kind-row">
      <button v-for="item in kinds" :key="item[0]" type="button" :data-testid="`kind-${item[0]}`" :class="{ on: kind === item[0] }" @click="kind = item[0]">
        {{ item[1] }}
      </button>
    </div>
    <form class="form-grid" @submit.prevent="createElement">
      <span data-testid="element-name"><el-input v-model="displayName" placeholder="名称" /></span>
      <span data-testid="element-api"><el-input v-model="apiName" placeholder="API 名" /></span>
      <select v-if="kind === 'OBJECT_TYPE' || kind === 'LINK_TYPE'" v-model="cqUid" data-testid="element-cq">
        <option value="">选择能力问题</option>
        <option v-for="row in ofType('CQ')" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
      </select>
      <select v-if="kind === 'PROPERTY' || kind === 'LINK_TYPE' || kind === 'AXIOM' || kind === 'DATA_RULE' || kind === 'ENUM_VALUE'" v-model="ownerUid" data-testid="element-owner">
        <option value="">{{ kind === 'ENUM_VALUE' ? '所属枚举' : kind === 'PROPERTY' ? '所属对象' : '起点 / 对象' }}</option>
        <option v-for="row in ofType(kind === 'ENUM_VALUE' ? 'ENUM_TYPE' : 'OBJECT_TYPE')" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
      </select>
      <select v-if="kind === 'LINK_TYPE' || kind === 'AXIOM'" v-model="targetUid" data-testid="element-target">
        <option value="">终点</option>
        <option v-for="row in ofType('OBJECT_TYPE')" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
      </select>
      <select v-if="kind === 'DATA_RULE'" v-model="targetUid" data-testid="element-property">
        <option value="">属性</option>
        <option v-for="row in ofType('PROPERTY')" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
      </select>
      <template v-if="kind === 'PROPERTY'">
        <span data-testid="element-min"><el-input v-model="minCount" placeholder="最小基数" /></span>
        <span data-testid="element-max"><el-input v-model="maxCount" placeholder="最大基数" /></span>
        <span data-testid="element-default"><el-input v-model="defaultValue" placeholder="默认值" /></span>
      </template>
      <el-button native-type="submit" type="primary" data-testid="create-element">提交</el-button>
      <span class="hint" data-testid="form-error">{{ message }}</span>
    </form>
    <a v-if="downloadUrl" :href="downloadUrl" download="ontology.ttl" data-testid="download-ttl">下载导出文件</a>
    <pre v-if="preview" class="export" data-testid="export-preview">{{ preview }}</pre>
    <ul data-testid="element-list">
      <li v-for="row in ofType(kind)" :key="row.uid">{{ labelOf(row) }} · {{ row.apiName }}</li>
    </ul>
    <form class="form-grid" @submit.prevent="saveDescription">
      <select v-model="editUid" data-testid="edit-object">
        <option value="">选择要修改的对象</option>
        <option v-for="row in ofType('OBJECT_TYPE')" :key="row.uid" :value="row.uid">{{ labelOf(row) }}</option>
      </select>
      <span data-testid="edit-description"><el-input v-model="editDescription" placeholder="对象说明" /></span>
      <el-button native-type="submit" data-testid="save-description">保存说明</el-button>
    </form>
  </main>
</template>

<style scoped>
.kind-row { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.kind-row button { border: 1px solid var(--line); background: #fff; border-radius: 999px; padding: 6px 12px; }
.kind-row button.on { background: var(--pine); color: #fff; }
select { height: 32px; border: 1px solid var(--line); border-radius: 10px; background: #fff; }
ul, .export { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px 20px; }
.export { white-space: pre-wrap; max-height: 280px; overflow: auto; }
</style>
