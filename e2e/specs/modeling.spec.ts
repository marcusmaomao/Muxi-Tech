import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("E2E-02 form modeling covers terms, enums, objects, properties, links, and blocks a missing CQ", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`model-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill("表单电网");
  await page.getByTestId("project-namespace").locator("input").fill(`https://muxi.example/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await page.getByTestId(`model-model-${stamp}`).click();
  await expect(page.getByTestId("modeling-page")).toBeVisible();

  await page.getByTestId("element-name").locator("input").fill("有哪些设备");
  await page.getByTestId("element-api").locator("input").fill("whichAssets");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("有哪些设备");

  await page.getByTestId("kind-OBJECT_TYPE").click();
  await page.getByTestId("element-name").locator("input").fill("变压器");
  await page.getByTestId("element-api").locator("input").fill("Transformer");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("form-error")).toContainText("能力问题");
  await page.screenshot({ path: "test-results/modeling-blocked.png", fullPage: true });

  await page.getByTestId("element-cq").selectOption({ label: "有哪些设备" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("变压器");

  await page.getByTestId("kind-PROPERTY").click();
  await page.getByTestId("element-name").locator("input").fill("额定电压");
  await page.getByTestId("element-api").locator("input").fill("ratedVoltage");
  await page.getByTestId("element-owner").selectOption({ label: "变压器" });
  await page.getByTestId("element-min").locator("input").fill("1");
  await page.getByTestId("element-max").locator("input").fill("1");
  await page.getByTestId("element-default").locator("input").fill("10");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("额定电压");

  await page.getByTestId("kind-TERM").click();
  await page.getByTestId("element-name").locator("input").fill("变压器术语");
  await page.getByTestId("element-api").locator("input").fill("transformerTerm");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("变压器术语");

  await page.getByTestId("kind-ENUM_TYPE").click();
  await page.getByTestId("element-name").locator("input").fill("运行状态");
  await page.getByTestId("element-api").locator("input").fill("RunState");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("运行状态");
  await page.getByTestId("kind-ENUM_VALUE").click();
  await page.getByTestId("element-name").locator("input").fill("运行");
  await page.getByTestId("element-api").locator("input").fill("Running");
  await page.getByTestId("element-owner").selectOption({ label: "运行状态" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("运行");

  await page.getByTestId("kind-LINK_TYPE").click();
  await page.getByTestId("element-name").locator("input").fill("连接");
  await page.getByTestId("element-api").locator("input").fill("connects");
  await page.getByTestId("element-cq").selectOption({ label: "有哪些设备" });
  await page.getByTestId("element-owner").selectOption({ label: "变压器" });
  await page.getByTestId("element-target").selectOption({ label: "变压器" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("连接");
  await page.screenshot({ path: "test-results/modeling.png", fullPage: true });
});
