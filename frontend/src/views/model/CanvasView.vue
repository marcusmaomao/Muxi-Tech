<script setup lang="ts">
import { Graph, type Node } from "@antv/x6";
import { History } from "@antv/x6-plugin-history";
import { Keyboard } from "@antv/x6-plugin-keyboard";
import { MiniMap } from "@antv/x6-plugin-minimap";
import { Selection } from "@antv/x6-plugin-selection";
import { Snapline } from "@antv/x6-plugin-snapline";
import { Stencil } from "@antv/x6-plugin-stencil";
import "@antv/x6-plugin-stencil/es/index.css";
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, http } from "@/api/http";
import type { LayoutPoint } from "@/canvas/layeredLayout";

interface ElementRow {
  uid: string;
  elementType: string;
  apiName?: string;
  payload: string;
}

interface NodeData {
  kind: "OBJECT_TYPE" | "ENUM_TYPE" | "NOTE";
  apiName: string;
  displayName: string;
  cqUid: string;
  parents: string[];
  committed: boolean;
  serverUid: string;
  invalid: string;
}

interface ListedNode extends NodeData {
  id: string;
}

interface CqRow {
  uid: string;
  label: string;
}

type CanvasGraph = Graph & {
  undo: () => CanvasGraph;
  redo: () => CanvasGraph;
  disableHistory: () => CanvasGraph;
  enableHistory: () => CanvasGraph;
  cleanHistory: () => CanvasGraph;
  bindKey: (keys: string | string[], handler: () => void) => CanvasGraph;
};

const route = useRoute();
const router = useRouter();
const projectId = computed(() => String(route.params.projectId ?? ""));
const projects = ref<Array<{ id: string; name: string }>>([]);
const branchId = ref("");
const cqs = ref<CqRow[]>([]);
const nodes = ref<ListedNode[]>([]);
const edges = ref<Array<{ id: string; label: string }>>([]);
const message = ref("");
const lockState = ref("");
const ready = ref(false);
const selectedId = ref("");
const panelName = ref("");
const panelApi = ref("");
const panelCq = ref("");
const panelParent = ref("");
const graphHost = ref<HTMLElement | null>(null);
const mapHost = ref<HTMLElement | null>(null);
const stencilHost = ref<HTMLElement | null>(null);
interface Snap {
  nodes: Array<{ id: string; x: number; y: number; data: NodeData }>;
  edges: Array<{ source: string; target: string; kind: string }>;
}

let graph: CanvasGraph | null = null;
let seq = 0;
let booted = false;
const past: Snap[] = [];
const future: Snap[] = [];

function readName(row: ElementRow) {
  try {
    const payload = JSON.parse(row.payload) as { displayName?: string };
    return payload.displayName || row.apiName || row.uid;
  } catch {
    return row.apiName || row.uid;
  }
}

function dataOf(node: Node): NodeData {
  return node.getData() as NodeData;
}

function missingCq(data: NodeData) {
  return data.kind === "OBJECT_TYPE" && !data.cqUid;
}

function paint(node: Node) {
  const data = dataOf(node);
  const missing = missingCq(data);
  node.attr("body/stroke", missing ? "#9d3b32" : "#1c5c4e");
  node.attr("body/strokeWidth", node.id === selectedId.value ? 3 : 1.5);
  node.attr("label/text", missing ? `${data.displayName}\n缺少能力问题` : data.displayName);
}

function sync() {
  if (!graph) {
    return;
  }
  nodes.value = graph.getNodes().map((node) => {
    const data = dataOf(node);
    return { id: node.id, ...data, invalid: missingCq(data) ? "缺少能力问题" : "" };
  });
  edges.value = graph.getEdges().map((edge) => {
    const source = graph?.getCellById(edge.getSourceCellId() ?? "") as Node | null;
    const target = graph?.getCellById(edge.getTargetCellId() ?? "") as Node | null;
    const kind = (edge.getData() as { kind?: string } | undefined)?.kind ?? "EXTENDS";
    const label = kind === "LINK" ? "关系" : "继承";
    return {
      id: edge.id,
      label: `${label} ${source ? dataOf(source).displayName : ""} → ${target ? dataOf(target).displayName : ""}`,
    };
  });
}

