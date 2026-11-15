import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("canvas adds a class, marks a missing CQ, undoes, then saves the view", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`canvas-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill(`画布${stamp}`);
  await page.getByTestId("project-namespace").locator("input").fill(`https://example.com/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await page.getByTestId(`canvas-canvas-${stamp}`).click();
  await expect(page.getByTestId("canvas-page")).toBeVisible();
  await expect(page.getByTestId("canvas-graph")).toBeVisible();
  await expect(page.getByTestId("canvas-ready")).toBeAttached();

  await page.getByTestId("stencil-object").click();
  await expect(page.getByTestId("node-Obj1")).toContainText("缺少能力问题");
  await page.getByTestId("undo-canvas").click();
  await expect(page.getByTestId("canvas-nodes")).not.toContainText("新对象1");
  await page.getByTestId("redo-canvas").click();
  await expect(page.getByTestId("validation-mark")).toBeVisible();

  await page.getByTestId("create-cq").click();
  await expect(page.getByTestId("canvas-message")).toContainText("能力问题已就绪");
  await page.getByTestId("node-Obj1").click();
  await page.getByTestId("canvas-cq").selectOption({ label: "画布对象" });
  await page.getByTestId("apply-node").click();
  await page.getByTestId("commit-canvas").click();
  await expect(page.getByTestId("canvas-message")).toContainText("已提交");
  await expect(page.getByTestId("validation-mark")).toHaveCount(0);

  await page.getByTestId("stencil-object").click();
  await page.getByTestId("canvas-parent").selectOption({ label: "新对象1" });
  await page.getByTestId("apply-node").click();
  await expect(page.getByTestId("canvas-edges")).toContainText("继承");
  await page.getByTestId("commit-canvas").click();
  await expect(page.getByTestId("canvas-message")).toContainText("已提交");
  await page.getByTestId("layout-canvas").click();
  await expect(page.getByTestId("canvas-message")).toContainText("已自动布局");
  await page.getByTestId("save-view").click();
  await expect(page.getByTestId("canvas-message")).toContainText("视图已保存");

  await page.reload();
  await expect(page.getByTestId("node-Obj1")).toBeVisible();
  await expect(page.getByTestId("node-Obj2")).toBeVisible();
  await page.getByTestId("node-Obj1").click();
  await page.getByTestId("lock-node").click();
  await expect(page.getByTestId("lock-state")).toContainText("已锁定");
  await page.screenshot({ path: "test-results/canvas.png", fullPage: true });
});
