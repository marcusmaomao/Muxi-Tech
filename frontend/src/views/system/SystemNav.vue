<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const links = [
  ["users", "/system/users"],
  ["roles", "/system/roles"],
  ["menus", "/system/menus"],
  ["depts", "/system/depts"],
  ["posts", "/system/posts"],
  ["dicts", "/system/dicts"],
  ["configs", "/system/configs"],
  ["notices", "/system/notices"],
  ["logs", "/system/logs"],
] as const;
</script>

<template>
  <nav class="system-nav">
    <router-link v-for="item in links" :key="item[0]" :to="item[1]">{{ t(`nav.${item[0]}`) }}</router-link>
  </nav>
</template>

<style scoped>
.system-nav { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.system-nav a { text-decoration: none; color: var(--ink-soft); padding: 6px 10px; border-radius: 999px; background: #fff; border: 1px solid var(--line); }
.system-nav a.router-link-active { color: #fff; background: var(--pine); }
</style>
