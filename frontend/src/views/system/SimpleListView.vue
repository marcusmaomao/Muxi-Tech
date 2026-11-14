<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { http } from "@/api/http";
import SystemNav from "./SystemNav.vue";

const props = defineProps<{ kind: "roles" | "menus" | "depts" | "posts" | "dicts" | "configs" | "notices" }>();
const rows = ref<Record<string, unknown>[]>([]);
const title: Record<typeof props.kind, string> = {
  roles: "角色",
  menus: "菜单",
  depts: "部门",
  posts: "岗位",
  dicts: "字典",
  configs: "参数",
  notices: "通知",
};
const name = ref("");
const extra = ref("");

async function load() {
  const response = await http.get<{ data: Record<string, unknown>[] }>(`/system/${props.kind}`);
  rows.value = response.data.data ?? [];
}

async function createItem() {
  const body =
    props.kind === "depts"
      ? { name: name.value }
      : props.kind === "roles" || props.kind === "posts" || props.kind === "dicts"
        ? { code: extra.value || name.value, name: name.value }
        : props.kind === "configs"
          ? { key: name.value, value: extra.value || "1" }
          : props.kind === "notices"
            ? { title: name.value, content: extra.value || "内容" }
            : null;
  if (!body) {
    return;
  }
  await http.post(`/system/${props.kind}`, body);
  name.value = "";
  extra.value = "";
  await load();
}

onMounted(() => {
  void load();
});
watch(() => props.kind, () => void load());
</script>

<template>
  <main class="page" :data-testid="`${kind}-page`">
    <SystemNav />
    <h1>{{ title[kind] }}</h1>
    <form v-if="kind !== 'menus'" class="inline-form" @submit.prevent="createItem">
      <el-input v-model="name" :placeholder="title[kind]" />
      <el-input v-if="kind !== 'depts'" v-model="extra" placeholder="编码或内容" />
      <el-button native-type="submit" type="primary">新建</el-button>
    </form>
    <el-table :data="rows">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="permission" label="权限" />
      <el-table-column prop="configKey" label="参数" />
    </el-table>
  </main>
</template>

<style scoped>
.inline-form { display: flex; gap: 8px; margin-bottom: 16px; }
</style>
