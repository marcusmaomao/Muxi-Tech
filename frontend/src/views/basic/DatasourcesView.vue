<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http, ApiError } from "@/api/http";
import BasicNav from "./BasicNav.vue";

interface DatasourceRow {
  id: string;
  name: string;
  dbType: string;
  host: string;
  port: number;
  databaseName: string;
}

interface TableRow {
  name: string;
  kind: string;
}

interface ColumnRow {
  name: string;
  typeName: string;
}

const rows = ref<DatasourceRow[]>([]);
const activeId = ref("");
const tables = ref<TableRow[]>([]);
const columns = ref<ColumnRow[]>([]);
const name = ref("");
const dbType = ref("MYSQL");
const host = ref("");
const port = ref("3306");
const databaseName = ref("");
const username = ref("");
const password = ref("");
const message = ref("");

async function load() {
  const response = await http.get<{ data: DatasourceRow[] }>("/datasources");
  rows.value = response.data.data ?? [];
}

async function createDatasource() {
  message.value = "";
  try {
    await http.post("/datasources", {
      name: name.value,
      dbType: dbType.value,
      host: host.value,
      port: Number(port.value),
      databaseName: databaseName.value,
      username: username.value,
      password: password.value,
    });
    password.value = "";
    message.value = "已保存，口令已加密";
    await load();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "保存失败";
  }
}

async function test(row: DatasourceRow) {
  const response = await http.post<{ data: { ok: boolean; message: string; elapsedMs: number } }>(`/datasources/${row.id}/test`);
  const result = response.data.data;
  message.value = result ? `${result.ok ? "已连通" : "未连通"}：${result.message}（${result.elapsedMs} ms）` : "无结果";
}

async function browse(row: DatasourceRow) {
  columns.value = [];
  activeId.value = row.id;
  const response = await http.get<{ data: TableRow[] }>(`/datasources/${row.id}/tables`);
  tables.value = response.data.data ?? [];
  message.value = `${row.name} 的表`;
}

async function openColumns(table: string) {
  const response = await http.get<{ data: ColumnRow[] }>(`/datasources/${activeId.value}/tables/${encodeURIComponent(table)}/columns`);
  columns.value = response.data.data ?? [];
}

onMounted(() => {
  void load();
});
</script>

<template>
  <main class="page" data-testid="datasources-page">
    <BasicNav />
    <h1>数据源</h1>
    <p class="lead">登记 MySQL、PostgreSQL 或达梦。口令加密保存，连通测试在 5 秒内返回。</p>
    <form class="form-grid" @submit.prevent="createDatasource">
      <span data-testid="ds-name"><el-input v-model="name" placeholder="名称" /></span>
      <select v-model="dbType" data-testid="ds-type">
        <option value="MYSQL">MySQL</option>
        <option value="POSTGRESQL">PostgreSQL</option>
        <option value="DAMENG">达梦</option>
      </select>
      <span data-testid="ds-host"><el-input v-model="host" placeholder="主机" /></span>
      <span data-testid="ds-port"><el-input v-model="port" placeholder="端口" /></span>
      <span data-testid="ds-database"><el-input v-model="databaseName" placeholder="数据库" /></span>
      <span data-testid="ds-username"><el-input v-model="username" placeholder="用户名" /></span>
      <span data-testid="ds-password"><el-input v-model="password" type="password" placeholder="密码" show-password /></span>
      <el-button native-type="submit" type="primary" data-testid="create-datasource">保存</el-button>
    </form>
    <p class="hint" data-testid="ds-result">{{ message }}</p>
    <div data-testid="datasource-table">
      <table class="plain">
        <thead>
          <tr><th>名称</th><th>类型</th><th>地址</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.name }}</td>
            <td>{{ row.dbType }}</td>
            <td>{{ row.host }}:{{ row.port }}/{{ row.databaseName }}</td>
            <td>
              <button type="button" @click="test(row)">测试</button>
              <button type="button" @click="browse(row)">表</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div data-testid="table-list">
      <button v-for="table in tables" :key="table.name" type="button" @click="openColumns(table.name)">
        {{ table.name }}
      </button>
    </div>
    <ul data-testid="column-list">
      <li v-for="column in columns" :key="column.name">{{ column.name }} {{ column.typeName }}</li>
    </ul>
  </main>
</template>

<style scoped>
select { height: 32px; border: 1px solid var(--line); border-radius: 10px; background: #fff; padding: 0 8px; }
button { margin-right: 6px; }
.plain { width: 100%; border-collapse: collapse; background: #fff; }
.plain th, .plain td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--line); }
</style>
