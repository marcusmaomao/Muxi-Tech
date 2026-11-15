import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("ELK reports a class under two disjoint parents as unsatisfiable", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`reason-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill(`推理${stamp}`);
  await page.getByTestId("project-namespace").locator("input").fill(`https://example.com/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await page.getByTestId(`model-reason-${stamp}`).click();

  await page.getByTestId("element-name").locator("input").fill("有哪些设备");
  await page.getByTestId("element-api").locator("input").fill("whichAssets");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("有哪些设备");

  await page.getByTestId("kind-OBJECT_TYPE").click();
  await page.getByTestId("element-name").locator("input").fill("甲类");
  await page.getByTestId("element-api").locator("input").fill("ClassA");
  await page.getByTestId("element-cq").selectOption({ label: "有哪些设备" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("甲类");
  await page.getByTestId("element-name").locator("input").fill("乙类");
  await page.getByTestId("element-api").locator("input").fill("ClassB");
  await page.getByTestId("element-cq").selectOption({ label: "有哪些设备" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("乙类");
  await page.getByTestId("element-name").locator("input").fill("冲突类");
  await page.getByTestId("element-api").locator("input").fill("Clash");
  await page.getByTestId("element-cq").selectOption({ label: "有哪些设备" });
  await page.getByTestId("element-parents").selectOption([{ label: "甲类" }, { label: "乙类" }]);
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("冲突类");

  await page.getByTestId("kind-AXIOM").click();
  await page.getByTestId("axiom-kind").selectOption("DISJOINT");
  await page.getByTestId("element-name").locator("input").fill("甲乙互斥");
  await page.getByTestId("element-api").locator("input").fill("classADisjointClassB");
  await page.getByTestId("element-owner").selectOption({ label: "甲类" });
  await page.getByTestId("element-target").selectOption({ label: "乙类" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("甲乙互斥");

  await page.getByTestId("run-reason").click();
  const report = page.getByTestId("reason-report");
  await expect(report).toContainText("不可满足：冲突类");
  await expect(report).toContainText("冲突类 ⊑");
  await page.screenshot({ path: "test-results/reason.png", fullPage: true });
});
