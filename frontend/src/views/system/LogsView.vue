<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { http } from "@/api/http";
import SystemNav from "./SystemNav.vue";

const loginLogs = ref<Record<string, unknown>[]>([]);
const operLogs = ref<Record<string, unknown>[]>([]);
const query = ref("");
const shownLogin = computed(() => loginLogs.value.filter((row) => String(row.username ?? "").includes(query.value)));
const shownOper = computed(() => operLogs.value.filter((row) => `${row.title ?? ""} ${row.action ?? ""}`.includes(query.value)));

onMounted(async () => {
  const login = await http.get<{ data: Record<string, unknown>[] }>("/system/logs/login");
  const oper = await http.get<{ data: Record<string, unknown>[] }>("/system/logs/oper");
  loginLogs.value = login.data.data ?? [];
  operLogs.value = oper.data.data ?? [];
});
</script>

<template>
  <main class="page" data-testid="logs-page">
    <SystemNav />
    <h1>日志</h1>
    <input v-model="query" data-testid="log-query" placeholder="检索用户或动作" />
    <h2>登录日志</h2>
    <div data-testid="login-log-table">
      <el-table :data="shownLogin">
        <el-table-column prop="username" label="用户" />
        <el-table-column prop="message" label="结果" />
        <el-table-column prop="success" label="成功" />
      </el-table>
    </div>
    <h2>操作日志</h2>
    <div data-testid="oper-log-table">
      <el-table :data="shownOper">
        <el-table-column prop="title" label="模块" />
        <el-table-column prop="action" label="动作" />
        <el-table-column prop="success" label="成功" />
      </el-table>
    </div>
  </main>
</template>