function capture(): Snap {
  if (!graph) {
    return { nodes: [], edges: [] };
  }
  return {
    nodes: graph.getNodes().map((node) => {
      const position = node.position();
      return { id: node.id, x: position.x, y: position.y, data: { ...dataOf(node) } };
    }),
    edges: graph.getEdges().map((edge) => ({
      source: edge.getSourceCellId() ?? "",
      target: edge.getTargetCellId() ?? "",
      kind: ((edge.getData() as { kind?: string } | undefined)?.kind) ?? "EXTENDS",
    })),
  };
}

function restore(snap: Snap) {
  if (!graph) {
    return;
  }
  graph.disableHistory();
  graph.clearCells();
  snap.nodes.forEach((item) => {
    const node = graph?.addNode({
      id: item.id,
      x: item.x,
      y: item.y,
      width: 180,
      height: 64,
      attrs: {
        body: { fill: "#fffdf8", stroke: "#1c5c4e", rx: 12, ry: 12 },
        label: { fill: "#10241f", fontSize: 14 },
      },
      data: item.data,
    });
    if (node) {
      paint(node);
    }
  });
  snap.edges.forEach((edge) => {
    graph?.addEdge({
      source: edge.source,
      target: edge.target,
      attrs: {
        line: {
          stroke: "#1c5c4e",
          strokeWidth: 1.6,
          strokeDasharray: edge.kind === "LINK" ? "6 4" : undefined,
          targetMarker: { name: "block", width: 14, height: 10, fill: "#fffdf8", stroke: "#1c5c4e" },
        },
      },
      data: { kind: edge.kind },
    });
  });
  graph.cleanHistory();
  graph.enableHistory();
  sync();
}

function remember() {
  past.push(capture());
  if (past.length > 50) {
    past.shift();
  }
  future.length = 0;
}

function addDraft(kind: NodeData["kind"]) {
  if (!graph) {
    return;
  }
  remember();
  seq += 1;
  const apiName = kind === "OBJECT_TYPE" ? `Obj${seq}` : kind === "ENUM_TYPE" ? `Enum${seq}` : `Note${seq}`;
  const displayName = kind === "OBJECT_TYPE" ? `新对象${seq}` : kind === "ENUM_TYPE" ? `新枚举${seq}` : `注释${seq}`;
  const node = graph.batchUpdate("add-draft", () => graph?.addNode({
    id: `draft-${seq}`,
    x: 72 + ((seq - 1) % 3) * 210,
    y: 72 + Math.floor((seq - 1) / 3) * 120,
    width: 180,
    height: 64,
    attrs: {
      body: { fill: "#fffdf8", stroke: "#1c5c4e", rx: 12, ry: 12 },
      label: { fill: "#10241f", fontSize: 14, textWrap: { width: 160, height: 48 } },
    },
    data: {
      kind,
      apiName,
      displayName,
      cqUid: kind === "OBJECT_TYPE" ? panelCq.value : "",
      parents: [],
      committed: false,
      serverUid: "",
      invalid: "",
    } satisfies NodeData,
  })) as Node;
  paint(node);
  select(node.id);
  sync();
}

function undoCanvas() {
  const snap = past.pop();
  if (!snap) {
    return;
  }
  future.push(capture());
  restore(snap);
}

function redoCanvas() {
  const snap = future.pop();
  if (!snap) {
    return;
  }
  past.push(capture());
  restore(snap);
}

function select(id: string) {
  selectedId.value = id;
  const node = graph?.getCellById(id) as Node | null;
  if (!node) {
    return;
  }
  const data = dataOf(node);
  panelName.value = data.displayName;
  panelApi.value = data.apiName;
  panelCq.value = data.cqUid;
  const parent = data.parents[0];
  panelParent.value = parent ?? "";
  graph?.getNodes().forEach(paint);
  lockState.value = "";
}

