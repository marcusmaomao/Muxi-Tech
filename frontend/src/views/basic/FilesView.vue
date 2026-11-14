<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http, ApiError } from "@/api/http";
import BasicNav from "./BasicNav.vue";

interface FileRow {
  id: string;
  originalName: string;
  category?: string;
  sizeBytes: number;
  textStatus: string;
}

const rows = ref<FileRow[]>([]);
const category = ref("");
const picked = ref<File | null>(null);
const message = ref("");
const text = ref("");

async function load() {
  const response = await http.get<{ data: FileRow[] }>("/files");
  rows.value = response.data.data ?? [];
}

function onPick(event: Event) {
  const input = event.target as HTMLInputElement;
  picked.value = input.files?.[0] ?? null;
}

async function upload() {
  if (!picked.value) {
    message.value = "请选择文件";
    return;
  }
  const body = new FormData();
  body.append("file", picked.value);
  if (category.value.trim()) {
    body.append("category", category.value.trim());
  }
  try {
    await http.post("/files", body);
    message.value = "已上传";
    picked.value = null;
    await load();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "上传失败";
  }
}

async function readText(row: FileRow) {
  const response = await http.get<{ data: { text: string; textStatus: string } }>(`/files/${row.id}/text`);
  text.value = response.data.data?.text ?? "";
  message.value = response.data.data?.textStatus ?? "";
}

onMounted(() => {
  void load();
});
</script>

<template>
  <main class="page" data-testid="files-page">
    <BasicNav />
    <h1>文件</h1>
    <p class="lead">文件放在 MinIO，文本由 Tika 抽出。单个文件不超过 50 MB。</p>
    <form class="form-grid" @submit.prevent="upload">
      <input data-testid="file-input" type="file" @change="onPick" />
      <span data-testid="file-category"><el-input v-model="category" placeholder="分类" /></span>
      <el-button native-type="submit" type="primary" data-testid="upload-file">上传</el-button>
      <span class="hint">{{ message }}</span>
    </form>
    <div data-testid="file-table">
      <table class="plain">
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.originalName }}</td>
            <td>{{ row.category }}</td>
            <td>{{ row.textStatus }}</td>
            <td><button type="button" @click="readText(row)">文本</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <pre data-testid="file-text">{{ text }}</pre>
  </main>
</template>

<style scoped>
.plain { width: 100%; border-collapse: collapse; background: #fff; }
.plain td { padding: 8px 10px; border-bottom: 1px solid var(--line); }
pre { white-space: pre-wrap; background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px; }
</style>
