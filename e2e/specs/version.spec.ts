import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("E2E-06 creates a feature branch, shows the diff, resolves a conflict, and merges", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`version-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill(`版本${stamp}`);
  await page.getByTestId("project-namespace").locator("input").fill(`https://example.com/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await page.getByTestId(`model-version-${stamp}`).click();
  await expect(page.getByTestId("modeling-branch")).toContainText("main");

  await page.getByTestId("element-name").locator("input").fill("有哪些设备");
  await page.getByTestId("element-api").locator("input").fill("whichAssets");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("有哪些设备");
  await page.getByTestId("kind-OBJECT_TYPE").click();
  await page.getByTestId("element-name").locator("input").fill("变压器");
  await page.getByTestId("element-api").locator("input").fill("Transformer");
  await page.getByTestId("element-cq").selectOption({ label: "有哪些设备" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("变压器");

  await page.locator("aside").getByRole("link", { name: "版本" }).click();
  await expect(page.getByTestId("version-page")).toBeVisible();
  await page.getByTestId("version-project").selectOption({ label: `版本${stamp}` });
  await expect(page.getByTestId("branch-list")).toContainText("main");
  await expect(page.getByTestId("commit-list")).toContainText("OBJECT_TYPE");
  await page.getByTestId("branch-name").locator("input").fill(`feature-${stamp}`);
  await page.getByTestId("branch-parent").selectOption({ label: "main" });
  await page.getByTestId("create-branch").click();
  await expect(page.getByTestId("branch-list")).toContainText(`feature-${stamp}`);

  await page.getByTestId(`model-branch-feature-${stamp}`).click();
  await expect(page.getByTestId("modeling-branch")).toContainText(`feature-${stamp}`);
  await page.getByTestId("edit-object").selectOption({ label: "变压器" });
  await page.getByTestId("edit-description").locator("input").fill("功能分支");
  await page.getByTestId("save-description").click();
  await expect(page.getByTestId("form-error")).toContainText("说明已保存");

  await page.locator("aside").getByRole("link", { name: "版本" }).click();
  await page.getByTestId("version-project").selectOption({ label: `版本${stamp}` });
  await page.getByTestId("model-branch-main").click();
  await expect(page.getByTestId("modeling-branch")).toContainText("main");
  await page.getByTestId("edit-object").selectOption({ label: "变压器" });
  await page.getByTestId("edit-description").locator("input").fill("主干");
  await page.getByTestId("save-description").click();
  await expect(page.getByTestId("form-error")).toContainText("说明已保存");

  await page.locator("aside").getByRole("link", { name: "版本" }).click();
  await page.getByTestId("version-project").selectOption({ label: `版本${stamp}` });
  await page.getByTestId("diff-from").selectOption("main");
  await page.getByTestId("diff-to").selectOption(`feature-${stamp}`);
  await page.getByTestId("run-diff").click();
  await expect(page.getByTestId("diff-modified")).toContainText("/description");
  await expect(page.getByTestId("diff-modified")).toContainText("功能分支");

  await page.getByTestId("mr-source").selectOption({ label: `feature-${stamp}` });
  await page.getByTestId("mr-target").selectOption({ label: "main" });
  await page.getByTestId("mr-title").locator("input").fill(`合并${stamp}`);
  await page.getByTestId("open-mr").click();
  await expect(page.getByTestId("mr-list")).toContainText("有冲突");
  await expect(page.getByTestId("conflict-list")).toContainText("/description");
  await page.getByTestId("keep-source").click();
  await expect(page.getByTestId("mr-list")).toContainText("待合并");
  await page.getByTestId("merge-mr").click();
  await expect(page.getByTestId("mr-list")).toContainText("已合并");
  await page.screenshot({ path: "test-results/version.png", fullPage: true });
});