function wouldCycle(childId: string, parentId: string) {
  const seen = new Set<string>();
  const stack = [parentId];
  while (stack.length > 0) {
    const next = stack.pop() ?? "";
    if (next === childId) {
      return true;
    }
    if (seen.has(next)) {
      continue;
    }
    seen.add(next);
    const node = graph?.getCellById(next) as Node | null;
    if (node) {
      stack.push(...dataOf(node).parents);
    }
  }
  return false;
}

function applyPanel() {
  if (!graph || !selectedId.value) {
    message.value = "请先选择节点";
    return;
  }
  const node = graph.getCellById(selectedId.value) as Node;
  const data = dataOf(node);
  if (panelParent.value === node.id) {
    message.value = "不能把自己设为父类";
    return;
  }
  if (panelParent.value && wouldCycle(node.id, panelParent.value)) {
    message.value = "继承环";
    return;
  }
  remember();
  data.displayName = panelName.value.trim() || data.displayName;
  if (!data.committed) {
    data.apiName = panelApi.value.trim() || data.apiName;
  }
  data.cqUid = panelCq.value;
  data.parents = panelParent.value ? [panelParent.value] : [];
  node.setData(data);
  paint(node);
  graph.getEdges().filter((edge) => edge.getSourceCellId() === node.id && (edge.getData() as { kind?: string })?.kind !== "LINK").forEach((edge) => edge.remove());
  if (panelParent.value) {
    graph.addEdge({
      source: node.id,
      target: panelParent.value,
      attrs: {
        line: {
          stroke: "#1c5c4e",
          strokeWidth: 1.6,
          targetMarker: { name: "block", width: 14, height: 10, fill: "#fffdf8", stroke: "#1c5c4e" },
        },
      },
      data: { kind: "EXTENDS" },
    });
  }
  message.value = "已应用到画布";
  sync();
}

async function createCq() {
  message.value = "";
  try {
    const response = await http.post<{ data: ElementRow }>(`/branches/${branchId.value}/cqs`, {
      displayName: "画布对象",
      apiName: `CanvasCq${seq + 1}`,
      priority: "P1",
      acceptance: "能在画布上看到",
    });
    const row = response.data.data;
    if (!row) {
      return;
    }
    cqs.value.push({ uid: row.uid, label: readName(row) });
    panelCq.value = row.uid;
    message.value = "能力问题已就绪";
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "创建失败";
  }
}

async function commitCanvas() {
  if (!graph) {
    return;
  }
  message.value = "";
  try {
    for (const node of graph.getNodes()) {
      const data = dataOf(node);
      if (data.kind === "NOTE" || data.committed) {
        continue;
      }
      if (data.kind === "OBJECT_TYPE" && !data.cqUid) {
        paint(node);
        continue;
      }
      const path = data.kind === "ENUM_TYPE" ? "enums" : "object-types";
      const payload: Record<string, unknown> = { displayName: data.displayName, apiName: data.apiName };
      if (data.kind === "OBJECT_TYPE") {
        payload.cqUids = [data.cqUid];
      }
      const response = await http.post<{ data: ElementRow }>(`/branches/${branchId.value}/${path}`, payload);
      data.committed = true;
      data.serverUid = response.data.data?.uid ?? "";
      node.setData(data);
      paint(node);
    }
    for (const node of graph.getNodes()) {
      const data = dataOf(node);
      if (data.kind !== "OBJECT_TYPE" || !data.committed || data.parents.length === 0) {
        continue;
      }
      const parentUids = data.parents
        .map((id) => (graph?.getCellById(id) as Node | null)?.getData() as NodeData | undefined)
        .map((parent) => parent?.serverUid)
        .filter((uid): uid is string => Boolean(uid));
      if (parentUids.length === 0) {
        continue;
      }
      await http.put(`/branches/${branchId.value}/object-types/${data.serverUid}`, {
        displayName: data.displayName,
        apiName: data.apiName,
        cqUids: [data.cqUid],
        parentUids,
      });
    }
    message.value = "已提交";
    sync();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "提交失败";
  }
}

