import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("admin saves an encrypted local model and completes a mock chat", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "模型" }).click();
  await expect(page.getByTestId("models-page")).toBeVisible();
  await page.getByTestId("model-display").locator("input").fill("回放模型");
  await page.getByTestId("model-name").locator("input").fill(`mock-${stamp}`);
  await page.getByTestId("model-vendor").selectOption("LOCAL");
  await page.getByTestId("model-key").locator("input").fill("sk-e2e-secret");
  await page.getByTestId("create-model").click();
  const row = page.getByRole("row", { name: new RegExp(`mock-${stamp}`) });
  await expect(row).toContainText("已加密");
  await expect(page.getByTestId("model-table")).not.toContainText("sk-e2e-secret");
  await row.getByRole("button", { name: "测试" }).click();
  await expect(page.getByTestId("model-result")).toContainText("llm-mock");
  await row.getByRole("button", { name: "调用" }).click();
  await expect(page.getByTestId("model-reply")).toContainText("llm-mock");
  await page.screenshot({ path: "test-results/models.png", fullPage: true });
});
