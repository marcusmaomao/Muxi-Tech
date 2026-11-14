<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http } from "@/api/http";
import SystemNav from "./SystemNav.vue";

const loginLogs = ref<Record<string, unknown>[]>([]);
const operLogs = ref<Record<string, unknown>[]>([]);

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
    <h2>登录日志</h2>
    <el-table :data="loginLogs">
      <el-table-column prop="username" label="用户" />
      <el-table-column prop="message" label="结果" />
      <el-table-column prop="success" label="成功" />
    </el-table>
    <h2>操作日志</h2>
    <el-table :data="operLogs" data-testid="oper-log-table">
      <el-table-column prop="title" label="模块" />
      <el-table-column prop="action" label="动作" />
      <el-table-column prop="success" label="成功" />
    </el-table>
  </main>
</template>