async function saveView() {
  if (!graph) {
    return;
  }
  const layout: Record<string, { x: number; y: number }> = {};
  graph.getNodes().forEach((node) => {
    const data = dataOf(node);
    const key = data.serverUid || node.id;
    const position = node.position();
    layout[key] = { x: position.x, y: position.y };
  });
  try {
    await http.post(`/branches/${branchId.value}/canvas-views`, { name: "默认", layout: JSON.stringify(layout) });
    message.value = "视图已保存";
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "保存失败";
  }
}

function layoutNodes() {
  if (!graph) {
    return;
  }
  const worker = new Worker(new URL("../../canvas/layout.worker.ts", import.meta.url), { type: "module" });
  worker.onmessage = (event: MessageEvent<LayoutPoint[]>) => {
    event.data.forEach((point) => {
      const node = graph?.getCellById(point.id) as Node | null;
      node?.position(point.x, point.y);
    });
    worker.terminate();
    sync();
    message.value = "已自动布局";
  };
  worker.postMessage({
    nodes: graph.getNodes().map((node) => ({ id: node.id })),
    edges: graph.getEdges().map((edge) => ({
      id: edge.id,
      source: edge.getTargetCellId() ?? "",
      target: edge.getSourceCellId() ?? "",
    })),
  });
}

async function lockSelected() {
  const node = graph?.getCellById(selectedId.value) as Node | null;
  if (!node) {
    message.value = "请先选择节点";
    return;
  }
  const data = dataOf(node);
  if (!data.serverUid) {
    message.value = "请先提交再锁定";
    return;
  }
  try {
    await http.post(`/branches/${branchId.value}/locks/${data.serverUid}`);
    lockState.value = "已锁定";
  } catch (caught) {
    lockState.value = "";
    message.value = caught instanceof ApiError ? caught.message : "锁定失败";
  }
}

function placeExisting(rows: ElementRow[], layout: Record<string, { x: number; y: number }>) {
  if (!graph) {
    return;
  }
  graph.disableHistory();
  graph.clearCells();
  const objects = rows.filter((row) => row.elementType === "OBJECT_TYPE" || row.elementType === "ENUM_TYPE");
  objects.forEach((row, index) => {
    const saved = layout[row.uid];
    const payload = JSON.parse(row.payload) as { cqUids?: string[]; parentUids?: string[] };
    graph?.addNode({
      id: row.uid,
      x: saved?.x ?? 72 + (index % 3) * 210,
      y: saved?.y ?? 72 + Math.floor(index / 3) * 120,
      width: 180,
      height: 64,
      attrs: {
        body: { fill: "#fffdf8", stroke: "#1c5c4e", rx: 12, ry: 12 },
        label: { fill: "#10241f", fontSize: 14 },
      },
      data: {
        kind: row.elementType as NodeData["kind"],
        apiName: row.apiName ?? row.uid,
        displayName: readName(row),
        cqUid: payload.cqUids?.[0] ?? "",
        parents: payload.parentUids ?? [],
        committed: true,
        serverUid: row.uid,
        invalid: "",
      } satisfies NodeData,
    });
  });
  objects.filter((row) => row.elementType === "OBJECT_TYPE").forEach((row) => {
    const payload = JSON.parse(row.payload) as { parentUids?: string[] };
    (payload.parentUids ?? []).forEach((parent) => {
      if (graph?.getCellById(parent)) {
        graph?.addEdge({
          source: row.uid,
          target: parent,
          attrs: {
            line: {
              stroke: "#1c5c4e",
              strokeWidth: 1.6,
              targetMarker: { name: "block", width: 14, height: 10, fill: "#fffdf8", stroke: "#1c5c4e" },
            },
          },
          data: { kind: "EXTENDS" },
        });
      }
    });
  });
  graph.getNodes().forEach(paint);
  graph.cleanHistory();
  graph.enableHistory();
  sync();
}

