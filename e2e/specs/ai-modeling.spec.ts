import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("E2E-04 AI modeling shows progress and evidence, then adopt creates a commit", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`ai-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill(`电网${stamp}`);
  await page.getByTestId("project-namespace").locator("input").fill(`https://example.com/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await expect(page.getByTestId("project-table")).toContainText(`电网${stamp}`);

  await page.locator("aside").getByRole("link", { name: "文件" }).click();
  await page.getByTestId("file-input").setInputFiles({
    name: `device-${stamp}.txt`,
    mimeType: "text/plain",
    buffer: Buffer.from("变压器是电网的主设备。额定电压需要记录。"),
  });
  await page.getByTestId("upload-file").click();
  await expect(page.getByTestId("file-table")).toContainText(`device-${stamp}.txt`);

  await page.locator("aside").getByRole("link", { name: "模型" }).click();
  await page.getByTestId("model-display").locator("input").fill(`回放${stamp}`);
  await page.getByTestId("model-name").locator("input").fill(`mock-${stamp}`);
  await page.getByTestId("model-vendor").selectOption("LOCAL");
  await page.getByTestId("model-key").locator("input").fill("sk-e2e-secret");
  await page.getByTestId("model-rpm").locator("input").fill("200");
  await page.getByTestId("create-model").click();
  await expect(page.getByTestId("model-table")).toContainText(`mock-${stamp}`);

  await page.locator("aside").getByRole("link", { name: "AI 建模" }).click();
  await page.getByTestId("ai-project").selectOption({ label: `电网${stamp}` });
  await page.getByTestId("ai-file").selectOption({ label: `device-${stamp}.txt` });
  await page.getByTestId("ai-model").selectOption({ label: `回放${stamp}` });
  await page.getByTestId("start-job").click();
  const list = page.getByTestId("candidate-list");
  await expect(list).toContainText("变压器", { timeout: 30_000 });
  await expect(list).toContainText("变压器是电网的主设备");
  await expect(page.getByTestId("ai-log")).toContainText("候选");
  await list.getByRole("listitem").filter({ hasText: "CQ" }).getByRole("button", { name: "采纳" }).click();
  await expect(page.getByTestId("ai-status")).toContainText("已采纳");
  await page.getByTestId("adopt-all").click();
  await expect(page.getByTestId("ai-status")).toContainText("已批量采纳");
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId(`model-ai-${stamp}`).click();
  await page.getByTestId("kind-OBJECT_TYPE").click();
  await expect(page.getByTestId("element-list")).toContainText("变压器");
  await page.screenshot({ path: "test-results/ai-modeling.png", fullPage: true });
});
