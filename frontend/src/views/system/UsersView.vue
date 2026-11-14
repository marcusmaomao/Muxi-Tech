<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http } from "@/api/http";
import { useAuthStore } from "@/stores/auth";
import SystemNav from "./SystemNav.vue";

interface UserRow {
  id: string;
  username: string;
  displayName: string;
  status: number;
}

const auth = useAuthStore();
const rows = ref<UserRow[]>([]);
const username = ref("");
const displayName = ref("");
const password = ref("demo-admin");
const message = ref("");

async function load() {
  const response = await http.get<{ data: { records: UserRow[] } }>("/system/users");
  rows.value = response.data.data?.records ?? [];
}

async function createUser() {
  await http.post("/system/users", {
    username: username.value,
    password: password.value,
    displayName: displayName.value,
  });
  username.value = "";
  displayName.value = "";
  message.value = "已创建";
  await load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <main class="page" data-testid="users-page">
    <SystemNav />
    <h1>用户</h1>
    <form v-if="auth.can('system:user:create')" class="inline-form" @submit.prevent="createUser">
      <span data-testid="new-username"><el-input v-model="username" placeholder="用户名" /></span>
      <span data-testid="new-display-name"><el-input v-model="displayName" placeholder="显示名" /></span>
      <el-button native-type="submit" type="primary" data-testid="create-user">新建</el-button>
      <span>{{ message }}</span>
    </form>
    <div data-testid="user-table">
      <el-table :data="rows">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="displayName" label="显示名" />
        <el-table-column prop="status" label="状态" />
      </el-table>
    </div>
  </main>
</template>

<style scoped>
.inline-form { display: flex; gap: 8px; margin-bottom: 16px; align-items: center; }
</style>
