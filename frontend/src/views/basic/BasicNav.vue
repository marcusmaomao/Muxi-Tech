<script setup lang="ts">
const links = [
  ["项目", "/basic/projects"],
  ["数据源", "/basic/datasources"],
  ["文件", "/basic/files"],
] as const;
</script>

<template>
  <nav class="basic-nav">
    <router-link v-for="item in links" :key="item[1]" :to="item[1]">{{ item[0] }}</router-link>
  </nav>
</template>

<style scoped>
.basic-nav { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.basic-nav a { text-decoration: none; color: var(--ink-soft); padding: 6px 10px; border-radius: 999px; background: #fff; border: 1px solid var(--line); }
.basic-nav a.router-link-active { color: #fff; background: var(--pine); }
</style>
