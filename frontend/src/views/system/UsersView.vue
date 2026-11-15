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
interface RoleRow { id: string; name: string; code: string }

const rows = ref<UserRow[]>([]);
const roles = ref<RoleRow[]>([]);
const username = ref("");
const displayName = ref("");
const password = ref("demo-admin");
const tenantCode = ref("");
const tenantName = ref("");
const tenantAdmin = ref("");
const tenantPassword = ref("demo-admin");
const roleCode = ref("");
const roleName = ref("");
const grantUserId = ref("");
const grantRoleId = ref("");
const message = ref("");

async function load() {
  const response = await http.get<{ data: { records: UserRow[] } }>("/system/users");
  rows.value = response.data.data?.records ?? [];
  if (auth.can("system:role:list")) {
    roles.value = (await http.get<{ data: RoleRow[] }>("/system/roles")).data.data ?? [];
  }
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

async function createTenant() {
  await http.post("/tenants", {
    code: tenantCode.value,
    name: tenantName.value,
    adminUsername: tenantAdmin.value,
    adminPassword: tenantPassword.value,
    adminDisplayName: tenantName.value,
  });
  message.value = "租户已创建";
}

async function createRole() {
  await http.post("/system/roles", { code: roleCode.value, name: roleName.value });
  roleCode.value = "";
  roleName.value = "";
  message.value = "角色已创建";
  await load();
}

async function grant() {
  await http.post(`/system/users/${grantUserId.value}/roles/${grantRoleId.value}`);
  message.value = "已授权";
}

onMounted(() => {
  void load();
});
</script>

<template>
  <main class="page" data-testid="users-page">
    <SystemNav />
    <h1>用户</h1>
    <form v-if="auth.can('system:user:create')" class="inline-form" data-testid="tenant-form" @submit.prevent="createTenant">
      <span data-testid="tenant-code"><el-input v-model="tenantCode" placeholder="租户编码" /></span>
      <span data-testid="tenant-name"><el-input v-model="tenantName" placeholder="租户名称" /></span>
      <span data-testid="tenant-admin"><el-input v-model="tenantAdmin" placeholder="租户管理员" /></span>
      <span data-testid="tenant-password"><el-input v-model="tenantPassword" placeholder="初始口令" /></span>
      <el-button native-type="submit" data-testid="create-tenant">创建租户</el-button>
    </form>
    <form v-if="auth.can('system:role:create')" class="inline-form" @submit.prevent="createRole">
      <span data-testid="role-code"><el-input v-model="roleCode" placeholder="角色编码" /></span>
      <span data-testid="role-name"><el-input v-model="roleName" placeholder="角色名称" /></span>
      <el-button native-type="submit" data-testid="create-role">新建角色</el-button>
    </form>
    <form v-if="auth.can('system:user:grant')" class="inline-form" @submit.prevent="grant">
      <select v-model="grantUserId" data-testid="grant-user">
        <option value="">用户</option>
        <option v-for="row in rows" :key="row.id" :value="row.id">{{ row.username }}</option>
      </select>
      <select v-model="grantRoleId" data-testid="grant-role">
        <option value="">角色</option>
        <option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</option>
      </select>
      <el-button native-type="submit" data-testid="grant-role-button">授权</el-button>
    </form>
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
