import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";

test("login reaches the workbench", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByText("沐曦本体平台").first()).toBeVisible();
  await page.screenshot({ path: "test-results/login.png", fullPage: true });

  await new LoginPage(page).signIn(username, password);

  await expect(page.getByTestId("workbench")).toBeVisible();
  await expect(page.getByText("你好，平台管理员")).toBeVisible();
  await expect(page.getByTestId("health-status")).toContainText("接口正常");
  await page.screenshot({ path: "test-results/workbench.png", fullPage: true });
});

test("wrong password stays on the login page", async ({ page }) => {
  const login = new LoginPage(page);
  await login.open();
  await login.signIn(username, "wrong-password");
  await expect(page.getByTestId("login-error")).toContainText("用户名或密码错误");
  await expect(page).toHaveURL(/\/login/);
});
