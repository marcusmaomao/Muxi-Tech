import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("E2E-07 quality center blocks release while a class is unsatisfiable and allows it after the axiom is removed", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`qual-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill(`质量${stamp}`);
  await page.getByTestId("project-namespace").locator("input").fill(`https://example.com/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await page.getByTestId(`model-qual-${stamp}`).click();

  await page.getByTestId("element-name").locator("input").fill("有哪些");
  await page.getByTestId("element-api").locator("input").fill("which");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("有哪些");

  await page.getByTestId("kind-OBJECT_TYPE").click();
  await page.getByTestId("element-name").locator("input").fill("甲类");
  await page.getByTestId("element-api").locator("input").fill("ClassA");
  await page.getByTestId("element-cq").selectOption({ label: "有哪些" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("甲类");
  await page.getByTestId("element-name").locator("input").fill("乙类");
  await page.getByTestId("element-api").locator("input").fill("ClassB");
  await page.getByTestId("element-cq").selectOption({ label: "有哪些" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("乙类");
  await page.getByTestId("element-name").locator("input").fill("冲突类");
  await page.getByTestId("element-api").locator("input").fill("Clash");
  await page.getByTestId("element-cq").selectOption({ label: "有哪些" });
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

  await page.goto(page.url().replace(/\/model\/([^/]+).*/, "/model/$1/quality"));
  await expect(page.getByTestId("quality-page")).toBeVisible();
  await page.getByTestId("run-quality").click();
  await expect(page.getByTestId("quality-issues")).toContainText("不可满足");
  await expect(page.getByTestId("quality-score")).not.toHaveText("");
  await expect(page.getByTestId("quality-run")).toContainText("运行");
  await page.getByTestId("release-version").locator("input").fill("1.0.0");
  await page.getByTestId("publish-release").click();
  await expect(page.getByTestId("publish-message")).toContainText("发布门禁未通过");

  await page.getByTestId("delete-axiom-classADisjointClassB").click();
  await expect(page.getByTestId("publish-message")).toContainText("公理已删除");
  await page.getByTestId("publish-release").click();
  await expect(page.getByTestId("publish-message")).toContainText("已发布 1.0.0");
  await expect(page.getByTestId("publish-message")).toContainText("ClassA");
  await page.screenshot({ path: "test-results/quality.png", fullPage: true });
});
