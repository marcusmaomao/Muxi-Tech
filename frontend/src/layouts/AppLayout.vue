<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const { t } = useI18n();
const router = useRouter();
const auth = useAuthStore();

async function onLogout() {
  await auth.signOut();
  await router.push({ name: "login" });
}
</script>

<template>
  <div class="shell">
    <aside class="rail">
      <div class="rail-brand">
        <div class="seal">沐</div>
        <div>
          <strong>{{ t("brand") }}</strong>
          <small>MOP</small>
        </div>
      </div>
      <nav>
        <router-link to="/workbench" class="active">{{ t("nav.workbench") }}</router-link>
        <button class="nav" type="button" disabled>
          {{ t("nav.modeling") }} <span class="soon">{{ t("nav.soon") }}</span>
        </button>
        <button class="nav" type="button" disabled>
          {{ t("nav.semantic") }} <span class="soon">{{ t("nav.soon") }}</span>
        </button>
        <button class="nav" type="button" disabled>
          {{ t("nav.runtime") }} <span class="soon">{{ t("nav.soon") }}</span>
        </button>
        <router-link v-if="auth.can('system:user:list')" to="/system/users">{{ t("nav.system") }}</router-link>
      </nav>
    </aside>
    <div class="main">
      <header class="topbar">
        <span>{{ t("workbench.title") }}</span>
        <div class="who">
          <span>{{ auth.user?.tenantName }}</span>
          <strong>{{ auth.user?.displayName }}</strong>
          <el-button data-testid="logout" @click="onLogout">{{ t("workbench.logout") }}</el-button>
        </div>
      </header>
      <router-view />
    </div>
  </div>
</template>
