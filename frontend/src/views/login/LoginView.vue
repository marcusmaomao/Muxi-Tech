<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { fetchCaptcha } from "@/api/auth";
import { ApiError } from "@/api/http";
import { useAuthStore } from "@/stores/auth";

const { t, locale } = useI18n();
const router = useRouter();
const auth = useAuthStore();
const username = ref("");
const password = ref("");
const captchaCode = ref("");
const captchaId = ref("");
const captchaSvg = ref("");
const error = ref("");
const submitting = ref(false);
const captchaUrl = computed(() =>
  captchaSvg.value ? `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(captchaSvg.value)}` : "",
);

async function loadCaptcha() {
  const challenge = await fetchCaptcha();
  captchaId.value = challenge.captchaId;
  captchaSvg.value = challenge.imageSvg;
}

onMounted(() => {
  void loadCaptcha();
});

function toggleLocale() {
  locale.value = locale.value === "zh-CN" ? "en-US" : "zh-CN";
}

async function onSubmit() {
  error.value = "";
  submitting.value = true;
  try {
    await auth.signIn(username.value.trim(), password.value, captchaId.value, captchaCode.value);
    await router.push({ name: "workbench" });
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "登录失败";
    void loadCaptcha();
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="login-shell">
    <section class="login-story">
      <div class="brand-lockup">
        <div class="seal">沐</div>
        <div>
          <div class="brand-name">{{ t("brand") }}</div>
          <div class="brand-sub">MUXI ONTOLOGY</div>
        </div>
      </div>
      <div class="story-copy">
        <h1>{{ t("tagline") }}</h1>
        <p>{{ t("login.subtitle") }}</p>
        <svg class="graph" viewBox="0 0 440 220" aria-hidden="true">
          <g fill="none" stroke="#d7efe4" stroke-width="1.2" opacity="0.85">
            <path d="M70 150 C120 80, 180 70, 210 110" />
            <path d="M210 110 C250 40, 320 50, 360 120" />
            <path d="M70 150 C140 190, 250 200, 360 120" />
            <path d="M140 70 C170 40, 200 90, 210 110" />
          </g>
          <g font-size="12" fill="#f6f3ec">
            <circle cx="70" cy="150" r="18" fill="#1c5c4e" stroke="#e7b089" />
            <text x="70" y="154" text-anchor="middle">对象</text>
            <circle cx="140" cy="70" r="16" fill="#245e52" stroke="#d7efe4" />
            <text x="140" y="74" text-anchor="middle">术语</text>
            <circle cx="210" cy="110" r="20" fill="#c56a32" stroke="#f6f3ec" />
            <text x="210" y="114" text-anchor="middle">动作</text>
            <circle cx="300" cy="48" r="15" fill="#245e52" stroke="#d7efe4" />
            <text x="300" y="52" text-anchor="middle">约束</text>
            <circle cx="360" cy="120" r="18" fill="#1c5c4e" stroke="#e7b089" />
            <text x="360" y="124" text-anchor="middle">函数</text>
          </g>
        </svg>
      </div>
      <div />
    </section>
    <section class="login-panel">
      <form class="login-card" @submit.prevent="onSubmit">
        <div class="locale-row">
          <button type="button" data-testid="locale-toggle" @click="toggleLocale">
            {{ locale === "zh-CN" ? "EN" : "中文" }}
          </button>
        </div>
        <h2>{{ t("login.title") }}</h2>
        <p class="lead">{{ t("brand") }}</p>
        <p v-if="error" class="error" data-testid="login-error">{{ error }}</p>
        <label class="field">
          <span>{{ t("login.username") }}</span>
          <el-input v-model="username" data-testid="username" autocomplete="username" />
        </label>
        <label class="field">
          <span>{{ t("login.password") }}</span>
          <el-input
            v-model="password"
            data-testid="password"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </label>
        <label class="field">
          <span>{{ t("login.captcha") }}</span>
          <div class="captcha-row">
            <span data-testid="captcha">
              <el-input v-model="captchaCode" maxlength="8" />
            </span>
            <button type="button" class="captcha-image" data-testid="captcha-image" @click="loadCaptcha">
              <img v-if="captchaUrl" :src="captchaUrl" alt="验证码" />
            </button>
          </div>
        </label>
        <el-button
          class="submit"
          type="primary"
          native-type="submit"
          data-testid="login-submit"
          :loading="submitting"
        >
          {{ t("login.submit") }}
        </el-button>
        <p class="hint">{{ t("login.hint") }}</p>
      </form>
    </section>
  </div>
</template>
