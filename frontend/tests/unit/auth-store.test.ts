import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import { useAuthStore } from "../../src/stores/auth";

describe("auth store", () => {
  beforeEach(() => {
    sessionStorage.clear();
    setActivePinia(createPinia());
  });

  it("keeps a session after login data is stored", () => {
    const store = useAuthStore();
    store.setSession({
      accessToken: "token-1",
      user: {
        uid: "uid",
        username: "admin",
        displayName: "平台管理员",
        tenantId: "7",
        tenantName: "演示租户",
      },
    });
    expect(store.isAuthenticated).toBe(true);
    expect(store.user?.displayName).toBe("平台管理员");
    expect(sessionStorage.getItem("mop.session")).toContain("token-1");
    store.clear();
    expect(store.isAuthenticated).toBe(false);
  });
});
