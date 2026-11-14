import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { currentUser, login, logout } from "@/api/auth";
import { readSession, writeSession, type StoredSession } from "@/api/session";

export const useAuthStore = defineStore("auth", () => {
  const session = ref<StoredSession | null>(readSession());
  const accessToken = computed(() => session.value?.accessToken ?? null);
  const user = computed(() => session.value?.user ?? null);
  const permissions = computed(() => session.value?.permissions ?? []);
  const isAuthenticated = computed(() => Boolean(accessToken.value));

  function setSession(next: StoredSession) {
    session.value = next;
    writeSession(next);
  }

  function clear() {
    session.value = null;
    writeSession(null);
  }

  function can(permission: string) {
    return permissions.value.includes("*") || permissions.value.includes(permission);
  }

  async function signIn(username: string, password: string, captchaId: string, captchaCode: string) {
    const result = await login(username, password, captchaId, captchaCode);
    setSession({
      accessToken: result.accessToken,
      refreshToken: result.refreshToken,
      user: result.user,
      permissions: [],
    });
    await refreshProfile();
  }

  async function signOut() {
    try {
      await logout(session.value?.refreshToken);
    } catch {
      // 令牌失效时仍然清掉本地会话。
    }
    clear();
  }

  async function refreshProfile() {
    if (!session.value) {
      return;
    }
    const profile = await currentUser();
    setSession({
      accessToken: session.value.accessToken,
      refreshToken: session.value.refreshToken,
      user: profile.user,
      permissions: profile.permissions,
    });
  }

  return { accessToken, user, permissions, isAuthenticated, can, setSession, clear, signIn, signOut, refreshProfile };
});
