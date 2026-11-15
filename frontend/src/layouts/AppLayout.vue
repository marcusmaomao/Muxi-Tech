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
        <router-link to="/workbench">{{ t("nav.workbench") }}</router-link>
        <router-link v-if="auth.can('model:form:edit')" to="/model">{{ t("nav.modeling") }}</router-link>
        <button class="nav" type="button" disabled>
          {{ t("nav.semantic") }} <span class="soon">{{ t("nav.soon") }}</span>
        </button>
        <router-link v-if="auth.can('runtime:sync')" to="/runtime/sync">{{ t("nav.sync") }}</router-link>
        <router-link v-if="auth.can('basic:project:list')" to="/basic/projects">{{ t("nav.projects") }}</router-link>
        <router-link v-if="auth.can('basic:datasource:list')" to="/basic/datasources">{{ t("nav.datasources") }}</router-link>
        <router-link v-if="auth.can('basic:file:list')" to="/basic/files">{{ t("nav.files") }}</router-link>
        <router-link v-if="auth.can('ai:model:list')" to="/ai/models">{{ t("nav.models") }}</router-link>
        <router-link v-if="auth.can('ai:model:edit')" to="/ai/modeling">{{ t("nav.aiModeling") }}</router-link>
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
