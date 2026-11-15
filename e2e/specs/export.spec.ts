import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("modeling page exports turtle with a stable api-name iri", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`owl-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill("导出电网");
  await page.getByTestId("project-namespace").locator("input").fill(`https://muxi.example/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await page.getByTestId(`model-owl-${stamp}`).click();

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

  await page.getByTestId("export-ttl").click();
  const preview = page.getByTestId("export-preview");
  await expect(preview).toContainText(`https://muxi.example/ont/${stamp}#`);
  await expect(preview).toContainText("Transformer");
  await page.screenshot({ path: "test-results/export-turtle.png", fullPage: true });
});
