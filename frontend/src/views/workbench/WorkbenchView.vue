<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { healthStatus } from "@/api/auth";
import { useAuthStore } from "@/stores/auth";

const { t } = useI18n();
const auth = useAuthStore();
const healthy = ref<boolean | null>(null);

onMounted(async () => {
  try {
    await auth.refreshProfile();
  } catch {
    // 401 会由请求封装清掉会话并回到登录页。
  }
  healthy.value = await healthStatus();
});
</script>

<template>
  <main class="page" data-testid="workbench">
    <h1>{{ t("workbench.title") }}</h1>
    <p class="hello">{{ t("workbench.hello", { name: auth.user?.displayName ?? "" }) }}</p>
    <section class="pillars">
      <article class="pillar">
        <em>01</em>
        <strong>{{ t("workbench.pillars.model.title") }}</strong>
        <p>{{ t("workbench.pillars.model.text") }}</p>
      </article>
      <article class="pillar">
        <em>02</em>
        <strong>{{ t("workbench.pillars.check.title") }}</strong>
        <p>{{ t("workbench.pillars.check.text") }}</p>
      </article>
      <article class="pillar">
        <em>03</em>
        <strong>{{ t("workbench.pillars.govern.title") }}</strong>
        <p>{{ t("workbench.pillars.govern.text") }}</p>
      </article>
      <article class="pillar">
        <em>04</em>
        <strong>{{ t("workbench.pillars.run.title") }}</strong>
        <p>{{ t("workbench.pillars.run.text") }}</p>
      </article>
    </section>
    <section class="empty">
      <h2>{{ t("workbench.emptyTitle") }}</h2>
      <p>{{ t("workbench.emptyBody") }}</p>
      <div class="health" data-testid="health-status">
        <span class="dot" :class="{ up: healthy }" />
        <span v-if="healthy === null">…</span>
        <span v-else>{{ healthy ? t("workbench.healthOk") : t("workbench.healthDown") }}</span>
      </div>
    </section>
  </main>
</template>
