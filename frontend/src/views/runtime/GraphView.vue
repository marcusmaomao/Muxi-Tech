<script setup lang="ts">
import { Graph } from "@antv/g6";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { http } from "@/api/http";

interface ProjectRow { id: string; name: string }
interface ElementRow { uid: string; elementType: string; apiName?: string; payload: string }
interface GraphObject { pk: string; props: Record<string, string> }
interface Neighbor { link: string; type: string; pk: string; props: Record<string, string> }

const projects = ref<ProjectRow[]>([]);
const types = ref<ElementRow[]>([]);
const projectId = ref("");
const typeName = ref("");
const rows = ref<GraphObject[]>([]);
const detail = ref("");
const neighbors = ref<Neighbor[]>([]);
const canvas = ref<HTMLElement | null>(null);
let graph: Graph | null = null;

function labelOf(row: ElementRow) {
  try {
    return (JSON.parse(row.payload) as { displayName?: string }).displayName || row.apiName || row.uid;
  } catch {
    return row.apiName || row.uid;
  }
}

function render(nodes: { id: string; label: string }[], edges: { source: string; target: string }[]) {
  if (!canvas.value) {
    return;
  }
  const data = {
    nodes: nodes.map((node) => ({ id: node.id, data: { label: node.label } })),
    edges: edges.map((edge, index) => ({ id: `e-${index}`, source: edge.source, target: edge.target })),
  };
  if (!graph) {
    graph = new Graph({
      container: canvas.value,
      width: 720,
      height: 320,
      data,
      node: { style: { labelText: (datum: { data?: { label?: string } }) => datum.data?.label ?? "" } },
      behaviors: ["drag-canvas", "zoom-canvas"],
    });
    void graph.render();
    return;
  }
  graph.setData(data);
  void graph.render();
}

async function loadProjects() {
  projects.value = (await http.get<{ data: ProjectRow[] }>("/projects")).data.data ?? [];
}

async function loadTypes() {
  types.value = [];
  typeName.value = "";
  if (!projectId.value) {
    return;
  }
  const branches = await http.get<{ data: { id: string; name: string }[] }>(`/projects/${projectId.value}/branches`);
  const main = (branches.data.data ?? []).find((branch) => branch.name === "main");
  if (!main) {
    return;
  }
  const elements = await http.get<{ data: ElementRow[] }>(`/branches/${main.id}/elements`);
  types.value = (elements.data.data ?? []).filter((row) => row.elementType === "OBJECT_TYPE");
}

async function loadObjects() {
  rows.value = [];
  neighbors.value = [];
  detail.value = "";
  if (!typeName.value) {
    return;
  }
  rows.value = (await http.get<{ data: GraphObject[] }>("/graph/objects", { params: { type: typeName.value } })).data.data ?? [];
  render(rows.value.map((row) => ({ id: `${typeName.value}:${row.pk}`, label: row.pk })), []);
}

function objectLabel(row: GraphObject) {
  const text = Object.entries(row.props ?? {})
    .filter(([key]) => !key.startsWith("_"))
    .map(([, value]) => value)
    .find((value) => value);
  return text ? `${text} · ${row.pk}` : row.pk;
}

function show(row: GraphObject) {
  detail.value = `${objectLabel(row)}\n${row.props._evidence ?? ""}\n${JSON.stringify(row.props)}`;
}

async function expand(row: GraphObject) {
  show(row);
  const response = await http.get<{ data: { center: GraphObject; neighbors: Neighbor[] } }>("/graph/neighbors", {
    params: { type: typeName.value, pk: row.pk },
  });
  neighbors.value = response.data.data?.neighbors ?? [];
  const nodes = [
    { id: `${typeName.value}:${row.pk}`, label: row.pk },
    ...neighbors.value.map((item) => ({ id: `${item.type}:${item.pk}`, label: item.pk })),
  ];
  const edges = neighbors.value.map((item) => ({ source: `${typeName.value}:${row.pk}`, target: `${item.type}:${item.pk}` }));
  render(nodes, edges);
}

watch(projectId, () => {
  void loadTypes();
});
watch(typeName, () => {
  void loadObjects();
});

onMounted(() => {
  void loadProjects();
});
onBeforeUnmount(() => {
  graph?.destroy();
});
</script>

<template>
  <main class="page" data-testid="graph-page">
    <h1>图谱探索</h1>
    <p class="lead">按对象类型筛选。点开一个节点可以展开邻居，并查看属性和原文出处。</p>
    <form class="form-grid">
      <select v-model="projectId" data-testid="graph-project">
        <option value="">项目</option>
        <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
      </select>
      <select v-model="typeName" data-testid="graph-type">
        <option value="">类型</option>
        <option v-for="row in types" :key="row.uid" :value="row.apiName">{{ labelOf(row) }}</option>
      </select>
    </form>
    <div ref="canvas" class="canvas" data-testid="graph-canvas"></div>
    <ul data-testid="graph-nodes">
      <li v-for="row in rows" :key="row.pk">
        <button type="button" @click="show(row)">{{ objectLabel(row) }}</button>
        <button type="button" :data-testid="`expand-${row.pk}`" @click="expand(row)">展开邻居</button>
      </li>
    </ul>
    <ul data-testid="graph-neighbors">
      <li v-for="item in neighbors" :key="`${item.type}-${item.pk}`">{{ item.link }} · {{ item.pk }}</li>
    </ul>
    <pre class="detail" data-testid="graph-detail">{{ detail }}</pre>
  </main>
</template>

<style scoped>
select, button { height: 32px; }
.canvas { height: 320px; margin: 12px 0; background: #fff; border: 1px solid var(--line); border-radius: 12px; }
ul, .detail { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px 16px; }
</style>
