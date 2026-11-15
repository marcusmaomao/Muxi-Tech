import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("E2E-03 canvas drag creates classes, a link, and multiple parents, rejects a cycle, and keeps layout", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`e2e03-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill(`画布流程${stamp}`);
  await page.getByTestId("project-namespace").locator("input").fill(`https://example.com/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await page.getByTestId(`canvas-e2e03-${stamp}`).click();
  await expect(page.getByTestId("canvas-ready")).toBeAttached();

  const graph = page.getByTestId("canvas-graph");
  for (let index = 0; index < 3; index += 1) {
    await page.getByTestId("stencil-object").dragTo(graph, { targetPosition: { x: 80 + index * 40, y: 80 } });
  }
  await expect(page.getByTestId("node-Obj1")).toBeVisible();
  await expect(page.getByTestId("node-Obj2")).toBeVisible();
  await expect(page.getByTestId("node-Obj3")).toBeVisible();

  await page.getByTestId("create-cq").click();
  await expect(page.getByTestId("canvas-message")).toContainText("能力问题已就绪");
  for (const api of ["Obj1", "Obj2", "Obj3"]) {
    await page.getByTestId(`node-${api}`).click();
    await page.getByTestId("canvas-cq").selectOption({ label: "画布对象" });
    await page.getByTestId("apply-node").click();
  }
  await page.getByTestId("node-Obj3").click();
  await page.getByTestId("canvas-parent").selectOption([{ label: "新对象1" }, { label: "新对象2" }]);
  await page.getByTestId("apply-node").click();
  await expect(page.getByTestId("canvas-edges")).toContainText("继承 新对象3 → 新对象1");
  await expect(page.getByTestId("canvas-edges")).toContainText("继承 新对象3 → 新对象2");

  await page.getByTestId("node-Obj1").click();
  await page.getByTestId("canvas-parent").selectOption({ label: "新对象3" });
  await page.getByTestId("apply-node").click();
  await expect(page.getByTestId("canvas-message")).toContainText("继承环");

  await page.getByTestId("node-Obj1").click();
  await page.getByTestId("canvas-link-target").selectOption({ label: "新对象2" });
  await page.getByTestId("apply-link").click();
  await expect(page.getByTestId("canvas-edges")).toContainText("关系 新对象1 → 新对象2");

  await page.getByTestId("stencil-object").click();
  await expect(page.getByTestId("node-Obj4")).toBeVisible();
  await page.getByTestId("undo-canvas").click();
  await expect(page.getByTestId("canvas-nodes")).not.toContainText("新对象4");
  await page.getByTestId("redo-canvas").click();
  await expect(page.getByTestId("node-Obj4")).toBeVisible();
  await page.getByTestId("undo-canvas").click();

  await page.getByTestId("commit-canvas").click();
  await expect(page.getByTestId("canvas-message")).toContainText("已提交");
  const x = await page.getByTestId("node-Obj1").getAttribute("data-x");
  const y = await page.getByTestId("node-Obj1").getAttribute("data-y");
  await page.getByTestId("save-view").click();
  await expect(page.getByTestId("canvas-message")).toContainText("视图已保存");
  await page.reload();
  await expect(page.getByTestId("canvas-ready")).toBeAttached();
  await expect(page.getByTestId("node-Obj1")).toHaveAttribute("data-x", x ?? "");
  await expect(page.getByTestId("node-Obj1")).toHaveAttribute("data-y", y ?? "");
  await expect(page.getByTestId("node-Obj3")).toBeVisible();
  await expect(page.getByTestId("canvas-edges")).toContainText("继承");
});