async function boot() {
  if (!projectId.value || !graphHost.value || booted) {
    return;
  }
  const branches = await http.get<{ data: Array<{ id: string; name: string }> }>(`/projects/${projectId.value}/branches`);
  const main = (branches.data.data ?? []).find((branch) => branch.name === "main");
  if (!main) {
    message.value = "没有 main 分支";
    return;
  }
  branchId.value = main.id;
  graph = new Graph({
    container: graphHost.value,
    grid: { visible: true, size: 10 },
    panning: true,
    mousewheel: { enabled: true, modifiers: ["ctrl", "meta"] },
    background: { color: "#f7f4ee" },
    connecting: { allowBlank: false, allowLoop: false },
  }) as CanvasGraph;
  graph.use(new History({ enabled: true, stackSize: 50 }));
  graph.use(new Selection({ enabled: true, multiple: true, rubberband: true, showNodeSelectionBox: true }));
  graph.use(new Snapline({ enabled: true }));
  graph.use(new Keyboard({ enabled: true, global: true }));
  if (mapHost.value) {
    graph.use(new MiniMap({ container: mapHost.value, width: 180, height: 120 }));
  }
  graph.bindKey(["ctrl+z", "meta+z"], () => {
    graph?.undo();
    sync();
  });
  graph.bindKey(["ctrl+shift+z", "meta+shift+z"], () => {
    graph?.redo();
    sync();
  });
  graph.on("node:click", ({ node }) => select(node.id));
  graph.on("node:added", ({ node }) => {
    const stencil = (node.getData() as { stencil?: string } | undefined)?.stencil;
    if (stencil === "OBJECT_TYPE" || stencil === "ENUM_TYPE" || stencil === "NOTE") {
      seq += 1;
      const kind = stencil as NodeData["kind"];
      const apiName = kind === "OBJECT_TYPE" ? `Obj${seq}` : kind === "ENUM_TYPE" ? `Enum${seq}` : `Note${seq}`;
      const displayName = kind === "OBJECT_TYPE" ? `新对象${seq}` : kind === "ENUM_TYPE" ? `新枚举${seq}` : `注释${seq}`;
      node.setData({
        kind,
        apiName,
        displayName,
        cqUid: kind === "OBJECT_TYPE" ? panelCq.value : "",
        parents: [],
        committed: false,
        serverUid: "",
        invalid: "",
      } satisfies NodeData);
      paint(node);
      select(node.id);
    }
    sync();
  });
  if (stencilHost.value) {
    try {
      const stencil = new Stencil({
        title: "元素",
        target: graph,
        stencilGraphWidth: 200,
        stencilGraphHeight: 160,
        groups: [{ name: "basic", title: "拖入" }],
      });
      stencilHost.value.appendChild(stencil.container);
      stencil.load([
        graph.createNode({ shape: "rect", width: 160, height: 36, label: "对象类型", data: { stencil: "OBJECT_TYPE" } }),
        graph.createNode({ shape: "rect", width: 160, height: 36, label: "枚举", data: { stencil: "ENUM_TYPE" } }),
      ], "basic");
    } catch {
      // 点选按钮仍然可以加入元素。
    }
  }
  const elements = await http.get<{ data: ElementRow[] }>(`/branches/${branchId.value}/elements`);
  const rows = elements.data.data ?? [];
  cqs.value = rows.filter((row) => row.elementType === "CQ").map((row) => ({ uid: row.uid, label: readName(row) }));
  const views = await http.get<{ data: Array<{ layout: string }> }>(`/branches/${branchId.value}/canvas-views`);
  const raw = views.data.data?.[0]?.layout;
  const layout = raw ? JSON.parse(raw) as Record<string, { x: number; y: number }> : {};
  placeExisting(rows, layout);
  seq = Math.max(seq, rows.length);
  booted = true;
  ready.value = true;
}

async function openProject(event: Event) {
  const id = (event.target as HTMLSelectElement).value;
  if (id) {
    await router.push(`/model/${id}/canvas`);
  }
}

watch(projectId, async () => {
  booted = false;
  ready.value = false;
  graph?.dispose();
  graph = null;
  await nextTick();
  await boot();
});

onBeforeUnmount(() => {
  graph?.dispose();
});

void (async () => {
  if (!projectId.value) {
    const response = await http.get<{ data: Array<{ id: string; name: string }> }>("/projects");
    projects.value = response.data.data ?? [];
    return;
  }
  await nextTick();
  try {
    await boot();
  } catch (caught) {
    message.value = caught instanceof Error ? caught.message : "画布初始化失败";
  }
})();
</script>

