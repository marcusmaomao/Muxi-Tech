import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("review an extraction and expand its neighbor on the graph", async ({ page }) => {
  test.setTimeout(90_000);
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`ex-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill(`抽取${stamp}`);
  await page.getByTestId("project-namespace").locator("input").fill(`https://example.com/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await page.getByTestId(`model-ex-${stamp}`).click();

  await page.getByTestId("element-name").locator("input").fill("有哪些设备");
  await page.getByTestId("element-api").locator("input").fill("whichDevices");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("有哪些设备");
  await page.getByTestId("kind-OBJECT_TYPE").click();
  await page.getByTestId("element-cq").selectOption({ label: "有哪些设备" });
  await page.getByTestId("element-name").locator("input").fill("设备");
  await page.getByTestId("element-api").locator("input").fill("Equipment");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("设备");
  await page.getByTestId("element-name").locator("input").fill("变电站");
  await page.getByTestId("element-api").locator("input").fill("Substation");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("变电站");

  await page.getByTestId("kind-PROPERTY").click();
  await page.getByTestId("element-name").locator("input").fill("设备名");
  await page.getByTestId("element-api").locator("input").fill("equipmentName");
  await page.getByTestId("element-owner").selectOption({ label: "设备" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("设备名");
  await page.getByTestId("element-name").locator("input").fill("站名");
  await page.getByTestId("element-api").locator("input").fill("stationName");
  await page.getByTestId("element-owner").selectOption({ label: "变电站" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("站名");

  await page.getByTestId("kind-LINK_TYPE").click();
  await page.getByTestId("element-name").locator("input").fill("安装于");
  await page.getByTestId("element-api").locator("input").fill("installedAt");
  await page.getByTestId("element-owner").selectOption({ label: "设备" });
  await page.getByTestId("element-target").selectOption({ label: "变电站" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("安装于");

  await page.locator("aside").getByRole("link", { name: "文件" }).click();
  await page.getByTestId("file-input").setInputFiles({
    name: `extract-${stamp}.txt`,
    mimeType: "text/plain",
    buffer: Buffer.from("一号变压器安装在城东站。"),
  });
  await page.getByTestId("upload-file").click();
  await expect(page.getByTestId("file-table")).toContainText(`extract-${stamp}.txt`);

  await page.locator("aside").getByRole("link", { name: "模型" }).click();
  await page.getByTestId("model-display").locator("input").fill(`抽取${stamp}`);
  await page.getByTestId("model-name").locator("input").fill(`extract-${stamp}`);
  await page.getByTestId("model-vendor").selectOption("LOCAL");
  await page.getByTestId("model-key").locator("input").fill("sk-e2e-secret");
  await page.getByTestId("model-rpm").locator("input").fill("100");
  await page.getByTestId("create-model").click();
  await expect(page.getByTestId("model-table")).toContainText(`extract-${stamp}`);

  await page.locator("aside").getByRole("link", { name: "抽取" }).click();
  await page.getByTestId("extract-project").selectOption({ label: `抽取${stamp}` });
  await page.getByTestId("extract-file").selectOption({ label: `extract-${stamp}.txt` });
  await page.getByTestId("extract-model").selectOption({ label: `抽取${stamp}` });
  await page.getByTestId("start-extraction").click();
  const items = page.getByTestId("extraction-items");
  await expect(items).toContainText("一号变压器", { timeout: 20_000 });
  await expect(items).toContainText("一号变压器安装在城东站");
  await page.getByTestId("accept-all").click();
  await expect(items).not.toContainText("REVIEW");
  await page.getByTestId("publish-extraction").click();
  await expect(page.getByTestId("extract-message")).toContainText("已发布");

  await page.locator("aside").getByRole("link", { name: "图谱" }).click();
  await page.getByTestId("graph-project").selectOption({ label: `抽取${stamp}` });
  await page.getByTestId("graph-type").selectOption("Equipment");
  await expect(page.getByTestId("graph-nodes")).toContainText("一号变压器");
  await page.getByTestId("expand-一号变压器").click();
  await expect(page.getByTestId("graph-neighbors")).toContainText("城东站");
  await expect(page.getByTestId("graph-detail")).toContainText("一号变压器安装在城东站");
  await page.screenshot({ path: "test-results/extraction-graph.png", fullPage: true });
});
