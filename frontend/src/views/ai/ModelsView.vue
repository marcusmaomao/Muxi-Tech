<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ApiError, http } from "@/api/http";

interface ModelRow {
  id: string;
  displayName: string;
  modelName: string;
  vendor: string;
  baseUrl: string;
  rpmLimit: number;
  hasKey: boolean;
}

const presets: Record<string, string> = {
  OPENAI: "https://api.openai.com/v1",
  TONGYI: "https://dashscope.aliyuncs.com/compatible-mode/v1",
  DEEPSEEK: "https://api.deepseek.com/v1",
  LOCAL: "http://llm-mock:8080/v1",
};

const rows = ref<ModelRow[]>([]);
const displayName = ref("");
const modelName = ref("");
const vendor = ref("LOCAL");
const baseUrl = ref(presets.LOCAL);
const apiKey = ref("");
const rpmLimit = ref("60");
const message = ref("");
const reply = ref("");

function applyPreset() {
  baseUrl.value = presets[vendor.value] ?? "";
}

async function load() {
  const response = await http.get<{ data: ModelRow[] }>("/ai/models");
  rows.value = response.data.data ?? [];
}

async function createModel() {
  message.value = "";
  try {
    await http.post("/ai/models", {
      displayName: displayName.value,
      modelName: modelName.value,
      vendor: vendor.value,
      baseUrl: baseUrl.value,
      apiKey: apiKey.value,
      rpmLimit: Number(rpmLimit.value),
    });
    apiKey.value = "";
    message.value = "已保存，密钥已加密";
    await load();
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "保存失败";
  }
}

async function testModel(row: ModelRow) {
  try {
    const response = await http.post<{ data: { ok: boolean; message: string } }>(`/ai/models/${row.id}/test`);
    const result = response.data.data;
    message.value = result ? result.message : "无结果";
  } catch (caught) {
    message.value = caught instanceof ApiError ? caught.message : "测试失败";
  }
}

async function ask(row: ModelRow) {
  try {
    const response = await http.post<{ data: { content: string } }>("/ai/chat", {
      modelId: row.id,
      messages: [{ role: "user", content: "用一句话介绍本体。" }],
    });
    reply.value = response.data.data?.content ?? "";
  } catch (caught) {
    reply.value = caught instanceof ApiError ? caught.message : "调用失败";
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <main class="page" data-testid="models-page">
    <h1>模型市场</h1>
    <p class="lead">模型名和接口地址由你填写，协议兼容 OpenAI。通义、DeepSeek 和本地模型只是地址预设，密钥加密保存，页面和日志都不回显明文。</p>
    <form class="form-grid" @submit.prevent="createModel">
      <span data-testid="model-display"><el-input v-model="displayName" placeholder="显示名" /></span>
      <span data-testid="model-name"><el-input v-model="modelName" placeholder="模型名，例如 qwen-plus" /></span>
      <select v-model="vendor" data-testid="model-vendor" @change="applyPreset">
        <option value="OPENAI">OpenAI 兼容</option>
        <option value="TONGYI">通义</option>
        <option value="DEEPSEEK">DeepSeek</option>
        <option value="LOCAL">本地 / 回放</option>
      </select>
      <span data-testid="model-base" class="wide"><el-input v-model="baseUrl" placeholder="接口根地址" /></span>
      <span data-testid="model-key"><el-input v-model="apiKey" type="password" placeholder="API 密钥" show-password /></span>
      <span data-testid="model-rpm"><el-input v-model="rpmLimit" placeholder="每分钟限额" /></span>
      <el-button native-type="submit" type="primary" data-testid="create-model">保存</el-button>
    </form>
    <p class="hint" data-testid="model-result">{{ message }}</p>
    <div data-testid="model-table">
      <table class="plain">
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.displayName }}</td>
            <td>{{ row.modelName }}</td>
            <td>{{ row.vendor }}</td>
            <td>{{ row.baseUrl }}</td>
            <td>{{ row.hasKey ? "已加密" : "无密钥" }}</td>
            <td>
              <button type="button" @click="testModel(row)">测试</button>
              <button type="button" @click="ask(row)">调用</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <pre v-if="reply" data-testid="model-reply">{{ reply }}</pre>
  </main>
</template>

<style scoped>
select { height: 32px; border: 1px solid var(--line); border-radius: 10px; background: #fff; padding: 0 8px; }
.plain { width: 100%; border-collapse: collapse; background: #fff; }
.plain td { padding: 8px 10px; border-bottom: 1px solid var(--line); }
pre { white-space: pre-wrap; background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px; }
button { margin-right: 6px; }
</style>