<template>
  <main class="page canvas-page" data-testid="canvas-page">
    <h1>画布建模</h1>
    <p class="lead">从左侧拖入或点选元素。画布上的改动先留在本地，点提交后才写入分支。IRI 仍使用项目自己的占位命名空间。</p>
    <select v-if="!projectId" data-testid="canvas-project" @change="openProject">
      <option value="">选择项目</option>
      <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
    </select>
    <template v-else>
      <div class="toolbar">
        <button type="button" data-testid="undo-canvas" @click="undoCanvas">撤销</button>
        <button type="button" data-testid="redo-canvas" @click="redoCanvas">重做</button>
        <button type="button" data-testid="layout-canvas" @click="layoutNodes">自动布局</button>
        <button type="button" data-testid="save-view" @click="saveView">保存视图</button>
        <button type="button" data-testid="commit-canvas" @click="commitCanvas">提交</button>
        <button type="button" data-testid="create-cq" @click="createCq">新建能力问题</button>
        <span data-testid="canvas-message">{{ message }}</span>
      </div>
      <div class="workspace">
        <aside class="stencil" data-testid="stencil">
          <button type="button" data-testid="stencil-object" :disabled="!ready" @click="addDraft('OBJECT_TYPE')">对象类型</button>
          <span v-if="ready" data-testid="canvas-ready"></span>
          <button type="button" data-testid="stencil-enum" @click="addDraft('ENUM_TYPE')">枚举</button>
          <button type="button" data-testid="stencil-note" @click="addDraft('NOTE')">注释</button>
          <div ref="stencilHost" class="stencil-host"></div>
        </aside>
        <div class="stage">
          <div ref="graphHost" class="graph" data-testid="canvas-graph"></div>
          <div ref="mapHost" class="map" data-testid="canvas-minimap"></div>
        </div>
        <aside class="panel" data-testid="canvas-panel">
          <h2>属性</h2>
          <label>名称 <input v-model="panelName" data-testid="canvas-name" /></label>
          <label>API 名 <input v-model="panelApi" data-testid="canvas-api" /></label>
          <label>能力问题
            <select v-model="panelCq" data-testid="canvas-cq">
              <option value="">未选择</option>
              <option v-for="cq in cqs" :key="cq.uid" :value="cq.uid">{{ cq.label }}</option>
            </select>
          </label>
          <label>父类
            <select v-model="panelParent" data-testid="canvas-parent">
              <option value="">无</option>
              <option v-for="node in nodes.filter((item) => item.id !== selectedId)" :key="node.id" :value="node.id">{{ node.displayName }}</option>
            </select>
          </label>
          <button type="button" data-testid="apply-node" @click="applyPanel">应用到画布</button>
          <button type="button" data-testid="lock-node" @click="lockSelected">锁定</button>
          <p data-testid="lock-state">{{ lockState }}</p>
        </aside>
      </div>
      <ul data-testid="canvas-nodes">
        <li v-for="node in nodes" :key="node.id" :data-testid="`node-${node.apiName}`" @click="select(node.id)">
          {{ node.displayName }}
          <span v-if="node.invalid" data-testid="validation-mark">{{ node.invalid }}</span>
        </li>
      </ul>
      <ul data-testid="canvas-edges">
        <li v-for="edge in edges" :key="edge.id">{{ edge.label }}</li>
      </ul>
    </template>
  </main>
</template>

<style scoped>
.toolbar, .stencil, .panel { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.workspace { display: grid; grid-template-columns: 200px 1fr 240px; gap: 12px; margin-top: 12px; }
.stencil, .panel { flex-direction: column; align-items: stretch; background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px; }
.graph { height: 480px; border: 1px solid var(--line); border-radius: 12px; background: #f7f4ee; }
.map { height: 120px; margin-top: 8px; }
label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
input, select, button { height: 32px; }
.stencil-host { position: relative; height: 220px; overflow: hidden; }
.stage { min-width: 0; }
.graph { width: 100%; }
ul { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px 20px; }
</style>
